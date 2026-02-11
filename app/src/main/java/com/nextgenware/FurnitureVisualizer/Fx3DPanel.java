package com.nextgenware.FurnitureVisualizer;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;
import javafx.scene.input.PickResult;

import javax.swing.*;
import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Fx3DPanel extends JPanel {

    private final LayoutModel model;
    private final JFXPanel fxPanel = new JFXPanel();
    private java.util.function.Consumer<String> onFurnitureClicked;

    // Root of 3D world
    private final Group root3d = new Group();

    // Room nodes (rebuild/update)
    private final Group roomGroup = new Group();
    private final Group gridGroup = new Group();

    // Furniture nodes by ID
    private final Map<String, Box> furnitureNodes = new HashMap<>();

    private SubScene subScene;
    private PerspectiveCamera camera;

    // Navigation rig: pivot(target) -> yaw -> pitch -> camera
    private final Group pivot = new Group();
    private final Rotate yaw = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(-25, Rotate.X_AXIS);

    // Target (orbit center) in world units
    private double targetX = 0;
    private double targetY = -120;
    private double targetZ = 0;

    // Orbit distance
    private double distance = 900;

    // Scale: 1 meter = 100 JavaFX units
    private static final double S = 100.0;

    // Track previous room state
    private double lastRoomW = -1;
    private double lastRoomD = -1;
    private double lastRoomH = -1;
    private int lastFloorColor = 0;
    private int lastWallColor = 0;
    private int lastGridColor = 0;

    // ---------- 3D Move (Blender-like) ----------
    private boolean moveMode = false;     // G key toggle
    private char axisLock = 0;            // 0=free, 'X','Y','Z'
    private String selectedId = null;

    private double dragStartMouseX, dragStartMouseY;
    private double dragStartItemX, dragStartItemY, dragStartItemZ;

// tweak sensitivity
    private static final double DRAG_SENS = 0.01; // screen pixels -> world units

    // ---------- Hover highlight ----------
    private Box hoveredBox = null;
    private final Map<Box, PhongMaterial> originalMaterials = new HashMap<>();

    // ---------- WASD state ----------
    private volatile boolean wDown, aDown, sDown, dDown, qDown, eDown, shiftDown;
    private AnimationTimer navTimer;

    public Fx3DPanel(LayoutModel model) {
        super(new BorderLayout());
        this.model = model;

        add(fxPanel, BorderLayout.CENTER);

        Platform.runLater(this::initFx);

        model.addListener(() -> Platform.runLater(this::syncFromModel));
    }

    public void setSelectedId(String id) {
        this.selectedId = id;
    }

    public void setOnFurnitureClicked(java.util.function.Consumer<String> cb) {
        this.onFurnitureClicked = cb;
    }

    // -------------- JavaFX init ----------------
    private void initFx() {
        subScene = new SubScene(root3d, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#f6f6f6"));

        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(5000);

        pivot.getTransforms().addAll(yaw, pitch);
        pivot.getChildren().add(camera);
        root3d.getChildren().add(pivot);

        root3d.getChildren().add(roomGroup);
        root3d.getChildren().add(gridGroup);

        resetView();

        subScene.setCamera(camera);

        Group uiRoot = new Group(subScene);
        Scene scene = new Scene(uiRoot);
        fxPanel.setScene(scene);

        fxPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                Platform.runLater(() -> {
                    subScene.setWidth(fxPanel.getWidth());
                    subScene.setHeight(fxPanel.getHeight());
                });
            }
        });

        // Make sure we can receive keyboard input
        subScene.setFocusTraversable(true);

        addLights();
        buildOrUpdateRoom(true);
        syncFromModel();

        install3dNavigationControls(scene);
        installHoverHighlight();
        startWASDNavigationLoop();
    }

    private void addLights() {
        AmbientLight amb = new AmbientLight(Color.color(0.85, 0.85, 0.85));

        PointLight key = new PointLight(Color.WHITE);
        key.setTranslateY(-600);
        key.setTranslateZ(-600);

        root3d.getChildren().addAll(amb, key);
    }

    // Rebuild room + grid if size/colors changed
    private void buildOrUpdateRoom(boolean force) {
        if (model.room == null) {
            return;
        }

        double w = model.room.width * S;
        double d = model.room.depth * S;
        double h = model.room.height * S;

        int floorCol = model.room.floorColor;
        int wallCol = model.room.wallColor;
        int gridCol = model.room.gridColor;

        boolean changed
                = force
                || w != lastRoomW || d != lastRoomD || h != lastRoomH
                || floorCol != lastFloorColor || wallCol != lastWallColor || gridCol != lastGridColor;

        if (!changed) {
            return;
        }

        lastRoomW = w;
        lastRoomD = d;
        lastRoomH = h;
        lastFloorColor = floorCol;
        lastWallColor = wallCol;
        lastGridColor = gridCol;

        roomGroup.getChildren().clear();

        PhongMaterial floorMat = new PhongMaterial(argbToFxColor(floorCol));
        PhongMaterial wallMat = new PhongMaterial(argbToFxColor(wallCol));

        // Floor
        Box floor = new Box(w, 5, d);
        floor.setTranslateY(0);
        floor.setMaterial(floorMat);

        // Walls
        Box wallN = new Box(w, h, 5);
        wallN.setTranslateZ(-d / 2);
        wallN.setTranslateY(-h / 2);
        wallN.setMaterial(wallMat);

        Box wallS = new Box(w, h, 5);
        wallS.setTranslateZ(d / 2);
        wallS.setTranslateY(-h / 2);
        wallS.setMaterial(wallMat);

        Box wallE = new Box(5, h, d);
        wallE.setTranslateX(w / 2);
        wallE.setTranslateY(-h / 2);
        wallE.setMaterial(wallMat);

        Box wallW = new Box(5, h, d);
        wallW.setTranslateX(-w / 2);
        wallW.setTranslateY(-h / 2);
        wallW.setMaterial(wallMat);

        roomGroup.getChildren().addAll(floor, wallN, wallS, wallE, wallW);

        // Build 3D grid floor (thin strips)
        buildGridFloor(w, d, argbToFxColor(gridCol));

        // Keep target centered after room changes
        targetX = 0;
        targetZ = 0;
        applyCameraRig();
    }

    private void buildGridFloor(double roomW, double roomD, Color gridColor) {
        gridGroup.getChildren().clear();

        // Grid step: 0.1m like 2D (10cm)
        double step = 0.1 * S; // in fx units
        if (step < 6) {
            step = 6;
        }

        // Thickness and height
        double thickness = 1.0;
        double y = -1.0; // slightly above floor (floor is at y=0 with thickness 5)

        PhongMaterial mat = new PhongMaterial(gridColor);

        // We center room at origin in 3D:
        // X from -roomW/2 to +roomW/2
        // Z from -roomD/2 to +roomD/2
        double xMin = -roomW / 2;
        double xMax = roomW / 2;
        double zMin = -roomD / 2;
        double zMax = roomD / 2;

        // Vertical lines (along Z)
        for (double x = xMin; x <= xMax + 0.001; x += step) {
            Box line = new Box(thickness, 0.5, roomD);
            line.setTranslateX(x);
            line.setTranslateY(y);
            line.setTranslateZ(0);
            line.setMaterial(mat);
            gridGroup.getChildren().add(line);
        }

        // Horizontal lines (along X)
        for (double z = zMin; z <= zMax + 0.001; z += step) {
            Box line = new Box(roomW, 0.5, thickness);
            line.setTranslateX(0);
            line.setTranslateY(y);
            line.setTranslateZ(z);
            line.setMaterial(mat);
            gridGroup.getChildren().add(line);
        }
    }

    // -------------- Sync model -> 3D ----------------
    private void syncFromModel() {
        if (model.room == null) {
            return;
        }

        buildOrUpdateRoom(false);

        double roomW = model.room.width * S;
        double roomD = model.room.depth * S;

        Set<String> alive = new HashSet<>();

        for (FurnitureItem it : model.items) {
            alive.add(it.id);

            Box b = furnitureNodes.get(it.id);
            if (b == null) {
                b = new Box(1, 1, 1);
                furnitureNodes.put(it.id, b);
                root3d.getChildren().add(b);
            }

            // Apply color
            PhongMaterial mat = (PhongMaterial) b.getMaterial();
            if (mat == null) {
                mat = new PhongMaterial();
            }
            mat.setDiffuseColor(argbToFxColor(it.color));
            b.setMaterial(mat);

            // Size with scale
            double sc = (it.scale <= 0) ? 1.0 : it.scale;
            b.setWidth((it.width * sc) * S);
            b.setDepth((it.depth * sc) * S);
            b.setHeight((it.height * sc) * S);

            // Position (center room at origin)
            double cx = (it.x * S) - roomW / 2;
            double cz = (it.z * S) - roomD / 2;

            b.setTranslateX(cx);
            b.setTranslateZ(cz);

            // Sit on floor
            double baseY = -((it.height * sc) * S) / 2;     // sits on floor
            double liftY = -(it.y * S);                     // y meters up (negative is up here)
            b.setTranslateY(baseY + liftY);

            // Rotation
            b.getTransforms().removeIf(t -> t instanceof Rotate);
            b.getTransforms().add(new Rotate(it.rotationDeg, Rotate.Y_AXIS));
        }

        // Remove deleted nodes + cleanup hover state
        furnitureNodes.entrySet().removeIf(entry -> {
            String id = entry.getKey();
            Box box = entry.getValue();
            if (!alive.contains(id)) {
                if (hoveredBox == box) {
                    clearHoverHighlight();
                }
                originalMaterials.remove(box);
                root3d.getChildren().remove(box);
                return true;
            }
            return false;
        });
    }

    // -------------- Mouse orbit/pan/zoom + reset ----------------
    private void install3dNavigationControls(Scene scene) {
        final double[] last = new double[2];

        subScene.setOnMouseEntered(e -> subScene.requestFocus());
        subScene.setOnMousePressed(e -> {
            if (moveMode && selectedId != null) {
                FurnitureItem it = findItemById(selectedId);
                if (it != null) {
                    dragStartMouseX = e.getSceneX();
                    dragStartMouseY = e.getSceneY();

                    dragStartItemX = it.x;
                    dragStartItemY = it.y;
                    dragStartItemZ = it.z;
                }
            }
            last[0] = e.getSceneX();
            last[1] = e.getSceneY();
            subScene.requestFocus();

        });

        subScene.setOnMouseDragged(e -> {
            if (moveMode && selectedId != null) {
                FurnitureItem it = findItemById(selectedId);
                if (it == null) {
                    return;
                }

                double dx = e.getSceneX() - dragStartMouseX;
                double dy = e.getSceneY() - dragStartMouseY;

                // Convert screen drag -> meters
                double mx = dx * DRAG_SENS;
                double my = -dy * DRAG_SENS;
                double mz = -dy * DRAG_SENS;

                // Axis lock
                if (axisLock == 'X') {
                    it.x = dragStartItemX + mx;
                    it.z = dragStartItemZ;
                    it.y = dragStartItemY;
                } else if (axisLock == 'Z') {
                    it.z = dragStartItemZ + mz;
                    it.x = dragStartItemX;
                    it.y = dragStartItemY;
                } else if (axisLock == 'Y') {
                    it.y = Math.max(0, dragStartItemY + my); // keep above floor
                    it.x = dragStartItemX;
                    it.z = dragStartItemZ;
                } else {
                    // free move on floor (X + Z)
                    it.x = dragStartItemX + mx;
                    it.z = dragStartItemZ + mz;
                    it.y = dragStartItemY;
                }

                // Keep inside room for X/Z
                constrainInsideRoom3D(it);

                model.notifyChanged(); // triggers 2D + 3D sync
                return; // IMPORTANT: don't orbit/pan while moving
            }

            double dx = e.getSceneX() - last[0];
            double dy = e.getSceneY() - last[1];
            last[0] = e.getSceneX();
            last[1] = e.getSceneY();

            boolean panMode = (e.getButton() == MouseButton.SECONDARY) || e.isShiftDown();

            if (!panMode) {
                // ORBIT
                yaw.setAngle(yaw.getAngle() + dx * 0.35);
                pitch.setAngle(clamp(pitch.getAngle() - dy * 0.25, -80, -10));
            } else {
                // PAN target in X/Z
                double panScale = Math.max(0.2, distance / 1200.0);
                targetX += (-dx * panScale);
                targetZ += (-dy * panScale);
            }
            applyCameraRig();
        });

        subScene.setOnScroll(e -> {
            double delta = e.getDeltaY();
            distance = clamp(distance - delta * 0.9, 250, 2500);
            applyCameraRig();
        });

        subScene.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                resetView();
            }
        });

        // -------- Keyboard (WASD) --------
        scene.setOnKeyPressed(e -> {
            KeyCode k = e.getCode();
            if (k == KeyCode.W) {
                wDown = true;
            }
            if (k == KeyCode.A) {
                aDown = true;
            }
            if (k == KeyCode.S) {
                sDown = true;
            }
            if (k == KeyCode.D) {
                dDown = true;
            }

            if (k == KeyCode.Q) {
                qDown = true;
            }
            if (k == KeyCode.E) {
                eDown = true;
            }

            if (k == KeyCode.SHIFT) {
                shiftDown = true;
            }
            if (k == KeyCode.G) {
                moveMode = !moveMode;
                axisLock = 0;
            }

// Axis lock while moving
            if (moveMode) {
                if (k == KeyCode.X) {
                    axisLock = 'X';
                }
                if (k == KeyCode.Y) {
                    axisLock = 'Y';
                }
                if (k == KeyCode.Z) {
                    axisLock = 'Z';
                }

                // ESC cancel
                if (k == KeyCode.ESCAPE) {
                    moveMode = false;
                    axisLock = 0;
                }
            }
        });

        scene.setOnKeyReleased(e -> {
            KeyCode k = e.getCode();
            if (k == KeyCode.W) {
                wDown = false;
            }
            if (k == KeyCode.A) {
                aDown = false;
            }
            if (k == KeyCode.S) {
                sDown = false;
            }
            if (k == KeyCode.D) {
                dDown = false;
            }

            if (k == KeyCode.Q) {
                qDown = false;
            }
            if (k == KeyCode.E) {
                eDown = false;
            }

            if (k == KeyCode.SHIFT) {
                shiftDown = false;
            }
        });
    }

    private void resetView() {
        yaw.setAngle(25);
        pitch.setAngle(-25);
        targetX = 0;
        targetZ = 0;
        targetY = -120;
        distance = 900;
        applyCameraRig();
    }

    private void applyCameraRig() {
        pivot.setTranslateX(targetX);
        pivot.setTranslateY(targetY);
        pivot.setTranslateZ(targetZ);

        camera.setTranslateX(0);
        camera.setTranslateY(0);
        camera.setTranslateZ(-distance);
    }

    // -------------- WASD loop (smooth movement) ----------------
    private void startWASDNavigationLoop() {
        navTimer = new AnimationTimer() {
            private long lastNs = 0;

            @Override
            public void handle(long now) {
                if (lastNs == 0) {
                    lastNs = now;
                    return;
                }

                double dt = (now - lastNs) / 1_000_000_000.0;
                lastNs = now;

                // Movement speed depends on zoom distance (nice feel)
                double base = Math.max(80, distance * 0.20); // units/sec
                if (shiftDown) {
                    base *= 2.2;
                }

                double move = base * dt;

                boolean changed = false;

                // Move in camera-facing direction on XZ plane based on yaw
                double yawRad = Math.toRadians(yaw.getAngle());
                double forwardX = Math.sin(yawRad);
                double forwardZ = Math.cos(yawRad);
                double rightX = Math.cos(yawRad);
                double rightZ = -Math.sin(yawRad);

                if (wDown) {
                    targetX += forwardX * move;
                    targetZ += forwardZ * move;
                    changed = true;
                }
                if (sDown) {
                    targetX -= forwardX * move;
                    targetZ -= forwardZ * move;
                    changed = true;
                }
                if (dDown) {
                    targetX += rightX * move;
                    targetZ += rightZ * move;
                    changed = true;
                }
                if (aDown) {
                    targetX -= rightX * move;
                    targetZ -= rightZ * move;
                    changed = true;
                }

                // Up/down (Q/E)
                if (qDown) {
                    targetY -= move * 0.6;
                    changed = true;
                }
                if (eDown) {
                    targetY += move * 0.6;
                    changed = true;
                }

                if (changed) {
                    applyCameraRig();
                }
            }
        };
        navTimer.start();
    }

    // Focus camera on specific furniture item
    public void focusOnItem(FurnitureItem it) {
        if (it == null || model.room == null) {
            return;
        }

        double roomW = model.room.width * S;
        double roomD = model.room.depth * S;

        // Convert item world position to centered 3D coordinates
        double cx = (it.x * S) - roomW / 2;
        double cz = (it.z * S) - roomD / 2;

        // Center camera pivot on item
        targetX = cx;
        targetZ = cz;

        // Slightly above floor
        targetY = -((it.height * it.scale) * S) / 2;

        // Adjust distance based on item size (nice UX)
        double maxSize = Math.max(it.width * it.scale, it.depth * it.scale);
        distance = Math.max(400, maxSize * S * 4);

        applyCameraRig();
    }

    // -------------- Hover Highlight ----------------
    private void installHoverHighlight() {
        subScene.setOnMouseMoved(e -> {
            PickResult pr = e.getPickResult();
            if (pr == null) {
                clearHoverHighlight();
                return;
            }

            Node n = pr.getIntersectedNode();
            if (!(n instanceof Box box)) {
                clearHoverHighlight();
                return;
            }

            // Only highlight furniture boxes (not floor/grid/walls)
            if (!furnitureNodes.containsValue(box)) {
                clearHoverHighlight();
                return;
            }

            if (hoveredBox == box) {
                return; // already highlighted
            }
            clearHoverHighlight();
            hoveredBox = box;

            PhongMaterial current = (PhongMaterial) box.getMaterial();
            if (current == null) {
                current = new PhongMaterial(Color.LIGHTBLUE);
            }

            // Save original
            originalMaterials.put(box, current);

            // Create highlight material (brighter + slight emissive)
            PhongMaterial hi = new PhongMaterial();
            hi.setDiffuseColor(brighten(current.getDiffuseColor(), 1.25));
            hi.setSpecularColor(Color.WHITE);
            hi.setSpecularPower(64);

            box.setMaterial(hi);
        });

        subScene.setOnMouseExited(e -> clearHoverHighlight());
        subScene.setOnMouseClicked(e -> {
            // Double click still resets view (your existing handler may do this elsewhere)
            // So only handle single click here.
            if (e.getClickCount() != 1) {
                return;
            }

            PickResult pr = e.getPickResult();
            if (pr == null) {
                return;
            }

            Node n = pr.getIntersectedNode();
            if (!(n instanceof Box box)) {
                return;
            }

            // Only furniture
            if (!furnitureNodes.containsValue(box)) {
                return;
            }

            // Find the ID for this box
            String clickedId = null;
            for (Map.Entry<String, Box> entry : furnitureNodes.entrySet()) {
                if (entry.getValue() == box) {
                    clickedId = entry.getKey();
                    break;
                }
            }

            if (clickedId != null && onFurnitureClicked != null) {
                onFurnitureClicked.accept(clickedId);
            }
        });
    }

    private void clearHoverHighlight() {
        if (hoveredBox == null) {
            return;
        }

        PhongMaterial orig = originalMaterials.get(hoveredBox);
        if (orig != null) {
            hoveredBox.setMaterial(orig);
        }

        hoveredBox = null;
    }

    private Color brighten(Color c, double factor) {
        double r = clamp01(c.getRed() * factor);
        double g = clamp01(c.getGreen() * factor);
        double b = clamp01(c.getBlue() * factor);
        return new Color(r, g, b, c.getOpacity());
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    // Convert ARGB int (0xAARRGGBB) to JavaFX Color
    private Color argbToFxColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;
        return Color.rgb(r, g, b, a / 255.0);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private FurnitureItem findItemById(String id) {
        if (id == null) {
            return null;
        }
        for (FurnitureItem it : model.items) {
            if (id.equals(it.id)) {
                return it;
            }
        }
        return null;
    }

    private void constrainInsideRoom3D(FurnitureItem it) {
        if (model.room == null) {
            return;
        }

        double sc = (it.scale <= 0) ? 1.0 : it.scale;
        double hw = (it.width * sc) / 2.0;
        double hd = (it.depth * sc) / 2.0;

        it.x = Math.max(hw, Math.min(model.room.width - hw, it.x));
        it.z = Math.max(hd, Math.min(model.room.depth - hd, it.z));

        // y just clamp above floor
        it.y = Math.max(0, it.y);
    }

}

package com.nextgenware.FurnitureVisualizer;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.*;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Rotate;

import javax.swing.*;
import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Fx3DPanel extends JPanel {

    private final LayoutModel model;
    private final JFXPanel fxPanel = new JFXPanel();

    // Root of 3D world
    private final Group root3d = new Group();

    // Room nodes (so we can rebuild/update)
    private final Group roomGroup = new Group();

    // Furniture nodes by ID
    private final Map<String, Box> furnitureNodes = new HashMap<>();

    private SubScene subScene;
    private PerspectiveCamera camera;

    // Navigation rig:
    // world -> pivot (target) -> yaw -> pitch -> camera
    private final Group pivot = new Group();
    private final Rotate yaw = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(-25, Rotate.X_AXIS);

    // Target (orbit center) in world units
    private double targetX = 0;
    private double targetY = -120; // slightly above floor (negative is up in this setup)
    private double targetZ = 0;

    // Orbit distance (camera dolly)
    private double distance = 900;

    // Scale: 1 meter = 100 JavaFX units
    private static final double S = 100.0;

    // Track previous room state (so we rebuild only if needed)
    private double lastRoomW = -1;
    private double lastRoomD = -1;
    private double lastRoomH = -1;
    private int lastFloorColor = 0;
    private int lastWallColor = 0;

    public Fx3DPanel(LayoutModel model) {
        super(new BorderLayout());
        this.model = model;

        add(fxPanel, BorderLayout.CENTER);

        // Initialize JavaFX scene
        Platform.runLater(this::initFx);

        // Re-render when model changes
        model.addListener(() -> Platform.runLater(this::syncFromModel));
    }

    // -------------- JavaFX init ----------------
    private void initFx() {
        subScene = new SubScene(root3d, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#f6f6f6"));

        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(5000);

        // Build navigation rig
        pivot.getTransforms().addAll(yaw, pitch);
        pivot.getChildren().add(camera);
        root3d.getChildren().add(pivot);

        // Room group inside world
        root3d.getChildren().add(roomGroup);

        // Initial camera state (distance is used)
        resetView();

        subScene.setCamera(camera);

        Group uiRoot = new Group(subScene);
        Scene scene = new Scene(uiRoot);
        fxPanel.setScene(scene);

        // Keep SubScene size synced to Swing panel size
        fxPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                Platform.runLater(() -> {
                    subScene.setWidth(fxPanel.getWidth());
                    subScene.setHeight(fxPanel.getHeight());
                });
            }
        });

        addLights();
        buildOrUpdateRoom(true);
        syncFromModel();
        install3dNavigationControls();
    }

    private void addLights() {
        AmbientLight amb = new AmbientLight(Color.color(0.85, 0.85, 0.85));

        PointLight key = new PointLight(Color.WHITE);
        key.setTranslateY(-600);
        key.setTranslateZ(-600);

        root3d.getChildren().addAll(amb, key);
    }

    // Rebuild room if size/colors changed
    private void buildOrUpdateRoom(boolean force) {
        if (model.room == null) {
            return;
        }

        double w = model.room.width * S;
        double d = model.room.depth * S;
        double h = model.room.height * S;

        int floorCol = model.room.floorColor;
        int wallCol = model.room.wallColor;

        boolean changed
                = force
                || w != lastRoomW || d != lastRoomD || h != lastRoomH
                || floorCol != lastFloorColor || wallCol != lastWallColor;

        if (!changed) {
            return;
        }

        lastRoomW = w;
        lastRoomD = d;
        lastRoomH = h;
        lastFloorColor = floorCol;
        lastWallColor = wallCol;

        roomGroup.getChildren().clear();

        PhongMaterial floorMat = new PhongMaterial(argbToFxColor(floorCol));
        PhongMaterial wallMat = new PhongMaterial(argbToFxColor(wallCol));

        // Floor
        Box floor = new Box(w, 5, d);
        floor.setTranslateY(0);
        floor.setMaterial(floorMat);

        // Walls (thin boxes)
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

        // Optional: auto-center target to room center on rebuild
        // (Keeps navigation feeling correct after room resize)
        targetX = 0;
        targetZ = 0;
        applyCameraRig();
    }

    // -------------- Sync model -> 3D ----------------
    private void syncFromModel() {
        if (model.room == null) {
            return;
        }

        buildOrUpdateRoom(false);

        double roomW = model.room.width * S;
        double roomD = model.room.depth * S;

        // Track IDs that still exist (for removal)
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

            // Apply scale to size
            double sc = (it.scale <= 0) ? 1.0 : it.scale;
            b.setWidth((it.width * sc) * S);
            b.setDepth((it.depth * sc) * S);
            b.setHeight((it.height * sc) * S);

            // Position (center room at origin)
            double cx = (it.x * S) - roomW / 2;
            double cz = (it.z * S) - roomD / 2;

            b.setTranslateX(cx);
            b.setTranslateZ(cz);

            // Sit on floor (use scaled height)
            b.setTranslateY(-((it.height * sc) * S) / 2);

            // Rotation around Y axis
            b.getTransforms().removeIf(t -> t instanceof Rotate);
            b.getTransforms().add(new Rotate(it.rotationDeg, Rotate.Y_AXIS));
        }

        // Remove deleted furniture nodes
        furnitureNodes.entrySet().removeIf(entry -> {
            String id = entry.getKey();
            if (!alive.contains(id)) {
                root3d.getChildren().remove(entry.getValue());
                return true;
            }
            return false;
        });
    }

    // -------------- 3D Navigation Controls ----------------
    private void install3dNavigationControls() {
        final double[] last = new double[2];

        subScene.setOnMousePressed(e -> {
            last[0] = e.getSceneX();
            last[1] = e.getSceneY();
        });

        subScene.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - last[0];
            double dy = e.getSceneY() - last[1];
            last[0] = e.getSceneX();
            last[1] = e.getSceneY();

            boolean panMode = (e.getButton() == MouseButton.SECONDARY) || e.isShiftDown();

            if (!panMode) {
                // ORBIT (Left drag)
                yaw.setAngle(yaw.getAngle() + dx * 0.35);
                pitch.setAngle(clamp(pitch.getAngle() - dy * 0.25, -80, -10));
            } else {
                // PAN (Right drag OR Shift+Left)
                // Pan scale depends on distance for consistent feel
                double panScale = Math.max(0.2, distance / 1200.0);

                // Move target in X/Z plane (screen-space-ish)
                // dx -> left/right, dy -> forward/back
                targetX += (-dx * panScale);
                targetZ += (-dy * panScale);
            }

            applyCameraRig();
        });

        subScene.setOnScroll(e -> {
            // ZOOM (wheel)
            double delta = e.getDeltaY();

            // Positive delta => zoom in => smaller distance
            distance = clamp(distance - delta * 0.9, 250, 2500);
            applyCameraRig();
        });

        subScene.setOnMouseClicked(e -> {
            // Double-click to reset view
            if (e.getClickCount() == 2) {
                resetView();
            }
        });
    }

    private void resetView() {
        yaw.setAngle(25);
        pitch.setAngle(-25);

        // Center target at room center (origin)
        targetX = 0;
        targetZ = 0;

        // Slightly above floor
        targetY = -120;

        // Default distance
        distance = 900;

        applyCameraRig();
    }

    private void applyCameraRig() {
        // Move pivot to orbit target
        pivot.setTranslateX(targetX);
        pivot.setTranslateY(targetY);
        pivot.setTranslateZ(targetZ);

        // Camera sits at -distance in Z of pivot (because pivot rotates)
        camera.setTranslateX(0);
        camera.setTranslateY(0);
        camera.setTranslateZ(-distance);
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
}

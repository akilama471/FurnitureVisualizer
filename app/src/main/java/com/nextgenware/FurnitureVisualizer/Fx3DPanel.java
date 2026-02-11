package com.nextgenware.FurnitureVisualizer;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.*;
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

    // World contains room + furniture
    private final Group root3d = new Group();
    private final Group world = new Group();
    private final Group roomGroup = new Group();

    private final Map<String, Box> furnitureNodes = new HashMap<>();

    private SubScene subScene;
    private PerspectiveCamera camera;

    // World transforms (navigation)
    private final Rotate worldRotY = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate worldRotX = new Rotate(-25, Rotate.X_AXIS);

    // Zoom (camera distance)
    private double cameraDistance = 1200;

    // Scale: 1 meter = 100 JavaFX units
    private static final double S = 100.0;

    // Track room state (rebuild when changes)
    private double lastRoomW = -1;
    private double lastRoomD = -1;
    private double lastRoomH = -1;
    private int lastFloorColor = 0;
    private int lastWallColor = 0;

    public Fx3DPanel(LayoutModel model) {
        super(new BorderLayout());
        this.model = model;

        add(fxPanel, BorderLayout.CENTER);

        Platform.runLater(this::initFx);
        model.addListener(() -> Platform.runLater(this::syncFromModel));
    }

    private void initFx() {
        subScene = new SubScene(root3d, 800, 600, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.web("#f6f6f6"));

        // Build graph
        world.getChildren().add(roomGroup);
        world.getTransforms().addAll(worldRotY, worldRotX);
        root3d.getChildren().add(world);

        // Camera (IMPORTANT: Z must be POSITIVE to look at origin)
        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(20000);
        camera.setTranslateY(-700);           // lift camera up
        camera.setTranslateZ(cameraDistance); // POSITIVE Z

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

        addLights();
        buildOrUpdateRoom(true);
        syncFromModel();
        install3DNavigation();
    }

    private void addLights() {
        AmbientLight amb = new AmbientLight(Color.color(0.85, 0.85, 0.85));

        PointLight key = new PointLight(Color.WHITE);
        key.setTranslateY(-600);
        key.setTranslateZ(800);  // in front of room

        root3d.getChildren().addAll(amb, key);
    }

    private void buildOrUpdateRoom(boolean force) {
        if (model.room == null) return;

        double w = model.room.width * S;
        double d = model.room.depth * S;
        double h = model.room.height * S;

        int floorCol = model.room.floorColor;
        int wallCol = model.room.wallColor;

        boolean changed =
                force ||
                w != lastRoomW || d != lastRoomD || h != lastRoomH ||
                floorCol != lastFloorColor || wallCol != lastWallColor;

        if (!changed) return;

        lastRoomW = w;
        lastRoomD = d;
        lastRoomH = h;
        lastFloorColor = floorCol;
        lastWallColor = wallCol;

        roomGroup.getChildren().clear();

        PhongMaterial floorMat = new PhongMaterial(argbToFxColor(floorCol));
        PhongMaterial wallMat  = new PhongMaterial(argbToFxColor(wallCol));

        // Floor centered at origin
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
    }

    private void syncFromModel() {
        if (model.room == null) return;

        buildOrUpdateRoom(false);

        double roomW = model.room.width * S;
        double roomD = model.room.depth * S;

        // remove deleted nodes
        Set<String> alive = new HashSet<>();
        for (FurnitureItem it : model.items) alive.add(it.id);

        furnitureNodes.entrySet().removeIf(entry -> {
            if (!alive.contains(entry.getKey())) {
                world.getChildren().remove(entry.getValue());
                return true;
            }
            return false;
        });

        for (FurnitureItem it : model.items) {
            Box b = furnitureNodes.get(it.id);

            if (b == null) {
                b = new Box(1, 1, 1);
                furnitureNodes.put(it.id, b);
                world.getChildren().add(b);
            }

            // Color
            PhongMaterial mat = (PhongMaterial) b.getMaterial();
            if (mat == null) mat = new PhongMaterial();
            mat.setDiffuseColor(argbToFxColor(it.color));
            b.setMaterial(mat);

            // Scale
            double sc = (it.scale <= 0) ? 1.0 : it.scale;

            // Size
            b.setWidth((it.width * sc) * S);
            b.setDepth((it.depth * sc) * S);
            b.setHeight((it.height * sc) * S);

            // Position: convert world meters to centered origin
            double cx = (it.x * S) - roomW / 2;
            double cz = (it.z * S) - roomD / 2;

            b.setTranslateX(cx);
            b.setTranslateZ(cz);

            // Sit on floor
            b.setTranslateY(-((it.height * sc) * S) / 2);

            // Rotation Y
            b.getTransforms().removeIf(t -> t instanceof Rotate);
            b.getTransforms().add(new Rotate(it.rotationDeg, Rotate.Y_AXIS));
        }
    }

    // --------- 3D NAVIGATION ----------
    private void install3DNavigation() {
        final double[] last = new double[2];

        final double orbitSpeed = 0.35;
        final double panSpeed = 1.0;
        final double zoomSpeed = 1.2;

        subScene.setOnMousePressed(e -> {
            last[0] = e.getSceneX();
            last[1] = e.getSceneY();
        });

        subScene.setOnMouseDragged(e -> {
            double x = e.getSceneX();
            double y = e.getSceneY();
            double dx = x - last[0];
            double dy = y - last[1];
            last[0] = x;
            last[1] = y;

            boolean panMode = e.isSecondaryButtonDown() || e.isShiftDown();

            if (panMode) {
                // PAN: move world in X/Y
                world.setTranslateX(world.getTranslateX() + dx * panSpeed);
                world.setTranslateY(world.getTranslateY() + dy * panSpeed);
            } else {
                // ORBIT: rotate world
                worldRotY.setAngle(worldRotY.getAngle() + dx * orbitSpeed);
                worldRotX.setAngle(clamp(worldRotX.getAngle() - dy * orbitSpeed, -80, -5));
            }
        });

        subScene.setOnScroll(e -> {
            double delta = e.getDeltaY();
            cameraDistance = clamp(cameraDistance - delta * zoomSpeed, 200, 8000);
            camera.setTranslateZ(cameraDistance); // POSITIVE Z
        });
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private Color argbToFxColor(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;
        return Color.rgb(r, g, b, a / 255.0);
    }
}

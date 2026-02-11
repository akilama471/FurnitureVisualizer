package com.nextgenware.FurnitureVisualizer;

import com.nextgenware.FurnitureVisualizer.model.*;
import com.nextgenware.FurnitureVisualizer.undo.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

public class Editor2DPanel extends JPanel {

    private final LayoutModel model;
    private final CommandManager cmd;

    // Catalog placement
    private FurnitureTemplate activeTemplate = null;

    // Selection (single + multi)
    private final List<FurnitureItem> selection = new ArrayList<>();
    private FurnitureItem primarySelected = null;

    // Drag state (group drag)
    private List<FurnitureItem> dragItems = null;
    private List<FurnitureSnapshot> dragStartSnapshots = null;
    private double dragStartMouseX, dragStartMouseZ;

    // Clipboard for copy/paste
    private final List<FurnitureSnapshot> clipboard = new ArrayList<>();
    private final List<String> clipboardTypes = new ArrayList<>();

    // Snap settings
    private boolean snapToGrid = true;
    private final double gridSize = 0.1; // 10cm
    private boolean snapToWalls = true;
    private final double wallSnapThreshold = 0.05; // 5cm

    // Guides (visual)
    private boolean showGuides = true;
    private Double guideX = null;
    private Double guideZ = null;
    private final double guideThreshold = 0.05; // 5cm

    // Collision warning
    private String warningText = "";

    private static final double MARGIN = 40;

    private java.util.function.Consumer<FurnitureItem> onSelectionChanged;

    public Editor2DPanel(LayoutModel model, CommandManager cmd) {
        this.model = model;
        this.cmd = cmd;

        setBackground(Color.WHITE);
        model.addListener(this::repaint);

        installMouseHandlers();
        installKeyHandlers();
    }

    // ---------------- Public API ----------------
    public void setActiveTemplate(FurnitureTemplate t) {
        this.activeTemplate = t;
    }

    public void setOnSelectionChanged(java.util.function.Consumer<FurnitureItem> cb) {
        this.onSelectionChanged = cb;
    }

    public void setSnapToGrid(boolean enabled) {
        this.snapToGrid = enabled;
        repaint();
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }

    public void setSnapToWalls(boolean enabled) {
        this.snapToWalls = enabled;
    }

    public boolean isSnapToWalls() {
        return snapToWalls;
    }

    public void setShowGuides(boolean enabled) {
        this.showGuides = enabled;
        repaint();
    }

    public FurnitureItem getPrimarySelected() {
        return primarySelected;
    }

    public List<FurnitureItem> getSelection() {
        return new ArrayList<>(selection);
    }

    public void rotateSelected(double deltaDeg) {
        if (primarySelected == null) {
            return;
        }

        FurnitureSnapshot before = FurnitureSnapshot.from(primarySelected);

        primarySelected.rotationDeg = (primarySelected.rotationDeg + deltaDeg) % 360;
        if (primarySelected.rotationDeg < 0) {
            primarySelected.rotationDeg += 360;
        }

        FurnitureSnapshot after = FurnitureSnapshot.from(primarySelected);

        cmd.doCommand(new FurnitureStateCommand(model, primarySelected, before, after, "Rotate"));
    }

    public void deleteSelected() {
        if (selection.isEmpty()) {
            return;
        }

        List<FurnitureItem> toDelete = new ArrayList<>(selection);
        clearSelection();

        cmd.doCommand(new DeleteManyFurnitureCommand(model, toDelete));
    }

    public void copySelection() {
        clipboard.clear();
        clipboardTypes.clear();
        for (FurnitureItem it : selection) {
            clipboard.add(FurnitureSnapshot.from(it));
            clipboardTypes.add(it.type);
        }
    }

    public void pasteClipboard() {
        if (clipboard.isEmpty()) {
            return;
        }

        double offset = 0.2; // meters
        List<FurnitureItem> newItems = new ArrayList<>();

        for (int i = 0; i < clipboard.size(); i++) {
            FurnitureSnapshot s = clipboard.get(i);
            String type = clipboardTypes.get(i);

            FurnitureItem it = new FurnitureItem(
                    model.nextId(),
                    type,
                    s.w, s.d, s.h,
                    s.x + offset, s.z + offset,
                    s.rot
            );
            it.scale = s.scale;      // ADD
            it.color = s.color;

            constrainInsideRoom(it);
            newItems.add(it);
        }

        cmd.doCommand(new AddManyFurnitureCommand(model, newItems));

        // Select pasted items
        selection.clear();
        selection.addAll(newItems);
        primarySelected = newItems.get(newItems.size() - 1);
        notifySelectionChanged();
    }

    // ---------------- Input ----------------
    private void installKeyHandlers() {
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_Q) {
                    rotateSelected(-15);
                }
                if (e.getKeyCode() == KeyEvent.VK_E) {
                    rotateSelected(+15);
                }
            }
        });
    }

    private void installMouseHandlers() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();

                FurnitureItem hit = findItemAt(e.getX(), e.getY());
                boolean shift = e.isShiftDown();

                // 1) Click empty + template selected => add new item
                if (hit == null && activeTemplate != null) {
                    double[] world = screenToWorld(e.getX(), e.getY());

                    FurnitureItem newItem = new FurnitureItem(
                            model.nextId(),
                            activeTemplate.type,
                            activeTemplate.width,
                            activeTemplate.depth,
                            activeTemplate.height,
                            world[0],
                            world[1],
                            0
                    );

                    if (snapToGrid) {
                        newItem.x = snap(newItem.x, gridSize);
                        newItem.z = snap(newItem.z, gridSize);
                    }
                    if (snapToWalls) {
                        snapItemToWalls(newItem);
                    }

                    constrainInsideRoom(newItem);

                    cmd.doCommand(new AddFurnitureCommand(model, newItem));

                    setSingleSelection(newItem);
                    beginDrag(world[0], world[1]); // allow immediate drag after placing
                    repaint();
                    return;
                }

                // 2) Otherwise: selection behavior
                if (hit != null) {
                    if (shift) {
                        toggleSelection(hit);
                    } else {
                        setSingleSelection(hit);
                    }
                } else {
                    if (!shift) {
                        clearSelection();
                    }
                }

                // 3) Start drag if we have selection
                if (primarySelected != null) {
                    double[] world = screenToWorld(e.getX(), e.getY());
                    beginDrag(world[0], world[1]);
                }

                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                commitDragCommand();
                dragItems = null;
                dragStartSnapshots = null;

                // Clear guides after drag
                guideX = guideZ = null;
                repaint();
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragItems == null || dragStartSnapshots == null || primarySelected == null) {
                    return;
                }

                double[] world = screenToWorld(e.getX(), e.getY());
                double dx = world[0] - dragStartMouseX;
                double dz = world[1] - dragStartMouseZ;

                for (int i = 0; i < dragItems.size(); i++) {
                    FurnitureItem it = dragItems.get(i);
                    FurnitureSnapshot start = dragStartSnapshots.get(i);

                    it.x = start.x + dx;
                    it.z = start.z + dz;

                    if (snapToGrid) {
                        it.x = snap(it.x, gridSize);
                        it.z = snap(it.z, gridSize);
                    }
                    if (snapToWalls) {
                        snapItemToWalls(it);
                    }

                    constrainInsideRoom(it);
                }

                // Collision warning based on primary item
                if (isColliding(primarySelected)) {
                    warningText = "⚠ Furniture overlaps another item";
                } else {
                    warningText = "";
                }

                if (showGuides) {
                    computeGuides(primarySelected);
                } else {
                    guideX = guideZ = null;
                }

                model.notifyChanged();
            }
        });
    }

    private void beginDrag(double mouseWorldX, double mouseWorldZ) {
        dragStartMouseX = mouseWorldX;
        dragStartMouseZ = mouseWorldZ;

        dragItems = new ArrayList<>(selection);
        dragStartSnapshots = new ArrayList<>();
        for (FurnitureItem it : dragItems) {
            dragStartSnapshots.add(FurnitureSnapshot.from(it));
        }
    }

    private void commitDragCommand() {
        if (dragItems == null || dragStartSnapshots == null || dragItems.isEmpty()) {
            return;
        }

        List<MultiFurnitureStateCommand.Entry> entries = new ArrayList<>();
        boolean changed = false;

        for (int i = 0; i < dragItems.size(); i++) {
            FurnitureItem it = dragItems.get(i);
            FurnitureSnapshot before = dragStartSnapshots.get(i);
            FurnitureSnapshot after = FurnitureSnapshot.from(it);

            if (before.x != after.x || before.z != after.z) {
                changed = true;
            }
            entries.add(new MultiFurnitureStateCommand.Entry(it, before, after));
        }

        if (changed) {
            // drag already applied; store only (no re-execute)
            cmd.pushExecutedCommand(new MultiFurnitureStateCommand(model, entries, "Move Selection"));
        }
    }

    // ---------------- Drawing ----------------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double scale = getScale();
        int roomW = (int) (model.room.width * scale);
        int roomH = (int) (model.room.depth * scale);
        int x0 = (getWidth() - roomW) / 2;
        int y0 = (getHeight() - roomH) / 2;

        // Room
        g2.setColor(new Color(model.room.floorColor, true));
        g2.fillRect(x0, y0, roomW, roomH);

        g2.setColor(new Color(model.room.wallColor, true));
        g2.drawRect(x0, y0, roomW, roomH);

        // Grid
        if (snapToGrid) {
            drawGrid(g2, x0, y0, roomW, roomH, scale);
        }

        // Guides
        if (showGuides) {
            drawGuides(g2, x0, y0, roomW, roomH, scale);
        }

        // Furniture
        for (FurnitureItem it : model.items) {
            boolean isSel = selection.contains(it);
            boolean isPrimary = (it == primarySelected);

            double px = x0 + it.x * scale;
            double py = y0 + it.z * scale;
            double fw = (it.width * it.scale) * scale;
            double fd = (it.depth * it.scale) * scale;

            AffineTransform old = g2.getTransform();
            Stroke oldStroke = g2.getStroke();

            g2.translate(px, py);
            g2.rotate(Math.toRadians(it.rotationDeg));

            // fill
            Color base = new Color(it.color, true);
            g2.setColor(base);

            g2.fillRect((int) (-fw / 2), (int) (-fd / 2), (int) fw, (int) fd);

            // border
            boolean colliding = isPrimary && isColliding(it);
            g2.setColor(colliding ? Color.RED : Color.BLUE.darker());
            g2.setStroke(isPrimary ? new BasicStroke(3f) : new BasicStroke(1f));
            g2.drawRect((int) (-fw / 2), (int) (-fd / 2), (int) fw, (int) fd);

            g2.setStroke(oldStroke);
            g2.setTransform(old);
        }

        if (!warningText.isEmpty()) {
            g2.setColor(Color.RED.darker());
            g2.drawString(warningText, 20, 20);
        }

        g2.dispose();
    }

    // ---------------- Geometry helpers ----------------
    private double getScale() {
        double w = getWidth() - MARGIN * 2;
        double h = getHeight() - MARGIN * 2;
        return Math.min(w / model.room.width, h / model.room.depth);
    }

    private double[] screenToWorld(int sx, int sy) {
        double scale = getScale();

        int roomW = (int) (model.room.width * scale);
        int roomH = (int) (model.room.depth * scale);
        int x0 = (getWidth() - roomW) / 2;
        int y0 = (getHeight() - roomH) / 2;

        double wx = (sx - x0) / scale;
        double wz = (sy - y0) / scale;
        return new double[]{wx, wz};
    }

    private FurnitureItem findItemAt(int sx, int sy) {
        double[] world = screenToWorld(sx, sy);
        double wx = world[0];
        double wz = world[1];

        // Topmost selection preference: iterate reverse
        for (int i = model.items.size() - 1; i >= 0; i--) {
            FurnitureItem it = model.items.get(i);
            double hw = (it.width * it.scale) / 2;
            double hd = (it.depth * it.scale) / 2;
            if (Math.abs(wx - it.x) <= hw && Math.abs(wz - it.z) <= hd) {
                return it;
            }
        }
        return null;
    }

    private void constrainInsideRoom(FurnitureItem it) {
        double hw = (it.width * it.scale) / 2;
        double hd = (it.depth * it.scale) / 2;

        it.x = Math.max(hw, Math.min(model.room.width - hw, it.x));
        it.z = Math.max(hd, Math.min(model.room.depth - hd, it.z));
    }

    private double snap(double value, double step) {
        return Math.round(value / step) * step;
    }

    private void snapItemToWalls(FurnitureItem it) {
        double hw = (it.width * it.scale) / 2;
        double hd = (it.depth * it.scale) / 2;

        double leftTarget = hw;
        if (Math.abs(it.x - leftTarget) <= wallSnapThreshold) {
            it.x = leftTarget;
        }

        double rightTarget = model.room.width - hw;
        if (Math.abs(it.x - rightTarget) <= wallSnapThreshold) {
            it.x = rightTarget;
        }

        double topTarget = hd;
        if (Math.abs(it.z - topTarget) <= wallSnapThreshold) {
            it.z = topTarget;
        }

        double bottomTarget = model.room.depth - hd;
        if (Math.abs(it.z - bottomTarget) <= wallSnapThreshold) {
            it.z = bottomTarget;
        }
    }

    private void drawGrid(Graphics2D g2, int x0, int y0, int roomW, int roomH, double scale) {
        g2.setColor(new Color(model.room.gridColor, true));
        double stepPx = gridSize * scale;
        if (stepPx < 6) {
            return;
        }

        for (double x = x0; x <= x0 + roomW; x += stepPx) {
            g2.drawLine((int) x, y0, (int) x, y0 + roomH);
        }
        for (double y = y0; y <= y0 + roomH; y += stepPx) {
            g2.drawLine(x0, (int) y, x0 + roomW, (int) y);
        }
    }

    // ---------------- Collision ----------------
    private boolean overlaps(FurnitureItem a, FurnitureItem b) {
        double aw = (a.width * a.scale) / 2;
        double ad = (a.depth * a.scale) / 2;
        double bw = (b.width * b.scale) / 2;
        double bd = (b.depth * b.scale) / 2;

        return Math.abs(a.x - b.x) < (aw + bw) && Math.abs(a.z - b.z) < (ad + bd);

    }

    private boolean isColliding(FurnitureItem it) {
        for (FurnitureItem other : model.items) {
            if (other == it) {
                continue;
            }
            if (overlaps(it, other)) {
                return true;
            }
        }
        return false;
    }

    // ---------------- Selection helpers ----------------
    private void clearSelection() {
        selection.clear();
        primarySelected = null;
        notifySelectionChanged();
    }

    private void setSingleSelection(FurnitureItem it) {
        selection.clear();
        if (it != null) {
            selection.add(it);
        }
        primarySelected = it;
        notifySelectionChanged();
    }

    private void toggleSelection(FurnitureItem it) {
        if (it == null) {
            return;
        }

        if (selection.contains(it)) {
            selection.remove(it);
        } else {
            selection.add(it);
        }

        primarySelected = selection.isEmpty() ? null : selection.get(selection.size() - 1);
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(primarySelected);
        }
    }

    // ---------------- Guides ----------------
    private void computeGuides(FurnitureItem it) {
        guideX = null;
        guideZ = null;

        double bestDx = guideThreshold + 1;
        double bestDz = guideThreshold + 1;

        double hw = (it.width * it.scale) / 2.0;
        double hd = (it.depth * it.scale) / 2.0;

        double[] candX = new double[]{hw, model.room.width / 2, model.room.width - hw};
        double[] candZ = new double[]{hd, model.room.depth / 2, model.room.depth - hd};

        for (double cx : candX) {
            double dx = Math.abs(it.x - cx);
            if (dx < bestDx) {
                bestDx = dx;
                guideX = cx;
            }
        }
        for (double cz : candZ) {
            double dz = Math.abs(it.z - cz);
            if (dz < bestDz) {
                bestDz = dz;
                guideZ = cz;
            }
        }

        for (FurnitureItem other : model.items) {
            if (other == it) {
                continue;
            }

            double dx = Math.abs(it.x - other.x);
            if (dx < bestDx && dx <= guideThreshold) {
                bestDx = dx;
                guideX = other.x;
            }

            double dz = Math.abs(it.z - other.z);
            if (dz < bestDz && dz <= guideThreshold) {
                bestDz = dz;
                guideZ = other.z;
            }
        }

        if (guideX != null && Math.abs(it.x - guideX) > guideThreshold) {
            guideX = null;
        }
        if (guideZ != null && Math.abs(it.z - guideZ) > guideThreshold) {
            guideZ = null;
        }
    }

    private void drawGuides(Graphics2D g2, int x0, int y0, int roomW, int roomH, double scale) {
        if (guideX == null && guideZ == null) {
            return;
        }

        g2.setColor(new Color(0, 150, 0, 160));

        if (guideX != null) {
            int px = (int) Math.round(x0 + guideX * scale);
            g2.drawLine(px, y0, px, y0 + roomH);
        }
        if (guideZ != null) {
            int py = (int) Math.round(y0 + guideZ * scale);
            g2.drawLine(x0, py, x0 + roomW, py);
        }
    }
}

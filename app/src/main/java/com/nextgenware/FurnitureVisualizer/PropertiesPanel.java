package com.nextgenware.FurnitureVisualizer;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;
import com.nextgenware.FurnitureVisualizer.model.Room;
import com.nextgenware.FurnitureVisualizer.undo.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class PropertiesPanel extends JPanel {

    private final LayoutModel model;
    private final Editor2DPanel editor;
    private final CommandManager cmd;

    private FurnitureItem current;

    // Furniture baseline snapshot (for undo)
    private FurnitureSnapshot baseline;
    private Timer debounceTimer;

    // Room baseline snapshot (for undo)
    private RoomSnapshot roomBaseline;
    private Timer roomDebounceTimer;

    private boolean updatingUI = false;

    // ---- Room fields ----
    private final JTextField txtRoomW = new JTextField();
    private final JTextField txtRoomD = new JTextField();
    private final JTextField txtRoomH = new JTextField();
    private final JComboBox<Room.Shape> cmbShape = new JComboBox<>(Room.Shape.values());

    private final JButton btnFloorColor = new JButton("Pick");
    private final JButton btnWallColor  = new JButton("Pick");
    private final JButton btnGridColor  = new JButton("Pick");

    // ---- Furniture fields ----
    private final JTextField txtType = new JTextField();
    private final JTextField txtX = new JTextField();
    private final JTextField txtZ = new JTextField();
    private final JTextField txtW = new JTextField();
    private final JTextField txtD = new JTextField();
    private final JTextField txtH = new JTextField();
    private final JTextField txtRot = new JTextField();

    private final JSpinner spScale = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 5.0, 0.1));
    private final JButton btnItemColor = new JButton("Pick");

    public PropertiesPanel(LayoutModel model, Editor2DPanel editor, CommandManager cmd) {
        this.model = model;
        this.editor = editor;
        this.cmd = cmd;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.NORTH);

        txtType.setEditable(false);

        // Debounce furniture edits (typing)
        debounceTimer = new Timer(600, e -> commitFurnitureChange("Edit Properties"));
        debounceTimer.setRepeats(false);

        // Debounce room edits (typing)
        roomDebounceTimer = new Timer(600, e -> commitRoomChange("Edit Room"));
        roomDebounceTimer.setRepeats(false);

        // Keep UI in sync when model changes
        model.addListener(() -> SwingUtilities.invokeLater(() -> {
            refreshRoomUI();
            if (current != null) setSelectedItem(current);
        }));

        installFurnitureListeners();
        installRoomListeners();

        setEnabledFurnitureFields(false);
        refreshRoomUI();
        roomBaseline = RoomSnapshot.from(model.room);
    }

    private JPanel buildForm() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        int r = 0;

        // --- ROOM SECTION ---
        addSectionTitle(p, c, r++, "Room");
        addRow(p, c, r++, "Width (m)", txtRoomW);
        addRow(p, c, r++, "Depth (m)", txtRoomD);
        addRow(p, c, r++, "Height (m)", txtRoomH);
        addRow(p, c, r++, "Shape", cmbShape);

        addRow(p, c, r++, "Floor Color", btnFloorColor);
        addRow(p, c, r++, "Wall Color", btnWallColor);
        addRow(p, c, r++, "Grid Color", btnGridColor);

        // --- FURNITURE SECTION ---
        addSectionTitle(p, c, r++, "Furniture");
        addRow(p, c, r++, "Type", txtType);
        addRow(p, c, r++, "X (m)", txtX);
        addRow(p, c, r++, "Z (m)", txtZ);
        addRow(p, c, r++, "Width (m)", txtW);
        addRow(p, c, r++, "Depth (m)", txtD);
        addRow(p, c, r++, "Height (m)", txtH);
        addRow(p, c, r++, "Rotation (deg)", txtRot);

        addRow(p, c, r++, "Scale", spScale);
        addRow(p, c, r++, "Color", btnItemColor);

        return p;
    }

    private void addSectionTitle(JPanel p, GridBagConstraints c, int row, String title) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 1.0;
        c.gridwidth = 2;
        JLabel lbl = new JLabel(title);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 14f));
        p.add(lbl, c);
        c.gridwidth = 1;
    }

    private void addRow(JPanel p, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0.35;
        p.add(new JLabel(label), c);

        c.gridx = 1;
        c.gridy = row;
        c.weightx = 0.65;
        p.add(field, c);
    }

    // ---------------- ROOM ----------------

    private void refreshRoomUI() {
        updatingUI = true;

        txtRoomW.setText(String.valueOf(model.room.width));
        txtRoomD.setText(String.valueOf(model.room.depth));
        txtRoomH.setText(String.valueOf(model.room.height));
        cmbShape.setSelectedItem(model.room.shape);

        btnFloorColor.setBackground(new Color(model.room.floorColor, true));
        btnWallColor.setBackground(new Color(model.room.wallColor, true));
        btnGridColor.setBackground(new Color(model.room.gridColor, true));

        updatingUI = false;
    }

    private void installRoomListeners() {
        DocumentListener roomDL = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyRoom(); }
            @Override public void removeUpdate(DocumentEvent e) { applyRoom(); }
            @Override public void changedUpdate(DocumentEvent e) { applyRoom(); }
        };

        txtRoomW.getDocument().addDocumentListener(roomDL);
        txtRoomD.getDocument().addDocumentListener(roomDL);
        txtRoomH.getDocument().addDocumentListener(roomDL);

        cmbShape.addActionListener(e -> {
            if (updatingUI) return;
            RoomSnapshot before = RoomSnapshot.from(model.room);
            model.room.shape = (Room.Shape) cmbShape.getSelectedItem();
            model.notifyChanged();
            RoomSnapshot after = RoomSnapshot.from(model.room);
            cmd.doCommand(new RoomStateCommand(model, before, after, "Change Room Shape"));
            roomBaseline = after;
        });

        btnFloorColor.addActionListener(e -> pickRoomColor("Pick Floor Color", "floorColor"));
        btnWallColor.addActionListener(e -> pickRoomColor("Pick Wall Color", "wallColor"));
        btnGridColor.addActionListener(e -> pickRoomColor("Pick Grid Color", "gridColor"));
    }

    private void pickRoomColor(String title, String which) {
        Color initial = switch (which) {
            case "floorColor" -> new Color(model.room.floorColor, true);
            case "wallColor"  -> new Color(model.room.wallColor, true);
            default           -> new Color(model.room.gridColor, true);
        };

        Color chosen = JColorChooser.showDialog(this, title, initial);
        if (chosen == null) return;

        RoomSnapshot before = RoomSnapshot.from(model.room);

        int argb = (0xFF << 24) | (chosen.getRed() << 16) | (chosen.getGreen() << 8) | chosen.getBlue();
        if (which.equals("floorColor")) model.room.floorColor = argb;
        else if (which.equals("wallColor")) model.room.wallColor = argb;
        else model.room.gridColor = argb;

        model.notifyChanged();

        RoomSnapshot after = RoomSnapshot.from(model.room);
        cmd.doCommand(new RoomStateCommand(model, before, after, "Change Room Color"));
        roomBaseline = after;
    }

    private void applyRoom() {
        if (updatingUI) return;

        try {
            double w = Double.parseDouble(txtRoomW.getText().trim());
            double d = Double.parseDouble(txtRoomD.getText().trim());
            double h = Double.parseDouble(txtRoomH.getText().trim());

            if (w <= 0 || d <= 0 || h <= 0) return;

            // Apply live (debounced commit to undo)
            model.room.width = w;
            model.room.depth = d;
            model.room.height = h;

            // Keep items inside new room bounds
            for (FurnitureItem it : model.items) clampInsideRoomScaled(it);

            model.notifyChanged();
            roomDebounceTimer.restart();
        } catch (Exception ignored) {}
    }

    private void commitRoomChange(String label) {
        if (roomBaseline == null) roomBaseline = RoomSnapshot.from(model.room);
        RoomSnapshot after = RoomSnapshot.from(model.room);

        boolean changed =
                after.width != roomBaseline.width ||
                after.depth != roomBaseline.depth ||
                after.height != roomBaseline.height ||
                after.shape != roomBaseline.shape ||
                after.floorColor != roomBaseline.floorColor ||
                after.wallColor != roomBaseline.wallColor ||
                after.gridColor != roomBaseline.gridColor;

        if (changed) {
            cmd.doCommand(new RoomStateCommand(model, roomBaseline, after, label));
            roomBaseline = after;
        }
    }

    // ---------------- FURNITURE ----------------

    private void installFurnitureListeners() {
        DocumentListener dl = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFurniture(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFurniture(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFurniture(); }
        };

        txtX.getDocument().addDocumentListener(dl);
        txtZ.getDocument().addDocumentListener(dl);
        txtW.getDocument().addDocumentListener(dl);
        txtD.getDocument().addDocumentListener(dl);
        txtH.getDocument().addDocumentListener(dl);
        txtRot.getDocument().addDocumentListener(dl);

        spScale.addChangeListener(e -> {
            if (updatingUI || current == null) return;

            FurnitureSnapshot before = FurnitureSnapshot.from(current);

            double sc = ((Number) spScale.getValue()).doubleValue();
            if (sc <= 0) return;

            current.scale = sc;
            clampInsideRoomScaled(current);

            model.notifyChanged();
            editor.repaint();

            FurnitureSnapshot after = FurnitureSnapshot.from(current);
            cmd.doCommand(new FurnitureStateCommand(model, current, before, after, "Change Scale"));
            baseline = after;
        });

        btnItemColor.addActionListener(e -> {
            if (current == null) return;

            Color initial = new Color(current.color, true);
            Color chosen = JColorChooser.showDialog(this, "Pick Furniture Color", initial);
            if (chosen == null) return;

            FurnitureSnapshot before = FurnitureSnapshot.from(current);

            int argb = (0xFF << 24) | (chosen.getRed() << 16) | (chosen.getGreen() << 8) | chosen.getBlue();
            current.color = argb;

            model.notifyChanged();
            editor.repaint();

            FurnitureSnapshot after = FurnitureSnapshot.from(current);
            cmd.doCommand(new FurnitureStateCommand(model, current, before, after, "Change Color"));
            baseline = after;
        });
    }

    public void setSelectedItem(FurnitureItem it) {
        this.current = it;
        updatingUI = true;

        if (it == null) {
            txtType.setText("");
            txtX.setText("");
            txtZ.setText("");
            txtW.setText("");
            txtD.setText("");
            txtH.setText("");
            txtRot.setText("");
            spScale.setValue(1.0);
            btnItemColor.setBackground(null);

            setEnabledFurnitureFields(false);
            baseline = null;
        } else {
            txtType.setText(it.type);
            txtX.setText(String.valueOf(it.x));
            txtZ.setText(String.valueOf(it.z));
            txtW.setText(String.valueOf(it.width));
            txtD.setText(String.valueOf(it.depth));
            txtH.setText(String.valueOf(it.height));
            txtRot.setText(String.valueOf(it.rotationDeg));
            spScale.setValue(it.scale);
            btnItemColor.setBackground(new Color(it.color, true));

            setEnabledFurnitureFields(true);
            baseline = FurnitureSnapshot.from(it);
        }

        updatingUI = false;
    }

    private void setEnabledFurnitureFields(boolean enabled) {
        txtX.setEnabled(enabled);
        txtZ.setEnabled(enabled);
        txtW.setEnabled(enabled);
        txtD.setEnabled(enabled);
        txtH.setEnabled(enabled);
        txtRot.setEnabled(enabled);
        spScale.setEnabled(enabled);
        btnItemColor.setEnabled(enabled);
    }

    private void applyFurniture() {
        if (updatingUI || current == null) return;

        try {
            double x = Double.parseDouble(txtX.getText().trim());
            double z = Double.parseDouble(txtZ.getText().trim());
            double w = Double.parseDouble(txtW.getText().trim());
            double d = Double.parseDouble(txtD.getText().trim());
            double h = Double.parseDouble(txtH.getText().trim());
            double rot = Double.parseDouble(txtRot.getText().trim());

            if (w <= 0 || d <= 0 || h <= 0) return;

            current.x = x;
            current.z = z;
            current.width = w;
            current.depth = d;
            current.height = h;
            current.rotationDeg = rot;

            clampInsideRoomScaled(current);

            model.notifyChanged();
            editor.repaint();
            debounceTimer.restart();
        } catch (Exception ignored) {}
    }

    private void commitFurnitureChange(String label) {
        if (current == null || baseline == null) return;

        FurnitureSnapshot after = FurnitureSnapshot.from(current);

        boolean changed =
                after.x != baseline.x || after.z != baseline.z ||
                after.w != baseline.w || after.d != baseline.d || after.h != baseline.h ||
                after.rot != baseline.rot ||
                after.scale != baseline.scale ||
                after.color != baseline.color;

        if (changed) {
            cmd.doCommand(new FurnitureStateCommand(model, current, baseline, after, label));
            baseline = after;
        }
    }

    // IMPORTANT: uses SCALED half sizes
    private void clampInsideRoomScaled(FurnitureItem it) {
        double hw = (it.width * it.scale) / 2.0;
        double hd = (it.depth * it.scale) / 2.0;

        it.x = Math.max(hw, Math.min(model.room.width - hw, it.x));
        it.z = Math.max(hd, Math.min(model.room.depth - hd, it.z));
    }
}

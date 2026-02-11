package com.nextgenware.FurnitureVisualizer;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import javafx.application.Platform;

import com.nextgenware.FurnitureVisualizer.model.LayoutModel;
import com.nextgenware.FurnitureVisualizer.undo.CommandManager;
import com.nextgenware.FurnitureVisualizer.model.FurnitureTemplate;
import com.nextgenware.FurnitureVisualizer.persistence.LayoutSerializer;
import java.nio.file.Path;

public class MainFrame extends JFrame {

    private final LayoutModel model;
    private java.nio.file.Path currentFile = null;
    private boolean dirty = false;
    private javax.swing.Timer autosaveTimer;
    private final CommandManager cmd;
    private final PropertiesPanel props;
    private boolean suppressDirty = false;

    public MainFrame(LayoutModel model) {
        super("Furniture Visualizer (Swing + JavaFX 3D)");
        this.model = model;

        setSize(1200, 800);
        setLocationRelativeTo(null);

        this.cmd = new CommandManager();

        Editor2DPanel editor2D = new Editor2DPanel(model, cmd);
        List<FurnitureTemplate> templates = List.of(
                new FurnitureTemplate("Sofa", 1.8, 0.8, 0.8),
                new FurnitureTemplate("Bed", 2.0, 1.6, 0.6),
                new FurnitureTemplate("Table", 1.2, 0.7, 0.75),
                new FurnitureTemplate("Chair", 0.5, 0.5, 0.9),
                new FurnitureTemplate("Wardrobe", 1.2, 0.6, 2.0)
        );
        CatalogPanel catalog = new CatalogPanel(templates, editor2D::setActiveTemplate);
        this.props = new PropertiesPanel(model, editor2D, cmd);
        Fx3DPanel fx3D = new Fx3DPanel(model);

        CardLayout cards = new CardLayout();
        JPanel center = new JPanel(cards);
        center.add(editor2D, "2D");
        center.add(fx3D, "3D");

        JToolBar bar = new JToolBar();
        JButton btn2d = new JButton("2D");
        JButton btn3d = new JButton("3D");
        JButton btnRotateLeft = new JButton("⟲ Rotate -15°");
        JButton btnRotateRight = new JButton("⟳ Rotate +15°");
        JCheckBox chkGrid = new JCheckBox("Grid Snap", true);
        JCheckBox chkWalls = new JCheckBox("Wall Snap", true);
        JButton btnUndo = new JButton("Undo");
        JButton btnRedo = new JButton("Redo");
        JButton btnDelete = new JButton("Delete");
        JCheckBox chkGuides = new JCheckBox("Guides", true);

        JSplitPane splitCenterRight = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, props);
        JSplitPane splitAll = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, catalog, splitCenterRight);
        JButton btnSave = new JButton("Save");
        JButton btnLoad = new JButton("Load");

        splitCenterRight.setResizeWeight(0.75);
        splitAll.setResizeWeight(0.20);

        bar.add(btnSave);
        bar.add(btnLoad);
        bar.addSeparator();
        bar.add(btn2d);
        bar.add(btn3d);
        bar.addSeparator();
        bar.add(btnUndo);
        bar.add(btnRedo);
        bar.addSeparator();
        bar.add(btnRotateLeft);
        bar.add(btnRotateRight);
        bar.addSeparator();
        bar.add(btnDelete);
        bar.addSeparator();
        bar.add(chkGrid);
        bar.add(chkWalls);
        bar.add(chkGuides);
        catalog.selectFirst();

        btn2d.addActionListener(e -> cards.show(center, "2D"));
        btn3d.addActionListener(e -> cards.show(center, "3D"));
        btnRotateLeft.addActionListener(e -> editor2D.rotateSelected(-15));
        btnRotateRight.addActionListener(e -> editor2D.rotateSelected(+15));
        chkGrid.addActionListener(e -> editor2D.setSnapToGrid(chkGrid.isSelected()));
        chkWalls.addActionListener(e -> editor2D.setSnapToWalls(chkWalls.isSelected()));
        btnUndo.addActionListener(e -> cmd.undo());
        btnRedo.addActionListener(e -> cmd.redo());
        btnDelete.addActionListener(e -> editor2D.deleteSelected());
        chkGuides.addActionListener(e -> editor2D.setShowGuides(chkGuides.isSelected()));
        btnUndo.setEnabled(cmd.canUndo());
        btnRedo.setEnabled(cmd.canRedo());

        btnSave.addActionListener(e -> {
            try {
                if (currentFile == null) {
                    JFileChooser fc = new JFileChooser();
                    fc.setDialogTitle("Save Layout as JSON");
                    if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                        return;
                    }
                    currentFile = fc.getSelectedFile().toPath();
                }

                LayoutSerializer.save(model, currentFile);
                dirty = false;
                updateTitle();

                JOptionPane.showMessageDialog(this, "Saved to:\n" + currentFile);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Save failed:\n" + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        btnLoad.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Load Layout JSON");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    java.nio.file.Path file = fc.getSelectedFile().toPath();
                    var loaded = LayoutSerializer.load(file);

                    suppressDirty = true;
                    model.replaceWith(loaded);
                    props.setSelectedItem(null);
                    cmd.clear();
                    suppressDirty = false;

                    currentFile = file;
                    dirty = false;
                    updateTitle();

                    JOptionPane.showMessageDialog(this, "Loaded from:\n" + file);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "Load failed:\n" + ex.getMessage(),
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        editor2D.setOnSelectionChanged(it -> {
            props.setSelectedItem(it);

            // Also focus 3D view
            Platform.runLater(() -> fx3D.focusOnItem(it));
        });

        add(bar, BorderLayout.NORTH);
        add(splitAll, BorderLayout.CENTER);

        cmd.addListener(() -> {
            if (!suppressDirty) {
                dirty = true;
            }
            SwingUtilities.invokeLater(this::updateTitle);
            SwingUtilities.invokeLater(() -> {
                btnUndo.setEnabled(cmd.canUndo());
                btnRedo.setEnabled(cmd.canRedo());
            });
        });

        updateTitle();
        fx3D.setOnFurnitureClicked(id -> {
            // Swing UI update must be on EDT
            SwingUtilities.invokeLater(() -> {
                editor2D.selectById(id);
                // props auto-updates because selection changed callback triggers
                // (you already wired editor2D.setOnSelectionChanged(props::setSelectedItem))
            });
        });

        fx3D.setOnFurnitureClicked(id -> {
            SwingUtilities.invokeLater(() -> {
                fx3D.setSelectedId(id);     // <-- add this
                editor2D.selectById(id);    // already added earlier
            });
        });

        editor2D.setOnSelectionChanged(props::setSelectedItem);

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control Z"), "UNDO");
        getRootPane().getActionMap().put("UNDO", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cmd.undo();
            }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control Y"), "REDO");
        getRootPane().getActionMap().put("REDO", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                cmd.redo();
            }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("DELETE"), "DELETE_SELECTED");
        getRootPane().getActionMap().put("DELETE_SELECTED", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                editor2D.deleteSelected();
            }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control C"), "COPY");
        getRootPane().getActionMap().put("COPY", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                editor2D.copySelection();
            }
        });

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control V"), "PASTE");
        getRootPane().getActionMap().put("PASTE", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                editor2D.pasteClipboard();
            }
        });

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (!dirty) {
                    dispose();
                    return;
                }

                int res = JOptionPane.showConfirmDialog(
                        MainFrame.this,
                        "You have unsaved changes. Exit anyway?",
                        "Confirm Exit",
                        JOptionPane.YES_NO_OPTION
                );

                if (res == JOptionPane.YES_OPTION) {
                    dispose();
                }
            }
        });

        autosaveTimer = new javax.swing.Timer(30_000, e -> runAutoSave());
        autosaveTimer.setRepeats(true);
        autosaveTimer.start();
        checkAndRestoreAutosave();

    }

    private java.nio.file.Path getAutoSavePath() {
        String home = System.getProperty("user.home");
        java.nio.file.Path dir = java.nio.file.Path.of(home, "FurnitureVisualizer");
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir.resolve("autosave.json");
    }

    private void runAutoSave() {
        if (!dirty) {
            return;
        }

        try {
            Path target = getAutoSavePath();
            com.nextgenware.FurnitureVisualizer.persistence.LayoutSerializer.save(model, target);
            dirty = false;
            SwingUtilities.invokeLater(this::updateTitle);

            // Optional: show small status (not a popup)
            // setTitle("Furniture Visualizer - Autosaved");
        } catch (Exception ex) {
            // Auto-save failure should NOT spam popups
            System.err.println("Auto-save failed: " + ex.getMessage());
        }
    }

    private void checkAndRestoreAutosave() {
        try {
            Path autosave = getAutoSavePath();
            if (!java.nio.file.Files.exists(autosave)) {
                return;
            }

            int res = JOptionPane.showConfirmDialog(
                    this,
                    "An autosave file was found.\nRestore it?",
                    "Restore Autosave",
                    JOptionPane.YES_NO_OPTION
            );

            if (res == JOptionPane.YES_OPTION) {
                var loaded = LayoutSerializer.load(autosave);
                suppressDirty = true;
                model.replaceWith(loaded);
                props.setSelectedItem(null);
                cmd.clear();
                suppressDirty = false;

                currentFile = null;
                dirty = false;
                updateTitle();

            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Autosave restore failed:\n" + ex.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateTitle() {
        String base = "Furniture Visualizer";
        String filePart = (currentFile != null) ? " - " + currentFile.getFileName() : "";
        String star = dirty ? " *" : "";
        setTitle(base + filePart + star);
    }

}

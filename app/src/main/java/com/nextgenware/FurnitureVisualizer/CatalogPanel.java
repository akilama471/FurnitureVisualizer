package com.nextgenware.FurnitureVisualizer;

import com.nextgenware.FurnitureVisualizer.model.FurnitureTemplate;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CatalogPanel extends JPanel {

    private final JList<FurnitureTemplate> list;

    public CatalogPanel(List<FurnitureTemplate> templates, java.util.function.Consumer<FurnitureTemplate> onSelect) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Catalog"));

        list = new JList<>(new DefaultListModel<>());
        DefaultListModel<FurnitureTemplate> m = (DefaultListModel<FurnitureTemplate>) list.getModel();
        templates.forEach(m::addElement);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSelect.accept(list.getSelectedValue());
            }
        });

        add(new JScrollPane(list), BorderLayout.CENTER);

        JLabel hint = new JLabel("<html>Tip: Select an item, then click on the room to place it.</html>");
        hint.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(hint, BorderLayout.SOUTH);
    }

    public void selectFirst() {
        if (list.getModel().getSize() > 0) list.setSelectedIndex(0);
    }
}

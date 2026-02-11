package com.nextgenware.FurnitureVisualizer;

import javax.swing.SwingUtilities;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LayoutModel layout = LayoutModel.sample();
            MainFrame frame = new MainFrame(layout);
            frame.setVisible(true);
        });
    }
}

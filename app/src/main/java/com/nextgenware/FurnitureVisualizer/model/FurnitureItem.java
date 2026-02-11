package com.nextgenware.FurnitureVisualizer.model;

public class FurnitureItem {

    public String id, type;
    public double width, depth, height;
    public double x, z, rotationDeg;
    public double scale = 1.0;     // 1.0 = normal size
    public int color = 0xFFB4D2FF;

    public FurnitureItem() {
    }

    public FurnitureItem(
            String id, String type,
            double width, double depth, double height,
            double x, double z, double rotationDeg
    ) {
        this.id = id;
        this.type = type;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.x = x;
        this.z = z;
        this.rotationDeg = rotationDeg;
    }
}

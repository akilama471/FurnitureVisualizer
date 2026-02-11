package com.nextgenware.FurnitureVisualizer.model;

public class FurnitureTemplate {
    public final String type;
    public final double width, depth, height;

    public FurnitureTemplate(String type, double width, double depth, double height) {
        this.type = type;
        this.width = width;
        this.depth = depth;
        this.height = height;
    }

    @Override
    public String toString() {
        return type + " (" + width + "m x " + depth + "m)";
    }
}

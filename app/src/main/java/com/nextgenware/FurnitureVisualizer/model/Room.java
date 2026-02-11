package com.nextgenware.FurnitureVisualizer.model;

public class Room {

    public enum Shape {
        RECTANGLE
    }

    public double width;
    public double depth;
    public double height;

    public Shape shape = Shape.RECTANGLE;

    public int floorColor = 0xFFF0F0F0;
    public int wallColor = 0xFF333333;
    public int gridColor = 0xFFDCDCDC;

    public Room() {
    }

    public Room(double width, double depth, double height) {
        this.width = width;
        this.depth = depth;
        this.height = height;
    }
}

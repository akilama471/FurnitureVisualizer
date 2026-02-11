package com.nextgenware.FurnitureVisualizer.undo;

import com.nextgenware.FurnitureVisualizer.model.Room;

public class RoomSnapshot {
    public double width, depth, height;
    public Room.Shape shape;
    public int floorColor, wallColor, gridColor;

    public static RoomSnapshot from(Room r) {
        RoomSnapshot s = new RoomSnapshot();
        s.width = r.width;
        s.depth = r.depth;
        s.height = r.height;
        s.shape = r.shape;
        s.floorColor = r.floorColor;
        s.wallColor = r.wallColor;
        s.gridColor = r.gridColor;
        return s;
    }

    public void applyTo(Room r) {
        r.width = width;
        r.depth = depth;
        r.height = height;
        r.shape = shape;
        r.floorColor = floorColor;
        r.wallColor = wallColor;
        r.gridColor = gridColor;
    }
}

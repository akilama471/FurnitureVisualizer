package com.nextgenware.FurnitureVisualizer.undo;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;

public class FurnitureSnapshot {
    public String id;
    public String type;

    public double w, d, h;
    public double x, z, rot;

    // NEW
    public double scale;
    public int color;

    public static FurnitureSnapshot from(FurnitureItem it) {
        FurnitureSnapshot s = new FurnitureSnapshot();
        s.id = it.id;
        s.type = it.type;

        s.w = it.width;
        s.d = it.depth;
        s.h = it.height;

        s.x = it.x;
        s.z = it.z;
        s.rot = it.rotationDeg;

        // NEW
        s.scale = it.scale;
        s.color = it.color;

        return s;
    }

    public void applyTo(FurnitureItem it) {
        it.width = w;
        it.depth = d;
        it.height = h;

        it.x = x;
        it.z = z;
        it.rotationDeg = rot;

        // NEW
        it.scale = scale;
        it.color = color;
    }
}

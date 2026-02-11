package com.nextgenware.FurnitureVisualizer.model;

import java.util.*;

public class LayoutModel {

    public Room room;
    private int idCounter = 100;

    public List<FurnitureItem> items = new ArrayList<>();
    private transient List<Runnable> listeners = new ArrayList<>();

    public void addListener(Runnable r) {
        if (listeners == null) {
            listeners = new ArrayList<>();
        }
        listeners.add(r);
    }

    public String nextId() {
        idCounter++;
        return String.valueOf(idCounter);
    }

    public void notifyChanged() {
        if (listeners == null) {
            return;
        }
        listeners.forEach(Runnable::run);
    }

    public static LayoutModel sample() {
        LayoutModel m = new LayoutModel();
        m.room = new Room(4, 3, 2.6);
        m.items.add(new FurnitureItem("1", "Sofa", 1.8, 0.8, 0.8, 1, 1, 0));
        return m;
    }

    public void replaceWith(LayoutModel other) {
        this.room = other.room;

        this.items.clear();
        this.items.addAll(other.items);

        // update id counter (so nextId won't clash)
        int maxId = 0;
        for (FurnitureItem it : this.items) {
            try {
                maxId = Math.max(maxId, Integer.parseInt(it.id));
            } catch (Exception ignored) {
            }
        }
        this.idCounter = maxId;
        if (this.listeners == null) {
            this.listeners = new ArrayList<>();
        }

        notifyChanged();
    }

}

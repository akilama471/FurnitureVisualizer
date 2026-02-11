package com.nextgenware.FurnitureVisualizer.undo;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

import java.util.List;

public class MultiFurnitureStateCommand implements Command {

    public static class Entry {
        public final FurnitureItem item;
        public final FurnitureSnapshot before;
        public final FurnitureSnapshot after;

        public Entry(FurnitureItem item, FurnitureSnapshot before, FurnitureSnapshot after) {
            this.item = item;
            this.before = before;
            this.after = after;
        }
    }

    private final LayoutModel model;
    private final List<Entry> entries;
    private final String name;

    public MultiFurnitureStateCommand(LayoutModel model, List<Entry> entries, String name) {
        this.model = model;
        this.entries = entries;
        this.name = name;
    }

    @Override public void execute() {
        for (Entry e : entries) e.after.applyTo(e.item);
        model.notifyChanged();
    }

    @Override public void undo() {
        for (Entry e : entries) e.before.applyTo(e.item);
        model.notifyChanged();
    }

    @Override public String name() { return name; }
}

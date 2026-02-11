package com.nextgenware.FurnitureVisualizer.undo;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

import java.util.ArrayList;
import java.util.List;

public class DeleteManyFurnitureCommand implements Command {

    private final LayoutModel model;
    private final List<FurnitureItem> items;
    private final List<Integer> indices = new ArrayList<>();

    public DeleteManyFurnitureCommand(LayoutModel model, List<FurnitureItem> items) {
        this.model = model;
        this.items = new ArrayList<>(items);
    }

    @Override public void execute() {
        indices.clear();
        for (FurnitureItem it : items) indices.add(model.items.indexOf(it));
        model.items.removeAll(items);
        model.notifyChanged();
    }

    @Override public void undo() {
        // restore in original positions as best effort
        for (int i = 0; i < items.size(); i++) {
            FurnitureItem it = items.get(i);
            int idx = indices.get(i);
            if (idx < 0 || idx > model.items.size()) model.items.add(it);
            else model.items.add(idx, it);
        }
        model.notifyChanged();
    }

    @Override public String name() { return "Delete Selection"; }
}

package com.nextgenware.FurnitureVisualizer.undo;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

import java.util.ArrayList;
import java.util.List;

public class AddManyFurnitureCommand implements Command {

    private final LayoutModel model;
    private final List<FurnitureItem> items;

    public AddManyFurnitureCommand(LayoutModel model, List<FurnitureItem> items) {
        this.model = model;
        this.items = new ArrayList<>(items);
    }

    @Override public void execute() {
        for (FurnitureItem it : items) {
            if (!model.items.contains(it)) model.items.add(it);
        }
        model.notifyChanged();
    }

    @Override public void undo() {
        model.items.removeAll(items);
        model.notifyChanged();
    }

    @Override public String name() { return "Paste"; }
}

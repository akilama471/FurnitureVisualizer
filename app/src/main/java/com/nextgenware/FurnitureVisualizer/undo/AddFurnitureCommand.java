package com.nextgenware.FurnitureVisualizer.undo;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

public class AddFurnitureCommand implements Command {

    private final LayoutModel model;
    private final FurnitureItem item;

    public AddFurnitureCommand(LayoutModel model, FurnitureItem item) {
        this.model = model;
        this.item = item;
    }

    @Override
    public void execute() {
        if (!model.items.contains(item)) {
            model.items.add(item);
        }
        model.notifyChanged();
    }

    @Override
    public void undo() {
        model.items.remove(item);
        model.notifyChanged();
    }

    @Override
    public String name() {
        return "Add " + item.type;
    }
}

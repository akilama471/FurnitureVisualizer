package com.nextgenware.FurnitureVisualizer.undo;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

public class DeleteFurnitureCommand implements Command {

    private final LayoutModel model;
    private final FurnitureItem item;
    private int index = -1;

    public DeleteFurnitureCommand(LayoutModel model, FurnitureItem item) {
        this.model = model;
        this.item = item;
    }

    @Override
    public void execute() {
        index = model.items.indexOf(item);
        model.items.remove(item);
        model.notifyChanged();
    }

    @Override
    public void undo() {
        if (index < 0 || index > model.items.size()) {
            model.items.add(item);
        } else {
            model.items.add(index, item);
        }
        model.notifyChanged();
    }

    @Override
    public String name() {
        return "Delete " + item.type;
    }
}

package com.nextgenware.FurnitureVisualizer.undo;

import com.nextgenware.FurnitureVisualizer.model.FurnitureItem;
import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

public class FurnitureStateCommand implements Command {

    private final LayoutModel model;
    private final FurnitureItem item;
    private final FurnitureSnapshot before;
    private final FurnitureSnapshot after;
    private final String name;

    public FurnitureStateCommand(LayoutModel model, FurnitureItem item,
                                FurnitureSnapshot before, FurnitureSnapshot after,
                                String name) {
        this.model = model;
        this.item = item;
        this.before = before;
        this.after = after;
        this.name = name;
    }

    @Override
    public void execute() {
        after.applyTo(item);
        model.notifyChanged();
    }

    @Override
    public void undo() {
        before.applyTo(item);
        model.notifyChanged();
    }

    @Override
    public String name() {
        return name;
    }
}

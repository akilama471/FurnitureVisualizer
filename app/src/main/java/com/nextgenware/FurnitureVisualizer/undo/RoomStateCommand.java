package com.nextgenware.FurnitureVisualizer.undo;

import com.nextgenware.FurnitureVisualizer.model.LayoutModel;

public class RoomStateCommand implements Command {

    private final LayoutModel model;
    private final RoomSnapshot before;
    private final RoomSnapshot after;
    private final String label;

    public RoomStateCommand(LayoutModel model,
                            RoomSnapshot before,
                            RoomSnapshot after,
                            String label) {
        this.model = model;
        this.before = before;
        this.after = after;
        this.label = label;
    }

    @Override
    public void execute() {
        after.applyTo(model.room);
        model.notifyChanged();
    }

    @Override
    public void undo() {
        before.applyTo(model.room);
        model.notifyChanged();
    }

    @Override
    public String name() {
        return label;
    }
}

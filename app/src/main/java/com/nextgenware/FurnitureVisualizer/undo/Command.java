package com.nextgenware.FurnitureVisualizer.undo;

public interface Command {
    void execute();
    void undo();
    String name();
}

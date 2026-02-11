package com.nextgenware.FurnitureVisualizer.undo;

import java.util.ArrayDeque;
import java.util.Deque;

public class CommandManager {

    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private final java.util.List<Runnable> listeners = new java.util.ArrayList<>();

    public void doCommand(Command c) {
        c.execute();
        undoStack.push(c);
        redoStack.clear();
        notifyListeners();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        notifyListeners();
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (!canUndo()) {
            return;
        }
        Command c = undoStack.pop();
        c.undo();
        redoStack.push(c);
        notifyListeners();
    }

    public void redo() {
        if (!canRedo()) {
            return;
        }
        Command c = redoStack.pop();
        c.execute();
        undoStack.push(c);
        notifyListeners();
    }

    public void pushExecutedCommand(Command c) {
        undoStack.push(c);
        redoStack.clear();
        notifyListeners();
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            r.run();
        }
    }
}

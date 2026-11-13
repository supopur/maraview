package cz.maramat.scada.undo;

import cz.maramat.scada.model.ScadaGraphicElement;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 50-step bounded undo/redo manager for ScadaScreen structural edits, matching Maraflow's UndoManager50.
 */
public class UndoManager50 {
    public static final int MAX_STEPS = 50;

    public record Snapshot(String description, List<ScadaSymbolInstance> symbols,
                           List<ScadaPipe> pipes, List<ScadaGraphicElement> graphics) {
        public Snapshot(String description, List<ScadaSymbolInstance> symbols, List<ScadaPipe> pipes) {
            this(description, symbols, pipes, List.of());
        }
    }

    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public void addChangeListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void recordSnapshot(String description, ScadaScreen screen) {
        if (screen == null) return;
        if (undoStack.size() >= MAX_STEPS) {
            undoStack.removeLast();
        }
        undoStack.push(capture(description, screen));
        redoStack.clear();
        notifyListeners();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public String getUndoDescription() {
        return undoStack.isEmpty() ? "" : undoStack.peek().description();
    }

    public String getRedoDescription() {
        return redoStack.isEmpty() ? "" : redoStack.peek().description();
    }

    public boolean undo(ScadaScreen screen) {
        if (screen == null || undoStack.isEmpty()) return false;
        Snapshot prev = undoStack.pop();
        redoStack.push(capture(prev.description(), screen));
        restore(prev, screen);
        notifyListeners();
        return true;
    }

    public boolean redo(ScadaScreen screen) {
        if (screen == null || redoStack.isEmpty()) return false;
        Snapshot next = redoStack.pop();
        undoStack.push(capture(next.description(), screen));
        restore(next, screen);
        notifyListeners();
        return true;
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        notifyListeners();
    }

    private static Snapshot capture(String description, ScadaScreen screen) {
        List<ScadaSymbolInstance> symCopies = new ArrayList<>(screen.getSymbols().size());
        for (ScadaSymbolInstance s : screen.getSymbols()) {
            symCopies.add(s.copy());
        }
        List<ScadaPipe> pipeCopies = new ArrayList<>(screen.getPipes().size());
        for (ScadaPipe p : screen.getPipes()) {
            pipeCopies.add(p.copy());
        }
        List<ScadaGraphicElement> graphicCopies = new ArrayList<>(screen.getGraphics().size());
        for (ScadaGraphicElement g : screen.getGraphics()) {
            graphicCopies.add(g.copy());
        }
        return new Snapshot(description == null ? "Edit" : description, symCopies, pipeCopies, graphicCopies);
    }

    private static void restore(Snapshot snap, ScadaScreen screen) {
        screen.getSymbols().clear();
        for (ScadaSymbolInstance s : snap.symbols()) {
            screen.getSymbols().add(s.copy());
        }
        screen.getPipes().clear();
        for (ScadaPipe p : snap.pipes()) {
            screen.getPipes().add(p.copy());
        }
        screen.getGraphics().clear();
        if (snap.graphics() != null) {
            for (ScadaGraphicElement g : snap.graphics()) {
                screen.getGraphics().add(g.copy());
            }
        }
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            r.run();
        }
    }
}

package cz.maramat.scada.undo;

import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoManager50Test {

    @Test
    void enforces50StepBoundAndRestoresSnapshots() {
        UndoManager50 mgr = new UndoManager50();
        ScadaScreen screen = new ScadaScreen("Main");

        for (int i = 0; i < 60; i++) {
            mgr.recordSnapshot("Step " + i, screen);
            screen.getSymbols().add(new ScadaSymbolInstance("HandValve", "HV-" + i, i, 0, 4, 4));
        }
        assertEquals(60, screen.getSymbols().size());

        int undone = 0;
        while (mgr.canUndo()) {
            assertTrue(mgr.undo(screen));
            undone++;
        }
        assertEquals(UndoManager50.MAX_STEPS, undone);
        assertEquals(10, screen.getSymbols().size());
        assertTrue(mgr.canRedo());
        assertTrue(mgr.redo(screen));
        assertEquals(11, screen.getSymbols().size());

        // Recording a new edit clears redo stack
        mgr.recordSnapshot("New Edit", screen);
        assertFalse(mgr.canRedo());
    }
}

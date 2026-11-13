package cz.maramat.scada.ui;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.PipePort;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScadaCanvasInteractionTest {

    private static ScadaProject newProjectWithTwoConnectedPumps() {
        ScadaProject project = new ScadaProject();
        ScadaScreen screen = project.getScreens().getFirst();

        ScadaSymbolInstance s1 = new ScadaSymbolInstance("CentrifugalPump", "Pump1", 10, 10, 8, 10);
        s1.setId("s1");
        s1.getPorts().add(new PipePort("out", "Out", 8, 5, CardinalDirection.EAST));

        ScadaSymbolInstance s2 = new ScadaSymbolInstance("CentrifugalPump", "Pump2", 30, 10, 8, 10);
        s2.setId("s2");
        s2.getPorts().add(new PipePort("in", "In", 0, 5, CardinalDirection.WEST));

        screen.getSymbols().add(s1);
        screen.getSymbols().add(s2);

        ScadaPipe pipe = new ScadaPipe("p1");
        pipe.setSourceSymbolId("s1");
        pipe.setSourcePortId("out");
        pipe.setTargetSymbolId("s2");
        pipe.setTargetPortId("in");
        pipe.setVertices(List.of(new GridPoint(18, 15), new GridPoint(30, 15)));
        screen.getPipes().add(pipe);

        return project;
    }

    @Test
    void copyAndPasteDuplicatesSymbolsAndInterconnectingPipes() {
        ScadaProject project = newProjectWithTwoConnectedPumps();
        ScadaScreen screen = project.getScreens().getFirst();
        ScadaCanvas canvas = new ScadaCanvas(project, screen);
        try {
            canvas.getSelectedSymbolIds().add("s1");
            canvas.getSelectedSymbolIds().add("s2");

            canvas.copySelection();
            canvas.pasteFromClipboard();

            assertEquals(4, screen.getSymbols().size());
            assertEquals(2, screen.getPipes().size());

            ScadaPipe pastedPipe = screen.getPipes().get(1);
            assertNotEquals("p1", pastedPipe.getId());
            assertNotEquals("s1", pastedPipe.getSourceSymbolId());
            assertNotEquals("s2", pastedPipe.getTargetSymbolId());
            assertEquals(new GridPoint(20, 17), pastedPipe.getVertices().getFirst());
            assertEquals(new GridPoint(32, 17), pastedPipe.getVertices().getLast());
        } finally {
            canvas.dispose();
        }
    }

    @Test
    void rotateSelectedReroutesConnectedPipes() {
        ScadaProject project = newProjectWithTwoConnectedPumps();
        ScadaScreen screen = project.getScreens().getFirst();
        ScadaCanvas canvas = new ScadaCanvas(project, screen);
        try {
            canvas.getSelectedSymbolIds().add("s1");
            canvas.rotateSelected90CW();

            ScadaSymbolInstance s1 = screen.findSymbolById("s1");
            PipePort.WorldPort rotatedPort = s1.resolveWorldPort("out");
            ScadaPipe pipe = screen.getPipes().getFirst();

            assertEquals(90, s1.getRotationDeg());
            assertEquals(rotatedPort.position(), pipe.getVertices().getFirst());
            assertEquals(new GridPoint(30, 15), pipe.getVertices().getLast());
        } finally {
            canvas.dispose();
        }
    }

    @Test
    void stepSimulationAndEmptyViewportClickAdvanceState() {
        ScadaProject project = newProjectWithTwoConnectedPumps();
        ScadaScreen screen = project.getScreens().getFirst();
        ScadaCanvas canvas = new ScadaCanvas(project, screen);
        try {
            AtomicInteger stepCalls = new AtomicInteger();
            canvas.setOnStepCallback(stepCalls::incrementAndGet);

            assertEquals(ScadaCanvas.SimState.STOPPED, canvas.getSimState());

            // Click on empty viewport in editor mode -> steps simulation once
            canvas.setSize(800, 600);
            MouseEvent press = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), 0, 5, 5, 1, false, MouseEvent.BUTTON1);
            MouseEvent release = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    System.currentTimeMillis() + 10, 0, 5, 5, 1, false, MouseEvent.BUTTON1);
            canvas.dispatchEvent(press);
            canvas.dispatchEvent(release);

            assertEquals(ScadaCanvas.SimState.PAUSED, canvas.getSimState());
            assertEquals(1, stepCalls.get());

            canvas.stepSimulation();
            assertEquals(2, stepCalls.get());

            canvas.setSimState(ScadaCanvas.SimState.STOPPED);
            assertEquals(ScadaCanvas.SimState.STOPPED, canvas.getSimState());
            assertTrue(canvas.getSelectedSymbolIds().isEmpty());
        } finally {
            canvas.dispose();
        }
    }
}

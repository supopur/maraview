package cz.maramat.scada.ui;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.model.ConnectionType;
import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.EquipmentDomain;
import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.PipePort;
import cz.maramat.scada.model.ScadaGraphicElement;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.model.SymbolCatalog;
import cz.maramat.scada.tags.InternalTagProvider;
import cz.maramat.scada.tags.ScadaTag;
import cz.maramat.scada.tags.TagSourceType;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

            // Click on empty viewport in editor mode -> should NOT step simulation
            canvas.setSize(800, 600);
            MouseEvent press = new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), 0, 5, 5, 1, false, MouseEvent.BUTTON1);
            MouseEvent release = new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED,
                    System.currentTimeMillis() + 10, 0, 5, 5, 1, false, MouseEvent.BUTTON1);
            canvas.dispatchEvent(press);
            canvas.dispatchEvent(release);

            assertEquals(ScadaCanvas.SimState.STOPPED, canvas.getSimState());
            assertEquals(0, stepCalls.get());

            canvas.stepSimulation();
            assertEquals(ScadaCanvas.SimState.PAUSED, canvas.getSimState());
            assertEquals(1, stepCalls.get());

            canvas.setSimState(ScadaCanvas.SimState.STOPPED);
            assertEquals(ScadaCanvas.SimState.STOPPED, canvas.getSimState());
            assertTrue(canvas.getSelectedSymbolIds().isEmpty());
        } finally {
            canvas.dispose();
        }
    }

    @Test
    void deleteSelectionCascadeDeletesConnectedPipes() {
        ScadaProject project = newProjectWithTwoConnectedPumps();
        ScadaScreen screen = project.getScreens().getFirst();
        ScadaCanvas canvas = new ScadaCanvas(project, screen);
        try {
            assertEquals(1, screen.getPipes().size());
            canvas.getSelectedSymbolIds().add("s1");
            canvas.deleteSelection();

            assertEquals(1, screen.getSymbols().size());
            assertEquals(0, screen.getPipes().size(), "Deleting a symbol must delete its connected pipes");
        } finally {
            canvas.dispose();
        }
    }

    @Test
    void symbolCatalogDomainsPortsAndResizabilityAreCorrect() {
        ScadaSymbolInstance motor = SymbolCatalog.createInstance("ElectricMotor", 5, 5, 1);
        assertEquals(EquipmentDomain.ELECTRICAL, SymbolCatalog.getDomain("ElectricMotor"));
        assertTrue(motor.getPorts().stream().noneMatch(p -> p.connectionType() == ConnectionType.PIPE),
                "ElectricMotor must not have fluid pipe ports");
        assertTrue(motor.getPorts().stream().anyMatch(p -> p.connectionType() == ConnectionType.ELECTRICAL_POWER));

        assertEquals(EquipmentDomain.MAR_SENSOR, SymbolCatalog.getDomain("TempSensor"));
        assertFalse(SymbolCatalog.isResizable("TempSensor"), "Sensors should be fixed 1:1");

        ScadaSymbolInstance boiler = SymbolCatalog.createInstance("Boiler", 10, 10, 1);
        assertEquals(EquipmentDomain.HEATING_HVAC, SymbolCatalog.getDomain("Boiler"));
        assertTrue(SymbolCatalog.isResizable("Boiler"));
        boiler.setGridW(20);
        boiler.setGridH(20);
        SymbolCatalog.rebuildPortsForSize(boiler);
        assertEquals(5, boiler.getPorts().getFirst().offsetX());
    }

    @Test
    void graphicsAndPicturesCopyPasteAndDelete() {
        ScadaProject project = new ScadaProject();
        ScadaScreen screen = project.getScreens().getFirst();
        ScadaCanvas canvas = new ScadaCanvas(project, screen);
        try {
            ScadaGraphicElement rect = new ScadaGraphicElement(ScadaGraphicElement.Kind.RECTANGLE, 4, 4, 8, 6);
            screen.getGraphics().add(rect);
            canvas.getSelectedGraphicIds().add(rect.getId());

            canvas.copySelection();
            canvas.pasteFromClipboard();
            assertEquals(2, screen.getGraphics().size());

            canvas.deleteSelection();
            assertEquals(1, screen.getGraphics().size());
        } finally {
            canvas.dispose();
        }
    }

    @Test
    void scadaCatalogExcludesSimControllersIncludesWastewaterAndRestoresTagOnReplay() {
        assertNull(SymbolCatalog.getDefinition("PidController"));
        assertNull(SymbolCatalog.getDefinition("MarController"));

        assertEquals(EquipmentDomain.WASTEWATER, SymbolCatalog.getDomain("AerationBasin"));
        assertEquals(EquipmentDomain.WASTEWATER, SymbolCatalog.getDomain("PrimaryClarifier"));
        assertEquals(EquipmentDomain.WASTEWATER, SymbolCatalog.getDomain("AnaerobicDigester"));
        assertEquals(EquipmentDomain.WASTEWATER, SymbolCatalog.getDomain("UvDisinfection"));

        ScadaTag pumpRun = new ScadaTag("Pump1_Run", DataType.BOOL, TagSourceType.INTERNAL);
        pumpRun.setInitialValue("1");
        pumpRun.resetRuntimeToInitial();
        assertEquals(1.0, pumpRun.getRuntimeValue());

        // Stop zeroes runtime value, Play restores initial value
        pumpRun.setRuntimeValue(0.0);
        assertEquals(0.0, pumpRun.getRuntimeValue());
        pumpRun.resetRuntimeToInitial();
        assertEquals(1.0, pumpRun.getRuntimeValue());
    }

    @Test
    void operatorPushButtonSupportsToggleAndMomentaryWithoutDoubleFiring() {
        ScadaProject project = new ScadaProject();
        project.putTag(new ScadaTag("BtnToggle", DataType.BOOL, TagSourceType.INTERNAL));
        project.putTag(new ScadaTag("BtnMomentary", DataType.BOOL, TagSourceType.INTERNAL));

        ScadaScreen screen = project.getScreens().getFirst();
        ScadaSymbolInstance toggleBtn = SymbolCatalog.createInstance("PushButton", 10, 10, 1);
        toggleBtn.getTagBindings().put("RunTag", "BtnToggle");
        toggleBtn.setProperty("ButtonMode", "Toggle");

        ScadaSymbolInstance momBtn = SymbolCatalog.createInstance("PushButton", 20, 10, 2);
        momBtn.getTagBindings().put("RunTag", "BtnMomentary");
        momBtn.setProperty("ButtonMode", "Momentary");

        screen.getSymbols().addAll(List.of(toggleBtn, momBtn));

        InternalTagProvider tags = new InternalTagProvider();
        ScadaCanvas canvas = new ScadaCanvas(project, screen);
        canvas.setTagProvider(tags);
        canvas.setOperatorMode(true);
        try {
            int gPx = canvas.getGridSizePx();
            int tx = 12 * gPx;
            int ty = 12 * gPx;
            // Toggle mode: press + release + click should toggle ONCE (0 -> 1)
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 1, 0, tx, ty, 1, false, MouseEvent.BUTTON1));
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 2, 0, tx, ty, 1, false, MouseEvent.BUTTON1));
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 3, 0, tx, ty, 1, false, MouseEvent.BUTTON1));
            assertEquals(1.0, tags.readTag(project, "BtnToggle"), "Toggle button must stay ON after first click");

            // Second click should toggle back OFF (1 -> 0)
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 4, 0, tx, ty, 1, false, MouseEvent.BUTTON1));
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 5, 0, tx, ty, 1, false, MouseEvent.BUTTON1));
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 6, 0, tx, ty, 1, false, MouseEvent.BUTTON1));
            assertEquals(0.0, tags.readTag(project, "BtnToggle"), "Toggle button must turn OFF after second click");

            // Momentary mode: press -> 1.0, release -> 0.0, click -> still 0.0
            int mx = 22 * gPx;
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 7, 0, mx, ty, 1, false, MouseEvent.BUTTON1));
            assertEquals(1.0, tags.readTag(project, "BtnMomentary"), "Momentary button must be ON while held");
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 8, 0, mx, ty, 1, false, MouseEvent.BUTTON1));
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, 9, 0, mx, ty, 1, false, MouseEvent.BUTTON1));
            assertEquals(0.0, tags.readTag(project, "BtnMomentary"), "Momentary button must return to OFF on release");
        } finally {
            canvas.dispose();
        }
    }
}

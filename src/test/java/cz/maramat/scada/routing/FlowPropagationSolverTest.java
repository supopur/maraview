package cz.maramat.scada.routing;

import cz.maramat.scada.model.ConnectionType;
import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.tags.InternalTagProvider;
import cz.maramat.scada.tags.ScadaTag;
import cz.maramat.scada.tags.TagSourceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowPropagationSolverTest {

    @Test
    void propagatesFlowThroughRunningPumpAndOpenValveButStopsAtClosedValve() {
        ScadaProject project = new ScadaProject();
        ScadaTag pumpRun = new ScadaTag("P1_Run", DataType.BOOL, TagSourceType.INTERNAL);
        ScadaTag valveOpen = new ScadaTag("V1_Open", DataType.BOOL, TagSourceType.INTERNAL);
        project.putTag(pumpRun);
        project.putTag(valveOpen);

        ScadaScreen screen = project.getScreens().getFirst();
        ScadaSymbolInstance pump = new ScadaSymbolInstance("CentrifugalPump", "P-101", 10, 10, 8, 10);
        pump.getTagBindings().put("RunTag", "P1_Run");
        ScadaSymbolInstance valve = new ScadaSymbolInstance("SolenoidValve", "XV-101", 25, 10, 4, 4);
        valve.getTagBindings().put("OpenTag", "V1_Open");
        ScadaSymbolInstance tank = new ScadaSymbolInstance("CylindricalTank", "TK-101", 40, 5, 10, 16);
        screen.getSymbols().addAll(List.of(pump, valve, tank));

        ScadaPipe p1 = new ScadaPipe("p1");
        p1.setSourceSymbolId(pump.getId());
        p1.setTargetSymbolId(valve.getId());
        p1.setFluidType(FluidType.WATER);
        p1.setVertices(List.of(new GridPoint(18, 12), new GridPoint(25, 12)));

        ScadaPipe p2 = new ScadaPipe("p2");
        p2.setSourceSymbolId(valve.getId());
        p2.setTargetSymbolId(tank.getId());
        p2.setFluidType(FluidType.WATER);
        p2.setVertices(List.of(new GridPoint(29, 12), new GridPoint(40, 12)));

        ScadaPipe tee = new ScadaPipe("tee1");
        tee.setSourceTeePipeId(p2.getId());
        tee.setFluidType(FluidType.WATER);
        tee.setVertices(List.of(new GridPoint(34, 12), new GridPoint(34, 22)));

        screen.getPipes().addAll(List.of(p1, p2, tee));

        InternalTagProvider tags = new InternalTagProvider();
        tags.writeTag(project, "P1_Run", 1.0);
        tags.writeTag(project, "V1_Open", 0.0);

        Map<String, FlowState> closedMap = FlowPropagationSolver.solve(screen, project, tags);
        assertTrue(closedMap.get("p1").pressurized());
        assertFalse(closedMap.get("p2").pressurized());
        assertFalse(closedMap.get("tee1").pressurized());

        tags.writeTag(project, "V1_Open", 1.0);
        Map<String, FlowState> openMap = FlowPropagationSolver.solve(screen, project, tags);
        assertTrue(openMap.get("p1").pressurized());
        assertTrue(openMap.get("p2").pressurized());
        assertTrue(openMap.get("tee1").pressurized());
    }

    @Test
    void stoppedPumpWithOpenValveAndFullTankDoesNotBackfeedFlowThroughInlet() {
        ScadaProject project = new ScadaProject();
        project.putTag(new ScadaTag("P1_Run", DataType.BOOL, TagSourceType.INTERNAL));
        project.putTag(new ScadaTag("V1_Open", DataType.BOOL, TagSourceType.INTERNAL));
        project.putTag(new ScadaTag("TK_Level", DataType.FLOAT, TagSourceType.INTERNAL));

        ScadaScreen screen = project.getScreens().getFirst();
        ScadaSymbolInstance pump = new ScadaSymbolInstance("CentrifugalPump", "P-101", 10, 10, 8, 10);
        pump.getTagBindings().put("RunTag", "P1_Run");
        ScadaSymbolInstance valve = new ScadaSymbolInstance("HandValve", "HV-101", 25, 10, 4, 4);
        valve.getTagBindings().put("OpenTag", "V1_Open");
        ScadaSymbolInstance tank = new ScadaSymbolInstance("CylindricalTank", "TK-101", 40, 5, 10, 16);
        tank.getTagBindings().put("LevelTag", "TK_Level");
        screen.getSymbols().addAll(List.of(pump, valve, tank));

        ScadaPipe p1 = new ScadaPipe("p1");
        p1.setSourceSymbolId(pump.getId());
        p1.setSourcePortId("discharge");
        p1.setTargetSymbolId(valve.getId());
        p1.setTargetPortId("inlet");
        p1.setFluidType(FluidType.WATER);

        ScadaPipe p2 = new ScadaPipe("p2");
        p2.setSourceSymbolId(valve.getId());
        p2.setSourcePortId("outlet");
        p2.setTargetSymbolId(tank.getId());
        p2.setTargetPortId("inlet");
        p2.setFluidType(FluidType.WATER);

        screen.getPipes().addAll(List.of(p1, p2));

        InternalTagProvider tags = new InternalTagProvider();
        tags.writeTag(project, "P1_Run", 0.0);
        tags.writeTag(project, "V1_Open", 1.0);
        tags.writeTag(project, "TK_Level", 65.0);

        Map<String, FlowState> stoppedMap = FlowPropagationSolver.solve(screen, project, tags);
        assertFalse(stoppedMap.get("p1").pressurized(), "Stopped pump must not have flow in discharge pipe");
        assertFalse(stoppedMap.get("p2").pressurized(), "Tank inlet must not backfeed flow through open valve to stopped pump");

        // Turn pump ON (even with suction unconnected) -> should pump through open valve into tank
        tags.writeTag(project, "P1_Run", 1.0);
        Map<String, FlowState> runningMap = FlowPropagationSolver.solve(screen, project, tags);
        assertTrue(runningMap.get("p1").pressurized(), "Running pump with unconnected suction must pump out of discharge");
        assertTrue(runningMap.get("p2").pressurized(), "Flow must continue through open valve to tank");
        assertTrue(runningMap.get("p1").flowRate() > 0.0);
        assertTrue(runningMap.get("p2").flowRate() > 0.0);
    }

    @Test
    void filledTankIntoClosedValveAndUnpressedButtonDoNotAnimateFlow() {
        ScadaProject project = new ScadaProject();
        project.putTag(new ScadaTag("TK_Level", DataType.FLOAT, TagSourceType.INTERNAL));
        project.putTag(new ScadaTag("XV_Open", DataType.BOOL, TagSourceType.INTERNAL));
        project.putTag(new ScadaTag("PB_Run", DataType.BOOL, TagSourceType.INTERNAL));

        ScadaScreen screen = project.getScreens().getFirst();
        ScadaSymbolInstance tank1 = new ScadaSymbolInstance("CylindricalTank", "TK-1", 5, 5, 10, 16);
        tank1.getTagBindings().put("LevelTag", "TK_Level");
        ScadaSymbolInstance valve = new ScadaSymbolInstance("SolenoidValve", "XV-1", 20, 10, 4, 4);
        valve.getTagBindings().put("OpenTag", "XV_Open");
        ScadaSymbolInstance tank2 = new ScadaSymbolInstance("CylindricalTank", "TK-2", 30, 5, 10, 16);

        ScadaSymbolInstance btn = new ScadaSymbolInstance("PushButton", "PB-1", 5, 30, 4, 4);
        btn.getTagBindings().put("RunTag", "PB_Run");
        ScadaSymbolInstance lamp = new ScadaSymbolInstance("PilotLamp", "HL-1", 15, 30, 4, 4);

        screen.getSymbols().addAll(List.of(tank1, valve, tank2, btn, lamp));

        ScadaPipe p1 = new ScadaPipe("p1");
        p1.setSourceSymbolId(tank1.getId());
        p1.setSourcePortId("outlet");
        p1.setTargetSymbolId(valve.getId());
        p1.setTargetPortId("inlet");
        p1.setFluidType(FluidType.WATER);

        ScadaPipe p2 = new ScadaPipe("p2");
        p2.setSourceSymbolId(valve.getId());
        p2.setSourcePortId("outlet");
        p2.setTargetSymbolId(tank2.getId());
        p2.setTargetPortId("inlet");
        p2.setFluidType(FluidType.WATER);

        ScadaPipe w1 = new ScadaPipe("w1");
        w1.setConnectionType(ConnectionType.CONTROL_SIGNAL);
        w1.setSourceSymbolId(btn.getId());
        w1.setSourcePortId("sig");
        w1.setTargetSymbolId(lamp.getId());
        w1.setTargetPortId("sig");

        screen.getPipes().addAll(List.of(p1, p2, w1));

        InternalTagProvider tags = new InternalTagProvider();
        tags.writeTag(project, "TK_Level", 80.0);
        tags.writeTag(project, "XV_Open", 0.0);
        tags.writeTag(project, "PB_Run", 0.0);

        Map<String, FlowState> blocked = FlowPropagationSolver.solve(screen, project, tags);
        assertTrue(blocked.get("p1").pressurized());
        assertTrue(blocked.get("p1").flowRate() == 0.0, "Closed valve dead-end must have 0 flowRate (no bubbles)");
        assertFalse(blocked.get("w1").pressurized(), "Unpressed button must not energize control wire");
        assertTrue(blocked.get("w1").flowRate() == 0.0);

        // Open valve & press button -> both flow
        tags.writeTag(project, "XV_Open", 1.0);
        tags.writeTag(project, "PB_Run", 1.0);
        Map<String, FlowState> active = FlowPropagationSolver.solve(screen, project, tags);
        assertTrue(active.get("p1").flowRate() > 0.0);
        assertTrue(active.get("p2").flowRate() > 0.0);
        assertTrue(active.get("w1").pressurized());
        assertTrue(active.get("w1").flowRate() > 0.0);
    }

    @Test
    void inletFlowsThroughOpenValveToOutletAndStopsWhenValveCloses() {
        ScadaProject project = new ScadaProject();
        project.putTag(new ScadaTag("V_Open", DataType.BOOL, TagSourceType.INTERNAL));

        ScadaScreen screen = project.getScreens().getFirst();
        ScadaSymbolInstance inlet = new ScadaSymbolInstance("Inlet", "CityWater", 2, 10, 4, 4);
        ScadaSymbolInstance valve = new ScadaSymbolInstance("SolenoidValve", "XV-1", 10, 10, 4, 5);
        valve.getTagBindings().put("OpenTag", "V_Open");
        ScadaSymbolInstance outlet = new ScadaSymbolInstance("Outlet", "CityDrain", 20, 10, 4, 4);
        screen.getSymbols().addAll(List.of(inlet, valve, outlet));

        ScadaPipe p1 = new ScadaPipe("p1");
        p1.setSourceSymbolId(inlet.getId());
        p1.setSourcePortId("outlet");
        p1.setTargetSymbolId(valve.getId());
        p1.setTargetPortId("inlet");
        p1.setFluidType(FluidType.WATER);

        ScadaPipe p2 = new ScadaPipe("p2");
        p2.setSourceSymbolId(valve.getId());
        p2.setSourcePortId("outlet");
        p2.setTargetSymbolId(outlet.getId());
        p2.setTargetPortId("inlet");
        p2.setFluidType(FluidType.WATER);

        screen.getPipes().addAll(List.of(p1, p2));

        InternalTagProvider tags = new InternalTagProvider();
        tags.writeTag(project, "V_Open", 0.0);
        Map<String, FlowState> closed = FlowPropagationSolver.solve(screen, project, tags);
        assertTrue(closed.get("p1").pressurized());
        assertTrue(closed.get("p1").flowRate() == 0.0, "Inlet into closed valve must not show flowing bubbles");
        assertFalse(closed.get("p2").pressurized());

        tags.writeTag(project, "V_Open", 1.0);
        Map<String, FlowState> open = FlowPropagationSolver.solve(screen, project, tags);
        assertTrue(open.get("p1").flowRate() > 0.0, "Inlet through open valve to Outlet must flow");
        assertTrue(open.get("p2").flowRate() > 0.0);
    }
}

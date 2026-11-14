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

    @Test
    void collectFlowRelevantTagsIncludesFlowControlsAndExcludesProcessSensors() {
        ScadaProject project = new ScadaProject();
        ScadaScreen screen = project.getScreens().getFirst();

        ScadaSymbolInstance pump = new ScadaSymbolInstance("CirculationPump", "P-1", 4, 4, 4, 4);
        pump.getTagBindings().put("RunTag", "P_Run");
        pump.getTagBindings().put("SpeedTag", "P_Speed");
        pump.getTagBindings().put("FaultTag", "P_Fault");

        ScadaSymbolInstance valve = new ScadaSymbolInstance("ThreeWayMixingValve", "MV-1", 12, 4, 4, 4);
        valve.getTagBindings().put("PositionTag", "V_Pos");
        valve.getTagBindings().put("TempTag", "T_Mix");

        ScadaSymbolInstance sensor = new ScadaSymbolInstance("TempSensor", "TT-1", 20, 4, 4, 4);
        sensor.getTagBindings().put("ValueTag", "T_Kotel");

        ScadaSymbolInstance readout = new ScadaSymbolInstance("NumericReadout", "E-1", 28, 4, 6, 2);
        readout.getTagBindings().put("ValueTag", "E_KWh");

        screen.getSymbols().addAll(List.of(pump, valve, sensor, readout));

        ScadaPipe explicitPipe = new ScadaPipe("pExplicit");
        explicitPipe.setAutoFlow(false);
        explicitPipe.setFlowTag("ExplicitFlow");
        screen.getPipes().add(explicitPipe);

        var relevant = FlowPropagationSolver.collectFlowRelevantTags(screen);
        assertTrue(relevant.contains("P_Run"));
        assertTrue(relevant.contains("P_Speed"));
        assertTrue(relevant.contains("V_Pos"));
        assertTrue(relevant.contains("ExplicitFlow"));
        assertFalse(relevant.contains("T_Kotel"));
        assertFalse(relevant.contains("T_Mix"));
        assertFalse(relevant.contains("E_KWh"));
        assertFalse(relevant.contains("P_Fault"));
    }

    @Test
    void closedHeatingLoopAndReverseDrawnPipesFlowWithCorrectDirection() {
        ScadaProject project = new ScadaProject();
        ScadaScreen screen = project.getScreens().getFirst();

        ScadaSymbolInstance boiler = new ScadaSymbolInstance("GasBoiler", "B-1", 5, 10, 8, 8);
        boiler.setProperty("Running", "true");
        ScadaSymbolInstance pump = new ScadaSymbolInstance("CirculationPump", "CP-1", 18, 10, 4, 4);
        pump.setProperty("Running", "true");
        ScadaSymbolInstance radiator = new ScadaSymbolInstance("Radiator", "RAD-1", 28, 10, 6, 4);
        screen.getSymbols().addAll(List.of(boiler, pump, radiator));

        ScadaPipe supply1 = new ScadaPipe("s1");
        supply1.setSourceSymbolId(boiler.getId());
        supply1.setSourcePortId("outlet");
        supply1.setTargetSymbolId(pump.getId());
        supply1.setTargetPortId("inlet");

        ScadaPipe supply2 = new ScadaPipe("s2");
        supply2.setSourceSymbolId(pump.getId());
        supply2.setSourcePortId("outlet");
        supply2.setTargetSymbolId(radiator.getId());
        supply2.setTargetPortId("inlet");

        // Drawn in reverse (target -> source) for return pipe
        ScadaPipe ret = new ScadaPipe("ret");
        ret.setSourceSymbolId(boiler.getId());
        ret.setSourcePortId("return");
        ret.setTargetSymbolId(radiator.getId());
        ret.setTargetPortId("outlet");

        screen.getPipes().addAll(List.of(supply1, supply2, ret));

        Map<String, FlowState> solved = FlowPropagationSolver.solve(screen, project, new InternalTagProvider());
        assertTrue(solved.get("s1").flowRate() > 0.0);
        assertTrue(solved.get("s2").flowRate() > 0.0);
        assertTrue(solved.get("ret").flowRate() > 0.0, "Closed-loop return pipe must flow");
        assertTrue(solved.get("ret").direction() < 0, "Reverse-drawn return pipe must have direction = -1");
    }

    @Test
    void electricalPowerThroughClosedBreakerEnergizesPumpWithoutTags() {
        ScadaProject project = new ScadaProject();
        ScadaScreen screen = project.getScreens().getFirst();

        ScadaSymbolInstance pwr = new ScadaSymbolInstance("PowerSource400V", "400V", 2, 2, 4, 4);
        ScadaSymbolInstance breaker = new ScadaSymbolInstance("CircuitBreaker", "CB-1", 10, 2, 4, 4);
        breaker.setProperty("Closed", "true");
        ScadaSymbolInstance pump = new ScadaSymbolInstance("CentrifugalPump", "P-1", 18, 2, 6, 6);
        ScadaSymbolInstance outlet = new ScadaSymbolInstance("Outlet", "Out", 28, 2, 4, 4);
        screen.getSymbols().addAll(List.of(pwr, breaker, pump, outlet));

        ScadaPipe w1 = new ScadaPipe("w1");
        w1.setConnectionType(ConnectionType.ELECTRICAL_POWER);
        w1.setSourceSymbolId(pwr.getId());
        w1.setSourcePortId("out");
        w1.setTargetSymbolId(breaker.getId());
        w1.setTargetPortId("in");

        ScadaPipe w2 = new ScadaPipe("w2");
        w2.setConnectionType(ConnectionType.ELECTRICAL_POWER);
        w2.setSourceSymbolId(breaker.getId());
        w2.setSourcePortId("out");
        w2.setTargetSymbolId(pump.getId());
        w2.setTargetPortId("pwr");

        ScadaPipe p1 = new ScadaPipe("p1");
        p1.setSourceSymbolId(pump.getId());
        p1.setSourcePortId("discharge");
        p1.setTargetSymbolId(outlet.getId());
        p1.setTargetPortId("inlet");

        screen.getPipes().addAll(List.of(w1, w2, p1));

        Map<String, FlowState> closedSolved = FlowPropagationSolver.solve(screen, project, new InternalTagProvider());
        assertTrue(closedSolved.get("w2").flowRate() > 0.0);
        assertTrue(closedSolved.get("p1").flowRate() > 0.0, "Energized pump must flow water to outlet");

        breaker.setProperty("Closed", "false");
        Map<String, FlowState> openSolved = FlowPropagationSolver.solve(screen, project, new InternalTagProvider());
        assertFalse(openSolved.get("w2").pressurized());
        assertFalse(openSolved.get("p1").pressurized(), "De-energized pump must stop flowing");
    }

    @Test
    void plateHeatExchangerIsolatesPrimaryAndSecondaryWaterCircuits() {
        ScadaProject project = new ScadaProject();
        ScadaScreen screen = project.getScreens().getFirst();

        ScadaSymbolInstance tank = new ScadaSymbolInstance("BufferTank", "AKU", 2, 10, 8, 9);
        ScadaSymbolInstance hex = new ScadaSymbolInstance("PlateHeatExchanger", "OKR2", 15, 10, 8, 10);
        ScadaSymbolInstance stoppedPump = new ScadaSymbolInstance("CirculationPump", "P-OKR2", 28, 10, 5, 5);
        stoppedPump.setProperty("Running", "false");
        ScadaSymbolInstance fcu = new ScadaSymbolInstance("UnderfloorHeatingZone", "M2", 38, 10, 9, 8);
        screen.getSymbols().addAll(List.of(tank, hex, stoppedPump, fcu));

        ScadaPipe secFeed = new ScadaPipe("secFeed");
        secFeed.setSourceSymbolId(tank.getId());
        secFeed.setSourcePortId("top_out");
        secFeed.setTargetSymbolId(hex.getId());
        secFeed.setTargetPortId("sec_in");

        ScadaPipe priPipe1 = new ScadaPipe("priPipe1");
        priPipe1.setSourceSymbolId(hex.getId());
        priPipe1.setSourcePortId("pri_out");
        priPipe1.setTargetSymbolId(stoppedPump.getId());
        priPipe1.setTargetPortId("inlet");

        ScadaPipe priPipe2 = new ScadaPipe("priPipe2");
        priPipe2.setSourceSymbolId(stoppedPump.getId());
        priPipe2.setSourcePortId("outlet");
        priPipe2.setTargetSymbolId(fcu.getId());
        priPipe2.setTargetPortId("supply");

        ScadaPipe priReturn = new ScadaPipe("priReturn");
        priReturn.setSourceSymbolId(fcu.getId());
        priReturn.setSourcePortId("return");
        priReturn.setTargetSymbolId(hex.getId());
        priReturn.setTargetPortId("pri_in");

        screen.getPipes().addAll(List.of(secFeed, priPipe1, priPipe2, priReturn));

        Map<String, FlowState> solved = FlowPropagationSolver.solve(screen, project, new InternalTagProvider());
        assertFalse(solved.get("priPipe1").pressurized(), "Secondary flow must not cross into primary circuit");
        assertFalse(solved.get("priPipe2").pressurized());
        assertFalse(solved.get("priReturn").pressurized());
    }
}

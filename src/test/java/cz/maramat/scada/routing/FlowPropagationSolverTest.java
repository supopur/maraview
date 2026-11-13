package cz.maramat.scada.routing;

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
}

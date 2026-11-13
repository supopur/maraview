package cz.maramat.scada.render.symbols;

import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.render.SymbolLayerCache;
import cz.maramat.scada.tags.InternalTagProvider;
import cz.maramat.scada.tags.ScadaTag;
import cz.maramat.scada.tags.TagSourceType;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentSymbolRendererTest {

    @Test
    void rendersAllEpoch4EquipmentSymbolsWithCachingAndRotations() {
        ScadaProject project = new ScadaProject();
        ScadaTag run = new ScadaTag("RunAll", DataType.BOOL, TagSourceType.INTERNAL);
        run.setInitialValue("1");
        run.resetRuntimeToInitial();
        project.putTag(run);

        InternalTagProvider tags = new InternalTagProvider();
        SymbolLayerCache cache = new SymbolLayerCache();
        EquipmentSymbolRenderer renderer = new EquipmentSymbolRenderer();

        List<String> types = List.of(
                "CentrifugalPump", "InlinePump", "MeteringPump", "ElectricMotor", "BlowerFan",
                "HandValve", "SolenoidValve", "ControlValve", "CheckValve", "Damper",
                "CylindricalTank", "HorizontalTank", "HopperSilo", "OpenBasin", "TankAgitator",
                "ConveyorBelt", "ScrewAuger", "PneumaticCylinder", "HydraulicPress", "HeaterCoil"
        );

        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < types.size(); i++) {
                String t = types.get(i);
                ScadaSymbolInstance sym = new ScadaSymbolInstance(t, "EQ-" + i, 2, 2, 8, 10);
                sym.setRotationDeg((i % 4) * 90);
                sym.setFlipH((i & 1) == 1);
                sym.getTagBindings().put("RunTag", "RunAll");
                renderer.paintSymbol(g, sym, 10, project, tags, cache, 1000L, false);
            }
        } finally {
            g.dispose();
        }

        assertTrue(cache.backgroundSize() >= 4, "Expected static shells cached in SymbolLayerCache");
    }

    @Test
    void resolvesStandbyAndRendersLocalRemoteHoaBadges() {
        ScadaProject project = new ScadaProject();
        ScadaTag stby = new ScadaTag("PumpStby", DataType.BOOL, TagSourceType.INTERNAL);
        stby.setInitialValue("1");
        stby.resetRuntimeToInitial();
        project.putTag(stby);

        InternalTagProvider tags = new InternalTagProvider();
        ScadaSymbolInstance sym = new ScadaSymbolInstance("CentrifugalPump", "P-101", 2, 3, 8, 10);
        sym.getTagBindings().put("StandbyTag", "PumpStby");
        sym.setProperty("ControlMode", "REMOTE");
        sym.setProperty("HoaMode", "AUTO");

        org.junit.jupiter.api.Assertions.assertEquals(
                cz.maramat.scada.render.EquipmentState.STANDBY,
                EquipmentSymbolRenderer.resolveState(sym, project, tags));

        BufferedImage img = new BufferedImage(140, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            new EquipmentSymbolRenderer().paintSymbol(g, sym, 10, project, tags, new SymbolLayerCache(), 500L, false);
        } finally {
            g.dispose();
        }
        assertTrue((img.getRGB(60, 80) >>> 24) > 0);
    }

    @Test
    void rendersAllNewPumpsAndActuators() {
        ScadaProject project = new ScadaProject();
        ScadaTag run = new ScadaTag("RunTag", DataType.BOOL, TagSourceType.INTERNAL);
        run.setInitialValue("1");
        run.resetRuntimeToInitial();
        project.putTag(run);

        InternalTagProvider tags = new InternalTagProvider();
        SymbolLayerCache cache = new SymbolLayerCache();
        EquipmentSymbolRenderer renderer = new EquipmentSymbolRenderer();

        List<String> types = List.of(
                "SubmersiblePump", "PositiveDisplacementPump", "GearPump", "DiaphragmPump",
                "PeristalticPump", "ScrewPump", "VacuumPump", "BoosterPump",
                "PneumaticActuator", "ElectricActuator", "HydraulicActuator"
        );

        for (String t : types) {
            ScadaSymbolInstance sym = cz.maramat.scada.model.SymbolCatalog.createInstance(t, 1, 1, 1);
            sym.getTagBindings().put("RunTag", "RunTag");
            if (t.endsWith("Actuator")) {
                sym.setProperty("ActuatorState", "TRAVELLING");
                sym.setProperty("OpenLimit", "false");
                sym.setProperty("ClosedLimit", "false");
            }
            BufferedImage img = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                renderer.paintSymbol(g, sym, 10, project, tags, cache, 500L, false);
            } finally {
                g.dispose();
            }
            assertTrue((img.getRGB(40, 40) >>> 24) > 0, "Expected non-empty render for " + t);
        }
    }

    @Test
    void rendersAllNewValvesWithPositionFillAndFailSafeBadges() {
        ScadaProject project = new ScadaProject();
        ScadaTag pos = new ScadaTag("ValvePos", DataType.FLOAT, TagSourceType.INTERNAL);
        pos.setInitialValue("65.0");
        pos.resetRuntimeToInitial();
        project.putTag(pos);

        InternalTagProvider tags = new InternalTagProvider();
        SymbolLayerCache cache = new SymbolLayerCache();
        EquipmentSymbolRenderer renderer = new EquipmentSymbolRenderer();

        List<String> valves = List.of(
                "GateValve", "GlobeValve", "BallValve", "ButterflyValve",
                "NeedleValve", "PlugValve", "DiaphragmValve", "FourWayValve",
                "MotorizedValve", "PneumaticValve", "ControlValve", "Damper"
        );

        for (int i = 0; i < valves.size(); i++) {
            String v = valves.get(i);
            ScadaSymbolInstance sym = cz.maramat.scada.model.SymbolCatalog.createInstance(v, 1, 1, 1);
            sym.getTagBindings().put("PositionTag", "ValvePos");
            sym.setProperty("FailSafe", (i % 2 == 0) ? "FO" : "FC");
            BufferedImage img = new BufferedImage(120, 120, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                renderer.paintSymbol(g, sym, 10, project, tags, cache, 500L, false);
            } finally {
                g.dispose();
            }
            boolean hasOpaque = false;
            for (int px = 15; px < 50 && !hasOpaque; px += 5) {
                for (int py = 15; py < 55 && !hasOpaque; py += 5) {
                    if ((img.getRGB(px, py) >>> 24) > 0) {
                        hasOpaque = true;
                    }
                }
            }
            assertTrue(hasOpaque, "Expected non-empty render for " + v);
        }
    }

    @Test
    void rendersAllNewTanksWithSetpointAndAlarmBands() {
        ScadaProject project = new ScadaProject();
        ScadaTag lvl = new ScadaTag("TankLvl", DataType.FLOAT, TagSourceType.INTERNAL);
        lvl.setInitialValue("72.0");
        lvl.resetRuntimeToInitial();
        project.putTag(lvl);

        InternalTagProvider tags = new InternalTagProvider();
        SymbolLayerCache cache = new SymbolLayerCache();
        EquipmentSymbolRenderer renderer = new EquipmentSymbolRenderer();

        List<String> vessels = List.of(
                "ConeBottomTank", "SphericalTank", "ClosedTank", "Silo", "Hopper", "Reactor"
        );

        for (String v : vessels) {
            ScadaSymbolInstance sym = cz.maramat.scada.model.SymbolCatalog.createInstance(v, 1, 1, 1);
            sym.getTagBindings().put("LevelTag", "TankLvl");
            sym.setProperty("Setpoint", "60");
            sym.setProperty("AlarmHH", "90");
            sym.setProperty("AlarmH", "80");
            sym.setProperty("AlarmL", "20");
            sym.setProperty("AlarmLL", "10");

            BufferedImage img = new BufferedImage(180, 200, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                renderer.paintSymbol(g, sym, 10, project, tags, cache, 500L, false);
            } finally {
                g.dispose();
            }
            assertTrue((img.getRGB(55, 70) >>> 24) > 0, "Expected non-empty render for " + v);
        }
    }
}

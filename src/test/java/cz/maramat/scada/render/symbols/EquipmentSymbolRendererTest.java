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
}

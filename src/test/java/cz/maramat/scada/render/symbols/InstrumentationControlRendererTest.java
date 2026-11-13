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

class InstrumentationControlRendererTest {

    @Test
    void rendersAllEpoch5SensorsAndControls() {
        ScadaProject project = new ScadaProject();
        ScadaTag val = new ScadaTag("PV_1", DataType.FLOAT, TagSourceType.INTERNAL);
        val.setRuntimeValue(64.5);
        project.putTag(val);

        InternalTagProvider tags = new InternalTagProvider();
        SymbolLayerCache cache = new SymbolLayerCache();
        EquipmentSymbolRenderer renderer = new EquipmentSymbolRenderer();

        List<String> types = List.of(
                "PressureSensor", "TempSensor", "FlowMeter", "LevelSwitch", "ProximitySensor",
                "PushButton", "SelectorSwitch", "EStopMushroom", "PilotLamp",
                "NumericReadout", "NumericSetpoint", "BarGraph", "TrendChart"
        );

        BufferedImage img = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (int i = 0; i < types.size(); i++) {
                ScadaSymbolInstance sym = new ScadaSymbolInstance(types.get(i), "IC-" + i, 2, 2, 6, 6);
                sym.getTagBindings().put("ValueTag", "PV_1");
                sym.getProperties().put("EngineeringUnits", "bar");
                renderer.paintSymbol(g, sym, 10, project, tags, cache, 1000L + i * 50L, true);
            }
        } finally {
            g.dispose();
        }

        int centerArgb = img.getRGB(50, 50);
        assertTrue((centerArgb >>> 24) > 0);
    }
}

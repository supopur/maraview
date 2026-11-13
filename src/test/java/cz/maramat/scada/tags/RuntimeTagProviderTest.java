package cz.maramat.scada.tags;

import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.ScadaProject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeTagProviderTest {

    @Test
    void internalAndMaraflowBridgeProvidersWorkCorrectly() {
        ScadaProject project = new ScadaProject();
        ScadaTag internalTag = new ScadaTag("SimValve", DataType.BOOL, TagSourceType.INTERNAL);
        ScadaTag plcTag = new ScadaTag("PumpSpeed", DataType.INT, TagSourceType.MFL_LINK);
        plcTag.setAddressOrRef("VR1");
        project.putTag(internalTag);
        project.putTag(plcTag);

        InternalTagProvider internalProvider = new InternalTagProvider();
        internalProvider.writeTag(project, "SimValve", 1.0);
        assertEquals(1.0, internalProvider.readTag(project, "SimValve"));

        Map<String, Double> plcMem = new HashMap<>();
        plcMem.put("VR1", 1450.0);
        final boolean[] online = {true};
        PlcTagEndpoint endpoint = new PlcTagEndpoint() {
            @Override
            public double readOperand(String operandOrAddress) {
                return plcMem.getOrDefault(operandOrAddress, 0.0);
            }

            @Override
            public void writeOperand(String operandOrAddress, double value) {
                plcMem.put(operandOrAddress, value);
            }

            @Override
            public boolean isOnline() {
                return online[0];
            }
        };

        MaraflowBridgeTagProvider bridge = new MaraflowBridgeTagProvider(endpoint);
        assertTrue(bridge.isConnected());
        bridge.syncAll(project);
        assertEquals(1450.0, plcTag.getRuntimeValue());
        assertEquals(TagQuality.GOOD, plcTag.getRuntimeQuality());

        bridge.writeTag(project, "PumpSpeed", 1750.0);
        assertEquals(1750.0, plcMem.get("VR1"));

        online[0] = false;
        bridge.syncAll(project);
        assertFalse(bridge.isConnected());
        assertEquals(TagQuality.DISCONNECTED, plcTag.getRuntimeQuality());
        assertEquals(TagQuality.GOOD, internalTag.getRuntimeQuality());
    }
}

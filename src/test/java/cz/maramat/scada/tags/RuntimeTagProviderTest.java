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

    @Test
    void readTagNeverBlocksOnEndpointIo() {
        ScadaProject project = new ScadaProject();
        ScadaTag plcTag = new ScadaTag("Temp", DataType.FLOAT, TagSourceType.MFL_LINK);
        plcTag.setAddressOrRef("VR10");
        plcTag.setRuntimeValue(42.5);
        project.putTag(plcTag);

        PlcTagEndpoint throwingOnRead = new PlcTagEndpoint() {
            @Override
            public double readOperand(String operandOrAddress) {
                throw new AssertionError("readTag must not perform blocking endpoint I/O");
            }

            @Override
            public void writeOperand(String operandOrAddress, double value) {
            }

            @Override
            public boolean isOnline() {
                return true;
            }
        };

        MaraflowBridgeTagProvider bridge = new MaraflowBridgeTagProvider(throwingOnRead);
        assertEquals(42.5, bridge.readTag(project, "Temp"));
    }

    @Test
    void floatDeadbandFiltersNoiseAndTracksChangedTags() {
        ScadaProject project = new ScadaProject();
        ScadaTag temp = new ScadaTag("T1", DataType.FLOAT, TagSourceType.MFL_LINK);
        temp.setAddressOrRef("T1");
        temp.setRuntimeValue(20.0);
        ScadaTag run = new ScadaTag("Run", DataType.BOOL, TagSourceType.MFL_LINK);
        run.setAddressOrRef("Run");
        run.setRuntimeValue(1.0);
        project.putTag(temp);
        project.putTag(run);

        MaraflowBridgeTagProvider bridge = new MaraflowBridgeTagProvider(null);

        // Sub-deadband fluctuation (< 1e-4) must not mark tag changed or advance revision
        long rev0 = bridge.getRevision();
        boolean changedSmall = bridge.applySnapshot(project,
                new MaraflowBridgeTagProvider.Snapshot(Map.of("T1", 20.00005, "Run", 1.0), true));
        assertFalse(changedSmall);
        assertEquals(rev0, bridge.getRevision());
        assertTrue(bridge.getLastChangedTagNames().isEmpty());
        assertEquals(20.0, temp.getRuntimeValue());

        // Cumulative change >= 1e-4 triggers update ONLY for T1
        boolean changedBig = bridge.applySnapshot(project,
                new MaraflowBridgeTagProvider.Snapshot(Map.of("T1", 20.00012, "Run", 1.0), true));
        assertTrue(changedBig);
        assertEquals(rev0 + 1, bridge.getRevision());
        assertEquals(20.00012, temp.getRuntimeValue());
        assertEquals(java.util.Set.of("T1"), bridge.getLastChangedTagNames());
    }
}

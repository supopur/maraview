package cz.maramat.scada.io;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.model.CommSettings;
import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.PipeDiameter;
import cz.maramat.scada.model.PipePort;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.tags.AlarmConfig;
import cz.maramat.scada.tags.MflOrigin;
import cz.maramat.scada.tags.ModbusBinding;
import cz.maramat.scada.tags.ScadaTag;
import cz.maramat.scada.tags.TagSourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MslProjectSerializerTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripAndDeterministicSerialization() throws IOException {
        ScadaProject project = new ScadaProject();
        project.setProjectName("BoilerHouseHMI");
        project.setAuthor("M. Smekal");
        project.setDescription("Line \"A\"\nPrimary steam loop");
        project.setLinkedMflPath("/projects/boiler.mfl");

        CommSettings comm = project.getCommSettings();
        comm.setMode(CommSettings.Mode.MODBUS_TCP);
        comm.setHost("192.168.1.50");
        comm.setTcpPort(1502);
        comm.setSlaveId(3);
        comm.setPollIntervalMs(25);

        ScadaTag pumpRun = new ScadaTag("Pump1_Run", DataType.BOOL, TagSourceType.MFL_LINK);
        pumpRun.setMflOrigin(MflOrigin.HARDWARE_IO);
        pumpRun.setAddressOrRef("DO0");
        pumpRun.setInitialValue("0");
        pumpRun.setDescription("Feedwater pump run command");
        AlarmConfig alarm = new AlarmConfig();
        alarm.setEnabled(true);
        alarm.setFaultOnTrue(true);
        alarm.setAlarmMessage("Pump 1 Fault");
        pumpRun.setAlarmConfig(alarm);
        project.putTag(pumpRun);

        ScadaTag tankLevel = new ScadaTag("Tank1_Level", DataType.FLOAT, TagSourceType.MODBUS);
        tankLevel.setEngineeringUnits("%");
        tankLevel.setMinValue(0.0);
        tankLevel.setMaxValue(100.0);
        tankLevel.setModbusBinding(new ModbusBinding(3, 4, 100, 0.1, -5.0));
        tankLevel.getAlarmConfig().setEnabled(true);
        tankLevel.getAlarmConfig().setHighLimit(85.0);
        tankLevel.getAlarmConfig().setLowLimit(15.0);
        project.putTag(tankLevel);

        ScadaScreen overview = project.getScreens().getFirst();
        overview.setName("Overview");
        overview.setBackgroundColorHex(ScadaScreen.BG_DARK_CONTROL_ROOM);
        overview.setShowGrid(false);

        ScadaSymbolInstance pump = new ScadaSymbolInstance("CentrifugalPump", "P-101", 12, 18, 8, 10);
        pump.setRotationDeg(90);
        pump.setFlipH(true);
        pump.setZIndex(2);
        pump.setTooltip("Main feed pump");
        pump.getProperties().put("FluidType", "WATER");
        pump.getTagBindings().put("RunTag", "Pump1_Run");
        pump.getPorts().add(new PipePort("suction", "Suction", 0, 5, CardinalDirection.WEST));
        pump.getPorts().add(new PipePort("discharge", "Discharge", 3, 0, CardinalDirection.NORTH));
        overview.getSymbols().add(pump);

        ScadaPipe pipe = new ScadaPipe("pipe-1");
        pipe.setSourceSymbolId(pump.getId());
        pipe.setSourcePortId("discharge");
        pipe.setDiameter(PipeDiameter.LARGE);
        pipe.setFluidType(FluidType.HOT_WATER_STEAM);
        pipe.setAutoFlow(false);
        pipe.setFlowTag("Pump1_Run");
        pipe.setLabel("Header-A");
        pipe.setVertices(List.of(new GridPoint(15, 18), new GridPoint(15, 10), new GridPoint(30, 10)));
        overview.getPipes().add(pipe);

        File mslFile = tempDir.resolve("boiler.msl").toFile();
        MslProjectSerializer.saveProject(project, mslFile);
        assertFalse(project.isDirty());

        ScadaProject loaded = MslProjectSerializer.loadProject(mslFile);
        assertEquals("BoilerHouseHMI", loaded.getProjectName());
        assertEquals("Line \"A\"\nPrimary steam loop", loaded.getDescription());
        assertEquals(CommSettings.Mode.MODBUS_TCP, loaded.getCommSettings().getMode());
        assertEquals("192.168.1.50", loaded.getCommSettings().getHost());
        assertEquals(1502, loaded.getCommSettings().getTcpPort());

        assertEquals(2, loaded.getTags().size());
        ScadaTag loadedLevel = loaded.getTag("Tank1_Level");
        assertNotNull(loadedLevel);
        assertEquals(DataType.FLOAT, loadedLevel.getDataType());
        assertEquals("%", loadedLevel.getEngineeringUnits());
        assertNotNull(loadedLevel.getModbusBinding());
        assertEquals(100, loadedLevel.getModbusBinding().getAddress());
        assertEquals(85.0, loadedLevel.getAlarmConfig().getHighLimit());

        ScadaScreen loadedScreen = loaded.getScreens().getFirst();
        assertEquals("#2B3036", loadedScreen.getBackgroundColorHex());
        assertFalse(loadedScreen.isShowGrid());
        assertEquals(1, loadedScreen.getSymbols().size());
        ScadaSymbolInstance loadedPump = loadedScreen.getSymbols().getFirst();
        assertEquals(90, loadedPump.getRotationDeg());
        assertTrue(loadedPump.isFlipH());
        assertEquals("Pump1_Run", loadedPump.getTagBindings().get("RunTag"));
        assertEquals(2, loadedPump.getPorts().size());

        assertEquals(1, loadedScreen.getPipes().size());
        ScadaPipe loadedPipe = loadedScreen.getPipes().getFirst();
        assertEquals(PipeDiameter.LARGE, loadedPipe.getDiameter());
        assertEquals(FluidType.HOT_WATER_STEAM, loadedPipe.getFluidType());
        assertFalse(loadedPipe.isAutoFlow());
        assertEquals(3, loadedPipe.getVertices().size());
        assertEquals(new GridPoint(30, 10), loadedPipe.getVertices().get(2));

        // Deterministic output check
        assertEquals(MslProjectSerializer.toJsonString(project), MslProjectSerializer.toJsonString(loaded));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(IOException.class, () -> JsonCodec.parseObject("{ \"unclosed\": 123 "));
        assertThrows(IOException.class, () -> JsonCodec.parseObject("[1, 2, 3]"));
    }
}

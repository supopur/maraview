package cz.maramat.scada.tags;

import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.ScadaProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MflTagImporterTest {

    @TempDir
    Path tempDir;

    @Test
    void importsSymbolicStXVarHardwareIoAndVirtualRegisters() throws IOException {
        Properties props = new Properties();
        props.setProperty("project.name", "PumpStation");
        props.setProperty("project.tagCount", "2");
        props.setProperty("project.tag.0.name", "MotorCmd");
        props.setProperty("project.tag.0.type", "BOOL");
        props.setProperty("project.tag.0.desc", "Main motor command");
        props.setProperty("project.tag.1.name", "VR10");
        props.setProperty("project.tag.1.type", "INT");
        props.setProperty("project.tag.1.desc", "Speed setpoint");

        props.setProperty("project.pageCount", "1");
        props.setProperty("page.0.name", "Main");
        props.setProperty("page.0.stVarCount", "1");
        props.setProperty("page.0.stVar.0.scope", "VAR");
        props.setProperty("page.0.stVar.0.name", "TankTemp");
        props.setProperty("page.0.stVar.0.type", "FLOAT");
        props.setProperty("page.0.stVar.0.init", "22.5");
        props.setProperty("page.0.stVar.0.desc", "Vessel temperature");

        props.setProperty("page.0.blockCount", "3");
        // xVar BoolVar
        props.setProperty("page.0.block.0.defId", "Visual.Vars.BoolVar");
        props.setProperty("page.0.block.0.name", "StartBtn");
        props.setProperty("page.0.block.0.out.0.type", "BOOL");
        props.setProperty("page.0.block.0.propCount", "1");
        props.setProperty("page.0.block.0.prop.0.k", "CurrentValue");
        props.setProperty("page.0.block.0.prop.0.v", "1");

        // IO.IREF DI0
        props.setProperty("page.0.block.1.defId", "IO.IREF");
        props.setProperty("page.0.block.1.name", "IREF_1");
        props.setProperty("page.0.block.1.propCount", "1");
        props.setProperty("page.0.block.1.prop.0.k", "Channel");
        props.setProperty("page.0.block.1.prop.0.v", "DI0");

        // IO.OREF AO2
        props.setProperty("page.0.block.2.defId", "IO.OREF");
        props.setProperty("page.0.block.2.name", "OREF_1");
        props.setProperty("page.0.block.2.propCount", "1");
        props.setProperty("page.0.block.2.prop.0.k", "Channel");
        props.setProperty("page.0.block.2.prop.0.v", "AO2");

        // Ladder rung referencing VR20
        props.setProperty("page.0.rungCount", "1");
        props.setProperty("page.0.rung.0.coilCount", "1");
        props.setProperty("page.0.rung.0.coil.0.kind", "COIL");
        props.setProperty("page.0.rung.0.coil.0.operand", "VR20");

        File mflFile = tempDir.resolve("station.mfl").toFile();
        try (FileOutputStream out = new FileOutputStream(mflFile)) {
            props.storeToXML(out, "Maraflow PLC Project (.mfl)", "UTF-8");
        }

        ScadaProject scada = new ScadaProject();
        ScadaTag internal = new ScadaTag("LocalTest", DataType.INT, TagSourceType.INTERNAL);
        scada.putTag(internal);

        int count = MflTagImporter.importIntoProject(mflFile, scada);
        assertEquals(7, count);
        assertNotNull(scada.getTag("LocalTest"));
        assertEquals(MflOrigin.SYMBOLIC_TAG, scada.getTag("MotorCmd").getMflOrigin());
        assertEquals(MflOrigin.VIRTUAL_REGISTER, scada.getTag("VR10").getMflOrigin());
        assertEquals(MflOrigin.ST_VARIABLE, scada.getTag("TankTemp").getMflOrigin());
        assertEquals(22.5, scada.getTag("TankTemp").getRuntimeValue(), 1e-6);
        assertEquals(MflOrigin.X_VAR, scada.getTag("StartBtn").getMflOrigin());
        assertEquals(1.0, scada.getTag("StartBtn").getRuntimeValue(), 1e-6);
        assertEquals(MflOrigin.HARDWARE_IO, scada.getTag("DI0").getMflOrigin());
        assertEquals(DataType.INT, scada.getTag("AO2").getDataType());
        assertEquals(MflOrigin.VIRTUAL_REGISTER, scada.getTag("VR20").getMflOrigin());

        // Customize engineering units on TankTemp, remove MotorCmd from .mfl, and re-import
        scada.getTag("TankTemp").setEngineeringUnits("°C");
        props.setProperty("project.tagCount", "1");
        props.setProperty("project.tag.0.name", "VR10");
        props.setProperty("project.tag.0.type", "INT");
        try (FileOutputStream out = new FileOutputStream(mflFile)) {
            props.storeToXML(out, "Maraflow PLC Project (.mfl)", "UTF-8");
        }

        MflTagImporter.importIntoProject(mflFile, scada);
        assertNull(scada.getTag("MotorCmd"));
        assertNotNull(scada.getTag("LocalTest"));
        assertEquals("°C", scada.getTag("TankTemp").getEngineeringUnits());
    }

    @Test
    void importsBlockOutputPins() {
        Properties props = new Properties();
        props.setProperty("project.pageCount", "1");
        props.setProperty("page.0.name", "Main");
        props.setProperty("page.0.blockCount", "1");
        props.setProperty("page.0.block.0.defId", "Logic.AND");
        props.setProperty("page.0.block.0.name", "And1");
        props.setProperty("page.0.block.0.inCount", "2");
        props.setProperty("page.0.block.0.in.0.short", "En");
        props.setProperty("page.0.block.0.in.0.type", "BOOL");
        props.setProperty("page.0.block.0.in.1.short", "A");
        props.setProperty("page.0.block.0.in.1.type", "BOOL");
        props.setProperty("page.0.block.0.outCount", "1");
        props.setProperty("page.0.block.0.out.0.short", "Y");
        props.setProperty("page.0.block.0.out.0.type", "BOOL");

        var imported = MflTagImporter.parseMflProperties(props);
        ScadaTag pinTag = imported.get("And1.Y");
        assertNotNull(pinTag);
        assertEquals(MflOrigin.BLOCK_PIN, pinTag.getMflOrigin());
        assertEquals("And1.Y", pinTag.getAddressOrRef());
        assertEquals(DataType.BOOL, pinTag.getDataType());

        ScadaTag inPinTag = imported.get("And1.A");
        assertNull(inPinTag);
        assertNull(imported.get("And1.En"));
    }

    @Test
    void importsVisualButtonsAndModbusSlaveMap() {
        Properties props = new Properties();
        props.setProperty("project.pageCount", "1");
        props.setProperty("page.0.name", "Main");
        props.setProperty("page.0.graphicCount", "1");
        props.setProperty("page.0.graphic.0.kind", "VISUAL_BUTTON");
        props.setProperty("page.0.graphic.0.text", "StartBtn");
        props.setProperty("page.0.graphic.0.boundTargetPin", "And1.A");
        props.setProperty("project.unit.stationId", "3");
        props.setProperty("project.unit.modbusMap.count", "1");
        props.setProperty("project.unit.modbusMap.0.table", "HOLDING_REGISTER");
        props.setProperty("project.unit.modbusMap.0.addr", "12");
        props.setProperty("project.unit.modbusMap.0.target", "INT:0");

        var imported = MflTagImporter.parseMflProperties(props);
        ScadaTag btnTag = imported.get("StartBtn");
        assertNotNull(btnTag);
        assertEquals("And1.A", btnTag.getAddressOrRef());
        assertEquals(DataType.BOOL, btnTag.getDataType());

        ScadaTag mbTag = imported.get("MHR12");
        assertNotNull(mbTag);
        assertEquals(TagSourceType.MODBUS, mbTag.getSourceType());
        assertNotNull(mbTag.getModbusBinding());
        assertEquals(3, mbTag.getModbusBinding().getSlaveId());
        assertEquals(12, mbTag.getModbusBinding().getAddress());
    }
}

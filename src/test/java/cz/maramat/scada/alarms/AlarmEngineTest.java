package cz.maramat.scada.alarms;

import cz.maramat.scada.model.DataType;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.tags.AlarmConfig;
import cz.maramat.scada.tags.ScadaTag;
import cz.maramat.scada.tags.TagSourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlarmEngineTest {

    @Test
    void evaluatesThresholdsTracksAcknowledgmentAndClears() {
        ScadaProject project = new ScadaProject();
        ScadaTag temp = new ScadaTag("BoilerTemp", DataType.FLOAT, TagSourceType.INTERNAL);
        AlarmConfig cfg = new AlarmConfig();
        cfg.setEnabled(true);
        cfg.setHighLimit(80.0);
        cfg.setHighHighLimit(95.0);
        cfg.setLowLimit(10.0);
        temp.setAlarmConfig(cfg);
        temp.setRuntimeValue(50.0);
        project.putTag(temp);

        AlarmEngine engine = new AlarmEngine();
        engine.evaluate(project, 1000L);
        assertTrue(engine.getAlarms().isEmpty());

        // Trigger HIGH
        temp.setRuntimeValue(85.0);
        engine.evaluate(project, 2000L);
        List<AlarmEntry> alarms = engine.getAlarms();
        assertEquals(1, alarms.size());
        assertEquals(AlarmEntry.Severity.HIGH, alarms.getFirst().getSeverity());
        assertFalse(alarms.getFirst().isAcknowledged());
        assertTrue(engine.hasUnacknowledged());

        // Escalate to HIGH_HIGH -> resets acknowledgment
        engine.acknowledge("BoilerTemp");
        assertFalse(engine.hasUnacknowledged());
        temp.setRuntimeValue(98.0);
        engine.evaluate(project, 3000L);
        assertEquals(AlarmEntry.Severity.HIGH_HIGH, engine.getAlarms().getFirst().getSeverity());
        assertTrue(engine.hasUnacknowledged());

        // Return to normal while unacknowledged -> stays in list as inactive unack
        temp.setRuntimeValue(50.0);
        engine.evaluate(project, 4000L);
        assertEquals(1, engine.getAlarms().size());
        assertFalse(engine.getAlarms().getFirst().isActive());

        // Acknowledge -> cleared from active list
        engine.acknowledge("BoilerTemp");
        assertTrue(engine.getAlarms().isEmpty());
    }
}

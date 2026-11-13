package cz.maramat.scada.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipePortTransformTest {

    @Test
    void rotatesAndFlipsPortsCorrectly() {
        // CentrifugalPump: 8G x 10G, discharge at (3, 0) pointing NORTH
        PipePort discharge = new PipePort("discharge", "Discharge", 3, 0, CardinalDirection.NORTH);

        PipePort.WorldPort p0 = discharge.resolveWorldPort(10, 20, 8, 10, 0, false, false);
        assertEquals(new GridPoint(13, 20), p0.position());
        assertEquals(CardinalDirection.NORTH, p0.direction());

        PipePort.WorldPort p90 = discharge.resolveWorldPort(10, 20, 8, 10, 90, false, false);
        assertEquals(new GridPoint(20, 23), p90.position());
        assertEquals(CardinalDirection.EAST, p90.direction());

        PipePort.WorldPort p180 = discharge.resolveWorldPort(10, 20, 8, 10, 180, false, false);
        assertEquals(new GridPoint(15, 30), p180.position());
        assertEquals(CardinalDirection.SOUTH, p180.direction());

        PipePort.WorldPort pFlipV = discharge.resolveWorldPort(10, 20, 8, 10, 0, false, true);
        assertEquals(new GridPoint(13, 30), pFlipV.position());
        assertEquals(CardinalDirection.SOUTH, pFlipV.direction());
    }
}

package cz.maramat.scada.routing;

import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.ScadaPipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipeEdgeDragControllerTest {

    @Test
    void hitsMovesAndSplitsPipeSegments() {
        ScadaPipe pipe = new ScadaPipe("p1");
        pipe.setVertices(List.of(
                new GridPoint(0, 10),
                new GridPoint(10, 10),
                new GridPoint(10, 20),
                new GridPoint(20, 20)
        ));

        PipeEdgeDragController.SegmentHit hit = PipeEdgeDragController.hitTest(List.of(pipe), 10.2, 15.0, 0.5);
        assertNotNull(hit);
        assertEquals(1, hit.segmentIndex());

        // Drag vertical middle segment right by 4G
        int newIdx = PipeEdgeDragController.moveSegment(pipe, 1, 4);
        assertEquals(1, newIdx);
        assertEquals(new GridPoint(14, 10), pipe.getVertices().get(1));
        assertEquals(new GridPoint(14, 20), pipe.getVertices().get(2));

        // Add bend on first horizontal segment
        PipeEdgeDragController.addBendAt(pipe, 0, new GridPoint(6, 10));
        assertTrue(pipe.getVertices().size() >= 6);
    }
}

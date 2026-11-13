package cz.maramat.scada.routing;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.PipePort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipeRouterTest {

    @Test
    void routesStraightLBendZBendAndUBendOrthogonally() {
        // 1-segment straight run
        List<GridPoint> straight = PipeRouter.route(
                new PipePort.WorldPort(new GridPoint(10, 10), CardinalDirection.EAST),
                new PipePort.WorldPort(new GridPoint(25, 10), CardinalDirection.WEST)
        );
        assertOrthogonal(straight, new GridPoint(10, 10), new GridPoint(25, 10));
        assertEquals(2, straight.size());

        // 3-segment Z-bend
        List<GridPoint> zBend = PipeRouter.route(
                new PipePort.WorldPort(new GridPoint(10, 10), CardinalDirection.EAST),
                new PipePort.WorldPort(new GridPoint(30, 20), CardinalDirection.WEST)
        );
        assertOrthogonal(zBend, new GridPoint(10, 10), new GridPoint(30, 20));
        assertEquals(4, zBend.size());

        // 2-segment L-bend
        List<GridPoint> lBend = PipeRouter.route(
                new PipePort.WorldPort(new GridPoint(10, 10), CardinalDirection.EAST),
                new PipePort.WorldPort(new GridPoint(25, 25), CardinalDirection.NORTH)
        );
        assertOrthogonal(lBend, new GridPoint(10, 10), new GridPoint(25, 25));

        // U-bend (same direction NORTH -> NORTH)
        List<GridPoint> uBend = PipeRouter.route(
                new PipePort.WorldPort(new GridPoint(10, 20), CardinalDirection.NORTH),
                new PipePort.WorldPort(new GridPoint(24, 20), CardinalDirection.NORTH)
        );
        assertOrthogonal(uBend, new GridPoint(10, 20), new GridPoint(24, 20));
        assertTrue(uBend.size() >= 4);
    }

    private static void assertOrthogonal(List<GridPoint> pts, GridPoint expectedStart, GridPoint expectedEnd) {
        assertTrue(pts.size() >= 2);
        assertEquals(expectedStart, pts.getFirst());
        assertEquals(expectedEnd, pts.getLast());
        for (int i = 0; i < pts.size() - 1; i++) {
            GridPoint a = pts.get(i);
            GridPoint b = pts.get(i + 1);
            assertTrue(a.x() == b.x() || a.y() == b.y(),
                    "Non-orthogonal segment from " + a + " to " + b);
        }
    }
}

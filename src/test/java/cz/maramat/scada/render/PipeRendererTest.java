package cz.maramat.scada.render;

import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.GridPoint;
import cz.maramat.scada.model.PipeDiameter;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.routing.FlowState;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PipeRendererTest {

    @Test
    void renders3PassPipeAndAnimatedFlowCore() {
        ScadaPipe pipe = new ScadaPipe("p1");
        pipe.setDiameter(PipeDiameter.LARGE);
        pipe.setFluidType(FluidType.WATER);
        pipe.setVertices(List.of(
                new GridPoint(2, 2),
                new GridPoint(10, 2),
                new GridPoint(10, 10)
        ));

        BufferedImage img = new BufferedImage(160, 160, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            PipeRenderer.paintPipe(g, pipe, 10, new FlowState(true, 1.0, FluidType.WATER), true, 4.0, true);
        } finally {
            g.dispose();
        }

        // Pixel along the horizontal pipe centerline (60, 20) must be opaque
        int centerArgb = img.getRGB(60, 20);
        assertTrue((centerArgb >>> 24) > 0);
    }

    @Test
    void stoppedSimulationHidesBlueWaterInfill() {
        ScadaPipe pipe = new ScadaPipe("p2");
        pipe.setDiameter(PipeDiameter.LARGE);
        pipe.setFluidType(FluidType.WATER);
        pipe.setVertices(List.of(
                new GridPoint(2, 2),
                new GridPoint(10, 2)
        ));

        BufferedImage stoppedImg = new BufferedImage(140, 60, BufferedImage.TYPE_INT_ARGB);
        BufferedImage runningImg = new BufferedImage(140, 60, BufferedImage.TYPE_INT_ARGB);

        Graphics2D gStopped = stoppedImg.createGraphics();
        Graphics2D gRunning = runningImg.createGraphics();
        try {
            PipeRenderer.paintPipe(gStopped, pipe, 10, new FlowState(true, 1.0, FluidType.WATER), false, 0.0, false);
            PipeRenderer.paintPipe(gRunning, pipe, 10, new FlowState(true, 1.0, FluidType.WATER), true, 4.0, false);
        } finally {
            gStopped.dispose();
            gRunning.dispose();
        }

        int stoppedCenter = stoppedImg.getRGB(60, 20);
        int runningCenter = runningImg.getRGB(60, 20);
        int stoppedR = (stoppedCenter >> 16) & 0xFF;
        int stoppedB = stoppedCenter & 0xFF;
        int runningR = (runningCenter >> 16) & 0xFF;
        int runningB = runningCenter & 0xFF;

        // Stopped pipe is solid metallic grey (low B-R), running water pipe is vividly blue (B >> R)
        assertTrue((stoppedB - stoppedR) < 35, "Stopped pipe should be metallic grey without blue water infill");
        assertTrue((runningB - runningR) > 100, "Running water pipe should have blue water infill");
    }

    @Test
    void rendersPneumaticAndCapillaryLineStyles() {
        for (cz.maramat.scada.model.ConnectionType ct : List.of(
                cz.maramat.scada.model.ConnectionType.PNEUMATIC,
                cz.maramat.scada.model.ConnectionType.CAPILLARY)) {
            ScadaPipe line = new ScadaPipe("line-" + ct.name());
            line.setConnectionType(ct);
            line.setVertices(List.of(new GridPoint(1, 3), new GridPoint(10, 3)));
            BufferedImage img = new BufferedImage(120, 60, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                PipeRenderer.paintPipe(g, line, 10, new FlowState(true, 1.0, FluidType.GAS), true, 3.0, false);
            } finally {
                g.dispose();
            }
            assertTrue((img.getRGB(50, 30) >>> 24) > 0, "Expected opaque pixels for " + ct);
        }
    }
}

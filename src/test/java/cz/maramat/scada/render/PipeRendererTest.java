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
}

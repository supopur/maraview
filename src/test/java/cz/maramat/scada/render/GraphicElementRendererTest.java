package cz.maramat.scada.render;

import cz.maramat.scada.io.MslProjectSerializer;
import cz.maramat.scada.model.ScadaGraphicElement;
import cz.maramat.scada.model.ScadaProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicElementRendererTest {

    @TempDir
    Path tempDir;

    @Test
    void rendersActiveAndInactiveIndicatorColors() {
        ScadaGraphicElement rect = new ScadaGraphicElement(ScadaGraphicElement.Kind.RECTANGLE, 0, 0, 4, 4);
        rect.setIndicatorEnabled(true);
        rect.setIndicatorPin("And1.Y");
        rect.setIndicatorActiveColor(new Color(0x00, 0xFF, 0x00));
        rect.setIndicatorInactiveColor(new Color(0x11, 0x22, 0x33));

        BufferedImage activeImg = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ag = activeImg.createGraphics();
        try {
            GraphicElementRenderer.paint(ag, rect, 10, false, true);
        } finally {
            ag.dispose();
        }

        BufferedImage inactiveImg = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig = inactiveImg.createGraphics();
        try {
            GraphicElementRenderer.paint(ig, rect, 10, false, false);
        } finally {
            ig.dispose();
        }

        assertEquals(new Color(0x00, 0xFF, 0x00).getRGB(), activeImg.getRGB(20, 20));
        assertEquals(new Color(0x11, 0x22, 0x33).getRGB(), inactiveImg.getRGB(20, 20));
    }

    @Test
    void roundTripsIndicatorPropertiesInMslSerializer() throws IOException {
        ScadaProject project = new ScadaProject();
        ScadaGraphicElement circle = new ScadaGraphicElement(ScadaGraphicElement.Kind.CIRCLE, 2, 3, 4, 4);
        circle.setIndicatorEnabled(true);
        circle.setIndicatorPin("TON1.Q");
        circle.setIndicatorActiveColor(new Color(0xAA, 0xBB, 0xCC));
        circle.setIndicatorInactiveColor(new Color(0x12, 0x34, 0x56));
        project.getScreens().getFirst().getGraphics().add(circle);

        File out = tempDir.resolve("indicator.msl").toFile();
        MslProjectSerializer.saveProject(project, out);

        ScadaProject loaded = MslProjectSerializer.loadProject(out);
        ScadaGraphicElement loadedElem = loaded.getScreens().getFirst().getGraphics().getFirst();
        assertTrue(loadedElem.isIndicatorEnabled());
        assertEquals("TON1.Q", loadedElem.getIndicatorPin());
        assertEquals(new Color(0xAA, 0xBB, 0xCC), loadedElem.getIndicatorActiveColor());
        assertEquals(new Color(0x12, 0x34, 0x56), loadedElem.getIndicatorInactiveColor());
    }
}

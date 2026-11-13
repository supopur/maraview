package cz.maramat.scada.render;

import cz.maramat.scada.model.CardinalDirection;
import cz.maramat.scada.render.ScadaPalette.Material;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScadaShaderTest {

    @Test
    void rendersAllPrimitivesWithoutExceptions() {
        BufferedImage img = new BufferedImage(240, 240, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ScadaShader.paintCylinderH(g, 10, 10, 80, 20, Material.PIPE_WALL);
            ScadaShader.paintCylinderV(g, 100, 10, 24, 80, Material.STAINLESS_STEEL);
            ScadaShader.paintDishedTankHead(g, 10, 40, 60, 18, true, Material.STAINLESS_STEEL);
            ScadaShader.paintDishedTankHead(g, 10, 90, 60, 18, false, Material.STAINLESS_STEEL);
            for (CardinalDirection dir : CardinalDirection.values()) {
                ScadaShader.paintFlange(g, 160, 40 + dir.ordinal() * 20.0, 22, 6, dir);
            }
            ScadaShader.paintBeveledHousing(g, 10, 120, 60, 40, 6, Material.CAST_IRON);
            ScadaShader.paintVoluteCasing(g, 120, 150, 36, 22, Material.CAST_IRON);
            ScadaShader.paintRotorImpeller(g, 120, 150, 20, 8, Math.PI / 6.0);
            ScadaShader.paintLedIndicator(g, 200, 150, 8, ScadaPalette.STATE_RUNNING_LED, true);
            ScadaShader.paintStateOverlay(g, 10, 120, 60, 40, EquipmentState.WARNING, 0L);
            ScadaShader.paintStateOverlay(g, 84, 114, 72, 72, EquipmentState.FAULT, 100L);
        } finally {
            g.dispose();
        }

        // Verify pixels were actually drawn into the center of the volute/impeller
        int centerArgb = img.getRGB(120, 150);
        assertTrue((centerArgb >>> 24) > 0, "Expected non-transparent pixel at impeller hub");
    }

    @Test
    void faultFlashPeriodIs500Ms() {
        assertTrue(EquipmentState.isFaultFlashOn(0L));
        assertTrue(EquipmentState.isFaultFlashOn(249L));
        assertFalse(EquipmentState.isFaultFlashOn(250L));
        assertFalse(EquipmentState.isFaultFlashOn(499L));
        assertTrue(EquipmentState.isFaultFlashOn(500L));
    }
}

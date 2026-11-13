package cz.maramat.scada.model;

import cz.maramat.scada.render.SymbolLayerCache;
import cz.maramat.scada.render.symbols.EquipmentSymbolRenderer;
import cz.maramat.scada.routing.PipeRouter;
import cz.maramat.scada.tags.InternalTagProvider;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolPortAlignmentTest {

    @Test
    void allCatalogSymbolPortsAlignToExpectedCardinalBoundaries() {
        int counter = 1;
        for (List<String> types : SymbolCatalog.getCategorizedTypes().values()) {
            for (String type : types) {
                ScadaSymbolInstance sym = SymbolCatalog.createInstance(type, 10, 10, counter++);
                int w = sym.getGridW();
                int h = sym.getGridH();
                for (PipePort port : sym.getPorts()) {
                    assertTrue(port.offsetX() >= 0 && port.offsetX() <= w,
                            type + " port " + port.id() + " offsetX out of bounds: " + port.offsetX());
                    assertTrue(port.offsetY() >= 0 && port.offsetY() <= h,
                            type + " port " + port.id() + " offsetY out of bounds: " + port.offsetY());
                    switch (port.direction()) {
                        case WEST -> assertEquals(0, port.offsetX(),
                                type + " WEST port " + port.id() + " must be at offsetX=0");
                        case EAST -> assertEquals(w, port.offsetX(),
                                type + " EAST port " + port.id() + " must be at offsetX=" + w);
                        case NORTH -> assertEquals(0, port.offsetY(),
                                type + " NORTH port " + port.id() + " must be at offsetY=0");
                        case SOUTH -> assertEquals(h, port.offsetY(),
                                type + " SOUTH port " + port.id() + " must be at offsetY=" + h);
                    }
                }
            }
        }
    }

    @Test
    void pipeRouterEndpointsMatchResolvedWorldPortsWithoutOffset() {
        ScadaSymbolInstance pump = SymbolCatalog.createInstance("CentrifugalPump", 10, 10, 1);
        ScadaSymbolInstance valve = SymbolCatalog.createInstance("HandValve", 22, 8, 1);
        ScadaSymbolInstance tank = SymbolCatalog.createInstance("CylindricalTank", 34, 6, 1);

        PipePort.WorldPort pumpDis = pump.resolveWorldPort("discharge");
        PipePort.WorldPort valveIn = valve.resolveWorldPort("inlet");
        PipePort.WorldPort valveOut = valve.resolveWorldPort("outlet");
        PipePort.WorldPort tankIn = tank.resolveWorldPort("inlet");

        assertNotNull(pumpDis);
        assertNotNull(valveIn);
        assertNotNull(valveOut);
        assertNotNull(tankIn);

        List<GridPoint> p1 = PipeRouter.route(pumpDis, valveIn);
        assertFalse(p1.isEmpty());
        assertEquals(pumpDis.position(), p1.getFirst(), "Pipe 1 must start at exact pump discharge world port");
        assertEquals(valveIn.position(), p1.getLast(), "Pipe 1 must end at exact valve inlet world port");

        List<GridPoint> p2 = PipeRouter.route(valveOut, tankIn);
        assertFalse(p2.isEmpty());
        assertEquals(valveOut.position(), p2.getFirst(), "Pipe 2 must start at exact valve outlet world port");
        assertEquals(tankIn.position(), p2.getLast(), "Pipe 2 must end at exact tank inlet world port");
    }

    @Test
    void renderedEquipmentHasVisiblePixelsAtEveryPipePortWithZeroGap() {
        List<String> typesToCheck = List.of(
                "CentrifugalPump", "InlinePump",
                "HandValve", "SolenoidValve", "CheckValve",
                "Inlet", "Outlet",
                "CylindricalTank", "HorizontalTank", "HopperSilo", "OpenBasin", "TankAgitator"
        );
        int gPx = 12;
        ScadaProject project = new ScadaProject();
        InternalTagProvider tags = new InternalTagProvider();
        EquipmentSymbolRenderer renderer = new EquipmentSymbolRenderer();
        SymbolLayerCache layerCache = new SymbolLayerCache();

        int counter = 1;
        for (String type : typesToCheck) {
            ScadaSymbolInstance sym = SymbolCatalog.createInstance(type, 0, 0, counter++);
            int imgW = sym.getGridW() * gPx;
            int imgH = sym.getGridH() * gPx;
            BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            renderer.paintSymbol(g2, sym, gPx, project, tags, layerCache, 0L, false);
            g2.dispose();

            for (PipePort port : sym.getPorts()) {
                int px = Math.min(imgW - 1, Math.max(0, port.offsetX() * gPx));
                int py = Math.min(imgH - 1, Math.max(0, port.offsetY() * gPx));
                int checkX = switch (port.direction()) {
                    case WEST -> 1;
                    case EAST -> imgW - 2;
                    default -> px;
                };
                int checkY = switch (port.direction()) {
                    case NORTH -> 1;
                    case SOUTH -> imgH - 2;
                    default -> py;
                };
                int alpha = (img.getRGB(checkX, checkY) >>> 24) & 0xFF;
                assertTrue(alpha > 0,
                        type + " port " + port.id() + " (" + port.direction() + ") has a visual gap at ("
                                + checkX + "," + checkY + ")");
            }
        }
    }
}

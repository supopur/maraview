package cz.maramat.scada.ui;

import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.render.SymbolLayerCache;
import cz.maramat.scada.tags.RuntimeTagProvider;

import java.awt.Graphics2D;

/**
 * Pluggable renderer for SCADA symbols on ScadaCanvas.
 */
@FunctionalInterface
public interface SymbolRenderer {
    void paintSymbol(Graphics2D g,
                     ScadaSymbolInstance symbol,
                     int gridSizePx,
                     ScadaProject project,
                     RuntimeTagProvider tagProvider,
                     SymbolLayerCache layerCache,
                     long animationTimeMs,
                     boolean operatorMode);
}

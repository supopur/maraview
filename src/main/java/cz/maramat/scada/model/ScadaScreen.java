package cz.maramat.scada.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Single HMI screen tab within a ScadaProject, holding equipment symbols and orthogonal pipes.
 */
public class ScadaScreen {
    public static final int DEFAULT_GRID_SIZE_PX = 10;
    public static final String BG_WHITE = "#FFFFFF";
    public static final String BG_LIGHT_SLATE = "#E2E6EA";
    public static final String BG_DARK_CONTROL_ROOM = "#2B3036";

    private String id;
    private String name;
    private int gridSizePx = DEFAULT_GRID_SIZE_PX;
    private String backgroundColorHex = BG_LIGHT_SLATE;
    private boolean showGrid = true;
    private final List<ScadaSymbolInstance> symbols = new ArrayList<>();
    private final List<ScadaPipe> pipes = new ArrayList<>();
    private final List<ScadaGraphicElement> graphics = new ArrayList<>();

    public ScadaScreen() {
        this.id = UUID.randomUUID().toString();
        this.name = "Overview";
    }

    public ScadaScreen(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = (name == null || name.isBlank()) ? "Screen" : name.trim();
    }

    public ScadaSymbolInstance findSymbolById(String symbolId) {
        if (symbolId == null) return null;
        for (ScadaSymbolInstance sym : symbols) {
            if (symbolId.equals(sym.getId())) {
                return sym;
            }
        }
        return null;
    }

    public ScadaPipe findPipeById(String pipeId) {
        if (pipeId == null) return null;
        for (ScadaPipe pipe : pipes) {
            if (pipeId.equals(pipe.getId())) {
                return pipe;
            }
        }
        return null;
    }

    public Color getBackgroundColor() {
        return parseHexColor(backgroundColorHex, new Color(0xE2, 0xE6, 0xEA));
    }

    public static Color parseHexColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        String clean = hex.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.length() != 6) return fallback;
        try {
            int rgb = Integer.parseInt(clean, 16);
            return new Color(rgb);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static String normalizeHexColor(String hex, String fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        String clean = hex.trim();
        if (clean.startsWith("#")) clean = clean.substring(1);
        if (clean.length() != 6) return fallback;
        try {
            Integer.parseInt(clean, 16);
            return "#" + clean.toUpperCase();
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Screen" : name.trim();
    }

    public int getGridSizePx() {
        return gridSizePx;
    }

    public void setGridSizePx(int gridSizePx) {
        this.gridSizePx = Math.max(4, gridSizePx);
    }

    public String getBackgroundColorHex() {
        return backgroundColorHex;
    }

    public void setBackgroundColorHex(String backgroundColorHex) {
        this.backgroundColorHex = normalizeHexColor(backgroundColorHex, BG_LIGHT_SLATE);
    }

    public boolean isShowGrid() {
        return showGrid;
    }

    public void setShowGrid(boolean showGrid) {
        this.showGrid = showGrid;
    }

    public List<ScadaSymbolInstance> getSymbols() {
        return symbols;
    }

    public List<ScadaPipe> getPipes() {
        return pipes;
    }

    public List<ScadaGraphicElement> getGraphics() {
        return graphics;
    }

    public ScadaGraphicElement findGraphicById(String graphicId) {
        if (graphicId == null) return null;
        for (ScadaGraphicElement g : graphics) {
            if (graphicId.equals(g.getId())) {
                return g;
            }
        }
        return null;
    }
}

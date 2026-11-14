package cz.maramat.scada.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Placed SCADA equipment, instrumentation, or operator control symbol on a ScadaScreen.
 */
public class ScadaSymbolInstance {
    public enum LabelPosition {
        BOTTOM("Bottom"),
        TOP("Top"),
        LEFT("Left"),
        RIGHT("Right"),
        INSIDE("Inside");

        private final String displayName;

        LabelPosition(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private String id;
    private String symbolType;
    private String name;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int rotationDeg;
    private boolean flipH;
    private boolean flipV;
    private int zIndex;
    private String tooltip = "";
    private boolean showLabel = true;
    private LabelPosition labelPosition = LabelPosition.BOTTOM;
    private int labelOffsetX;
    private int labelOffsetY;
    private boolean showValueReadout = true;
    private final Map<String, String> properties = new LinkedHashMap<>();
    private final Map<String, String> tagBindings = new LinkedHashMap<>();
    private final List<PipePort> ports = new ArrayList<>();

    public ScadaSymbolInstance() {
        this.id = UUID.randomUUID().toString();
        this.symbolType = "Generic";
        this.name = "SYM-1";
        this.gridW = 6;
        this.gridH = 6;
    }

    public ScadaSymbolInstance(String symbolType, String name, int gridX, int gridY, int gridW, int gridH) {
        this.id = UUID.randomUUID().toString();
        this.symbolType = (symbolType == null || symbolType.isBlank()) ? "Generic" : symbolType.trim();
        this.name = (name == null) ? this.symbolType : name.trim();
        this.gridX = gridX;
        this.gridY = gridY;
        this.gridW = Math.max(1, gridW);
        this.gridH = Math.max(1, gridH);
        this.showLabel = !isDefaultHiddenLabelType(this.symbolType);
    }

    public static boolean isDefaultHiddenLabelType(String symbolType) {
        if (symbolType == null) return false;
        return switch (symbolType) {
            case "TeeFitting", "ElbowFitting", "ReducerFitting", "FlangeFitting",
                 "Coupling", "ExpansionJoint", "Strainer", "ProcessFilter",
                 "SteamTrap", "SightGlass", "AirDirtSeparator", "AirFilter",
                 "ExpansionVessel", "Inlet", "Outlet" -> true;
            default -> false;
        };
    }

    public String getProperty(String key, String fallback) {
        if (key == null) return fallback;
        String val = properties.get(key);
        return (val == null || val.isBlank()) ? fallback : val;
    }

    public void setProperty(String key, String value) {
        if (key == null) return;
        if (value == null) {
            properties.remove(key);
        } else {
            properties.put(key, value);
        }
    }

    public ScadaSymbolInstance copy() {
        ScadaSymbolInstance copy = new ScadaSymbolInstance(symbolType, name, gridX, gridY, gridW, gridH);
        copy.id = this.id;
        copy.rotationDeg = this.rotationDeg;
        copy.flipH = this.flipH;
        copy.flipV = this.flipV;
        copy.zIndex = this.zIndex;
        copy.tooltip = this.tooltip;
        copy.showLabel = this.showLabel;
        copy.labelPosition = this.labelPosition;
        copy.labelOffsetX = this.labelOffsetX;
        copy.labelOffsetY = this.labelOffsetY;
        copy.showValueReadout = this.showValueReadout;
        copy.properties.putAll(this.properties);
        copy.tagBindings.putAll(this.tagBindings);
        copy.ports.addAll(this.ports);
        return copy;
    }

    /**
     * Effective width in grid units after accounting for 90/270 degree rotation.
     */
    public int getEffectiveGridW() {
        return (rotationDeg == 90 || rotationDeg == 270) ? gridH : gridW;
    }

    /**
     * Effective height in grid units after accounting for 90/270 degree rotation.
     */
    public int getEffectiveGridH() {
        return (rotationDeg == 90 || rotationDeg == 270) ? gridW : gridH;
    }

    public PipePort findPort(String portId) {
        if (portId == null) return null;
        for (PipePort p : ports) {
            if (p.id().equals(portId)) {
                return p;
            }
        }
        return null;
    }

    public PipePort.WorldPort resolveWorldPort(String portId) {
        PipePort p = findPort(portId);
        if (p == null) return null;
        return p.resolveWorldPort(gridX, gridY, gridW, gridH, rotationDeg, flipH, flipV);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }

    public String getSymbolType() {
        return symbolType;
    }

    public void setSymbolType(String symbolType) {
        this.symbolType = (symbolType == null || symbolType.isBlank()) ? "Generic" : symbolType.trim();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null) ? "" : name.trim();
    }

    public int getGridX() {
        return gridX;
    }

    public void setGridX(int gridX) {
        this.gridX = gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public void setGridY(int gridY) {
        this.gridY = gridY;
    }

    public int getGridW() {
        return gridW;
    }

    public void setGridW(int gridW) {
        this.gridW = Math.max(1, gridW);
    }

    public int getGridH() {
        return gridH;
    }

    public void setGridH(int gridH) {
        this.gridH = Math.max(1, gridH);
    }

    public int getRotationDeg() {
        return rotationDeg;
    }

    public void setRotationDeg(int rotationDeg) {
        this.rotationDeg = PipePort.normalizeRotation(rotationDeg);
    }

    public boolean isFlipH() {
        return flipH;
    }

    public void setFlipH(boolean flipH) {
        this.flipH = flipH;
    }

    public boolean isFlipV() {
        return flipV;
    }

    public void setFlipV(boolean flipV) {
        this.flipV = flipV;
    }

    public int getZIndex() {
        return zIndex;
    }

    public void setZIndex(int zIndex) {
        this.zIndex = zIndex;
    }

    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = (tooltip == null) ? "" : tooltip;
    }

    public boolean isShowLabel() {
        return showLabel;
    }

    public void setShowLabel(boolean showLabel) {
        this.showLabel = showLabel;
    }

    public LabelPosition getLabelPosition() {
        return labelPosition;
    }

    public void setLabelPosition(LabelPosition labelPosition) {
        this.labelPosition = (labelPosition == null) ? LabelPosition.BOTTOM : labelPosition;
    }

    public int getLabelOffsetX() {
        return labelOffsetX;
    }

    public void setLabelOffsetX(int labelOffsetX) {
        this.labelOffsetX = labelOffsetX;
    }

    public int getLabelOffsetY() {
        return labelOffsetY;
    }

    public void setLabelOffsetY(int labelOffsetY) {
        this.labelOffsetY = labelOffsetY;
    }

    public boolean isShowValueReadout() {
        return showValueReadout;
    }

    public void setShowValueReadout(boolean showValueReadout) {
        this.showValueReadout = showValueReadout;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public Map<String, String> getTagBindings() {
        return tagBindings;
    }

    public List<PipePort> getPorts() {
        return ports;
    }
}

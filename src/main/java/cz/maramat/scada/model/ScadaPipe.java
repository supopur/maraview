package cz.maramat.scada.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Orthogonal process pipe connecting equipment PipePorts or tee junctions on the integer grid.
 */
public class ScadaPipe {
    public enum FlowMode {
        AUTO("Auto (Solver)"),
        ALWAYS_ON("Always Flowing"),
        ALWAYS_OFF("Always Stopped"),
        TAG("Explicit Tag");

        private final String displayName;

        FlowMode(String displayName) {
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
    private String sourceSymbolId;
    private String sourcePortId;
    private String targetSymbolId;
    private String targetPortId;
    private String sourceTeePipeId;
    private final List<GridPoint> vertices = new ArrayList<>();
    private ConnectionType connectionType = ConnectionType.PIPE;
    private PipeDiameter diameter = PipeDiameter.MEDIUM;
    private FluidType fluidType = FluidType.WATER;
    private String voltageOrSignal = "400V 3~ AC";
    private boolean autoFlow = true;
    private FlowMode flowMode = FlowMode.AUTO;
    private String flowTag = "";
    private String label = "";
    private boolean showLabel = true;
    private String customColorHex = "";
    private boolean showFlanges = false;

    public ScadaPipe() {
        this.id = UUID.randomUUID().toString();
    }

    public ScadaPipe(String id) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }

    public ScadaPipe copy() {
        ScadaPipe copy = new ScadaPipe(this.id);
        copy.sourceSymbolId = this.sourceSymbolId;
        copy.sourcePortId = this.sourcePortId;
        copy.targetSymbolId = this.targetSymbolId;
        copy.targetPortId = this.targetPortId;
        copy.sourceTeePipeId = this.sourceTeePipeId;
        copy.vertices.addAll(this.vertices);
        copy.connectionType = this.connectionType;
        copy.diameter = this.diameter;
        copy.fluidType = this.fluidType;
        copy.voltageOrSignal = this.voltageOrSignal;
        copy.autoFlow = this.autoFlow;
        copy.flowMode = this.flowMode;
        copy.flowTag = this.flowTag;
        copy.label = this.label;
        copy.showLabel = this.showLabel;
        copy.customColorHex = this.customColorHex;
        copy.showFlanges = this.showFlanges;
        return copy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }

    public String getSourceSymbolId() {
        return sourceSymbolId;
    }

    public void setSourceSymbolId(String sourceSymbolId) {
        this.sourceSymbolId = sourceSymbolId;
    }

    public String getSourcePortId() {
        return sourcePortId;
    }

    public void setSourcePortId(String sourcePortId) {
        this.sourcePortId = sourcePortId;
    }

    public String getTargetSymbolId() {
        return targetSymbolId;
    }

    public void setTargetSymbolId(String targetSymbolId) {
        this.targetSymbolId = targetSymbolId;
    }

    public String getTargetPortId() {
        return targetPortId;
    }

    public void setTargetPortId(String targetPortId) {
        this.targetPortId = targetPortId;
    }

    public String getSourceTeePipeId() {
        return sourceTeePipeId;
    }

    public void setSourceTeePipeId(String sourceTeePipeId) {
        this.sourceTeePipeId = sourceTeePipeId;
    }

    public List<GridPoint> getVertices() {
        return vertices;
    }

    public List<GridPoint> getVerticesView() {
        return Collections.unmodifiableList(vertices);
    }

    public void setVertices(List<GridPoint> newVertices) {
        vertices.clear();
        if (newVertices != null) {
            vertices.addAll(newVertices);
        }
    }

    public PipeDiameter getDiameter() {
        return diameter;
    }

    public void setDiameter(PipeDiameter diameter) {
        this.diameter = (diameter == null) ? PipeDiameter.MEDIUM : diameter;
    }

    public FluidType getFluidType() {
        return fluidType;
    }

    public void setFluidType(FluidType fluidType) {
        this.fluidType = (fluidType == null) ? FluidType.WATER : fluidType;
    }

    public boolean isAutoFlow() {
        return autoFlow;
    }

    public void setAutoFlow(boolean autoFlow) {
        this.autoFlow = autoFlow;
        if (!autoFlow && this.flowMode == FlowMode.AUTO) {
            this.flowMode = FlowMode.TAG;
        } else if (autoFlow && this.flowMode == FlowMode.TAG) {
            this.flowMode = FlowMode.AUTO;
        }
    }

    public FlowMode getFlowMode() {
        return flowMode;
    }

    public void setFlowMode(FlowMode flowMode) {
        this.flowMode = (flowMode == null) ? FlowMode.AUTO : flowMode;
        this.autoFlow = (this.flowMode == FlowMode.AUTO);
    }

    public boolean isShowLabel() {
        return showLabel;
    }

    public void setShowLabel(boolean showLabel) {
        this.showLabel = showLabel;
    }

    public String getCustomColorHex() {
        return customColorHex;
    }

    public void setCustomColorHex(String customColorHex) {
        this.customColorHex = (customColorHex == null) ? "" : customColorHex.trim();
    }

    public boolean isShowFlanges() {
        return showFlanges;
    }

    public void setShowFlanges(boolean showFlanges) {
        this.showFlanges = showFlanges;
    }

    public String getFlowTag() {
        return flowTag;
    }

    public void setFlowTag(String flowTag) {
        this.flowTag = (flowTag == null) ? "" : flowTag.trim();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = (label == null) ? "" : label.trim();
    }

    public ConnectionType getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(ConnectionType connectionType) {
        this.connectionType = (connectionType == null) ? ConnectionType.PIPE : connectionType;
    }

    public String getVoltageOrSignal() {
        return voltageOrSignal;
    }

    public void setVoltageOrSignal(String voltageOrSignal) {
        this.voltageOrSignal = (voltageOrSignal == null) ? "" : voltageOrSignal.trim();
    }
}

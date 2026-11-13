package cz.maramat.scada.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Orthogonal process pipe connecting equipment PipePorts or tee junctions on the integer grid.
 */
public class ScadaPipe {
    private String id;
    private String sourceSymbolId;
    private String sourcePortId;
    private String targetSymbolId;
    private String targetPortId;
    private String sourceTeePipeId;
    private final List<GridPoint> vertices = new ArrayList<>();
    private PipeDiameter diameter = PipeDiameter.MEDIUM;
    private FluidType fluidType = FluidType.WATER;
    private boolean autoFlow = true;
    private String flowTag = "";
    private String label = "";

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
        copy.diameter = this.diameter;
        copy.fluidType = this.fluidType;
        copy.autoFlow = this.autoFlow;
        copy.flowTag = this.flowTag;
        copy.label = this.label;
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
}

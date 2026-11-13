package cz.maramat.scada.routing;

import cz.maramat.scada.model.FluidType;

/**
 * Resolved runtime flow and pressure state for a single ScadaPipe.
 */
public record FlowState(boolean pressurized, double flowRate, FluidType fluidType) {
    public static final FlowState IDLE = new FlowState(false, 0.0, FluidType.EMPTY);
}

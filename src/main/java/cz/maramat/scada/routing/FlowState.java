package cz.maramat.scada.routing;

import cz.maramat.scada.model.FluidType;

/**
 * Resolved runtime flow and pressure state for a single ScadaPipe.
 *
 * @param direction +1 when fluid flows from vertices.get(0) toward vertices.get(last),
 *                  -1 when fluid flows in the opposite direction.
 */
public record FlowState(boolean pressurized, double flowRate, FluidType fluidType, int direction) {
    public static final FlowState IDLE = new FlowState(false, 0.0, FluidType.EMPTY, 1);

    public FlowState(boolean pressurized, double flowRate, FluidType fluidType) {
        this(pressurized, flowRate, fluidType, 1);
    }
}

package cz.maramat.scada.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central catalog of all SCADA, Electrical, Heating/HVAC, and MaR (Měření a regulace) symbols.
 * Defines domain, fixed vs resizable geometry, minimum bounds, and dynamic port anchors.
 */
public final class SymbolCatalog {

    public record PortSpec(
            String id,
            String label,
            double ratioX,
            double ratioY,
            CardinalDirection direction,
            ConnectionType connectionType
    ) {
        public PipePort toPipePort(int w, int h) {
            int ox = (int) Math.round(ratioX * w);
            int oy = (int) Math.round(ratioY * h);
            return new PipePort(id, label, ox, oy, direction, connectionType);
        }
    }

    public record SymbolDef(
            String symbolType,
            String category,
            EquipmentDomain domain,
            int defaultW,
            int defaultH,
            int minW,
            int minH,
            boolean resizable,
            List<PortSpec> ports
    ) {
    }

    private static final Map<String, SymbolDef> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, List<String>> CATEGORIES = new LinkedHashMap<>();

    static {
        // 1. Piping & Valves
        register("HandValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 4, 4, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("SolenoidValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 5, 4, 5, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("coil", "Coil", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("ControlValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 5, 5, 5, 5, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("cmd", "0-10V Cmd", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("ThreeWayMixingValve", "Piping & Valves", EquipmentDomain.HEATING_HVAC, 6, 6, 6, 6, false, List.of(
                new PortSpec("a", "Port A (Hot)", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("ab", "Port AB (Mix)", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("b", "Port B (Bypass)", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("cmd", "Actuator Cmd", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("TwoWayControlValve", "Piping & Valves", EquipmentDomain.HEATING_HVAC, 5, 5, 5, 5, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("cmd", "Actuator Cmd", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("CheckValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 4, 4, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("SafetyReliefValve", "Piping & Valves", EquipmentDomain.HEATING_HVAC, 4, 5, 4, 5, false, List.of(
                new PortSpec("inlet", "Inlet", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("discharge", "Relief", 1.0, 0.4, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("GateValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 5, 4, 5, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("GlobeValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 5, 4, 5, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("BallValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 4, 4, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("ButterflyValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 4, 4, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("NeedleValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 5, 4, 5, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("PlugValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 4, 4, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("DiaphragmValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 5, 4, 5, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("FourWayValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 6, 6, 6, 6, false, List.of(
                new PortSpec("a", "Port A", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("b", "Port B", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("c", "Port C", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("d", "Port D", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("MotorizedValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 5, 6, 5, 6, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.65, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.65, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Motor Power", 0.3, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("cmd", "0-10V Cmd", 0.7, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("PneumaticValve", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 5, 6, 5, 6, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.65, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.65, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("air", "Air Supply", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PNEUMATIC)
        ));
        register("AirDirtSeparator", "Piping & Valves", EquipmentDomain.HEATING_HVAC, 4, 7, 4, 6, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.4, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.4, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("Damper", "Piping & Valves", EquipmentDomain.HEATING_HVAC, 5, 5, 4, 4, true, List.of(
                new PortSpec("inlet", "Air In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Air Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("cmd", "Actuator", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("Inlet", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 3, 3, true, List.of(
                new PortSpec("outlet", "Supply Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("Outlet", "Piping & Valves", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 3, 3, true, List.of(
                new PortSpec("inlet", "Drain In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE)
        ));

        // 1b. Pipe Fittings
        register("TeeFitting", "Fittings", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 3, 3, true, List.of(
                new PortSpec("a", "Left", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("b", "Right", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("c", "Branch", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("ElbowFitting", "Fittings", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 3, 3, true, List.of(
                new PortSpec("a", "In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("b", "Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("ReducerFitting", "Fittings", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 3, 3, true, List.of(
                new PortSpec("inlet", "Large End", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Small End", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("FlangeFitting", "Fittings", EquipmentDomain.FLUID_EQUIPMENT, 3, 4, 3, 3, false, List.of(
                new PortSpec("a", "Side A", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("b", "Side B", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("Strainer", "Fittings", EquipmentDomain.FLUID_EQUIPMENT, 5, 5, 4, 4, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.4, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.4, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("ProcessFilter", "Fittings", EquipmentDomain.FLUID_EQUIPMENT, 5, 6, 4, 5, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.35, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.35, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("SteamTrap", "Fittings", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 4, 4, false, List.of(
                new PortSpec("inlet", "Condensate In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Drain Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("SightGlass", "Fittings", EquipmentDomain.FLUID_EQUIPMENT, 5, 4, 4, 3, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("ExpansionJoint", "Fittings", EquipmentDomain.FLUID_EQUIPMENT, 5, 4, 4, 3, true, List.of(
                new PortSpec("a", "Side A", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("b", "Side B", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("Coupling", "Fittings", EquipmentDomain.FLUID_EQUIPMENT, 4, 4, 3, 3, false, List.of(
                new PortSpec("a", "Side A", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("b", "Side B", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));

        // 1c. Process Equipment
        register("MixerAgitator", "Process Equipment", EquipmentDomain.FLUID_EQUIPMENT, 6, 10, 5, 7, true, List.of(
                new PortSpec("pwr", "Motor Power", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("Separator", "Process Equipment", EquipmentDomain.FLUID_EQUIPMENT, 9, 10, 6, 7, true, List.of(
                new PortSpec("inlet", "Feed In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("light", "Top Outlet", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("heavy", "Bottom Outlet", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("Cyclone", "Process Equipment", EquipmentDomain.FLUID_EQUIPMENT, 8, 12, 6, 8, true, List.of(
                new PortSpec("inlet", "Tangential In", 0.0, 0.25, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("gas", "Clean Gas Out", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("solids", "Solids Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("Scrubber", "Process Equipment", EquipmentDomain.FLUID_EQUIPMENT, 9, 13, 6, 9, true, List.of(
                new PortSpec("gas_in", "Gas In", 0.0, 0.75, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("gas_out", "Clean Gas Out", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("liquid_in", "Spray Liquid", 1.0, 0.25, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("drain", "Effluent Drain", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("ProcessScreen", "Process Equipment", EquipmentDomain.FLUID_EQUIPMENT, 8, 7, 6, 5, true, List.of(
                new PortSpec("inlet", "Feed In", 0.0, 0.35, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Screened Out", 1.0, 0.65, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("rejects", "Rejects", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));

        // 2. Pumps & Fans
        register("CentrifugalPump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 8, 10, 6, 7, true, List.of(
                new PortSpec("suction", "Suction", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("discharge", "Discharge", 0.375, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("pwr", "Motor Power", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER)
        ));
        register("CirculationPump", "Pumps & Fans", EquipmentDomain.HEATING_HVAC, 6, 6, 5, 5, true, List.of(
                new PortSpec("inlet", "Suction", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Discharge", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "230V Power", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("InlinePump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 6, 6, 5, 5, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Power", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("MeteringPump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 6, 6, 5, 5, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("cmd", "Pulse/4-20mA", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("SubmersiblePump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 6, 9, 5, 7, true, List.of(
                new PortSpec("discharge", "Discharge", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("pwr", "Submersible Cable", 1.0, 0.25, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER)
        ));
        register("PositiveDisplacementPump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 7, 6, 5, 5, true, List.of(
                new PortSpec("inlet", "Suction", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Discharge", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Motor Power", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("GearPump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 7, 6, 5, 5, true, List.of(
                new PortSpec("inlet", "Suction", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Discharge", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Motor Power", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("DiaphragmPump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 7, 6, 5, 5, true, List.of(
                new PortSpec("inlet", "Suction", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Discharge", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("air", "Drive / Power", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PNEUMATIC)
        ));
        register("PeristalticPump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 6, 6, 5, 5, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("cmd", "Speed Cmd", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("ScrewPump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 9, 6, 6, 5, true, List.of(
                new PortSpec("inlet", "Suction", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Discharge", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Drive Power", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("VacuumPump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 7, 7, 5, 5, true, List.of(
                new PortSpec("inlet", "Vacuum In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Exhaust", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("pwr", "Motor Power", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER)
        ));
        register("BoosterPump", "Pumps & Fans", EquipmentDomain.FLUID_EQUIPMENT, 7, 9, 5, 7, true, List.of(
                new PortSpec("inlet", "Suction", 0.0, 0.75, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Boosted Out", 1.0, 0.25, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "400V Power", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));

        // 2b. Actuators
        register("PneumaticActuator", "Actuators", EquipmentDomain.FLUID_EQUIPMENT, 6, 6, 5, 5, true, List.of(
                new PortSpec("air", "Air Supply", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PNEUMATIC),
                new PortSpec("sig", "Limit Switches", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("ElectricActuator", "Actuators", EquipmentDomain.FLUID_EQUIPMENT, 6, 6, 5, 5, true, List.of(
                new PortSpec("pwr", "Motor Power", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("sig", "Cmd / Limits", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("HydraulicActuator", "Actuators", EquipmentDomain.FLUID_EQUIPMENT, 7, 6, 5, 5, true, List.of(
                new PortSpec("hyd_a", "Hyd Port A", 0.0, 0.3, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("hyd_b", "Hyd Port B", 0.0, 0.7, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("sig", "Limit Switches", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("BlowerFan", "Pumps & Fans", EquipmentDomain.HEATING_HVAC, 6, 6, 5, 5, true, List.of(
                new PortSpec("inlet", "Intake", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Exhaust", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Power", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("VentilationFan", "Pumps & Fans", EquipmentDomain.HEATING_HVAC, 6, 6, 5, 5, true, List.of(
                new PortSpec("inlet", "Air In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Air Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Power", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));

        // 3. Heating, Boilers & HVAC
        register("Boiler", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 10, 12, 7, 8, true, List.of(
                new PortSpec("supply", "Flow Out", 0.25, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("return", "Return In", 0.75, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("gas", "Fuel/Gas", 0.0, 0.75, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("pwr", "230V/400V", 1.0, 0.75, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ctrl", "Bus/0-10V", 1.0, 0.25, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("GasBoiler", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 10, 12, 7, 8, true, List.of(
                new PortSpec("supply", "Flow Out", 0.25, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("return", "Return In", 0.75, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("gas", "Gas Supply", 0.0, 0.75, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("ctrl", "Modulation", 1.0, 0.25, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("ElectricBoiler", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 9, 12, 7, 8, true, List.of(
                new PortSpec("supply", "Flow Out", 0.3, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("return", "Return In", 0.7, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("pwr", "400V 3~", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ctrl", "Control", 0.0, 0.3, CardinalDirection.WEST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("BiomassBoiler", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 12, 12, 8, 8, true, List.of(
                new PortSpec("supply", "Flow Out", 0.3, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("return", "Return In", 0.7, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("pwr", "Auger/Fan Pwr", 1.0, 0.7, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER)
        ));
        register("Burner", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 7, 5, 5, 4, true, List.of(
                new PortSpec("fuel", "Fuel In", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("pwr", "Power", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ctrl", "Firing Cmd", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("Chiller", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 14, 10, 9, 7, true, List.of(
                new PortSpec("chw_out", "CHW Supply", 0.0, 0.3, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("chw_in", "CHW Return", 0.0, 0.7, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("cw_out", "Cond Out", 1.0, 0.3, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("cw_in", "Cond In", 1.0, 0.7, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "400V Power", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("HeatPump", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 12, 10, 8, 7, true, List.of(
                new PortSpec("supply", "Heating Out", 1.0, 0.3, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("return", "Heating In", 1.0, 0.7, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "400V Supply", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ctrl", "MaR Bus", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("AirSourceHeatPump", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 13, 10, 9, 7, true, List.of(
                new PortSpec("supply", "Flow Out", 1.0, 0.3, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("return", "Return In", 1.0, 0.7, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "400V Power", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ctrl", "MaR Bus", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("GroundSourceHeatPump", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 13, 10, 9, 7, true, List.of(
                new PortSpec("brine_in", "Brine In", 0.0, 0.3, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("brine_out", "Brine Out", 0.0, 0.7, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("supply", "Flow Out", 1.0, 0.3, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("return", "Return In", 1.0, 0.7, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "400V Power", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("WaterSourceHeatPump", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 13, 10, 9, 7, true, List.of(
                new PortSpec("src_in", "Source In", 0.0, 0.3, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("src_out", "Source Out", 0.0, 0.7, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("supply", "Flow Out", 1.0, 0.3, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("return", "Return In", 1.0, 0.7, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "400V Power", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("PlateHeatExchanger", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 8, 10, 6, 7, true, List.of(
                new PortSpec("pri_in", "Primary In", 0.0, 0.2, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("pri_out", "Primary Out", 0.0, 0.8, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("sec_out", "Secondary Out", 1.0, 0.2, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("sec_in", "Secondary In", 1.0, 0.8, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("ShellAndTubeHeatExchanger", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 12, 7, 8, 5, true, List.of(
                new PortSpec("tube_in", "Tube In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("tube_out", "Tube Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("shell_in", "Shell In", 0.25, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("shell_out", "Shell Out", 0.75, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("CoilHeatExchanger", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 8, 9, 6, 6, true, List.of(
                new PortSpec("pri_in", "Coil In", 0.0, 0.25, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("pri_out", "Coil Out", 0.0, 0.75, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("sec_in", "Shell In", 1.0, 0.75, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("sec_out", "Shell Out", 1.0, 0.25, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("CoolingTower", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 11, 13, 7, 8, true, List.of(
                new PortSpec("hot_in", "Hot Water In", 0.0, 0.35, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("cold_out", "Cooled Out", 1.0, 0.85, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Fan Motor", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("DryCooler", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 13, 7, 8, 5, true, List.of(
                new PortSpec("inlet", "Fluid In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Fluid Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Fan Power", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("FanCoilUnit", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 10, 6, 7, 5, true, List.of(
                new PortSpec("water_in", "Water In", 0.0, 0.3, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("water_out", "Water Out", 0.0, 0.7, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("air_out", "Air Supply", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "230V Fan", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("BufferTank", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 8, 14, 6, 9, true, List.of(
                new PortSpec("top_in", "Hot Top", 0.0, 0.2, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("top_out", "Supply Out", 1.0, 0.2, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("bot_in", "Return In", 1.0, 0.8, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("bot_out", "Cold Bottom", 0.0, 0.8, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("t_top", "Top Sensor", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("ExpansionVessel", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 6, 9, 4, 6, true, List.of(
                new PortSpec("conn", "System Conn", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE)
        ));
        register("Radiator", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 10, 6, 5, 4, true, List.of(
                new PortSpec("inlet", "Supply", 0.0, 0.2, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Return", 0.0, 0.8, CardinalDirection.WEST, ConnectionType.PIPE)
        ));
        register("FloorHeatingManifold", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 12, 7, 8, 5, true, List.of(
                new PortSpec("supply", "Supply In", 0.0, 0.28, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("return", "Return Out", 0.0, 0.72, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("ctrl", "Zone Actuators", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("AhuUnit", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 16, 9, 10, 6, true, List.of(
                new PortSpec("fresh", "Fresh Air", 0.0, 0.3, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("exhaust", "Exhaust Air", 0.0, 0.7, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("supply", "Supply Air", 1.0, 0.3, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("extract", "Extract Air", 1.0, 0.7, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "400V Power", 0.3, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ctrl", "MaR Control", 0.7, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("HeatingCoil", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 6, 7, 5, 5, true, List.of(
                new PortSpec("air_in", "Air In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("air_out", "Air Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("water_in", "Water In", 0.3, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("water_out", "Water Out", 0.7, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE)
        ));
        register("CoolingCoil", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 6, 7, 5, 5, true, List.of(
                new PortSpec("air_in", "Air In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("air_out", "Air Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("water_in", "Chilled In", 0.3, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("water_out", "Chilled Out", 0.7, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE)
        ));
        register("HeatRecovery", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 8, 8, 6, 6, true, List.of(
                new PortSpec("oa_in", "Outdoor In", 0.0, 0.25, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("sa_out", "Supply Out", 1.0, 0.25, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("ra_in", "Return In", 1.0, 0.75, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("ea_out", "Exhaust Out", 0.0, 0.75, CardinalDirection.WEST, ConnectionType.PIPE)
        ));
        register("AirFilter", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 4, 6, 3, 4, true, List.of(
                new PortSpec("inlet", "Air In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Air Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("dp", "dP Switch", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("Humidifier", "Heating & HVAC", EquipmentDomain.HEATING_HVAC, 6, 7, 5, 5, true, List.of(
                new PortSpec("air_in", "Air In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("air_out", "Air Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("water", "Water In", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("ctrl", "0-10V Cmd", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));

        // 4. Electrical Elements
        register("ElectricMotor", "Electrical", EquipmentDomain.ELECTRICAL, 7, 7, 5, 5, true, List.of(
                new PortSpec("pwr", "3~ Power", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ptc", "Thermal/FB", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("PowerSource400V", "Electrical", EquipmentDomain.ELECTRICAL, 6, 5, 5, 4, true, List.of(
                new PortSpec("out", "L1/L2/L3 Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("bot", "Bus Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("PowerSource230V", "Electrical", EquipmentDomain.ELECTRICAL, 5, 5, 4, 4, true, List.of(
                new PortSpec("out", "230V Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("bot", "230V Bot", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("PowerSource24V", "Electrical", EquipmentDomain.ELECTRICAL, 5, 5, 4, 4, true, List.of(
                new PortSpec("out", "24V Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ctrl", "24V Aux", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("CircuitBreaker", "Electrical", EquipmentDomain.ELECTRICAL, 4, 6, 4, 6, false, List.of(
                new PortSpec("line", "Line In", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("load", "Load Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("aux", "Aux Status", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("Contactor", "Electrical", EquipmentDomain.ELECTRICAL, 5, 6, 5, 6, false, List.of(
                new PortSpec("line", "Line In", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("load", "Load Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("coil", "A1/A2 Coil", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("VfdDrive", "Electrical", EquipmentDomain.ELECTRICAL, 7, 9, 5, 7, true, List.of(
                new PortSpec("in", "Mains In", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("out", "Motor Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ctrl", "0-10V / Run", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("Fuse", "Electrical", EquipmentDomain.ELECTRICAL, 3, 5, 3, 5, false, List.of(
                new PortSpec("in", "In", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("out", "Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("OverloadRelay", "Electrical", EquipmentDomain.ELECTRICAL, 4, 5, 4, 5, false, List.of(
                new PortSpec("in", "In", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("out", "Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("trip", "Trip NC", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("Transformer", "Electrical", EquipmentDomain.ELECTRICAL, 6, 7, 5, 6, true, List.of(
                new PortSpec("pri", "Primary", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("sec", "Secondary", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("DistributionPanel", "Electrical", EquipmentDomain.ELECTRICAL, 10, 8, 7, 6, true, List.of(
                new PortSpec("in", "Main In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("out1", "Feeder 1", 1.0, 0.25, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("out2", "Feeder 2", 1.0, 0.75, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ctrl", "Telemetry", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));

        // 5. Tanks, Vessels & Reactors
        register("CylindricalTank", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 10, 14, 6, 8, true, List.of(
                new PortSpec("inlet", "Top Inlet", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("outlet", "Bottom Outlet", 0.0, 0.85, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("drain", "East Outlet", 1.0, 0.85, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("HorizontalTank", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 14, 8, 8, 5, true, List.of(
                new PortSpec("inlet", "Top Inlet", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("HopperSilo", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 10, 14, 6, 9, true, List.of(
                new PortSpec("inlet", "Top Fill", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("outlet", "Discharge", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("OpenBasin", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 12, 8, 6, 5, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.35, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.75, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("TankAgitator", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 10, 14, 6, 8, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.25, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.85, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Motor", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("ConeBottomTank", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 10, 14, 6, 9, true, List.of(
                new PortSpec("inlet", "Top Inlet", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("outlet", "Cone Drain", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("SphericalTank", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 11, 11, 7, 7, true, List.of(
                new PortSpec("inlet", "Top Inlet", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("outlet", "Bottom Outlet", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("ClosedTank", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 10, 13, 6, 8, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.25, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.80, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("vent", "Vent/PRV", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE)
        ));
        register("Silo", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 9, 16, 6, 10, true, List.of(
                new PortSpec("inlet", "Pneumatic Fill", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("outlet", "Discharge", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("Hopper", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 10, 10, 6, 7, true, List.of(
                new PortSpec("inlet", "Top Feed", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("outlet", "Bottom Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("Reactor", "Tanks & Vessels", EquipmentDomain.FLUID_EQUIPMENT, 11, 15, 7, 9, true, List.of(
                new PortSpec("feed", "Feed In", 0.0, 0.25, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("product", "Product Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("jacket_in", "Jacket In", 1.0, 0.75, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("jacket_out", "Jacket Out", 1.0, 0.30, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Agitator Motor", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));

        // 6. MaR Sensors & Meters (Měření a regulace)
        register("TempSensor", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 4, 5, 4, 5, false, List.of(
                new PortSpec("sig", "Pt1000/4-20mA", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("OutdoorTempSensor", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 4, 5, 4, 5, false, List.of(
                new PortSpec("sig", "Outside T", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("PressureSensor", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 4, 5, 4, 5, false, List.of(
                new PortSpec("sig", "4-20mA Out", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("DiffPressureSensor", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 5, 5, 5, 5, false, List.of(
                new PortSpec("sig", "dP Out", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("FlowMeter", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 6, 5, 5, 4, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("sig", "Pulse/Flow", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("OrificePlate", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 4, 5, 4, 4, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("dp", "dP Signal", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("VenturiTube", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 6, 5, 5, 4, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("dp", "dP Signal", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("MagneticFlowMeter", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 6, 6, 5, 5, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("sig", "4-20mA Out", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("UltrasonicFlowMeter", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 6, 5, 5, 4, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("sig", "Pulse/Bus", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("CoriolisFlowMeter", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 6, 6, 5, 5, false, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.6, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.6, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("sig", "Mass Flow", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("Rotameter", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 4, 8, 4, 6, true, List.of(
                new PortSpec("inlet", "Bottom In", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("outlet", "Top Out", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE)
        ));
        register("Calorimeter", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 6, 6, 5, 5, false, List.of(
                new PortSpec("inlet", "Flow In", 0.0, 0.65, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Flow Out", 1.0, 0.65, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("sig", "M-Bus/kW", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("HumiditySensor", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 4, 5, 4, 5, false, List.of(
                new PortSpec("sig", "%RH Out", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("Co2Sensor", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 4, 5, 4, 5, false, List.of(
                new PortSpec("sig", "CO2 ppm", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("RoomThermostat", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 5, 5, 5, 5, false, List.of(
                new PortSpec("sig", "Bus/AI", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("LevelSwitch", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 4, 5, 4, 5, false, List.of(
                new PortSpec("sig", "DI Switch", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("ProximitySensor", "MaR Sensors & Meters", EquipmentDomain.MAR_SENSOR, 4, 4, 4, 4, false, List.of(
                new PortSpec("sig", "DI Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));

        // 6b. Wastewater Treatment Plant (WWTP / ČOV)
        register("BarScreen", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 9, 8, 6, 6, true, List.of(
                new PortSpec("in", "Raw Influent", 0.0, 0.55, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("out", "Screened Out", 1.0, 0.55, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Rake Drive", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("GritChamber", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 10, 8, 7, 6, true, List.of(
                new PortSpec("in", "Inlet", 0.0, 0.4, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("out", "Outlet", 1.0, 0.4, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("grit", "Grit Drain", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("PrimaryClarifier", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 12, 12, 8, 8, true, List.of(
                new PortSpec("in", "Influent", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("out", "Effluent", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("sludge", "Primary Sludge", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("pwr", "Bridge Drive", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("AerationBasin", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 14, 10, 9, 7, true, List.of(
                new PortSpec("in", "Inlet", 0.0, 0.35, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("out", "Mixed Liquor", 1.0, 0.35, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("air", "Air Header", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("do", "DO Sensor", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("AirBlower", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 7, 7, 5, 5, true, List.of(
                new PortSpec("air_out", "Air Supply", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "400V Motor", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.ELECTRICAL_POWER),
                new PortSpec("ctrl", "VFD/Speed", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("SecondaryClarifier", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 12, 12, 8, 8, true, List.of(
                new PortSpec("in", "Mixed Liquor In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("out", "Clarified Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("ras", "RAS/WAS Sludge", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("pwr", "Bridge Drive", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("SludgeThickener", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 10, 11, 7, 7, true, List.of(
                new PortSpec("in", "Sludge In", 0.0, 0.35, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("out", "Thickened Out", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("super", "Supernatant", 1.0, 0.25, CardinalDirection.EAST, ConnectionType.PIPE)
        ));
        register("AnaerobicDigester", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 10, 14, 7, 9, true, List.of(
                new PortSpec("in", "Sludge Feed", 0.0, 0.55, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("out", "Digested Out", 1.0, 0.75, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("gas", "Biogas Out", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE)
        ));
        register("FilterPress", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 12, 8, 8, 6, true, List.of(
                new PortSpec("in", "Sludge Feed", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("filtrate", "Filtrate", 1.0, 0.75, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Hydraulic Pwr", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("UvDisinfection", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 10, 6, 7, 5, true, List.of(
                new PortSpec("in", "Effluent In", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("out", "Treated Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "UV Ballast", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));
        register("DosingStation", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 7, 8, 5, 6, true, List.of(
                new PortSpec("out", "Chemical Out", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("ctrl", "Dosing Cmd", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.CONTROL_SIGNAL)
        ));
        register("SubmersibleMixer", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 7, 5, 5, 4, true, List.of(
                new PortSpec("pwr", "Power Cable", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.ELECTRICAL_POWER)
        ));
        register("ArchimedesScrewPump", "Wastewater Treatment (WWTP)", EquipmentDomain.WASTEWATER, 10, 8, 7, 6, true, List.of(
                new PortSpec("in", "Low Sump In", 0.0, 0.85, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("out", "Elevated Out", 1.0, 0.15, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Drive Motor", 0.5, 0.0, CardinalDirection.NORTH, ConnectionType.ELECTRICAL_POWER)
        ));

        // 7. Material Handling & Machines
        register("ConveyorBelt", "Material Handling", EquipmentDomain.FLUID_EQUIPMENT, 14, 5, 8, 4, true, List.of(
                new PortSpec("pwr", "Drive", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.ELECTRICAL_POWER)
        ));
        register("ScrewAuger", "Material Handling", EquipmentDomain.FLUID_EQUIPMENT, 12, 5, 7, 4, true, List.of(
                new PortSpec("inlet", "Feed", 0.15, 0.0, CardinalDirection.NORTH, ConnectionType.PIPE),
                new PortSpec("outlet", "Discharge", 0.85, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("pwr", "Drive", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.ELECTRICAL_POWER)
        ));
        register("PneumaticCylinder", "Material Handling", EquipmentDomain.FLUID_EQUIPMENT, 10, 5, 7, 4, true, List.of(
                new PortSpec("air_a", "Air A", 0.1, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE),
                new PortSpec("air_b", "Air B", 0.7, 1.0, CardinalDirection.SOUTH, ConnectionType.PIPE)
        ));
        register("HydraulicPress", "Material Handling", EquipmentDomain.FLUID_EQUIPMENT, 10, 12, 7, 8, true, List.of(
                new PortSpec("hyd_in", "Hyd In", 0.0, 0.3, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("pwr", "Power", 1.0, 0.8, CardinalDirection.EAST, ConnectionType.ELECTRICAL_POWER)
        ));
        register("HeaterCoil", "Material Handling", EquipmentDomain.HEATING_HVAC, 8, 6, 5, 4, true, List.of(
                new PortSpec("inlet", "Inlet", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.PIPE),
                new PortSpec("outlet", "Outlet", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.PIPE),
                new PortSpec("pwr", "Heater Pwr", 0.5, 1.0, CardinalDirection.SOUTH, ConnectionType.ELECTRICAL_POWER)
        ));

        // 8. Operator Controls & Readouts
        register("PushButton", "Operator Controls", EquipmentDomain.OPERATOR_CONTROL, 4, 4, 4, 4, false, List.of(
                new PortSpec("sig", "Contact", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("SelectorSwitch", "Operator Controls", EquipmentDomain.OPERATOR_CONTROL, 4, 4, 4, 4, false, List.of(
                new PortSpec("sig", "Contact", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("EStopMushroom", "Operator Controls", EquipmentDomain.OPERATOR_CONTROL, 5, 5, 5, 5, false, List.of(
                new PortSpec("sig", "Safety NC", 1.0, 0.5, CardinalDirection.EAST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("PilotLamp", "Operator Controls", EquipmentDomain.OPERATOR_CONTROL, 4, 4, 4, 4, false, List.of(
                new PortSpec("sig", "Input", 0.0, 0.5, CardinalDirection.WEST, ConnectionType.CONTROL_SIGNAL)
        ));
        register("NumericReadout", "Operator Controls", EquipmentDomain.OPERATOR_CONTROL, 8, 4, 6, 3, true, List.of());
        register("NumericSetpoint", "Operator Controls", EquipmentDomain.OPERATOR_CONTROL, 8, 4, 6, 3, true, List.of());
        register("BarGraph", "Operator Controls", EquipmentDomain.OPERATOR_CONTROL, 5, 10, 4, 6, true, List.of());
        register("TrendChart", "Operator Controls", EquipmentDomain.OPERATOR_CONTROL, 16, 10, 10, 6, true, List.of());
    }

    private SymbolCatalog() {
    }

    private static void register(String type, String category, EquipmentDomain domain,
                                 int defaultW, int defaultH, int minW, int minH, boolean resizable,
                                 List<PortSpec> ports) {
        SymbolDef def = new SymbolDef(type, category, domain, defaultW, defaultH, minW, minH, resizable, ports);
        REGISTRY.put(type, def);
        CATEGORIES.computeIfAbsent(category, k -> new ArrayList<>()).add(type);
    }

    public static SymbolDef getDefinition(String symbolType) {
        if (symbolType == null) return null;
        return REGISTRY.get(symbolType);
    }

    public static EquipmentDomain getDomain(String symbolType) {
        SymbolDef def = getDefinition(symbolType);
        return (def == null) ? EquipmentDomain.FLUID_EQUIPMENT : def.domain();
    }

    public static boolean isResizable(String symbolType) {
        SymbolDef def = getDefinition(symbolType);
        return def == null || def.resizable();
    }

    public static int getMinGridW(String symbolType) {
        SymbolDef def = getDefinition(symbolType);
        return (def == null) ? 2 : def.minW();
    }

    public static int getMinGridH(String symbolType) {
        SymbolDef def = getDefinition(symbolType);
        return (def == null) ? 2 : def.minH();
    }

    public static Map<String, List<String>> getCategorizedTypes() {
        return Collections.unmodifiableMap(CATEGORIES);
    }

    public static ScadaSymbolInstance createInstance(String symbolType, int gridX, int gridY, int instanceCounter) {
        SymbolDef def = getDefinition(symbolType);
        int w = (def != null) ? def.defaultW() : 6;
        int h = (def != null) ? def.defaultH() : 6;
        ScadaSymbolInstance sym = new ScadaSymbolInstance(
                symbolType,
                symbolType + "_" + instanceCounter,
                gridX,
                gridY,
                w,
                h
        );
        if (def != null) {
            for (PortSpec ps : def.ports()) {
                sym.getPorts().add(ps.toPipePort(w, h));
            }
            if (def.domain() == EquipmentDomain.ELECTRICAL) {
                sym.setProperty("voltage", symbolType.contains("24V") ? "24V DC"
                        : (symbolType.contains("230V") ? "230V AC" : "400V 3~ AC"));
            } else if (def.domain() == EquipmentDomain.MAR_SENSOR) {
                sym.setProperty("unit", inferDefaultUnit(symbolType));
                sym.setProperty("rangeMin", "0");
                sym.setProperty("rangeMax", symbolType.contains("Temp") ? "120" : "100");
            } else if (def.domain() == EquipmentDomain.HEATING_HVAC || def.domain() == EquipmentDomain.FLUID_EQUIPMENT) {
                sym.setProperty("fluidType", "WATER");
            }
        }
        return sym;
    }

    /**
     * Recomputes dynamic port offsets when a resizable symbol changes dimensions.
     */
    public static void rebuildPortsForSize(ScadaSymbolInstance sym) {
        if (sym == null) return;
        SymbolDef def = getDefinition(sym.getSymbolType());
        if (def == null || def.ports().isEmpty()) return;
        sym.getPorts().clear();
        for (PortSpec ps : def.ports()) {
            sym.getPorts().add(ps.toPipePort(sym.getGridW(), sym.getGridH()));
        }
    }

    private static String inferDefaultUnit(String symbolType) {
        if (symbolType.contains("Temp") || symbolType.equals("RoomThermostat")) return "°C";
        if (symbolType.contains("Pressure")) return "bar";
        if (symbolType.equals("FlowMeter")) return "m³/h";
        if (symbolType.equals("Calorimeter")) return "kW";
        if (symbolType.equals("HumiditySensor")) return "%RH";
        if (symbolType.equals("Co2Sensor")) return "ppm";
        return "%";
    }
}

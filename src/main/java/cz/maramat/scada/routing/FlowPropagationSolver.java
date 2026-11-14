package cz.maramat.scada.routing;

import cz.maramat.scada.model.ConnectionType;
import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.ScadaPipe;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaScreen;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.tags.RuntimeTagProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Solves automatic upstream-to-downstream fluid pressure and flow propagation across
 * connected pumps, tanks, open valves, and T-junctions, plus explicit pipe flow bindings.
 */
public final class FlowPropagationSolver {

    private FlowPropagationSolver() {
    }

    /**
     * Collects the exact set of tag names that can influence {@link #solve} for the given screen,
     * excluding unrelated process variables such as temperature or energy readouts.
     */
    public static Set<String> collectFlowRelevantTags(ScadaScreen screen) {
        Set<String> tags = new LinkedHashSet<>();
        if (screen == null) return tags;
        for (ScadaPipe pipe : screen.getPipes()) {
            if (!pipe.isAutoFlow()) {
                addIfPresent(tags, pipe.getFlowTag());
            }
        }
        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            String type = sym.getSymbolType();
            Map<String, String> b = sym.getTagBindings();
            if (b == null || b.isEmpty()) continue;
            if (type.endsWith("Pump") || type.equals("BlowerFan") || type.equals("VentilationFan")
                    || type.equals("AxialFan") || type.equals("CentrifugalFan") || type.equals("AirBlower")) {
                addIfPresent(tags, b.get("RunTag"));
                addIfPresent(tags, b.get("SpeedTag"));
            } else if (type.contains("Tank") || type.equals("HopperSilo") || type.equals("OpenBasin")) {
                addIfPresent(tags, b.get("LevelTag"));
            } else if (type.equals("Inlet")
                    || type.equals("PushButton") || type.equals("EStopMushroom") || type.equals("SelectorSwitch")
                    || type.equals("CircuitBreaker") || type.equals("Contactor") || type.equals("VfdDrive")
                    || type.equals("OverloadRelay") || type.equals("Fuse")) {
                for (String role : TAG_ROLES) {
                    addIfPresent(tags, b.get(role));
                }
            } else if (type.equals("HandValve") || type.equals("SolenoidValve") || type.equals("CheckValve")
                    || type.equals("SafetyReliefValve") || type.equals("Damper")
                    || type.equals("GateValve") || type.equals("GlobeValve") || type.equals("BallValve")
                    || type.equals("ButterflyValve") || type.equals("NeedleValve") || type.equals("PlugValve")
                    || type.equals("DiaphragmValve") || type.equals("FourWayValve")
                    || type.equals("ZoneValve") || type.equals("ExpansionValve")) {
                String openTag = b.get("OpenTag");
                if (openTag == null || openTag.isBlank()) {
                    openTag = b.get("RunTag");
                }
                addIfPresent(tags, openTag);
            } else if (type.equals("ControlValve") || type.equals("TwoWayControlValve")
                    || type.equals("ThreeWayMixingValve") || type.equals("MotorizedValve")
                    || type.equals("PneumaticValve")) {
                String posTag = b.get("PositionTag");
                if (posTag == null || posTag.isBlank()) {
                    posTag = b.get("SetpointTag");
                }
                addIfPresent(tags, posTag);
            }
        }
        return tags;
    }

    private static void addIfPresent(Set<String> out, String tagName) {
        if (tagName != null && !tagName.isBlank()) {
            out.add(tagName.trim());
        }
    }

    public static Map<String, FlowState> solve(ScadaScreen screen, ScadaProject project, RuntimeTagProvider tags) {
        Map<String, FlowState> result = new HashMap<>();
        if (screen == null) return result;

        // Index symbols and adjacency: symbolId -> connected pipes
        Map<String, ScadaSymbolInstance> symById = new HashMap<>();
        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            symById.put(sym.getId(), sym);
        }

        Map<String, List<ScadaPipe>> pipesBySymbol = new HashMap<>();
        Map<String, List<ScadaPipe>> teeChildrenByParent = new HashMap<>();
        for (ScadaPipe pipe : screen.getPipes()) {
            result.put(pipe.getId(), new FlowState(false, 0.0, pipe.getFluidType(), 1));
            if (pipe.getSourceSymbolId() != null) {
                pipesBySymbol.computeIfAbsent(pipe.getSourceSymbolId(), k -> new ArrayList<>()).add(pipe);
            }
            if (pipe.getTargetSymbolId() != null && !pipe.getTargetSymbolId().equals(pipe.getSourceSymbolId())) {
                pipesBySymbol.computeIfAbsent(pipe.getTargetSymbolId(), k -> new ArrayList<>()).add(pipe);
            }
            if (pipe.getSourceTeePipeId() != null && !pipe.getSourceTeePipeId().isBlank()) {
                teeChildrenByParent.computeIfAbsent(pipe.getSourceTeePipeId(), k -> new ArrayList<>()).add(pipe);
            }
        }

        // Pass A: Solve electrical / control / pneumatic wires first so energized coils/pwr can drive pumps & valves
        Map<String, Boolean> energizedSymbols = new HashMap<>();
        Set<String> activePipes = new HashSet<>();
        solveWiresAndCoupling(screen, project, tags, symById, pipesBySymbol, teeChildrenByParent, result, energizedSymbols, activePipes);

        // Pass B: Apply explicit pipe flow modes & seed BFS from active fluid/signal sources
        Deque<ScadaPipe> queue = new ArrayDeque<>();

        for (ScadaPipe pipe : screen.getPipes()) {
            ScadaPipe.FlowMode mode = pipe.getFlowMode();
            if (mode == ScadaPipe.FlowMode.ALWAYS_OFF) {
                result.put(pipe.getId(), new FlowState(false, 0.0, pipe.getFluidType(), 1));
            } else if (mode == ScadaPipe.FlowMode.ALWAYS_ON) {
                result.put(pipe.getId(), new FlowState(true, 1.0, pipe.getFluidType(), 1));
                activePipes.add(pipe.getId());
                queue.add(pipe);
            } else if (mode == ScadaPipe.FlowMode.TAG || !pipe.isAutoFlow()) {
                double val = readBinding(project, tags, pipe.getFlowTag(), 0.0);
                if (val > 0.0) {
                    result.put(pipe.getId(), new FlowState(true, val, pipe.getFluidType(), 1));
                    activePipes.add(pipe.getId());
                    queue.add(pipe);
                }
            }
        }

        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            double sourceFlow = evaluateSourceFlow(sym, project, tags, energizedSymbols);
            if (sourceFlow > 0.0) {
                List<ScadaPipe> connected = pipesBySymbol.getOrDefault(sym.getId(), List.of());
                for (ScadaPipe pipe : connected) {
                    if (pipe.getFlowMode() == ScadaPipe.FlowMode.AUTO && pipe.isAutoFlow()
                            && !activePipes.contains(pipe.getId()) && canSourceOnPipe(sym, pipe)) {
                        int dir = sym.getId().equals(pipe.getTargetSymbolId()) && !sym.getId().equals(pipe.getSourceSymbolId()) ? -1 : 1;
                        result.put(pipe.getId(), new FlowState(true, sourceFlow, pipe.getFluidType(), dir));
                        activePipes.add(pipe.getId());
                        queue.add(pipe);
                    }
                }
            }
        }

        Map<String, Double> conductanceCache = new HashMap<>(Math.max(16, symById.size() * 2));

        // 2. Propagate downstream through open valves, pass-through fittings, and tee branches
        while (!queue.isEmpty()) {
            ScadaPipe current = queue.poll();
            FlowState curState = result.get(current.getId());

            // Propagate to tee children
            for (ScadaPipe child : teeChildrenByParent.getOrDefault(current.getId(), List.of())) {
                if (child.getFlowMode() == ScadaPipe.FlowMode.AUTO && child.isAutoFlow() && !activePipes.contains(child.getId())) {
                    FluidType ft = (child.getFluidType() == FluidType.EMPTY) ? curState.fluidType() : child.getFluidType();
                    result.put(child.getId(), new FlowState(true, curState.flowRate(), ft, 1));
                    activePipes.add(child.getId());
                    queue.add(child);
                }
            }

            // Propagate across endpoints if equipment conducts
            propagateAcrossEndpoint(current.getSourceSymbolId(), current, curState,
                    symById, pipesBySymbol, conductanceCache, project, tags, energizedSymbols, result, activePipes, queue);
            propagateAcrossEndpoint(current.getTargetSymbolId(), current, curState,
                    symById, pipesBySymbol, conductanceCache, project, tags, energizedSymbols, result, activePipes, queue);
        }

        // 3. Only animate flow (flowRate > 0) on pressurized pipes that have a path to an actual sink/load or form a closed loop.
        Set<String> flowingPipes = findFlowingPipesToSinks(screen.getPipes(), activePipes, result, symById, pipesBySymbol, conductanceCache, project, tags, energizedSymbols);
        for (ScadaPipe pipe : screen.getPipes()) {
            FlowState fs = result.get(pipe.getId());
            if (fs != null && fs.pressurized() && !flowingPipes.contains(pipe.getId())) {
                result.put(pipe.getId(), new FlowState(true, 0.0, fs.fluidType(), fs.direction()));
            }
        }

        return result;
    }

    private static final String[] TAG_ROLES = {"RunTag", "OpenTag", "ValueTag"};

    private static void solveWiresAndCoupling(ScadaScreen screen, ScadaProject project, RuntimeTagProvider tags,
                                              Map<String, ScadaSymbolInstance> symById,
                                              Map<String, List<ScadaPipe>> pipesBySymbol,
                                              Map<String, List<ScadaPipe>> teeChildrenByParent,
                                              Map<String, FlowState> result,
                                              Map<String, Boolean> energizedSymbols,
                                              Set<String> activeWires) {
        Deque<ScadaPipe> wireQueue = new ArrayDeque<>();

        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            double src = evaluateSourceFlow(sym, project, tags, energizedSymbols);
            if (src > 0.0) {
                for (ScadaPipe pipe : pipesBySymbol.getOrDefault(sym.getId(), List.of())) {
                    if (isWirePipe(pipe) && pipe.getFlowMode() == ScadaPipe.FlowMode.AUTO && pipe.isAutoFlow()
                            && !activeWires.contains(pipe.getId()) && canSourceOnPipe(sym, pipe)) {
                        int dir = sym.getId().equals(pipe.getTargetSymbolId()) && !sym.getId().equals(pipe.getSourceSymbolId()) ? -1 : 1;
                        result.put(pipe.getId(), new FlowState(true, src, pipe.getFluidType(), dir));
                        activeWires.add(pipe.getId());
                        wireQueue.add(pipe);
                    }
                }
            }
        }

        while (!wireQueue.isEmpty()) {
            ScadaPipe current = wireQueue.poll();
            FlowState curState = result.get(current.getId());
            markControlPortEnergized(current.getSourceSymbolId(), current.getSourcePortId(), energizedSymbols);
            markControlPortEnergized(current.getTargetSymbolId(), current.getTargetPortId(), energizedSymbols);

            for (ScadaPipe child : teeChildrenByParent.getOrDefault(current.getId(), List.of())) {
                if (isWirePipe(child) && !activeWires.contains(child.getId())) {
                    result.put(child.getId(), new FlowState(true, curState.flowRate(), child.getFluidType(), 1));
                    activeWires.add(child.getId());
                    wireQueue.add(child);
                }
            }

            propagateWireEndpoint(current.getSourceSymbolId(), current, curState, symById, pipesBySymbol, project, tags, energizedSymbols, result, activeWires, wireQueue);
            propagateWireEndpoint(current.getTargetSymbolId(), current, curState, symById, pipesBySymbol, project, tags, energizedSymbols, result, activeWires, wireQueue);
        }
    }

    private static void markControlPortEnergized(String symbolId, String portId, Map<String, Boolean> energizedSymbols) {
        if (symbolId == null) return;
        if (portId == null || isControlOrPowerPort(portId)) {
            energizedSymbols.put(symbolId, Boolean.TRUE);
        }
    }

    private static boolean isControlOrPowerPort(String portId) {
        String lower = portId.toLowerCase();
        return lower.equals("pwr") || lower.equals("power") || lower.equals("coil")
                || lower.equals("cmd") || lower.equals("sig") || lower.equals("air")
                || lower.equals("in") || lower.equals("inlet") || lower.equals("l1");
    }

    private static boolean isWirePipe(ScadaPipe pipe) {
        ConnectionType ct = pipe.getConnectionType();
        return ct == ConnectionType.ELECTRICAL_POWER
                || ct == ConnectionType.CONTROL_SIGNAL
                || ct == ConnectionType.PNEUMATIC
                || ct == ConnectionType.CAPILLARY;
    }

    private static void propagateWireEndpoint(String symbolId, ScadaPipe fromPipe, FlowState curState,
                                              Map<String, ScadaSymbolInstance> symById,
                                              Map<String, List<ScadaPipe>> pipesBySymbol,
                                              ScadaProject project, RuntimeTagProvider tags,
                                              Map<String, Boolean> energizedSymbols,
                                              Map<String, FlowState> result, Set<String> activeWires,
                                              Deque<ScadaPipe> wireQueue) {
        if (symbolId == null) return;
        ScadaSymbolInstance sym = symById.get(symbolId);
        if (sym == null) return;
        double conductance = evaluateConductance(sym, project, tags, energizedSymbols);
        if (conductance <= 0.0) return;

        for (ScadaPipe neighbor : pipesBySymbol.getOrDefault(symbolId, List.of())) {
            if (neighbor.getId().equals(fromPipe.getId()) || !isWirePipe(neighbor)) continue;
            if (!activeWires.contains(neighbor.getId())) {
                int dir = symbolId.equals(neighbor.getSourceSymbolId()) ? 1 : -1;
                result.put(neighbor.getId(), new FlowState(true, curState.flowRate() * conductance, neighbor.getFluidType(), dir));
                activeWires.add(neighbor.getId());
                wireQueue.add(neighbor);
            }
        }
    }

    private static double getConductanceCached(ScadaSymbolInstance sym,
                                               Map<String, Double> conductanceCache,
                                               ScadaProject project, RuntimeTagProvider tags,
                                               Map<String, Boolean> energizedSymbols) {
        Double cached = conductanceCache.get(sym.getId());
        if (cached != null) return cached;
        double c = evaluateConductance(sym, project, tags, energizedSymbols);
        conductanceCache.put(sym.getId(), c);
        return c;
    }

    private static void propagateAcrossEndpoint(String symbolId, ScadaPipe fromPipe, FlowState curState,
                                                Map<String, ScadaSymbolInstance> symById,
                                                Map<String, List<ScadaPipe>> pipesBySymbol,
                                                Map<String, Double> conductanceCache,
                                                ScadaProject project, RuntimeTagProvider tags,
                                                Map<String, Boolean> energizedSymbols,
                                                Map<String, FlowState> result, Set<String> activePipes,
                                                Deque<ScadaPipe> queue) {
        if (symbolId == null) return;
        ScadaSymbolInstance sym = symById.get(symbolId);
        if (sym == null) return;
        double conductance = getConductanceCached(sym, conductanceCache, project, tags, energizedSymbols);
        if (conductance <= 0.0) return;

        boolean isCheckValve = "CheckValve".equals(sym.getSymbolType());
        if (isCheckValve) {
            String fromPortId = fromPipe != null ? getConnectedPortId(fromPipe, symbolId) : null;
            if (fromPortId != null && !isInletPortId(fromPortId)) {
                return; // Check valve blocks reverse flow entering through its outlet
            }
        }

        String fromPipeId = fromPipe != null ? fromPipe.getId() : null;
        String fromPortId = fromPipe != null ? getConnectedPortId(fromPipe, symbolId) : null;
        String symType = sym.getSymbolType();
        boolean isHeatExchanger = symType.equals("PlateHeatExchanger")
                || symType.equals("ShellAndTubeHeatExchanger")
                || symType.equals("CoilHeatExchanger");
        boolean directionalEquipment = "CheckValve".equals(symType)
                || symType.endsWith("Pump")
                || symType.contains("Boiler")
                || symType.equals("Chiller")
                || symType.contains("HeatPump");

        double nextRate = curState.flowRate() * conductance;
        for (ScadaPipe neighbor : pipesBySymbol.getOrDefault(symbolId, List.of())) {
            if (neighbor.getId().equals(fromPipeId)) continue;
            // Don't cross from fluid pipe into electrical/control wire or vice versa
            if (fromPipe != null && isWirePipe(fromPipe) != isWirePipe(neighbor)) continue;
            if (isHeatExchanger && fromPortId != null) {
                String neighborPortId = getConnectedPortId(neighbor, symbolId);
                if (neighborPortId != null && !sameExchangerSide(fromPortId, neighborPortId)) {
                    continue; // Keep primary and secondary heat-exchanger circuits hydraulically isolated
                }
            }
            if (directionalEquipment) {
                String neighborPortId = getConnectedPortId(neighbor, symbolId);
                if (fromPortId != null && !isInletPortId(fromPortId)) {
                    // If entering from an outlet port, don't backfeed out of an inlet port
                    if (neighborPortId != null && isInletPortId(neighborPortId)) {
                        continue;
                    }
                } else if (neighborPortId != null && isInletPortId(neighborPortId)) {
                    continue; // Directional equipment only discharges out of its outlet
                }
            }
            if (neighbor.getFlowMode() == ScadaPipe.FlowMode.AUTO && neighbor.isAutoFlow() && !activePipes.contains(neighbor.getId())) {
                FluidType ft = (neighbor.getFluidType() == FluidType.EMPTY) ? curState.fluidType() : neighbor.getFluidType();
                int dir = symbolId.equals(neighbor.getSourceSymbolId()) ? 1 : -1;
                result.put(neighbor.getId(), new FlowState(true, nextRate, ft, dir));
                activePipes.add(neighbor.getId());
                queue.add(neighbor);
            }
        }
    }

    private static ScadaPipe findPipe(List<ScadaPipe> pipes, String pipeId) {
        for (ScadaPipe p : pipes) {
            if (p.getId().equals(pipeId)) return p;
        }
        return null;
    }

    private static boolean canSourceOnPipe(ScadaSymbolInstance sym, ScadaPipe pipe) {
        String portId = getConnectedPortId(pipe, sym.getId());
        if (portId != null) {
            return !isInletPortId(portId);
        }
        // Fallback when portIds are omitted: only source out of pipes whose sourceSymbolId is this symbol
        return sym.getId().equals(pipe.getSourceSymbolId());
    }

    private static String getConnectedPortId(ScadaPipe pipe, String symbolId) {
        if (symbolId.equals(pipe.getSourceSymbolId())) {
            return pipe.getSourcePortId();
        }
        if (symbolId.equals(pipe.getTargetSymbolId())) {
            return pipe.getTargetPortId();
        }
        return null;
    }

    private static boolean isInletPortId(String portId) {
        if (portId == null) return false;
        String lower = portId.toLowerCase();
        return lower.equals("inlet") || lower.equals("in") || lower.equals("suction")
                || lower.equals("top_in") || lower.equals("bot_in") || lower.equals("fill")
                || lower.equals("air") || lower.equals("return") || lower.equals("feed")
                || lower.endsWith("_in") || lower.endsWith("_inlet");
    }

    private static double evaluateSourceFlow(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags,
                                             Map<String, Boolean> energizedSymbols) {
        String type = sym.getSymbolType();
        if (type.endsWith("Pump") || type.equals("BlowerFan") || type.equals("VentilationFan")
                || type.equals("AxialFan") || type.equals("CentrifugalFan") || type.equals("AirBlower")
                || type.equals("ScrewCompressor") || type.equals("PistonCompressor")
                || type.equals("ScrollCompressor") || type.equals("TurboCompressor")) {
            String runTag = sym.getTagBindings().get("RunTag");
            String speedTag = sym.getTagBindings().get("SpeedTag");
            boolean hasTag = (runTag != null && !runTag.isBlank()) || (speedTag != null && !speedTag.isBlank());
            if (hasTag) {
                double run = (runTag != null && !runTag.isBlank()) ? readBinding(project, tags, runTag, 0.0) : 0.0;
                double speed = (speedTag != null && !speedTag.isBlank()) ? readBinding(project, tags, speedTag, 0.0) : 0.0;
                if (run > 0.0) {
                    return speed > 0.0 ? Math.max(0.2, speed / 100.0) : 1.0;
                }
                return speed > 0.0 ? Math.max(0.2, speed / 100.0) : 0.0;
            }
            if (Boolean.TRUE.equals(energizedSymbols.get(sym.getId()))) {
                return 1.0;
            }
            return Boolean.parseBoolean(sym.getProperties().getOrDefault("Running", "false")) ? 1.0 : 0.0;
        }
        if (type.contains("Tank") || type.equals("HopperSilo") || type.equals("OpenBasin")
                || type.equals("Silo") || type.equals("Hopper") || type.equals("Reactor")
                || type.equals("PrimaryClarifier") || type.equals("SecondaryClarifier")
                || type.equals("AerationBasin") || type.equals("AnaerobicDigester")
                || type.equals("SludgeThickener") || type.equals("RefrigerantReceiver")) {
            String levelTag = sym.getTagBindings().get("LevelTag");
            if (levelTag != null && !levelTag.isBlank()) {
                double lvl = readBinding(project, tags, levelTag, 0.0);
                return lvl > 0.0 ? 0.5 : 0.0;
            }
            try {
                double manualLvl = Double.parseDouble(sym.getProperties().getOrDefault("Level", "65"));
                return manualLvl > 0.0 ? 0.5 : 0.0;
            } catch (NumberFormatException ex) {
                return 0.5;
            }
        }
        if (type.equals("Inlet")) {
            for (String role : TAG_ROLES) {
                String t = sym.getTagBindings().get(role);
                if (t != null && !t.isBlank()) {
                    return readBinding(project, tags, t, 0.0) > 0.0 ? 1.0 : 0.0;
                }
            }
            return Boolean.parseBoolean(sym.getProperties().getOrDefault("Active", "true")) ? 1.0 : 0.0;
        }
        if (type.equals("Boiler") || type.equals("GasBoiler") || type.equals("ElectricBoiler")
                || type.equals("BiomassBoiler") || type.equals("HeatPump") || type.equals("AirSourceHeatPump")
                || type.equals("GroundSourceHeatPump") || type.equals("WaterSourceHeatPump")
                || type.equals("Chiller")) {
            String runTag = sym.getTagBindings().get("RunTag");
            if (runTag != null && !runTag.isBlank()) {
                return readBinding(project, tags, runTag, 0.0) > 0.0 ? 1.0 : 0.0;
            }
            if (Boolean.TRUE.equals(energizedSymbols.get(sym.getId()))) {
                return 1.0;
            }
            return Boolean.parseBoolean(sym.getProperties().getOrDefault("Running", "false")) ? 1.0 : 0.0;
        }
        // Electrical power sources
        if (type.equals("PowerSource400V") || type.equals("PowerSource230V") || type.equals("PowerSource24V")) {
            return Boolean.parseBoolean(sym.getProperties().getOrDefault("Energized", "true")) ? 1.0 : 0.0;
        }
        // Operator buttons / switches: only source signal when pressed/active
        if (type.equals("PushButton") || type.equals("EStopMushroom") || type.equals("SelectorSwitch")) {
            String targetTag = sym.getTagBindings().get("TargetTag");
            if (targetTag != null && !targetTag.isBlank()) {
                return readBinding(project, tags, targetTag, 0.0) != 0.0 ? 1.0 : 0.0;
            }
            for (String role : TAG_ROLES) {
                String t = sym.getTagBindings().get(role);
                if (t != null && !t.isBlank()) {
                    return readBinding(project, tags, t, 0.0) != 0.0 ? 1.0 : 0.0;
                }
            }
            return Boolean.parseBoolean(sym.getProperties().getOrDefault("Pressed", "false")) ? 1.0 : 0.0;
        }
        return 0.0;
    }

    private static double evaluateConductance(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags,
                                              Map<String, Boolean> energizedSymbols) {
        String type = sym.getSymbolType();
        // Tanks and external inlet/outlet boundaries terminate pass-through flow
        if (type.contains("Tank") || type.equals("HopperSilo") || type.equals("OpenBasin")
                || type.equals("Silo") || type.equals("Hopper") || type.equals("Reactor")
                || type.equals("PrimaryClarifier") || type.equals("SecondaryClarifier")
                || type.equals("AerationBasin") || type.equals("AnaerobicDigester")
                || type.equals("SludgeThickener") || type.equals("RefrigerantReceiver")
                || type.equals("Inlet") || type.equals("Outlet")) {
            return 0.0;
        }
        // Stopped pumps/blowers block flow; running pumps may pass/boost flow
        if (type.endsWith("Pump") || type.equals("BlowerFan") || type.equals("VentilationFan")
                || type.equals("AxialFan") || type.equals("CentrifugalFan") || type.equals("AirBlower")
                || type.equals("ScrewCompressor") || type.equals("PistonCompressor")
                || type.equals("ScrollCompressor") || type.equals("TurboCompressor")) {
            return evaluateSourceFlow(sym, project, tags, energizedSymbols) > 0.0 ? 1.0 : 0.0;
        }
        if (type.equals("ThreeWayMixingValve") || type.equals("FourWayValve")) {
            // Mixing/diverting valves always conduct between common port AB and A/B regardless of 0-100% split
            return 1.0;
        }
        if (type.equals("HandValve") || type.equals("SolenoidValve") || type.equals("CheckValve")
                || type.equals("SafetyReliefValve") || type.equals("Damper")
                || type.equals("GateValve") || type.equals("GlobeValve") || type.equals("BallValve")
                || type.equals("ButterflyValve") || type.equals("NeedleValve") || type.equals("PlugValve")
                || type.equals("DiaphragmValve")
                || type.equals("ZoneValve") || type.equals("ExpansionValve")) {
            String openTag = sym.getTagBindings().get("OpenTag");
            if (openTag == null || openTag.isBlank()) {
                openTag = sym.getTagBindings().get("RunTag");
            }
            if (openTag != null && !openTag.isBlank()) {
                return readBinding(project, tags, openTag, 0.0) > 0.0 ? 1.0 : 0.0;
            }
            if (Boolean.TRUE.equals(energizedSymbols.get(sym.getId()))) {
                return 1.0;
            }
            String propOpen = sym.getProperties().getOrDefault("Open", "true");
            return Boolean.parseBoolean(propOpen) ? 1.0 : 0.0;
        }
        if (type.equals("ControlValve") || type.equals("TwoWayControlValve")
                || type.equals("MotorizedValve") || type.equals("PneumaticValve") || type.equals("VavBox")) {
            String posTag = sym.getTagBindings().get("PositionTag");
            if (posTag == null || posTag.isBlank()) {
                posTag = sym.getTagBindings().get("SetpointTag");
            }
            if (posTag != null && !posTag.isBlank()) {
                double pos = readBinding(project, tags, posTag, 0.0);
                return Math.max(0.0, Math.min(1.0, pos / 100.0));
            }
            try {
                double manualPos = Double.parseDouble(sym.getProperties().getOrDefault("Position", "100"));
                return Math.max(0.0, Math.min(1.0, manualPos / 100.0));
            } catch (NumberFormatException ex) {
                return 1.0;
            }
        }
        // Electrical switching / protection devices
        if (type.equals("CircuitBreaker") || type.equals("Contactor") || type.equals("VfdDrive")
                || type.equals("OverloadRelay") || type.equals("Fuse")) {
            String closedTag = sym.getTagBindings().get("ClosedTag");
            if (closedTag != null && !closedTag.isBlank()) {
                return readBinding(project, tags, closedTag, 0.0) > 0.0 ? 1.0 : 0.0;
            }
            for (String role : TAG_ROLES) {
                String t = sym.getTagBindings().get(role);
                if (t != null && !t.isBlank()) {
                    return readBinding(project, tags, t, 0.0) > 0.0 ? 1.0 : 0.0;
                }
            }
            if (type.equals("Contactor") && Boolean.TRUE.equals(energizedSymbols.get(sym.getId()))) {
                return 1.0;
            }
            return Boolean.parseBoolean(sym.getProperties().getOrDefault("Closed", type.equals("Fuse") ? "true" : "false")) ? 1.0 : 0.0;
        }
        if (type.equals("PushButton") || type.equals("EStopMushroom") || type.equals("SelectorSwitch")) {
            return evaluateSourceFlow(sym, project, tags, energizedSymbols) > 0.0 ? 1.0 : 0.0;
        }
        // Inline sensors, fittings, exchangers, boilers, radiators conduct flow straight through
        return 1.0;
    }

    private static Set<String> findFlowingPipesToSinks(List<ScadaPipe> pipes, Set<String> activePipes,
                                                     Map<String, FlowState> currentStates,
                                                     Map<String, ScadaSymbolInstance> symById,
                                                     Map<String, List<ScadaPipe>> pipesBySymbol,
                                                     Map<String, Double> conductanceCache,
                                                     ScadaProject project, RuntimeTagProvider tags,
                                                     Map<String, Boolean> energizedSymbols) {
        Map<String, ScadaPipe> pipeById = new HashMap<>();
        for (ScadaPipe p : pipes) {
            pipeById.put(p.getId(), p);
        }

        Set<String> flowing = new HashSet<>();
        Deque<ScadaPipe> queue = new ArrayDeque<>();
        for (ScadaPipe p : pipes) {
            if (!activePipes.contains(p.getId())) continue;
            FlowState fs = currentStates.get(p.getId());
            int dir = (fs != null && fs.direction() < 0) ? -1 : 1;
            String downstreamSymId = (dir >= 0) ? p.getTargetSymbolId() : p.getSourceSymbolId();
            String downstreamPortId = (dir >= 0) ? p.getTargetPortId() : p.getSourcePortId();

            boolean touchesSink = p.getFlowMode() == ScadaPipe.FlowMode.ALWAYS_ON
                    || !p.isAutoFlow()
                    || isSinkEndpoint(downstreamSymId, downstreamPortId, p.getConnectionType(), symById)
                    || (p.getSourceTeePipeId() != null && !p.getSourceTeePipeId().isBlank() && p.getTargetSymbolId() == null);
            if (touchesSink) {
                flowing.add(p.getId());
                queue.add(p);
            }
        }

        while (!queue.isEmpty()) {
            ScadaPipe cur = queue.poll();
            if (cur.getSourceTeePipeId() != null && !cur.getSourceTeePipeId().isBlank()) {
                ScadaPipe parent = pipeById.get(cur.getSourceTeePipeId());
                if (parent != null && activePipes.contains(parent.getId()) && flowing.add(parent.getId())) {
                    queue.add(parent);
                }
            }
            String srcSymId = cur.getSourceSymbolId();
            if (srcSymId != null) {
                ScadaSymbolInstance sym = symById.get(srcSymId);
                if (sym != null && getConductanceCached(sym, conductanceCache, project, tags, energizedSymbols) > 0.0) {
                    String curPort = getConnectedPortId(cur, srcSymId);
                    boolean isHex = isHeatExchangerType(sym.getSymbolType());
                    for (ScadaPipe neighbor : pipesBySymbol.getOrDefault(srcSymId, List.of())) {
                        if (isHex && curPort != null) {
                            String nPort = getConnectedPortId(neighbor, srcSymId);
                            if (nPort != null && !sameExchangerSide(curPort, nPort)) continue;
                        }
                        if (activePipes.contains(neighbor.getId()) && isWirePipe(cur) == isWirePipe(neighbor) && flowing.add(neighbor.getId())) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
            String tgtSymId = cur.getTargetSymbolId();
            if (tgtSymId != null && !tgtSymId.equals(srcSymId)) {
                ScadaSymbolInstance sym = symById.get(tgtSymId);
                if (sym != null && getConductanceCached(sym, conductanceCache, project, tags, energizedSymbols) > 0.0) {
                    String curPort = getConnectedPortId(cur, tgtSymId);
                    boolean isHex = isHeatExchangerType(sym.getSymbolType());
                    for (ScadaPipe neighbor : pipesBySymbol.getOrDefault(tgtSymId, List.of())) {
                        if (isHex && curPort != null) {
                            String nPort = getConnectedPortId(neighbor, tgtSymId);
                            if (nPort != null && !sameExchangerSide(curPort, nPort)) continue;
                        }
                        if (activePipes.contains(neighbor.getId()) && isWirePipe(cur) == isWirePipe(neighbor) && flowing.add(neighbor.getId())) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
        return flowing;
    }

    private static boolean isSinkEndpoint(String symbolId, String portId, ConnectionType connType,
                                          Map<String, ScadaSymbolInstance> symById) {
        if (symbolId == null) return false;
        ScadaSymbolInstance sym = symById.get(symbolId);
        if (sym == null) return false;
        String type = sym.getSymbolType();
        if (connType == ConnectionType.ELECTRICAL_POWER || connType == ConnectionType.CONTROL_SIGNAL || connType == ConnectionType.PNEUMATIC) {
            return type.equals("ElectricMotor") || type.equals("PilotLamp")
                    || type.equals("SolenoidValve") || type.equals("ControlValve")
                    || type.equals("TwoWayControlValve") || type.equals("ThreeWayMixingValve")
                    || type.equals("MotorizedValve") || type.equals("PneumaticValve")
                    || type.equals("ZoneValve") || type.equals("Contactor")
                    || type.equals("HeaterCoil") || type.equals("ElectricBoiler")
                    || type.endsWith("Pump") || type.equals("Boiler") || type.equals("Chiller") || type.equals("HeatPump")
                    || type.equals("BlowerFan") || type.equals("VentilationFan") || type.equals("AirBlower")
                    || type.equals("NumericReadout") || type.equals("BarGraph") || type.equals("TrendChart");
        }
        if (portId != null && !isInletPortId(portId)) {
            return false;
        }
        return type.equals("Outlet") || type.contains("Tank") || type.equals("HopperSilo") || type.equals("OpenBasin")
                || type.equals("Silo") || type.equals("Hopper") || type.equals("Reactor")
                || type.equals("PrimaryClarifier") || type.equals("SecondaryClarifier") || type.equals("AerationBasin")
                || type.equals("AnaerobicDigester") || type.equals("SludgeThickener") || type.equals("UvDisinfection")
                || type.equals("Radiator") || type.equals("FloorHeatingManifold") || type.equals("UnderfloorHeatingZone")
                || type.equals("FanCoilUnit") || type.equals("AhuUnit") || type.equals("HeatingCoil") || type.equals("CoolingCoil")
                || type.equals("PlateHeatExchanger") || type.equals("ShellAndTubeHeatExchanger") || type.equals("CoilHeatExchanger")
                || type.equals("Separator") || type.equals("Cyclone") || type.equals("Scrubber") || type.equals("FilterPress");
    }

    private static boolean isHeatExchangerType(String symType) {
        return "PlateHeatExchanger".equals(symType)
                || "ShellAndTubeHeatExchanger".equals(symType)
                || "CoilHeatExchanger".equals(symType);
    }

    private static boolean sameExchangerSide(String portA, String portB) {
        String a = portA.toLowerCase();
        String b = portB.toLowerCase();
        boolean aPri = a.startsWith("pri") || a.startsWith("hot");
        boolean bPri = b.startsWith("pri") || b.startsWith("hot");
        boolean aSec = a.startsWith("sec") || a.startsWith("cold");
        boolean bSec = b.startsWith("sec") || b.startsWith("cold");
        if (aPri || aSec || bPri || bSec) {
            return (aPri && bPri) || (aSec && bSec);
        }
        return true;
    }

    private static double readBinding(ScadaProject project, RuntimeTagProvider tags, String tagName, double fallback) {
        if (tagName == null || tagName.isBlank() || project == null || tags == null) {
            return fallback;
        }
        if (project.getTag(tagName) == null) {
            return fallback;
        }
        return tags.readTag(project, tagName);
    }
}

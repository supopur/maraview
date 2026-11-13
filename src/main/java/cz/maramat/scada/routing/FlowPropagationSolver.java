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
            result.put(pipe.getId(), new FlowState(false, 0.0, pipe.getFluidType()));
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

        // 1. Apply explicit bindings & seed BFS from active sources (running pumps / non-empty tanks)
        Deque<ScadaPipe> queue = new ArrayDeque<>();
        Set<String> activePipes = new HashSet<>();

        for (ScadaPipe pipe : screen.getPipes()) {
            if (!pipe.isAutoFlow()) {
                double val = readBinding(project, tags, pipe.getFlowTag(), 0.0);
                if (val > 0.0) {
                    result.put(pipe.getId(), new FlowState(true, val, pipe.getFluidType()));
                    activePipes.add(pipe.getId());
                    queue.add(pipe);
                }
            }
        }

        for (ScadaSymbolInstance sym : screen.getSymbols()) {
            double sourceFlow = evaluateSourceFlow(sym, project, tags);
            if (sourceFlow > 0.0) {
                List<ScadaPipe> connected = pipesBySymbol.getOrDefault(sym.getId(), List.of());
                for (ScadaPipe pipe : connected) {
                    if (pipe.isAutoFlow() && !activePipes.contains(pipe.getId()) && canSourceOnPipe(sym, pipe)) {
                        // Only seed from discharge/outlet ports (never backwards out of an inlet/suction port)
                        result.put(pipe.getId(), new FlowState(true, sourceFlow, pipe.getFluidType()));
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
                if (child.isAutoFlow() && !activePipes.contains(child.getId())) {
                    FluidType ft = (child.getFluidType() == FluidType.EMPTY) ? curState.fluidType() : child.getFluidType();
                    result.put(child.getId(), new FlowState(true, curState.flowRate(), ft));
                    activePipes.add(child.getId());
                    queue.add(child);
                }
            }

            // Propagate across endpoints if equipment conducts
            propagateAcrossEndpoint(current.getSourceSymbolId(), current, curState,
                    symById, pipesBySymbol, conductanceCache, project, tags, result, activePipes, queue);
            propagateAcrossEndpoint(current.getTargetSymbolId(), current, curState,
                    symById, pipesBySymbol, conductanceCache, project, tags, result, activePipes, queue);
        }

        // 3. Only animate flow (flowRate > 0) on pressurized pipes that have a path to an actual sink/load.
        // Dead-ended pipes (e.g. filled tank -> closed solenoid valve, power -> open switch) stay pressurized
        // with flowRate = 0.0 (no water bubbles, paused wire animation).
        Set<String> flowingPipes = findFlowingPipesToSinks(screen.getPipes(), activePipes, symById, pipesBySymbol, conductanceCache, project, tags);
        for (ScadaPipe pipe : screen.getPipes()) {
            FlowState fs = result.get(pipe.getId());
            if (fs != null && fs.pressurized() && !flowingPipes.contains(pipe.getId())) {
                result.put(pipe.getId(), new FlowState(true, 0.0, fs.fluidType()));
            }
        }

        return result;
    }

    private static final String[] TAG_ROLES = {"RunTag", "OpenTag", "ValueTag"};

    private static double getConductanceCached(ScadaSymbolInstance sym,
                                               Map<String, Double> conductanceCache,
                                               ScadaProject project, RuntimeTagProvider tags) {
        Double cached = conductanceCache.get(sym.getId());
        if (cached != null) return cached;
        double c = evaluateConductance(sym, project, tags);
        conductanceCache.put(sym.getId(), c);
        return c;
    }

    private static void propagateAcrossEndpoint(String symbolId, ScadaPipe fromPipe, FlowState curState,
                                                Map<String, ScadaSymbolInstance> symById,
                                                Map<String, List<ScadaPipe>> pipesBySymbol,
                                                Map<String, Double> conductanceCache,
                                                ScadaProject project, RuntimeTagProvider tags,
                                                Map<String, FlowState> result, Set<String> activePipes,
                                                Deque<ScadaPipe> queue) {
        if (symbolId == null) return;
        ScadaSymbolInstance sym = symById.get(symbolId);
        if (sym == null) return;
        double conductance = getConductanceCached(sym, conductanceCache, project, tags);
        if (conductance <= 0.0) return;

        boolean isCheckValve = "CheckValve".equals(sym.getSymbolType());
        if (isCheckValve) {
            String fromPortId = fromPipe != null ? getConnectedPortId(fromPipe, symbolId) : null;
            if (fromPortId != null && !isInletPortId(fromPortId)) {
                return; // Check valve blocks reverse flow entering through its outlet
            }
        }

        String fromPipeId = fromPipe != null ? fromPipe.getId() : null;
        double nextRate = curState.flowRate() * conductance;
        for (ScadaPipe neighbor : pipesBySymbol.getOrDefault(symbolId, List.of())) {
            if (neighbor.getId().equals(fromPipeId)) continue;
            if (isCheckValve) {
                String neighborPortId = getConnectedPortId(neighbor, symbolId);
                if (neighborPortId != null && isInletPortId(neighborPortId)) {
                    continue; // Check valve only discharges out of its outlet
                }
            }
            if (neighbor.isAutoFlow() && !activePipes.contains(neighbor.getId())) {
                FluidType ft = (neighbor.getFluidType() == FluidType.EMPTY) ? curState.fluidType() : neighbor.getFluidType();
                result.put(neighbor.getId(), new FlowState(true, nextRate, ft));
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

    private static double evaluateSourceFlow(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags) {
        String type = sym.getSymbolType();
        if (type.endsWith("Pump") || type.equals("BlowerFan") || type.equals("VentilationFan") || type.equals("AirBlower")) {
            String runTag = sym.getTagBindings().get("RunTag");
            String speedTag = sym.getTagBindings().get("SpeedTag");
            double run = (runTag != null && !runTag.isBlank()) ? readBinding(project, tags, runTag, 0.0) : 0.0;
            double speed = (speedTag != null && !speedTag.isBlank()) ? readBinding(project, tags, speedTag, 0.0) : 0.0;
            if (run > 0.0) {
                return speed > 0.0 ? Math.max(0.2, speed / 100.0) : 1.0;
            }
            return speed > 0.0 ? Math.max(0.2, speed / 100.0) : 0.0;
        }
        if (type.contains("Tank") || type.equals("HopperSilo") || type.equals("OpenBasin")) {
            String levelTag = sym.getTagBindings().get("LevelTag");
            if (levelTag != null && !levelTag.isBlank()) {
                double lvl = readBinding(project, tags, levelTag, 0.0);
                return lvl > 0.0 ? 0.5 : 0.0;
            }
            return 0.0;
        }
        if (type.equals("Inlet")) {
            for (String role : TAG_ROLES) {
                String t = sym.getTagBindings().get(role);
                if (t != null && !t.isBlank()) {
                    return readBinding(project, tags, t, 0.0) > 0.0 ? 1.0 : 0.0;
                }
            }
            return 1.0;
        }
        // Electrical power sources
        if (type.equals("PowerSource400V") || type.equals("PowerSource230V") || type.equals("PowerSource24V")) {
            return 1.0;
        }
        // Operator buttons / switches: only source signal when pressed/active
        if (type.equals("PushButton") || type.equals("EStopMushroom") || type.equals("SelectorSwitch")) {
            for (String role : TAG_ROLES) {
                String t = sym.getTagBindings().get(role);
                if (t != null && !t.isBlank()) {
                    return readBinding(project, tags, t, 0.0) != 0.0 ? 1.0 : 0.0;
                }
            }
            return 0.0;
        }
        return 0.0;
    }

    private static double evaluateConductance(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags) {
        String type = sym.getSymbolType();
        // Tanks and external inlet/outlet boundaries terminate pass-through flow
        if (type.contains("Tank") || type.equals("HopperSilo") || type.equals("OpenBasin")
                || type.equals("Inlet") || type.equals("Outlet")) {
            return 0.0;
        }
        // Stopped pumps/blowers block flow; running pumps may pass/boost flow
        if (type.endsWith("Pump") || type.equals("BlowerFan") || type.equals("VentilationFan") || type.equals("AirBlower")) {
            return evaluateSourceFlow(sym, project, tags) > 0.0 ? 1.0 : 0.0;
        }
        if (type.equals("HandValve") || type.equals("SolenoidValve") || type.equals("CheckValve")
                || type.equals("SafetyReliefValve") || type.equals("Damper")
                || type.equals("GateValve") || type.equals("GlobeValve") || type.equals("BallValve")
                || type.equals("ButterflyValve") || type.equals("NeedleValve") || type.equals("PlugValve")
                || type.equals("DiaphragmValve") || type.equals("FourWayValve")) {
            String openTag = sym.getTagBindings().get("OpenTag");
            if (openTag == null || openTag.isBlank()) {
                openTag = sym.getTagBindings().get("RunTag");
            }
            if (openTag != null && !openTag.isBlank()) {
                return readBinding(project, tags, openTag, 0.0) > 0.0 ? 1.0 : 0.0;
            }
            String propOpen = sym.getProperties().getOrDefault("Open", "true");
            return Boolean.parseBoolean(propOpen) ? 1.0 : 0.0;
        }
        if (type.equals("ControlValve") || type.equals("TwoWayControlValve") || type.equals("ThreeWayMixingValve")
                || type.equals("MotorizedValve") || type.equals("PneumaticValve")) {
            String posTag = sym.getTagBindings().get("PositionTag");
            if (posTag == null || posTag.isBlank()) {
                posTag = sym.getTagBindings().get("SetpointTag");
            }
            if (posTag != null && !posTag.isBlank()) {
                double pos = readBinding(project, tags, posTag, 0.0);
                return Math.max(0.0, Math.min(1.0, pos / 100.0));
            }
            return 1.0;
        }
        // Electrical switching / protection devices
        if (type.equals("CircuitBreaker") || type.equals("Contactor") || type.equals("VfdDrive")
                || type.equals("OverloadRelay") || type.equals("Fuse")) {
            for (String role : TAG_ROLES) {
                String t = sym.getTagBindings().get(role);
                if (t != null && !t.isBlank()) {
                    return readBinding(project, tags, t, 0.0) > 0.0 ? 1.0 : 0.0;
                }
            }
            return type.equals("Fuse") ? 1.0 : 0.0;
        }
        if (type.equals("PushButton") || type.equals("EStopMushroom") || type.equals("SelectorSwitch")) {
            return evaluateSourceFlow(sym, project, tags) > 0.0 ? 1.0 : 0.0;
        }
        // Inline sensors (FlowMeter, PressureSensor, TempSensor, HeaterCoil) pass flow straight through
        return 1.0;
    }

    private static Set<String> findFlowingPipesToSinks(List<ScadaPipe> pipes, Set<String> activePipes,
                                                     Map<String, ScadaSymbolInstance> symById,
                                                     Map<String, List<ScadaPipe>> pipesBySymbol,
                                                     Map<String, Double> conductanceCache,
                                                     ScadaProject project, RuntimeTagProvider tags) {
        Map<String, ScadaPipe> pipeById = new HashMap<>();
        for (ScadaPipe p : pipes) {
            pipeById.put(p.getId(), p);
        }

        Set<String> flowing = new HashSet<>();
        Deque<ScadaPipe> queue = new ArrayDeque<>();
        for (ScadaPipe p : pipes) {
            if (!activePipes.contains(p.getId())) continue;
            boolean touchesSink = !p.isAutoFlow()
                    || isSinkEndpoint(p.getTargetSymbolId(), p.getTargetPortId(), p.getConnectionType(), symById)
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
                if (sym != null && getConductanceCached(sym, conductanceCache, project, tags) > 0.0) {
                    for (ScadaPipe neighbor : pipesBySymbol.getOrDefault(srcSymId, List.of())) {
                        if (activePipes.contains(neighbor.getId()) && flowing.add(neighbor.getId())) {
                            queue.add(neighbor);
                        }
                    }
                }
            }
            String tgtSymId = cur.getTargetSymbolId();
            if (tgtSymId != null && !tgtSymId.equals(srcSymId)) {
                ScadaSymbolInstance sym = symById.get(tgtSymId);
                if (sym != null && getConductanceCached(sym, conductanceCache, project, tags) > 0.0) {
                    for (ScadaPipe neighbor : pipesBySymbol.getOrDefault(tgtSymId, List.of())) {
                        if (activePipes.contains(neighbor.getId()) && flowing.add(neighbor.getId())) {
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
        if (connType == ConnectionType.ELECTRICAL_POWER || connType == ConnectionType.CONTROL_SIGNAL) {
            return type.equals("ElectricMotor") || type.equals("PilotLamp")
                    || type.equals("SolenoidValve") || type.equals("ControlValve")
                    || type.equals("TwoWayControlValve") || type.equals("ThreeWayMixingValve")
                    || type.equals("HeaterCoil") || type.endsWith("Pump")
                    || type.equals("BlowerFan") || type.equals("VentilationFan") || type.equals("AirBlower")
                    || type.equals("NumericReadout") || type.equals("BarGraph") || type.equals("TrendChart");
        }
        if (portId != null && !isInletPortId(portId)) {
            // If connected to an outlet port of a tank, it's not a sink (that's the source side)
            return false;
        }
        return type.equals("Outlet") || type.contains("Tank") || type.equals("HopperSilo") || type.equals("OpenBasin")
                || type.equals("PrimaryClarifier") || type.equals("AerationBasin")
                || type.equals("AnaerobicDigester") || type.equals("SludgeThickener")
                || type.equals("UvDisinfection");
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

package cz.maramat.scada.routing;

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
                    if (pipe.isAutoFlow() && !activePipes.contains(pipe.getId())) {
                        // Discharge or general port from running pump / tank
                        result.put(pipe.getId(), new FlowState(true, sourceFlow, pipe.getFluidType()));
                        activePipes.add(pipe.getId());
                        queue.add(pipe);
                    }
                }
            }
        }

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
            propagateAcrossEndpoint(current.getSourceSymbolId(), current.getId(), curState,
                    symById, pipesBySymbol, project, tags, result, activePipes, queue);
            propagateAcrossEndpoint(current.getTargetSymbolId(), current.getId(), curState,
                    symById, pipesBySymbol, project, tags, result, activePipes, queue);
        }

        return result;
    }

    private static void propagateAcrossEndpoint(String symbolId, String fromPipeId, FlowState curState,
                                                Map<String, ScadaSymbolInstance> symById,
                                                Map<String, List<ScadaPipe>> pipesBySymbol,
                                                ScadaProject project, RuntimeTagProvider tags,
                                                Map<String, FlowState> result, Set<String> activePipes,
                                                Deque<ScadaPipe> queue) {
        if (symbolId == null) return;
        ScadaSymbolInstance sym = symById.get(symbolId);
        if (sym == null) return;
        double conductance = evaluateConductance(sym, project, tags);
        if (conductance <= 0.0) return;

        double nextRate = curState.flowRate() * conductance;
        for (ScadaPipe neighbor : pipesBySymbol.getOrDefault(symbolId, List.of())) {
            if (neighbor.getId().equals(fromPipeId)) continue;
            if (neighbor.isAutoFlow() && !activePipes.contains(neighbor.getId())) {
                FluidType ft = (neighbor.getFluidType() == FluidType.EMPTY) ? curState.fluidType() : neighbor.getFluidType();
                result.put(neighbor.getId(), new FlowState(true, nextRate, ft));
                activePipes.add(neighbor.getId());
                queue.add(neighbor);
            }
        }
    }

    private static double evaluateSourceFlow(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags) {
        String type = sym.getSymbolType();
        if (type.endsWith("Pump") || type.equals("BlowerFan")) {
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
        return 0.0;
    }

    private static double evaluateConductance(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags) {
        String type = sym.getSymbolType();
        // Tanks terminate flow (do not pass pressure straight through unless they are a source)
        if (type.contains("Tank") || type.equals("HopperSilo") || type.equals("OpenBasin")) {
            return 0.0;
        }
        if (type.equals("HandValve") || type.equals("SolenoidValve") || type.equals("CheckValve") || type.equals("Damper")) {
            String openTag = sym.getTagBindings().getOrDefault("OpenTag", sym.getTagBindings().get("RunTag"));
            if (openTag != null && !openTag.isBlank()) {
                return readBinding(project, tags, openTag, 0.0) > 0.0 ? 1.0 : 0.0;
            }
            String propOpen = sym.getProperties().getOrDefault("Open", "true");
            return Boolean.parseBoolean(propOpen) ? 1.0 : 0.0;
        }
        if (type.equals("ControlValve")) {
            String posTag = sym.getTagBindings().getOrDefault("PositionTag", sym.getTagBindings().get("SetpointTag"));
            if (posTag != null && !posTag.isBlank()) {
                double pos = readBinding(project, tags, posTag, 0.0);
                return Math.max(0.0, Math.min(1.0, pos / 100.0));
            }
            return 1.0;
        }
        // Inline sensors (FlowMeter, PressureSensor, TempSensor, HeaterCoil) pass flow straight through
        return 1.0;
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

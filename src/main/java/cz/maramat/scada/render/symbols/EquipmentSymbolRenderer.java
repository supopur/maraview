package cz.maramat.scada.render.symbols;

import cz.maramat.scada.model.FluidType;
import cz.maramat.scada.model.PipePort;
import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;
import cz.maramat.scada.render.SymbolLayerCache;
import cz.maramat.scada.tags.RuntimeTagProvider;
import cz.maramat.scada.ui.SymbolRenderer;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;

/**
 * Unified SymbolRenderer dispatching to 3D industrial equipment, instrumentation, and operator control renderers.
 */
public class EquipmentSymbolRenderer implements SymbolRenderer {

    @FunctionalInterface
    public interface ExtensionRenderer {
        boolean paint(Graphics2D g, ScadaSymbolInstance sym, int w, int h, int gPx,
                      ScadaProject project, RuntimeTagProvider tags, EquipmentState state, long timeMs);
    }

    private ExtensionRenderer extensionRenderer = new InstrumentationControlRenderer();

    public void setExtensionRenderer(ExtensionRenderer extensionRenderer) {
        this.extensionRenderer = extensionRenderer;
    }

    @Override
    public void paintSymbol(Graphics2D g, ScadaSymbolInstance sym, int gPx,
                            ScadaProject project, RuntimeTagProvider tagProvider,
                            SymbolLayerCache layerCache, long animationTimeMs, boolean operatorMode) {
        int x = sym.getGridX() * gPx;
        int y = sym.getGridY() * gPx;
        int effW = sym.getEffectiveGridW() * gPx;
        int effH = sym.getEffectiveGridH() * gPx;
        int localW = sym.getGridW() * gPx;
        int localH = sym.getGridH() * gPx;

        EquipmentState state = resolveState(sym, project, tagProvider);
        double speedPct = readBoundValue(sym, project, tagProvider, "SpeedTag", state == EquipmentState.RUNNING ? 100.0 : 0.0);
        double rawLevel = readBoundValue(sym, project, tagProvider, "LevelTag", 65.0);
        double minLevel = readDoubleProp(sym, "Min", "rangeMin", 0.0);
        double maxLevel = readDoubleProp(sym, "Max", "rangeMax", 100.0);
        double levelSpan = maxLevel - minLevel;
        double levelPct = (levelSpan > 0.0) ? ((rawLevel - minLevel) / levelSpan) * 100.0 : rawLevel;
        double posPct = readBoundValue(sym, project, tagProvider, "PositionTag",
                readBoundValue(sym, project, tagProvider, "SetpointTag", 50.0));
        boolean open = isValveOpen(sym, project, tagProvider);
        FluidType fluid = parseFluid(sym.getProperties().get("FluidType"));

        Graphics2D lg = (Graphics2D) g.create();
        try {
            AffineTransform at = new AffineTransform();
            at.translate(x, y);
            switch (sym.getRotationDeg()) {
                case 90 -> {
                    at.translate(effW, 0);
                    at.rotate(Math.PI / 2.0);
                }
                case 180 -> {
                    at.translate(effW, effH);
                    at.rotate(Math.PI);
                }
                case 270 -> {
                    at.translate(0, effH);
                    at.rotate(-Math.PI / 2.0);
                }
                default -> {
                }
            }
            if (sym.isFlipH()) {
                at.translate(localW, 0);
                at.scale(-1.0, 1.0);
            }
            if (sym.isFlipV()) {
                at.translate(0, localH);
                at.scale(1.0, -1.0);
            }
            lg.transform(at);

            for (PipePort port : sym.getPorts()) {
                ScadaShader.paintPortNozzle(lg, port.offsetX() * (double) gPx, port.offsetY() * (double) gPx,
                        port.direction(), gPx, port.connectionType());
            }

            String type = sym.getSymbolType();
            double phase = animationTimeMs / 60.0;
            double numVal = readBoundValue(sym, project, tagProvider, "ValueTag",
                    readBoundValue(sym, project, tagProvider, "SetpointTag", 0.0));
            if (ElectricalSymbolRenderer.paint(lg, sym, localW, localH, state, phase)) {
                // Rendered by ElectricalSymbolRenderer
            } else if (HvacHeatingRenderer.paint(lg, sym, localW, localH, state, phase)) {
                // Rendered by HvacHeatingRenderer
            } else if (WastewaterRenderer.paint(lg, sym, localW, localH, state, phase)) {
                // Rendered by WastewaterRenderer
            } else if (MarSensorRenderer.paint(lg, sym, localW, localH, state, numVal)) {
                // Rendered by MarSensorRenderer
            } else if (isRotatingMachinery(type)) {
                RotatingMachineryRenderer.paint(lg, type, localW, localH, gPx, state, speedPct / 100.0, animationTimeMs, layerCache);
            } else if (isValveOrDamper(type)) {
                ValveDamperRenderer.paint(lg, type, localW, localH, gPx, open, posPct);
            } else if (isTankOrVessel(type)) {
                TankVesselRenderer.paint(lg, type, localW, localH, gPx, levelPct, fluid, state, animationTimeMs, layerCache);
            } else if (isMaterialHandling(type)) {
                MaterialHandlingRenderer.paint(lg, type, localW, localH, gPx, state, posPct, animationTimeMs);
            } else if (extensionRenderer == null || !extensionRenderer.paint(lg, sym, localW, localH, gPx, project, tagProvider, state, animationTimeMs)) {
                ScadaShader.paintBeveledHousing(lg, 2, 2, localW - 4, localH - 4, 4, Material.CAST_IRON);
            }
        } finally {
            lg.dispose();
        }

        // State overlay (warning / 2 Hz fault halo) in world space
        ScadaShader.paintStateOverlay(g, x, y, effW, effH, state, animationTimeMs);

        // Crisp equipment tag label below symbol
        if (!sym.getName().isBlank()) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, gPx)));
            Rectangle2D bounds = g.getFontMetrics().getStringBounds(sym.getName(), g);
            float tx = (float) (x + (effW - bounds.getWidth()) / 2.0);
            float ty = (float) (y + effH + bounds.getHeight() + 1);
            g.setColor(new Color(0x14, 0x17, 0x1A));
            g.drawString(sym.getName(), tx, ty);
        }
    }

    public static EquipmentState resolveState(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags) {
        if (readBoundValue(sym, project, tags, "FaultTag", 0.0) != 0.0) {
            return EquipmentState.FAULT;
        }
        if (readBoundValue(sym, project, tags, "WarningTag", 0.0) != 0.0) {
            return EquipmentState.WARNING;
        }
        if (readBoundValue(sym, project, tags, "RunTag", 0.0) != 0.0
                || readBoundValue(sym, project, tags, "SpeedTag", 0.0) > 0.0) {
            return EquipmentState.RUNNING;
        }
        return EquipmentState.STOPPED;
    }

    public static double readBoundValue(ScadaSymbolInstance sym, ScadaProject project,
                                        RuntimeTagProvider tags, String role, double fallback) {
        if (sym == null || project == null || tags == null) return fallback;
        String tagName = sym.getTagBindings().get(role);
        if (tagName == null || tagName.isBlank() || project.getTag(tagName) == null) {
            return fallback;
        }
        return tags.readTag(project, tagName);
    }

    private static boolean isValveOpen(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags) {
        String openTag = sym.getTagBindings().getOrDefault("OpenTag", sym.getTagBindings().get("RunTag"));
        if (openTag != null && !openTag.isBlank() && project != null && project.getTag(openTag) != null && tags != null) {
            return tags.readTag(project, openTag) != 0.0;
        }
        return Boolean.parseBoolean(sym.getProperties().getOrDefault("Open", "true"));
    }

    private static double readDoubleProp(ScadaSymbolInstance sym, String key1, String key2, double fallback) {
        String raw = sym.getProperties().getOrDefault(key1, sym.getProperties().get(key2));
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static FluidType parseFluid(String raw) {
        if (raw == null || raw.isBlank()) return FluidType.WATER;
        try {
            return FluidType.valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return FluidType.WATER;
        }
    }

    private static boolean isRotatingMachinery(String t) {
        return switch (t) {
            case "CentrifugalPump", "InlinePump", "MeteringPump", "ElectricMotor", "BlowerFan" -> true;
            default -> false;
        };
    }

    private static boolean isValveOrDamper(String t) {
        return switch (t) {
            case "HandValve", "SolenoidValve", "ControlValve", "CheckValve", "Damper", "Inlet", "Outlet" -> true;
            default -> false;
        };
    }

    private static boolean isTankOrVessel(String t) {
        return switch (t) {
            case "CylindricalTank", "HorizontalTank", "HopperSilo", "OpenBasin", "TankAgitator" -> true;
            default -> false;
        };
    }

    private static boolean isMaterialHandling(String t) {
        return switch (t) {
            case "ConveyorBelt", "ScrewAuger", "PneumaticCylinder", "HydraulicPress", "HeaterCoil" -> true;
            default -> false;
        };
    }
}

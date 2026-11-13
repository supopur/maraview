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

        default boolean paint(Graphics2D g, ScadaSymbolInstance sym, int w, int h, int gPx,
                              ScadaProject project, RuntimeTagProvider tags, EquipmentState state,
                              long timeMs, SymbolLayerCache layerCache) {
            return paint(g, sym, w, h, gPx, project, tags, state, timeMs);
        }
    }

    private static final Color LABEL_COLOR = new Color(0x14, 0x17, 0x1A);
    private Font cachedLabelFont;
    private int cachedLabelFontSize = -1;

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
        String fluidRaw = sym.getProperties().get("FluidType");
        if (fluidRaw == null || fluidRaw.isBlank()) {
            fluidRaw = sym.getProperties().get("fluidType");
        }
        FluidType fluid = parseFluid(fluidRaw);

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
                    readBoundValue(sym, project, tagProvider, "TempTag",
                            readBoundValue(sym, project, tagProvider, "SetpointTag", 21.5)));
            if (ElectricalSymbolRenderer.paint(lg, sym, localW, localH, state, phase)) {
                // Rendered by ElectricalSymbolRenderer
            } else if (HvacHeatingRenderer.paint(lg, sym, localW, localH, state, phase, speedPct, posPct, numVal, layerCache)) {
                // Rendered by HvacHeatingRenderer
            } else if (WastewaterRenderer.paint(lg, sym, localW, localH, state, phase)) {
                // Rendered by WastewaterRenderer
            } else if (ActuatorRenderer.paint(lg, sym, localW, localH, gPx, project, tagProvider, state, animationTimeMs)) {
                // Rendered by ActuatorRenderer
            } else if (ProcessFittingRenderer.paint(lg, sym, localW, localH, gPx, state, fluid, animationTimeMs, layerCache)) {
                // Rendered by ProcessFittingRenderer
            } else if (MarSensorRenderer.paint(lg, sym, localW, localH, state, numVal)) {
                // Rendered by MarSensorRenderer
            } else if (isRotatingMachinery(type)) {
                RotatingMachineryRenderer.paint(lg, type, localW, localH, gPx, state, speedPct / 100.0, animationTimeMs, layerCache);
            } else if (isValveOrDamper(type)) {
                String failSafe = sym.getProperty("FailSafe", "");
                ValveDamperRenderer.paint(lg, type, localW, localH, gPx, open, posPct, failSafe, layerCache);
            } else if (isTankOrVessel(type)) {
                TankVesselRenderer.TankMarkers markers = resolveTankMarkers(sym, project, tagProvider, minLevel, levelSpan);
                TankVesselRenderer.paint(lg, type, localW, localH, gPx, levelPct, fluid, state, animationTimeMs, layerCache, markers);
            } else if (isMaterialHandling(type)) {
                MaterialHandlingRenderer.paint(lg, type, localW, localH, gPx, state, posPct, animationTimeMs);
            } else if (extensionRenderer == null || !extensionRenderer.paint(lg, sym, localW, localH, gPx, project, tagProvider, state, animationTimeMs, layerCache)) {
                ScadaShader.paintBeveledHousing(lg, 2, 2, localW - 4, localH - 4, 4, Material.CAST_IRON);
            }
        } finally {
            lg.dispose();
        }

        // State overlay (standby / warning / 2 Hz fault halo) in world space
        ScadaShader.paintStateOverlay(g, x, y, effW, effH, state, animationTimeMs);

        // Optional Local/Remote, Hand/Off/Auto, and Standby mode badges
        paintControlModeBadges(g, sym, project, tagProvider, state, x, y, effW, gPx);

        // Crisp equipment tag label below symbol
        if (!sym.getName().isBlank()) {
            int fontSize = Math.max(9, gPx);
            if (cachedLabelFont == null || cachedLabelFontSize != fontSize) {
                cachedLabelFont = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
                cachedLabelFontSize = fontSize;
            }
            g.setFont(cachedLabelFont);
            Rectangle2D bounds = g.getFontMetrics().getStringBounds(sym.getName(), g);
            float tx = (float) (x + (effW - bounds.getWidth()) / 2.0);
            float ty = (float) (y + effH + bounds.getHeight() + 1);
            g.setColor(LABEL_COLOR);
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
        if (readBoundValue(sym, project, tags, "StandbyTag", 0.0) != 0.0) {
            return EquipmentState.STANDBY;
        }
        String propState = sym.getProperty("State", "");
        if ("STANDBY".equalsIgnoreCase(propState)) {
            return EquipmentState.STANDBY;
        }
        return EquipmentState.STOPPED;
    }

    private static void paintControlModeBadges(Graphics2D g, ScadaSymbolInstance sym,
                                               ScadaProject project, RuntimeTagProvider tags,
                                               EquipmentState state, int x, int y, int effW, int gPx) {
        String lr = resolveLocalRemote(sym, project, tags);
        String hoa = resolveHoa(sym, project, tags);
        boolean stby = state == EquipmentState.STANDBY;
        if (lr == null && hoa == null && !stby) {
            return;
        }
        Font oldFont = g.getFont();
        Color oldColor = g.getColor();
        int badgeH = Math.max(11, gPx + 2);
        int fontSize = Math.max(8, gPx - 1);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));

        int bx = x + effW;
        int by = y - badgeH - 1;
        if (hoa != null) {
            Color bg = switch (hoa) {
                case "H" -> new Color(0xE6, 0x51, 0x00);
                case "O" -> new Color(0x54, 0x6E, 0x7A);
                default -> new Color(0x1E, 0x84, 0x49);
            };
            bx -= drawMiniBadge(g, hoa, bx, by, badgeH, bg) + 2;
        }
        if (lr != null) {
            Color bg = "L".equals(lr) ? new Color(0x8E, 0x24, 0xAA) : new Color(0x02, 0x77, 0xBD);
            bx -= drawMiniBadge(g, lr, bx, by, badgeH, bg) + 2;
        }
        if (stby) {
            int stbyW = Math.max(badgeH, g.getFontMetrics().stringWidth("STBY") + 6);
            drawMiniBadge(g, "STBY", x + stbyW, by, badgeH, cz.maramat.scada.render.ScadaPalette.STATE_STANDBY);
        }
        g.setFont(oldFont);
        g.setColor(oldColor);
    }

    private static int drawMiniBadge(Graphics2D g, String text, int rightX, int y, int h, Color bg) {
        int tw = g.getFontMetrics().stringWidth(text);
        int w = Math.max(h, tw + 6);
        int x = rightX - w;
        g.setColor(bg);
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(Color.WHITE);
        int ty = y + ((h + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
        g.drawString(text, x + (w - tw) / 2, ty);
        return w;
    }

    private static String resolveLocalRemote(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags) {
        String tag = sym.getTagBindings().get("LocalRemoteTag");
        if (tag != null && !tag.isBlank() && project != null && project.getTag(tag) != null && tags != null) {
            return tags.readTag(project, tag) > 0.0 ? "R" : "L";
        }
        String prop = sym.getProperty("ControlMode", "");
        if ("LOCAL".equalsIgnoreCase(prop) || "L".equalsIgnoreCase(prop)) return "L";
        if ("REMOTE".equalsIgnoreCase(prop) || "R".equalsIgnoreCase(prop)) return "R";
        return null;
    }

    private static String resolveHoa(ScadaSymbolInstance sym, ScadaProject project, RuntimeTagProvider tags) {
        String tag = sym.getTagBindings().get("HoaTag");
        if (tag != null && !tag.isBlank() && project != null && project.getTag(tag) != null && tags != null) {
            int val = (int) Math.round(tags.readTag(project, tag));
            return switch (val) {
                case 1 -> "H";
                case 2 -> "A";
                default -> "O";
            };
        }
        String prop = sym.getProperty("HoaMode", "");
        if ("HAND".equalsIgnoreCase(prop) || "H".equalsIgnoreCase(prop)) return "H";
        if ("OFF".equalsIgnoreCase(prop) || "O".equalsIgnoreCase(prop)) return "O";
        if ("AUTO".equalsIgnoreCase(prop) || "A".equalsIgnoreCase(prop)) return "A";
        return null;
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
        String openTag = sym.getTagBindings().get("OpenTag");
        if (openTag == null || openTag.isBlank()) {
            openTag = sym.getTagBindings().get("RunTag");
        }
        if (openTag != null && !openTag.isBlank() && project != null && project.getTag(openTag) != null && tags != null) {
            return tags.readTag(project, openTag) > 0.0;
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
            case "CentrifugalPump", "InlinePump", "MeteringPump",
                 "SubmersiblePump", "PositiveDisplacementPump", "GearPump", "DiaphragmPump",
                 "PeristalticPump", "ScrewPump", "VacuumPump", "BoosterPump",
                 "ElectricMotor", "BlowerFan" -> true;
            default -> false;
        };
    }

    private static boolean isValveOrDamper(String t) {
        return switch (t) {
            case "HandValve", "SolenoidValve", "ControlValve", "CheckValve", "Damper", "Inlet", "Outlet",
                 "GateValve", "GlobeValve", "BallValve", "ButterflyValve", "NeedleValve", "PlugValve",
                 "DiaphragmValve", "FourWayValve", "MotorizedValve", "PneumaticValve" -> true;
            default -> false;
        };
    }

    private static TankVesselRenderer.TankMarkers resolveTankMarkers(ScadaSymbolInstance sym, ScadaProject project,
                                                                     RuntimeTagProvider tags, double minLevel, double levelSpan) {
        Double spPct = null;
        String spTag = sym.getTagBindings().get("SetpointTag");
        if (spTag != null && !spTag.isBlank() && project != null && project.getTag(spTag) != null && tags != null) {
            double rawSp = tags.readTag(project, spTag);
            spPct = (levelSpan > 0.0) ? ((rawSp - minLevel) / levelSpan) * 100.0 : rawSp;
        } else {
            spPct = readOptionalDoubleProp(sym, "Setpoint", minLevel, levelSpan);
        }
        Double hh = readOptionalDoubleProp(sym, "AlarmHH", minLevel, levelSpan);
        Double h = readOptionalDoubleProp(sym, "AlarmH", minLevel, levelSpan);
        Double l = readOptionalDoubleProp(sym, "AlarmL", minLevel, levelSpan);
        Double ll = readOptionalDoubleProp(sym, "AlarmLL", minLevel, levelSpan);
        return new TankVesselRenderer.TankMarkers(spPct, hh, h, l, ll);
    }

    private static Double readOptionalDoubleProp(ScadaSymbolInstance sym, String key, double minLevel, double levelSpan) {
        String raw = sym.getProperties().get(key);
        if (raw == null || raw.isBlank()) return null;
        try {
            double v = Double.parseDouble(raw.trim());
            return (levelSpan > 0.0) ? ((v - minLevel) / levelSpan) * 100.0 : v;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isTankOrVessel(String t) {
        return switch (t) {
            case "CylindricalTank", "HorizontalTank", "HopperSilo", "OpenBasin", "TankAgitator",
                 "ConeBottomTank", "SphericalTank", "ClosedTank", "Silo", "Hopper", "Reactor" -> true;
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

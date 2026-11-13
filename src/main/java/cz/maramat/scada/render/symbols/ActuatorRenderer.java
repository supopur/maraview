package cz.maramat.scada.render.symbols;

import cz.maramat.scada.model.ScadaProject;
import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;
import cz.maramat.scada.tags.RuntimeTagProvider;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

/**
 * 3D procedural renderers for PneumaticActuator, ElectricActuator, and HydraulicActuator
 * with open/closed/travelling/fault states and ZSO/ZSC limit switch indicators.
 */
public final class ActuatorRenderer {

    public enum ActuatorState {
        OPEN,
        CLOSED,
        TRAVELLING,
        FAULT
    }

    private ActuatorRenderer() {
    }

    public static boolean paint(Graphics2D g, ScadaSymbolInstance sym, int w, int h, int gPx,
                                ScadaProject project, RuntimeTagProvider tags, EquipmentState eqState, long timeMs) {
        String type = sym.getSymbolType();
        if (!isActuator(type)) {
            return false;
        }

        ActuatorState actState = resolveActuatorState(sym, project, tags, eqState);
        boolean openLimit = resolveLimitSwitch(sym, project, tags, "OpenLimitTag", "OpenLimit", actState == ActuatorState.OPEN);
        boolean closedLimit = resolveLimitSwitch(sym, project, tags, "ClosedLimitTag", "ClosedLimit", actState == ActuatorState.CLOSED);

        switch (type) {
            case "PneumaticActuator" -> paintPneumatic(g, w, h, actState);
            case "ElectricActuator" -> paintElectric(g, w, h, actState, timeMs);
            case "HydraulicActuator" -> paintHydraulic(g, w, h, actState);
            default -> {
                return false;
            }
        }

        paintStatusAndLimitSwitches(g, w, h, gPx, actState, openLimit, closedLimit, timeMs);
        return true;
    }

    public static boolean isActuator(String type) {
        return "PneumaticActuator".equals(type)
                || "ElectricActuator".equals(type)
                || "HydraulicActuator".equals(type);
    }

    private static void paintPneumatic(Graphics2D g, int w, int h, ActuatorState state) {
        // Diaphragm dome + spring-return stem
        ScadaShader.paintDishedTankHead(g, w * 0.14, h * 0.10, w * 0.72, h * 0.34, true, Material.CAST_IRON);
        ScadaShader.paintBeveledHousing(g, w * 0.14, h * 0.42, w * 0.72, h * 0.36, 3.0, Material.STAINLESS_STEEL);
        ScadaShader.paintCylinderV(g, w * 0.44, h * 0.78, w * 0.12, h * 0.20, Material.FLANGE_STEEL);
        drawCenterTag(g, "PNEU", w * 0.50, h * 0.62, state);
    }

    private static void paintElectric(Graphics2D g, int w, int h, ActuatorState state, long timeMs) {
        // Motor housing + gearbox
        ScadaShader.paintBeveledHousing(g, w * 0.12, h * 0.14, w * 0.76, h * 0.64, 5.0, Material.CAST_IRON);
        ScadaShader.paintCylinderV(g, w * 0.44, h * 0.78, w * 0.12, h * 0.20, Material.FLANGE_STEEL);
        // Animated travel spinner when travelling
        if (state == ActuatorState.TRAVELLING) {
            double ang = (timeMs / 1000.0) * 5.0;
            ScadaShader.paintRotorImpeller(g, w * 0.50, h * 0.36, Math.min(w, h) * 0.15, 4, ang);
        } else {
            drawCenterTag(g, "ELEC", w * 0.50, h * 0.42, state);
        }
    }

    private static void paintHydraulic(Graphics2D g, int w, int h, ActuatorState state) {
        // Heavy hydraulic cylinder body + piston rod
        ScadaShader.paintCylinderH(g, w * 0.10, h * 0.20, w * 0.80, h * 0.54, Material.FLANGE_STEEL);
        double rodExtend = (state == ActuatorState.OPEN) ? w * 0.10 : 0.0;
        ScadaShader.paintCylinderH(g, w * 0.38 + rodExtend, h * 0.40, w * 0.48, h * 0.14, Material.STAINLESS_STEEL);
        drawCenterTag(g, "HYD", w * 0.44, h * 0.50, state);
    }

    private static void paintStatusAndLimitSwitches(Graphics2D g, int w, int h, int gPx,
                                                    ActuatorState state, boolean zso, boolean zsc, long timeMs) {
        double ledR = Math.max(2.5, gPx * 0.26);
        // ZSO (Open limit switch) - top left
        ScadaShader.paintLedIndicator(g, w * 0.22, h * 0.22, ledR,
                zso ? ScadaPalette.STATE_RUNNING_LED : ScadaPalette.STATE_STOPPED_LED, zso);
        // ZSC (Closed limit switch) - top right
        ScadaShader.paintLedIndicator(g, w * 0.78, h * 0.22, ledR,
                zsc ? new Color(0xEF, 0x6C, 0x00) : ScadaPalette.STATE_STOPPED_LED, zsc);

        // State bar at bottom of housing
        Color barColor = switch (state) {
            case OPEN -> ScadaPalette.STATE_RUNNING_LED;
            case CLOSED -> new Color(0x54, 0x6E, 0x7A);
            case TRAVELLING -> ((timeMs / 250L) & 1L) == 0L ? ScadaPalette.STATE_STANDBY : ScadaPalette.STATE_WARNING;
            case FAULT -> ScadaPalette.STATE_FAULT;
        };
        g.setColor(barColor);
        g.fill(new Rectangle2D.Double(w * 0.20, h * 0.68, w * 0.60, Math.max(3.0, h * 0.08)));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.0f));
        g.draw(new Rectangle2D.Double(w * 0.20, h * 0.68, w * 0.60, Math.max(3.0, h * 0.08)));

        if (state == ActuatorState.TRAVELLING) {
            // Travelling chevron
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.4f));
            double midY = h * 0.72;
            g.draw(new Line2D.Double(w * 0.44, midY - 2, w * 0.50, midY));
            g.draw(new Line2D.Double(w * 0.44, midY + 2, w * 0.50, midY));
        }
    }

    private static void drawCenterTag(Graphics2D g, String text, double cx, double y, ActuatorState state) {
        g.setColor(state == ActuatorState.FAULT ? ScadaPalette.STATE_FAULT : Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) y);
    }

    public static ActuatorState resolveActuatorState(ScadaSymbolInstance sym, ScadaProject project,
                                                     RuntimeTagProvider tags, EquipmentState eqState) {
        if (eqState == EquipmentState.FAULT) {
            return ActuatorState.FAULT;
        }
        if (EquipmentSymbolRenderer.readBoundValue(sym, project, tags, "TravellingTag", 0.0) != 0.0) {
            return ActuatorState.TRAVELLING;
        }
        String propState = sym.getProperty("ActuatorState", sym.getProperty("State", ""));
        if ("TRAVELLING".equalsIgnoreCase(propState)) return ActuatorState.TRAVELLING;
        if ("OPEN".equalsIgnoreCase(propState)) return ActuatorState.OPEN;
        if ("CLOSED".equalsIgnoreCase(propState)) return ActuatorState.CLOSED;
        if ("FAULT".equalsIgnoreCase(propState)) return ActuatorState.FAULT;

        double openVal = EquipmentSymbolRenderer.readBoundValue(sym, project, tags, "OpenTag",
                EquipmentSymbolRenderer.readBoundValue(sym, project, tags, "RunTag", -1.0));
        if (openVal >= 0.0) {
            return openVal > 0.0 ? ActuatorState.OPEN : ActuatorState.CLOSED;
        }
        return Boolean.parseBoolean(sym.getProperty("Open", "true")) ? ActuatorState.OPEN : ActuatorState.CLOSED;
    }

    private static boolean resolveLimitSwitch(ScadaSymbolInstance sym, ScadaProject project,
                                              RuntimeTagProvider tags, String tagRole, String propKey, boolean fallback) {
        String tag = sym.getTagBindings().get(tagRole);
        if (tag != null && !tag.isBlank() && project != null && project.getTag(tag) != null && tags != null) {
            return tags.readTag(project, tag) > 0.0;
        }
        String prop = sym.getProperty(propKey, "");
        if (!prop.isBlank()) {
            return Boolean.parseBoolean(prop);
        }
        return fallback;
    }
}

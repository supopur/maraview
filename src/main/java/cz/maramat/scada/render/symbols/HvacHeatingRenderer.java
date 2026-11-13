package cz.maramat.scada.render.symbols;

import cz.maramat.scada.model.ScadaSymbolInstance;
import cz.maramat.scada.render.EquipmentState;
import cz.maramat.scada.render.ScadaPalette;
import cz.maramat.scada.render.ScadaPalette.Material;
import cz.maramat.scada.render.ScadaShader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Dynamic vector renderer for Heating, Boilers, Heat Pumps, Manifolds, Radiators, and HVAC units.
 */
public final class HvacHeatingRenderer {

    private static final Color HOT_RED = new Color(0xE5, 0x39, 0x35);
    private static final Color COOL_BLUE = new Color(0x1E, 0x88, 0xE5);
    private static final Color FLAME_ORANGE = new Color(0xFF, 0x98, 0x00);

    private HvacHeatingRenderer() {
    }

    public static boolean paint(Graphics2D g, ScadaSymbolInstance sym,
                                double w, double h, EquipmentState state, double phase) {
        return paint(g, sym, w, h, state, phase, state == EquipmentState.RUNNING ? 100.0 : 0.0, 50.0, 21.5);
    }

    public static boolean paint(Graphics2D g, ScadaSymbolInstance sym,
                                double w, double h, EquipmentState state, double phase,
                                double speedPct, double posPct, double numVal) {
        return switch (sym.getSymbolType()) {
            case "Boiler" -> { paintBoiler(g, sym, w, h, state, phase, "BOILER"); yield true; }
            case "GasBoiler" -> { paintBoiler(g, sym, w, h, state, phase, "GAS"); yield true; }
            case "ElectricBoiler" -> { paintElectricBoiler(g, w, h, state); yield true; }
            case "BiomassBoiler" -> { paintBiomassBoiler(g, w, h, state, phase); yield true; }
            case "Burner" -> { paintBurner(g, w, h, state, phase); yield true; }
            case "Chiller" -> { paintChiller(g, w, h, state, phase); yield true; }
            case "HeatPump" -> { paintHeatPump(g, sym, w, h, state, phase, "HP"); yield true; }
            case "AirSourceHeatPump" -> { paintHeatPump(g, sym, w, h, state, phase, "ASHP"); yield true; }
            case "GroundSourceHeatPump" -> { paintHeatPump(g, sym, w, h, state, phase, "GSHP"); yield true; }
            case "WaterSourceHeatPump" -> { paintHeatPump(g, sym, w, h, state, phase, "WSHP"); yield true; }
            case "PlateHeatExchanger" -> { paintPlateHeatExchanger(g, w, h, state); yield true; }
            case "ShellAndTubeHeatExchanger" -> { paintShellAndTube(g, w, h, state); yield true; }
            case "CoilHeatExchanger" -> { paintCoilHeatExchanger(g, w, h, state); yield true; }
            case "CoolingTower" -> { paintCoolingTower(g, w, h, state, phase); yield true; }
            case "DryCooler" -> { paintDryCooler(g, w, h, state, phase); yield true; }
            case "FanCoilUnit" -> { paintFanCoilUnit(g, w, h, state, phase); yield true; }
            case "BufferTank" -> { paintBufferTank(g, w, h, state); yield true; }
            case "ThermalStorageTank" -> { paintBufferTank(g, w, h, state); yield true; }
            case "ExpansionVessel" -> { paintExpansionVessel(g, w, h); yield true; }
            case "Radiator" -> { paintRadiator(g, sym, w, h, state, numVal); yield true; }
            case "FloorHeatingManifold" -> { paintFloorHeatingManifold(g, w, h, state); yield true; }
            case "UnderfloorHeatingZone" -> { paintUnderfloorZone(g, sym, w, h, state, numVal); yield true; }
            case "ZoneValve" -> { paintTwoWayValve(g, w, h, state); yield true; }
            case "VavBox" -> { paintVavBox(g, w, h, state, posPct); yield true; }
            case "RoomSymbol" -> { paintRoomSymbol(g, sym, w, h, state, numVal); yield true; }
            case "SolarThermalCollector" -> { paintSolarCollector(g, w, h, state); yield true; }
            case "ThreeWayMixingValve" -> { paintThreeWayValve(g, w, h, state); yield true; }
            case "TwoWayControlValve" -> { paintTwoWayValve(g, w, h, state); yield true; }
            case "SafetyReliefValve" -> { paintSafetyValve(g, w, h, state); yield true; }
            case "AirDirtSeparator" -> { paintAirDirtSeparator(g, w, h); yield true; }
            case "CirculationPump" -> { paintCirculationPump(g, w, h, state, phase); yield true; }
            case "VentilationFan" -> { paintVentilationFan(g, w, h, state, phase); yield true; }
            case "AxialFan" -> { paintFanWithSpeed(g, w, h, state, phase, speedPct, true); yield true; }
            case "CentrifugalFan" -> { paintFanWithSpeed(g, w, h, state, phase, speedPct, false); yield true; }
            case "AhuUnit" -> { paintAhuUnit(g, sym, w, h, state, phase); yield true; }
            case "HeatingCoil" -> { paintCoil(g, w, h, true, state); yield true; }
            case "CoolingCoil" -> { paintCoil(g, w, h, false, state); yield true; }
            case "HeatRecovery" -> { paintHeatRecovery(g, w, h, state); yield true; }
            case "AirFilter" -> { paintAirFilter(g, w, h); yield true; }
            case "Humidifier" -> { paintHumidifier(g, w, h, state); yield true; }
            case "ScrewCompressor" -> { paintCompressor(g, w, h, state, phase, "SCR"); yield true; }
            case "ScrollCompressor" -> { paintCompressor(g, w, h, state, phase, "SCL"); yield true; }
            case "PistonCompressor" -> { paintCompressor(g, w, h, state, phase, "PST"); yield true; }
            case "CentrifugalCompressor" -> { paintCompressor(g, w, h, state, phase, "CNT"); yield true; }
            case "ExpansionValve" -> { paintExpansionValve(g, w, h, state); yield true; }
            case "RefrigerantReceiver" -> { paintRefrigerantReceiver(g, w, h, state); yield true; }
            default -> false;
        };
    }

    private static void paintBoiler(Graphics2D g, ScadaSymbolInstance sym, double w, double h,
                                    EquipmentState state, double phase, String badge) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 8, Material.STAINLESS_STEEL);

        // Combustion chamber window
        double wx = w * 0.18;
        double wy = h * 0.25;
        double ww = w * 0.64;
        double wh = h * 0.50;
        RoundRectangle2D.Double window = new RoundRectangle2D.Double(wx, wy, ww, wh, 6, 6);
        g.setColor(new Color(0x26, 0x32, 0x38));
        g.fill(window);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(window);

        if (state == EquipmentState.RUNNING) {
            double pulse = 1.0 + 0.15 * Math.sin(phase * 0.2);
            double cx = w / 2.0;
            double by = wy + wh - 4;
            Polygon flame = new Polygon(
                    new int[]{(int) (cx - ww * 0.22), (int) cx, (int) (cx + ww * 0.22)},
                    new int[]{(int) by, (int) (by - wh * 0.65 * pulse), (int) by},
                    3
            );
            g.setColor(FLAME_ORANGE);
            g.fill(flame);
            Polygon inner = new Polygon(
                    new int[]{(int) (cx - ww * 0.11), (int) cx, (int) (cx + ww * 0.11)},
                    new int[]{(int) by, (int) (by - wh * 0.38 * pulse), (int) by},
                    3
            );
            g.setColor(HOT_RED);
            g.fill(inner);
        }
        drawBadge(g, badge, w / 2.0, h * 0.17, ScadaPalette.TEXT_PRIMARY, 9);
    }

    private static void paintElectricBoiler(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 8, Material.STAINLESS_STEEL);
        // Immersion heating elements
        g.setColor(state == EquipmentState.RUNNING ? HOT_RED : new Color(0x54, 0x6E, 0x7A));
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            double ex = w * (0.30 + i * 0.20);
            g.draw(new Line2D.Double(ex, h * 0.32, ex, h * 0.78));
        }
        drawBadge(g, "EL", w / 2.0, h * 0.20, ScadaPalette.TEXT_PRIMARY, 9);
    }

    private static void paintBiomassBoiler(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        // Side pellet hopper + main combustion chamber
        ScadaShader.paintBeveledHousing(g, 2, h * 0.28, w * 0.28, h * 0.68, 4, Material.FLANGE_STEEL);
        paintBoiler(g, null, w * 0.72, h, state, phase, "BIO");
        // Auger feed link
        ScadaShader.paintCylinderH(g, w * 0.26, h * 0.70, w * 0.16, h * 0.10, Material.CAST_IRON);
    }

    private static void paintBurner(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, h * 0.18, w * 0.54, h * 0.64, 4, Material.CAST_IRON);
        ScadaShader.paintCylinderH(g, w * 0.56, h * 0.32, w * 0.28, h * 0.36, Material.STAINLESS_STEEL);
        if (state == EquipmentState.RUNNING) {
            double pulse = 1.0 + 0.14 * Math.sin(phase * 0.25);
            Polygon flame = new Polygon(
                    new int[]{(int) (w * 0.84), (int) (w * (0.96 * pulse)), (int) (w * 0.84)},
                    new int[]{(int) (h * 0.34), (int) (h * 0.50), (int) (h * 0.66)},
                    3
            );
            g.setColor(FLAME_ORANGE);
            g.fill(flame);
        }
    }

    private static void paintChiller(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 6, Material.STAINLESS_STEEL);
        // Evaporator (blue) & Condenser (amber) shells
        ScadaShader.paintCylinderH(g, w * 0.10, h * 0.22, w * 0.80, h * 0.22, Material.FLANGE_STEEL);
        g.setColor(state == EquipmentState.RUNNING ? COOL_BLUE : new Color(0x54, 0x6E, 0x7A));
        g.fill(new Rectangle2D.Double(w * 0.14, h * 0.26, w * 0.72, h * 0.14));
        ScadaShader.paintCylinderH(g, w * 0.10, h * 0.56, w * 0.80, h * 0.22, Material.FLANGE_STEEL);
        g.setColor(state == EquipmentState.RUNNING ? FLAME_ORANGE : new Color(0x54, 0x6E, 0x7A));
        g.fill(new Rectangle2D.Double(w * 0.14, h * 0.60, w * 0.72, h * 0.14));
        drawBadge(g, "CHILLER", w * 0.50, h * 0.16, ScadaPalette.TEXT_PRIMARY, 8);
    }

    private static void paintHeatPump(Graphics2D g, ScadaSymbolInstance sym, double w, double h,
                                      EquipmentState state, double phase, String badge) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 7, Material.STAINLESS_STEEL);

        // Left fan / source section
        double fd = Math.min(w * 0.42, h * 0.58);
        double fcx = w * 0.28;
        double fcy = h * 0.48;
        Ellipse2D.Double fanRing = new Ellipse2D.Double(fcx - fd / 2.0, fcy - fd / 2.0, fd, fd);
        g.setColor(new Color(0x37, 0x47, 0x4F));
        g.fill(fanRing);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(fanRing);

        double angle = (state == EquipmentState.RUNNING) ? (phase * 0.25) : 0.0;
        g.setColor(new Color(0xCF, 0xD8, 0xDC));
        g.setStroke(new BasicStroke(2.0f));
        for (int i = 0; i < 3; i++) {
            double a = angle + i * (2.0 * Math.PI / 3.0);
            g.draw(new Line2D.Double(fcx, fcy, fcx + Math.cos(a) * fd * 0.40, fcy + Math.sin(a) * fd * 0.40));
        }

        // Right internal component state panel (Compressor, Evaporator, Condenser, Reversing Valve)
        g.setColor(new Color(0x26, 0x32, 0x38));
        g.fill(new RoundRectangle2D.Double(w * 0.54, h * 0.12, w * 0.40, h * 0.76, 4, 4));
        drawBadge(g, badge, w * 0.74, h * 0.25, Color.WHITE, 8);

        String revMode = (sym != null) ? sym.getProperty("ReversingValve", "HEATING") : "HEATING";
        boolean cooling = "COOLING".equalsIgnoreCase(revMode);
        boolean defrost = "DEFROST".equalsIgnoreCase(revMode);
        boolean running = state == EquipmentState.RUNNING;

        double ledX = w * 0.62;
        double ledR = 2.6;
        ScadaShader.paintLedIndicator(g, ledX, h * 0.36, ledR, running ? ScadaPalette.STATE_RUNNING_LED : ScadaPalette.STATE_STOPPED_LED, running);
        drawBadge(g, "CMP", w * 0.79, h * 0.39, Color.WHITE, 6);

        Color evapColor = running ? (cooling ? COOL_BLUE : new Color(0x4F, 0xC3, 0xF7)) : ScadaPalette.STATE_STOPPED_LED;
        ScadaShader.paintLedIndicator(g, ledX, h * 0.50, ledR, evapColor, running);
        drawBadge(g, "EVP", w * 0.79, h * 0.53, Color.WHITE, 6);

        Color condColor = running ? (cooling ? FLAME_ORANGE : HOT_RED) : ScadaPalette.STATE_STOPPED_LED;
        ScadaShader.paintLedIndicator(g, ledX, h * 0.64, ledR, condColor, running);
        drawBadge(g, "CND", w * 0.79, h * 0.67, Color.WHITE, 6);

        Color revColor = defrost ? ScadaPalette.STATE_WARNING : (cooling ? COOL_BLUE : HOT_RED);
        ScadaShader.paintLedIndicator(g, ledX, h * 0.78, ledR, revColor, running || defrost);
        drawBadge(g, defrost ? "DEF" : (cooling ? "CLG" : "HTG"), w * 0.79, h * 0.81, Color.WHITE, 6);
    }

    private static void paintShellAndTube(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintCylinderH(g, 4, h * 0.16, w - 8, h * 0.68, Material.STAINLESS_STEEL);
        g.setColor(state == EquipmentState.RUNNING ? HOT_RED : new Color(0x45, 0x5A, 0x64));
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 4; i++) {
            double ty = h * (0.28 + i * 0.14);
            g.draw(new Line2D.Double(w * 0.14, ty, w * 0.86, ty));
        }
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(new Line2D.Double(w * 0.35, h * 0.18, w * 0.35, h * 0.65));
        g.draw(new Line2D.Double(w * 0.65, h * 0.35, w * 0.65, h * 0.82));
    }

    private static void paintCoilHeatExchanger(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 6, Material.STAINLESS_STEEL);
        g.setColor(state == EquipmentState.RUNNING ? HOT_RED : new Color(0x54, 0x6E, 0x7A));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int loops = 4;
        double step = (h * 0.64) / loops;
        for (int i = 0; i < loops; i++) {
            double y0 = h * 0.18 + i * step;
            g.draw(new Line2D.Double(w * 0.20, y0, w * 0.80, y0 + step * 0.5));
            g.draw(new Line2D.Double(w * 0.80, y0 + step * 0.5, w * 0.20, y0 + step));
        }
    }

    private static void paintCoolingTower(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        Polygon body = new Polygon(
                new int[]{(int) (w * 0.20), (int) (w * 0.80), (int) (w * 0.92), (int) (w * 0.08)},
                new int[]{(int) (h * 0.18), (int) (h * 0.18), (int) (h * 0.82), (int) (h * 0.82)},
                4
        );
        g.setColor(new Color(0x90, 0xA4, 0xAE));
        g.fill(body);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(body);
        ScadaShader.paintBeveledHousing(g, 2, h * 0.80, w - 4, h * 0.18, 2, Material.FLANGE_STEEL);
        g.setColor(COOL_BLUE);
        g.fill(new Rectangle2D.Double(6, h * 0.84, w - 12, h * 0.10));
        ScadaShader.paintBeveledHousing(g, w * 0.26, 2, w * 0.48, h * 0.16, 2, Material.CAST_IRON);
        double ang = (state == EquipmentState.RUNNING) ? phase * 0.3 : 0.0;
        ScadaShader.paintRotorImpeller(g, w * 0.50, h * 0.10, Math.min(w, h) * 0.08, 4, ang);
    }

    private static void paintDryCooler(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, h * 0.22, w - 4, h * 0.64, 4, Material.STAINLESS_STEEL);
        double r = Math.min(w * 0.18, h * 0.22);
        double ang = (state == EquipmentState.RUNNING) ? phase * 0.3 : 0.0;
        ScadaShader.paintVoluteCasing(g, w * 0.30, h * 0.54, r, r * 0.72, Material.CAST_IRON);
        ScadaShader.paintRotorImpeller(g, w * 0.30, h * 0.54, r * 0.65, 5, ang);
        ScadaShader.paintVoluteCasing(g, w * 0.70, h * 0.54, r, r * 0.72, Material.CAST_IRON);
        ScadaShader.paintRotorImpeller(g, w * 0.70, h * 0.54, r * 0.65, 5, ang);
    }

    private static void paintFanCoilUnit(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        g.setColor(state == EquipmentState.RUNNING ? HOT_RED : new Color(0x60, 0x7D, 0x8B));
        g.setStroke(new BasicStroke(2.0f));
        g.draw(new Line2D.Double(w * 0.16, h * 0.20, w * 0.38, h * 0.80));
        g.draw(new Line2D.Double(w * 0.38, h * 0.20, w * 0.16, h * 0.80));
        double r = Math.min(w, h) * 0.28;
        double ang = (state == EquipmentState.RUNNING) ? phase * 0.3 : 0.0;
        ScadaShader.paintVoluteCasing(g, w * 0.68, h * 0.50, r, r * 0.70, Material.CAST_IRON);
        ScadaShader.paintRotorImpeller(g, w * 0.68, h * 0.50, r * 0.65, 5, ang);
    }

    private static void paintPlateHeatExchanger(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 4, 3, w - 8, h - 6, 4, Material.STAINLESS_STEEL);
        int plates = Math.max(4, (int) (w / 10));
        double step = (w - 14) / plates;
        g.setStroke(new BasicStroke(1.3f));
        for (int i = 0; i <= plates; i++) {
            double px = 7 + i * step;
            g.setColor((i % 2 == 0) ? HOT_RED : COOL_BLUE);
            g.draw(new Line2D.Double(px, 7, px, h - 7));
        }
        if (state == EquipmentState.RUNNING) {
            g.setColor(new Color(0x2E, 0x7D, 0x32));
            g.fill(new Ellipse2D.Double(w - 11, 5, 5, 5));
        }
    }

    private static void paintBufferTank(Graphics2D g, double w, double h, EquipmentState state) {
        RoundRectangle2D.Double body = new RoundRectangle2D.Double(3, 3, w - 6, h - 6, w * 0.35, w * 0.35);
        g.setPaint(new GradientPaint(0f, 3f, new Color(0xE5, 0x73, 0x73), 0f, (float) (h - 3), new Color(0x64, 0xB5, 0xF6)));
        g.fill(body);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(body);
        // Insulation seam
        g.setColor(new Color(255, 255, 255, 110));
        g.draw(new Line2D.Double(6, h * 0.5, w - 6, h * 0.5));
        drawBadge(g, "BUFFER", w / 2.0, h * 0.54, Color.WHITE, 9);
    }

    private static void paintExpansionVessel(Graphics2D g, double w, double h) {
        RoundRectangle2D.Double body = new RoundRectangle2D.Double(3, 3, w - 6, h - 6, w * 0.45, w * 0.45);
        g.setPaint(new GradientPaint(0f, 0f, new Color(0xE5, 0x39, 0x35), (float) w, 0f, new Color(0xB7, 0x1C, 0x1C)));
        g.fill(body);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.4f));
        g.draw(body);
        // Membrane line
        g.setColor(new Color(255, 255, 255, 170));
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f));
        g.draw(new Line2D.Double(6, h * 0.5, w - 6, h * 0.5));
    }

    private static void paintRadiator(Graphics2D g, ScadaSymbolInstance sym, double w, double h,
                                      EquipmentState state, double tempC) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        int fins = Math.max(4, (int) (w / 9));
        double step = (w - 10) / fins;
        g.setColor(state == EquipmentState.RUNNING ? new Color(0xEF, 0x53, 0x50) : new Color(0x78, 0x90, 0x9C));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < fins; i++) {
            double fx = 6 + i * step + step * 0.3;
            g.draw(new Line2D.Double(fx, 6, fx, h - 6));
        }
        // Temperature display pill
        g.setColor(new Color(0x14, 0x17, 0x1A, 200));
        g.fill(new RoundRectangle2D.Double(w * 0.24, h * 0.28, w * 0.52, h * 0.44, 4, 4));
        drawBadge(g, String.format("%.1f°C", tempC), w * 0.50, h * 0.58, Color.WHITE, 8);
    }

    private static void paintFloorHeatingManifold(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        // Supply bar (top, red) & Return bar (bottom, blue)
        g.setColor(HOT_RED);
        g.fill(new RoundRectangle2D.Double(6, h * 0.20, w - 12, Math.max(5, h * 0.16), 3, 3));
        g.setColor(COOL_BLUE);
        g.fill(new RoundRectangle2D.Double(6, h * 0.64, w - 12, Math.max(5, h * 0.16), 3, 3));

        int loops = Math.max(3, (int) (w / 16));
        double step = (w - 18) / loops;
        g.setColor(state == EquipmentState.RUNNING ? FLAME_ORANGE : new Color(0x54, 0x6E, 0x7A));
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < loops; i++) {
            double lx = 10 + i * step + step * 0.3;
            g.draw(new Line2D.Double(lx, h * 0.36, lx, h * 0.64));
            g.fill(new Rectangle2D.Double(lx - 3, h * 0.08, 6, h * 0.12));
        }
    }

    private static void paintThreeWayValve(Graphics2D g, double w, double h, EquipmentState state) {
        double cx = w / 2.0;
        double cy = h * 0.50;
        double s = Math.min(w, h) * 0.26;
        g.setColor(state == EquipmentState.RUNNING ? new Color(0x43, 0xA0, 0x47) : new Color(0xB8, 0x86, 0x0B));
        Polygon left = new Polygon(new int[]{3, (int) cx, 3}, new int[]{(int) (cy - s), (int) cy, (int) (cy + s)}, 3);
        Polygon right = new Polygon(new int[]{(int) (w - 3), (int) cx, (int) (w - 3)}, new int[]{(int) (cy - s), (int) cy, (int) (cy + s)}, 3);
        Polygon bot = new Polygon(new int[]{(int) (cx - s), (int) cx, (int) (cx + s)}, new int[]{(int) (h - 3), (int) cy, (int) (h - 3)}, 3);
        g.fill(left);
        g.fill(right);
        g.fill(bot);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(left);
        g.draw(right);
        g.draw(bot);
        // Actuator stem & dome
        g.draw(new Line2D.Double(cx, cy, cx, h * 0.22));
        g.setColor(new Color(0x37, 0x47, 0x4F));
        g.fill(new RoundRectangle2D.Double(cx - s * 0.8, 2, s * 1.6, h * 0.20, 4, 4));
    }

    private static void paintTwoWayValve(Graphics2D g, double w, double h, EquipmentState state) {
        double cx = w / 2.0;
        double cy = h * 0.60;
        double s = Math.min(w, h) * 0.26;
        g.setColor(state == EquipmentState.RUNNING ? new Color(0x43, 0xA0, 0x47) : new Color(0xB8, 0x86, 0x0B));
        Polygon left = new Polygon(new int[]{3, (int) cx, 3}, new int[]{(int) (cy - s), (int) cy, (int) (cy + s)}, 3);
        Polygon right = new Polygon(new int[]{(int) (w - 3), (int) cx, (int) (w - 3)}, new int[]{(int) (cy - s), (int) cy, (int) (cy + s)}, 3);
        g.fill(left);
        g.fill(right);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(left);
        g.draw(right);
        g.draw(new Line2D.Double(cx, cy, cx, h * 0.25));
        g.setColor(new Color(0x37, 0x47, 0x4F));
        g.fill(new RoundRectangle2D.Double(cx - s * 0.8, 2, s * 1.6, h * 0.24, 4, 4));
    }

    private static void paintSafetyValve(Graphics2D g, double w, double h, EquipmentState state) {
        double cx = w * 0.5;
        double cy = h * 0.45;
        double s = Math.min(w, h) * 0.24;
        g.setColor(state == EquipmentState.FAULT ? ScadaPalette.STATE_FAULT : HOT_RED);
        Polygon p = new Polygon(new int[]{(int) (cx - s), (int) cx, (int) (cx + s)},
                new int[]{(int) (h - 3), (int) cy, (int) (h - 3)}, 3);
        g.fill(p);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(p);
        // Spring top
        g.draw(new Line2D.Double(cx, cy, cx, 4));
        g.draw(new Line2D.Double(cx - 5, 4, cx + 5, 4));
    }

    private static void paintAirDirtSeparator(Graphics2D g, double w, double h) {
        ScadaShader.paintBeveledHousing(g, w * 0.15, 2, w * 0.70, h - 4, w * 0.35, Material.CAST_IRON);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(new Line2D.Double(w * 0.25, h * 0.4, w * 0.75, h * 0.4));
    }

    private static void paintCirculationPump(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        double d = Math.min(w, h) - 4;
        double cx = w / 2.0;
        double cy = h / 2.0;
        Ellipse2D.Double circle = new Ellipse2D.Double(cx - d / 2.0, cy - d / 2.0, d, d);
        g.setColor(new Color(0xC8, 0xD2, 0xDC));
        g.fill(circle);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(circle);
        // Flow triangle pointing EAST
        double r = d * 0.36;
        Polygon tri = new Polygon(
                new int[]{(int) (cx - r * 0.6), (int) (cx + r), (int) (cx - r * 0.6)},
                new int[]{(int) (cy - r * 0.75), (int) cy, (int) (cy + r * 0.75)},
                3
        );
        g.setColor(state == EquipmentState.FAULT ? ScadaPalette.STATE_FAULT
                : (state == EquipmentState.RUNNING ? ScadaPalette.STATE_RUNNING : new Color(0x45, 0x5A, 0x64)));
        g.fill(tri);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(tri);
    }

    private static void paintVentilationFan(Graphics2D g, double w, double h, EquipmentState state, double phase) {
        double d = Math.min(w, h) - 4;
        double cx = w / 2.0;
        double cy = h / 2.0;
        Ellipse2D.Double ring = new Ellipse2D.Double(cx - d / 2.0, cy - d / 2.0, d, d);
        g.setColor(new Color(0xC8, 0xD2, 0xDC));
        g.fill(ring);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(ring);
        double angle = (state == EquipmentState.RUNNING) ? phase * 0.3 : 0.0;
        g.setColor(new Color(0x37, 0x47, 0x4F));
        g.setStroke(new BasicStroke(2.2f));
        for (int i = 0; i < 4; i++) {
            double a = angle + i * (Math.PI / 2.0);
            g.draw(new Line2D.Double(cx, cy, cx + Math.cos(a) * d * 0.38, cy + Math.sin(a) * d * 0.38));
        }
    }

    private static void paintAhuUnit(Graphics2D g, ScadaSymbolInstance sym, double w, double h,
                                     EquipmentState state, double phase) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 6, Material.STAINLESS_STEEL);
        // Divider between supply (top) and extract (bottom)
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Line2D.Double(4, h * 0.5, w - 4, h * 0.5));

        // 1. Dampers (intake/exhaust louvres at left)
        g.setColor(new Color(0x45, 0x5A, 0x64));
        g.setStroke(new BasicStroke(1.8f));
        g.draw(new Line2D.Double(w * 0.08, h * 0.16, w * 0.14, h * 0.38));
        g.draw(new Line2D.Double(w * 0.08, h * 0.62, w * 0.14, h * 0.84));

        // 2. Filter banks (zig-zag)
        g.setColor(new Color(0x54, 0x6E, 0x7A));
        g.setStroke(new BasicStroke(1.2f));
        for (double y0 : new double[]{h * 0.16, h * 0.62}) {
            g.draw(new Line2D.Double(w * 0.22, y0, w * 0.27, y0 + h * 0.11));
            g.draw(new Line2D.Double(w * 0.27, y0 + h * 0.11, w * 0.22, y0 + h * 0.22));
        }

        // 3. Heat recovery wheel (rotating center wheel)
        double wd = Math.min(w * 0.20, h * 0.72);
        double wcx = w * 0.45;
        double wcy = h * 0.50;
        g.setColor(new Color(0x90, 0xA4, 0xAE));
        g.fill(new Ellipse2D.Double(wcx - wd / 2.0, wcy - wd / 2.0, wd, wd));
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(new Ellipse2D.Double(wcx - wd / 2.0, wcy - wd / 2.0, wd, wd));
        double wheelAng = (state == EquipmentState.RUNNING) ? phase * 0.15 : 0.0;
        for (int i = 0; i < 4; i++) {
            double a = wheelAng + i * (Math.PI / 2.0);
            g.draw(new Line2D.Double(wcx, wcy, wcx + Math.cos(a) * wd * 0.46, wcy + Math.sin(a) * wd * 0.46));
        }

        // 4. Heating & Cooling Coils
        g.setColor(state == EquipmentState.RUNNING ? HOT_RED : new Color(0x78, 0x90, 0x9C));
        g.fill(new Rectangle2D.Double(w * 0.62, h * 0.14, 4, h * 0.24));
        g.setColor(state == EquipmentState.RUNNING ? COOL_BLUE : new Color(0x78, 0x90, 0x9C));
        g.fill(new Rectangle2D.Double(w * 0.67, h * 0.14, 4, h * 0.24));

        // 5. Humidifier droplets
        g.setColor(state == EquipmentState.RUNNING ? COOL_BLUE : new Color(0x60, 0x7D, 0x8B));
        g.fill(new Ellipse2D.Double(w * 0.74, h * 0.22, 4, 5));
        g.fill(new Ellipse2D.Double(w * 0.77, h * 0.30, 4, 5));

        // 6. Supply & Extract Fans (right)
        double fd = Math.min(w * 0.12, h * 0.28);
        double fanAng = (state == EquipmentState.RUNNING) ? phase * 0.35 : 0.0;
        for (double fcy : new double[]{h * 0.28, h * 0.72}) {
            double fcx = w * 0.88;
            g.setColor(new Color(0x37, 0x47, 0x4F));
            g.fill(new Ellipse2D.Double(fcx - fd / 2.0, fcy - fd / 2.0, fd, fd));
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.3f));
            for (int i = 0; i < 3; i++) {
                double a = fanAng + i * (2.0 * Math.PI / 3.0);
                g.draw(new Line2D.Double(fcx, fcy, fcx + Math.cos(a) * fd * 0.42, fcy + Math.sin(a) * fd * 0.42));
            }
        }
        drawBadge(g, "AHU", w * 0.35, h * 0.20, ScadaPalette.TEXT_PRIMARY, 8);
    }

    private static void paintCoil(Graphics2D g, double w, double h, boolean heating, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        g.setColor(heating ? HOT_RED : COOL_BLUE);
        g.setStroke(new BasicStroke(2.0f));
        g.draw(new Line2D.Double(w * 0.22, h * 0.20, w * 0.78, h * 0.80));
        g.draw(new Line2D.Double(w * 0.78, h * 0.20, w * 0.22, h * 0.80));
        drawBadge(g, heating ? "+" : "-", w / 2.0, h * 0.22, heating ? HOT_RED : COOL_BLUE, 11);
    }

    private static void paintHeatRecovery(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        g.setColor(HOT_RED);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Line2D.Double(6, h * 0.25, w - 6, h * 0.75));
        g.setColor(COOL_BLUE);
        g.draw(new Line2D.Double(6, h * 0.75, w - 6, h * 0.25));
    }

    private static void paintAirFilter(Graphics2D g, double w, double h) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 3, Material.STAINLESS_STEEL);
        g.setColor(new Color(0x45, 0x5A, 0x64));
        g.setStroke(new BasicStroke(1.5f));
        int zig = Math.max(3, (int) (h / 10));
        double step = (h - 8) / zig;
        for (int i = 0; i < zig; i++) {
            double y0 = 4 + i * step;
            g.draw(new Line2D.Double(5, y0, w - 5, y0 + step * 0.5));
            g.draw(new Line2D.Double(w - 5, y0 + step * 0.5, 5, y0 + step));
        }
    }

    private static void paintHumidifier(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        g.setColor(state == EquipmentState.RUNNING ? COOL_BLUE : new Color(0x60, 0x7D, 0x8B));
        for (int i = 0; i < 3; i++) {
            double dx = w * (0.30 + i * 0.20);
            g.fill(new Ellipse2D.Double(dx - 3, h * 0.45, 6, 8));
        }
    }

    private static void paintFanWithSpeed(Graphics2D g, double w, double h, EquipmentState state,
                                          double phase, double speedPct, boolean axial) {
        if (axial) {
            ScadaShader.paintCylinderH(g, 2, h * 0.18, w - 4, h * 0.64, Material.STAINLESS_STEEL);
        } else {
            double r = Math.min(w, h) * 0.42;
            ScadaShader.paintVoluteCasing(g, w * 0.5, h * 0.5, r, r * 0.72, Material.CAST_IRON);
        }
        double d = Math.min(w, h) * 0.54;
        double cx = w * 0.5;
        double cy = h * 0.5;
        double angle = (state == EquipmentState.RUNNING) ? phase * (0.2 + 0.25 * (speedPct / 100.0)) : 0.0;
        g.setColor(ScadaPalette.ROTOR_BLADE_A_HIGHLIGHT);
        g.setStroke(new BasicStroke(2.2f));
        for (int i = 0; i < 4; i++) {
            double a = angle + i * (Math.PI / 2.0);
            g.draw(new Line2D.Double(cx, cy, cx + Math.cos(a) * d * 0.42, cy + Math.sin(a) * d * 0.42));
        }
        drawBadge(g, String.format("%.0f%%", Math.max(0, Math.min(100, speedPct))), w * 0.5, h * 0.92, ScadaPalette.TEXT_PRIMARY, 8);
    }

    private static void paintCompressor(Graphics2D g, double w, double h, EquipmentState state, double phase, String tag) {
        ScadaShader.paintCylinderH(g, w * 0.12, h * 0.18, w * 0.76, h * 0.64, Material.CAST_IRON);
        double r = Math.min(w, h) * 0.24;
        double ang = (state == EquipmentState.RUNNING) ? phase * 0.35 : 0.0;
        ScadaShader.paintRotorImpeller(g, w * 0.50, h * 0.50, r, 6, ang);
        drawBadge(g, tag, w * 0.50, h * 0.20, Color.WHITE, 8);
    }

    private static void paintExpansionValve(Graphics2D g, double w, double h, EquipmentState state) {
        double cy = h * 0.60;
        double cx = w * 0.50;
        Polygon left = new Polygon(new int[]{2, (int) cx, 2}, new int[]{(int) (cy - h * 0.22), (int) cy, (int) (cy + h * 0.22)}, 3);
        Polygon right = new Polygon(new int[]{(int) (w - 2), (int) cx, (int) (w - 2)}, new int[]{(int) (cy - h * 0.22), (int) cy, (int) (cy + h * 0.22)}, 3);
        g.setColor(state == EquipmentState.RUNNING ? COOL_BLUE : new Color(0x63, 0x6D, 0x7A));
        g.fill(left);
        g.fill(right);
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.draw(left);
        g.draw(right);
        ScadaShader.paintDishedTankHead(g, cx - w * 0.22, h * 0.10, w * 0.44, h * 0.18, true, Material.STAINLESS_STEEL);
        g.draw(new Line2D.Double(cx, h * 0.28, cx, cy));
    }

    private static void paintRefrigerantReceiver(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintDishedTankHead(g, w * 0.16, 4, w * 0.68, h * 0.16, true, Material.STAINLESS_STEEL);
        ScadaShader.paintCylinderV(g, w * 0.16, 4 + h * 0.16, w * 0.68, h * 0.62, Material.STAINLESS_STEEL);
        ScadaShader.paintDishedTankHead(g, w * 0.16, h * 0.80, w * 0.68, h * 0.16, false, Material.STAINLESS_STEEL);
        g.setColor(COOL_BLUE);
        g.fill(new Rectangle2D.Double(w * 0.34, h * 0.46, w * 0.32, h * 0.28));
        drawBadge(g, "RCV", w * 0.50, h * 0.36, ScadaPalette.TEXT_PRIMARY, 8);
    }

    private static void paintSolarCollector(Graphics2D g, double w, double h, EquipmentState state) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.FLANGE_STEEL);
        g.setColor(state == EquipmentState.RUNNING ? new Color(0x15, 0x65, 0xC0) : new Color(0x26, 0x32, 0x38));
        g.fill(new Rectangle2D.Double(6, 6, w - 12, h - 12));
        g.setColor(FLAME_ORANGE);
        g.setStroke(new BasicStroke(1.6f));
        for (int i = 1; i <= 4; i++) {
            double x = 8 + (w - 16) * i / 5.0;
            g.draw(new Line2D.Double(x, 8, x, h - 8));
        }
    }

    private static void paintUnderfloorZone(Graphics2D g, ScadaSymbolInstance sym, double w, double h,
                                            EquipmentState state, double tempC) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 4, Material.STAINLESS_STEEL);
        g.setColor(state == EquipmentState.RUNNING ? FLAME_ORANGE : new Color(0x60, 0x7D, 0x8B));
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int loops = 4;
        double step = (h - 16) / loops;
        for (int i = 0; i < loops; i++) {
            double y0 = 8 + i * step;
            g.draw(new Line2D.Double(8, y0, w - 8, y0));
            if (i < loops - 1) {
                double rx = (i % 2 == 0) ? w - 8 : 8;
                g.draw(new Line2D.Double(rx, y0, rx, y0 + step));
            }
        }
        drawBadge(g, String.format("%.1f°C", tempC), w * 0.50, h * 0.56, ScadaPalette.TEXT_PRIMARY, 8);
    }

    private static void paintVavBox(Graphics2D g, double w, double h, EquipmentState state, double posPct) {
        ScadaShader.paintCylinderH(g, 2, h * 0.22, w - 4, h * 0.56, Material.STAINLESS_STEEL);
        double clamped = Math.max(0.0, Math.min(100.0, posPct));
        double bladeAng = Math.toRadians(85.0 - (clamped / 100.0) * 80.0);
        double len = h * 0.24;
        g.setColor(ScadaPalette.OUTLINE_DARK);
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new Line2D.Double(w * 0.5 - Math.cos(bladeAng) * len, h * 0.5 - Math.sin(bladeAng) * len,
                w * 0.5 + Math.cos(bladeAng) * len, h * 0.5 + Math.sin(bladeAng) * len));
        drawBadge(g, String.format("VAV %.0f%%", clamped), w * 0.50, h * 0.94, ScadaPalette.TEXT_PRIMARY, 7);
    }

    private static void paintRoomSymbol(Graphics2D g, ScadaSymbolInstance sym, double w, double h,
                                        EquipmentState state, double tempC) {
        ScadaShader.paintBeveledHousing(g, 2, 2, w - 4, h - 4, 6, Material.STAINLESS_STEEL);
        g.setColor(new Color(0x18, 0x1C, 0x20));
        g.fill(new RoundRectangle2D.Double(6, 6, w - 12, h - 12, 4, 4));
        drawBadge(g, String.format("%.1f°C", tempC), w * 0.50, h * 0.52,
                state == EquipmentState.RUNNING ? new Color(0x69, 0xF0, 0xAE) : Color.WHITE, 11);
        String sp = sym != null ? sym.getProperty("Setpoint", "") : "";
        if (!sp.isBlank()) {
            drawBadge(g, "SP " + sp + "°C", w * 0.50, h * 0.78, new Color(0x90, 0xCA, 0xF9), 8);
        }
    }

    private static void drawBadge(Graphics2D g, String text, double cx, double y, Color color, int size) {
        g.setColor(color);
        g.setFont(new Font("Segoe UI", Font.BOLD, size));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) y);
    }
}

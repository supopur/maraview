package cz.maramat.scada.render;

import java.awt.Color;

/**
 * Standard 3D industrial shading palettes, outlines, and state indicator colors
 * matching the WinCC / Symbol Factory aesthetic in SCADA_SPECIFICATION.md.
 */
public final class ScadaPalette {

    public enum Material {
        CAST_IRON(new Color[]{
                new Color(0x46, 0x4E, 0x59),
                new Color(0x8C, 0x97, 0xA6),
                new Color(0x63, 0x6D, 0x7A),
                new Color(0x34, 0x3A, 0x42)
        }),
        STAINLESS_STEEL(new Color[]{
                new Color(0x5A, 0x63, 0x70),
                new Color(0xBC, 0xC6, 0xD4),
                new Color(0x7C, 0x87, 0x96),
                new Color(0x40, 0x47, 0x52)
        }),
        FLANGE_STEEL(new Color[]{
                new Color(0x2F, 0x35, 0x3D),
                new Color(0x6E, 0x78, 0x85),
                new Color(0x47, 0x4E, 0x59),
                new Color(0x22, 0x26, 0x2B)
        }),
        PIPE_WALL(new Color[]{
                new Color(0x2B, 0x30, 0x38),
                new Color(0x9A, 0xA4, 0xB2),
                new Color(0x5A, 0x63, 0x70),
                new Color(0x2B, 0x30, 0x38)
        });

        private final Color[] stops;

        Material(Color[] stops) {
            this.stops = stops;
        }

        public Color[] getStops() {
            return stops.clone();
        }

        Color[] stopsRaw() {
            return stops;
        }
    }

    public static final float[] STANDARD_FRACTIONS = {0.0f, 0.28f, 0.65f, 1.0f};

    public static final Color OUTLINE_DARK = new Color(0x14, 0x17, 0x1A);
    public static final Color BEVEL_HIGHLIGHT = new Color(0xB8, 0xC2, 0xCC);
    public static final Color BEVEL_HIGHLIGHT_SOFT = new Color(0xB8, 0xC2, 0xCC, 150);

    public static final Color ROTOR_BLADE_A = new Color(0x3B, 0x4C, 0xCA);
    public static final Color ROTOR_BLADE_A_HIGHLIGHT = new Color(0x52, 0x66, 0xE8);
    public static final Color ROTOR_BLADE_B = new Color(0x1B, 0x22, 0x4B);

    public static final Color CUTOUT_DARK = new Color(0x18, 0x1C, 0x20);
    public static final Color CUTOUT_LIGHT = new Color(0x2A, 0x30, 0x38);

    public static final Color STATE_STOPPED_LED = new Color(0x4A, 0x52, 0x5D);
    public static final Color STATE_RUNNING_LED = new Color(0x1E, 0x84, 0x49);
    public static final Color STATE_RUNNING = STATE_RUNNING_LED;
    public static final Color STATE_STANDBY = new Color(0x02, 0x88, 0xD1);
    public static final Color STATE_WARNING = new Color(0xD6, 0x89, 0x10);
    public static final Color STATE_FAULT = new Color(0xD9, 0x26, 0x26);
    public static final Color SELECTION_BLUE = new Color(0x00, 0x78, 0xD7);
    public static final Color TEXT_PRIMARY = new Color(0x14, 0x17, 0x1A);

    private ScadaPalette() {
    }
}

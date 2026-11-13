package cz.maramat.scada.model;

import java.awt.Color;
import java.util.Locale;

/**
 * PLC-compatible data types for SCADA tags and bindings, aligned with Maraflow's DataType.
 */
public enum DataType {
    BOOL("BOOL", new Color(0x1E, 0x84, 0x49), "0"),
    INT("INT", new Color(0x1A, 0x1A, 0x1A), "0"),
    LONG("LONG", new Color(0x7D, 0x3C, 0x98), "0"),
    FLOAT("FLOAT", new Color(0x1F, 0x61, 0x8D), "0");

    private final String displayName;
    private final Color wireColor;
    private final String defaultConstant;

    DataType(String displayName, Color wireColor, String defaultConstant) {
        this.displayName = displayName;
        this.wireColor = wireColor;
        this.defaultConstant = defaultConstant;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getWireColor() {
        return wireColor;
    }

    public String getDefaultConstant() {
        return defaultConstant;
    }

    public static DataType fromString(String raw, DataType fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return DataType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public String validateAndNormalizeConstant(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Constant value cannot be empty.");
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Constant value cannot be empty.");
        }
        return switch (this) {
            case BOOL -> {
                if (s.equalsIgnoreCase("true")) yield "1";
                if (s.equalsIgnoreCase("false")) yield "0";
                long v = parseIntegerLiteral(s);
                yield (v <= 0) ? "0" : "1";
            }
            case INT -> {
                long v = parseIntegerLiteral(s);
                if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("INT constant out of 32-bit signed range: " + s);
                }
                yield Integer.toString((int) v);
            }
            case LONG -> Long.toString(parseIntegerLiteral(s));
            case FLOAT -> {
                if (s.startsWith("0x") || s.startsWith("0X") || s.startsWith("0b") || s.startsWith("0B")) {
                    long bits = parseIntegerLiteral(s);
                    yield Float.toString((float) bits);
                }
                try {
                    float f = Float.parseFloat(s);
                    if (Float.isNaN(f) || Float.isInfinite(f)) {
                        throw new IllegalArgumentException("FLOAT constant must be finite.");
                    }
                    if (f == (long) f) {
                        yield Long.toString((long) f);
                    }
                    yield Float.toString(f);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid FLOAT constant: '" + s + "'");
                }
            }
        };
    }

    public static long parseIntegerLiteral(String s) {
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) {
                return Long.parseUnsignedLong(s.substring(2), 16);
            } else if (s.startsWith("-0x") || s.startsWith("-0X")) {
                return -Long.parseUnsignedLong(s.substring(3), 16);
            } else if (s.startsWith("0b") || s.startsWith("0B")) {
                return Long.parseUnsignedLong(s.substring(2), 2);
            } else if (s.startsWith("-0b") || s.startsWith("-0B")) {
                return -Long.parseUnsignedLong(s.substring(3), 2);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric constant: '" + s + "'");
        }
    }

    public double toNumericValue(String valStr) {
        if (valStr == null || valStr.isBlank()) {
            return 0.0;
        }
        String trimmed = valStr.trim();
        if (this == BOOL) {
            if ("true".equalsIgnoreCase(trimmed)) return 1.0;
            if ("false".equalsIgnoreCase(trimmed)) return 0.0;
        }
        try {
            if (this == FLOAT) {
                return Double.parseDouble(trimmed);
            }
            return (double) parseIntegerLiteral(trimmed);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public String formatRuntimeValue(double rawVal, boolean compact) {
        return switch (this) {
            case BOOL -> (rawVal != 0.0 && !Double.isNaN(rawVal)) ? "1" : "0";
            case INT -> Integer.toString((int) rawVal);
            case LONG -> Long.toString((long) rawVal);
            case FLOAT -> {
                float f = (float) rawVal;
                if (Float.isNaN(f) || Float.isInfinite(f)) yield "0.00";
                if (!compact) yield Float.toString(f);
                if (Math.abs(f - Math.round(f)) < 1e-5f) {
                    yield Integer.toString(Math.round(f));
                }
                yield String.format(Locale.US, "%.2f", f);
            }
        };
    }

    public static double convertValue(double srcValue, DataType srcType, DataType dstType) {
        if (srcType == dstType) return srcValue;
        return switch (dstType) {
            case BOOL -> (srcValue != 0.0 && !Double.isNaN(srcValue)) ? 1.0 : 0.0;
            case INT -> (double) ((int) srcValue);
            case LONG -> (double) ((long) srcValue);
            case FLOAT -> (double) ((float) srcValue);
        };
    }
}

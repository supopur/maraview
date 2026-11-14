package cz.maramat.scada.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zero-dependency, deterministic RFC-8259 JSON parser and pretty-printer.
 * Preserves insertion order via LinkedHashMap so serialized .msl files are diff-friendly.
 */
public final class JsonCodec {

    private JsonCodec() {
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder(1024);
        writeValue(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    public static Object parse(String json) throws IOException {
        if (json == null) {
            throw new IOException("JSON input is null.");
        }
        Parser parser = new Parser(json);
        Object val = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new IOException("Unexpected trailing characters at position " + parser.pos);
        }
        return val;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) throws IOException {
        Object val = parse(json);
        if (!(val instanceof Map<?, ?> map)) {
            throw new IOException("Expected JSON root object.");
        }
        return (Map<String, Object>) map;
    }

    private static void writeValue(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (value instanceof Integer || value instanceof Long) {
            sb.append(value);
        } else if (value instanceof Double d) {
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("0");
            } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                sb.append((long) d.doubleValue());
            } else {
                sb.append(d);
            }
        } else if (value instanceof Number n) {
            sb.append(n);
        } else if (value instanceof String s) {
            writeEscapedString(sb, s);
        } else if (value instanceof Map<?, ?> map) {
            writeMap(sb, map, indent);
        } else if (value instanceof List<?> list) {
            writeList(sb, list, indent);
        } else {
            writeEscapedString(sb, value.toString());
        }
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        int size = map.size();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            appendIndent(sb, indent + 1);
            writeEscapedString(sb, String.valueOf(entry.getKey()));
            sb.append(": ");
            writeValue(sb, entry.getValue(), indent + 1);
            if (++i < size) {
                sb.append(',');
            }
            sb.append('\n');
        }
        appendIndent(sb, indent);
        sb.append('}');
    }

    private static void writeList(StringBuilder sb, List<?> list, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        int size = list.size();
        for (int i = 0; i < size; i++) {
            appendIndent(sb, indent + 1);
            writeValue(sb, list.get(i), indent + 1);
            if (i + 1 < size) {
                sb.append(',');
            }
            sb.append('\n');
        }
        appendIndent(sb, indent);
        sb.append(']');
    }

    private static void appendIndent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
    }

    private static void writeEscapedString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String src;
        private int pos = 0;

        Parser(String src) {
            this.src = src;
        }

        boolean isAtEnd() {
            return pos >= src.length();
        }

        void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private static final int MAX_DEPTH = 256;
        private int depth = 0;

        Object parseValue() throws IOException {
            if (++depth > MAX_DEPTH) {
                throw new IOException("JSON nesting depth exceeds " + MAX_DEPTH);
            }
            try {
                skipWhitespace();
                if (isAtEnd()) {
                    throw new IOException("Unexpected end of JSON input.");
                }
                char c = src.charAt(pos);
                return switch (c) {
                    case '{' -> parseObject();
                    case '[' -> parseArray();
                    case '"' -> parseString();
                    case 't', 'f' -> parseBoolean();
                    case 'n' -> parseNull();
                    default -> {
                        if (c == '-' || (c >= '0' && c <= '9')) {
                            yield parseNumber();
                        }
                        throw new IOException("Unexpected character '" + c + "' at position " + pos);
                    }
                };
            } finally {
                depth--;
            }
        }

        Map<String, Object> parseObject() throws IOException {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (!isAtEnd() && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (isAtEnd() || src.charAt(pos) != '"') {
                    throw new IOException("Expected string key at position " + pos);
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (isAtEnd()) {
                    throw new IOException("Unterminated JSON object.");
                }
                char next = src.charAt(pos);
                if (next == ',') {
                    pos++;
                } else if (next == '}') {
                    pos++;
                    return map;
                } else {
                    throw new IOException("Expected ',' or '}' at position " + pos);
                }
            }
        }

        List<Object> parseArray() throws IOException {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (!isAtEnd() && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (isAtEnd()) {
                    throw new IOException("Unterminated JSON array.");
                }
                char next = src.charAt(pos);
                if (next == ',') {
                    pos++;
                } else if (next == ']') {
                    pos++;
                    return list;
                } else {
                    throw new IOException("Expected ',' or ']' at position " + pos);
                }
            }
        }

        String parseString() throws IOException {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= src.length()) {
                        throw new IOException("Unterminated escape sequence.");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > src.length()) {
                                throw new IOException("Invalid unicode escape at position " + pos);
                            }
                            String hex = src.substring(pos, pos + 4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                throw new IOException("Invalid unicode escape: \\u" + hex);
                            }
                            pos += 4;
                        }
                        default -> throw new IOException("Invalid escape '\\" + esc + "' at position " + (pos - 1));
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IOException("Unterminated JSON string.");
        }

        Boolean parseBoolean() throws IOException {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IOException("Invalid boolean literal at position " + pos);
        }

        Object parseNull() throws IOException {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IOException("Invalid null literal at position " + pos);
        }

        Number parseNumber() throws IOException {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            boolean hasDigits = false;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
                hasDigits = true;
            }
            if (!hasDigits) {
                throw new IOException("Invalid number at position " + start);
            }
            boolean isFloat = false;
            if (pos < src.length() && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                    pos++;
                }
            }
            String numStr = src.substring(start, pos);
            try {
                if (!isFloat) {
                    return Long.parseLong(numStr);
                }
                return Double.parseDouble(numStr);
            } catch (NumberFormatException ex) {
                throw new IOException("Invalid number '" + numStr + "' at position " + start, ex);
            }
        }

        void expect(char expected) throws IOException {
            if (pos >= src.length() || src.charAt(pos) != expected) {
                throw new IOException("Expected '" + expected + "' at position " + pos);
            }
            pos++;
        }
    }
}

package pollsystem.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * מיני-פרסר/serializer ל-JSON, ללא כל תלות בספריות חיצוניות (org.json / Gson / Jackson).
 * <p>
 * הפרויקט אינו משתמש ב-Maven להורדת ספריות בזמן קומפילציה בסביבה הזו, ולכן כל
 * התקשורת עם Telegram Bot API ועם OpenAI API ממומשת מעל java.net.http.HttpClient
 * הסטנדרטי + כלי ה-JSON הזה. תומך באובייקטים, מערכים, מחרוזות, מספרים,
 * בוליאנים ו-null - כל מה שדרוש לשני ה-API-ים.
 */
public final class Json {

    private Json() {}

    // ==================== Parsing ====================

    public static Object parse(String text) {
        Parser p = new Parser(text);
        Object result = p.parseValue();
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new JsonException("תווים עודפים בסוף ה-JSON במיקום " + p.pos);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new JsonException("ציפינו לאובייקט JSON ברמה העליונה, התקבל: " + text);
        }
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        private final String s;
        private int pos = 0;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return pos >= s.length(); }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char peek() {
            if (pos >= s.length()) throw new JsonException("סוף קלט לא צפוי");
            return s.charAt(pos);
        }

        char next() {
            if (pos >= s.length()) throw new JsonException("סוף קלט לא צפוי");
            return s.charAt(pos++);
        }

        void expect(char c) {
            char got = next();
            if (got != c) {
                throw new JsonException("ציפינו ל-'" + c + "' אך התקבל '" + got + "' במיקום " + (pos - 1));
            }
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObjectValue();
                case '[' -> parseArrayValue();
                case '"' -> parseStringValue();
                case 't' -> { expectLiteral("true"); yield Boolean.TRUE; }
                case 'f' -> { expectLiteral("false"); yield Boolean.FALSE; }
                case 'n' -> { expectLiteral("null"); yield null; }
                default -> parseNumberValue();
            };
        }

        void expectLiteral(String literal) {
            if (pos + literal.length() > s.length() || !s.startsWith(literal, pos)) {
                throw new JsonException("ציפינו ל-'" + literal + "' במיקום " + pos);
            }
            pos += literal.length();
        }

        Map<String, Object> parseObjectValue() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWhitespace();
            if (peek() == '}') { next(); return map; }
            while (true) {
                skipWhitespace();
                String key = parseStringValue();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw new JsonException("ציפינו ל-',' או '}' במיקום " + (pos - 1));
            }
            return map;
        }

        List<Object> parseArrayValue() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') { next(); return list; }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char c = next();
                if (c == ']') break;
                if (c != ',') throw new JsonException("ציפינו ל-',' או ']' במיקום " + (pos - 1));
            }
            return list;
        }

        String parseStringValue() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') break;
                if (c == '\\') {
                    char esc = next();
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new JsonException("escape לא תקין '\\" + esc + "' במיקום " + (pos - 1));
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseNumberValue() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            boolean isDouble = false;
            if (pos < s.length() && s.charAt(pos) == '.') {
                isDouble = true;
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isDouble = true;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            String numStr = s.substring(start, pos);
            if (numStr.isEmpty() || numStr.equals("-")) {
                throw new JsonException("מספר לא תקין במיקום " + start);
            }
            if (isDouble) return Double.parseDouble(numStr);
            try {
                return Long.parseLong(numStr);
            } catch (NumberFormatException e) {
                return Double.parseDouble(numStr);
            }
        }
    }

    // ==================== Writing ====================

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String str) {
            writeString(str, sb);
        } else if (value instanceof Boolean b) {
            sb.append(b.toString());
        } else if (value instanceof Number n) {
            sb.append(n.toString());
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                write(item, sb);
            }
            sb.append(']');
        } else if (value instanceof Object[] arr) {
            write(Arrays.asList(arr), sb);
        } else {
            writeString(value.toString(), sb);
        }
    }

    private static void writeString(String str, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
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

    // ==================== Convenience builders ====================

    /** אובייקט JSON ניתן לבנייה בהדרגה: Json.obj().put("k", v)... ואז Json.stringify(...) */
    public static Map<String, Object> obj() {
        return new LinkedHashMap<>();
    }

    // ==================== Convenience accessors (null-safe) ====================

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asList(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public static long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        throw new JsonException("ציפינו למספר, התקבל: " + o);
    }

    /** נוחות: שולף מפתח מתוך מפה ומחזיר אותו כ-Map, או null אם חסר/לא מסוג אובייקט. */
    public static Map<String, Object> get(Map<String, Object> map, String key) {
        return asMap(map.get(key));
    }

    public static final class JsonException extends RuntimeException {
        public JsonException(String message) { super(message); }
    }
}

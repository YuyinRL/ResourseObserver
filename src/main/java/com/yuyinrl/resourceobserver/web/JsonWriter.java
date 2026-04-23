package com.yuyinrl.resourceobserver.web;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 轻量 JSON 序列化器 —— 仅支持 API 响应所需的基本类型。
 * <p>
 * 避免引入 Gson/Jackson 等额外运行时依赖；输出的 JSON 符合 RFC 8259。
 * 支持：{@link Map}、{@link Iterable}、{@link Number}、{@link Boolean}、{@link String}、{@code null}。
 * 其他类型调用 {@code toString()} 后按字符串输出。
 */
public final class JsonWriter {
    private JsonWriter() {
    }

    public static String write(@Nullable Object value) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, @Nullable Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map<?, ?> map) {
            writeMap(sb, map);
        } else if (value instanceof Iterable<?> it) {
            writeArray(sb, it);
        } else if (value instanceof Number n) {
            writeNumber(sb, n);
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue() ? "true" : "false");
        } else if (value instanceof CharSequence cs) {
            writeString(sb, cs.toString());
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeNumber(StringBuilder sb, Number n) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            sb.append("null");
            return;
        }
        sb.append(n);
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            writeValue(sb, e.getValue());
            first = false;
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Iterable<?> it) {
        sb.append('[');
        boolean first = true;
        for (Object v : it) {
            if (!first) sb.append(',');
            writeValue(sb, v);
            first = false;
        }
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
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
}

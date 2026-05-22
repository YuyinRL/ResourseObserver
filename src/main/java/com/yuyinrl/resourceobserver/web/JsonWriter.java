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

    /**
     * 把任意值序列化为 JSON 字符串。
     *
     * @param value 待序列化对象（支持 Map/Iterable/Number/Boolean/CharSequence/null）
     * @return 标准 JSON 文本
     */
    public static String write(@Nullable Object value) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(sb, value);
        return sb.toString();
    }

    /** 通用值分派 —— 根据运行时类型路由到对应序列化方法。 */
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

    /** 写数字；NaN/Infinity 退化为 {@code null} 以保证 JSON 合法。 */
    private static void writeNumber(StringBuilder sb, Number n) {
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            sb.append("null");
            return;
        }
        sb.append(n);
    }

    /** 写对象；仅按插入顺序遍历 entrySet（{@link java.util.LinkedHashMap} 友好）。 */
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

    /** 写数组。 */
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

    /** 写字符串；按 RFC 8259 转义控制字符与引号、反斜杠。 */
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

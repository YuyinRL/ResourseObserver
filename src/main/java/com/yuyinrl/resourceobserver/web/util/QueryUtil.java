package com.yuyinrl.resourceobserver.web.util;

import org.jetbrains.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** HTTP 查询字符串解析工具 */
public final class QueryUtil {
    private QueryUtil() {}

    /**
     * 解析 query string 为 key→value 映射，保留插入顺序。
     * <p>遇到无 {@code =} 的片段视为空值；多次出现的 key 后者覆盖前者。</p>
     *
     * @param raw URL 中 {@code ?} 之后的原始片段，可为 {@code null} 或空
     */
    public static Map<String, String> parseQuery(@Nullable String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) out.put(urlDecode(pair), "");
            else out.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
        }
        return out;
    }

    /** 安全 URL 解码 —— 解码失败时退回原串以避免 query 解析整体失败。 */
    public static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}

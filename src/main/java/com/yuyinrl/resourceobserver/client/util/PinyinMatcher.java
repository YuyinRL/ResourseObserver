package com.yuyinrl.resourceobserver.client.util;

import com.yuyinrl.resourceobserver.integration.JecIntegration;

import java.nio.charset.Charset;
import java.util.Locale;

/**
 * 拼音匹配工具 —— 支持中文拼音搜索。
 * <p>
 * 优先使用 JEC (Just Enough Characters) 的拼音引擎，支持全拼、首字母、模糊音等高级搜索；
 * JEC 未安装时回退到内置 GB2312 首字母匹配（仅支持拼音首字母）。
 * <p>
 * 内置匹配支持三种模式：
 * <ol>
 *   <li>普通子串匹配（大小写不敏感）</li>
 *   <li>纯拼音首字母匹配（如 "tj" 匹配 "铁剑"）</li>
 *   <li>混合匹配（如 "铁j" 匹配 "铁剑"、"64kccyj" 匹配 "64K存储元件"）</li>
 * </ol>
 */
public final class PinyinMatcher {

    private PinyinMatcher() {}

    /**
     * GB2312 一级汉字区拼音首字母分区边界值。
     * 计算方式：GBK 编码的 firstByte * 256 + secondByte - 65536。
     * 一级汉字按拼音排序（0xB0A1–0xD7F9），此表覆盖约 3755 个常用汉字。
     */
    private static final int[] BOUNDARIES = {
            -20319, -20284, -19776, -19219, -18711, -18527, -17922, -17418,
            -16475, -15921, -15445, -15016, -14580, -14069, -13319, -12839,
            -12557, -11848, -11056, -10247, -9959, -9231, -8531
    };

    /** 与 {@link #BOUNDARIES} 一一对应的拼音首字母（小写）。注意汉语拼音无 I/U/V 开头。 */
    private static final char[] INITIALS = {
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h',
            'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
            'r', 's', 't', 'w', 'x', 'y', 'z'
    };

    private static final Charset GBK;

    static {
        Charset cs;
        try {
            cs = Charset.forName("GBK");
        } catch (Exception e) {
            cs = null;
        }
        GBK = cs;
    }

    /**
     * 判断搜索关键词是否匹配显示名称。
     * <p>
     * 如果 JEC (Just Enough Characters) 已安装，委托给 JEC 的拼音引擎（支持全拼、首字母、模糊音）；
     * 否则回退到内置匹配：普通子串匹配 → 纯拼音首字母匹配 → 混合匹配。
     *
     * @param displayName 物品显示名称
     * @param query       搜索关键词（原始输入，内部自动处理大小写）
     * @return true 表示匹配
     */
    public static boolean matches(String displayName, String query) {
        if (query == null || query.isEmpty()) return true;
        if (displayName == null || displayName.isEmpty()) return false;

        // 优先使用 JEC 的拼音引擎（支持全拼、模糊音等高级搜索）
        if (JecIntegration.isAvailable()) {
            return JecIntegration.contains(
                    displayName.toLowerCase(Locale.ROOT),
                    query.toLowerCase(Locale.ROOT)
            );
        }

        // 回退到内置拼音首字母匹配
        return builtinMatches(displayName, query);
    }

    /**
     * 内置拼音首字母匹配（JEC 未安装时的回退方案）。
     * 依次尝试：普通子串匹配 → 纯拼音首字母匹配 → 混合匹配。
     */
    private static boolean builtinMatches(String displayName, String query) {

        String queryLower = query.toLowerCase(Locale.ROOT);
        String nameLower = displayName.toLowerCase(Locale.ROOT);

        // 普通子串匹配（最常用路径，快速返回）
        if (nameLower.contains(queryLower)) return true;

        // GBK 不可用则跳过拼音匹配
        if (GBK == null) return false;

        // 纯拼音首字母匹配（如 "tj" 匹配 "铁剑"）
        String initials = extractInitials(nameLower);
        if (!initials.isEmpty() && initials.contains(queryLower)) return true;

        // 混合匹配（如 "铁j" 匹配 "铁剑"，"64kccyj" 匹配 "64K存储元件"）
        return mixedMatch(nameLower, queryLower);
    }

    /**
     * 提取字符串中所有中文字符的拼音首字母，非中文字符原样保留。
     * 例如 "AE2控制器" → "ae2kzq"。
     */
    private static String extractInitials(String textLower) {
        StringBuilder sb = new StringBuilder(textLower.length());
        for (int i = 0; i < textLower.length(); i++) {
            char c = textLower.charAt(i);
            if (c >= '\u4E00' && c <= '\u9FFF') {
                char initial = getInitial(c);
                if (initial != 0) {
                    sb.append(initial);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 混合匹配 —— 允许搜索词中同时包含直接字符和拼音首字母。
     * <p>
     * 对 displayName 中的每个起始位置，逐字符比较：
     * <ul>
     *   <li>直接匹配（大小写不敏感）</li>
     *   <li>中文字符 vs 拉丁字母：检查拼音首字母是否一致</li>
     * </ul>
     */
    private static boolean mixedMatch(String name, String query) {
        int nameLen = name.length();
        int queryLen = query.length();
        for (int start = 0; start <= nameLen - queryLen; start++) {
            if (matchAt(name, start, query)) return true;
        }
        return false;
    }

    private static boolean matchAt(String name, int start, String query) {
        int ni = start;
        for (int qi = 0; qi < query.length(); qi++) {
            if (ni >= name.length()) return false;
            char qc = query.charAt(qi);
            char nc = name.charAt(ni);

            if (qc == nc) {
                ni++;
            } else if (nc >= '\u4E00' && nc <= '\u9FFF' && qc >= 'a' && qc <= 'z') {
                char initial = getInitial(nc);
                if (initial == qc) {
                    ni++;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取单个中文字符的拼音首字母（小写）。
     * 仅支持 GB2312 一级汉字区（约 3755 字），超出范围返回 0。
     */
    private static char getInitial(char ch) {
        if (GBK == null) return 0;
        try {
            byte[] bytes = String.valueOf(ch).getBytes(GBK);
            if (bytes.length != 2) return 0;
            int code = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
            int value = code - 65536;

            // GB2312 一级汉字区范围：-20319 (0xB0A1) 至约 -8032
            if (value < -20319 || value > -8032) return 0;

            // 逆序查找：找到最后一个 <= value 的边界
            for (int i = BOUNDARIES.length - 1; i >= 0; i--) {
                if (value >= BOUNDARIES[i]) {
                    return INITIALS[i];
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}

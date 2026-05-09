package com.yuyinrl.resourceobserver.service.snapshot.crafting;

/**
 * 搜索匹配抽象 —— 服务层不依赖客户端 JEC 拼音库。
 * 默认实现 {@link #PLAIN} 走大小写不敏感的 substring；客户端可注入拼音匹配。
 */
public interface CraftingSearchMatcher {

    boolean matches(String text, String query);

    /** 大小写不敏感 substring 匹配（兜底） */
    CraftingSearchMatcher PLAIN = (text, query) -> {
        if (query == null || query.isEmpty()) return true;
        if (text == null) return false;
        return text.toLowerCase(java.util.Locale.ROOT)
                .contains(query.toLowerCase(java.util.Locale.ROOT));
    };
}

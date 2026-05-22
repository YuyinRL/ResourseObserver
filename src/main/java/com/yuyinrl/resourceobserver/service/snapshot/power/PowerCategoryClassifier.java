package com.yuyinrl.resourceobserver.service.snapshot.power;

import java.util.Locale;

/**
 * 设备分类工具 —— 把 targetBlockId 按关键字归类到 4 个 LoadCategory。
 * 同时提供翻译键 / 模组命名空间提取。
 */
public final class PowerCategoryClassifier {

    public static final String CATEGORY_KEY_PREFIX = "screen.resourceobserver.power.category.";

    private PowerCategoryClassifier() {
    }

    /** 按 targetBlockId 关键字推断 LoadCategory */
    public static PowerSnapshot.LoadCategory inferCategory(String targetBlockId) {
        if (targetBlockId == null || targetBlockId.isBlank()) {
            return PowerSnapshot.LoadCategory.OTHER;
        }
        String lower = targetBlockId.toLowerCase(Locale.ROOT);
        if (lower.contains("miner") || lower.contains("quarry") || lower.contains("drill")
                || lower.contains("pump") || lower.contains("excavat")) {
            return PowerSnapshot.LoadCategory.MINING;
        }
        if (lower.contains("assembl") || lower.contains("craft") || lower.contains("inscriber")
                || lower.contains("press") || lower.contains("furnace") || lower.contains("smelter")
                || lower.contains("grinder") || lower.contains("crusher") || lower.contains("machine")) {
            return PowerSnapshot.LoadCategory.ASSEMBLY;
        }
        if (lower.contains("bus") || lower.contains("interface") || lower.contains("import")
                || lower.contains("export") || lower.contains("pipe") || lower.contains("duct")
                || lower.contains("conveyor") || lower.contains("router")) {
            return PowerSnapshot.LoadCategory.LOGISTICS;
        }
        return PowerSnapshot.LoadCategory.OTHER;
    }

    /** LoadCategory → 翻译键 */
    public static String translationKey(PowerSnapshot.LoadCategory category) {
        return switch (category) {
            case MINING -> CATEGORY_KEY_PREFIX + "mining";
            case ASSEMBLY -> CATEGORY_KEY_PREFIX + "assembly";
            case LOGISTICS -> CATEGORY_KEY_PREFIX + "logistics";
            case OTHER -> CATEGORY_KEY_PREFIX + "other";
        };
    }

    /** 从方块注册 ID 中提取模组命名空间（冒号前）；非法/空 → 空串 */
    public static String extractModNamespace(String targetBlockId) {
        if (targetBlockId == null || targetBlockId.isBlank()) return "";
        int colonIdx = targetBlockId.indexOf(':');
        if (colonIdx <= 0) return targetBlockId;
        return targetBlockId.substring(0, colonIdx);
    }
}

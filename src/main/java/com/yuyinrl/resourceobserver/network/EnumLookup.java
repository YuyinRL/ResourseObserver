package com.yuyinrl.resourceobserver.network;

import java.util.Locale;
import java.util.function.ToIntFunction;
import java.util.function.Function;

/**
 * 枚举查找工具类 —— 提供通用的枚举值查找方法，
 * 消除各枚举类中重复的 fromId / fromKey 循环匹配模式。
 */
public final class EnumLookup {
    private EnumLookup() {
    }

    /**
     * 根据整型 ID 查找枚举值。
     *
     * @param values       枚举常量数组（通常为 {@code MyEnum.values()}）
     * @param idExtractor  从枚举值中提取 ID 的函数
     * @param id           要查找的 ID
     * @param defaultValue 未找到时返回的默认值
     * @param <E>          枚举类型
     * @return 匹配的枚举值，未找到时返回 defaultValue
     */
    public static <E extends Enum<E>> E fromId(E[] values, ToIntFunction<E> idExtractor, int id, E defaultValue) {
        for (E value : values) {
            if (idExtractor.applyAsInt(value) == id) {
                return value;
            }
        }
        return defaultValue;
    }

    /**
     * 根据字符串键查找枚举值（不区分大小写）。
     *
     * @param values       枚举常量数组（通常为 {@code MyEnum.values()}）
     * @param keyExtractor 从枚举值中提取键的函数
     * @param key          要查找的键（可为 null 或空白）
     * @param defaultValue 未找到或键为空时返回的默认值
     * @param <E>          枚举类型
     * @return 匹配的枚举值，未找到时返回 defaultValue
     */
    public static <E extends Enum<E>> E fromKey(E[] values, Function<E, String> keyExtractor, String key, E defaultValue) {
        if (key == null || key.isBlank()) {
            return defaultValue;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (E value : values) {
            if (keyExtractor.apply(value).equals(normalized)) {
                return value;
            }
        }
        return defaultValue;
    }
}

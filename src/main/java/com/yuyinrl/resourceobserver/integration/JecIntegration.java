package com.yuyinrl.resourceobserver.integration;

import com.yuyinrl.resourceobserver.ResourceObserverMod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Just Enough Characters (JEC) 集成适配器 —— 复用 JEC 的拼音匹配引擎实现中文搜索。
 * <p>
 * JEC 内部使用 PinIn 库进行拼音 → 汉字的模糊匹配，支持全拼、首字母、模糊音等多种搜索模式。
 * 本类通过反射调用 {@code me.towdium.jecharacters.utils.Match.contains(String, CharSequence)}，
 * 避免对 JEC 的编译期硬依赖。
 * <p>
 * 使用前应先调用 {@link #isAvailable()} 检查 JEC 是否已加载。
 */
public final class JecIntegration {

    private JecIntegration() {}

    private static volatile Boolean available;

    /**
     * 缓存的 MethodHandle，指向 {@code Match.contains(String, CharSequence)} 方法。
     * 仅在 JEC 可用时初始化，之后直接复用。
     */
    private static MethodHandle containsHandle;

    /**
     * 检测 JEC (Just Enough Characters) 是否已加载（懒初始化，线程安全）。
     */
    public static boolean isAvailable() {
        if (available == null) {
            synchronized (JecIntegration.class) {
                if (available == null) {
                    try {
                        Class<?> matchClass = Class.forName("me.towdium.jecharacters.utils.Match");
                        // Match.contains(String, CharSequence) → boolean
                        containsHandle = MethodHandles.publicLookup().findStatic(
                                matchClass,
                                "contains",
                                MethodType.methodType(boolean.class, String.class, CharSequence.class)
                        );
                        available = true;
                        ResourceObserverMod.LOGGER.info(
                                "[ResourceObserver] JEC (Just Enough Characters) integration enabled — pinyin search active");
                    } catch (ClassNotFoundException e) {
                        available = false;
                        ResourceObserverMod.LOGGER.info(
                                "[ResourceObserver] JEC not found, using built-in pinyin matcher");
                    } catch (Exception e) {
                        available = false;
                        ResourceObserverMod.LOGGER.warn(
                                "[ResourceObserver] JEC found but integration failed: {}", e.getMessage());
                    }
                }
            }
        }
        return available;
    }

    /**
     * 使用 JEC 的拼音引擎判断 text 中是否包含 query 的拼音匹配。
     * <p>
     * 调用前必须确保 {@link #isAvailable()} 返回 true。
     * 内部调用 {@code Match.contains(text, query)}，该方法支持：
     * <ul>
     *   <li>普通子串匹配</li>
     *   <li>拼音全拼匹配（如 "tiejian" 匹配 "铁剑"）</li>
     *   <li>拼音首字母匹配（如 "tj" 匹配 "铁剑"）</li>
     *   <li>混合匹配（如 "铁j" 匹配 "铁剑"）</li>
     *   <li>模糊音匹配（zh/z、sh/s、ch/c 等，取决于 JEC 配置）</li>
     * </ul>
     *
     * @param text  待搜索的文本（如物品显示名称）
     * @param query 搜索关键词
     * @return true 表示匹配
     */
    public static boolean contains(String text, String query) {
        try {
            return (boolean) containsHandle.invokeExact(text, (CharSequence) query);
        } catch (Throwable e) {
            return false;
        }
    }
}

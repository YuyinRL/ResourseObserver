package com.yuyinrl.resourceobserver.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * 网络数据包编解码工具类 —— 提供 Payload 序列化/反序列化中复用的辅助方法。
 * <p>
 * 主要用于统一处理可选字符串（optional string）的编解码模式，
 * 避免在多个 Payload 类中重复编写 null/blank 检查和条件读写逻辑。
 */
public final class PayloadCodecUtils {
    private PayloadCodecUtils() {
    }

    /**
     * 判断字符串是否为 null 或空白。
     */
    public static boolean isNullOrBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * 从字节缓冲区读取可选字符串。
     * 先读取一个 boolean 标志位，为 true 时读取 UTF 字符串，否则返回空串。
     *
     * @param buf    字节缓冲区
     * @param maxLen UTF 字符串最大长度
     * @return 解码后的字符串，无值时返回空串
     */
    public static String readOptionalString(FriendlyByteBuf buf, int maxLen) {
        return buf.readBoolean() ? buf.readUtf(maxLen) : "";
    }

    /**
     * 将可选字符串写入字节缓冲区。
     * 先写入一个 boolean 标志位，为 true 时继续写入 UTF 字符串。
     *
     * @param buf    字节缓冲区
     * @param value  要写入的字符串（可为 null 或空白）
     * @param maxLen UTF 字符串最大长度
     */
    public static void writeOptionalString(FriendlyByteBuf buf, String value, int maxLen) {
        boolean hasValue = !isNullOrBlank(value);
        buf.writeBoolean(hasValue);
        if (hasValue) {
            buf.writeUtf(value, maxLen);
        }
    }

    /**
     * 截断 UTF 字符串至指定最大长度，防止超长文本导致数据包溢出。
     *
     * @param text      原始文本（可为 null）
     * @param maxLength 最大字符数
     * @return 截断后的文本，null/空白时返回空串
     */
    public static String clampUtf(String text, int maxLength) {
        if (isNullOrBlank(text)) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}

package com.yuyinrl.resourceobserver.world.block.entity;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * 网络标识封装 —— Observer 中 {@code networkId} 的字符串协议为
 * {@code "<blockId>@<posLong>"}，例如 {@code "ae2:controller@123456789"}。
 *
 * <p>{@code blockId} 为目标方块的注册名（如 {@code "ae2:controller"}），
 * {@code posLong} 为 {@link BlockPos#asLong()} 的十进制编码。
 *
 * <p>此 record 把 {@link #parse(String)} / {@link #format(String, BlockPos)} 集中到一处，
 * 供 {@link ObserverBlockEntity}、{@link com.yuyinrl.resourceobserver.world.item.ResourceTerminalItem}
 * 等多处调用，避免散落的 {@code lastIndexOf('@')} 解析逻辑。
 *
 * @param blockId 目标方块的注册名
 * @param pos     目标方块的世界坐标
 */
public record NetworkRef(String blockId, BlockPos pos) {

    /**
     * 解析 networkId 字符串，失败返回 null。
     */
    @Nullable
    public static NetworkRef parse(String networkId) {
        if (networkId == null) return null;
        int atIdx = networkId.lastIndexOf('@');
        if (atIdx <= 0) return null;
        try {
            BlockPos pos = BlockPos.of(Long.parseLong(networkId.substring(atIdx + 1)));
            String blockId = networkId.substring(0, atIdx);
            return new NetworkRef(blockId, pos);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 仅解析坐标部分，不需要 blockId 时使用。失败返回 null。
     */
    @Nullable
    public static BlockPos extractPos(String networkId) {
        NetworkRef ref = parse(networkId);
        return ref == null ? null : ref.pos();
    }

    /**
     * 用 blockId 与 pos 拼装 networkId 字符串。
     */
    public static String format(String blockId, BlockPos pos) {
        return blockId + "@" + pos.asLong();
    }

    /** 格式化当前 NetworkRef 为 networkId 字符串。 */
    public String asNetworkId() {
        return format(blockId, pos);
    }
}

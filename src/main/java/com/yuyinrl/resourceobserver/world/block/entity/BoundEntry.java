package com.yuyinrl.resourceobserver.world.block.entity;

/**
 * 网络绑定条目 —— 描述一个 Observer 已绑定的资源网络。
 *
 * @param networkType   网络类型字符串（"AE2_ITEMS" / "FLUX_ENERGY" 等）
 * @param networkId     网络唯一标识，格式 "blockId@posLong"，可由 {@link NetworkRef#parse(String)} 解析
 * @param targetBlockId 目标方块的注册名（{@link net.minecraft.resources.ResourceLocation#toString()}）
 */
public record BoundEntry(String networkType, String networkId, String targetBlockId) {
}

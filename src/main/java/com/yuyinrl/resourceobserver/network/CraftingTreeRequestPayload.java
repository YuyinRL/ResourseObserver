package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：请求合成树详情。
 *
 * @param observerPos 调用上下文（用于权限/位置校验）
 * @param treeId      在客户端 ObserverDataPayload.CraftingJobSnapshot 中拿到的 treeId
 */
public record CraftingTreeRequestPayload(BlockPos observerPos, String treeId)
        implements CustomPacketPayload {

    public static final Type<CraftingTreeRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "crafting_tree_request"));

    public static final StreamCodec<FriendlyByteBuf, CraftingTreeRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CraftingTreeRequestPayload decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            String tid = PayloadCodecUtils.readOptionalString(buf, 64);
            return new CraftingTreeRequestPayload(pos, tid);
        }

        @Override
        public void encode(FriendlyByteBuf buf, CraftingTreeRequestPayload p) {
            buf.writeBlockPos(p.observerPos());
            PayloadCodecUtils.writeOptionalString(buf, p.treeId(), 64);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

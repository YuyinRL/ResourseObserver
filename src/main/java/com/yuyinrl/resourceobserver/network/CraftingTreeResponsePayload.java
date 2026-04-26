package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.integration.CraftingTreeNode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：合成树详情响应。
 *
 * @param treeId           对应请求的 treeId
 * @param progressFraction 当前进度（0..1）；review 阶段为 0
 * @param root             树根，可能为 null（树已过期或不存在）
 */
public record CraftingTreeResponsePayload(String treeId, double progressFraction, CraftingTreeNode root)
        implements CustomPacketPayload {

    public static final Type<CraftingTreeResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "crafting_tree_response"));

    public static final StreamCodec<FriendlyByteBuf, CraftingTreeResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CraftingTreeResponsePayload decode(FriendlyByteBuf buf) {
            String tid = PayloadCodecUtils.readOptionalString(buf, 64);
            double frac = buf.readDouble();
            CraftingTreeNode root = CraftingTreeCodec.readOptional(buf);
            return new CraftingTreeResponsePayload(tid, frac, root);
        }

        @Override
        public void encode(FriendlyByteBuf buf, CraftingTreeResponsePayload p) {
            PayloadCodecUtils.writeOptionalString(buf, p.treeId(), 64);
            buf.writeDouble(p.progressFraction());
            CraftingTreeCodec.writeOptional(buf, p.root());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端的定期刷新请求数据包。
 * 当资源终端 GUI 打开时，客户端周期性发送此请求以获取最新数据。
 *
 * @param observerPos 绑定的观察者方块坐标
 * @param chartWindow 当前选择的图表时间窗口
 * @param chartScope  图表作用域（全局/单物品）
 * @param scopeItemId 单物品作用域时的物品 ID
 */
public record ObserverRefreshRequestPayload(
        BlockPos observerPos,
        ChartWindow chartWindow,
        ChartScope chartScope,
        String scopeItemId
) implements CustomPacketPayload {
    public static final Type<ObserverRefreshRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "observer_refresh_request"));

    public static final StreamCodec<FriendlyByteBuf, ObserverRefreshRequestPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ObserverRefreshRequestPayload decode(FriendlyByteBuf buf) {
                    BlockPos observerPos = buf.readBlockPos();
                    ChartWindow chartWindow = ChartWindow.fromId(buf.readVarInt());
                    ChartScope chartScope = ChartScope.fromId(buf.readVarInt());
                    String scopeItemId = PayloadCodecUtils.readOptionalString(buf, 256);
                    return new ObserverRefreshRequestPayload(observerPos, chartWindow, chartScope, scopeItemId);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ObserverRefreshRequestPayload payload) {
                    buf.writeBlockPos(payload.observerPos());
                    buf.writeVarInt(payload.chartWindow().id());
                    buf.writeVarInt(payload.chartScope().id());
                    PayloadCodecUtils.writeOptionalString(buf, payload.scopeItemId(), 256);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

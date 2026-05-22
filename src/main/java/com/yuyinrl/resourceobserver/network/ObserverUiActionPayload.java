package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端 → 服务端的 UI 操作数据包。
 * 当玩家在资源终端 GUI 中执行操作（如关注物品、切换排序、管理分组等）时发送。
 * 服务端接收后将操作应用到 PlayerUiPrefsSavedData 中，然后回传最新数据。
 *
 * @param observerPos 观察者方块坐标
 * @param actionType  UI 操作类型
 * @param itemId      操作关联的单个物品 ID（可选）
 * @param itemIds     操作关联的多个物品 ID 列表（如批量分组分配）
 * @param actionValue 操作附加值（如分组名称、排序模式 ID 等）
 * @param chartWindow 当前图表窗口（操作后回传数据时使用）
 * @param chartScope  当前图表作用域
 * @param scopeItemId 当前作用域物品 ID
 */
public record ObserverUiActionPayload(
        BlockPos observerPos,
        UiActionType actionType,
        String itemId,
        List<String> itemIds,
        String actionValue,
        ChartWindow chartWindow,
        ChartScope chartScope,
        String scopeItemId
) implements CustomPacketPayload {
    public static final Type<ObserverUiActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "observer_ui_action"));

    public static final StreamCodec<FriendlyByteBuf, ObserverUiActionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ObserverUiActionPayload decode(FriendlyByteBuf buf) {
                    BlockPos observerPos = buf.readBlockPos();
                    UiActionType actionType = UiActionType.fromId(buf.readVarInt());
                    String itemId = PayloadCodecUtils.readOptionalString(buf, 512);
                    int itemIdsCount = buf.readVarInt();
                    List<String> itemIds = new ArrayList<>(itemIdsCount);
                    for (int i = 0; i < itemIdsCount; i++) {
                        itemIds.add(buf.readUtf(512));
                    }
                    String actionValue = PayloadCodecUtils.readOptionalString(buf, 256);
                    ChartWindow chartWindow = ChartWindow.fromId(buf.readVarInt());
                    ChartScope chartScope = ChartScope.fromId(buf.readVarInt());
                    String scopeItemId = PayloadCodecUtils.readOptionalString(buf, 512);
                    return new ObserverUiActionPayload(observerPos, actionType, itemId, itemIds, actionValue, chartWindow, chartScope, scopeItemId);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ObserverUiActionPayload payload) {
                    buf.writeBlockPos(payload.observerPos());
                    buf.writeVarInt(payload.actionType().id());
                    PayloadCodecUtils.writeOptionalString(buf, payload.itemId(), 512);

                    List<String> itemIds = payload.itemIds() == null ? List.of() : payload.itemIds();
                    buf.writeVarInt(itemIds.size());
                    for (String id : itemIds) {
                        buf.writeUtf(id == null ? "" : id, 512);
                    }

                    PayloadCodecUtils.writeOptionalString(buf, payload.actionValue(), 256);

                    buf.writeVarInt(payload.chartWindow().id());
                    buf.writeVarInt(payload.chartScope().id());
                    PayloadCodecUtils.writeOptionalString(buf, payload.scopeItemId(), 512);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

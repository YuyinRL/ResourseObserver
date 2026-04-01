package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

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
                    String itemId = buf.readBoolean() ? buf.readUtf(512) : "";
                    int itemIdsCount = buf.readVarInt();
                    List<String> itemIds = new ArrayList<>(itemIdsCount);
                    for (int i = 0; i < itemIdsCount; i++) {
                        itemIds.add(buf.readUtf(512));
                    }
                    String actionValue = buf.readBoolean() ? buf.readUtf(256) : "";
                    ChartWindow chartWindow = ChartWindow.fromId(buf.readVarInt());
                    ChartScope chartScope = ChartScope.fromId(buf.readVarInt());
                    String scopeItemId = buf.readBoolean() ? buf.readUtf(512) : "";
                    return new ObserverUiActionPayload(observerPos, actionType, itemId, itemIds, actionValue, chartWindow, chartScope, scopeItemId);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ObserverUiActionPayload payload) {
                    buf.writeBlockPos(payload.observerPos());
                    buf.writeVarInt(payload.actionType().id());
                    boolean hasItemId = payload.itemId() != null && !payload.itemId().isBlank();
                    buf.writeBoolean(hasItemId);
                    if (hasItemId) {
                        buf.writeUtf(payload.itemId(), 512);
                    }

                    List<String> itemIds = payload.itemIds() == null ? List.of() : payload.itemIds();
                    buf.writeVarInt(itemIds.size());
                    for (String id : itemIds) {
                        buf.writeUtf(id == null ? "" : id, 512);
                    }

                    boolean hasActionValue = payload.actionValue() != null && !payload.actionValue().isBlank();
                    buf.writeBoolean(hasActionValue);
                    if (hasActionValue) {
                        buf.writeUtf(payload.actionValue(), 256);
                    }

                    buf.writeVarInt(payload.chartWindow().id());
                    buf.writeVarInt(payload.chartScope().id());
                    boolean hasScopeItemId = payload.scopeItemId() != null && !payload.scopeItemId().isBlank();
                    buf.writeBoolean(hasScopeItemId);
                    if (hasScopeItemId) {
                        buf.writeUtf(payload.scopeItemId(), 512);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

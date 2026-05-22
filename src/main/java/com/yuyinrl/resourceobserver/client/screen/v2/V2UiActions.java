package com.yuyinrl.resourceobserver.client.screen.v2;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.network.ObserverUiActionPayload;
import com.yuyinrl.resourceobserver.network.UiActionType;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * V2 客户端发送 UI Action 的辅助器。
 *
 * <p>从 {@link LatestSnapshotProvider} 读取 observerPos 与 chart 窗口配置，
 * 与 ModernUI 的 {@code ResourceTerminalFragment.sendUiAction} 行为对齐：
 * 缺省 chartWindow=DEFAULT、scope=GLOBAL（V2 暂未跟踪选中物品）。
 */
public final class V2UiActions {

    private V2UiActions() {}

    public static void send(UiActionType actionType, String itemId, String actionValue) {
        send(actionType, itemId, List.of(), actionValue);
    }

    public static void send(UiActionType actionType, String itemId,
                            List<String> itemIds, String actionValue) {
        ObserverDataPayload payload = LatestSnapshotProvider.getPayload();
        if (payload == null) return;
        try {
            PacketDistributor.sendToServer(new ObserverUiActionPayload(
                    payload.observerPos(),
                    actionType,
                    itemId == null ? "" : itemId,
                    itemIds == null ? List.of() : itemIds,
                    actionValue == null ? "" : actionValue,
                    ChartWindow.HOUR_1H_1M,
                    ChartScope.GLOBAL,
                    ""));
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.debug("V2 UI action failed", e);
        }
    }
}

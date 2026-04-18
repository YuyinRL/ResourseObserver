package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.PowerExternalSelectionCache;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModelMapper;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModelMapper;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModelMapper;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * ViewModel 桥接层 —— 持有当前 ObserverDataPayload 并管理所有页面的 ViewModel。
 * 当数据更新时重建 ViewModel 并通知监听器。
 */
public final class ViewModelBridge {

    /** 数据变化监听器 */
    public interface DataChangeListener {
        void onDataChanged();
    }

    private ObserverDataPayload payload;
    private OverviewViewModel overviewViewModel;
    private StorageNetworkViewModel storageViewModel;
    private PowerNetworkViewModel powerViewModel;

    private String storageSelectedNodeId;
    private boolean storageAlertFilterActive;

    /**
     * EMA smoothed buffer state per item — survives across rebuilds for stable display.
     * Each entry: [0] = smoothed seconds, [1] = anchor system time (ms).
     */
    private final Map<String, double[]> bufferEma = new HashMap<>();

    // External group selection state (power page)
    private final LinkedHashSet<String> selectedExternalGroupIds = new LinkedHashSet<>();
    private String externalSelectionCacheKey = "";

    private final List<DataChangeListener> listeners = new ArrayList<>();

    public ViewModelBridge(ObserverDataPayload initialPayload) {
        this.payload = initialPayload;
        rebuildAllViewModels();
    }

    /**
     * 应用新的服务端数据载荷，重建所有 ViewModel 并通知监听器。
     * 仅当 ViewModel 实际发生变化时才触发通知，避免不必要的 UI 重建。
     */
    public void applyPayload(ObserverDataPayload newPayload) {
        this.payload = newPayload;

        OverviewViewModel oldOverview = overviewViewModel;
        StorageNetworkViewModel oldStorage = storageViewModel;
        PowerNetworkViewModel oldPower = powerViewModel;

        rebuildAllViewModels();
        refreshExternalSelectionCacheKey();

        if (!Objects.equals(overviewViewModel, oldOverview)
                || !Objects.equals(storageViewModel, oldStorage)
                || !Objects.equals(powerViewModel, oldPower)) {
            notifyListeners();
        }
    }

    /**
     * 更新存储页面的本地筛选状态并重建存储 ViewModel。
     */
    public void updateStorageFilter(String selectedNodeId, boolean alertFilterActive) {
        this.storageSelectedNodeId = selectedNodeId;
        this.storageAlertFilterActive = alertFilterActive;
        storageViewModel = StorageNetworkViewModelMapper.fromPayload(
                payload, storageSelectedNodeId, storageAlertFilterActive, bufferEma);
        notifyListeners();
    }

    private void rebuildAllViewModels() {
        overviewViewModel = OverviewViewModelMapper.fromPayload(payload);
        storageViewModel = StorageNetworkViewModelMapper.fromPayload(
                payload, storageSelectedNodeId, storageAlertFilterActive, bufferEma);
        powerViewModel = PowerNetworkViewModelMapper.fromPayload(payload);
    }

    // ===================== Getters =====================

    public ObserverDataPayload getPayload() {
        return payload;
    }

    public BlockPos getObserverPos() {
        return payload.observerPos();
    }

    public OverviewViewModel getOverviewViewModel() {
        return overviewViewModel;
    }

    public StorageNetworkViewModel getStorageViewModel() {
        return storageViewModel;
    }

    public PowerNetworkViewModel getPowerViewModel() {
        return powerViewModel;
    }

    public String getStorageSelectedNodeId() {
        return storageSelectedNodeId;
    }

    public boolean isStorageAlertFilterActive() {
        return storageAlertFilterActive;
    }

    /** EMA 锚点 map — 供倒计时 tick 直接读取（只读；写入由 mapper 在数据刷新时完成）。 */
    public Map<String, double[]> getBufferEma() {
        return bufferEma;
    }

    // ===================== External Group Selection (Power Page) =====================

    public Set<String> getSelectedExternalGroupIds() {
        return Collections.unmodifiableSet(selectedExternalGroupIds);
    }

    /**
     * 切换外部存储组选择 —— 若所有 relatedIds 已选中则反选，否则全选。
     * 完全移植自 ResourceTerminalScreen.handlePowerNetworkClick 中的切换逻辑。
     */
    public void toggleExternalGroupSelection(List<String> relatedIds) {
        if (relatedIds == null || relatedIds.isEmpty()) return;
        boolean allSelected = selectedExternalGroupIds.containsAll(relatedIds);
        if (allSelected) {
            selectedExternalGroupIds.removeAll(relatedIds);
        } else {
            selectedExternalGroupIds.addAll(relatedIds);
        }
        persistSelectedExternalGroups();
        notifyListeners();
    }

    /**
     * 清理无效的外部组 ID —— 移除在当前 DebugSnapshot 中不存在的 extId。
     * 移植自 ResourceTerminalScreen.sanitizeSelectedExternalGroups。
     */
    public void sanitizeSelectedExternalGroups() {
        if (selectedExternalGroupIds.isEmpty()) return;
        if (powerViewModel == null || powerViewModel.debugSnapshot() == null) {
            selectedExternalGroupIds.clear();
            persistSelectedExternalGroups();
            return;
        }
        LinkedHashSet<String> validIds = new LinkedHashSet<>();
        for (PowerNetworkViewModel.ExternalGroup group : powerViewModel.debugSnapshot().externalGroups()) {
            validIds.add(group.extId());
        }
        if (selectedExternalGroupIds.retainAll(validIds)) {
            persistSelectedExternalGroups();
        }
    }

    private void refreshExternalSelectionCacheKey() {
        String nextKey = buildExternalSelectionCacheKey(payload != null ? payload.observerPos() : null);
        if (nextKey.equals(externalSelectionCacheKey)) return;
        externalSelectionCacheKey = nextKey;
        selectedExternalGroupIds.clear();
        selectedExternalGroupIds.addAll(PowerExternalSelectionCache.loadSelection(externalSelectionCacheKey));
    }

    private void persistSelectedExternalGroups() {
        if (externalSelectionCacheKey == null || externalSelectionCacheKey.isBlank()) return;
        PowerExternalSelectionCache.saveSelection(externalSelectionCacheKey, selectedExternalGroupIds);
    }

    private String buildExternalSelectionCacheKey(BlockPos observerPos) {
        if (observerPos == null) return "";
        Minecraft mc = Minecraft.getInstance();
        String sessionId = "unknown";
        ServerData serverData = mc.getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
            sessionId = "server:" + serverData.ip.trim().toLowerCase(Locale.ROOT);
        } else if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            String levelName = mc.getSingleplayerServer().getWorldData().getLevelName();
            sessionId = (levelName != null && !levelName.isBlank())
                    ? "singleplayer:" + levelName.trim().toLowerCase(Locale.ROOT)
                    : "singleplayer";
        }
        String dimension = mc.level != null
                ? mc.level.dimension().location().toString()
                : "unknown";
        return sessionId + "|" + dimension + "|"
                + observerPos.getX() + "," + observerPos.getY() + "," + observerPos.getZ();
    }

    // ===================== Listener =====================

    public void addListener(DataChangeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(DataChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (int i = 0, n = listeners.size(); i < n; i++) {
            listeners.get(i).onDataChanged();
        }
    }
}

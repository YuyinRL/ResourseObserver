package com.yuyinrl.resourceobserver.client.screen.v2;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.power.PowerSnapshot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla V2 使用的最新载荷缓存。
 *
 * <p>ModernUI 桥接层收到数据后写入此处，V2 Screen 打开时即可复用最近一次载荷。</p>
 */
public final class LatestSnapshotProvider {

    private static volatile State state = State.empty();
    private static volatile boolean powerDonutUsedOnly;
    private static long nextVersion;

    private LatestSnapshotProvider() {
    }

    /** 写入最新载荷与倒计时 EMA 状态。 */
    public static synchronized void set(ObserverDataPayload payload, Map<String, double[]> bufferEma) {
        nextVersion++;
        state = new State(nextVersion, payload, deepCopy(bufferEma),
                state.overviewSnapshot(), state.overviewViewModel(), state.powerSnapshot(),
                state.powerNetworkViewModel(), state.storageNetworkViewModel());
    }

    /** 写入最新 Overview 领域快照。 */
    public static synchronized void setOverviewSnapshot(OverviewSnapshot snapshot) {
        nextVersion++;
        state = new State(nextVersion, state.payload(), state.bufferEma(),
                snapshot, state.overviewViewModel(), state.powerSnapshot(),
                state.powerNetworkViewModel(), state.storageNetworkViewModel());
    }

    /** 写入最新 Overview 视图模型。 */
    public static synchronized void setOverviewViewModel(OverviewViewModel viewModel) {
        nextVersion++;
        state = new State(nextVersion, state.payload(), state.bufferEma(),
                state.overviewSnapshot(), viewModel, state.powerSnapshot(),
                state.powerNetworkViewModel(), state.storageNetworkViewModel());
    }

    /** 写入最新 Power 领域快照。 */
    public static synchronized void setPowerSnapshot(PowerSnapshot snapshot) {
        nextVersion++;
        state = new State(nextVersion, state.payload(), state.bufferEma(),
                state.overviewSnapshot(), state.overviewViewModel(), snapshot,
                state.powerNetworkViewModel(), state.storageNetworkViewModel());
    }

    /** 写入最新 Power 视图模型。 */
    public static synchronized void setPowerNetworkViewModel(PowerNetworkViewModel viewModel) {
        nextVersion++;
        state = new State(nextVersion, state.payload(), state.bufferEma(),
                state.overviewSnapshot(), state.overviewViewModel(), state.powerSnapshot(), viewModel,
                state.storageNetworkViewModel());
    }

    /** 写入最新 Storage 视图模型。 */
    public static synchronized void setStorageNetworkViewModel(StorageNetworkViewModel viewModel) {
        nextVersion++;
        state = new State(nextVersion, state.payload(), state.bufferEma(),
                state.overviewSnapshot(), state.overviewViewModel(), state.powerSnapshot(),
                state.powerNetworkViewModel(), viewModel);
    }

    /** 当前完整状态快照。 */
    public static State get() {
        return state;
    }

    /** 最近的原始载荷；没有数据时返回 null。 */
    public static ObserverDataPayload getPayload() {
        return state.payload();
    }

    /** 最近的 EMA 深拷贝，调用方可安全传给 SnapshotBuilder 修改。 */
    public static Map<String, double[]> copyBufferEma() {
        return state.copyBufferEma();
    }

    /** 最近的 Overview 快照；没有数据时返回 null。 */
    public static OverviewSnapshot getOverviewSnapshot() {
        return state.overviewSnapshot();
    }

    /** 最近的 Overview 视图模型；没有数据时返回 null。 */
    public static OverviewViewModel getOverviewViewModel() {
        return state.overviewViewModel();
    }

    /** 最近的 Power 快照；没有数据时返回 null。 */
    public static PowerSnapshot getPowerSnapshot() {
        return state.powerSnapshot();
    }

    /** 最近的 Power 视图模型；没有数据时返回 null。 */
    public static PowerNetworkViewModel getPowerNetworkViewModel() {
        return state.powerNetworkViewModel();
    }

    /** 最近的 Storage 视图模型；没有数据时返回 null。 */
    public static StorageNetworkViewModel getStorageNetworkViewModel() {
        return state.storageNetworkViewModel();
    }

    /** V2 Power 甜甜圈是否只显示用电分布。 */
    public static boolean isPowerDonutUsedOnly() {
        return powerDonutUsedOnly;
    }

    /** 设置 V2 Power 甜甜圈显示模式。 */
    public static void setPowerDonutUsedOnly(boolean usedOnly) {
        powerDonutUsedOnly = usedOnly;
    }

    private static Map<String, double[]> deepCopy(Map<String, double[]> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, double[]> copy = new HashMap<>();
        for (Map.Entry<String, double[]> entry : source.entrySet()) {
            double[] value = entry.getValue();
            copy.put(entry.getKey(), value == null ? new double[0] : value.clone());
        }
        return Collections.unmodifiableMap(copy);
    }

    /** 不可变状态对象。 */
    public record State(
            long version,
            ObserverDataPayload payload,
            Map<String, double[]> bufferEma,
            OverviewSnapshot overviewSnapshot,
            OverviewViewModel overviewViewModel,
            PowerSnapshot powerSnapshot,
            PowerNetworkViewModel powerNetworkViewModel,
            StorageNetworkViewModel storageNetworkViewModel
    ) {
        private static State empty() {
            return new State(0L, null, Map.of(), null, null, null, null, null);
        }

        public Map<String, double[]> copyBufferEma() {
            Map<String, double[]> copy = new HashMap<>();
            for (Map.Entry<String, double[]> entry : bufferEma.entrySet()) {
                double[] value = entry.getValue();
                copy.put(entry.getKey(), value == null ? new double[0] : value.clone());
            }
            return copy;
        }
    }
}



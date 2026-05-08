package com.yuyinrl.resourceobserver.world.block.entity;

import appeng.api.AECapabilities;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * AE2 网络 Grid 解析器 —— 将散落在 ObserverBlockEntity 上的 AE2 拓扑工具与
 * 容量告警限流逻辑收敛到同一个对象上，保证主实体类的"瘦"职责。
 * <p>
 * 状态：仅持有容量告警限流相关的 Map，Grid 解析本身是无状态的。
 */
public final class Ae2GridResolver {

    /** 容量告警日志最小间隔（纳秒），同一网络 5 分钟内最多输出一次 WARN */
    private static final long CAPACITY_WARN_INTERVAL_NS = 5L * 60L * 1_000_000_000L;

    /** 容量告警签名（networkId -> last signature），避免重复刷日志 */
    private final Map<String, String> capacityWarnSignatureMap = new HashMap<>();
    /** 容量告警时间戳（networkId -> 上次输出 WARN 的 System.nanoTime()），用于频率限制 */
    private final Map<String, Long> capacityWarnTimestampMap = new HashMap<>();

    /**
     * 解析目标方块所在的 AE2 网络 Grid。
     * <p>
     * AE2 节点具有方向性，需遍历所有 6 个面方向（上/下/东/西/南/北）
     * 尝试获取有效的 GridNode。找到第一个非 null 节点后停止遍历。
     * getGrid() 可能在网络处于无效/拆卸状态时抛出 IllegalStateException，
     * 此时安全返回 null。
     */
    @Nullable
    public IGrid resolveAt(Level level, BlockPos targetPos) {
        if (level == null) {
            return null;
        }
        IInWorldGridNodeHost host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, targetPos, null);
        if (host == null) {
            return null;
        }
        IGridNode node = null;
        for (Direction dir : Direction.values()) {
            node = host.getGridNode(dir);
            if (node != null) {
                break;
            }
        }
        if (node == null) {
            return null;
        }
        try {
            return node.getGrid();
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    /** 通过 networkId 解析对应的 AE2 Grid。 */
    @Nullable
    public IGrid resolveForNetwork(Level level, String networkId) {
        if (level == null) {
            return null;
        }
        BlockPos targetPos = NetworkRef.extractPos(networkId);
        if (targetPos == null || !level.isLoaded(targetPos)) {
            return null;
        }
        return resolveAt(level, targetPos);
    }

    /** 判断两个 networkId 是否对应同一个 AE2 Grid（同网络重复绑定检测）。 */
    public boolean isSameGrid(Level level, String leftNetworkId, String rightNetworkId) {
        IGrid leftGrid = resolveForNetwork(level, leftNetworkId);
        if (leftGrid == null) {
            return false;
        }
        IGrid rightGrid = resolveForNetwork(level, rightNetworkId);
        return rightGrid != null && leftGrid == rightGrid;
    }

    /**
     * 报告 AE2 网络容量探针的状态。当探针不可用或不可靠时按 networkId 维度
     * 限流写出 WARN 日志，避免重复刷屏；正常状态会清掉对应限流痕迹。
     */
    public void reportCapacityIssue(String networkId, BlockPos targetPos,
                                    ObserverBlockEntity.Ae2ReadResult readResult) {
        Ae2CellCapacityMetrics metrics = readResult.cellCapacityMetrics();
        if (!metrics.available() || !metrics.reliable()) {
            String signature = readResult.debugInfo();
            String previous = capacityWarnSignatureMap.put(networkId, signature);
            if (!signature.equals(previous)) {
                long now = System.nanoTime();
                Long lastWarn = capacityWarnTimestampMap.get(networkId);
                if (lastWarn == null || (now - lastWarn) >= CAPACITY_WARN_INTERVAL_NS) {
                    capacityWarnTimestampMap.put(networkId, now);
                    ResourceObserverMod.LOGGER.warn(
                            "[ResourceObserver] AE2 capacity probe degraded at {} network={}"
                                    + " reliable={} available={}",
                            targetPos.toShortString(),
                            networkId,
                            metrics.reliable(),
                            metrics.available()
                    );
                }
                ResourceObserverMod.LOGGER.debug(
                        "[ResourceObserver] AE2 capacity probe detail at {} network={} info={}",
                        targetPos.toShortString(),
                        networkId,
                        signature
                );
            }
            return;
        }
        capacityWarnSignatureMap.remove(networkId);
        capacityWarnTimestampMap.remove(networkId);
    }

    /** Flux 采样器使用的访问点：清理 AE2 容量告警签名缓存（Flux 切换后调用）。 */
    public void clearCapacityWarnSignature(String networkId) {
        capacityWarnSignatureMap.remove(networkId);
        capacityWarnTimestampMap.remove(networkId);
    }
}

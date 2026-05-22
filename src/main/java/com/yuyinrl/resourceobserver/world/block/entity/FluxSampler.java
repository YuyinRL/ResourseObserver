package com.yuyinrl.resourceobserver.world.block.entity;

import com.yuyinrl.resourceobserver.integration.FluxNetworksIntegration;
import com.yuyinrl.resourceobserver.world.block.entity.BindingStats;
import com.yuyinrl.resourceobserver.world.block.entity.BoundEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.Map;

/**
 * Flux Networks 采样器 —— 把原本在 {@link ObserverBlockEntity#sampleAll()} 中
 * 内嵌约 70 行的 {@code FLUX_ENERGY} 分支整体抽出。
 *
 * <p>采样优先走 Flux Networks 原生 API；若不可用或返回 null，则回退到
 * NeoForge 的 {@link IEnergyStorage} 通用能力。无论走哪条路径，
 * 都会清理该网络在 dataStore 中的 AE2 容量指标和容量告警签名缓存。
 */
final class FluxSampler {

    /**
     * 一次 Flux 采样的全部产物，由 {@link #sample} 返回，调用方自行写回 OBE 的状态字段。
     *
     * @param newStats          更新后的 BindingStats（已合入本 tick 的 stored / capacity / input / output）
     * @param itemDeltaSnapshot 本 tick 增量快照（用于 ItemDeltas 通道，仅含 input/output 两键）
     * @param itemAmountSnapshot 全量快照（用于 ItemAmounts 通道，含设备数与缓冲量）
     * @param debugInfo          单行调试串（写入 {@code debugInfoMap}）
     */
    record SampleOutput(
            BindingStats newStats,
            Map<String, Long> itemDeltaSnapshot,
            Map<String, Long> itemAmountSnapshot,
            String debugInfo
    ) {
    }

    /**
     * 对单个 Flux/能量绑定执行一次采样。
     * <p>
     * 优先调用 Flux Networks 原生 API；若 Mod 不可用或返回 null，则降级到 NeoForge 通用 IEnergyStorage 能力。
     * 采样完成后会清理该网络在 AE2 通道上的容量指标与告警签名缓存，避免脏数据。
     *
     * @param be          所属 ObserverBlockEntity（用于获取 level、写回 fluxStore/dataStore）
     * @param binding     当前绑定条目
     * @param targetPos   被采样的目标方块位置
     * @param oldStats    上一 tick 的统计（可作为初值）
     * @return 本次采样的输出快照
     */
    SampleOutput sample(ObserverBlockEntity be, BoundEntry binding, BlockPos targetPos, BindingStats oldStats) {
        Level level = be.getLevel();
        BindingStats newStats;
        Map<String, Long> itemDeltaSnapshot = Map.of();
        Map<String, Long> itemAmountSnapshot = Map.of();
        String debugInfo;

        if (FluxNetworksIntegration.isAvailable()) {
            BlockEntity targetBe = level.getBlockEntity(targetPos);
            FluxNetworksIntegration.FluxSampleResult fluxResult = FluxNetworksIntegration.readFluxNetwork(targetBe);
            if (fluxResult != null) {
                be.fluxStore.record(binding.networkId(), fluxResult);
                long inputPerTick = fluxResult.energyInput();
                long outputPerTick = fluxResult.energyOutput();
                long capacity = fluxResult.totalMaxEnergyStorage();
                if (capacity <= 0L) {
                    capacity = fluxResult.totalBuffer() + fluxResult.totalEnergy();
                }
                newStats = oldStats.withDelta(
                        fluxResult.totalEnergy(),
                        capacity,
                        inputPerTick,
                        outputPerTick
                );
                itemDeltaSnapshot = Map.of(
                        "flux.input_per_tick", inputPerTick,
                        "flux.output_per_tick", outputPerTick
                );
                itemAmountSnapshot = Map.ofEntries(
                        Map.entry("flux.input_per_tick", inputPerTick),
                        Map.entry("flux.output_per_tick", outputPerTick),
                        Map.entry("flux.energy_stored", fluxResult.totalEnergy()),
                        Map.entry("flux.total_buffer", fluxResult.totalBuffer()),
                        Map.entry("flux.max_energy_storage", fluxResult.totalMaxEnergyStorage()),
                        Map.entry("flux.plug_count", (long) fluxResult.plugCount()),
                        Map.entry("flux.point_count", (long) fluxResult.pointCount()),
                        Map.entry("flux.storage_count", (long) fluxResult.storageCount()),
                        Map.entry("flux.controller_count", (long) fluxResult.controllerCount())
                );
                debugInfo = "channel=flux.api;status=ok;target=" + targetPos.toShortString()
                        + ";network=" + fluxResult.networkName()
                        + ";id=" + fluxResult.networkId()
                        + ";input=" + inputPerTick
                        + ";output=" + outputPerTick
                        + ";stored=" + fluxResult.totalEnergy()
                        + ";buffer=" + fluxResult.totalBuffer()
                        + ";maxCapacity=" + fluxResult.totalMaxEnergyStorage()
                        + ";plugs=" + fluxResult.plugCount()
                        + ";points=" + fluxResult.pointCount()
                        + ";storages=" + fluxResult.storageCount()
                        + ";controllers=" + fluxResult.controllerCount()
                        + ";devices=" + fluxResult.devices().size();
            } else {
                be.fluxStore.remove(binding.networkId());
                newStats = sampleEnergy(level, targetPos, oldStats);
                debugInfo = "channel=flux.api;status=fallback_to_energy;target=" + targetPos.toShortString();
            }
        } else {
            be.fluxStore.remove(binding.networkId());
            newStats = sampleEnergy(level, targetPos, oldStats);
            debugInfo = "channel=neoforge.energy;target=" + targetPos.toShortString();
        }

        // Flux 采样完成后，清理该网络在 AE2 数据通道上残留的容量指标与告警签名。
        be.dataStore.clearCellCapacityMetrics(binding.networkId());
        be.clearCapacityWarnSignature(binding.networkId());

        return new SampleOutput(newStats, itemDeltaSnapshot, itemAmountSnapshot, debugInfo);
    }

    /**
     * 通用 NeoForge IEnergyStorage 能力采样（Flux API 不可用时使用）。
     * 先尝试无方向获取，失败后遍历所有方向。
     */
    private static BindingStats sampleEnergy(Level level, BlockPos targetPos, BindingStats oldStats) {
        IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, null);
        if (energy == null) {
            for (Direction dir : Direction.values()) {
                energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, dir);
                if (energy != null) break;
            }
        }
        if (energy == null) return oldStats;

        // IEnergyStorage 接口返回 int，超过 Integer.MAX_VALUE (~2.1B) 时会溢出为负数。
        // 使用 Integer.toUnsignedLong() 将 32-bit 无符号整数正确转换为 long，支持最大 ~4.29B FE。
        long stored = Integer.toUnsignedLong(energy.getEnergyStored());
        long capacity = Integer.toUnsignedLong(energy.getMaxEnergyStored());
        return oldStats.withSample(stored, capacity);
    }
}

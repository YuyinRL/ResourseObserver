package com.yuyinrl.resourceobserver.integration;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IEnergyConversionHelper;
import mekanism.api.energy.IStrictEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxDevice;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.NetworkStatistics;
import sonar.fluxnetworks.common.device.FluxStorageHandler;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Flux Networks 集成适配器 —— 从 Flux Controller 的 BlockEntity 中提取完整的网络统计和设备数据。
 * <p>
 * 所有 Flux Networks API 调用都在 try-catch 中执行，以便在 Flux Networks 未安装时安全降级。
 * 使用前应先调用 {@link #isAvailable()} 检查 Flux Networks 是否已加载。
 */
public final class FluxNetworksIntegration {
    private FluxNetworksIntegration() {
    }

    private static volatile Boolean available;

    // ========== Mekanism IStrictEnergyHandler 能力懒初始化 ==========
    /**
     * 通过反射懒获取 Mekanism 的 BlockCapability&lt;IStrictEnergyHandler, Direction&gt;。
     * Mekanism 主模组 jar 是运行期依赖（localRuntime），不在编译期 classpath 中，
     * 因此不能静态导入 mekanism.common.capabilities.Capabilities，需用反射在运行期获取。
     * 若 Mekanism 未加载，则返回 null，并不再重试。
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static volatile BlockCapability<IStrictEnergyHandler, Direction> mekanismStrictEnergyCap;
    /** true = 已尝试过解析（无论成功与否），避免重复反射 */
    private static volatile boolean mekanismCapResolved = false;

    @Nullable
    @SuppressWarnings("unchecked")
    private static BlockCapability<IStrictEnergyHandler, Direction> getMekanismStrictEnergyCapability() {
        if (!mekanismCapResolved) {
            synchronized (FluxNetworksIntegration.class) {
                if (!mekanismCapResolved) {
                    try {
                        Class<?> capsClass = Class.forName("mekanism.common.capabilities.Capabilities");
                        Field field = capsClass.getField("STRICT_ENERGY");
                        Object multiTypeCap = field.get(null);
                        Method blockMethod = multiTypeCap.getClass().getMethod("block");
                        mekanismStrictEnergyCap = (BlockCapability<IStrictEnergyHandler, Direction>) blockMethod.invoke(multiTypeCap);
                        ResourceObserverMod.LOGGER.debug("[ResourceObserver] Mekanism IStrictEnergyHandler capability resolved");
                    } catch (Exception e) {
                        mekanismStrictEnergyCap = null;
                        ResourceObserverMod.LOGGER.debug("[ResourceObserver] Mekanism capability not available: {}", e.getMessage());
                    }
                    mekanismCapResolved = true;
                }
            }
        }
        return mekanismStrictEnergyCap;
    }

    /**
     * 检测 Flux Networks 是否已加载（懒初始化，线程安全）。
     */
    public static boolean isAvailable() {
        if (available == null) {
            synchronized (FluxNetworksIntegration.class) {
                if (available == null) {
                    try {
                        Class.forName("sonar.fluxnetworks.common.device.TileFluxController");
                        available = true;
                        ResourceObserverMod.LOGGER.info("[ResourceObserver] Flux Networks integration enabled");
                    } catch (ClassNotFoundException e) {
                        available = false;
                        ResourceObserverMod.LOGGER.info("[ResourceObserver] Flux Networks not found, integration disabled");
                    }
                }
            }
        }
        return available;
    }

    /**
     * 从目标方块实体读取 Flux Networks 网络数据。
     *
     * @param blockEntity 目标方块实体（应为 TileFluxController 或其他 TileFluxDevice）
     * @return 完整的网络采样结果，如果无法读取则返回 null
     */
    @Nullable
    public static FluxSampleResult readFluxNetwork(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null || !isAvailable()) {
            return null;
        }
        try {
            if (!(blockEntity instanceof TileFluxDevice fluxDevice)) {
                return null;
            }

            FluxNetwork network = fluxDevice.getNetwork();
            if (network == null || !network.isValid()) {
                return null;
            }

            NetworkStatistics stats = network.getStatistics();
            if (stats == null) {
                return null;
            }

            // 读取网络级统计
            long energyInput = stats.energyInput;
            long energyOutput = stats.energyOutput;
            long totalBuffer = stats.totalBuffer;
            long totalEnergy = stats.totalEnergy;
            int plugCount = stats.fluxPlugCount;
            int pointCount = stats.fluxPointCount;
            int storageCount = stats.fluxStorageCount;
            int controllerCount = stats.fluxControllerCount;

            // 读取网络名称和颜色
            String networkName = network.getNetworkName();
            int networkColor = network.getNetworkColor();
            int networkId = network.getNetworkID();

            // 读取每个连接设备的快照
            List<FluxDeviceSnapshot> devices = new ArrayList<>();
            long totalMaxEnergyStorage = 0L;
            try {
                Collection<IFluxDevice> connections = network.getAllConnections();
                if (connections != null) {
                    for (IFluxDevice device : connections) {
                        try {
                            FluxDeviceType deviceType = device.getDeviceType();
                            String customName = device.getCustomName();
                            long transferChange = device.getTransferChange();
                            long transferBuffer = device.getTransferBuffer();
                            long rawLimit = device.getRawLimit();
                            long maxTransferLimit = device.getMaxTransferLimit();

                            // 尝试读取储能方块的最大容量
                            long maxEnergyStorage = 0L;
                            if (deviceType == FluxDeviceType.STORAGE && device instanceof TileFluxDevice tileDevice) {
                                try {
                                    if (tileDevice.getTransferHandler() instanceof FluxStorageHandler storageHandler) {
                                        maxEnergyStorage = storageHandler.getMaxEnergyStorage();
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                            totalMaxEnergyStorage += maxEnergyStorage;

                            // 构建位置键
                            String posKey = "";
                            BlockPos deviceBlockPos = null;
                            try {
                                GlobalPos gp = device.getGlobalPos();
                                if (gp != null) {
                                    posKey = gp.pos().toShortString();
                                    deviceBlockPos = gp.pos();
                                }
                            } catch (Exception ignored) {
                            }

                            // 探测外部能量容器（仅 PLUG/POINT 类型）
                            long extEnergyStored = 0L;
                            long extEnergyCapacity = 0L;
                            if ((deviceType == FluxDeviceType.PLUG || deviceType == FluxDeviceType.POINT)
                                    && deviceBlockPos != null) {
                                Level deviceLevel = blockEntity.getLevel();
                                if (deviceLevel != null) {
                                    long[] ext = probeAdjacentEnergy(deviceLevel, deviceBlockPos);
                                    extEnergyStored = ext[0];
                                    extEnergyCapacity = ext[1];
                                }
                            }

                            devices.add(new FluxDeviceSnapshot(
                                    deviceTypeToString(deviceType),
                                    customName != null ? customName : "",
                                    transferChange,
                                    transferBuffer,
                                    rawLimit,
                                    maxTransferLimit,
                                    maxEnergyStorage,
                                    posKey,
                                    extEnergyStored,
                                    extEnergyCapacity
                            ));
                        } catch (Exception e) {
                            ResourceObserverMod.LOGGER.debug(
                                    "[ResourceObserver] Error reading Flux device: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                ResourceObserverMod.LOGGER.debug(
                        "[ResourceObserver] Error iterating Flux connections: {}", e.getMessage());
            }

            return new FluxSampleResult(
                    energyInput, energyOutput,
                    totalBuffer, totalEnergy, totalMaxEnergyStorage,
                    plugCount, pointCount, storageCount, controllerCount,
                    networkName, networkColor, networkId,
                    devices
            );
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.debug(
                    "[ResourceObserver] Error reading Flux network: {}", e.getMessage());
            return null;
        }
    }

    private static String deviceTypeToString(FluxDeviceType type) {
        if (type == null) return "unknown";
        return switch (type) {
            case PLUG -> "plug";
            case POINT -> "point";
            case STORAGE -> "storage";
            case CONTROLLER -> "controller";
        };
    }

    /**
     * 探测指定方块位置周围 6 个方向的 IEnergyStorage 能力。
     * 返回找到的最大容量外部容器的 [energyStored, maxEnergyCapacity]。
     * 跳过 Flux Networks 自身的 TileFluxDevice 方块。
     *
     * @param level    服务端 Level
     * @param devicePos Flux 接口（Plug/Point）所在的方块坐标
     * @return long[2]，[0]=外部容器当前储能，[1]=外部容器最大容量；未找到则均为 0
     */
    private static long[] probeAdjacentEnergy(Level level, BlockPos devicePos) {
        long bestStored = 0L;
        long bestCapacity = 0L;
        BlockCapability<IStrictEnergyHandler, Direction> mekaEnergyCap = getMekanismStrictEnergyCapability();
        try {
            for (Direction dir : Direction.values()) {
                BlockPos adjacentPos = devicePos.relative(dir);
                // 跳过未加载的区块
                if (!level.isLoaded(adjacentPos)) {
                    continue;
                }
                // 跳过 Flux Networks 自身的设备方块
                BlockEntity adjacentBe = level.getBlockEntity(adjacentPos);
                if (adjacentBe instanceof TileFluxDevice) {
                    continue;
                }

                // ── 路径 1：标准 NeoForge IEnergyStorage（返回 int，上限 ~4.29B FE）──
                IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, adjacentPos, dir.getOpposite());
                if (energy == null) {
                    energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, adjacentPos, null);
                }
                if (energy != null) {
                    // IEnergyStorage 接口返回 int，容量超过 Integer.MAX_VALUE (~2.1B) 时会溢出为负数。
                    // 使用 Integer.toUnsignedLong() 正确处理 32-bit 无符号值，支持最大 ~4.29B FE。
                    long cap = Integer.toUnsignedLong(energy.getMaxEnergyStored());
                    if (cap > bestCapacity) {
                        bestCapacity = cap;
                        bestStored = Integer.toUnsignedLong(energy.getEnergyStored());
                    }
                }

                // ── 路径 2：Mekanism IStrictEnergyHandler（long Joules，支持 T 级别 FE）──
                // 仅当 Mekanism 已加载，且当前结果疑似被 int 上限截断（≤ Integer.MAX_VALUE）时才尝试。
                // 也会在 IEnergyStorage 未找到时尝试，以覆盖仅注册 Mekanism 能力的方块。
                if (mekaEnergyCap != null) {
                    try {
                        IStrictEnergyHandler mekaHandler = level.getCapability(mekaEnergyCap, adjacentPos, dir.getOpposite());
                        if (mekaHandler == null) {
                            mekaHandler = level.getCapability(mekaEnergyCap, adjacentPos, null);
                        }
                        if (mekaHandler != null) {
                            long joulesMax = 0L;
                            long joulesStored = 0L;
                            for (int ci = 0; ci < mekaHandler.getEnergyContainerCount(); ci++) {
                                joulesMax += mekaHandler.getMaxEnergy(ci);
                                joulesStored += mekaHandler.getEnergy(ci);
                            }
                            // 将 Mekanism 内部 Joules 转换为 FE
                            long feCap = joulesToFE(joulesMax);
                            long feStored = joulesToFE(joulesStored);
                            if (feCap > bestCapacity) {
                                bestCapacity = feCap;
                                bestStored = feStored;
                            }
                        }
                    } catch (Exception e) {
                        ResourceObserverMod.LOGGER.debug(
                                "[ResourceObserver] Mekanism energy probe failed at {}: {}", adjacentPos, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.debug(
                    "[ResourceObserver] Error probing adjacent energy at {}: {}", devicePos, e.getMessage());
        }
        return new long[]{bestStored, bestCapacity};
    }

    /**
     * 将 Mekanism 内部能量单位（Joules）转换为 FE（Forge Energy）。
     * 使用 Mekanism API 提供的 {@link IEnergyConversionHelper#feConversion()} 完成转换。
     * 若转换助手不可用（Mekanism 未完全初始化），则直接返回原始 Joules 值作为降级方案。
     *
     * @param joules Mekanism 内部能量值（Joules）
     * @return 对应的 FE 值
     */
    private static long joulesToFE(long joules) {
        if (joules <= 0L) return 0L;
        try {
            IEnergyConversionHelper helper = IEnergyConversionHelper.INSTANCE;
            if (helper == null) return joules; // Mekanism 尚未初始化，降级返回原值
            IEnergyConversion feConv = helper.feConversion();
            if (feConv == null || !feConv.isEnabled()) return joules;
            return feConv.convertToAsLong(joules);
        } catch (Exception e) {
            return joules; // 安全降级
        }
    }


    /**
     * Flux 网络完整采样结果。
     *
     * @param energyInput            当前每 tick 总输入（FE/t）
     * @param energyOutput           当前每 tick 总输出（FE/t）
     * @param totalBuffer            非储能设备的当前缓冲量合计（FE）—— 注意：这不是最大容量
     * @param totalEnergy            储能方块的当前储能合计（FE）
     * @param totalMaxEnergyStorage  所有储能方块的最大容量合计（FE）—— 真实的网络储能上限
     * @param plugCount              Plug（输入端）数量
     * @param pointCount             Point（输出端）数量
     * @param storageCount           Storage（储能方块）数量
     * @param controllerCount        Controller（控制器）数量
     * @param networkName            网络名称
     * @param networkColor           网络颜色（ARGB）
     * @param networkId              网络 ID
     * @param devices                所有连接设备的快照列表
     */
    public record FluxSampleResult(
            long energyInput,
            long energyOutput,
            long totalBuffer,
            long totalEnergy,
            long totalMaxEnergyStorage,
            int plugCount,
            int pointCount,
            int storageCount,
            int controllerCount,
            String networkName,
            int networkColor,
            int networkId,
            List<FluxDeviceSnapshot> devices
    ) {
    }

    /**
     * 单个 Flux 设备快照。
     *
     * @param deviceType            设备类型（plug/point/storage/controller）
     * @param customName            自定义名称
     * @param transferChange        当前每 tick 传输变化量（FE/t）
     * @param transferBuffer        当前缓冲区储能量（FE）
     * @param rawLimit              设置的传输上限（FE/t）
     * @param maxTransferLimit      最大传输上限（FE/t）
     * @param maxEnergyStorage      最大储能容量（FE）—— 仅 Storage 类型有效，其余为 0
     * @param posKey                位置标识字符串
     * @param externalEnergyStored  外部容器当前储能（FE）—— 仅 PLUG/POINT 有效
     * @param externalEnergyCapacity 外部容器最大容量（FE）—— 仅 PLUG/POINT 有效
     */
    public record FluxDeviceSnapshot(
            String deviceType,
            String customName,
            long transferChange,
            long transferBuffer,
            long rawLimit,
            long maxTransferLimit,
            long maxEnergyStorage,
            String posKey,
            long externalEnergyStored,
            long externalEnergyCapacity
    ) {
    }
}


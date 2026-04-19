package com.yuyinrl.resourceobserver.integration;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IEnergyConversionHelper;
import mekanism.api.energy.IStrictEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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

    // ========== BrandonsCore IOPStorage 能力懒初始化（Draconic Evolution） ==========
    /**
     * 通过反射懒获取 BrandonsCore 的 BlockCapability（IOPStorage）。
     * IOPStorage 扩展自 IEnergyStorage，提供 long 精度的 getOPStored() / getMaxOPStored()，
     * 解决 DE Energy Core 数万亿 RF 容量在 int 精度下溢出的问题。
     * BrandonsCore 不在编译期 classpath 中，全部通过反射访问。
     * 若 BrandonsCore 未加载，则返回 null，并不再重试。
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static volatile BlockCapability<Object, Direction> brandonsCoreOPCap;
    private static volatile boolean brandonsCoreCapResolved = false;

    @Nullable
    @SuppressWarnings("unchecked")
    private static BlockCapability<Object, Direction> getBrandonsCoreOPCapability() {
        if (!brandonsCoreCapResolved) {
            synchronized (FluxNetworksIntegration.class) {
                if (!brandonsCoreCapResolved) {
                    try {
                        Class<?> capClass = Class.forName("com.brandon3055.brandonscore.capability.CapabilityOP");
                        Field field = capClass.getField("BLOCK");
                        brandonsCoreOPCap = (BlockCapability<Object, Direction>) field.get(null);
                        ResourceObserverMod.LOGGER.debug("[ResourceObserver] BrandonsCore IOPStorage capability resolved");
                    } catch (Exception e) {
                        brandonsCoreOPCap = null;
                        ResourceObserverMod.LOGGER.debug("[ResourceObserver] BrandonsCore capability not available: {}", e.getMessage());
                    }
                    brandonsCoreCapResolved = true;
                }
            }
        }
        return brandonsCoreOPCap;
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
                            List<ExternalEnergyRef> externalRefs = List.of();
                            if ((deviceType == FluxDeviceType.PLUG || deviceType == FluxDeviceType.POINT)
                                    && deviceBlockPos != null) {
                                Level deviceLevel = blockEntity.getLevel();
                                if (deviceLevel != null) {
                                    externalRefs = probeAdjacentEnergyRefs(deviceLevel, deviceBlockPos);
                                    for (ExternalEnergyRef ref : externalRefs) {
                                        extEnergyStored = saturatingAdd(extEnergyStored, ref.stored());
                                        extEnergyCapacity = saturatingAdd(extEnergyCapacity, ref.capacity());
                                    }
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
                                    extEnergyCapacity,
                                    externalRefs
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
    private static List<ExternalEnergyRef> probeAdjacentEnergyRefs(Level level, BlockPos devicePos) {
        Map<String, ExternalEnergyRef> refs = new LinkedHashMap<>();
        BlockCapability<IStrictEnergyHandler, Direction> mekaEnergyCap = getMekanismStrictEnergyCapability();
        try {
            for (Direction dir : Direction.values()) {
                BlockPos adjacentPos = devicePos.relative(dir);
                if (!level.isLoaded(adjacentPos)) {
                    continue;
                }
                BlockEntity adjacentBe = level.getBlockEntity(adjacentPos);
                if (adjacentBe instanceof TileFluxDevice) {
                    continue;
                }

                long storedAtPos = 0L;
                long capAtPos = 0L;
                long maxAcceptAtPos = 0L;

                IEnergyStorage energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, adjacentPos, dir.getOpposite());
                if (energy == null) {
                    energy = level.getCapability(Capabilities.EnergyStorage.BLOCK, adjacentPos, null);
                }
                if (energy != null) {
                    long cap = Integer.toUnsignedLong(energy.getMaxEnergyStored());
                    if (cap > capAtPos) {
                        capAtPos = cap;
                        storedAtPos = Integer.toUnsignedLong(energy.getEnergyStored());
                    }
                    // 模拟接收以获取设备最大接受速率
                    if (energy.canReceive()) {
                        try {
                            maxAcceptAtPos = Integer.toUnsignedLong(
                                    energy.receiveEnergy(Integer.MAX_VALUE, true));
                        } catch (Exception ignored) {
                        }
                    }
                }

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
                            long feCap = joulesToFE(joulesMax);
                            long feStored = joulesToFE(joulesStored);
                            if (feCap > capAtPos) {
                                capAtPos = feCap;
                                storedAtPos = feStored;
                            }
                        }
                    } catch (Exception e) {
                        ResourceObserverMod.LOGGER.debug(
                                "[ResourceObserver] Mekanism energy probe failed at {}: {}", adjacentPos, e.getMessage());
                    }
                }

                // DE Energy Pylon → Energy Core 直连探测
                // 通过 Pylon 的 coreOffset 字段定位到 Energy Core 多方块结构，
                // 直接从 Core.energy 读取真实储能数据（long 精度），跳过 Pylon 自身的 opAdapter
                long[] deResult = probeDEEnergyCoreViaPylon(level, adjacentPos, adjacentBe);
                if (deResult != null) {
                    if (deResult[1] > capAtPos) {
                        capAtPos = deResult[1];
                        storedAtPos = deResult[0];
                    }
                    if (deResult[2] > maxAcceptAtPos) {
                        maxAcceptAtPos = deResult[2];
                    }
                } else {
                    // 通用 BrandonsCore IOPStorage 探测（非 DE Pylon 的其他使用 OP 系统的模组）
                    BlockCapability<Object, Direction> opCap = getBrandonsCoreOPCapability();
                    if (opCap != null) {
                        try {
                            Object opStorage = level.getCapability(opCap, adjacentPos, dir.getOpposite());
                            if (opStorage == null) {
                                opStorage = level.getCapability(opCap, adjacentPos, null);
                            }
                            if (opStorage != null) {
                                long opStored = (long) opStorage.getClass().getMethod("getOPStored").invoke(opStorage);
                                long opMax = (long) opStorage.getClass().getMethod("getMaxOPStored").invoke(opStorage);
                                if (opMax > capAtPos) {
                                    capAtPos = opMax;
                                    storedAtPos = opStored;
                                }
                                try {
                                    long opMaxAccept = (long) opStorage.getClass()
                                            .getMethod("receiveOP", long.class, boolean.class)
                                            .invoke(opStorage, Long.MAX_VALUE, true);
                                    if (opMaxAccept > maxAcceptAtPos) {
                                        maxAcceptAtPos = opMaxAccept;
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        } catch (Exception e) {
                            ResourceObserverMod.LOGGER.debug(
                                    "[ResourceObserver] BrandonsCore OP probe failed at {}: {}", adjacentPos, e.getMessage());
                        }
                    }
                }

                if (capAtPos > 0L) {
                    ExternalEnergyRef ref = new ExternalEnergyRef(
                            resolveExternalGroupId(level, adjacentPos, adjacentBe),
                            buildExternalRefName(level, adjacentPos),
                            storedAtPos,
                            capAtPos,
                            maxAcceptAtPos
                    );
                    refs.putIfAbsent(ref.extId(), ref);
                }
            }
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.debug(
                    "[ResourceObserver] Error probing adjacent energy at {}: {}", devicePos, e.getMessage());
        }
        return List.copyOf(refs.values());
    }

    private static String resolveExternalGroupId(Level level, BlockPos pos, @Nullable BlockEntity adjacentBe) {
        String mekMultiblockId = resolveMekMultiblockGroupId(adjacentBe);
        if (mekMultiblockId != null && !mekMultiblockId.isBlank()) {
            return mekMultiblockId;
        }
        String deCoreId = resolveDECoreGroupId(level, pos, adjacentBe);
        if (deCoreId != null && !deCoreId.isBlank()) {
            return deCoreId;
        }
        return buildExternalRefId(level, pos);
    }

    @Nullable
    private static String resolveMekMultiblockGroupId(@Nullable BlockEntity adjacentBe) {
        if (adjacentBe == null) {
            return null;
        }
        String className = adjacentBe.getClass().getName();
        if (!className.startsWith("mekanism.")) {
            return null;
        }

        Object mbUuid = invokeNoArg(adjacentBe, "getMultiblockUUID");
        String uuidId = toMekMultiblockId(mbUuid);
        if (uuidId != null) {
            return uuidId;
        }

        Object multiblock = invokeNoArg(adjacentBe, "getMultiblock");
        if (multiblock != null) {
            Object inventoryId = readField(multiblock, "inventoryID");
            String inventoryUuidId = toMekMultiblockId(inventoryId);
            if (inventoryUuidId != null) {
                return inventoryUuidId;
            }
        }
        return null;
    }

    /**
     * 通过 DE Energy Pylon 定位到其连接的 Energy Core 多方块结构，
     * 直接从 Core.energy（OPStorageOP）读取储能数据（long 精度）。
     * <p>
     * 链路：Pylon.coreOffset → Core BlockPos → Core.energy → getOPStored() / getMaxOPStored()
     *
     * @param level    服务端 Level
     * @param pylonPos Energy Pylon 的方块坐标
     * @param pylonBe  Energy Pylon 的方块实体
     * @return long[3] = {stored, capacity, maxAcceptPerTick}；非 DE Pylon 或定位失败时返回 null
     */
    @Nullable
    private static long[] probeDEEnergyCoreViaPylon(Level level, BlockPos pylonPos, @Nullable BlockEntity pylonBe) {
        if (pylonBe == null) {
            return null;
        }
        String className = pylonBe.getClass().getName();
        if (!className.equals("com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyPylon")) {
            return null;
        }

        // Pylon.coreOffset 是 ManagedPos 类型，调用 get() 获取 BlockPos
        Object coreOffsetField = readField(pylonBe, "coreOffset");
        if (coreOffsetField == null) {
            return null;
        }
        Object offsetPos = invokeNoArg(coreOffsetField, "get");
        if (!(offsetPos instanceof BlockPos coreOffset)) {
            return null;
        }

        // 计算 Core 坐标：corePos = pylonPos - coreOffset（与 TileEnergyPylon.getCore() 一致）
        BlockPos corePos = pylonPos.subtract(coreOffset);
        if (!level.isLoaded(corePos)) {
            return null;
        }

        BlockEntity coreBe = level.getBlockEntity(corePos);
        if (coreBe == null) {
            return null;
        }
        String coreClassName = coreBe.getClass().getName();
        if (!coreClassName.equals("com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyCore")) {
            return null;
        }

        // 读取 Core.energy 字段（OPStorageOP 类型）并调用 getOPStored() / getMaxOPStored()
        Object energy = readField(coreBe, "energy");
        if (energy == null) {
            return null;
        }

        try {
            long stored = (long) energy.getClass().getMethod("getOPStored").invoke(energy);
            long capacity = (long) energy.getClass().getMethod("getMaxOPStored").invoke(energy);

            long maxAccept = 0L;
            try {
                maxAccept = (long) energy.getClass()
                        .getMethod("receiveOP", long.class, boolean.class)
                        .invoke(energy, Long.MAX_VALUE, true);
            } catch (Exception ignored) {
            }

            return new long[]{stored, capacity, maxAccept};
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.debug(
                    "[ResourceObserver] DE Energy Core read failed at {}: {}", corePos, e.getMessage());
            return null;
        }
    }

    /**
     * 通过 DE Energy Pylon 定位到 Energy Core，读取 Core 的 linkUUID 作为多方块分组 ID。
     * 同一个 Energy Core 的多个 Pylon 会返回相同的 linkUUID，实现去重分组。
     */
    @Nullable
    private static String resolveDECoreGroupId(Level level, BlockPos pylonPos, @Nullable BlockEntity adjacentBe) {
        if (adjacentBe == null) {
            return null;
        }
        String className = adjacentBe.getClass().getName();
        if (!className.equals("com.brandon3055.draconicevolution.blocks.tileentity.TileEnergyPylon")) {
            return null;
        }

        // Pylon.coreOffset → Core 坐标
        Object coreOffsetField = readField(adjacentBe, "coreOffset");
        if (coreOffsetField == null) {
            return null;
        }
        Object offsetPos = invokeNoArg(coreOffsetField, "get");
        if (!(offsetPos instanceof BlockPos coreOffset)) {
            return null;
        }

        BlockPos corePos = pylonPos.subtract(coreOffset);
        if (!level.isLoaded(corePos)) {
            return null;
        }

        BlockEntity coreBe = level.getBlockEntity(corePos);
        if (coreBe == null) {
            return null;
        }

        // Core.linkUUID 是 ManagedUUID 类型，调用 get() 获取实际 UUID
        Object linkUUID = readField(coreBe, "linkUUID");
        if (linkUUID != null) {
            Object uuid = invokeNoArg(linkUUID, "get");
            if (uuid instanceof UUID u) {
                return "decore_" + u;
            }
        }

        // 降级：用 Core 坐标作为分组标识（Core 未激活时没有 linkUUID）
        return "decore_" + corePos.getX() + "_" + corePos.getY() + "_" + corePos.getZ();
    }

    @Nullable
    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Exception ignored) {
        }
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (Exception ignored) {
        }
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static String toMekMultiblockId(@Nullable Object idObj) {
        if (idObj == null) {
            return null;
        }
        if (idObj instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
                return null;
            }
            return toMekMultiblockId(optional.get());
        }
        String uuid;
        if (idObj instanceof UUID value) {
            uuid = value.toString();
        } else {
            uuid = idObj.toString();
        }
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        String normalized = uuid.trim().replace('.', '_');
        if (normalized.regionMatches(true, 0, "Optional[", 0, "Optional[".length())
                && normalized.endsWith("]")) {
            normalized = normalized.substring("Optional[".length(), normalized.length() - 1).trim();
        }
        if (normalized.isBlank()) {
            return null;
        }
        String safe = normalized.replaceAll("[^A-Za-z0-9_-]", "_");
        if (safe.isBlank()) {
            return null;
        }
        return "mekmb_" + safe;
    }

    private static String buildExternalRefId(Level level, BlockPos pos) {
        String dimKey = level.dimension().location().toString();
        long dimHash = Integer.toUnsignedLong(dimKey.hashCode());
        return "pos_"
                + Long.toHexString(dimHash)
                + "_" + pos.getX()
                + "_" + pos.getY()
                + "_" + pos.getZ();
    }

    private static String buildExternalRefName(Level level, BlockPos pos) {
        try {
            Block block = level.getBlockState(pos).getBlock();
            String blockName = block.getName().getString();
            // 注册 ID 前缀用于客户端提取模组命名空间，格式：registryId|BlockName @ [x, y, z]
            ResourceLocation regId = BuiltInRegistries.BLOCK.getKey(block);
            String prefix = regId != null ? regId.toString() + "|" : "";
            if (blockName != null && !blockName.isBlank()) {
                return prefix + blockName + " @ " + pos.toShortString();
            }
        } catch (Exception ignored) {
        }
        return "External Storage @ " + pos.toShortString();
    }
    private static long joulesToFE(long joules) {
        if (joules <= 0L) return 0L;
        try {
            IEnergyConversionHelper helper = IEnergyConversionHelper.INSTANCE;
            if (helper == null) return joules; // Mekanism 尚未初始化，降级返回原值
            IEnergyConversion feConv = helper.feConversion();
            if (feConv == null || !feConv.isEnabled()) return joules;
            return feConv.convertTo(joules);
        } catch (Exception e) {
            return joules; // 瀹夊叏闄嶇骇
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
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
            long externalEnergyCapacity,
            List<ExternalEnergyRef> externalRefs
    ) {
    }

    public record ExternalEnergyRef(
            String extId,
            String displayName,
            long stored,
            long capacity,
            long maxAcceptPerTick
    ) {
    }
}



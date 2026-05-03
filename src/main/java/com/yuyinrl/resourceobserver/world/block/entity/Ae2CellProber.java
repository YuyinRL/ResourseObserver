package com.yuyinrl.resourceobserver.world.block.entity;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.storage.cells.StorageCell;
import appeng.api.stacks.AEKeyType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AE2 Cell 反射探测与容量评估工具。
 * 从 ObserverBlockEntity 中提取 ~1000 行 Cell 级别反射访问、容量指标采集和外储探测逻辑。
 */
public final class Ae2CellProber {

    private Ae2CellProber() {}

    private static final Map<Class<?>, CellMetricsAccessors> CELL_METRICS_ACCESSORS = new HashMap<>();
    private static final Set<Class<?>> CELL_METRICS_UNSUPPORTED = new HashSet<>();
    private static final int DEFAULT_ITEM_PROBE_COUNT = 1;
    private static final int MAX_ITEM_PROBE_CANDIDATES = 6;
    private static final int MIN_ITEM_PROBE_BUDGET = 96;

    // ===================== 内部类型 =====================

    enum CellChannel {
        ITEM("item", "getTotalItemTypes", "getStoredItemTypes", "getStoredItemCount", "getRemainingItemCount"),
        FLUID("fluid", "getTotalFluidTypes", "getStoredFluidTypes", "getStoredFluidCount", "getRemainingFluidCount");

        final String key;
        final String totalTypesMethodName, usedTypesMethodName, storedUnitsMethodName, remainingUnitsMethodName;

        CellChannel(String key, String totalTypesMethodName, String usedTypesMethodName,
                    String storedUnitsMethodName, String remainingUnitsMethodName) {
            this.key = key;
            this.totalTypesMethodName = totalTypesMethodName;
            this.usedTypesMethodName = usedTypesMethodName;
            this.storedUnitsMethodName = storedUnitsMethodName;
            this.remainingUnitsMethodName = remainingUnitsMethodName;
        }
    }

    record ReflectedCellMetrics(CellChannel channel, long totalBytes, long usedBytes,
                                long totalTypes, long usedTypes, long usedUnits, long maxUnits) {}

    record CellMetricsAccessors(Method totalBytes, Method usedBytes, Method totalTypes, Method usedTypes,
                                Method storedUnits, Method remainingUnits) {
        ReflectedCellMetrics read(StorageCell cell, CellChannel channel) throws ReflectiveOperationException {
            return new ReflectedCellMetrics(channel,
                    (long) totalBytes.invoke(cell), (long) usedBytes.invoke(cell),
                    (long) totalTypes.invoke(cell), (long) usedTypes.invoke(cell),
                    (long) storedUnits.invoke(cell), (long) remainingUnits.invoke(cell));
        }
        CellChannel detectChannel(StorageCell cell) {
            if (totalBytes == null || usedBytes == null) return null;
            try { long tb = (long) totalBytes.invoke(cell); long ub = (long) usedBytes.invoke(cell);
                  if (tb > 0 || ub > 0) return CellChannel.ITEM; }
            catch (Exception e) { try { if ((long) totalBytes.invoke(cell) > 0) return CellChannel.ITEM; } catch (Exception ignored) {} }
            return CellChannel.FLUID;
        }
    }

    record SlotCellProbe(@Nullable CellChannel channel, @Nullable String itemId, @Nullable String keyTypeId) {}

    record CapacityMachineCollection(Set<IChestOrDrive> machines, int fromChestOrDriveCount,
                                     int fromStorageProviderCount, int fromNodeOwnerCount,
                                     int fromNodeStorageServiceCount, int nodesScanned) {}

    record StorageProviderCollection(Set<IStorageProvider> providers, int fromMachinesCount,
                                     int fromNodeOwnerCount, int fromNodeStorageServiceCount, int nodesScanned) {}

    record ExternalCapacityReadResult(long itemUsedUnits, long itemTotalUnits, long fluidUsedUnits,
                                      long fluidTotalUnits, boolean reliable, boolean available, String debugSuffix) {
        static ExternalCapacityReadResult empty() {
            return new ExternalCapacityReadResult(0L, 0L, 0L, 0L, true, false,
                    ";capacity_external_available=false;capacity_external_reliable=true"
                            + ";capacity_external_providers_total=0"
                            + ";capacity_external_storage_bus_total=0"
                            + ";capacity_external_storage_bus_readable=0");
        }
    }

    record ItemSlotCapacityEstimate(long totalUnits, boolean reliable) {}
    record SimulatedInsertProbe(long insertedUnits, boolean reliable) {}

    // ===================== 主入口 =====================

    private static final String AE2_CAPACITY_SCOPE_CELLS_ONLY = "AE2_CELLS_ONLY";

    /** AE2 网络 Cell 容量指标采集 —— 从 ObserverBlockEntity.readAe2CellCapacityMetrics 完整迁移 */
    static ObserverBlockEntity.Ae2CapacityReadResult readCellCapacityMetrics(IGrid grid) {
        long itemUsedBytes = 0L, itemTotalBytes = 0L, itemUsedTypes = 0L, itemTotalTypes = 0L;
        long itemUsedUnits = 0L, itemMaxUnits = 0L;
        long fluidUsedBytes = 0L, fluidTotalBytes = 0L, fluidUsedTypes = 0L, fluidTotalTypes = 0L;
        long fluidUsedUnits = 0L, fluidMaxUnits = 0L;
        boolean reliable = true;
        int cellCount = 0, readableCellCount = 0, itemCellCount = 0, itemReadableCellCount = 0;
        int fluidCellCount = 0, fluidReadableCellCount = 0, deviceCount = 0, poweredDeviceCount = 0;
        int nullCellCount = 0, nullCellOnUnpoweredCount = 0, statusProbeFailedCount = 0;
        int fromChestOrDriveCount = 0, fromStorageProviderCount = 0, fromNodeOwnerCount = 0;
        int fromNodeStorageServiceCount = 0, nodesScanned = 0;
        Map<String, Integer> deviceClasses = new HashMap<>();
        Map<String, Integer> readableCellClasses = new HashMap<>();
        Map<String, Integer> readableItemCellClasses = new HashMap<>();
        Map<String, Integer> readableFluidCellClasses = new HashMap<>();
        Map<String, Integer> slotCellItemIds = new HashMap<>();
        Map<String, Integer> slotCellKeyTypes = new HashMap<>();
        Map<String, Integer> missingMethodCells = new HashMap<>();
        Map<String, Integer> invocationFailedCells = new HashMap<>();
        int slotCellChannelItem = 0, slotCellChannelFluid = 0, slotCellChannelUnknown = 0;

        CapacityMachineCollection machineCollection = collectCapacityMachines(grid);
        Set<IChestOrDrive> machines = machineCollection.machines();
        fromChestOrDriveCount = machineCollection.fromChestOrDriveCount();
        fromStorageProviderCount = machineCollection.fromStorageProviderCount();
        fromNodeOwnerCount = machineCollection.fromNodeOwnerCount();
        fromNodeStorageServiceCount = machineCollection.fromNodeStorageServiceCount();
        nodesScanned = machineCollection.nodesScanned();

        for (IChestOrDrive machine : machines) {
            deviceCount++;
            incrementCount(deviceClasses, machine.getClass().getName());
            boolean powered = safeIsPowered(machine);
            if (powered) poweredDeviceCount++;
            for (int slot = 0; slot < machine.getCellCount(); slot++) {
                if (!probeCellStatus(machine, slot)) statusProbeFailedCount++;
                StorageCell cell = machine.getOriginalCellInventory(slot);
                if (cell == null) {
                    if (powered) nullCellCount++; else nullCellOnUnpoweredCount++;
                    continue;
                }
                cellCount++;
                Class<?> cellClass = cell.getClass();
                CellMetricsAccessors accessors = resolveCellMetricsAccessors(cellClass);
                if (accessors == null) { reliable = false; incrementCount(missingMethodCells, cellClass.getName()); continue; }
                SlotCellProbe slotProbe = detectChannelFromCellItem(machine, slot);
                if (slotProbe.itemId() != null) incrementCount(slotCellItemIds, slotProbe.itemId());
                if (slotProbe.keyTypeId() != null) incrementCount(slotCellKeyTypes, slotProbe.keyTypeId());
                CellChannel channel = slotProbe.channel();
                if (channel == null) { slotCellChannelUnknown++; channel = accessors.detectChannel(cell); }
                else if (channel == CellChannel.ITEM) slotCellChannelItem++;
                else slotCellChannelFluid++;
                if (channel == CellChannel.ITEM) itemCellCount++; else fluidCellCount++;
                ReflectedCellMetrics cellMetrics;
                try { cellMetrics = accessors.read(cell, channel); }
                catch (ReflectiveOperationException ex) { reliable = false; incrementCount(invocationFailedCells, cellClass.getName()); continue; }
                readableCellCount++;
                incrementCount(readableCellClasses, cellClass.getName());
                if (cellMetrics.channel() == CellChannel.ITEM) {
                    itemReadableCellCount++; incrementCount(readableItemCellClasses, cellClass.getName());
                    itemUsedBytes = saturatingAdd(itemUsedBytes, Math.max(0L, cellMetrics.usedBytes()));
                    itemTotalBytes = saturatingAdd(itemTotalBytes, Math.max(0L, cellMetrics.totalBytes()));
                    itemUsedTypes = saturatingAdd(itemUsedTypes, Math.max(0L, cellMetrics.usedTypes()));
                    itemTotalTypes = saturatingAdd(itemTotalTypes, Math.max(0L, cellMetrics.totalTypes()));
                    itemUsedUnits = saturatingAdd(itemUsedUnits, Math.max(0L, cellMetrics.usedUnits()));
                    itemMaxUnits = saturatingAdd(itemMaxUnits, Math.max(0L, cellMetrics.maxUnits()));
                } else {
                    fluidReadableCellCount++; incrementCount(readableFluidCellClasses, cellClass.getName());
                    fluidUsedBytes = saturatingAdd(fluidUsedBytes, Math.max(0L, cellMetrics.usedBytes()));
                    fluidTotalBytes = saturatingAdd(fluidTotalBytes, Math.max(0L, cellMetrics.totalBytes()));
                    fluidUsedTypes = saturatingAdd(fluidUsedTypes, Math.max(0L, cellMetrics.usedTypes()));
                    fluidTotalTypes = saturatingAdd(fluidTotalTypes, Math.max(0L, cellMetrics.totalTypes()));
                    fluidUsedUnits = saturatingAdd(fluidUsedUnits, Math.max(0L, cellMetrics.usedUnits()));
                    fluidMaxUnits = saturatingAdd(fluidMaxUnits, Math.max(0L, cellMetrics.maxUnits()));
                }
            }
        }

        ExternalCapacityReadResult externalCapacity = readExternalCapacityMetrics(grid);
        ObserverBlockEntity.Ae2CellCapacityMetrics metrics = new ObserverBlockEntity.Ae2CellCapacityMetrics(
                itemUsedBytes, itemTotalBytes, itemUsedTypes, itemTotalTypes, itemUsedUnits, itemMaxUnits,
                fluidUsedBytes, fluidTotalBytes, fluidUsedTypes, fluidTotalTypes, fluidUsedUnits, fluidMaxUnits,
                externalCapacity.itemUsedUnits(), externalCapacity.itemTotalUnits(),
                externalCapacity.fluidUsedUnits(), externalCapacity.fluidTotalUnits(),
                AE2_CAPACITY_SCOPE_CELLS_ONLY,
                reliable && externalCapacity.reliable(), cellCount > 0,
                externalCapacity.reliable(), externalCapacity.available());
        String debugSuffix = ";capacity_cells_total=" + cellCount
                + ";capacity_cells_readable=" + readableCellCount
                + ";capacity_devices_total=" + deviceCount + ";capacity_devices_powered=" + poweredDeviceCount
                + ";capacity_cells_null=" + nullCellCount + ";capacity_cells_null_unpowered=" + nullCellOnUnpoweredCount
                + ";capacity_cell_status_probe_failed=" + statusProbeFailedCount
                + ";capacity_item_cells_total=" + itemCellCount + ";capacity_item_cells_readable=" + itemReadableCellCount
                + ";capacity_fluid_cells_total=" + fluidCellCount + ";capacity_fluid_cells_readable=" + fluidReadableCellCount
                + ";capacity_devices_from_iChestOrDrive=" + fromChestOrDriveCount
                + ";capacity_devices_from_storage_provider=" + fromStorageProviderCount
                + ";capacity_devices_from_node_owner=" + fromNodeOwnerCount
                + ";capacity_devices_from_node_storage_service=" + fromNodeStorageServiceCount
                + ";capacity_nodes_scanned=" + nodesScanned
                + ";capacity_device_classes=" + summarizeFailureClasses(deviceClasses)
                + ";capacity_cells_readable_classes=" + summarizeFailureClasses(readableCellClasses)
                + ";capacity_item_cells_readable_classes=" + summarizeFailureClasses(readableItemCellClasses)
                + ";capacity_fluid_cells_readable_classes=" + summarizeFailureClasses(readableFluidCellClasses)
                + ";capacity_slot_cell_items=" + summarizeFailureClasses(slotCellItemIds)
                + ";capacity_slot_cell_keytypes=" + summarizeFailureClasses(slotCellKeyTypes)
                + ";capacity_slot_channel_item=" + slotCellChannelItem
                + ";capacity_slot_channel_fluid=" + slotCellChannelFluid
                + ";capacity_slot_channel_unknown=" + slotCellChannelUnknown
                + ";capacity_cells_missing_methods=" + summarizeFailureClasses(missingMethodCells)
                + ";capacity_cells_invocation_failed=" + summarizeFailureClasses(invocationFailedCells)
                + externalCapacity.debugSuffix();
        return new ObserverBlockEntity.Ae2CapacityReadResult(metrics, debugSuffix);
    }

    private static long saturatingAdd(long left, long right) {
        return Ae2Sampler.saturatingAdd(left, right);
    }

    // ===================== 反射工具 =====================

    static @Nullable CellMetricsAccessors resolveCellMetricsAccessors(Class<?> cellClass) {
        if (CELL_METRICS_ACCESSORS.containsKey(cellClass)) return CELL_METRICS_ACCESSORS.get(cellClass);
        if (CELL_METRICS_UNSUPPORTED.contains(cellClass)) return null;
        try {
            Method totalBytes = findCellMetricMethod(cellClass, "getTotalBytes");
            if (totalBytes == null) { CELL_METRICS_UNSUPPORTED.add(cellClass); return null; }
            totalBytes.setAccessible(true);
            Method usedBytes = findCellMetricMethod(cellClass, "getUsedBytes"); usedBytes.setAccessible(true);
            Method totalTypes = findOptionalMethod(cellClass, "getTotalItemTypes");
            if (totalTypes == null) totalTypes = findOptionalMethod(cellClass, "getTotalTypes");
            if (totalTypes != null) totalTypes.setAccessible(true);
            Method usedTypes = findOptionalMethod(cellClass, "getStoredItemTypes");
            if (usedTypes == null) usedTypes = findOptionalMethod(cellClass, "getStoredTypes");
            if (usedTypes != null) usedTypes.setAccessible(true);
            Method storedUnits = findOptionalMethod(cellClass, "getStoredItemCount");
            if (storedUnits == null) storedUnits = findOptionalMethod(cellClass, "getStoredCount");
            if (storedUnits != null) storedUnits.setAccessible(true);
            Method remainingUnits = findOptionalMethod(cellClass, "getRemainingItemCount");
            if (remainingUnits == null) remainingUnits = findOptionalMethod(cellClass, "getRemainingCount");
            if (remainingUnits != null) remainingUnits.setAccessible(true);
            CellMetricsAccessors a = new CellMetricsAccessors(totalBytes, usedBytes, totalTypes, usedTypes, storedUnits, remainingUnits);
            CELL_METRICS_ACCESSORS.put(cellClass, a);
            return a;
        } catch (Exception e) { CELL_METRICS_UNSUPPORTED.add(cellClass); return null; }
    }

    static long invokeLong(Method method, Object target) throws ReflectiveOperationException {
        Object result = method.invoke(target);
        if (result instanceof Number n) return n.longValue();
        throw new ReflectiveOperationException("bad return: " + result);
    }

    static Method findCellMetricMethod(Class<?> cellClass, String name) throws ReflectiveOperationException {
        Method m = findOptionalMethod(cellClass, name);
        if (m == null) throw new ReflectiveOperationException("not found: " + cellClass.getName() + "#" + name);
        return m;
    }

    static @Nullable Method findOptionalMethod(Class<?> cellClass, String name) {
        try { return cellClass.getMethod(name); } catch (NoSuchMethodException ignored) {}
        try { return cellClass.getDeclaredMethod(name); } catch (NoSuchMethodException ignored) {}
        return null;
    }

    static @Nullable Field findOptionalField(Class<?> cls, String name) {
        try { return cls.getField(name); } catch (NoSuchFieldException ignored) {}
        try { return cls.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        return null;
    }

    static @Nullable Object invokeOptional(Object target, String methodName) {
        if (target == null) return null;
        Method m = findOptionalMethod(target.getClass(), methodName);
        if (m == null) return null;
        try { m.setAccessible(true); return m.invoke(target); } catch (Exception ignored) { return null; }
    }

    static @Nullable Object readOptionalField(Object target, String name) {
        if (target == null) return null;
        Field f = findOptionalField(target.getClass(), name);
        if (f == null) return null;
        try { f.setAccessible(true); return f.get(target); } catch (Exception ignored) { return null; }
    }

    @SuppressWarnings("unchecked")
    static <T> @Nullable T readFieldByType(Object target, Class<T> expectedType) {
        if (target == null || expectedType == null) return null;
        Class<?> c = target.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (expectedType.isAssignableFrom(f.getType())) {
                    try { f.setAccessible(true); Object v = f.get(target); if (expectedType.isInstance(v)) return (T) v; }
                    catch (Exception ignored) {}
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    // ===================== Cell 探测 =====================

    static SlotCellProbe detectChannelFromCellItem(IChestOrDrive machine, int slot) {
        try {
            Item cellItem = machine.getCellItem(slot);
            if (cellItem == null) return new SlotCellProbe(null, null, null);
            String itemId = BuiltInRegistries.ITEM.getKey(cellItem).toString();
            if (cellItem instanceof IBasicCellItem basicCellItem) {
                AEKeyType keyType = basicCellItem.getKeyType();
                String keyTypeId = keyType == null ? null : keyType.getId().toString();
                CellChannel channel = parseChannelFromKeyType(keyType);
                if (channel == null) channel = parseChannelFromItemId(itemId);
                return new SlotCellProbe(channel, itemId, keyTypeId);
            }
            return new SlotCellProbe(parseChannelFromItemId(itemId), itemId, null);
        } catch (RuntimeException ex) {
            return new SlotCellProbe(null, null, null);
        }
    }

    static boolean probeCellStatus(IChestOrDrive machine, int slot) {
        try { machine.getCellStatus(slot); return true; } catch (Exception ignored) { return false; }
    }

    static boolean safeIsPowered(IChestOrDrive machine) {
        try { return machine.isPowered(); } catch (Exception ignored) { return false; }
    }

    static @Nullable CellChannel parseChannelFromObject(@Nullable Object channelObject) {
        if (channelObject == null) return null;
        String s = channelObject.toString().toLowerCase(Locale.ROOT);
        if (s.contains("fluid")) return CellChannel.FLUID;
        if (s.contains("item")) return CellChannel.ITEM;
        return null;
    }

    static @Nullable CellChannel parseChannelFromKeyType(@Nullable Object keyTypeObject) {
        if (keyTypeObject == null) return null;
        String s = keyTypeObject.toString().toLowerCase(Locale.ROOT);
        if (s.contains("fluid")) return CellChannel.FLUID;
        if (s.contains("item")) return CellChannel.ITEM;
        return null;
    }

    static @Nullable CellChannel parseChannelFromItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        String s = itemId.toLowerCase(Locale.ROOT);
        if (s.contains("fluid")) return CellChannel.FLUID;
        if (s.contains("item")) return CellChannel.ITEM;
        return null;
    }

    // ===================== 容量收集 =====================

    static CapacityMachineCollection collectCapacityMachines(IGrid grid) {
        Set<IChestOrDrive> result = Collections.newSetFromMap(new IdentityHashMap<>());
        int fromChestOrDrive = 0, fromStorageP = 0, fromOwner = 0, fromService = 0, scanned = 0;
        for (IChestOrDrive m : grid.getMachines(IChestOrDrive.class)) { fromChestOrDrive++; result.add(m); }
        for (IStorageProvider p : grid.getMachines(IStorageProvider.class)) {
            if (p instanceof IChestOrDrive cd) { fromStorageP++; result.add(cd); }
        }
        for (IGridNode n : grid.getNodes()) {
            scanned++;
            if (n.getOwner() instanceof IChestOrDrive cd) { fromOwner++; result.add(cd); }
            IStorageProvider s = n.getService(IStorageProvider.class);
            if (s instanceof IChestOrDrive cd) { fromService++; result.add(cd); }
        }
        return new CapacityMachineCollection(result, fromChestOrDrive, fromStorageP, fromOwner, fromService, scanned);
    }

    static StorageProviderCollection collectStorageProviders(IGrid grid) {
        Set<IStorageProvider> providers = Collections.newSetFromMap(new IdentityHashMap<>());
        int fromMachines = 0, fromOwner = 0, fromService = 0, scanned = 0;
        for (IStorageProvider p : grid.getMachines(IStorageProvider.class)) { if (p == null) continue; fromMachines++; providers.add(p); }
        for (IGridNode n : grid.getNodes()) {
            scanned++;
            if (n.getOwner() instanceof IStorageProvider p) { fromOwner++; providers.add(p); }
            IStorageProvider s = n.getService(IStorageProvider.class);
            if (s != null) { fromService++; providers.add(s); }
        }
        return new StorageProviderCollection(providers, fromMachines, fromOwner, fromService, scanned);
    }

    // ===================== 外储容量探测 =====================

    static ExternalCapacityReadResult readExternalCapacityMetrics(IGrid grid) {
        StorageProviderCollection providerCollection = collectStorageProviders(grid);
        if (providerCollection.providers().isEmpty()) return ExternalCapacityReadResult.empty();

        Set<IItemHandler> itemHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<IFluidHandler> fluidHandlers = Collections.newSetFromMap(new IdentityHashMap<>());
        int storageBusProviderCount = 0, readableStorageBusProviderCount = 0, unreadableStorageBusProviderCount = 0;
        boolean reliable = true;
        Map<String, Integer> storageBusProviderClasses = new HashMap<>();
        Map<String, Integer> unreadableProviderClasses = new HashMap<>();

        for (IStorageProvider provider : providerCollection.providers()) {
            if (!isStorageBusProvider(provider)) continue;
            storageBusProviderCount++;
            incrementCount(storageBusProviderClasses, provider.getClass().getName());
            Object internalStorage = extractStorageBusInternalStorage(provider);
            if (internalStorage == null) { reliable = false; unreadableStorageBusProviderCount++;
                incrementCount(unreadableProviderClasses, provider.getClass().getName()); continue; }
            boolean providerReadable = false;
            for (Object storageNode : extractStorageBranches(internalStorage)) {
                IItemHandler itemHandler = unwrapItemHandler(storageNode);
                if (itemHandler != null) { itemHandlers.add(itemHandler); providerReadable = true; }
                IFluidHandler fluidHandler = unwrapFluidHandler(storageNode);
                if (fluidHandler != null) { fluidHandlers.add(fluidHandler); providerReadable = true; }
            }
            if (providerReadable) readableStorageBusProviderCount++;
            else { reliable = false; unreadableStorageBusProviderCount++;
                   incrementCount(unreadableProviderClasses, provider.getClass().getName()); }
        }

        long extItemUsed = 0L, extItemTotal = 0L, extProbeBudget = 0L, extProbeCalls = 0L;
        for (IItemHandler handler : itemHandlers) {
            int slotCount;
            try { slotCount = Math.max(0, handler.getSlots()); } catch (RuntimeException ex) { reliable = false; continue; }
            int budget = Math.max(MIN_ITEM_PROBE_BUDGET, slotCount * 3);
            int[] probeBudget = new int[]{budget};
            List<ItemStack> probeCandidates = collectItemProbeCandidates(handler, slotCount);
            for (int slot = 0; slot < slotCount; slot++) {
                try {
                    ItemStack stackInSlot = handler.getStackInSlot(slot);
                    ItemSlotCapacityEstimate sc = estimateItemSlotCapacity(handler, slot, stackInSlot, probeCandidates, probeBudget);
                    extItemUsed = saturatingAdd(extItemUsed, Math.max(0, stackInSlot.getCount()));
                    extItemTotal = saturatingAdd(extItemTotal, Math.max(0L, sc.totalUnits()));
                    if (!sc.reliable()) reliable = false;
                } catch (RuntimeException ex) { reliable = false; }
            }
            extProbeBudget = saturatingAdd(extProbeBudget, budget);
            extProbeCalls = saturatingAdd(extProbeCalls, Math.max(0L, (long) budget - probeBudget[0]));
        }

        long extFluidUsed = 0L, extFluidTotal = 0L;
        for (IFluidHandler handler : fluidHandlers) {
            int tankCount;
            try { tankCount = Math.max(0, handler.getTanks()); } catch (RuntimeException ex) { reliable = false; continue; }
            for (int tank = 0; tank < tankCount; tank++) {
                try { extFluidUsed = saturatingAdd(extFluidUsed, Math.max(0, handler.getFluidInTank(tank).getAmount()));
                      extFluidTotal = saturatingAdd(extFluidTotal, Math.max(0, handler.getTankCapacity(tank))); }
                catch (RuntimeException ex) { reliable = false; }
            }
        }

        boolean available = readableStorageBusProviderCount > 0;
        String debugSuffix = ";capacity_external_available=" + available + ";capacity_external_reliable=" + reliable
                + ";capacity_external_item_used=" + extItemUsed + ";capacity_external_item_total=" + extItemTotal
                + ";capacity_external_fluid_used=" + extFluidUsed + ";capacity_external_fluid_total=" + extFluidTotal
                + ";capacity_external_providers_total=" + providerCollection.providers().size()
                + ";capacity_external_providers_from_machines=" + providerCollection.fromMachinesCount()
                + ";capacity_external_providers_from_node_owner=" + providerCollection.fromNodeOwnerCount()
                + ";capacity_external_providers_from_node_service=" + providerCollection.fromNodeStorageServiceCount()
                + ";capacity_external_nodes_scanned=" + providerCollection.nodesScanned()
                + ";capacity_external_storage_bus_total=" + storageBusProviderCount
                + ";capacity_external_storage_bus_readable=" + readableStorageBusProviderCount
                + ";capacity_external_storage_bus_unreadable=" + unreadableStorageBusProviderCount
                + ";capacity_external_item_handler_sources=" + itemHandlers.size()
                + ";capacity_external_fluid_handler_sources=" + fluidHandlers.size()
                + ";capacity_external_item_probe_budget=" + extProbeBudget
                + ";capacity_external_item_probe_calls=" + extProbeCalls
                + ";capacity_external_storage_bus_classes=" + summarizeFailureClasses(storageBusProviderClasses)
                + ";capacity_external_unreadable_classes=" + summarizeFailureClasses(unreadableProviderClasses);
        return new ExternalCapacityReadResult(extItemUsed, extItemTotal, extFluidUsed, extFluidTotal,
                reliable, available, debugSuffix);
    }

    // ===================== 外储探测辅助 =====================

    static boolean isStorageBusProvider(IStorageProvider p) {
        return p != null && p.getClass().getName().toLowerCase(Locale.ROOT).contains("storagebus");
    }

    static @Nullable Object extractStorageBusInternalStorage(IStorageProvider p) {
        Object o = invokeOptional(p, "getInternalHandler");
        if (o != null) return o;
        o = invokeOptional(p, "getInventory");
        return o != null ? o : readOptionalField(p, "handler");
    }

    static List<Object> extractStorageBranches(Object root) {
        List<Object> branches = new ArrayList<>();
        if (root == null) return branches;
        Map<?, ?> map = null;
        Object mc = invokeOptional(root, "getStorages");
        if (mc instanceof Map<?, ?> m) map = m;
        else { Object fc = readOptionalField(root, "storages"); if (fc instanceof Map<?, ?> m) map = m; }
        if (map != null) { for (Object v : map.values()) if (v != null) branches.add(v); }
        if (branches.isEmpty()) branches.add(root);
        return branches;
    }

    static @Nullable IItemHandler unwrapItemHandler(@Nullable Object c) {
        if (c == null) return null;
        Object dc = unwrapDelegateChain(c, IItemHandler.class);
        if (dc instanceof IItemHandler h) return h;
        IItemHandler fm = readFieldByType(c, IItemHandler.class);
        if (fm == null) return null;
        Object fc = unwrapDelegateChain(fm, IItemHandler.class);
        return fc instanceof IItemHandler h ? h : fm;
    }

    static @Nullable IFluidHandler unwrapFluidHandler(@Nullable Object c) {
        if (c == null) return null;
        Object dc = unwrapDelegateChain(c, IFluidHandler.class);
        if (dc instanceof IFluidHandler h) return h;
        IFluidHandler fm = readFieldByType(c, IFluidHandler.class);
        if (fm == null) return null;
        Object fc = unwrapDelegateChain(fm, IFluidHandler.class);
        return fc instanceof IFluidHandler h ? h : fm;
    }

    static @Nullable Object readLikelyDelegate(Object t) {
        Object v = readOptionalField(t, "handler"); if (v != null) return v;
        v = readOptionalField(t, "delegate"); if (v != null) return v;
        v = invokeOptional(t, "getHandler"); if (v != null) return v;
        return invokeOptional(t, "getDelegate");
    }

    static @Nullable Object unwrapDelegateChain(@Nullable Object c, Class<?> expectedType) {
        if (c == null || expectedType == null) return null;
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Object cur = c, resolved = expectedType.isInstance(cur) ? cur : null;
        while (cur != null && visited.add(cur)) {
            Object d = readLikelyDelegate(cur);
            if (d == null) break;
            if (expectedType.isInstance(d)) resolved = d;
            cur = d;
        }
        return resolved;
    }

    // ===================== 物品探测 =====================

    static List<ItemStack> collectItemProbeCandidates(IItemHandler h, int slotCount) {
        List<ItemStack> cands = new ArrayList<>(MAX_ITEM_PROBE_CANDIDATES);
        for (int s = 0; s < slotCount; s++) {
            if (cands.size() >= MAX_ITEM_PROBE_CANDIDATES) break;
            try { ItemStack st = h.getStackInSlot(s); if (st.isEmpty()) continue;
                  ItemStack p = st.copy(); p.setCount(DEFAULT_ITEM_PROBE_COUNT); cands.add(p); }
            catch (RuntimeException ignored) {}
        }
        if (cands.isEmpty()) {
            cands.add(new ItemStack(Items.COBBLESTONE, DEFAULT_ITEM_PROBE_COUNT));
            cands.add(new ItemStack(Items.REDSTONE, DEFAULT_ITEM_PROBE_COUNT));
            cands.add(new ItemStack(Items.DIRT, DEFAULT_ITEM_PROBE_COUNT));
        }
        return cands;
    }

    static ItemSlotCapacityEstimate estimateItemSlotCapacity(IItemHandler h, int slot,
            ItemStack stackInSlot, List<ItemStack> probeCands, int[] probeBudget) {
        int stackMax = Math.max(0, stackInSlot.isEmpty() ? 64 : stackInSlot.getMaxStackSize());
        long baseTotal = Math.max(stackMax, stackInSlot.getCount());
        if (probeBudget[0] <= 0 || probeCands.isEmpty() || !stackInSlot.isEmpty())
            return new ItemSlotCapacityEstimate(baseTotal, true);
        int reqCount = computeItemProbeRequestCount(stackMax, stackInSlot.getCount());
        if (reqCount <= 0) return new ItemSlotCapacityEstimate(baseTotal, true);
        for (ItemStack pc : probeCands) {
            if (probeBudget[0] <= 0) break;
            int cnt = Math.min(reqCount, probeBudget[0]);
            SimulatedInsertProbe probe = probeSimulatedInsert(h, slot, pc, cnt);
            probeBudget[0] = Math.max(0, probeBudget[0] - cnt);
            if (probe.insertedUnits() > 0)
                return new ItemSlotCapacityEstimate(Math.max(baseTotal, stackInSlot.getCount() + probe.insertedUnits()), probe.reliable());
            if (!probe.reliable()) return new ItemSlotCapacityEstimate(baseTotal, false);
        }
        return new ItemSlotCapacityEstimate(baseTotal, true);
    }

    static int computeItemProbeRequestCount(int rawSlotLimit, int currentAmount) {
        return Math.max(1, Math.min(64, rawSlotLimit - currentAmount));
    }

    static SimulatedInsertProbe probeSimulatedInsert(IItemHandler h, int slot, ItemStack probe, int count) {
        ItemStack ps = probe.copy(); ps.setCount(count);
        try { ItemStack rem = h.insertItem(slot, ps, true); return new SimulatedInsertProbe(count - Math.max(0, rem.getCount()), true); }
        catch (RuntimeException ex) { return new SimulatedInsertProbe(0L, false); }
    }

    // ===================== 工具方法 =====================

    static void incrementCount(Map<String, Integer> counts, String key) { counts.merge(key, 1, Integer::sum); }

    static String summarizeFailureClasses(Map<String, Integer> counts) {
        if (counts.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(simpleClassName(e.getKey())).append("(").append(e.getValue()).append(")");
        }
        return sb.toString();
    }

    static String simpleClassName(String className) {
        int dot = className.lastIndexOf('.'); return dot >= 0 ? className.substring(dot + 1) : className;
    }
}

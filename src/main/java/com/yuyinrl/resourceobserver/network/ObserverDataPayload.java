package com.yuyinrl.resourceobserver.network;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端的观察者数据负载 —— 资源终端 GUI 的完整数据包。
 * <p>
 * 该数据包包含终端界面所需的所有数据：
 * - 观察者绑定状态和坐标
 * - 图表时间窗口和作用域设置
 * - 图表数据点序列
 * - 表格筛选/排序/分组状态
 * - 关注列表物品 ID
 * - 分组定义列表
 * - 所有绑定网络的详细数据（含每种物品的增量）
 * <p>
 * 使用自定义 StreamCodec 进行二进制序列化/反序列化，通过 NeoForge 网络通道传输。
 *
 * @param observerPos        观察者方块坐标
 * @param isBound            观察者是否已绑定网络
 * @param debugPreferred     是否优先显示调试视图
 * @param chartWindow        当前图表时间窗口
 * @param chartScope         图表作用域（全局/单物品）
 * @param chartScopeItemId   单物品作用域时的物品 ID
 * @param chartSeries        图表数据点列表
 * @param tableGroupFilterKey 表格分组筛选键
 * @param tableSortMode      表格排序模式
 * @param tableSortDesc      是否降序排列
 * @param tableStatusFilter  表格状态筛选
 * @param watchlistLimit     关注列表最大容量
 * @param watchlistItemIds   关注列表中的物品 ID 列表
 * @param groups             分组定义列表
 * @param bindings           所有绑定网络的数据条目
 */
public record ObserverDataPayload(
        BlockPos observerPos,
        boolean isBound,
        boolean debugPreferred,
        ChartWindow chartWindow,
        ChartScope chartScope,
        String chartScopeItemId,
        List<ChartPoint> chartSeries,
        String tableGroupFilterKey,
        TableSortMode tableSortMode,
        boolean tableSortDesc,
        TableStatusFilter tableStatusFilter,
        int watchlistLimit,
        List<String> watchlistItemIds,
        List<GroupEntry> groups,
        List<BindingEntry> bindings
) implements CustomPacketPayload {

    /**
     * 分组条目 —— 物品分组的定义信息。
     * @param key         分组唯一键
     * @param displayName 分组显示名称
     * @param systemGroup 是否为系统内置分组（不可删除）
     */
    public record GroupEntry(
            String key,
            String displayName,
            boolean systemGroup
    ) {
    }

    /**
     * 绑定条目 —— 单个网络绑定的完整数据。
     * @param networkType   网络类型（AE2_ITEMS / FLUX_ENERGY）
     * @param networkId     网络唯一标识
     * @param targetBlockId 目标方块注册 ID
     * @param displayName   显示名称
     * @param iconSprite    图标精灵路径
     * @param currentValue  当前值（物品总量/能量存储量）
     * @param capacity      容量/种类数
     * @param totalProduced 累计生产量
     * @param totalConsumed 累计消耗量
     * @param itemDeltas    每种物品的当前数量和增量
     * @param debugInfo     调试信息字符串
     */
    public record BindingEntry(
            String networkType,
            String networkId,
            String targetBlockId,
            String displayName,
            String iconSprite,
            long currentValue,
            long capacity,
            long totalProduced,
            long totalConsumed,
            List<ItemDeltaEntry> itemDeltas,
            String debugInfo
    ) {
    }

    /**
     * 物品增量条目 —— 单种物品的当前数量和采样增量。
     * @param itemId      物品注册 ID（如 "minecraft:iron_ingot"）
     * @param displayName 可读显示名称
     * @param groupKey    所属分组键
     * @param iconSprite  图标精灵路径
     * @param amount      当前库存数量
     * @param delta       与上次采样的变化量（正=生产，负=消耗）
     */
    public record ItemDeltaEntry(
            String itemId,
            String displayName,
            String groupKey,
            String iconSprite,
            long amount,
            long delta
    ) {
    }

    /**
     * 图表数据点 —— 一个时间桶的聚合数据。
     * @param slotIndex   桶索引（X 轴位置）
     * @param production  该桶内的生产总量
     * @param consumption 该桶内的消耗总量
     * @param net         净变化量（production - consumption）
     * @param stock       该桶的库存快照
     * @param hasFlow     是否有流量数据（用于区分"无数据"和"数据为0"）
     * @param hasStock    是否有库存数据
     */
    public record ChartPoint(
            int slotIndex,
            double production,
            double consumption,
            double net,
            double stock,
            boolean hasFlow,
            boolean hasStock
    ) {
    }

    /** 数据包类型标识 */
    public static final Type<ObserverDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ResourceObserverMod.MODID, "observer_data"));

    /**
     * 二进制流编解码器 —— 负责数据包的序列化（encode）和反序列化（decode）。
     * 编码顺序严格对应解码顺序，包含：
     * 基础信息 → 图表参数 → 图表数据 → 表格设置 → 关注列表 → 分组 → 绑定条目
     */
    public static final StreamCodec<FriendlyByteBuf, ObserverDataPayload> STREAM_CODEC =
            new StreamCodec<>() {
                /** 从字节缓冲区解码（反序列化）数据包 */
                @Override
                public ObserverDataPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    boolean bound = buf.readBoolean();
                    boolean debugPreferred = buf.readBoolean();
                    ChartWindow chartWindow = ChartWindow.fromId(buf.readVarInt());
                    ChartScope chartScope = ChartScope.fromId(buf.readVarInt());
                    String chartScopeItemId = buf.readBoolean() ? buf.readUtf(256) : "";
                    List<ChartPoint> chartSeries = readChartSeries(buf);
                    String tableGroupFilterKey = buf.readUtf(64);
                    TableSortMode sortMode = TableSortMode.fromId(buf.readVarInt());
                    boolean sortDesc = buf.readBoolean();
                    TableStatusFilter statusFilter = TableStatusFilter.fromId(buf.readVarInt());
                    int watchlistLimit = buf.readVarInt();
                    List<String> watchlistItemIds = readWatchlist(buf);
                    List<GroupEntry> groups = readGroups(buf);
                    int count = buf.readVarInt();
                    List<BindingEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new BindingEntry(
                                buf.readUtf(256),
                                buf.readUtf(512),
                                buf.readUtf(256),
                                buf.readUtf(256),
                                buf.readUtf(256),
                                buf.readLong(),
                                buf.readLong(),
                                buf.readLong(),
                                buf.readLong(),
                                readItemDeltas(buf),
                                buf.readUtf(512)
                        ));
                    }
                    return new ObserverDataPayload(
                            pos,
                            bound,
                            debugPreferred,
                            chartWindow,
                            chartScope,
                            chartScopeItemId,
                            chartSeries,
                            tableGroupFilterKey,
                            sortMode,
                            sortDesc,
                            statusFilter,
                            watchlistLimit,
                            watchlistItemIds,
                            groups,
                            entries
                    );
                }

                /** 将数据包编码（序列化）到字节缓冲区 */
                @Override
                public void encode(FriendlyByteBuf buf, ObserverDataPayload payload) {
                    buf.writeBlockPos(payload.observerPos);
                    buf.writeBoolean(payload.isBound);
                    buf.writeBoolean(payload.debugPreferred);
                    buf.writeVarInt(payload.chartWindow.id());
                    buf.writeVarInt(payload.chartScope.id());
                    boolean hasScopeItem = payload.chartScopeItemId != null && !payload.chartScopeItemId.isBlank();
                    buf.writeBoolean(hasScopeItem);
                    if (hasScopeItem) {
                        buf.writeUtf(payload.chartScopeItemId, 256);
                    }
                    writeChartSeries(buf, payload.chartSeries);
                    buf.writeUtf(payload.tableGroupFilterKey == null ? "" : payload.tableGroupFilterKey, 64);
                    buf.writeVarInt(payload.tableSortMode.id());
                    buf.writeBoolean(payload.tableSortDesc);
                    buf.writeVarInt(payload.tableStatusFilter.id());
                    buf.writeVarInt(payload.watchlistLimit);
                    writeWatchlist(buf, payload.watchlistItemIds);
                    writeGroups(buf, payload.groups);
                    buf.writeVarInt(payload.bindings.size());
                    for (BindingEntry entry : payload.bindings) {
                        buf.writeUtf(entry.networkType, 256);
                        buf.writeUtf(entry.networkId, 512);
                        buf.writeUtf(entry.targetBlockId, 256);
                        buf.writeUtf(entry.displayName(), 256);
                        buf.writeUtf(entry.iconSprite(), 256);
                        buf.writeLong(entry.currentValue);
                        buf.writeLong(entry.capacity);
                        buf.writeLong(entry.totalProduced);
                        buf.writeLong(entry.totalConsumed);
                        writeItemDeltas(buf, entry.itemDeltas());
                        buf.writeUtf(entry.debugInfo(), 512);
                    }
                }

                /** 反序列化物品增量列表 */
                private static List<ItemDeltaEntry> readItemDeltas(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<ItemDeltaEntry> result = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        result.add(new ItemDeltaEntry(
                                buf.readUtf(256),
                                buf.readUtf(256),
                                buf.readUtf(128),
                                buf.readUtf(256),
                                buf.readLong(),
                                buf.readLong()
                        ));
                    }
                    return result;
                }

                /** 序列化物品增量列表 */
                private static void writeItemDeltas(FriendlyByteBuf buf, List<ItemDeltaEntry> itemDeltas) {
                    buf.writeVarInt(itemDeltas.size());
                    for (ItemDeltaEntry itemDelta : itemDeltas) {
                        buf.writeUtf(itemDelta.itemId(), 256);
                        buf.writeUtf(itemDelta.displayName(), 256);
                        buf.writeUtf(itemDelta.groupKey(), 128);
                        buf.writeUtf(itemDelta.iconSprite(), 256);
                        buf.writeLong(itemDelta.amount());
                        buf.writeLong(itemDelta.delta());
                    }
                }

                /** 反序列化图表数据点列表 */
                private static List<ChartPoint> readChartSeries(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<ChartPoint> points = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        points.add(new ChartPoint(
                                buf.readVarInt(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readBoolean(),
                                buf.readBoolean()
                        ));
                    }
                    return points;
                }

                /** 序列化图表数据点列表 */
                private static void writeChartSeries(FriendlyByteBuf buf, List<ChartPoint> chartSeries) {
                    buf.writeVarInt(chartSeries.size());
                    for (ChartPoint point : chartSeries) {
                        buf.writeVarInt(point.slotIndex());
                        buf.writeDouble(point.production());
                        buf.writeDouble(point.consumption());
                        buf.writeDouble(point.net());
                        buf.writeDouble(point.stock());
                        buf.writeBoolean(point.hasFlow());
                        buf.writeBoolean(point.hasStock());
                    }
                }

                /** 反序列化关注列表 */
                private static List<String> readWatchlist(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<String> result = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        result.add(buf.readUtf(512));
                    }
                    return result;
                }

                /** 序列化关注列表 */
                private static void writeWatchlist(FriendlyByteBuf buf, List<String> watchlistItemIds) {
                    buf.writeVarInt(watchlistItemIds.size());
                    for (String itemId : watchlistItemIds) {
                        buf.writeUtf(itemId, 512);
                    }
                }

                /** 反序列化分组列表 */
                private static List<GroupEntry> readGroups(FriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<GroupEntry> groups = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        groups.add(new GroupEntry(
                                buf.readUtf(128),
                                buf.readUtf(128),
                                buf.readBoolean()
                        ));
                    }
                    return groups;
                }

                /** 序列化分组列表 */
                private static void writeGroups(FriendlyByteBuf buf, List<GroupEntry> groups) {
                    buf.writeVarInt(groups.size());
                    for (GroupEntry group : groups) {
                        buf.writeUtf(group.key(), 128);
                        buf.writeUtf(group.displayName(), 128);
                        buf.writeBoolean(group.systemGroup());
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

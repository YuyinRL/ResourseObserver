package com.yuyinrl.resourceobserver.service;

import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Observer 服务桥接层 —— Web API 与 BlockEntity 之间的唯一入口点。
 * <p>
 * Web Handler 通过此服务获取 Observer 数据，不再直接依赖 {@link ObserverBlockEntity}。
 * 未来 ObserverBlockEntity 内部重构时只需更新此服务即可，不影响 Handler。
 * <p>
 * Phase 1: 薄封装，委托给 ObserverBlockEntity 现有方法<br>
 * Phase 2: 引入数据快照 DTO，完全隔离领域模型
 */
public final class ObserverService {

    private ObserverService() {}

    /** 返回所有已加载的 Observer 方块实体列表 */
    public static List<ObserverBlockEntity> listObservers() {
        return ObserverBlockEntity.loadedObservers();
    }

    /** 按世界和坐标查找 Observer */
    public static Optional<ObserverBlockEntity> findByPos(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return Optional.empty();
        if (!(level.getBlockEntity(pos) instanceof ObserverBlockEntity be)) return Optional.empty();
        return Optional.of(be);
    }

    /** 生成 Observer 摘要信息（供列表 API 使用） */
    public static List<Map<String, Object>> buildObserverSummaries() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ObserverBlockEntity be : listObservers()) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("dimension", be.getLevel() != null ? be.getLevel().dimension().location().toString() : "unknown");
            row.put("x", be.getBlockPos().getX());
            row.put("y", be.getBlockPos().getY());
            row.put("z", be.getBlockPos().getZ());
            row.put("bindings", be.getBindings().size());
            row.put("bound", be.isBound());
            list.add(row);
        }
        return list;
    }

    /** 获取 Observer 的调试状态消息 */
    public static List<Component> getDebugMessages(ObserverBlockEntity be) {
        if (be == null) return List.of();
        return be.createDebugStatusMessages();
    }
}

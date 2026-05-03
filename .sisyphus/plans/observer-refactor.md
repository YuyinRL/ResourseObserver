# ObserverBlockEntity 架构重构设计

> Status: 进行中 | 创建: 2026-05-03

## 1. 现状分析

`ObserverBlockEntity.java` (2726 行) 承担 7 类职责，违反单一职责原则：

| 职责 | 行数估计 | 区域 |
|------|---------|------|
| AE2 网络采样 + 存储格探测 | ~800 | `sampleAe2Network()`, Cell/Provider 探测 |
| 数据存储/速率计算 (Maps) | ~300 | ae2ItemAmounts/Deltas/Rates, 双缓冲窗口 |
| Payload 组装 | ~200 | 构建 ObserverDataPayload 的 getter 方法 |
| NBT 持久化 | ~100 | `saveAdditional()` / `loadAdditional()` |
| 绑定管理 | ~50 | `addBinding()`, `clearAllBindings()` |
| 调试/探测模拟 | ~150 | `createDebugStatusMessages()` |
| 反射工具 | ~200 | CellMetrics 反射访问 |

Web Handler 直接引用 `ObserverBlockEntity` 类型，跨包耦合。

## 2. 目标架构

```
ObserverBlockEntity (保留：tick、bindings、NBT、数据访问委托)
    │
    ├── Ae2DataStore (新) ── 所有采样数据的 Map 容器 + 速率计算
    │       └── 包含：ae2ItemAmounts, ae2ItemDeltas, rates, 双缓冲窗口, KPI 聚合
    │
    ├── Ae2Sampler (新) ── AE2 网络采样 + 存储格探测 + 容量指标
    │       └── 使用 Ae2DataStore 存储结果
    │
    └── ObserverService (新) ── 桥接层
            │
            ├── 提供 ObserverSnapshot 给 Web Handler
            ├── 通过 BlockPos 查找 ObserverBlockEntity
            └── Web Handler 只依赖 ObserverService，不依赖 ObserverBlockEntity

WebHandler ──→ ObserverService ──→ ObserverBlockEntity ──→ Ae2DataStore
```

## 3. 实施阶段

### Phase 1: Ae2DataStore（数据容器，最低风险）
- 新建 `world/block/entity/Ae2DataStore.java`
- 迁移所有 Map 字段 (ae2ItemAmounts, ae2ItemDeltas, ae2ItemRatesPerMin, ...)
- 迁移速率计算方法 (computeDeltas, 双缓冲窗口逻辑)
- ObserverBlockEntity 持有 Ae2DataStore 实例，getter 方法委托给它

### Phase 2: Ae2Sampler（采样逻辑）
- 新建 `world/block/entity/Ae2Sampler.java`
- 迁移 `sampleAe2Network()` 及其调用的反射/探测方法
- Ae2Sampler 接收 Ae2DataStore 引用，采样结果写入 DataStore

### Phase 3: ObserverService（桥接层）
- 新建 `service/ObserverService.java`
- 提供 `getSnapshot(BlockPos)` → 返回不可变快照
- 提供 `listObservers()` → 返回所有活跃 Observer 信息
- Web Handler 改为依赖 ObserverService

### Phase 4: 清理 ObserverBlockEntity
- 移除已迁移的方法
- 保留 tick()、绑定管理、NBT 持久化
- `tick()` 中：调用 `Ae2Sampler.sample()` + `HistoryRecorder.record()`

## 4. 不变量

- 所有现有 public API 保持兼容
- 不改变任何数据语义
- 采样时序不变（SAMPLE_INTERVAL=10 tick）

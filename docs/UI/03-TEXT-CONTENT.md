# Complete Text Content (i18n Translation Map)

All UI text is centralized in `src/app/i18n.tsx`. Below is the complete translation table.

---

## App Shell

| Key | English | 中文 |
|---|---|---|
| `app.title.overview` | Operations Dashboard | 运营仪表盘 |
| `app.title.storage` | Storage Network Controller | 存储网络控制器 |
| `app.title.power` | Power Grid Management | 电力网格管理 |
| `app.title.default` | Dashboard | 仪表盘 |
| `app.subtitle.overview` | Global monitoring of all linked storage networks and resource flow. | 全局监控所有关联存储网络及资源流动。 |
| `app.subtitle.storage` | Inventory health and logistical buffer tracking across all nodes. | 跨节点库存健康状况与物流缓冲跟踪。 |
| `app.subtitle.power` | Energy load balancing and production line efficiency monitoring. | 能源负载均衡与生产线效率监控。 |
| `app.subtitle.default` | Figma Make Resource Terminal v2.4 | Figma Make 资源终端 v2.4 |
| `app.linkEstablished` | Link Established | 连接已建立 |
| `app.footer.encrypted` | Encrypted Data Link [AES-256] | 加密数据链路 [AES-256] |
| `app.footer.subnet` | Sub-Network Sector 7G | 子网络 7G 区段 |
| `app.footer.systemNominal` | System Nominal | 系统正常 |

## Sidebar

| Key | English | 中文 |
|---|---|---|
| `sidebar.title` | ResourceObserver | ResourceObserver |
| `sidebar.subtitle` | Resource Terminal | 资源终端 |
| `sidebar.search` | Search items... | 搜索物品... |
| `sidebar.nav.overview` | Overview | 总览 |
| `sidebar.nav.storage` | Storage Network | 存储网络 |
| `sidebar.nav.power` | Power Network | 电力网络 |
| `sidebar.systemTime` | System Time | 系统时间 |
| `sidebar.netHealth` | Net Health | 网络健康 |

## Overview — KPIs

| Key | English | 中文 |
|---|---|---|
| `overview.kpi.netProduction` | Net Production | 净产出 |
| `overview.kpi.netConsumption` | Net Consumption | 净消耗 |
| `overview.kpi.storageStatus` | Storage Status | 存储状态 |
| `overview.kpi.systemEfficiency` | System Efficiency | 系统效率 |
| `overview.kpi.active` | Active | 运行中 |
| `overview.kpi.vsCycle` | vs last cycle | 较上一周期 |

## Overview — Chart

| Key | English | 中文 |
|---|---|---|
| `overview.chart.globalFlow` | Global Resource Flow | 全局资源流动 |
| `overview.chart.flowRate` | Flow Rate | 流速 |
| `overview.chart.globalDesc` | Combined production vs consumption rates across all sub-networks. | 所有子网络的综合生产与消耗速率。 |
| `overview.chart.itemDesc` | Real-time analytics for | 实时分析： |
| `overview.chart.resetGlobal` | Reset to Global | 重置为全局 |
| `overview.chart.production` | Production | 生产 |
| `overview.chart.consumption` | Consumption | 消耗 |

## Overview — Watchlist & Table

| Key | English | 中文 |
|---|---|---|
| `overview.watchlist` | Watchlist | 关注列表 |
| `overview.watchlist.netChange` | Net Change | 净变化 |
| `overview.watchlist.stock` | Stock | 库存 |
| `overview.table.title` | Item Circulation | 物品流通 |
| `overview.table.itemsActive` | items active | 个物品活跃 |
| `overview.table.resourceNode` | Resource Node | 资源节点 |
| `overview.table.production` | Production | 生产 |
| `overview.table.consumption` | Consumption | 消耗 |
| `overview.table.netChange` | Net Change | 净变化 |
| `overview.table.inventoryBalance` | Inventory Balance | 库存余量 |

## Overview — Categories

| Key | English | 中文 |
|---|---|---|
| `overview.category.raw` | Raw | 原材料 |
| `overview.category.intermediate` | Intermediate | 中间产品 |
| `overview.category.finished` | Finished | 成品 |
| `overview.category.commonParts` | Common Parts | 通用零件 |

## Resource Names (shared across pages)

| Key | English | 中文 |
|---|---|---|
| `resource.ironOre` | Iron Ore | 铁矿石 |
| `resource.copperOre` | Copper Ore | 铜矿石 |
| `resource.steelPlate` | Steel Plate | 钢板 |
| `resource.copperWire` | Copper Wire | 铜线 |
| `resource.microchip` | Microchip | 微芯片 |
| `resource.energyCell` | Energy Cell | 能量电池 |
| `resource.screw` | Screw | 螺丝 |
| `resource.gear` | Gear | 齿轮 |

## Storage Network

| Key | English | 中文 |
|---|---|---|
| `storage.units` | Storage Units | 存储单元 |
| `storage.globalUsage` | Global Usage Summary | 全局使用概览 |
| `storage.totalUsed` | Total Used | 总使用率 |
| `storage.globalInventory` | Global Inventory Health | 全局库存健康 |
| `storage.contents` | Contents: | 内容： |
| `storage.itemStatus` | Real-time status of items in linked networks | 已连接网络中物品的实时状态 |
| `storage.allStatus` | All Status | 全部状态 |
| `storage.deficitsOnly` | Deficits Only | 仅显示不足 |
| `storage.stable` | Stable | 稳定 |
| `storage.warning` | Warning (< 30s) | 警告 (< 30秒) |
| `storage.critical` | Critical | 严重 |
| `storage.itemIdentity` | Item Identity | 物品标识 |
| `storage.globalStock` | Global Stock | 全局库存 |
| `storage.burnRate` | Burn Rate | 消耗速率 |
| `storage.estimatedBuffer` | Estimated Buffer (Time) | 预估缓冲（时间） |
| `storage.remaining` | Remaining | 剩余 |
| `storage.belowThreshold` | 3 Items below critical buffer threshold | 3个物品低于临界缓冲阈值 |
| `storage.recalculate` | Recalculate Logistics Pathways | 重新计算物流路径 |
| `storage.distribution.raw` | Raw Materials | 原材料 |
| `storage.distribution.intermediate` | Intermediates | 中间产品 |
| `storage.distribution.finished` | Finished | 成品 |
| `storage.distribution.other` | Other | 其他 |

## Storage — Node Names & Types

| Key | English | 中文 |
|---|---|---|
| `storage.node.hubAlpha` | Hub Alpha - Central Storage | Alpha 中心 - 中央存储 |
| `storage.node.outpostBeta` | Outpost Beta - Mining Buffers | Beta 前哨 - 采矿缓冲 |
| `storage.node.line3Output` | Assembly Line 3 - Finished | 装配线 3 - 成品区 |
| `storage.node.sector4Silo` | Sector 4 - Resource Silo | 4号区 - 资源仓 |
| `storage.node.type.centralHub` | Central ME Hub | 中央 ME 中心 |
| `storage.node.type.localBuffer` | Local Buffer | 本地缓冲 |
| `storage.node.type.outputBuffer` | Output Buffer | 输出缓冲 |
| `storage.node.type.massStorage` | Mass Storage | 大容量存储 |
| `storage.node.status.healthy` | Healthy | 健康 |
| `storage.node.status.alert` | Alert | 警报 |

## Power Network

| Key | English | 中文 |
|---|---|---|
| `power.gridLoad` | Grid Load Dynamics | 电网负载动态 |
| `power.gridLoadDesc` | Real-time power generation vs consumption (last 24h) | 实时电力生产与消耗（最近24小时） |
| `power.generation` | Generation | 发电量 |
| `power.peakLoad` | Peak Load | 峰值负载 |
| `power.loadDistribution` | Load Distribution | 负载分布 |
| `power.loadDistDesc` | Energy split by device category | 按设备类别的能源分配 |
| `power.currentDemand` | Current Demand | 当前需求 |
| `power.usage` | Usage | 使用率 |
| `power.lineConsumption` | Production Line Consumption | 生产线能耗 |
| `power.lineConsumptionDesc` | Energy usage grouped by item production chains | 按产品链分组的能源消耗 |
| `power.overloadDetected` | 1 Overload Detected | 检测到 1 处过载 |
| `power.productionLine` | Production Line | 生产线 |
| `power.assignedItem` | Assigned Item | 分配产品 |
| `power.deviceCount` | Device Count | 设备数量 |
| `power.consumption` | Consumption | 能耗 |
| `power.capacityUtilization` | Capacity Utilization | 容量利用率 |
| `power.status` | Status | 状态 |
| `power.units` | Units | 台 |
| `power.mainGrid` | Main Grid: Operational | 主电网：运行中 |
| `power.reserves` | Reserves: 94.2% | 储备：94.2% |
| `power.optimizeLoad` | Optimize Load Balancers | 优化负载均衡器 |
| `power.overloadTitle` | Critical Overload: Electronics Assembly | 严重过载：电子装配线 |
| `power.overloadDesc` | Throughput Loss Detected: −20% due to power shortage on Phase A | 检测到吞吐量下降：因A相电力不足导致 −20% |
| `power.reroutePower` | Reroute Power | 重新分配电力 |

## Power — Line & Category Names

| Key | English | 中文 |
|---|---|---|
| `power.dist.mining` | Mining | 采矿 |
| `power.dist.assembly` | Assembly | 装配 |
| `power.dist.logistics` | Logistics | 物流 |
| `power.line.ironChain` | Iron Production Chain | 铁矿生产链 |
| `power.line.copperChain` | Copper Production Chain | 铜矿生产链 |
| `power.line.steelProcess` | Steel Processing Line | 钢材加工线 |
| `power.line.electronicsAssembly` | Electronics Assembly | 电子装配线 |
| `power.line.logisticsHub` | Main Logistics Network | 主物流网络 |
| `power.line.item.transportHub` | Transport Hub | 运输中心 |

## Language Toggle

| Key | English | 中文 |
|---|---|---|
| `lang.toggle` | EN / 中文 | 中文 / EN |

---

## Unused Keys (legacy, still in i18n.tsx but not rendered)

These keys exist in the translation file but are no longer used in any component:

| Key | Note |
|---|---|
| `app.gridLatency` | Removed from header |
| `app.coreStatus` | Removed from header |
| `app.coreStatus.stable` | Removed from header |
| `app.protocol` | Removed from header |
| `sidebar.sub.ores` | Sidebar sub-groups removed |
| `sidebar.sub.ingots` | Sidebar sub-groups removed |
| `sidebar.sub.components` | Sidebar sub-groups removed |
| `sidebar.sub.electronics` | Sidebar sub-groups removed |
| `sidebar.sub.chemicals` | Sidebar sub-groups removed |
| `sidebar.sub.finished` | Sidebar sub-groups removed |

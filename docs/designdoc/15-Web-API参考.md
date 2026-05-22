---
title: 15-Web API 参考
tags: [Web, API, Reference]
aliases: [API Reference, Web API Reference]
---

# Web API 参考（v1）

> 本文面向**第三方开发者**与**前端集成者**，提供 Resource Observer 模组内置 Web 服务的完整 REST API 详细说明。
> 架构层面的设计与生命周期请见 [[14-Web服务]]，本文聚焦"如何调用"。

---

## 1. 概述

Resource Observer 在 Minecraft 服务端进程内启动一个轻量级 HTTP 服务（基于 JDK `com.sun.net.httpserver.HttpServer`），用于：

- 向 Web Dashboard SPA 提供资源/网络/合成的实时数据
- 暴露给第三方工具（外部 dashboard、Discord bot、Prometheus exporter 等）

服务在 `ServerStartedEvent` 时启动，`ServerStoppingEvent` 时关闭，绑定到主世界 `MinecraftServer` 实例的生命周期。

### 1.1 基础信息

| 项 | 值 |
|----|----|
| Base URL | `http://<host>:<port>/` |
| 默认监听 | `127.0.0.1:28080` |
| 协议 | HTTP/1.1（无 TLS） |
| 认证 | **无**（仅本机访问；如需对外暴露请用反向代理鉴权） |
| 字符编码 | UTF-8 |
| API 版本 | `1`（见 `/api/meta`） |

> **安全警告**：本服务无内置鉴权。默认仅监听 `127.0.0.1`。如将 `web.host` 改为 `0.0.0.0` 暴露至局域网/公网，请自行通过 nginx/Caddy 等加上鉴权。

### 1.2 配置文件

文件位置：`<world>/serverconfig/resourceobserver-web.toml`（单人）或 `config/resourceobserver-web.toml`（专用服务端）。

```toml
[web]
enabled = true
host = "127.0.0.1"
port = 28080
corsAllowAll = true
```

### 1.3 CORS

当 `corsAllowAll = true`（默认）时，所有 `/api/*` 响应附带：

```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, OPTIONS
Access-Control-Allow-Headers: Content-Type
```

`OPTIONS` 预检请求统一返回 `204 No Content`。

### 1.4 错误响应

所有 API 错误返回 JSON：

```json
{ "error": "<message>", "status": <int> }
```

| 状态码 | 含义 |
|--------|------|
| 400 | 参数缺失或非法 |
| 404 | Observer/路径不存在 |
| 405 | 方法不允许（仅接受 GET 或仅接受 POST） |
| 409 | 业务冲突（如 confirm 用了过期的 planId） |
| 500 | 内部错误 |
| 504 | 主线程超时（见 §1.5） |

### 1.5 线程模型

HTTP 工作线程池为 4 个 daemon 线程，名为 `ResourceObserver-Web`。**任何访问游戏世界/方块实体的逻辑必须切回主线程**（处理器统一使用 `MinecraftServer.execute(Runnable)`，主线程超时时间 3 秒，超时返回 504）。

### 1.6 通用约定

- `dim`：维度 ID，如 `minecraft:overworld`、`minecraft:the_nether`。**注意 URL 中的冒号需要 URL 编码**为 `%3A`。
- `x`、`y`、`z`：方块坐标，整数。
- `itemId`：
  - 普通物品：`<namespace>:<path>`，例如 `minecraft:redstone`
  - 流体：以 `fluid:` 前缀，例如 `fluid:minecraft:water`
- `networkId`：AE2/Flux 网络的内部唯一 ID，形如 `<targetBlockId>@<encodedPos>`，由列表接口返回，请勿手工拼装。
- `entryType`：`"ITEM"` 或 `"FLUID"`，由 `itemId` 是否带 `fluid:` 前缀派生。

---

## 2. Endpoint 列表

| Method | Path | 用途 |
|--------|------|------|
| GET | `/api/health` | 健康检查 |
| GET | `/api/meta` | 服务元数据（API 版本、可用集成、端点目录） |
| GET | `/api/observers` | 列出所有已加载的 Observer |
| GET | `/api/observers/{dim}/{x}/{y}/{z}` | 单个 Observer 详细快照 |
| GET | `/api/observers/{dim}/{x}/{y}/{z}/items` | 分页查询 AE2 物品列表 |
| GET | `/api/observers/{dim}/{x}/{y}/{z}/history` | 时间序列图表数据 |
| GET | `/api/observers/{dim}/{x}/{y}/{z}/crafting` | AE2 合成相关汇总 |
| POST | `/api/observers/{dim}/{x}/{y}/{z}/crafting/plan` | 计算合成计划摘要 |
| POST | `/api/observers/{dim}/{x}/{y}/{z}/crafting/confirm` | 提交计划 |
| POST | `/api/observers/{dim}/{x}/{y}/{z}/crafting/cancel` | 取消缓存的计划 |
| POST | `/api/observers/{dim}/{x}/{y}/{z}/crafting/tree` | 查询合成树详情 |
| GET | `/api/observers/{dim}/{x}/{y}/{z}/debug/sampler` | 高精度采样诊断 |
| GET | `/api/icon/{namespace}/{path}` | 物品/方块图标 PNG |
| GET | `/`、`/<spa-path>` | Web Dashboard SPA（静态资源） |

---

## 3. 详细说明

### 3.1 GET `/api/health`

**用途**：健康检查端点，可用于反向代理探针、监控告警。

**响应**（200）：
```json
{
  "status": "ok",
  "modid": "resourceobserver",
  "serverName": "<MOTD>",
  "tickCount": 12345,
  "singleplayer": false
}
```

---

### 3.2 GET `/api/meta`

**用途**：返回服务元数据；前端启动时调用一次。

**响应**（200）：
```json
{
  "modid": "resourceobserver",
  "apiVersion": 1,
  "integrations": { "ae2": true, "fluxNetworks": true, "mekanism": false },
  "endpoints": { "health": "/api/health", "...": "..." },
  "iconsAvailable": true
}
```

`apiVersion` 用于客户端版本兼容性判断。本文档对应 `apiVersion = 1`。

---

### 3.3 GET `/api/observers`

**用途**：列出所有已加载维度中已绑定/已放置的 Observer。

**响应**（200）：
```json
{
  "count": 2,
  "observers": [
    {
      "dimension": "minecraft:overworld",
      "x": 12, "y": 64, "z": -88,
      "bound": true,
      "bindingCount": 2,
      "displayName": "Observer @ overworld [12,64,-88]",
      "id": "minecraft:overworld/12/64/-88"
    }
  ]
}
```

---

### 3.4 GET `/api/observers/{dim}/{x}/{y}/{z}`

**用途**：单个 Observer 的详细数据，包含全部绑定网络的快照。

**示例**：`GET /api/observers/minecraft%3Aoverworld/12/64/-88`

**响应**（200）：
```json
{
  "dimension": "minecraft:overworld",
  "x": 12, "y": 64, "z": -88,
  "bound": true,
  "bindingCount": 2,
  "displayName": "...",
  "id": "...",
  "bindings": [
    {
      "networkType": "AE2_ITEMS",
      "networkId": "ae2:controller@12345",
      "targetBlockId": "ae2:controller",
      "currentValue": 100000,
      "capacity": 250000,
      "totalProduced": 12345,
      "totalConsumed": 6789,
      "items": [
        {
          "id": "minecraft:redstone",
          "amount": 1024,
          "production": 12.5,
          "consumption": 3.2,
          "net": 9.3,
          "translationKey": "item.minecraft.redstone",
          "displayName": "红石粉"
        }
      ],
      "cellCapacity": {
        "itemUsedBytes": 1234, "itemTotalBytes": 8192,
        "itemUsedTypes": 56,   "itemTotalTypes": 63,
        "itemUsedUnits": 2048, "itemMaxUnits": 16384,
        "fluidUsedBytes": 0,   "fluidTotalBytes": 0,
        "fluidUsedUnits": 0,   "fluidMaxUnits": 0,
        "reliable": true,
        "available": true
      }
    },
    {
      "networkType": "FLUX_ENERGY",
      "networkId": "...",
      "targetBlockId": "fluxnetworks:flux_plug",
      "currentValue": 5000000,
      "capacity": 10000000,
      "totalProduced": 0,
      "totalConsumed": 0,
      "flux": {
        "totalEnergy": 5000000,
        "totalMaxEnergyStorage": 10000000,
        "totalBuffer": 4500000,
        "energyInput": 800,
        "energyOutput": 600,
        "networkName": "Main Grid",
        "networkId": 1,
        "networkColor": 16711680,
        "plugCount": 2,
        "pointCount": 3,
        "storageCount": 1,
        "controllerCount": 1,
        "deviceCount": 7,
        "devices": [
          {
            "deviceType": "POINT",
            "customName": "Quarry Output",
            "transferChange": 800,
            "transferBuffer": 1024,
            "rawLimit": 0,
            "maxTransferLimit": 9223372036854775807,
            "maxEnergyStorage": 1000000,
            "posKey": 12345678901234,
            "externalEnergyStored": 100,
            "externalEnergyCapacity": 200,
            "externalRefs": [
              {
                "extId": "minecraft:overworld/12345/64/-88/north",
                "displayName": "minecraft:furnace",
                "stored": 100,
                "capacity": 200,
                "maxAcceptPerTick": 0
              }
            ]
          }
        ]
      }
    }
  ]
}
```

`networkType` 取值：`"AE2_ITEMS"` / `"FLUX_ENERGY"` / `"MEK_ENERGY"`。

---

### 3.5 GET `/api/observers/{dim}/{x}/{y}/{z}/items`

**用途**：分页查询 AE2 物品列表（适合大型网络，避免一次性返回全部）。

**Query 参数**：

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `offset` | int | `0` | 偏移量 |
| `limit` | int | `100` | 单页条数；上限 `500` |
| `q` | string | `""` | 子串搜索，匹配 `id` 或 `displayName`（不区分大小写） |
| `sort` | enum | `"amount"` | `amount` / `name` / `production` / `consumption` / `net` / `remainingMinutes` |
| `dir` | enum | `"desc"` | `asc` / `desc` |
| `alertsOnly` | bool | `false` | 仅返回 `remainingMinutes < 30` 的物品 |

**响应**（200）：
```json
{
  "total": 312, "offset": 0, "limit": 100, "sort": "amount", "dir": "desc",
  "items": [
    {
      "id": "minecraft:redstone",
      "displayName": "红石粉",
      "translationKey": "item.minecraft.redstone",
      "amount": 1024,
      "production": 12.5,
      "consumption": 3.2,
      "net": 9.3,
      "capacity": 1536.0,
      "remainingMinutes": 320.0,
      "networkId": "ae2:controller@12345"
    }
  ]
}
```

> `remainingMinutes` 公式：`amount / max(consumption, 1.0)`；用于估算"耗光时间"。
> `capacity` 字段：当前为 `max(amount * 1.5, 1000)` 占位（AE2 不暴露单 type 容量）。

---

### 3.6 GET `/api/observers/{dim}/{x}/{y}/{z}/history`

**用途**：返回时间序列图表数据，覆盖 4 档时间颗粒度。

**Query 参数**：

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `range` | enum | `"short"` | `detail` / `short` / `medium` / `long` |
| `item` | string | -- | 可选 itemId；提供则返回该物品序列（ChartScope.ITEM），否则返回全网络合计 |

**range 映射**：

| range | 可见窗口 | bucket 间隔 | 缓冲窗口 | 说明 |
|-------|---------|------------|---------|------|
| `detail` | 1 分钟 | 1 秒 | 5 分钟 | 走 `WebHighPrecisionSampler`（0.25s 桶 + 3 秒滑动窗口求速率） |
| `short` | 1 分钟 | 10 秒 | 5 分钟 | 默认 |
| `medium` | 15 分钟 | 1 分钟 | 75 分钟 | |
| `long` | 1 小时 | 5 分钟 | 5 小时 | |

**响应**（200）：
```json
{
  "range": "short",
  "bucketCount": 30,
  "visibleBucketCount": 6,
  "bufferMultiplier": 5,
  "bucketSeconds": 10.0,
  "gameTime": 12345,
  "latestBucket": 1234,
  "serverTimeMs": 1700000000000,
  "scope": "global",
  "points": [
    {
      "t": 0,
      "bucket": 1234,
      "x": 12345.0,
      "produced": 120.0,
      "consumed": 60.0,
      "net": 60.0,
      "stock": 1024,
      "hasFlow": true,
      "hasStock": true,
      "sampleCount": 6
    }
  ]
}
```

`detail` 档额外字段：`highPrecision: true`、`rateWindowSeconds: 3.0`。

`scope` 在指定 `item` 时为 `"item"`，否则为 `"global"`，并附带 `itemId` 字段。

> `produced` / `consumed` 单位均为**每分钟**（已按窗口归一化）。

---

### 3.7 GET `/api/observers/{dim}/{x}/{y}/{z}/crafting`

**用途**：AE2 合成相关汇总 —— 可合成物品、活跃任务、CPU/存储统计。

**响应**（200）：
```json
{
  "dimension": "minecraft:overworld",
  "x": 12, "y": 64, "z": -88,
  "bindings": [
    {
      "networkId": "ae2:controller@12345",
      "targetBlockId": "ae2:controller",
      "craftables": [
        { "itemId": "minecraft:redstone", "entryType": "ITEM", "displayName": "红石粉", "translationKey": "..." }
      ],
      "jobs": [
        {
          "cpuName": "Main CPU",
          "busy": true,
          "outputItemId": "minecraft:diamond",
          "outputDisplayName": "钻石",
          "totalAmount": 64,
          "remainingAmount": 32,
          "jobId": "uuid",
          "storageBytes": 4096,
          "coProcessors": 4,
          "progressFraction": 0.5,
          "elapsedMillis": 30000,
          "treeId": "tree-uuid"
        }
      ],
      "activeJobCount": 1,
      "storage": {
        "cpuCount": 4,
        "busyCpuCount": 1,
        "totalStorageBytes": 65536,
        "totalCoProcessors": 16,
        "reliable": true
      }
    }
  ]
}
```

---

### 3.8 POST `/api/observers/{dim}/{x}/{y}/{z}/crafting/plan`

**用途**：异步触发 AE2 合成计划计算，返回所需材料/缺料/CPU 候选与一次性 `planId`。

**Body**（JSON 或 query-style，使用正则解析 `STRING_FIELD` / `NUMBER_FIELD`）：
```json
{ "networkId": "...", "itemId": "minecraft:diamond", "amount": 64 }
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `networkId` | string | ✅ | 目标 AE2 网络 ID |
| `itemId` | string | ✅ | 物品 ID（流体加 `fluid:` 前缀） |
| `amount` | int | ✅ | 数量（>0） |

**响应**（200，**注意**即使计算失败也返回 200，根据 `status` 判断）：
```json
{
  "ok": false,
  "status": "SUCCESS|MISSING|CALCULATION_TIMEOUT|GRID_UNAVAILABLE|...",
  "message": "",
  "planId": "uuid",
  "simulation": true,
  "bytes": 1024,
  "finalOutputItemId": "minecraft:diamond",
  "finalOutputDisplayName": "钻石",
  "finalOutputTranslationKey": "...",
  "finalOutputAmount": 64,
  "usedItems": [ { "itemId": "...", "entryType": "ITEM", "displayName": "...", "amount": 100 } ],
  "missingItems": [ /* 同 stack 结构 */ ],
  "emittedItems": [ /* 同 */ ],
  "patternTimes": [ /* 同 */ ],
  "cpus": [ { "name": "Main", "storageBytes": 4096, "coProcessors": 4, "busy": false } ],
  "treeId": "tree-uuid",
  "tree": { /* CraftingTreeNode（见 §4.4） */ }
}
```

> 后端 12 秒超时；超时返回 `status: "CALCULATION_TIMEOUT"`。

> `planId` 仅在 SUCCESS 时存在；其作为 `confirm`/`cancel` 入参。

**错误**：
- `400` 缺 `networkId` / `itemId` / `amount<=0`
- `504` 主线程超时

---

### 3.9 POST `/api/observers/{dim}/{x}/{y}/{z}/crafting/confirm`

**用途**：使用 `planId` 提交计划至 AE2 网络。

**Body**：
```json
{ "planId": "uuid", "cpu": "Main CPU" }
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `planId` | ✅ | 来自 `plan` 的返回 |
| `cpu` | -- | 可选，指定 CPU 名称；空则由 AE2 选择 |

**响应**：
- 成功 200：`{ "ok": true, "status": "SUCCESS", "message": "", "linkId": "..." }`
- 失败 409：`{ "ok": false, "status": "...", "message": "..." }`

---

### 3.10 POST `/api/observers/{dim}/{x}/{y}/{z}/crafting/cancel`

**用途**：主动丢弃尚未 confirm 的 plan 缓存。

**Body**：`{ "planId": "uuid" }`

**响应**（200）：`{ "ok": true }`

---

### 3.11 POST `/api/observers/{dim}/{x}/{y}/{z}/crafting/tree`

**用途**：根据 `treeId` 查询合成树详情（plan 时 `tree` 字段过大可裁剪，再用此接口按需查询）。

**Body**：`{ "treeId": "..." }`

**响应**：
- 200：`{ "treeId": "...", "root": <CraftingTreeNode> }`
- 404：树已过期。

---

### 3.12 GET `/api/observers/{dim}/{x}/{y}/{z}/debug/sampler`

**用途**：返回 `WebHighPrecisionSampler` 的运行时诊断信息，用于前端调试面板。

**响应**（200）：
```json
{
  "requestedIntervalTicks": 5,
  "active": true,
  "globalDemandActive": true,
  "itemDemandActive": false,
  "demandRemainingMs": 12000,
  "latestBucket": 1234,
  "bufferBuckets": 1200,
  "bucketTicks": 5,
  "minValidBuckets": 4,
  "gameTime": 12345,
  "serverTimeMs": 1700000000000,
  "networks": [
    {
      "networkId": "...",
      "validBuckets": 1200,
      "totalSamples": 12345,
      "lastSampleGameTime": 12340,
      "previousSnapshotSize": 56,
      "recentBuckets": [
        {
          "bucket": 1234,
          "sampleCount": 5,
          "observedTicks": 5,
          "producedPerMinute": 120.0,
          "consumedPerMinute": 60.0,
          "stock": 1024
        }
      ]
    }
  ]
}
```

---

### 3.13 GET `/api/icon/{namespace}/{path}`

**用途**：返回物品或方块的 16×16 PNG 图标（动画条带自动裁首帧）。

**示例**：`GET /api/icon/minecraft/redstone`

**行为**：
1. 内存缓存命中 → 直接返回
2. 磁盘缓存命中 → 写内存缓存返回
3. 集成端：调用客户端 `IconRenderer` 渲染并写盘
4. 专用服务端：发 `IconRequestPayload` 让在线客户端代渲染并上传（10 秒节流），本次请求返回 `404`，下次再请求则可命中

**响应**：
- `200` `image/png`，浏览器缓存 5 分钟
- `404` 暂不可用（专用服务端首次访问）

---

### 3.14 静态资源

`GET /<path>` —— 由 `StaticHandler` 从 jar 资源 `assets/resourceobserver/web/` 读取。

- `/` 与 `/index.html` 返回 SPA 入口；`Cache-Control: no-cache`
- 带 hash 的 `/assets/*` 文件长缓存（`max-age=31536000, immutable`）
- 未命中且无扩展名 → SPA 回退到 `index.html`，便于 React Router 直接刷新子路由
- 拒绝路径中含 `..`

---

## 4. 数据模型

### 4.1 `BindingStats`

| 字段 | 类型 | 含义 |
|------|------|------|
| `currentValue` | long | 当前值（AE2 总数 / Flux 储电量） |
| `capacity` | long | 容量（AE2 占位 / Flux maxEnergyStorage） |
| `totalProduced` | long | 累计生产 |
| `totalConsumed` | long | 累计消耗 |

### 4.2 `Ae2CellCapacityMetrics`

详见 §3.4 cellCapacity 字段。`reliable=false` 表示反射读取失败时使用了估算值；`available=false` 表示该网络无 ME 存储驱动器。

### 4.3 `FluxSampleResult` / `FluxDeviceSnapshot`

详见 §3.4 flux 字段。`networkColor` 为 RGB 整数（前端 `'#' + value.toString(16).padStart(6,'0')` 渲染）。

### 4.4 `CraftingTreeNode`

```json
{
  "itemId": "...",
  "entryType": "ITEM",
  "displayName": "...",
  "translationKey": "...",
  "requiredAmount": 100,
  "perExecOutAmount": 4,
  "timesExecuted": 25,
  "isMissing": false,
  "isLoop": false,
  "truncated": false,
  "children": [ /* 递归节点 */ ]
}
```

`isLoop`：检测到循环依赖。`truncated`：节点深度超限被截断。

### 4.5 `ChartPoint`

详见 §3.6 points 字段。`hasFlow=false` 表示该 bucket 无有效采样；`hasStock=false` 表示库存数据不可用。

---

## 5. 协议约定与坑

### 5.1 itemId

- 普通物品：`<namespace>:<path>`
- 流体：`fluid:<namespace>:<path>`（注意只有一个前缀，namespace 仍在内层）
- API 返回的 `entryType` 字段是从 `itemId.startsWith("fluid:")` 派生的字符串，仅有 `"ITEM"` / `"FLUID"` 两种取值。

### 5.2 URL 编码

`dim` 中的 `:` 必须编码为 `%3A`。坐标负号无需编码。

### 5.3 plan 异步与超时

`plan` 内部走 `MinecraftServer.submit` → `CraftingOrderService.planOrderAsync`（CompletableFuture），HTTP 端 12 秒超时。计划缓存 TTL 由 `CraftingOrderService` 内部管理，长时间不 confirm 会自动失效。

### 5.4 多语言

- 服务端启动时 `ServerAssetIndex` 异步扫描所有 mod 的 `lang/zh_cn.json`、`lang/en_us.json`，作为 displayName 来源
- 客户端登录后通过 `ClientNameUploadPayload` 上传当前语言下的本地化名称，覆盖服务端默认值
- 因此专用服务端首次启动后约几秒内 displayName 可能为英文，后续会被客户端镜像覆盖

### 5.5 路径长度

`/api/observers/<dim>/<x>/<y>/<z>` 共 5 段；后续可能附加 `/items`、`/history`、`/crafting`、`/crafting/plan`、`/debug/sampler` 等。所有 sub-handler 都通过去除尾缀后调用 `parseObserverPath` 复用解析逻辑。

---

## 6. 版本与兼容

- `apiVersion: 1` 自 v0.5.0 起，至 v0.8.x 保持兼容。
- **新增字段不视为 break change**，请用宽松解析。
- 移除字段、改字段语义、改路径均会触发 `apiVersion` 升级到 `2`。

---

## 7. 调用示例

### curl

```bash
curl http://127.0.0.1:28080/api/health
curl "http://127.0.0.1:28080/api/observers/minecraft%3Aoverworld/12/64/-88/items?limit=20&sort=net&dir=asc"
curl -X POST http://127.0.0.1:28080/api/observers/minecraft%3Aoverworld/12/64/-88/crafting/plan \
  -H "Content-Type: application/json" \
  -d '{"networkId":"ae2:controller@12345","itemId":"minecraft:diamond","amount":64}'
```

### TypeScript（Web Dashboard 内）

参见 `Data Visualization Dashboard/src/app/lib/api.ts` 与 `useObservers.ts` 的封装实现。

---

## 8. 相关源码

| 文件 | 说明 |
|------|------|
| `web/WebServerService.java` | HTTP 服务生命周期 |
| `web/handler/*Handler.java` | 各 endpoint 实现 |
| `web/JsonWriter.java` | 轻量 JSON 序列化 |
| `web/handler/BaseApiHandler.java` | 路径解析、主线程派发、错误响应基类 |
| `web/handler/ServerAssetIndex.java` | 服务端 lang/textures 扫描 |
| `web/handler/ItemNameResolver.java` | itemId → displayName 解析 + 客户端镜像 |
| `network/ChartWindow.java` | 4 档时间窗口枚举 |
| `world/history/HistoryRecorder.java` | 通用环形缓冲历史 |
| `world/history/WebHighPrecisionSampler.java` | detail 档高精度采样器（1200-bucket × 0.25s） |

---

> 如有疑问或发现差异，请优先以源码为准并提 Issue 修订本文档。

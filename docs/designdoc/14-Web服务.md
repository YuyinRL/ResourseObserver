---
tags:
  - Web
  - HTTP
  - Dashboard
  - 合成
aliases:
  - Web服务
  - Web Dashboard
  - Crafting API
---

# Web 服务与合成 API

> 自 `0.3.0` 起：Resource Observer 在 Minecraft 服务端启动时附带启动一个内置 HTTP 服务（基于 JDK 自带 `com.sun.net.httpserver`），通过 JSON API 暴露 Observer 数据和 AE2 合成信息。

## 🎯 设计目标

1. 在**不侵入游戏线程**的前提下提供外部可观测性接口
2. 允许外部 Dashboard（见 [[12-资源结构|Data Visualization Dashboard]]）直接消费数据
3. 额外支持 AE2 **合成**维度数据（可合成清单、活跃任务、合成存储）
4. 零新增外部依赖（不引入 Gson/Netty 等）

## 🧱 模块结构

```
com.yuyinrl.resourceobserver.web
├─ WebServerConfig      # TOML 配置（enabled/host/port/corsAllowAll）
├─ WebServerService     # HttpServer 封装，绑定生命周期事件
├─ JsonWriter           # 极简 JSON 序列化器（Map/Iterable/Number/String）
└─ handler/
   ├─ BaseApiHandler    # 主线程切换 + 路径解析工具
   ├─ HealthHandler     # GET /api/health
   ├─ IconHandler       # GET /api/icon/{namespace}/{path}
   ├─ ObserversHandler  # GET /api/observers[/{dim}/{x}/{y}/{z}]
   ├─ CraftingHandler   # GET /api/observers/{...}/crafting
   ├─ CraftingOrderHandler # POST /api/observers/{...}/crafting/{plan|confirm|cancel|tree}
   ├─ ItemNameResolver  # itemId -> translationKey/displayName 解析
   ├─ ServerAssetIndex  # 预加载 mod jar lang 资源（zh_cn/en_us）
   └─ StaticHandler     # GET / (静态资源 assets/resourceobserver/web/)
```

- 合成采集由 `integration.CraftingDataCollector` / `CraftingOrderService` 直接调用
  AE2 API（`ICraftingService`、`ICraftingCPU`、`AEKey`、`AEFluidKey`），支持计划计算、样板过滤与流体键。
- Observer 枚举通过 `ObserverBlockEntity` 的静态 `LOADED` 注册表，
  在 `onLoad()/setRemoved()` 中自动增删，避免扫描区块。

## 🔀 生命周期

| 事件 | 行为 |
|------|------|
| `ServerStartedEvent` | 读取配置；创建并启动 `HttpServer`，注册路由；设置线程池（4 线程） |
| `ServerStoppingEvent` | `server.stop(0)` 立即释放端口 |

Singleplayer 每次进入世界都是一次新的 `ServerStartedEvent`，退出世界即触发 `ServerStoppingEvent`，端口会被正确释放。

## 🧵 线程模型

- HTTP 工作线程（`ResourceObserver-Web`）绝对**不**直接访问 `Level` / `BlockEntity`。
- `BaseApiHandler.runOnMain(fn)` 将任务通过 `MinecraftServer.submit()` 交给主线程，带 3 秒超时保护。
- 主线程任务内只做数据快照（拷贝成 `Map`），不保留 Minecraft 对象引用。

## 📡 API 端点

### `GET /api/health`
```json
{"status":"ok","modid":"resourceobserver","serverName":"...","tickCount":1234,"singleplayer":true}
```

### `GET /api/meta`（0.4.0+）
Dashboard 启动时调用一次，返回 API schema 版本和可用集成列表：
```json
{
  "modid":"resourceobserver",
  "apiVersion":1,
  "integrations":{"ae2":true,"fluxNetworks":false,"mekanism":false},
  "endpoints":{"health":"/api/health","observers":"/api/observers", ...}
}
```

`iconsAvailable` 现始终对外暴露为可用：集成端可本地渲染，专用服务端则通过在线客户端镜像补图。

### `GET /api/observers`
0.4.0 新增 `id` 与 `displayName` 字段，便于前端 Observer Selector 使用：
```json
{"observers":[{
  "id":"minecraft:overworld/10/70/20",
  "dimension":"minecraft:overworld",
  "x":10,"y":70,"z":20,
  "bound":true,"bindingCount":2,
  "displayName":"Observer @ overworld [10,70,20]"
}],"count":1}
```

### `GET /api/observers/{dim}/{x}/{y}/{z}`
返回绑定概览 + 每个网络的详细字段（AE2 物品字典、`cellCapacity` 指标、或 Flux 能量快照）。

### `GET /api/observers/{dim}/{x}/{y}/{z}/crafting`
针对 `AE2_ITEMS` 绑定输出：
- `craftables`：可合成资源列表（`itemId` + `displayName` + `entryType`），支持普通物品与 `fluid:<namespace>:<path>` 流体键
- `jobs`：所有合成 CPU 的任务状态（`busy`、`outputItemId`、`remainingAmount`、`progressFraction`、`treeId`）
- `storage`：`cpuCount`、`busyCpuCount`、`totalStorageBytes`、`totalCoProcessors`、`reliable`

服务端会先对 `AEKey.dropSecondary()` 归一化，再按 `getCraftingFor(key)` / `canEmitFor(key)` 过滤，避免 Web 搜索结果出现“列表里有、下单时却提示没有样板”的无效条目。

### `POST /api/observers/{dim}/{x}/{y}/{z}/crafting/plan`

请求体：

```json
{"networkId":"...","itemId":"minecraft:iron_ingot","amount":128}
```

返回 AE2 计划摘要、缺料/用料/副产物、CPU 列表与可选的 `tree` / `treeId`。其中堆栈条目同样支持 `fluid:` 前缀流体键。

### `POST /api/observers/{dim}/{x}/{y}/{z}/crafting/confirm`

请求体：

```json
{"planId":"...","cpu":"可选 CPU 名"}
```

使用已缓存的 `planId` 真正提交订单。

### `POST /api/observers/{dim}/{x}/{y}/{z}/crafting/cancel`

主动丢弃已缓存的计划。

### `POST /api/observers/{dim}/{x}/{y}/{z}/crafting/tree`

按 `treeId` 返回完整 `CraftingTreeNode`，供 Review Dialog 与运行中任务详情复用。

### `GET /api/icon/{namespace}/{path}`

统一的物品 / 流体图标入口：

- 集成端：直接调用客户端 `IconRenderer` 真实渲染 `ItemStack` / 流体纹理
- 专用服务端：缓存未命中时通过 `IconRequestPayload` 请求在线客户端代渲染，再由 `ClientIconUploadPayload` 回传 PNG
- 浏览器缓存时间较短（`max-age=300`），避免错误旧图长时间驻留

## ⚙️ 配置（`config/resourceobserver-web.toml`）

| 键 | 默认 | 说明 |
|----|------|------|
| `web.enabled` | `true` | 总开关 |
| `web.host` | `127.0.0.1` | 监听地址；改为 `0.0.0.0` 可局域网访问（无鉴权） |
| `web.port` | `28080` | 端口 |
| `web.corsAllowAll` | `true` | 是否对 `/api/*` 响应 `Access-Control-Allow-Origin: *` |

## 🖥️ 前端 Dashboard（0.4.0+）

`Data Visualization Dashboard/` 是独立的 Vite + React + Tailwind 4 工程，构建产物
直接输出到 `src/main/resources/assets/resourceobserver/web/`。开发流程：

```bash
cd "Data Visualization Dashboard"
npm install
npm run build      # 产物落到 mod 资源目录
# 或：npm run dev  # dev server；vite 已配置代理 /api → 127.0.0.1:8877
```

**核心基础设施**（已迁移）：

| 模块 | 职责 |
|------|------|
| `src/app/lib/api.ts` | REST 端点封装 + TypeScript 类型声明 |
| `src/app/hooks/usePolling.ts` | 定时轮询 Hook（默认 5s） |
| `src/app/hooks/useObservers.ts` | `useObservers` / `useObserverDetail` / `useObserverCrafting` / `useMeta` |
| `src/app/components/ObserverSelector.tsx` | 当前 Observer 的 Context + 下拉选择器；选择状态持久化到 `localStorage` |

**图标与名称策略（0.7.0+）：**

- 统一通过 `ItemIcon` 组件访问 `/api/icon/...`
- 专用服务端优先等待客户端真实渲染结果，不再默认回退到服务端直接读取 `textures/*.png`
- `ItemNameResolver` 优先使用客户端名称镜像，其次使用 `ServerAssetIndex` 从 mod jar 读取 `zh_cn/en_us` 语言文件
- Crafting 列表在前端按 `networkId + itemId` 再做一层保险去重，统计口径与实际下单入口保持一致

**StaticHandler 能力增强**：

- 支持的 MIME：`html/js/mjs/css/json/map/svg/png/jpg/gif/webp/ico/woff/woff2/ttf/otf/wasm/txt`
- SPA 回退：无扩展名的未匹配路径统一返回 `index.html`，配合前端路由
- 缓存策略：`index.html` 不缓存；`/assets/*`（带 hash）使用 `immutable, max-age=1y`

**增量迁移状态**：

- [x] API client + 类型
- [x] 轮询 Hook + Observer Selector
- [x] 头部 "Link Established" 徽标使用真实 `/api/meta` 状态
- [ ] `Overview` / `StorageNetwork` / `PowerNetwork` 内的 mock 数据逐步替换为
  `useObserverDetail` / `useObserverCrafting` 返回值

## 🔐 安全提示

- 默认仅绑定 `127.0.0.1`，任何访问需要宿主机权限
- **不包含认证机制**，请勿暴露至公网
- 路径中的 `..` 会被静态资源处理器直接拒绝

## 🔗 相关文档

- [[07-模组集成]] — AE2 反射调用细节
- [[03-方块与实体]] — Observer 注册表与绑定
- [[12-资源结构]] — `Data Visualization Dashboard/` 前端源码
- [[13-更新日志]] — 版本变更


# Resource Observer — 后续开发计划（Handoff for Fresh AI Agent）

> **本文档为完整、自包含的任务交接说明**。即使你（AI agent）从零开始接手该项目，也应当通过本文档快速理解项目状态并直接开始 F4.B 工作。
>
> **当前版本**：`v1.0.0-beta.5`（commit `ea80ce31`）
> **目标版本**：`v1.0.0` GA  
> **剩余阶段**：F4.B → F4.C → F5（三大块）

---

## 0. 项目极速上手

### 0.1 项目是什么

**Resource Observer** 是一个 Minecraft NeoForge 1.21.1 mod，用来在游戏内监控资源网络（AE2 存储、Flux Networks 能源、Mekanism 能源）。它由以下几部分组成：

- **Observer Block**：放置后绑定一个网络，每 20 tick 在服务端采样
- **Binding Tool**：两步右键的状态机（选 Observer → 选目标网络方块）
- **Resource Terminal**：物品，打开 GUI 查看 Observer 数据，三个标签页（Overview / Storage / Power）
- **Web Dashboard**：内嵌 HTTP 服务器（`127.0.0.1:28080`）+ React + Vite 前端，浏览器查看

### 0.2 关键技术栈

| 层 | 技术 |
|----|------|
| 运行平台 | NeoForge 1.21.1 / `neo_version=21.1.206` |
| 语言 | Java 21 |
| 构建 | Gradle ModDevGradle plugin (`net.neoforged.moddev`) |
| 游戏内 UI（双套并行） | ① ModernUI Fragment（`icyllis modernui` 3.12.0）② Vanilla Screen V2（原版 `Screen` + `GuiGraphics`） |
| Web 后端 | JDK 内置 `com.sun.net.httpserver.HttpServer`，手写 `JsonWriter` |
| Web 前端 | React 18 + Vite 6 + TypeScript + Tailwind 4 + MUI + Radix UI + Recharts |
| 可选依赖 | JEI 19.25.1.332 (compileOnly + localRuntime) — **本计划要用到** |

### 0.3 必备命令

```bash
# 构建 mod jar
./gradlew build

# 启动 Minecraft 客户端（dev）
./gradlew runClient

# 启动专用服务器
./gradlew runServer

# 跑测试
./gradlew test

# 清构建
./gradlew clean build

# Web 前端独立开发
cd "Data Visualization Dashboard"
pnpm install
pnpm dev      # 代理 /api → http://127.0.0.1:28080
pnpm build    # 输出到 ../src/main/resources/assets/resourceobserver/web/
```

### 0.4 项目核心约定（**必须遵守**）

1. **所有 Javadoc / 行内注释一律用中文**（项目级强制约定，全仓库都是中文注释）
2. **日志只用 SLF4J**，禁止 `System.out.println()`
3. **所有版本号集中在 `gradle.properties`**，不准在 `build.gradle` 里硬编码
4. **mod 元数据走模板**：`src/main/templates/META-INF/neoforge.mods.toml`，编辑模板而不是生成产物
5. **行宽 120 字符；3 级嵌套以内；`try-with-resources`；不要通配 import**
6. **网络协议**：所有 payload 都是 Java `record`，含 `static final Type<X> TYPE` + `STREAM_CODEC`
7. **DeferredRegister**：每个注册分类一个类，含 `static register(IEventBus bus)` 方法，从 `ResourceObserverMod` 构造器调用
8. **集成模组的两类**：
   - **必需**：AE2、ModernUI（直接 import）
   - **可选**：Flux、Mekanism、Draconic、JEC（适配器模式 + 反射 + 可用性守卫）

---

## 1. 当前状态（截至 v1.0.0-beta.5）

### 1.1 已完成的"数据契约统一" Snapshot 管线

项目已建立完整的「**单一真相源 + 双 transport**」架构。这是理解后续 F4.B 工作的**关键背景**：

```
service/snapshot/<domain>/<Domain>SnapshotBuilder.fromObserver(ObserverBlockEntity, Localizer)
        │  （服务端唯一权威派生层；含 KPI / 进度 / 状态色 / 格式化字符串）
   ┌────┴────┐
   ▼         ▼
 Packet 通道       HTTP API 通道
 │                 │
 │  StreamCodec    │  JsonWriter（手写）
 │  → Payload      │  → REST JSON
 ▼                 ▼
 *ViewModelMapper  ServerAssetIndex / handler / liveAdapter.ts
 （**薄壳**：仅 i18n + 客户端筛选 + 1:1 透传，不做计算）
 │                 │
 ▼                 ▼
 ModernUI Fragment  React 组件
 + Vanilla V2 Panel
```

**已就位的 4 个 Snapshot 域**（均通过 `*SnapshotBuilder.fromObserver(...)` + `*SnapshotBuilder.fromPayload(...)` 双入口暴露）：

| 域 | 位置 | 状态 |
|----|------|------|
| Overview | `service/snapshot/overview/` | ✅ ModernUI / V2 / Web 三方已迁完（beta.5） |
| Storage | `service/snapshot/storage/` | ✅ 三方已迁完（beta.5） |
| Power | `service/snapshot/power/` | ✅ 三方已迁完，含 G7 数据源统一（beta.5） |
| **Crafting** | `service/snapshot/crafting/` | ⚠️ **基础设施完成（F4.A / beta.4），三方消费者未迁** |

### 1.2 Crafting 域 Snapshot 现状（**F4.B 起点**）

`service/snapshot/crafting/` 包目前已有：

```
CraftingSnapshot.java              ← record 数据契约
CraftingSnapshotBuilder.java       ← fromObserver + fromPayload 双入口
CraftingLocalizer.java             ← 接口
CraftingJobNormalizer.java         ← 纯逻辑工具
CraftingSearchMatcher.java         ← 搜索匹配
CraftingSorter.java                ← 排序
CraftingSummaryAggregator.java     ← 聚合
```

但以下 3 个消费者**仍在用老路径**：

1. **ModernUI**：`client/modernui/CraftingSubTabBuilder.java` + `client/ui/CraftingViewModelMapper.java`（Mapper 仍含派生逻辑）
2. **Web**：`web/handler/CraftingHandler.java`（旧路径，没走 Snapshot）
3. **Vanilla V2**：`client/screen/v2/storage/CraftingSubPanel.java`（最初实现，未对齐 ModernUI 完整功能）

### 1.3 V2 UI 现状

V2（`client/screen/ResourceTerminalScreenV2`）通过 **F8** 调试键打开。已具备：

- 完整的 widget kit：`client/screen/widget/`（Tabs / TextField / ProgressBar / LineChart / DonutChart / MiniDonut / StarToggleButton / ContextMenu）
- 三大 Panel：`v2/overview/OverviewPanel`、`v2/storage/StoragePanel`、`v2/power/PowerPanel` 完整功能
- Dialog 系统：`client/screen/dialog/`（DialogHost / BasicDialog / Kpi/CraftOrder/CraftPlanReview/CraftingTree/InterfaceList/StorageDetail/GroupName）
- 自动刷新：`tick()` 每 20 tick 发送 `ObserverRefreshRequestPayload`，与 ModernUI 1000ms 对齐
- 静态桥：`v2/LatestSnapshotProvider`（持有最新 `ObserverDataPayload` 与三个 `*Snapshot`）

V2 **仍未成为正式默认**，需要等 F5 阶段开关切换。

### 1.4 已存在但未集成的依赖

- **JEI**：依赖已在 `gradle.properties:46` 加好（`jei_version=19.25.1.332`），`build.gradle` 也接好（compileOnly + localRuntime）。**集成代码完全未写**。

---

## 2. 仓库导航（关键路径速查）

### 2.1 Java 源码包结构

```
com.yuyinrl.resourceobserver
├── ResourceObserverMod              ← @Mod("resourceobserver") 入口
├── DevMode                          ← !FMLLoader.isProduction() 守卫
├── client/
│   ├── input/                       ← 按键绑定（F8 = 打开 V2）
│   ├── modernui/                    ← ModernUI Fragment（含 *PageBuilder）
│   ├── screen/
│   │   ├── ResourceTerminalScreenV2 ← F8 入口
│   │   ├── widget/                  ← V2 widget kit
│   │   ├── theme/                   ← VanillaTheme / Sprites / NinePatch
│   │   ├── dialog/                  ← Dialog 系统
│   │   └── v2/
│   │       ├── LatestSnapshotProvider
│   │       ├── V2UiActions          ← C→S 动作发送统一入口
│   │       ├── overview/, storage/, power/, crafting/
│   ├── ui/                          ← ViewModel + Mapper（薄壳）+ render/
│   │   ├── format/                  ← FormatUtils
│   │   └── render/                  ← 15+ 渲染器 + chart/ 子包
│   └── web/                         ← IconRenderer 客户端图标渲染
├── integration/                     ← 模组适配器（Flux/Mekanism/Draconic/JEC）
├── network/                         ← Payload records + ModNetworking
├── registry/                        ← DeferredRegister
├── service/
│   ├── ObserverService              ← Web 入口
│   └── snapshot/                    ← 服务端权威派生层（v1.0.0-beta.5）
│       ├── overview/, storage/, power/, crafting/, format/
├── ui/state/                        ← 共享枚举（TableSortMode / Filter）
├── web/
│   ├── WebServerService             ← HttpServer 生命周期
│   ├── WebServerConfig              ← TOML 配置
│   ├── JsonWriter                   ← 手写 JSON 序列化
│   └── handler/                     ← 各 REST 端点 + Server*Localizer
└── world/
    ├── block/entity/ObserverBlockEntity
    ├── item/{ResourceTerminalItem, BindingToolItem, DebugTerminalItem}
    ├── history/                     ← 历史采样 + SavedData
    ├── command/                     ← /observer 调试命令
    └── ui/                          ← 玩家 UI 偏好持久化
```

### 2.2 资源路径

```
src/main/resources/assets/resourceobserver/
├── lang/{en_us,zh_cn}.json          ← 翻译键（已 500+ 条）
├── models/, textures/, blockstates/  ← 标准 MC 资源
├── shaders/core/                     ← GLSL 图表着色器
└── web/                              ← Vite 构建产物（嵌入式 SPA）

src/main/resources/data/resourceobserver/
├── loot_tables/, recipes/

src/generated/resources/              ← 数据生成器输出（已配置 sourceSets）

src/main/templates/META-INF/neoforge.mods.toml  ← mod 元数据模板
```

### 2.3 设计文档（**改架构时必同步**）

```
docs/designdoc/
├── 00-INDEX.md
├── 01-项目概述.md
├── 02-系统架构.md         ← 改架构必同步
├── 03-方块与实体.md
├── 04-物品系统.md
├── 05-网络通信.md         ← 改 payload 必同步
├── 06-UI系统.md           ← 改 UI 必同步
├── 07-模组集成.md         ← F4.C JEI 必同步
├── 08-数据持久化.md
├── 09-注册系统.md
├── 10-构建与开发.md
├── 11-代码规范.md
├── 12-资源结构.md
├── 13-更新日志.md         ← 每次发版必更新
├── 14-Web服务.md
├── 14-ModernUI 组件与交互清单.md
└── 15-Web-API参考.md
```

---

## 3. 阶段 F4.B · Crafting 三方消费者迁移

> **目标版本**：`v1.0.0-beta.6`  
> **基础设施前置**：已完成（F4.A / beta.4，CraftingSnapshot 已就位）  
> **模式参考**：F1.B / F2.B / F3.B（已落地的三方迁移模板）

### 3.1 范围声明

**In**：把 Crafting 域的 ModernUI / Vanilla V2 / Web 三方消费者全部切到统一的 `CraftingSnapshot` 契约，删除散落派生逻辑。

**Out**：不动 transport（Payload 字段保持兼容）、不动 i18n 策略、不动刷新调度。

### 3.2 子任务清单

#### F4.B.1 ModernUI 内化 Snapshot

**目标**：`CraftingViewModelMapper` 重写为薄壳（参考 `StorageNetworkViewModelMapper` 现状）。

**步骤**：
1. 阅读 `client/ui/CraftingViewModelMapper.java`（~XXX 行），列出其中所有派生计算（聚合 / 排序 / 过滤）
2. 把派生计算搬到 `service/snapshot/crafting/CraftingSnapshotBuilder.java`（必要时新增工具类如 `CraftingTableOps`）
3. Mapper 改为：
   ```java
   public static CraftingViewModel fromPayload(ObserverDataPayload payload, String searchQuery, ClientCraftingLocalizer loc) {
       CraftingSnapshot snapshot = CraftingSnapshotBuilder.fromPayload(payload, loc);
       return fromSnapshot(snapshot, searchQuery);
   }

   public static CraftingViewModel fromSnapshot(CraftingSnapshot snapshot, String searchQuery) {
       // 仅做：① 翻译 key → 文字 ② post-snapshot 过滤（如有 search） ③ 1:1 透传
   }
   ```
4. 新增 `client/ui/ClientCraftingLocalizer.java`（参考 `ClientOverviewLocalizer` / `ClientStorageLocalizer`）
5. `CraftingSubTabBuilder.java` 中所有 helper 调用改用 SnapshotBuilder 已暴露的工具方法
6. `ViewModelBridge.java` 在 `applyPayload` 同侧调用 `LatestSnapshotProvider.publishCrafting(snapshot)`（需要先在 LatestSnapshotProvider 加 craftingSnapshot 槽位）

**验证**：
- `./gradlew compileJava` 通过
- ModernUI 终端 → Storage tab → Crafting 子页：搜索 / 排序 / 计划 / 下单 行为与 beta.5 一致

#### F4.B.2 Web 后端切到 Snapshot

**目标**：`CraftingHandler` 改用 `CraftingSnapshotBuilder.fromObserver(...)`，前端拿到的字段与服务端 Snapshot 字面一致。

**步骤**：
1. 新建 `web/handler/ServerCraftingLocalizer.java`（参考 `ServerStorageLocalizer`）：
   - `displayName` 走 `ItemNameResolver`
   - 翻译键直传给前端处理
   - `searchMatcher` 用 `SearchMatcher.DEFAULT`（无拼音）
2. 修改 `web/handler/CraftingHandler.java`：
   ```java
   ObserverBlockEntity be = service.findByPos(pos);
   CraftingSnapshot snapshot = CraftingSnapshotBuilder.fromObserver(be, ServerCraftingLocalizer.INSTANCE);
   JsonWriter w = ...;
   writeCraftingSnapshot(w, snapshot);
   ```
3. 在 `JsonWriter` 中输出顶层 `crafting` 块（参考 Power 的实现）：
   - `displayName / activeJobs[] / queuedJobs[] / completedRecent[] / cpuStatus[] / patternsAvailable / kpiCards[]`（具体字段以 `CraftingSnapshot` record 为准）
4. **保留旧字段并存**（向后兼容），新增字段为可选

**验证**：
- 启动 `./gradlew runServer`，浏览器访问 `http://127.0.0.1:28080/api/observers/{id}/crafting`
- 字段对比 ModernUI Crafting 子页数值，必须字面一致

#### F4.B.3 Web 前端字段对齐

**目标**：React 端直接消费 snapshot 字段，删除 `liveAdapter.ts` 中 Crafting 相关派生。

**步骤**：
1. 在 `Data Visualization Dashboard/src/app/lib/api.ts`：
   - 新增 `CraftingPayload` interface（superset，包含新字段 + 旧字段）
   - `ObserverDetail.crafting` 类型改为可选 `CraftingPayload`
2. 在 `liveAdapter.ts`：
   - 优先消费 `detail.crafting`，缺失时回落旧逻辑
   - 删除现有 Crafting 相关派生代码（计算 / 聚合 / 排序）
3. 在 `components/CraftingTreeView.tsx` 与 `components/crafting-tree/` 子目录：
   - 切到新字段
   - 视觉行为与 ModernUI 一致

**验证**：
- `pnpm dev` 启动，浏览器对比 ModernUI / V2 / Web 三方数值
- 切换 active / completed 标签 / 排序 / 搜索：行为一致

#### F4.B.4 Vanilla V2 CraftingPanel 升级

**目标**：把 `client/screen/v2/storage/CraftingSubPanel.java` 升级为完整功能面板，对齐 ModernUI Crafting 子页。

**步骤**：
1. 评估当前 `CraftingSubPanel` 缺哪些功能（对照 ModernUI `CraftingSubTabBuilder` 与 V2 已有的对话框）
2. 接入 `CraftOrderFlow` / `CraftingAvailability`（已存在于 `v2/crafting/`）
3. 接入对话框：`CraftOrderDialog` / `CraftPlanningDialog` / `CraftPlanReviewDialog` / `CraftingTreeDialog`（已存在于 `client/screen/dialog/`）
4. 通过 `LatestSnapshotProvider.getCrafting()` 直接读最新 snapshot 渲染
5. 接入 `V2UiActions` 发送 `ObserverUiActionPayload`（PLACE_CRAFT_ORDER / CANCEL_JOB 等）

**验证**：
- 游戏内 F8 → Storage tab → Crafting 子页：完整执行一次「搜索 → 选物品 → 下单 → 计算中 → 审核 → 提交」流程
- `runGameTestServer` 通过

#### F4.B.5 文档与发版

1. 更新 `docs/designdoc/13-更新日志.md`：新增 `[1.0.0-beta.6]` 条目
2. 同步 `docs/designdoc/06-UI系统.md`、`05-网络通信.md`（如有 payload 改动）
3. `gradle.properties`：`mod_version=1.0.0-beta.6`
4. 单 commit，trailer：`Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`

### 3.3 完成标志

- [ ] `CraftingViewModelMapper` 行数显著缩减（目标 ≤ 250 行薄壳）
- [ ] 加一个新 Crafting 字段时，只需改 `CraftingSnapshot` record + 三处 render 代码
- [ ] `./gradlew compileJava test` 全绿
- [ ] ModernUI / V2 / Web 三方 Crafting 数据字面一致

---

## 4. 阶段 F4.C · JEI 集成

> **目标版本**：`v1.0.0-rc.1`  
> **依赖前置**：JEI 19.25.1.332 已在 `gradle.properties` 和 `build.gradle` 接好  
> **设计纪律**：JEI 是**可选**集成。移除 JEI runtime 依赖跑 `runClient` 必须零报错。

### 4.1 子任务清单

#### F4.C.1 JeiBridge — 反射检测 + no-op 降级

**目标**：建立可选集成的统一接入层，避免 JEI 缺失时崩溃。

**步骤**：
1. 新建 `client/screen/jei/JeiBridge.java`：
   ```java
   public final class JeiBridge {
       private static final boolean AVAILABLE;
       static {
           boolean ok;
           try {
               Class.forName("mezz.jei.api.IModPlugin");
               ok = true;
           } catch (ClassNotFoundException e) { ok = false; }
           AVAILABLE = ok;
       }
       public static boolean isAvailable() { return AVAILABLE; }
       
       // showRecipes(ItemStack) / showUses(ItemStack) / setSearchText(String) 等门面
       // 内部走反射或 IRuntimeRegistration（视 API 而定）
   }
   ```
2. 所有调用方仅通过 `JeiBridge` 间接访问 JEI 类型，**不在其它类直接 `import mezz.jei.*`**

#### F4.C.2 JeiTerminalPlugin — IModPlugin 注册

**目标**：注册到 JEI 生命周期，拿到 `IJeiRuntime` 引用。

**步骤**：
1. 新建 `client/screen/jei/JeiTerminalPlugin.java`：
   ```java
   @JeiPlugin
   public class JeiTerminalPlugin implements IModPlugin {
       @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("resourceobserver", "jei_plugin"); }
       @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
           JeiBridge.bindRuntime(runtime);
       }
       @Override public void registerGuiHandlers(IGuiHandlerRegistration r) {
           // 后续 F4.C.5 注册 IGhostIngredientHandler
       }
   }
   ```
2. 在 `gradle.properties` / `neoforge.mods.toml` 中确认 JEI 是 optional 依赖

#### F4.C.3 R / U 跳转配方与用途

**目标**：在 V2 的物品 hover 上响应 `R`（配方）/ `U`（用途）键。

**步骤**：
1. 在 `OverviewTableRow` / `StorageItemRow` 中暴露 hover 状态与当前 hovered ItemStack
2. 在 `ResourceTerminalScreenV2.keyPressed(...)` 中：
   - 检查当前焦点 widget 是否暴露 hovered stack
   - `R` → `JeiBridge.showRecipes(stack)`
   - `U` → `JeiBridge.showUses(stack)`
3. 加键位绑定到 `KeyBindings`（与 V2 widget kit 风格一致）

#### F4.C.4 物品自绘右键菜单

**目标**：物品行右键弹出菜单：查看配方 / 用途 / 加入观察列表 / 复制 ID。

**步骤**：
1. 复用现有 `client/screen/widget/ContextMenu.java`
2. 在 `OverviewTableRow` / `StorageItemRow` 的 `mouseClicked` 中：button == 1 → 弹出菜单
3. 菜单项：
   - `screen.jei.view_recipes` → `JeiBridge.showRecipes(stack)`
   - `screen.jei.view_uses` → `JeiBridge.showUses(stack)`
   - `screen.jei.add_to_watchlist` → 走 V2UiActions.toggleWatchlist
   - `screen.jei.copy_id` → 系统剪贴板写入 `ResourceLocation`

#### F4.C.5 IGhostIngredientHandler 拖拽到搜索框

**目标**：从 JEI 物品列表把物品**拖**到 V2 搜索框，自动填充 ID 或显示名。

**步骤**：
1. 实现 `IGhostIngredientHandler<ItemStack>`：
   ```java
   public class TerminalGhostHandler implements IGhostIngredientHandler<ResourceTerminalScreenV2> {
       public <I> List<Target<I>> getTargetsTyped(ResourceTerminalScreenV2 screen, ITypedIngredient<I> ing, boolean doStart) {
           // 返回搜索框的 Target，accept 时 textField.setText(ItemStack.displayName)
       }
   }
   ```
2. 在 `JeiTerminalPlugin.registerGuiHandlers(...)` 注册到 `ResourceTerminalScreenV2`

#### F4.C.6 i18n

新增翻译键，**en_us + zh_cn 必须同时加**：

```json
"screen.jei.view_recipes": "View Recipes (R)",
"screen.jei.view_uses": "View Uses (U)",
"screen.jei.add_to_watchlist": "Add to Watchlist",
"screen.jei.copy_id": "Copy ID",
"screen.jei.unavailable": "JEI not available"
```

#### F4.C.7 兼容性回归

**目标**：移除 JEI runtime 依赖跑 `runClient`，必须零报错。

**步骤**：
1. 临时把 `build.gradle` 里的 `localRuntime "mezz.jei:..."` 注释掉
2. `./gradlew --refresh-dependencies runClient`
3. 打开 V2 终端，验证：
   - 物品 hover 按 R/U 不崩，弹 toast「JEI not available」
   - 右键菜单 hide 掉 JEI 项
   - 整体无 NPE 无日志 ERROR
4. 还原 `build.gradle`

#### F4.C.8 文档与发版

1. 更新 `docs/designdoc/07-模组集成.md`：JEI 集成章节
2. 更新 `docs/designdoc/13-更新日志.md`：`[1.0.0-rc.1]` 条目
3. `gradle.properties`：`mod_version=1.0.0-rc.1`
4. 单 commit + trailer

### 4.2 完成标志

- [ ] JEI 装/不装均可跑（兼容性）
- [ ] R/U 跳转工作
- [ ] 右键菜单可用
- [ ] JEI → 搜索框拖拽生效
- [ ] `./gradlew compileJava test runGameTestServer` 全绿

---

## 5. 阶段 F5 · 收尾切换 + GA 发版

> **目标版本**：`v1.0.0`（GA 正式版）  
> **核心动作**：把 V2 切为正式默认；ModernUI 转为「高保真备选」。

### 5.1 子任务清单

#### F5.1 配置项 `client.uiMode`

**步骤**：
1. 在 `WebServerConfig` 同级（或新建 `ClientConfig`）加 ModConfigSpec：
   ```java
   public enum UiMode { VANILLA_V2, MODERN_UI }
   public static final ModConfigSpec.EnumValue<UiMode> UI_MODE = ...;
   // 默认 VANILLA_V2
   ```
2. 配置文件路径：`config/resourceobserver-client.toml`

#### F5.2 终端路由切换

**步骤**：
1. `world/item/ResourceTerminalItem.use(...)`：
   ```java
   if (ClientConfig.uiMode() == UiMode.VANILLA_V2) {
       // 客户端打开 ResourceTerminalScreenV2
   } else {
       // 老路径：发包 → ClientPayloadHandler 打开 ResourceTerminalFragment
   }
   ```
2. F8 调试键保持开启 V2（不变）
3. 确保两种模式下数据流都正确（refresh request / payload handler 兼容）

#### F5.3 ModernUI 标记备选

**步骤**：
1. 不删 `client/modernui/` 包（保留代码）
2. 在 `06-UI系统.md` 写明：ModernUI 仅在 `uiMode=MODERN_UI` 时启用
3. 在 ModernUI 入口加日志：`LOGGER.info("ModernUI mode active (legacy fallback)")`

#### F5.4 文档全量更新

**必改文档**：
- `06-UI系统.md`：全文重写，描述新 V2 默认架构
- `02-系统架构.md`：补 Snapshot 层完整示意（v1.0.0 GA 视角）
- `13-更新日志.md`：新增 `[1.0.0]` GA 条目，列出 alpha → beta → rc → GA 全部里程碑
- `01-项目概述.md`：更新「当前状态」段落
- `15-Web-API参考.md`：把 F4.B 新增的 crafting 字段补完整
- `07-模组集成.md`：JEI 章节定稿

#### F5.5 性能 sanity check

**步骤**：
1. `runClient` → 打开 V2 终端 30 分钟
2. 用 VisualVM 或 MC F3 profiler 监测：
   - 堆使用应在长开后保持稳定（无明显增长）
   - 图表帧时间 ≤ ModernUI 模式下的 1.2x
3. 关闭终端，确认无 listener / dialog 残留

#### F5.6 GA 发版

1. `gradle.properties`：`mod_version=1.0.0`
2. `./gradlew clean build` 通过
3. `./gradlew runGameTestServer` 通过
4. 创建 git tag `v1.0.0`
5. 单 commit + trailer

### 5.2 完成标志

- [ ] 普通玩家右键终端 → 看到 V2（不是 ModernUI）
- [ ] 配置文件可切回 ModernUI
- [ ] 长开终端 30 min 无内存泄漏
- [ ] 文档与代码状态一致

---

## 6. 历史候选（低优，v1.0.x 维护期）

来自 v0.8.1 ChartRenderer 重构未完成的尾巴：

| 任务 | 内容 |
|------|------|
| ChartRenderer Phase 3 | 抽 `ChartRenderCache` + 私有依赖到 `client/ui/render/chart/` 子包 |
| ChartRenderer Phase 4 | 合并 `ChartMath` 与 `prepareChart` 重复实现 |
| 集成测试基础设施 | 加烟雾测试 / E2E（目前只有 JUnit 单测，没有集成测试） |
| ModernUI 长期清理 | v0.14.0+ 后若无人启用 ModernUI，删除整个 `client/modernui/` 包 |

---

## 7. 工作流约定（每次 commit）

### 7.1 编码

1. **改架构** → 同步 `02-系统架构.md` / `05-网络通信.md` / `06-UI系统.md` / `07-模组集成.md` / `08-数据持久化.md` 中相关文档
2. **加翻译键** → en_us + zh_cn 同时加
3. **加 payload** → record + STREAM_CODEC + ModNetworking 注册 + handler `enqueueWork`
4. **可选集成** → 适配器模式 + 反射 + 可用性守卫

### 7.2 验证

```bash
./gradlew compileJava            # 必过
./gradlew test                   # 必过
./gradlew runGameTestServer      # 涉及游戏逻辑时跑
./gradlew runClient              # 手动验证 UI 行为
```

### 7.3 提交

按 `.github/skills/commit/SKILL.md` 的模板：

```
[v版本号] 更新摘要

### 新增
- ...

### 修复
- ...

### 变更
- ...

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
```

**单次提交所有变更**，不拆 commit。

---

## 8. 自定义 Skills 触发词速查

项目级 skill 在 `.github/skills/`，常用触发词：

| 触发词 | 用途 |
|--------|------|
| `/update-doc` 或「更新文档」 | 汇总今日 git 变更，分类追加到 `13-更新日志.md` |
| `/commit` 或「汇总提交」 | `git add -A` + 结构化 commit message + 单次提交 |
| `/design` | 口语化需求 → 结构化设计文档 |
| `/debug` | 分析报错 + 给最小修改方案 |
| `/analyze-java` | 分析 Java 类结构 |
| `/analyze-gradle` | 分析 Gradle 配置 |
| `/mod-design` | Minecraft Mod 功能设计 |
| `/refactor` | 重构建议 |
| `/api-flow` | API 数据流分析 |

---

## 9. 已知陷阱（避免踩坑）

1. **Tabs 是 record**：accessor 用 `content()` 不是字段访问
2. **V2 不要直接 `mc.setScreen(...)`**：会绕过 `ResourceTerminalItem.use()` 的服务端发包流程；F8 入口已修复（每 20 tick 主动发 RefreshRequest），但**新加 V2 入口必须遵循同样模式**
3. **Mapper 不允许做派生**：仅 ① 翻译 key→文字 ② 客户端筛选状态叠加 ③ VM 1:1 透传。**`selectedExtIds` 是唯一例外**（per-player 用户态，留在客户端）
4. **JsonWriter 是手写的**：不要引入 Gson / Jackson；新增字段时按现有风格手动写入
5. **V2 Panel 永远从 `LatestSnapshotProvider` 读最新 snapshot**：不要在 Panel 里缓存数据
6. **Power KPI 输出 4 张**（effective_input/output/stored/extracted）：不是 3 张；`PowerKpiBuilderTest` 有 4 用例守门
7. **Power 储能 KPI 格式**：`"X / Y FE"`（带斜杠和空格）；净功率 KPI 单位 `FE/t`
8. **Crafting 还没迁完**：F4.B 之前 `CraftingHandler` 走旧路径，**别误以为 Web 已对齐 Snapshot**
9. **ModernUI 当前是默认**：F5 之前不要切默认；F8 是 V2 调试入口
10. **改 mod 元数据走模板**：编辑 `src/main/templates/META-INF/neoforge.mods.toml`，`generateModMetadata` 会自动处理

---

## 10. 推荐执行顺序

```
[当前] v1.0.0-beta.5 (commit ea80ce31)
    ↓
F4.B  Crafting 三方迁移        →  v1.0.0-beta.6
    │   F4.B.1 ModernUI 内化
    │   F4.B.2 Web 后端
    │   F4.B.3 Web 前端
    │   F4.B.4 V2 Panel 升级
    │   F4.B.5 文档 + 单 commit
    ↓
F4.C  JEI 集成                  →  v1.0.0-rc.1
    │   F4.C.1 JeiBridge
    │   F4.C.2 JeiTerminalPlugin
    │   F4.C.3 R/U 跳转
    │   F4.C.4 右键菜单
    │   F4.C.5 IGhostIngredientHandler
    │   F4.C.6 i18n
    │   F4.C.7 兼容性回归
    │   F4.C.8 文档 + 单 commit
    ↓
F5    收尾切换                  →  v1.0.0 GA
    │   F5.1 client.uiMode 配置
    │   F5.2 终端路由切换
    │   F5.3 ModernUI 标备选
    │   F5.4 文档全量更新
    │   F5.5 性能 sanity check
    │   F5.6 GA 发版（tag v1.0.0）
    ↓
[可选] v1.0.x 维护期
    │   ChartRenderer Phase 3/4
    │   集成测试基础设施
    │   v0.14.0+ 删除 ModernUI 代码
```

---

## 11. 接手清单（新 AI agent 第一天）

1. **读本文档（Section 0–2）** —— 理解项目和当前状态
2. **跑一次 build**：
   ```bash
   ./gradlew compileJava test
   ```
3. **跑一次 runClient**，按 F8 打开 V2 终端，浏览三个标签 → 你应该看到完整的 V2 UI（自动每秒刷新）
4. **`./gradlew runServer` + 浏览器 `http://127.0.0.1:28080`** → 你应该看到 React Dashboard
5. **决定起点**：默认 F4.B.1 → 阅读 `client/ui/CraftingViewModelMapper.java` 现状
6. **建 SQL todos**：把 F4.B 的 5 个子任务插入 `todos` 表（参考 SQL 节）

### SQL todos 起手模板

```sql
INSERT INTO todos (id, title, status, description) VALUES
  ('f4b-1-modernui', 'F4.B.1 ModernUI 内化 Snapshot', 'pending',
    '把 CraftingViewModelMapper 重写为薄壳，委托 CraftingSnapshotBuilder.fromPayload；新增 ClientCraftingLocalizer；ViewModelBridge 同侧发布 craftingSnapshot 到 LatestSnapshotProvider。'),
  ('f4b-2-web-backend', 'F4.B.2 Web 后端切到 Snapshot', 'pending',
    '新建 ServerCraftingLocalizer；CraftingHandler 改用 CraftingSnapshotBuilder.fromObserver；JsonWriter 输出顶层 crafting 块；保留旧字段兼容。'),
  ('f4b-3-web-frontend', 'F4.B.3 Web 前端字段对齐', 'pending',
    'api.ts 加 CraftingPayload；liveAdapter.ts 优先消费 detail.crafting 删除派生；CraftingTreeView.tsx 切到新字段。'),
  ('f4b-4-v2-panel', 'F4.B.4 V2 CraftingPanel 升级', 'pending',
    'CraftingSubPanel 接入完整 CraftOrderFlow / Dialog 系统；通过 LatestSnapshotProvider.getCrafting() 读快照；V2UiActions 发动作。'),
  ('f4b-5-docs-release', 'F4.B.5 文档与发版', 'pending',
    '更新 13-更新日志.md beta.6 条目；同步 06-UI系统.md / 05-网络通信.md；gradle.properties beta.6；单 commit + Co-authored-by trailer。');

INSERT INTO todo_deps (todo_id, depends_on) VALUES
  ('f4b-2-web-backend', 'f4b-1-modernui'),
  ('f4b-3-web-frontend', 'f4b-2-web-backend'),
  ('f4b-4-v2-panel', 'f4b-1-modernui'),
  ('f4b-5-docs-release', 'f4b-3-web-frontend'),
  ('f4b-5-docs-release', 'f4b-4-v2-panel');
```

---

## 12. 联系点 / 不确定时的决策权重

如果你（AI agent）遇到本文档没覆盖的歧义：

1. **优先模仿现成模式**：F2.B Storage 三方迁移是最完整的参考样板（commit `ea80ce31` 的 diff 全是这套模式）
2. **保持兼容**：所有 Web JSON 字段新增可选；旧字段保留；Payload 新增字段需要 codec 版本号 +1
3. **测试优先**：纯逻辑（formatter / calculator / evaluator）必须有 JUnit 5 单测
4. **文档跟随**：架构改动同步 `02 / 05 / 06 / 07 / 08` 中相关文件
5. **不确定时停下问用户** —— 但本计划设计为「单跑通 F4.B → F4.C → F5」三连发，常态下不需要追加澄清

---

> **本文档是后续开发的唯一权威 handoff 文件。任何偏离请先在 commit message 里记录原因。**

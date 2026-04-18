# Copilot Instructions — Resource Observer

## Build & Run

```shell
# Build the mod JAR
./gradlew build

# Run Minecraft client with the mod loaded (dev environment)
./gradlew runClient

# Run dedicated server (headless)
./gradlew runServer

# Run data generators (loot tables, recipes, etc.)
./gradlew runData

# Run game tests
./gradlew runGameTestServer

# Refresh dependencies after adding/changing them
./gradlew --refresh-dependencies

# Full clean rebuild
./gradlew clean build
```

Java 21 is required. The project uses NeoForge ModDevGradle plugin (`net.neoforged.moddev`).

## Architecture

Resource Observer is a NeoForge 1.21.1 Minecraft mod that monitors in-game resource networks (AE2 storage, Flux Networks energy, Mekanism energy). It consists of three gameplay objects:

- **Observer Block** — placed in-world, binds to one network, samples data every 20 ticks server-side
- **Binding Tool** — two-step right-click state machine (select observer → select target network block)
- **Resource Terminal** — item that opens a GUI showing observer data across three tabs (Overview, Storage Network, Power Network)

### Package Layout (`com.yuyinrl.resourceobserver`)

| Package | Responsibility |
|---------|---------------|
| `client/` | Client-side rendering, input handling, shaders |
| `client/modernui/` | ModernUI Fragment + PageBuilder classes for the enhanced UI |
| `client/screen/` | Vanilla Minecraft Screen fallback implementation |
| `client/ui/` | ViewModels, ViewModelMappers, layout specs, theme tokens |
| `client/ui/render/` | 15+ specialized renderers (charts, tables, KPIs, etc.) |
| `integration/` | Adapter classes for optional mod integrations |
| `network/` | Custom payload records, codec utilities, payload registration |
| `registry/` | DeferredRegister classes (ModBlocks, ModItems, ModBlockEntities, ModCreativeTabs) |
| `ui/state/` | Shared UI state enums (filters, sort modes) |
| `world/block/` | ObserverBlock + ObserverBlockEntity (core sampling logic) |
| `world/command/` | Debug commands |
| `world/history/` | HistoryRecorder + SavedData for historical sampling |
| `world/item/` | BindingToolItem, ResourceTerminalItem, DebugTerminalItem |
| `world/ui/` | Player UI preferences persistence (SavedData) |

### Data Flow

```
Server tick → ObserverBlockEntity.serverTick() (samples network every 20 ticks)
           → HistoryRecorder → ObserverHistorySavedData (world persistence)

Player opens terminal → ResourceTerminalItem sends ObserverDataPayload (S→C)
                     → ViewModelMapper.map(payload) → ViewModel
                     → PageBuilder / Renderer (UI display)

Client actions → ObserverUiActionPayload / ObserverRefreshRequestPayload (C→S)
```

### Dual UI System

The mod maintains two parallel UI implementations sharing the same ViewModels and Mappers:

1. **ModernUI Fragment** (`ResourceTerminalFragment` + `*PageBuilder` classes) — rich UI using icyllis ModernUI's View/Fragment/Canvas APIs
2. **Vanilla Screen** (`ResourceTerminalScreen`) — fallback using Minecraft's `GuiGraphics`

When adding UI features, update both implementations.

## Key Conventions

### Registration — DeferredRegister

One registry class per category, each with a static `register(IEventBus)` method called from `ResourceObserverMod` constructor:

```java
public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
public static final DeferredBlock<ObserverBlock> OBSERVER_BLOCK =
    BLOCKS.register("observer_block", () -> new ObserverBlock(...));
public static void register(IEventBus bus) { BLOCKS.register(bus); }
```

### Networking — Record Payloads with StreamCodec

Each payload is a Java `record` with a static `TYPE` and `STREAM_CODEC`. Registered in `ModNetworking` via `RegisterPayloadHandlersEvent`. Handlers use `context.enqueueWork()` for thread safety:

```java
registrar.playToClient(ObserverDataPayload.TYPE, ObserverDataPayload.STREAM_CODEC, handler);
registrar.playToServer(ObserverRefreshRequestPayload.TYPE, ..., handler);
```

### Mod Integrations — Adapter Pattern

| Mod | Type | Notes |
|-----|------|-------|
| AE2 | **Required** | Direct API imports (`appeng.api.*`); declared in `neoforge.mods.toml` |
| ModernUI | **Required** (client) | Fragment-based UI framework |
| Flux Networks | **Optional** | Runtime-checked via `FluxNetworksIntegration.isAvailable()` |
| Mekanism | **Optional** | Accessed via reflection for energy unit conversion |

Always guard optional integration code with availability checks. New integrations should follow the adapter pattern in the `integration/` package.

### Comments — Chinese Language

All Javadoc and inline comments must be written in **Chinese (中文)**. This is a project-wide convention per the coding standards document. Do not write comments in English.

### Code Style

- Java 21 features are encouraged (records, pattern matching, sealed classes, etc.)
- Max line length: 120 characters
- No wildcard imports — use explicit imports
- Import order: `java.*` → `javax.*` → third-party → project packages
- Guard clauses preferred over deep nesting (max 3 levels)
- SLF4J logging only — no `System.out.println()`
- Use `try-with-resources` for closeable resources

### Version Management

All dependency and mod versions are centralized in `gradle.properties`. Never hardcode versions in `build.gradle`. Key properties:

- `minecraft_version`, `neo_version` — must agree for valid artifacts
- `mod_id` — must match the `@Mod` annotation string constant
- `modernui_core_version`, `modernui_mc_version`, `mekanism_version`

### Mod Metadata Templating

`src/main/templates/META-INF/neoforge.mods.toml` uses Gradle property expansion (`${mod_id}`, `${mod_version}`, etc.). The `generateModMetadata` task processes this automatically. Edit the template, not the generated output.

### Resources

- `src/main/resources/assets/resourceobserver/` — textures, models, blockstates, lang files (en_us + zh_cn), GLSL shaders
- `src/main/resources/data/resourceobserver/` — loot tables, recipes
- `src/generated/resources/` — data generator output (included via `sourceSets` config)

### Reference Directories (Not Part of Build)

The top-level `net/`, `temp_core/`, `temp_core_extract/`, and `temp_jar_extract/` directories contain extracted dependency sources for development reference. They are not compiled or included in the mod JAR.

---

## 常用任务指令

### 更新文档（每日开发结束时执行）

当用户说 **"更新文档"** 时，执行以下流程：

1. **收集今日变更内容**：
   - 回顾当前 session 上下文（plan.md、checkpoint 记录、已完成的 todo）
   - 如果有 git 变更，用 `git --no-pager log --since="today" --oneline` 和 `git --no-pager diff --stat` 获取变更文件列表
   - 总结今天新增的功能、修复的 bug、变更的架构

2. **更新设计文档更新日志**：
   - 编辑 `docs/designdoc/13-更新日志.md`
   - 在最新版本号下按 `### 新增` / `### 修复` / `### 变更` 分类追加条目
   - 每条附带相关设计文档的 `[[wikilink]]` 引用
   - 更新底部的「文档更新记录」表格

3. **如涉及架构变更**，同步更新对应的设计文档（如 `06-UI系统.md`、`05-网络通信.md` 等）

4. **输出摘要**：完成后向用户简要汇报更新了哪些内容

### 汇总提交

当用户说 **"汇总提交"** 时，执行以下流程：

1. **收集变更范围**：
   - 使用 `git --no-pager diff --stat HEAD` 查看所有待提交的文件变更
   - 回顾当前 session 完成的功能、修复和变更内容

2. **暂存所有修改**：
   - 执行 `git add -A` 将全部变更加入暂存区

3. **生成提交日志**：
   - 提交信息格式如下：
   ```
   [v版本号] 更新摘要（一句话概括）

   ### 新增
   - 功能描述 1
   - 功能描述 2

   ### 修复
   - 修复描述 1

   ### 变更
   - 变更描述 1

   Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
   ```
   - 版本号从 `docs/designdoc/13-更新日志.md` 中最新版本条目获取
   - 各条目内容与更新日志保持一致，但更简洁（每条一行）
   - 无对应分类时省略该分类标题

4. **执行提交**：
   - 使用 `git commit` 进行一次性汇总提交

5. **输出确认**：
   - 向用户展示提交的 hash、变更文件数和提交日志摘要

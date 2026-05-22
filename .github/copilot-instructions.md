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

## 自定义 Skills

项目级自定义 skills 位于 `.github/skills/`，每个子目录是一个独立 skill（含 `SKILL.md`）。详见 `.github/skills/README.md`。

可用触发词：

| 触发词 | 用途 | 别名 |
|--------|------|------|
| `/update-doc` | 更新设计文档与更新日志 | "更新文档" |
| `/commit` | 汇总提交所有修改 | "汇总提交" |
| `/design` | 口语需求转结构化设计 | — |
| `/debug` | 分析报错与异常 | — |
| `/analyze-java` | 分析 Java 类结构 | — |
| `/analyze-gradle` | 分析 Gradle 配置 | — |
| `/mod-design` | Minecraft Mod 功能设计 | — |
| `/next-page` | Next.js 页面设计 | — |
| `/api-flow` | API 数据流分析 | — |
| `/refactor` | 重构建议 | — |

当用户使用上述触发词或别名时，按对应 `.github/skills/<name>/SKILL.md` 中的步骤执行。

### 全局规则（适用于所有 skill）

- 不读取整个项目，优先局部文件
- 优先使用 git 信息（log / diff / status）
- 信息不足时主动请求用户澄清
- 避免大范围扫描
- 输出必须结构化（带分级标题/列表）
- 修改前说明影响范围
- 不确定的地方必须明确标注

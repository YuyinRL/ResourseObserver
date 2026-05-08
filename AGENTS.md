# Resource Observer — Project Overview

> Auto-generated project guide for AI agents and new sessions.  
> Last updated: 2026-05-03

---

## 1. What is this?

**Resource Observer** is a Minecraft NeoForge mod (version `0.1.0`) that provides **real-time resource network monitoring** inside the game. Players place Observer blocks, bind them to AE2 storage networks or Flux energy networks, and view real-time data, charts, and alerts via:
- An in-game **ModernUI** terminal (Canvas-based rendering)
- A built-in **Web Dashboard** (React SPA served from the mod's own HTTP server)

---

## 2. Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **Mod Platform** | NeoForge (Minecraft) | 1.21.1 / 21.1.206 |
| **Language** | Java | 21 |
| **Build** | Gradle (NeoForge moddev) | 2.0.141 |
| **In-game UI** | ModernUI (icyllis) | 3.12.0 |
| **Web Server** | JDK `com.sun.net.httpserver.HttpServer` | built-in |
| **Web Frontend** | React + Vite + TypeScript | 18 / 6.3 |
| **Web Styling** | Tailwind CSS + MUI + Radix UI | 4.1 / 7.3 |
| **Web Charts** | Recharts | 2.15 |
| **Web Routing** | React Router | 7.13 (minimal use; tab state-based) |

---

## 3. Directory Structure

```
ResourseObserver/
├── AGENTS.md                              # This file
├── build.gradle                           # NeoForge moddev build config
├── gradle.properties                      # mod_id, version, dependency coordinates
├── settings.gradle
│
├── src/main/java/com/yuyinrl/resourceobserver/
│   ├── ResourceObserverMod.java           # @Mod entry point
│   ├── DevMode.java                       # Dev/production detection
│   │
│   ├── client/                            # Client-side only
│   │   ├── modernui/                      # ModernUI Canvas pages (10 files)
│   │   │   ├── ResourceTerminalFragment.java    # Root tab container
│   │   │   ├── OverviewPageBuilder.java         # KPI cards + chart
│   │   │   ├── StorageNetworkPageBuilder.java   # Items table + browser
│   │   │   ├── PowerNetworkPageBuilder.java     # Power metrics + load
│   │   │   ├── CraftingSubTabBuilder.java       # Crafting UI
│   │   │   ├── ChartView.java                  # Hermite chart
│   │   │   ├── DonutChartView.java             # Donut widget
│   │   │   ├── ModernUiTheme.java              # Theme tokens
│   │   │   └── ItemTextureCache.java           # Item icon cache
│   │   ├── ui/                           # ViewModel layer
│   │   │   ├── OverviewViewModel.java / Mapper.java
│   │   │   ├── StorageNetworkViewModel.java / Mapper.java
│   │   │   ├── PowerNetworkViewModel.java / Mapper.java
│   │   │   ├── CraftingViewModel.java / Mapper.java
│   │   │   └── render/ (TableRenderer, WatchlistRenderer...)
│   │   ├── screen/ResourceTerminalScreen.java   # Vanilla fallback screen
│   │   └── web/IconRenderer.java               # Item icon PNG renderer
│   │
│   ├── integration/                      # Mod adapters (compileOnly API)
│   │   ├── FluxNetworksIntegration.java
│   │   ├── CraftingDataCollector.java
│   │   ├── CraftingOrderService.java          # Plan/confirm/cancel orders
│   │   ├── CraftingTreeNode.java
│   │   └── JecIntegration.java                # Pinyin search
│   │
│   ├── network/                          # NeoForge packet payloads (records)
│   │   ├── ModNetworking.java                # Registration & dispatch
│   │   ├── ObserverDataPayload.java           # S→C data push
│   │   ├── ObserverRefreshRequestPayload.java # C→S refresh
│   │   ├── ObserverUiActionPayload.java       # C→S UI actions
│   │   ├── CraftingPlanResultPayload.java     # S→C plan result
│   │   ├── CraftingTreeRequestPayload.java    # C→S tree request
│   │   ├── CraftingTreeResponsePayload.java   # S→C tree response
│   │   └── ChartWindow.java / ChartScope.java # Chart window config
│   │
│   ├── registry/                         # DeferredRegister entries
│   │   ├── ModBlocks.java / ModBlockEntities.java
│   │   ├── ModItems.java / ModCreativeTabs.java
│   │   └── ...
│   │
│   ├── web/                              # Built-in HTTP server
│   │   ├── WebServerService.java             # Lifecycle (ServerStartedEvent)
│   │   ├── WebServerConfig.java              # Config spec (host:port/CORS)
│   │   ├── JsonWriter.java                   # Manual JSON serializer
│   │   └── handler/
│   │       ├── ObserversHandler.java         # List & detail
│   │       ├── ItemsHandler.java             # Paginated item queries
│   │       ├── HistoryHandler.java           # Chart history data
│   │       ├── CraftingHandler.java          # Crafting jobs
│   │       ├── CraftingOrderHandler.java     # Plan/confirm/cancel
│   │       ├── MetaHandler.java / HealthHandler.java
│   │       ├── IconHandler.java              # Item icon PNG
│   │       ├── StaticHandler.java            # Web dashboard files
│   │       └── ServerAssetIndex.java         # Jar asset scanner
│   │
│   ├── world/                            # Block/entity/item/data
│   │   ├── block/ObserverBlock.java
│   │   ├── entity/ObserverBlockEntity.java   # Core logic (2726 lines!)
│   │   ├── item/ResourceTerminalItem.java, BindingToolItem.java...
│   │   ├── history/HistoryRecorder.java, WebHighPrecisionSampler.java (1200-bucket ring buffer)
│   │   ├── ui/PlayerUiPrefsSavedData.java
│   │   └── command/ObserverCommands.java
│   │
│   └── ui/state/                         # Enum types
│       ├── TableSortMode.java
│       ├── TableStatusFilter.java
│       └── TableGroupFilter.java
│
├── src/main/resources/assets/resourceobserver/
│   ├── web/                               # Vite build output (SPA files)
│   ├── lang/en_us.json, zh_cn.json        # 500+ translation keys each
│   ├── models/, textures/, blockstates/   # Standard MC assets
│   └── shaders/core/                      # GLSL chart shaders
│
└── Data Visualization Dashboard/          # React frontend (separate pnpm project)
    ├── package.json (70+ deps), vite.config.ts
    ├── src/
    │   ├── main.tsx → App.tsx
    │   └── app/
    │       ├── lib/api.ts                 # REST client + 20+ TypeScript interfaces
    │       ├── lib/liveAdapter.ts          # ObserverDetail → UI shapes
    │       ├── lib/i18n.tsx                # EN/ZH (560 lines, ~180 keys)
    │       ├── hooks/usePolling.ts         # Generic setInterval poller
    │       ├── hooks/useObservers.ts       # 7 typed API hooks
    │       ├── components/
    │       │   ├── Overview.tsx, PowerNetwork.tsx, StorageNetwork.tsx
    │       │   ├── CraftingTreeView.tsx + crafting-tree/ subfolder
    │       │   ├── Sidebar.tsx, ObserverSelector.tsx
    │       │   └── ui/ (55+ Radix UI wrappers)
    │       └── ...
    └── src/styles/ (index.css, tailwind.css, theme.css, fonts.css)
```

---

## 4. Architecture

### 4.1 Entry Points

- **Java**: `ResourceObserverMod.java:22` — `@Mod("resourceobserver")` constructor registers all blocks/items/entities/networking/web server/commands
- **Web Dashboard**: `main.tsx:2` — renders `<App />` into `#root`
- **Web Server**: `WebServerService.java:40` — starts on `ServerStartedEvent`, binds `127.0.0.1:28080`, 4-thread daemon pool

### 4.2 Data Flow

```
ObserverBlockEntity (server tick)
  → HistoryRecorder → WebHighPrecisionSampler (1200-bucket ring buffer, 0.25s granularity)
  → Web API handlers query snapshot
  → React frontend polls /api/observers/{id} every 5s
  → liveAdapter.ts transforms → UI components
```

### 4.3 Web API Routes

| Route | Handler | Description |
|-------|---------|-------------|
| `/api/health` | HealthHandler | Server health |
| `/api/meta` | MetaHandler | Mod metadata |
| `/api/observers` | ObserversHandler | Observer list |
| `/api/observers/{id}` | ObserversHandler | Observer detail |
| `/api/observers/{id}/crafting` | CraftingHandler | Crafting jobs |
| `/api/observers/{id}/items` | ItemsHandler | Paginated items |
| `/api/observers/{id}/history` | HistoryHandler | Chart data |
| `/api/observers/{id}/crafting/plan` | CraftingOrderHandler | Plan/confirm/cancel |
| `/api/auth/whoami` | AuthHandler | Returns `{ uuid, name, admin, authMode }` for the current Bearer token |
| `/api/icon/{item}` | IconHandler | Item icon PNG |
| `/` | StaticHandler | Web dashboard SPA |

All handlers use `MinecraftServer.execute(Runnable)` for thread safety.

### 4.3.1 Authentication & Access Control

All `/api/**` routes (except `/api/health` and `/api/meta`) are wrapped by `AuthFilter`,
which expects an `Authorization: Bearer <token>` header. Tokens are minted server-side
per player and persisted via `PlayerWebTokenSavedData`.

- **Token issuance**: Players obtain a token without typing commands by either:
  1. Clicking the **🌐** button in the top-right of the in-game Resource Terminal
     (opens `WebAccessDialog` with copyable URL + regenerate button), or
  2. Receiving the **first-use chat link** auto-pushed by `WebTokenService` when
     they first right-click the terminal. The link uses `ClickEvent.OPEN_URL` and
     embeds `?t=<token>` in the dashboard URL.
- **Frontend token handling** (`Data Visualization Dashboard/src/app/lib/auth.ts`):
  on first load, `consumeUrlToken()` reads `?t=<token>` from the URL, persists it in
  `localStorage["ro.token"]`, and strips the query so the token never lingers in browser
  history. Every fetch via `apiGet`/`authedFetch` injects `Authorization: Bearer`.
  A 401 response triggers `notifyUnauthorized()` → clears the token → `LoginGate` shows
  instructions for re-binding.
- **Per-observer ACL**: `ObserverBlockEntity.ownerUuid` is set in `ObserverBlock.setPlacedBy`.
  `BaseApiHandler.AccessResult<T>` and `runOnMainWithAccess()` enforce that non-admin
  viewers can only see observers they own. Admins (configurable in `WebServerConfig`) see all.
- **Auth modes**: `WebServerConfig.authMode` accepts `OFF` (legacy open access),
  `TOKEN` (default, enforced), and `OWNER_ONLY` (combined with `LegacyObserverPolicy`
  for observers placed before this feature existed).

### 4.4 Network Protocol

NeoForge packets (version 8). All payloads are Java `record` types with `STREAM_CODEC`.
- `ObserverDataPayload`: Server→Client (observer snapshot)
- `ObserverRefreshRequestPayload` + `ObserverUiActionPayload`: Client→Server
- `CraftingPlanResultPayload` + `CraftingTreeRequest/ResponsePayload`: Crafting flow
- `ClientNameUploadPayload` + `ClientIconUploadPayload`: Client uploads localized names/icons for web use
- `RequestWebTokenPayload` (C→S, `{ regenerate }`) + `WebTokenPayload` (S→C, `{ url, baseUrl, token, regenerated, reliable }`): Web access token issuance flow

### 4.5 Web Frontend State

- **State**: React Context only (`I18nProvider`, `ObserverSelectionProvider`) — no external store
- **Data Fetching**: Custom `usePolling<T>(fetcher, intervalMs, enabled)` hook
- **Polling Intervals**: 5s default, 2s detail charts, 15-30s history
- **Navigation**: Tab-based (overview/storage/power) via state switch, NOT React Router routes

---

## 5. Key Patterns & Conventions

1. **Adapter Pattern**: Optional mod integrations (Flux, Mekanism, Draconic, JEC) use `compileOnly` API + `localRuntime` implementation
2. **DevMode**: `DevMode.isDev()` controls debug terminal tab and creative tab access; `/observer debug` command preserved in production
3. **Manual JSON**: `JsonWriter.java` handles all web API serialization (no Gson/Jackson)
4. **Record Types**: All network payloads are Java records with STREAM_CODEC
5. **Comment Language**: Chinese comments throughout Java code
6. **Config**: `config/resourceobserver-web.toml` (TOML format, NeoForge ModConfigSpec)

### Mod Dependencies
- **Required (client)**: AE2, ModernUI
- **Optional**: Flux Networks, Mekanism, Draconic Evolution, JEC (pinyin search)

---

## 6. Commands

```bash
# === Mod Build & Run ===
./gradlew build              # Build mod JAR
./gradlew runClient           # Launch Minecraft client (dev)
./gradlew runServer           # Launch dedicated server
./gradlew runData             # Run data generators

# === Web Dashboard ===
cd "Data Visualization Dashboard"
pnpm install
pnpm dev                      # Vite dev server → proxies /api to localhost:8877
pnpm build                    # Build → src/main/resources/assets/resourceobserver/web/
```

### Vite Config Notes
- Build output: `../src/main/resources/assets/resourceobserver/web/`
- Dev proxy: `/api` → `http://127.0.0.1:8877`
- Base: `./` (relative paths for embedded serving)

### Gradle Properties
`mod_id=resourceobserver`, `mod_version=0.1.0`, `minecraft_version=1.21.1`, `neo_version=21.1.206`

---

## 7. Testing

**No test files exist** in either Java source or web dashboard. Tests have not been established yet.

---

## 8. Core Classes Quick Reference

| Class | Lines | Role |
|-------|-------|------|
| `ObserverBlockEntity.java` | 2726 | Core server logic — samples AE2/Flux, manages history |
| `WebServerService.java` | ~200 | HTTP server lifecycle & route registration |
| `JsonWriter.java` | ~300 | Manual JSON serialization for all API responses |
| `liveAdapter.ts` | ~400 | Transforms raw API data into UI-friendly shapes |
| `api.ts` | ~500 | REST API client with 20+ TypeScript interfaces |
| `i18n.tsx` | 560 | EN/ZH translations with ~180 keys |
| `useObservers.ts` | ~200 | 7 typed React hooks for each API endpoint |
| `ResourceTerminalFragment.java` | ~400 | Root ModernUI tab container |
| `ChartView.java` | ~300 | Hermite-interpolated line charts |
| `ModNetworking.java` | ~150 | Packet registration & handler dispatch |

# Resource Observer — In-Game ModernUI Reference

**Version:** 1.0 | **Purpose:** Complete feature reference for mirroring to Web Dashboard (React + TypeScript)

This document comprehensively documents the Minecraft client UI built with ModernUI 3.7+ (icyllis/ModernUI), detailing every page, component, dialog, and interaction pattern found in the Resource Observer terminal.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Page Structure](#page-structure)
3. [Global Refresh & State Management](#global-refresh--state-management)
4. [Overview Page (总览)](#overview-page)
5. [Storage Network Page (存储网络)](#storage-network-page)
6. [Power Network Page (电力网络)](#power-network-page)
7. [Dialogs & Popups](#dialogs--popups)
8. [Server Actions (UiActionType Enum)](#server-actions-uiactiontype-enum)
9. [Right-Click Flows & Terminal Lifecycle](#right-click-flows--terminal-lifecycle)
10. [Charts, Graphs & Data Visualization](#charts-graphs--data-visualization)

---

## Architecture Overview

### Core Components

| Component | Role | File |
|-----------|------|------|
| **ResourceTerminalFragment** | Main container; tab switching; lifecycle; refresh loop | ResourceTerminalFragment.java |
| **ViewModelBridge** | Holds all ViewModels + search query; notifies listeners on data change | ViewModelBridge.java |
| **OverviewPageBuilder** | Renders Overview page layout & interactions | OverviewPageBuilder.java |
| **StorageNetworkPageBuilder** | Renders Storage Network page + Crafting sub-tab | StorageNetworkPageBuilder.java |
| **PowerNetworkPageBuilder** | Renders Power Network page with debug panel | PowerNetworkPageBuilder.java |
| **ChartView / ChartRenderer** | Renders line charts for production/consumption/stock | ChartView.java, ChartRenderer.java |
| **DonutChartView** | Renders power network capacity donut chart | DonutChartView.java |
| **ModernUiTheme** | Global theme tokens, layout helpers, animations | ModernUiTheme.java |

### Data Flow

\\\
Server (ObserverDataPayload) 
  ↓ [Network]
ClientPayloadHandler 
  ↓
ResourceTerminalFragment.applyPayload() 
  ↓
ViewModelBridge.applyPayload() 
  ↓
[OverviewViewModel, StorageNetworkViewModel, PowerNetworkViewModel] 
  ↓ [Listeners notify]
ResourceTerminalFragment.onDataChanged() 
  ↓
buildPageContent() → rebuild active page
\\\

---

## Page Structure

### Top-Level Layout

The UI is a **centered panel** with three main regions:

1. **Chrome Bar** (fixed, top)
   - Horizontal tab bar (Overview | Storage Network | Power Network | Dev Components)
   - Each tab is a clickable TextView, highlighted when active
   - Height: ~28dp, always visible

2. **Content Container** (scrollable, main area)
   - Grows to fill remaining space
   - Contains active page (Overview, Storage, Power, or Dev)
   - ScrollView clips child content
   - Height responsive to screen dimensions

3. **Dialog Overlay** (FrameLayout, top-level)
   - Sits above content but below PopupWindows
   - Used for KPI detail dialogs, rename popups, etc.
   - Invisible until dialog is shown

### Screen Dimensions & Responsiveness

- **Panel width:** 90% of screen width
- **Panel height:** 90% of screen height
- **Scale modes:** S (1.0x), M (1.2x, default), L (1.45x)
  - Toggle at top-right of chrome
  - Affects TEXT_SCALES and LAYOUT_SCALES globally
  - Applied via ModernUiTheme.setUiScale()

---

## Global Refresh & State Management

### Refresh Cadence

| Chart Window | Mode | Interval |
|--------------|------|----------|
| HOUR_1H_1M | **Fast** | **500ms** |
| DAY_24H_5M | Normal | 1000ms |
| WEEK_7D_30M | Normal | 1000ms |
| DEBUG_10M_5S | Fast | 500ms |

**Mechanism:**
- \ResourceTerminalFragment.refreshRunnable\ posts itself with \currentRefreshInterval()\ delay
- Calls \equestRefreshNow()\ → sends \ObserverRefreshRequestPayload\ to server
- Server responds with \ObserverDataPayload\
- Client calls \pplyPayload()\ → ViewModels updated → \onDataChanged()\ fired

### Incremental Update Strategy

To avoid flickering and preserve state (hover, animations, scroll position):

- **Overview:** Structural key comparison; if same, only update KPI values + table cells in-place
- **Storage Network:** Tagged sections; KPI cards, node list, item list updated separately
- **Power Network:** Donut chart state preserved; KPI values refreshed in-place

**Search Debounce:** 200ms delay before ViewModel rebuild on search input

---

## Overview Page

### Layout (Top-to-Bottom)

1. **Header Section** (12% of page height)
   - Left: Title ("Resource Observer") + optional subtitle
   - Right: Link status badge (green dot if linked, red if not)

2. **KPI Cards Section** (18% of page height)
   - 4 horizontal cards: **Production** | **Consumption** | **Storage** | **Balance**
   - Each card shows: label + value + trend (e.g., "+5%")
   - Click → opens KPI detail dialog
   - **STORAGE card only** → opens dedicated Storage Detail dialog

3. **Chart Section** (32% of page height)
   - Title row with toolbar buttons
   - ChartView (line chart)
   - Subtitle (scope + window label)
   - Legend (bottom left) + Data type radio group (bottom right)

4. **Watchlist Section** (18% of page height, if items exist)
   - Cards in grid (1–3 per row, responsive)
   - Pagination controls (prev/next buttons)
   - Each card: item icon + name + net/min + stock + remove button

5. **Table Section** (remainder, scrollable)
   - Grouped rows by filter (e.g., "No Group", "Category A")
   - Collapsible groups
   - Columns: Icon | Name | Production | Consumption | Net | Stock

### KPI Cards

| Field | Type | Source | On Click |
|-------|------|--------|----------|
| **type** | enum (PRODUCTION, CONSUMPTION, STORAGE, BALANCE) | KpiMetric.type | showKpiDetailDialog / showStorageDetailDialog |
| **label** | string | KpiMetric.label | — |
| **value** | string (formatted) | KpiMetric.value | — |
| **trend** | string (e.g., "+5%") | KpiMetric.trend | — |
| **status** | enum (POSITIVE, WARNING, NEGATIVE, NEUTRAL) | KpiMetric.status | Determines color |
| **icon** | sprite path | KpiMetric.iconSprite | Not rendered in current build (placeholder exists) |

**Animations:**
- On value change: pulse animation (scale 1.0 → 1.04 → 1.0 in 250ms)
- Value text flashes (alpha 1.0 → 0.35 → 1.0 in 220ms)

### Chart Configuration

**ChartWindow Enum:**
- HOUR_1H_1M: 1H, 1-min buckets (60 points, 1200 ticks/bucket)
- DAY_24H_5M: 24H, 5-min buckets (288 points, 6000 ticks/bucket, **default**)
- WEEK_7D_30M: 7D, 30-min buckets (336 points, 36000 ticks/bucket)
- DEBUG_10M_5S: 10m, 5-sec buckets (120 points, 100 ticks/bucket)

**ChartPage Enum:**
- THROUGHPUT: Production, Consumption, Net lines
- STOCK: Storage quantity line

**LineMode Enum (Throughput only):**
- ALL: All three lines (production, consumption, net)
- PRODUCTION: Production only
- CONSUMPTION: Consumption only
- NET: Net only

**SmoothingMode Enum:**
- SMOOTH: Spline interpolation
- RAW: Direct point-to-point

**ChartDataType Enum:**
- ITEMS: Item flow data
- ENERGY: FLUX_ENERGY production/consumption (if available)

**Controls (Top Toolbar):**
| Button | Action | Behavior |
|--------|--------|----------|
| **Window** | Cycle through ChartWindow values | Circular: 1H → 24H → 7D → (1H) |
| **Page** | Toggle THROUGHPUT ↔ STOCK | Updates legend, chart data |
| **Mode** | Open dropdown menu (THROUGHPUT only) | Filters lines shown; disabled on STOCK page |
| **Smooth** | Toggle SMOOTH ↔ RAW | Updates curve interpolation |
| **Data Type** | Radio group: Items / Energy | Switches between chartSeries and energyChartSeries |

**Legend:**
- THROUGHPUT: "Production" (cyan), "Consumption" (amber), "Net" (emerald)
- STOCK: "Stock" (blue)

**Chart Scope (via click-on-table-row):**
- Click any item row → \setSelectedItemId()\ → chart scope becomes ITEM
- Click chart area (outside rows) → scope resets to GLOBAL
- Subtitle updates: "Scope: [Item Name] | Window: [1H/5m]"

### Table Layout

**Group Structure:**
- Header: group name (collapsible, toggle icon + text)
- Rows (if expanded):
  - Icon (12×12dp)
  - Item Name (truncated)
  - Production (compact format)
  - Consumption (compact format)
  - Net (±, colored green/red/gray)
  - Stock (compact format)
  - Action icons (star for favorite, context menu)

**Row Actions:**
- **Left-click:** Select item → update chart scope
- **Right-click:** Context menu (Copy to Clipboard, Add to Group, Remove from Group, etc.)
- **Star icon:** Toggle watch/favorite status

**Filtering & Sorting:**
- Dropdown: Group filter (All, Custom Group 1, Custom Group 2, …)
- Dropdown: Sort mode (Name, Production, Consumption, Net, Stock)
- Toggle: Ascending ↔ Descending
- Dropdown: Status filter (All, In Stock, Low Stock, Critical)

---

## Storage Network Page

### Layout (Top-to-Bottom)

1. **Sub-Tab Header** (fixed, ~32dp)
   - Two tabs: **Items** (active) and **Crafting**
   - Switch updates page content

2. **Items Tab: Two-Column Layout**
   - **Left Column (33%):**
     - Node List section
     - Usage Summary (segmented bar + text)
   - **Right Column (67%):**
     - Filter/Alert bar (status pills, search)
     - Item Health Table

3. **Crafting Tab:** (See "Crafting Sub-Tab" below)

### KPI Cards (Storage Network)

| Card | Type | Value Source | Status Indicator |
|------|------|--------------|------------------|
| **Stored Items** | count | vm.storedItemCount() | Item type count |
| **Total Storage** | bytes | vm.totalStorageBytes() | Color: POSITIVE |
| **Used Storage** | % | (used / total) × 100 | POSITIVE (green), WARNING (yellow), NEGATIVE (red) |
| **Stored Types** | count | vm.totalItemTypes() | Text |

### Node List Section

**Structure:**
- Title: "Network Nodes" + node count
- List of nodes, each showing:
  - Node name (editable via right-click → rename)
  - Status icon (green = online, red = offline)
  - Inventory level (progress bar + text "X / Y slots")

**Right-click Node:**
- **Rename:** Opens text input dialog
- **Copy Name:** Copies to clipboard

**Data Sources:**
- Node ID, name, online status → from StorageNetworkViewModel.Node
- Inventory fill ratio → computed from capacity + current items

### Usage Summary

**Layout:** Stacked bar chart with legend below

**Segments (left-to-right):**
1. **Disk Items** (color: CYAN)
   - Used / Total bytes (from vm.diskItemUsageBytes / vm.diskItemCapacityBytes)

2. **Disk Fluid** (color: BLUE)
   - Used / Total bytes (from vm.diskFluidUsageBytes / vm.diskFluidCapacityBytes)

3. **External Items** (color: AMBER)
   - Used / Total bytes (from vm.externalItemUsageBytes / vm.externalItemCapacityBytes)

4. **External Fluid** (color: ROSE)
   - Used / Total bytes (from vm.externalFluidUsageBytes / vm.externalFluidCapacityBytes)

**Interactivity:**
- Hover segment → shows tooltip with exact bytes + percentage
- Click segment → (may expand detail, TBD)

### Filter Bar

**Layout (left-to-right):**
- **Alert pill buttons** (toggles):
  - "All" (default)
  - "Critical" (stock < min threshold)
  - "Low" (stock < medium threshold)
  - "Excess" (stock > max threshold)
  - "Craftable" (recipes available)

- **Search box:** Real-time filter by item name/ID (200ms debounce)

### Item Health Table

**Columns:**
| Column | Width | Content | Sortable | Filterable |
|--------|-------|---------|----------|-----------|
| Icon | 16dp | Item texture | No | No |
| Name | flexible | Item display name | Yes (SORT_BY_NAME) | Via search |
| Local Amount | 12% | Compact format | Yes (SORT_BY_AMOUNT) | Via alert filter |
| Δ (delta/min) | 12% | ±N with color | Yes (SORT_BY_DELTA) | — |
| Burn Rate | 12% | Items/min consumed | Yes (SORT_BY_BURN) | — |
| Buffer Time | 12% | ETA (hours:mins) | — | — |

**Color Coding:**
- Delta > 0: EMERALD (gaining)
- Delta < 0: ROSE (losing)
- Delta = 0: TEXT (neutral)

**Buffer Bar Coloring:**
- Ratio < 20%: ROSE (red)
- Ratio 20–50%: AMBER (yellow)
- Ratio > 50%: EMERALD (green)

**Buffer Time Countdown:**
- Updates via 1-second ticker (independent of data refresh)
- Uses EMA (exponential moving average) to smooth burn rate
- Format: "HH:mm:ss" or "critical" if ratio < 5%

### Crafting Sub-Tab

**Layout:**
- **KPI Row** (4-column)
  - CPU Total Count
  - Busy CPUs / Total
  - Storage Bytes
  - Coprocessor Count

- **Two-Column Content:**
  - **Left (33%):** Active Jobs list
    - Title: "Jobs"
    - Rows:
      - CPU name (colored cyan if busy)
      - Output item icon + name (if busy)
      - Progress bar
      - "Idle" text (if not busy)

  - **Right (67%):** Craftables table
    - Search bar + "N / M available" label
    - Grid/table of craftable items (icon + name)
    - Click to start crafting (if permitted)

---

## Power Network Page

### Layout (Top-to-Bottom)

1. **KPI Cards** (effective stats, excluding selected external storage groups)
   - Input Rate (EU/t)
   - Output Rate (EU/t)
   - Utilization %
   - Headroom (remaining capacity)

2. **Two-Column Layout**
   - **Left (33%):**
     - Load Overview (summary KPI section)
     - Overload Risk (warning if utilization > 80%, shows reserve capacity)
     - External Control (list of external storage groups; click to toggle exclude)

   - **Right (67%):**
     - Power Grid Chart (stacked area or line)
     - Device List (generators, consumers, batteries)

3. **Power Summary Table** (full-width, bottom)
   - By Consumer / Generator type
   - Input/Output rate, device count

### Effective Stats Calculation

When an external storage group is selected (checked), its interface devices are **excluded** from:
- Total Input/Output/Utilization calculations
- Chart data
- Overload risk assessment

**Calculation:**
\\\
effectiveInput = totalInput - excludedInput
effectiveOutput = totalOutput - excludedOutput
headroom% = (effectiveInput - effectiveOutput) / effectiveInput * 100
reserve = effectiveInput - effectiveOutput (in EU/t)
\\\

### External Storage Group List (Dialog)

**Trigger:** Click "External Control" or list icon

**Content:**
- List of external groups (AE2 storage buses, Refined Storage exporters, etc.)
- Checkbox next to each group
- Hover → shows interface device count
- Click row → toggle selection

**On change:**
- Recalculate effective stats
- Update KPI values
- Update chart to exclude selected group's interfaces
- Update consumer list

### Device List

**Layout:** Scrollable table

**Columns:**
| Column | Content | Color |
|--------|---------|-------|
| Type Icon | Generator / Consumer / Battery sprite | — |
| Device Name | User-readable name | — |
| Status | Online / Offline / Overloaded | Icon + text |
| Input Rate | EU/t (if consumer) | BLUE |
| Output Rate | EU/t (if generator) | CYAN |
| Current Storage | % of max (if battery) | Progress bar |

**Row Colors:**
- Active: normal (TEXT color)
- Offline: TEXT_MUTED (dimmed)
- Overloaded: ROSE (red)

---

## Dialogs & Popups

### KPI Detail Dialog

**Trigger:** Click on KPI card (except STORAGE)

**Contents:**
- Title: KPI type name (e.g., "Production Rate")
- Hint text: description / unit info
- Two channels (Item + Fluid):
  - Channel label (e.g., "Items")
  - Recent value + time range
  - Previous value + time range
  - Trend line (arrow + %)
  - Availability badge (if N/A)

**Buttons:**
- Close (X button, top-right) or click outside

**Size:** Fixed ~400×300dp, centered on screen

### Storage Detail Dialog

**Trigger:** Click STORAGE KPI card

**Contents:**
- Title: "Storage Details"
- Sub-sections:
  - **Disk (AE2 ME Drives):**
    - Items: X bytes / Y bytes (progress bar)
    - Fluids: X bytes / Y bytes (progress bar)
    - Reliability badge (✓ or ⚠)

  - **External (Buses/Exporters/Importers):**
    - Items: X bytes / Y bytes
    - Fluids: X bytes / Y bytes
    - Reliability badge

**Hint Text:** If storage is unavailable, shows reason (e.g., "No AE2 binding")

**Buttons:**
- Close

**Size:** Fixed ~450×400dp, centered

### Group Name Input Dialog (Rename)

**Trigger:** Right-click on table group header or row "Move to Group" → "Create New"

**Contents:**
- Label: "Group Name"
- EditText (single-line)
- Two buttons: Cancel | OK

**Validation:**
- Non-empty text required
- Max 32 characters
- No special characters (or sanitize)

**On OK:**
- Send \UiActionType.CREATE_GROUP\ or \RENAME_GROUP\ payload
- Close dialog
- Trigger page rebuild

**Keyboard:**
- Enter → submit
- Escape → cancel

**Size:** ~240×120dp, centered

### Line Mode Dropdown (Chart)

**Trigger:** Click "Mode" button on chart toolbar (THROUGHPUT page only)

**Type:** PopupMenu (native Android-style dropdown)

**Options:**
- ● All (current if selected)
- ○ Production Only
- ○ Consumption Only
- ○ Net Only

**On select:**
- Update \chartLineMode\
- Refresh chart legend + data
- Close menu

### Context Menu (Right-click Row)

**Trigger:** Right-click on table row (Overview or Storage)

**Menu Items:**

| Item | ID | Action | Submenu |
|------|----|---------|---------| 
| Copy Item ID | — | Copy to clipboard | — |
| Add to Group | ASSIGN_BASE | → submenu | List of groups + "Create New" |
| Remove from Group | CLEAR | Send CLEAR_ITEM_GROUP | — |
| Toggle Watch | TOGGLE | Send TOGGLE_WATCH | — |
| (if custom group) Rename Group | RENAME_GROUP | Open rename dialog | — |
| (if custom group) Delete Group | DELETE_GROUP | Confirm delete | — |

**Submenu (Add to Group):**
- List of all groups (All, Category A, Category B, …)
- Click group → send \ASSIGN_ITEM_GROUP\ with group key
- "Create New" at bottom → open rename dialog

### Rename Node Dialog (Storage Network)

**Trigger:** Right-click node in node list → "Rename"

**Contents:**
- Label: "Node Name"
- EditText (pre-filled with current name)
- Buttons: Cancel | OK

**On OK:**
- Send \UiActionType.RENAME_NETWORK\ with new name
- Update node list display
- Close dialog

---

## Server Actions (UiActionType Enum)

All actions are sent via **ObserverUiActionPayload** to the server, which applies them to **PlayerUiPrefsSavedData** and responds with updated **ObserverDataPayload**.

| ID | Action Type | Trigger | Payload Fields | Effect |
|----|-------------|---------|-----------------|--------|
| 0 | TOGGLE_WATCH | Star button on row / Watchlist remove | itemId | Add/remove from watched items list |
| 1 | SET_GROUP_FILTER_KEY | Dropdown: "Group Filter" | actionValue (group key) | Filter table rows by group |
| 2 | SET_SORT_MODE | Dropdown: "Sort Mode" | actionValue (TableSortMode enum) | Sort table rows by mode |
| 3 | SET_STATUS_FILTER | Pill button: "All / Critical / Low / Excess" | actionValue (TableStatusFilter enum) | Filter table rows by stock status |
| 4 | RESET_FILTERS | Button: "Reset" (if custom filters active) | — | Revert to defaults |
| 5 | ASSIGN_ITEM_GROUP | Right-click menu: "Add to Group" | itemId, actionValue (group key) | Move item to group |
| 6 | CREATE_GROUP | Dialog: "Create New Group" | actionValue (group name) | Create custom group |
| 7 | RENAME_GROUP | Dialog: "Rename Group" | actionValue (new name) | Rename existing group |
| 8 | DELETE_GROUP | Right-click menu: "Delete Group" | actionValue (group key) | Delete custom group (confirm) |
| 9 | CLEAR_ITEM_GROUP | Right-click menu: "Remove from Group" | itemId | Move item back to "All" group |
| 10 | RENAME_NETWORK | Right-click node: "Rename" | itemId (node ID), actionValue (new name) | Rename storage node |

**Additional Payload Fields (always included):**
- \observerPos\: BlockPos of the observer block
- \chartWindow\: Current chart window (for context)
- \chartScope\: Current chart scope (GLOBAL or ITEM)
- \scopeItemId\: Selected item ID (if ITEM scope)

---

## Charts, Graphs & Data Visualization

### ChartView (Line Chart)

**Data Structure:**
\\\
List<FlowPoint>
├─ slotIndex: int (bucket position in time window)
├─ production: double (items/t or EU/t)
├─ consumption: double (items/t or EU/t)
├─ net: double (production - consumption)
├─ stock: double (current inventory quantity)
├─ hasFlow: boolean (any production/consumption in bucket)
└─ hasStock: boolean (non-zero stock)
\\\

**Rendering:**
- X-axis: Time (buckets; labels show time ago: "now", "5m ago", "1h ago", etc.)
- Y-axis: Rate (items/t) or Quantity (items)
- Lines:
  - **Production** (CYAN): Slope of rising stock
  - **Consumption** (AMBER): Slope of falling stock
  - **Net** (EMERALD): Production - Consumption
  - **Stock** (BLUE): Absolute quantity in storage

**Interactivity:**
- Hover over data point → tooltip (value + timestamp)
- Zoom: Mouse scroll or pinch (if touch)
- Pan: Drag to shift window

**Legend:**
- Toggles visible lines
- Click label to hide/show line

### DonutChartView (Power Network Capacity)

**Data:**
- Inner radius: 40dp
- Outer radius: 60dp
- Segments: By consumer type (AE2 ME Controller, Refined Storage, AE2 ME Interface, etc.)
- Colors: Palette from CONSUMER_COLORS (8-color cycle)

**Rendering:**
- Arc per consumer, proportional to their power draw
- Center text: "Utilization: 75.2%"

**Interactivity:**
- Hover segment → highlight + tooltip (consumer name + draw)
- Click segment → filter device list to show only devices of that type (TBD)

### Debug Panel (Power Network, Dev mode)

**Activation:** Toggle on "Debug" page (DevComponentsPageBuilder)

**Displays:**
- Raw input/output rates
- Per-device statistics table:
  - Device key
  - Interface key
  - Transfer rate (with sign)
  - Type (input / output / battery)

**Update Rate:** Same as main refresh (500ms or 1000ms)

---

## Right-Click Flows & Terminal Lifecycle

### Binding Tool (Item)

**Purpose:** Link a Resource Observer to a terminal block (optional)

**Right-click Flow:**
1. Player holds Binding Tool
2. Right-click on Terminal Block → sends packet to server
3. Server links block to observer via binding data
4. Server responds with updated terminal data
5. Client closes current terminal GUI, opens updated one

**State Machine:**
- Idle: No binding active
- Binding Pending: Player right-clicked, awaiting server response
- Bound: Terminal is linked to an observer

### Resource Terminal (Item)

**Purpose:** Open the Resource Observer terminal GUI

**Right-click Flow:**
1. Player holds Resource Terminal item
2. Right-click (in air or on block) → opens ResourceTerminalScreen
3. Screen creates ResourceTerminalFragment
4. Fragment requests initial data from server (ObserverRefreshRequestPayload)
5. Server responds with ObserverDataPayload
6. Fragment renders Overview page

**Lifecycle:**
\\\
Right-click 
  ↓
ResourceTerminalItem.use() 
  ↓
new ResourceTerminalScreen(observerPos) 
  ↓ (onCreate)
ClientPayloadHandler.onTerminalOpen() 
  ↓
ServerPayloadHandler.OBSERVER_REFRESH_REQUEST 
  ↓
Server computes & sends ObserverDataPayload 
  ↓
ClientPayloadHandler.onObserverDataPayload() 
  ↓
ResourceTerminalFragment.applyPayload() 
  ↓
buildPageContent() → render Overview 
\\\

### Debug Terminal (Dev Mode)

**Purpose:** Development/testing; opens DevComponentsPageBuilder

**Activation:** Via config or command

**Features:**
- Manual refresh button
- Scale toggle (S/M/L)
- Theme toggle (light/dark, if supported)
- Component showcase (buttons, popups, animations)
- Debug stat display

---

## UI Scale & Theme Configuration

### Scale Modes (S/M/L)

| Mode | TEXT_SCALES | LAYOUT_SCALES | Use Case |
|------|-------------|---------------|----------|
| **S** (Small) | 1.0× | 1.0× | High-res displays, compact view |
| **M** (Medium, default) | 1.2× | 1.2× | Standard Minecraft window |
| **L** (Large) | 1.45× | 1.45× | Low-res displays, accessibility |

**Implementation:**
- Toggle button in chrome bar (top-right), shows current mode
- Cycling: S → M → L → S
- On change: calls \ModernUiTheme.setUiScale(textScale, layoutScale)\
- Rebuilds all text sizes and layout dimensions

### Theme Tokens

All colors defined in **UiThemeTokens.java**:

| Token | Color | Usage |
|-------|-------|-------|
| **CYAN** | 0xFF00BCD4 | Primary actions, linked status, production line |
| **AMBER** | 0xFFFFC107 | Warnings, consumption line |
| **EMERALD** | 0xFF4CAF50 | Success, positive trends, net gain |
| **ROSE** | 0xFFEF5350 | Danger, critical status, net loss |
| **BLUE** | 0xFF2196F3 | Stock line, info |
| **TEXT** | 0xFFE0E0E0 | Primary text |
| **TEXT_MUTED** | 0xFF999999 | Secondary text, labels |
| **TITLE** | 0xFFFFFFFF | Large headings |
| **CARD_BG** | 0x0E182A41 | Card background |
| **CARD_BORDER** | 0xFF3A5478 | Card border |
| **SECTION_BG** | 0x3A182A41 | Section background |
| **SECTION_BORDER** | 0xFF4A6B8A | Section border |
| **TAB_INACTIVE** | 0xFF34556B | Inactive tab |

**Status Color Mapping:**
- \POSITIVE\ → EMERALD
- \WARNING\ → AMBER
- \NEGATIVE\ → ROSE
- \NEUTRAL\ → TEXT_MUTED

---

## Language & Localization

### Current Implementation

- All UI text via **Minecraft lang files** (\src/main/resources/assets/resourceobserver/lang/en_us.json\)
- Translation keys follow pattern: \screen.resourceobserver.[page].[component].[item]\

### Key Translation Keys

| Key Prefix | Examples |
|-----------|----------|
| \screen.resourceobserver.overview.*\ | KPI labels, filter names, chart legends |
| \screen.resourceobserver.storage.*\ | Node list, usage summary, item columns |
| \screen.resourceobserver.crafting.*\ | CPU KPIs, jobs list, craftables |
| \screen.resourceobserver.power.*\ | Input/Output rates, consumer names |
| \screen.resourceobserver.common.*\ | Generic buttons (OK, Cancel, Close) |

**Retrieval in Code:**
\\\java
String text = tr("screen.resourceobserver.overview.section.chart");
// or with placeholders:
String text = tr("screen.resourceobserver.overview.watchlist.net", netPerMinute);
\\\

---

## Performance Optimizations

### Incremental Updates

To avoid full-page rebuilds on every data refresh:

1. **Structural Key Comparison**
   - Compute hash of immutable structure (item IDs, group keys, table order)
   - If hash unchanged, only update cell text/colors in-place

2. **Tagged View Lookup**
   - KPI cards, table sections tagged for quick reference
   - Use \indViewWithTag()\ instead of traversing tree

3. **Cell Reference Maps**
   - Store TextView[] arrays mapped by item ID
   - On refresh: iterate map, call \setText()\ and \setTextColor()\ directly

4. **Buffer Countdown Ticker**
   - Separate 1-second timer from data refresh timer
   - Updates EMA-smoothed burn rate + buffer time display without network round-trip

### Hover & Animation State

- \preserveState = true\ on ScrollView: avoids destroying child views
- Hover listeners reattached only after full rebuild
- Animations (pulse, fade) preserved via local state, not view state

---

## Next Steps for Web Dashboard Integration

### Mapping ModernUI to React

1. **Pages** → React components (OverviewPage.tsx, StoragePage.tsx, PowerPage.tsx)
2. **ViewModels** → Redux state or Zustand stores
3. **Dialogs** → Modal components or Headless UI
4. **Charts** → Recharts or D3.js
5. **Refresh loop** → React Query (useQuery with pollingInterval)

### Data Structures to Mirror

- **OverviewViewModel** (KPI + Chart + Table + Watchlist)
- **StorageNetworkViewModel** (KPI + Nodes + Items + Crafting)
- **PowerNetworkViewModel** (KPI + DonutChart + Devices + Debug)
- **UiActionType** (all 11 actions must be mirrored)
- **ChartWindow**, **ChartScope**, **ChartPage**, **LineMode**, **SmoothingMode**

### Key Differences

- **No right-click menus** in web: Use context menus or dedicated buttons
- **No keyboard shortcuts** (Escape to close): Use modal backdrop clicks
- **Responsive grid** instead of fixed-width sections
- **REST API** instead of Minecraft packet protocol
- **Web language support** (i18next) instead of Minecraft lang files

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025 | Initial comprehensive documentation |

---

**Document Generated:** 2025 | **Last Updated:** 2025

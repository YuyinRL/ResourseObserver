# Storage Network Page — UI Replica Spec

## Goal
Implement the second page of the Resource Terminal GUI: **Storage Network**.
Players switch between Overview and Storage Network via tab navigation in the chrome bar.

## Layout Structure
The page uses a **single-column scrollable layout** with four sections:

1. `Header` (reused from Overview — same observer status card)
2. `Storage KPI Summary` (3-card row: total items, total types, fill rate)
3. `Node List` (horizontal scrollable list of storage nodes / bindings)
4. `Item Table` (filtered item list with alert bars and global comparison)

Below the item table, a compact `Usage Summary Bar` shows category distribution.

## Tab Navigation
- Two tabs in the chrome bar: **Overview** | **Storage Network**
- Active tab uses `TAB_ACTIVE` color; inactive uses `TAB_INACTIVE`
- Tab click switches page and resets scroll position
- All page-specific state (selected node, alert filter) persists across tab switches

## Section Details

### Storage KPI Summary
Three metric cards showing:
- **Total Items**: sum of all item amounts across selected node (or all nodes)
- **Total Types**: count of distinct item types
- **Fill Rate**: capacity utilization percentage (from CellCapacityMetrics)

### Node List
- Horizontal row of node cards, each representing one `BindingEntry`
- Card shows: icon, display name, item count, capacity progress bar
- Click a card to select that node → item table filters to that node's items
- "All Nodes" pseudo-card at the start to show unfiltered view
- Selected card highlighted with `TAB_ACTIVE` border

### Item Table
- **Columns**: Icon | Item Name | Local Amount | Global Amount | Delta | Alert Bar
- **Local Amount**: item amount within the selected node
- **Global Amount**: total amount across all nodes (always shown for comparison)
- **Delta**: net change (positive green, negative red, zero gray)
- **Alert Bar**: 3px color-coded bar per row:
  - `Green (EMERALD)`: stock sufficient
  - `Yellow (AMBER)`: stock covers < 30s of consumption (|delta| × 20 ticks conversion)
  - `Red (ROSE)`: stock ≤ 0, or storage capacity ≥ 95%
- **Alert Filter Toggle**: button to show only yellow/red items

### Usage Summary Bar
- Horizontal segmented bar at the bottom of the item table
- Segments represent item groups (Raw / Intermediate / Finished / Other)
- Each segment width proportional to that group's share of total stored amount
- Label format: "Raw 40% · Finished 35% · Other 25%"

## Interaction Rules
- Node selection persists during data refresh
- Alert filter toggle persists during data refresh
- Item table supports the same sorting as Overview (by clicking column headers)
- Tab switch does NOT reset Overview page state (chart selection, group collapse, etc.)

## Visual Semantics
- Same dark terminal background as Overview
- Node cards use `CARD_BG` / `CARD_BORDER`
- Alert bar colors: `EMERALD` (green), `AMBER` (yellow), `ROSE` (red)
- Selected node: `TAB_ACTIVE` border color

## Translation Keys (screen.resourceobserver.storage.*)
- `tab.overview` / `tab.storage_network` — tab labels
- `section.nodes` / `section.items` / `section.usage` — section titles
- `kpi.total_items` / `kpi.total_types` / `kpi.fill_rate` — KPI labels
- `col.item` / `col.local` / `col.global` / `col.delta` / `col.alert` — column headers
- `node.all` — "All Nodes" pseudo-card label
- `alert.sufficient` / `alert.low` / `alert.critical` — alert tooltips
- `filter.alert_only` / `filter.all` — filter toggle labels
- `items_in_node` — "%s items in this node"
- `no_items` — "No items match current filter."

## Acceptance Checklist
- [ ] Tab navigation visible and functional in chrome bar
- [ ] Node list renders all bindings as clickable cards
- [ ] Node selection filters item table correctly
- [ ] Global amount column always shows cross-node total
- [ ] Alert bars color-coded correctly per threshold
- [ ] Alert filter toggle works
- [ ] Usage summary bar shows correct proportions
- [ ] Dark theme colors match Overview page
- [ ] All strings use translation keys
- [ ] Page scroll works independently from Overview


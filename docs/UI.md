# Resource Terminal GUI — Enhanced UI Specification

> Applies to the Resource Terminal item's in-game GUI.  
> Builds on top of the MVP spec (Section 9 of `MVP_TECH_SPEC.md`).

---

## 1. Global: Navigation & Filtering

### 1.1 Search Bar
- A real-time search input pinned to the top of the sidebar.
- Filters all visible lists (items, observers, devices) as the player types.
- Supports fuzzy matching on item names and production-line names.

### 1.2 Sidebar Sub-Groups
Expand the existing three top-level tabs into collapsible two-level navigation:

| Top-Level Tab | Sub-Groups |
|---|---|
| **Overview** | Raw Materials · Intermediates · Finished Products |
| **Storage Network** | Raw Storage · Line Buffers · Output Storage |
| **Power Network** | Mining Devices · Assembly Devices · Logistics Devices |

Players can collapse groups they don't care about to reduce clutter.

---

## 2. Overview Page

### 2.1 Auto-Grouped & Collapsible Item List
- Items are grouped by production-chain tier: **Raw → Intermediate → Finished**.
- Common low-value intermediates (screws, gears, etc.) collapse into a "Common Parts" group by default; expandable on click.

### 2.2 Watchlist (Pinned Items)
- Players can mark any item as "Watched".
- Watched items are pinned to the top of the list, always visible regardless of grouping or filters.

### 2.3 Net Change Column
- Add a **Net Change Rate** column to every item row.
- Color-coded: **green** = surplus (production > consumption), **red** = deficit (production < consumption), **gray** = balanced.

### 2.4 Customizable Columns
- Players can show/hide data columns (e.g., production rate, consumption rate, stock count) via a column toggle menu.

---

## 3. Storage Network Page

### 3.1 Node ↔ Item Linked Filtering
- Clicking a storage node (chest / ME drive / etc.) filters the item list to show only items in that node.
- Each filtered item row also displays its **global total stock** for comparison.

### 3.2 Inventory Alert Bars
- Each item shows a color-coded status bar based on remaining supply duration:
  - **Green**: stock sufficient
  - **Yellow**: stock < 30 s of consumption
  - **Red**: critically low **or** storage full (overflow)
- A one-click filter shows only items in abnormal (yellow/red) state.

### 3.3 Storage Usage Summary
- A summary widget showing storage space usage grouped by item category (e.g., "Raw 40% · Finished 35% · Other 25%").

---

## 4. Power Network Page

### 4.1 Power Consumption by Production Line
- Group energy usage by the **item being produced**, not just by device.
- Display format: _"Green Motor line — total 120 FE/t"_.

### 4.2 Load Distribution Chart
- A pie/donut chart breaking down energy consumption by device category (Mining / Assembly / Logistics).

### 4.3 Overload Alerts
- Devices or lines exceeding capacity are highlighted in red.
- Alert includes the associated production item and estimated throughput loss (e.g., _"Green Motor line: −20% throughput due to power shortage"_).

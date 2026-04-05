# UI Structure — Component-by-Component Breakdown

This document describes every visual section of the UI, what data it shows, and how it's built. Use this to understand the full interface layout.

---

## 1. App Shell (`App.tsx`)

The root layout. Full-screen dark theme (`bg-slate-950`), flex row on desktop, flex column on mobile.

### 1.1 Top Header Bar (sticky)

```
┌──────────────────────────────────────────────────────────────────┐
│ [Page Icon]  OPERATIONS DASHBOARD  [LINK ESTABLISHED]            │
│              Global monitoring of all linked...                   │
│                                    [Bell] [Settings] [🌐 EN/中文] │
└──────────────────────────────────────────────────────────────────┘
```

- **Left**: Page icon (changes per tab: Activity/Database/Zap), page title + subtitle, green "Link Established" badge
- **Right**: Bell button, Settings button, Language toggle button (Globe icon + text)
- Title/subtitle change dynamically based on `activeTab`

### 1.2 Footer Status Bar

```
┌──────────────────────────────────────────────────────────────────┐
│ ENCRYPTED DATA LINK [AES-256]  ·  SUB-NETWORK SECTOR 7G  ·  🕐  │
│                                              🟢 SYSTEM NOMINAL   │
└──────────────────────────────────────────────────────────────────┘
```

Static status display. Green dot + "System Nominal" on the right.

---

## 2. Sidebar (`Sidebar.tsx`)

Left panel, fixed 256px wide on desktop, full width on mobile. Sticky, full height.

```
┌──────────────────┐
│ [Factory Icon]   │
│ ResourceObserver │
│ Resource Terminal│
├──────────────────┤
│ 🔍 Search items  │
├──────────────────┤
│ ● Overview       │  ← activeTab navigation
│   Storage Network│
│   Power Network  │
├──────────────────┤
│ System Time 24:41│
│ Net Health  99.8%│
└──────────────────┘
```

- **Logo area**: Factory icon + "ResourceObserver" + "Resource Terminal"
- **Search bar**: Text input with Search icon, filters items in Overview page
- **Nav items**: 3 buttons (Overview / Storage Network / Power Network), active one highlighted with cyan
- **Bottom info**: System Time (static) + Net Health 99.8% (static)

---

## 3. Overview Page (`Overview.tsx`)

Default page. Shows overall resource production/consumption metrics.

### 3.1 KPI Grid (4 cards, responsive grid)

```
┌────────────────┐ ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│ NET PRODUCTION │ │ NET CONSUMPTION│ │ STORAGE STATUS │ │ SYSTEM         │
│ 14,285 /min    │ │ 12,940 /min    │ │ 74.2 %         │ │ EFFICIENCY     │
│ ↑+5.2% vs last │ │ ↓-2.1% vs last │ │ 85.2M / 114.8M │ │ 98.5% Active   │
└────────────────┘ └────────────────┘ └────────────────┘ └────────────────┘
```

Each card: label, big value, change indicator (green up / red down), icon with colored background glow.

### 3.2 Resource Flow Chart (AreaChart)

```
┌──────────────────────────────────────────────────────────────────┐
│ GLOBAL RESOURCE FLOW                    🔵 Production  🟡 Consumption │
│ Combined production vs consumption rates...                       │
│                                                                    │
│  ████████████████████████████████████████  (area chart, 24h)      │
│                                                                    │
└──────────────────────────────────────────────────────────────────┘
```

- **Default**: Shows global aggregated flow data (24 data points, one per hour)
- **Item selected**: Shows flow data for that specific item, with a "Reset to Global" button
- Two overlapping areas: cyan (production) and amber (consumption) with gradient fills
- Recharts `<AreaChart>` with `<ResponsiveContainer>`

### 3.3 Watchlist Section

Only visible when items are starred and no search query active.

```
⭐ WATCHLIST
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ [Icon] Iron Ore ⭐│ │ [Icon] Cu Wire ⭐│ │ [Icon] Microchip⭐│
│ Net: +50 /min    │ │ Net: -200 /min   │ │ Net: 0 /min      │
│ Stock: 45k/50k ▓▓│ │ Stock: 12k/20k ▓ │ │ Stock: 2k/5k ▓   │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

- Grid of 1-3 columns. Clickable to select item → updates chart
- Shows: icon, name, star toggle, net change (/min), stock bar

### 3.4 Item Circulation Table (grouped, collapsible)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ ↔ ITEM CIRCULATION                                    [8 items active]  │
├──────────────────────────────────────────────────────────────────────────┤
│   │ Resource Node  │ Production │ Consumption │ Net Change │ Inventory  │
├───┼────────────────┼────────────┼─────────────┼────────────┼────────────┤
│ ▼ RAW (2)                                                               │
│ ⭐ [Pickaxe] Iron Ore #iron-ore │ 1,200     │ 1,150      │ +50/m ↑   │ 45k/50k ▓▓│
│   [Pickaxe] Copper Ore          │ 800       │ 750        │ +50/m ↑   │ 32k/40k ▓ │
│ ▼ INTERMEDIATE (2)                                                      │
│ ⭐ [Cpu] Copper Wire            │ 2,400     │ 2,600      │ -200/m ↓  │ 12k/20k ▓ │
│   [Layers] Steel Plate          │ 450       │ 400        │ +50/m ↑   │ 8.5k/10k ▓│
│ ▼ FINISHED (2)                                                          │
│ ⭐ [Activity] Microchip         │ 120       │ 120        │ 0/m       │ 2k/5k ▓   │
│   [Zap] Energy Cell             │ 60        │ 85         │ -25/m ↓   │ 450/1k ▓  │
│ ▶ COMMON PARTS (2)              collapsed                               │
└──────────────────────────────────────────────────────────────────────────┘
```

- Category groups: Raw / Intermediate / Finished / Common Parts
- Each row: star toggle, icon, name + ID, production (cyan), consumption (amber), net change (green/red), inventory progress bar
- Rows are clickable → selects item → updates chart
- Categories are collapsible

### Mock Data: 8 Resources

| ID | Name | Category | Produced | Consumed | Stock | Capacity | Icon |
|---|---|---|---|---|---|---|---|
| iron-ore | Iron Ore | Raw | 1,200 | 1,150 | 45,000 | 50,000 | Pickaxe |
| copper-ore | Copper Ore | Raw | 800 | 750 | 32,000 | 40,000 | Pickaxe |
| steel-plate | Steel Plate | Intermediate | 450 | 400 | 8,500 | 10,000 | Layers |
| copper-wire | Copper Wire | Intermediate | 2,400 | 2,600 | 12,000 | 20,000 | Cpu |
| microchip | Microchip | Finished | 120 | 120 | 2,000 | 5,000 | ActivitySquare |
| energy-cell | Energy Cell | Finished | 60 | 85 | 450 | 1,000 | Zap |
| screw | Screw | Common Parts | 15,000 | 14,500 | 80,000 | 100,000 | Hexagon |
| gear | Gear | Common Parts | 5,000 | 4,800 | 25,000 | 30,000 | Hexagon |

---

## 4. Storage Network Page (`StorageNetwork.tsx`)

12-column grid layout. Left panel (4 cols) + Right panel (8 cols).

### 4.1 Left Panel: Storage Nodes List

```
┌──────────────────────┐
│ 🗄 STORAGE UNITS  [Filter] │
│                             │
│ ┌─────────────────────┐    │
│ │ Hub Alpha            │    │
│ │ Central ME Hub       │    │
│ │ 852.0k / 1000.0k    │    │
│ │ ▓▓▓▓▓▓▓▓░░ 85.2% │ [Healthy] │
│ └─────────────────────┘    │
│ ┌─────────────────────┐    │
│ │ Outpost Beta         │    │
│ │ Local Buffer         │    │
│ │ 45.0k / 250.0k      │    │
│ │ ▓▓░░░░░░░░ 18.0% │ [Healthy] │
│ └─────────────────────┘    │
│ ┌─────────────────────┐    │
│ │ Assembly Line 3      │    │
│ │ Output Buffer        │    │
│ │ 48.5k / 50.0k        │    │
│ │ ▓▓▓▓▓▓▓▓▓░ 97.0% │ [Alert]  │  ← red badge
│ └─────────────────────┘    │
│ ┌─────────────────────┐    │
│ │ Sector 4 Silo        │    │
│ │ Mass Storage         │    │
│ │ 1200.0k / 5000.0k    │    │
│ │ ▓▓░░░░░░░░ 24.0% │ [Healthy] │
│ └─────────────────────┘    │
└────────────────────────────┘
```

4 storage nodes. Clickable → filters right panel to show only items in that node.

### 4.2 Left Panel: Global Usage Summary (Donut Chart)

```
┌────────────────────────┐
│ ▧ GLOBAL USAGE SUMMARY │
│                        │
│       ╭──────╮         │
│    ╭──┤ 74.2%├──╮     │
│    │  │Total │  │     │
│    ╰──┤Used  ├──╯     │
│       ╰──────╯         │
│                        │
│ 🟡 Raw Materials  45%  │
│ 🔵 Intermediates  35%  │
│ 🟢 Finished       15%  │
│ ⚫ Other            5%  │
└────────────────────────┘
```

Recharts `<PieChart>` donut with center label showing 74.2% total used.

### 4.3 Right Panel: Global Inventory Health Table

```
┌──────────────────────────────────────────────────────────────────┐
│ 📦 GLOBAL INVENTORY HEALTH                                       │
│     Real-time status of items in linked networks                 │
│                                    [All Status] [Deficits Only]  │
├──────────────────────────────────────────────────────────────────┤
│ 🟢 Stable   🟡 Warning (<30s)   🔴 Critical          Sync: 0.4s │
├──────────────────────────────────────────────────────────────────┤
│ Item Identity │ Global Stock │ Burn Rate │ Estimated Buffer      │
├───────────────┼──────────────┼───────────┼──────────────────────┤
│ [⛏] Iron Ore  │ 45,000       │ -1150/m   │ 39m 8s ▓▓▓▓▓▓▓ 🟢  │
│ [⛏] Copper Ore│ 32,000       │ -750/m    │ 42m 40s ▓▓▓▓▓▓▓ 🟢  │
│ [≡] Steel Pl  │ 8,500        │ -400/m    │ 21m 15s ▓▓▓▓▓░░ 🟢  │
│ [⊡] Microchip │ 2,000        │ -120/m    │ 16m 40s ▓▓▓▓░░░ 🟢  │
│ [⚡] Energy C  │ 450          │ -85/m     │ 5m 18s  ▓▓░░░░░ 🟡  │
├──────────────────────────────────────────────────────────────────┤
│ ⚠ 3 Items below critical buffer threshold    [Recalculate...]   │
└──────────────────────────────────────────────────────────────────┘
```

- Filter toggle: "All Status" / "Deficits Only" (items with buffer < 30s)
- Color-coded buffer time bars (green > amber > red)
- Node selection filters the item list

### Mock Data: 4 Storage Nodes

| ID | Name | Type | Capacity | Used | Status | Items |
|---|---|---|---|---|---|---|
| hub-alpha | Hub Alpha - Central Storage | Central ME Hub | 1,000,000 | 852,000 | Healthy | iron-ore, copper-ore, steel-plate, screw, gear |
| outpost-beta | Outpost Beta - Mining Buffers | Local Buffer | 250,000 | 45,000 | Healthy | iron-ore, copper-ore |
| line-3-output | Assembly Line 3 - Finished | Output Buffer | 50,000 | 48,500 | Alert | microchip, energy-cell |
| sector-4-silo | Sector 4 - Resource Silo | Mass Storage | 5,000,000 | 1,200,000 | Healthy | iron-ore |

---

## 5. Power Network Page (`PowerNetwork.tsx`)

### 5.1 Top Row: 12-column grid (8 + 4)

#### Grid Load Dynamics Chart (8 cols)

```
┌──────────────────────────────────────────────────────┐
│ ⚡ GRID LOAD DYNAMICS                                 │
│    Real-time power generation vs consumption (24h)   │
│                                  Generation: 18.0 GW │
│                                  Peak Load: 17.2 GW  │
│                                                      │
│  ████████████████████████████████████  (area chart)  │
│  ---- generation line (red dashed) ----              │
│                                                      │
└──────────────────────────────────────────────────────┘
```

- Blue area = actual usage (random 12-17 GW range)
- Red dashed step line = generation capacity (18 GW flat)

#### Load Distribution Donut (4 cols)

```
┌──────────────────────┐
│ ▧ LOAD DISTRIBUTION  │
│   Energy split by... │
│                      │
│      ╭──────╮       │
│   ╭──┤ 5.3GW├──╮   │
│   │  │Demand│  │   │
│   ╰──┤      ├──╯   │
│      ╰──────╯       │
│                      │
│ 🟡 Mining    15% Use │
│ 🔵 Assembly  65% Use │
│ 🟢 Logistics 20% Use │
└──────────────────────┘
```

### 5.2 Production Line Consumption Table

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ ⚡ PRODUCTION LINE CONSUMPTION              [⚠ 1 Overload Detected]         │
│    Energy usage grouped by item production chains                            │
├──────────────────────────────────────────────────────────────────────────────┤
│ Production Line │ Assigned Item │ Devices │ Consumption │ Capacity │ Status  │
├─────────────────┼───────────────┼─────────┼─────────────┼──────────┼─────────┤
│ Iron Chain      │ Iron Ore      │ 12 units│ 450 FE/t    │ 90%      │ 🟢 Normal│
│ Copper Chain    │ Copper Ore    │ 8 units │ 320 FE/t    │ 80%      │ 🟢 Normal│
│ Steel Process   │ Steel Plate   │ 24 units│ 1200 FE/t   │ 80%      │ 🟢 Normal│
│ Electronics Asm │ Microchip     │ 48 units│ 2500 FE/t   │ 96.2%   │ 🔴 Overld│  ← red row
│ Logistics Hub   │ Transport Hub │ 120 unit│ 800 FE/t    │ 80%      │ 🟢 Normal│
├──────────────────────────────────────────────────────────────────────────────┤
│ 🟢 Main Grid: Operational  · 🔵 Reserves: 94.2%  · Optimize Load Balancers │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 5.3 Overload Alert Card

```
┌─────────────────────────────────────────────────────────────────┐
│ [⚠] CRITICAL OVERLOAD: ELECTRONICS ASSEMBLY      [Reroute Power]│
│     Throughput Loss Detected: −20% due to power shortage Phase A│
└─────────────────────────────────────────────────────────────────┘
```

Red-tinted card with rose border. "Reroute Power" button.

### Mock Data: 5 Power Lines

| ID | Name | Item | Consumption | Capacity | Status | Devices | Category |
|---|---|---|---|---|---|---|---|
| iron-line | Iron Production Chain | Iron Ore | 450 | 500 | Normal | 12 | Mining |
| copper-line | Copper Production Chain | Copper Ore | 320 | 400 | Normal | 8 | Mining |
| steel-line | Steel Processing Line | Steel Plate | 1,200 | 1,500 | Normal | 24 | Assembly |
| circuit-line | Electronics Assembly | Microchip | 2,500 | 2,600 | Overload | 48 | Assembly |
| logistics-hub | Main Logistics Network | Transport Hub | 800 | 1,000 | Normal | 120 | Logistics |

---

## 6. Shared UI Patterns

### Card Component
Every page defines a local `Card` component (not from shadcn/ui):
```tsx
<div className="bg-slate-900/80 border border-slate-800 rounded-xl backdrop-blur-sm shadow-xl">
  {children}
</div>
```

### ProgressBar Component
Each page defines a local `ProgressBar`:
```tsx
<div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
  <div className={colorClass} style={{ width: `${percentage}%` }} />
</div>
```

### Color Scheme
- **Background**: `slate-950` (nearly black)
- **Cards**: `slate-900/80` with `slate-800` borders
- **Primary accent**: `cyan-400/500` (active items, production data)
- **Secondary accent**: `amber-400/500` (consumption data, warnings)
- **Positive**: `emerald-400/500` (healthy, increasing)
- **Negative**: `rose-400/500` (alerts, decreasing, overload)
- **Neutral text**: `slate-300` (primary), `slate-500` (secondary), `slate-600` (muted)

### Typography Patterns
- Section headers: `text-sm font-bold uppercase tracking-widest text-slate-300`
- Data values: `font-mono font-bold text-white`
- Labels: `text-[10px] font-bold uppercase tracking-widest text-slate-500`
- IDs: `text-[10px] font-mono text-slate-500`

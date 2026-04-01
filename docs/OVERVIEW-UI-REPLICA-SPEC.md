# Overview UI Replica Spec (Web -> Minecraft)

## Goal
Replicate the external Web dashboard `Overview` page inside the NeoForge mod GUI with high visual fidelity.
This document is the source of truth for layout, styling, and interaction constraints.

## Fixed Areas
The screen is composed of five fixed sections in this order:
1. `Header`
2. `KPI Grid`
3. `Resource Flow Area Chart`
4. `Watchlist`
5. `Item Circulation Table`

All sections live inside a centered root panel.

## Layout Rules
- Root panel is always centered in viewport.
- Root panel supports responsive scaling with `S/M/L` user size mode.
- Section vertical order must not change.
- Header has fixed height.
- KPI section is a 4-card row.
- Chart section sits below KPI and spans full content width.
- Watchlist section sits below chart.
- Table section occupies remaining height and must support pagination.
- If side controls cannot fit on right side, move them to left side.

## Visual Semantics
- Dark terminal background.
- Cyan = production / active.
- Amber = consumption / warning.
- Emerald = healthy / positive.
- Rose = alert / negative.
- Table grouping order is fixed:
1. Raw
2. Intermediate
3. Finished
4. Common Parts

## Text Rules
- English-only for this phase.
- No garbled text imported from external docs.
- All user-visible strings must use translation keys.

## Interaction Rules
- Watchlist card click selects item and switches chart to item-specific series.
- Reset control returns chart to global series.
- Group headers in table toggle collapse/expand.
- Table row click selects item for chart focus.
- Refresh payload must not reset:
- selected chart item
- expanded/collapsed group states
- current page index (except when clamped by shorter data)

## Asset Rules
- Use custom GUI sprites from mod resources.
- Render icons via sprite ids, not hardcoded text glyphs.
- Keep icon usage semantic:
- KPI icon
- status dot
- watchlist icon
- table row icon

## Acceptance Checklist
- Header, KPI, Chart, Watchlist, Table all visible and aligned.
- No overlapping components at small, medium, large viewport.
- `S/M/L` scaling keeps layout readable and in bounds.
- Color semantics match the Web design intent.
- Table grouping and watchlist interactions are stable during refresh.

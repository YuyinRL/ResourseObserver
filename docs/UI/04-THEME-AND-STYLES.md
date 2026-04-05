# Theme & Styling Reference

## CSS Architecture

```
src/styles/index.css          ← Entry point (imports below 3 files in order)
  ├── fonts.css               ← Font-face declarations (currently empty)
  ├── tailwind.css            ← Tailwind CSS 4 config
  └── theme.css               ← CSS custom properties + base layer styles
```

### Tailwind Config (`tailwind.css`)
```css
@import 'tailwindcss' source(none);
@source '../**/*.{js,ts,jsx,tsx}';
@import 'tw-animate-css';
```
- Tailwind CSS 4 (no `tailwind.config.js` — uses CSS-native config)
- Source scanning from `src/` directory
- `tw-animate-css` for animation utilities

---

## Color System

The app uses a **dark-first design**. All main UI colors come directly from Tailwind's `slate` palette, not CSS variables. The `theme.css` custom properties are for shadcn/ui components but are rarely used in the main pages.

### Primary Palette (actually used in components)

| Role | Color | Tailwind Class | Hex Approx |
|---|---|---|---|
| Page background | Near black | `bg-slate-950` | `#020617` |
| Card background | Dark with transparency | `bg-slate-900/80` | `#0f172a` @ 80% |
| Card border | Dark gray | `border-slate-800` | `#1e293b` |
| Primary text | White | `text-white` | `#ffffff` |
| Secondary text | Light gray | `text-slate-300` | `#cbd5e1` |
| Muted text | Gray | `text-slate-500` | `#64748b` |
| Dimmed text | Dark gray | `text-slate-600` | `#475569` |

### Accent Colors

| Role | Color | Tailwind Class | Usage |
|---|---|---|---|
| **Cyan** (primary accent) | Cyan 400/500 | `text-cyan-400`, `bg-cyan-500/10` | Active nav items, production data, selected rows, primary actions |
| **Amber** (secondary accent) | Amber 400/500 | `text-amber-400`, `bg-amber-500/10` | Consumption data, warnings, watchlist highlight |
| **Emerald** (positive) | Emerald 400/500 | `text-emerald-400`, `bg-emerald-500/10` | Healthy status, increasing values, system nominal |
| **Rose** (negative) | Rose 400/500 | `text-rose-400`, `bg-rose-500/10` | Alerts, overload, decreasing values, critical items |
| **Blue** | Blue 400/500 | `text-blue-400`, `bg-blue-500` | Power usage charts, assembly category, storage capacity bars |

### Glow Effects
Used extensively for accent elements:
```css
shadow-[0_0_15px_rgba(6,182,212,0.15)]   /* cyan glow */
shadow-[0_0_8px_rgba(16,185,129,0.5)]    /* emerald glow (status dots) */
shadow-[0_0_10px_rgba(16,185,129,0.1)]   /* soft emerald */
shadow-[0_0_8px_rgba(244,63,94,0.1)]     /* rose glow (overload badges) */
```

---

## Typography

No custom fonts loaded (fonts.css is empty). Uses system fonts via Tailwind defaults.

### Text Scale Used in Components

| Size | Tailwind | Usage |
|---|---|---|
| 2xl (`1.5rem`) | `text-2xl` | KPI values |
| xl (`1.25rem`) | `text-xl` | Page title |
| lg (`1.125rem`) | `text-lg` | Chart titles |
| sm (`0.875rem`) | `text-sm` | Table text, card names, section headers |
| xs (`0.75rem`) | `text-xs` | Descriptions, secondary info, badges |
| `10px` | `text-[10px]` | Labels, status badges, micro-text, tracking info |
| `9px` | `text-[9px]` | Footer status text, donut center labels |

### Font Weight Patterns
- **Bold** (`font-bold`): Headers, names, values, labels
- **Black** (`font-black`): Page title only
- **Semibold** (`font-semibold`): Subtitles, change indicators
- **Medium** (`font-medium`): Secondary labels

### Monospace
`font-mono` is used for all numeric data values: stock counts, timestamps, IDs, percentages.

### Letter Spacing
`tracking-widest` (0.1em) or `tracking-[0.2em]` for uppercase labels and section headers.  
`tracking-tighter` for subtitles and descriptions.

---

## Layout Patterns

### Responsive Grid
```
Mobile:  1 column (full width)
Tablet:  2 columns (md: breakpoint)
Desktop: 4 columns for KPIs (lg: breakpoint)
         12-column grid for Storage/Power pages
```

### Page Max Width
Content area: `max-w-[1600px] mx-auto`

### Sidebar
- Desktop: `w-64` (256px) fixed, sticky
- Mobile: `w-full`

### Card Padding
- Standard: `p-5` or `p-6`
- Table containers: `p-4` for header/footer, table has its own cell padding

---

## Chart Colors (Recharts)

| Chart | Element | Color |
|---|---|---|
| Overview Area | Production area | `#06b6d4` (cyan-500) with gradient fill |
| Overview Area | Consumption area | `#f59e0b` (amber-500) with gradient fill |
| Power Area | Usage area | `#3b82f6` (blue-500) with gradient fill |
| Power Area | Generation line | `#ef4444` (red-500) dashed |
| Storage Donut | Raw Materials | `#f59e0b` (amber) |
| Storage Donut | Intermediates | `#3b82f6` (blue) |
| Storage Donut | Finished | `#10b981` (emerald) |
| Storage Donut | Other | `#64748b` (slate) |
| Power Donut | Mining | `#f59e0b` (amber) |
| Power Donut | Assembly | `#3b82f6` (blue) |
| Power Donut | Logistics | `#10b981` (emerald) |

Chart tooltip: `bg-[#0f172a]` with `border: 1px solid #1e293b`, `rounded-xl`, `text-xs`.

---

## CSS Custom Properties (`theme.css`)

These are for shadcn/ui compatibility. Light mode values listed first, dark mode in `.dark` class.

### Key Variables
```css
:root {
  --font-size: 16px;
  --radius: 0.625rem;                    /* 10px — border radius base */
  --background: #ffffff;                 /* Light mode only */
  --primary: #030213;
  --destructive: #d4183d;
}

.dark {
  --background: oklch(0.145 0 0);        /* Very dark */
  --foreground: oklch(0.985 0 0);        /* Near white */
  --border: oklch(0.269 0 0);            /* Dark gray */
  --muted-foreground: oklch(0.708 0 0);  /* Gray */
}
```

> Note: The main page components do NOT use these CSS variables. They use Tailwind utility classes directly (`bg-slate-950`, `text-cyan-400`, etc.). The CSS variables are only consumed by the shadcn/ui primitives in `src/app/components/ui/`.

---

## Animation

- Page content entrance: `animate-in fade-in slide-in-from-bottom-2 duration-700` (from `tw-animate-css`)
- Overload badge: `animate-pulse`
- Background glows: `group-hover:scale-110 transition-transform duration-500`
- General transitions: `transition-all`, `transition-colors` on hover states

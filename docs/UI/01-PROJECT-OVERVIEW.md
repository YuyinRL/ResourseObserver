# Project Overview — ResourceObserver Data Visualization Dashboard

## Tech Stack

| Category | Technology | Version |
|---|---|---|
| Language | TypeScript | - |
| UI Framework | React | 18.3.1 |
| Build Tool | Vite | 6.3.5 |
| CSS Framework | Tailwind CSS | 4.1.12 |
| UI Components | Radix UI + shadcn/ui | - |
| Charts | Recharts | 2.15.2 |
| Icons | Lucide React | 0.487.0 |
| Routing | React Router | 7.13.0 |
| Animation | Motion (framer-motion) | 12.23.24 |

## Project File Structure

```
src/
├── main.tsx                          # App entry point, wraps <App> with <I18nProvider>
├── app/
│   ├── App.tsx                       # Root layout: Sidebar + Header + Page Router + Footer
│   ├── i18n.tsx                      # i18n context (en/zh), all UI text translations
│   └── components/
│       ├── Sidebar.tsx               # Left sidebar navigation
│       ├── Overview.tsx              # Overview page (default tab)
│       ├── StorageNetwork.tsx        # Storage Network page
│       ├── PowerNetwork.tsx          # Power Network page
│       ├── figma/
│       │   └── ImageWithFallback.tsx # Image helper component
│       └── ui/                       # shadcn/ui components (standard, no custom logic)
│           ├── accordion.tsx
│           ├── button.tsx
│           ├── card.tsx
│           ├── ... (40+ standard UI primitives)
│           └── utils.ts
└── styles/
    ├── index.css                     # CSS entry (imports fonts + tailwind + theme)
    ├── tailwind.css                  # Tailwind config: @import 'tailwindcss', source scanning
    ├── theme.css                     # CSS custom properties (colors, radius, typography)
    └── fonts.css                     # Font declarations (currently empty)
```

## Entry Point Flow

```
main.tsx
  └─ <I18nProvider>           ← i18n context (locale state + translation function)
       └─ <App>               ← Root component
            ├─ <Sidebar>      ← Navigation (left panel)
            └─ <main>         ← Content area
                 ├─ <header>  ← Top bar (title, status badges, language toggle, icons)
                 ├─ <div>     ← Page content (routed by activeTab state)
                 │    ├─ <Overview>        (activeTab === 'overview')
                 │    ├─ <StorageNetwork>  (activeTab === 'storage')
                 │    └─ <PowerNetwork>    (activeTab === 'power')
                 └─ <footer>  ← Status bar (encrypted link, date, system nominal)
```

## Routing

No URL-based routing. Navigation is handled by a `useState('overview')` in `App.tsx`, toggled via `Sidebar` button clicks. Three tabs:

| Tab ID | Component | Description |
|---|---|---|
| `overview` | `<Overview>` | KPIs, resource flow chart, watchlist, item circulation table |
| `storage` | `<StorageNetwork>` | Storage nodes, usage donut chart, inventory health table |
| `power` | `<PowerNetwork>` | Power grid chart, load distribution, production line table |

## Internationalization (i18n)

- Context-based: `I18nProvider` wraps the app, components use `useI18n()` hook
- Two locales: `en` (English) and `zh` (Chinese)
- Toggle button in the top header bar (Globe icon)
- All UI text is in `src/app/i18n.tsx` as a flat key-value translations object
- Translation keys follow dot notation: `'section.subsection.label'`

## Data

All data is **mock/static** — defined inline in each component as constants (e.g. `INITIAL_RESOURCES`, `STORAGE_NODES`, `POWER_LINES`). No API calls or backend connection.

## Key Dependencies (beyond standard UI)

| Package | Usage |
|---|---|
| `recharts` | AreaChart, PieChart, LineChart with responsive containers |
| `lucide-react` | All icons (Activity, Database, Zap, Factory, Pickaxe, etc.) |
| `tailwind-merge` + `clsx` + `class-variance-authority` | Conditional className composition in shadcn/ui |
| `motion` | Animations (imported but used sparingly) |

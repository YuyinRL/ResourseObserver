/** Standardized dialog dimensions */
export const DIALOG = {
  /** Compact dialogs (rename, quick input) */
  SMALL:  { w: 340, h: 300 },
  /** KPI detail dialogs */
  MEDIUM: { w: 440, h: 380 },
  /** Extended detail dialogs (power external storage, etc.) */
  LARGE:  { w: 560, h: 460 },
  /** Full workflows (crafting order, tree view) */
  XL:     { w: 720, h: 560 },
} as const;

import React, { useState } from 'react';
import { Box } from 'lucide-react';
import { api } from '../../lib/api';
import type { CraftingItemNode, CraftingTier } from './types';

interface NodeCardProps {
  node: CraftingItemNode;
  depth: number;
  onClick?: (node: CraftingItemNode) => void;
}

const TIER_STYLES: Record<CraftingTier, { ring: string; bg: string; chip: string; glow: string; label: string }> = {
  common: {
    ring: 'border-slate-300/30',
    bg: 'bg-slate-100/[0.04]',
    chip: 'bg-slate-200/10 text-slate-300 border-slate-300/20',
    glow: 'shadow-[0_2px_10px_rgba(148,163,184,0.10)]',
    label: 'COMMON',
  },
  uncommon: {
    ring: 'border-emerald-300/40',
    bg: 'bg-emerald-200/[0.05]',
    chip: 'bg-emerald-300/10 text-emerald-300 border-emerald-300/30',
    glow: 'shadow-[0_2px_10px_rgba(110,231,183,0.10)]',
    label: 'UNCOMMON',
  },
  rare: {
    ring: 'border-sky-300/40',
    bg: 'bg-sky-200/[0.05]',
    chip: 'bg-sky-300/10 text-sky-300 border-sky-300/30',
    glow: 'shadow-[0_2px_10px_rgba(125,211,252,0.12)]',
    label: 'RARE',
  },
  legendary: {
    ring: 'border-amber-300/50',
    bg: 'bg-amber-200/[0.06]',
    chip: 'bg-amber-300/10 text-amber-300 border-amber-300/30',
    glow: 'shadow-[0_2px_12px_rgba(252,211,77,0.15)]',
    label: 'LEGENDARY',
  },
};

/**
 * 单个物品卡片：图标 + 名称 + ID + 数量 + 稀有度 + 层级
 */
export function NodeCard({ node, depth, onClick }: NodeCardProps) {
  const tier = TIER_STYLES[node.tier];
  const url = node.icon || api.iconUrl(node.itemId);
  const [iconFailed, setIconFailed] = useState(false);

  return (
    <button
      type="button"
      onClick={() => onClick?.(node)}
      className={[
        'group relative flex w-[220px] items-center gap-3 rounded-xl border px-3 py-2.5 text-left',
        'backdrop-blur-sm transition-all duration-200 ease-out',
        'hover:-translate-y-0.5 hover:shadow-[0_8px_24px_rgba(15,23,42,0.45)]',
        tier.ring,
        tier.bg,
        tier.glow,
      ].join(' ')}
    >
      {/* 像素风物品图标区域 */}
      <div
        className={[
          'flex h-12 w-12 shrink-0 items-center justify-center rounded-lg border bg-slate-950/80 p-1.5',
          'transition-transform duration-200 group-hover:scale-105',
          tier.ring,
        ].join(' ')}
        style={{
          backgroundImage:
            'repeating-linear-gradient(45deg, rgba(255,255,255,0.02) 0 2px, transparent 2px 4px)',
        }}
      >
        {url && !iconFailed ? (
          <img
            src={url}
            alt={node.itemId}
            className="h-full w-full object-contain"
            style={{ imageRendering: 'pixelated' }}
            onError={() => setIconFailed(true)}
          />
        ) : (
          <Box className="text-slate-400" size={22} />
        )}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-baseline justify-between gap-2">
          <p className="truncate text-sm font-bold text-white" title={node.name}>
            {node.name}
          </p>
          <span className="shrink-0 font-mono text-sm font-bold text-cyan-300">×{node.count}</span>
        </div>
        <p className="truncate font-mono text-[10px] text-slate-500" title={node.itemId}>
          {node.itemId}
        </p>
        <div className="mt-1 flex items-center gap-1.5">
          <span className={`rounded-md border px-1.5 py-0.5 text-[9px] font-bold tracking-[0.15em] ${tier.chip}`}>
            {tier.label}
          </span>
          <span className="rounded-md border border-slate-700/60 bg-slate-900/60 px-1.5 py-0.5 font-mono text-[9px] text-slate-400">
            L{depth}
          </span>
        </div>
      </div>
    </button>
  );
}

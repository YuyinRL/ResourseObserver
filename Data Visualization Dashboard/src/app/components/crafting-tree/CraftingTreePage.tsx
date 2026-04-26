import React, { useState } from 'react';
import { GitBranch, Info } from 'lucide-react';
import { CraftingTree } from './CraftingTree';
import type { CraftingItemNode } from './types';

/**
 * Demo 数据：钻石镐合成树。
 * 真实接入时，把 `root` 替换为后端返回的合成树即可。
 */
const DEMO_ROOT: CraftingItemNode = {
  id: 'root',
  name: '钻石镐',
  itemId: 'minecraft:diamond_pickaxe',
  count: 1,
  tier: 'legendary',
  children: [
    {
      id: 'd-1',
      name: '钻石',
      itemId: 'minecraft:diamond',
      count: 3,
      tier: 'rare',
      children: [
        {
          id: 'do-1',
          name: '钻石矿石',
          itemId: 'minecraft:diamond_ore',
          count: 3,
          tier: 'rare',
        },
      ],
    },
    {
      id: 's-1',
      name: '木棍',
      itemId: 'minecraft:stick',
      count: 2,
      tier: 'common',
      children: [
        {
          id: 'p-1',
          name: '橡木木板',
          itemId: 'minecraft:oak_planks',
          count: 1,
          tier: 'common',
          children: [
            {
              id: 'l-1',
              name: '橡木原木',
              itemId: 'minecraft:oak_log',
              count: 1,
              tier: 'uncommon',
            },
          ],
        },
      ],
    },
  ],
};

interface CraftingTreePageProps {
  root?: CraftingItemNode;
}

export function CraftingTreePage({ root = DEMO_ROOT }: CraftingTreePageProps) {
  const [selected, setSelected] = useState<CraftingItemNode | null>(null);

  return (
    <section className="space-y-4">
      <header className="flex items-center justify-between gap-4 rounded-xl border border-slate-800/80 bg-slate-900/40 px-5 py-3">
        <div className="flex items-center gap-3">
          <div className="rounded-lg border border-cyan-500/30 bg-cyan-500/10 p-2 text-cyan-300">
            <GitBranch size={18} />
          </div>
          <div>
            <h3 className="text-sm font-bold uppercase tracking-[0.18em] text-white">Crafting Tree</h3>
            <p className="text-[10px] font-semibold uppercase tracking-[0.15em] text-slate-500">
              Final product · Left → Base materials · Right
            </p>
          </div>
        </div>
        <div className="hidden items-center gap-2 rounded-lg border border-slate-800 bg-slate-950/60 px-3 py-1.5 text-[10px] font-mono text-slate-400 md:flex">
          <Info size={12} />
          {selected ? selected.itemId : '— click any node —'}
        </div>
      </header>

      <div className="h-[640px]">
        <CraftingTree
          root={root}
          onSelect={(n) => {
            console.log('[CraftingTree] node clicked', n);
            setSelected(n);
          }}
        />
      </div>
    </section>
  );
}

import React from 'react';
import { CraftingNode } from './CraftingNode';
import type { CraftingItemNode } from './types';

interface CraftingTreeProps {
  root: CraftingItemNode;
  onSelect?: (node: CraftingItemNode) => void;
}

/**
 * 横向合成树容器：最终产物在最左侧，逐层向右展开为底层基础材料。
 * 自身负责横向滚动 —— 父级请用固定高度 + overflow-hidden 包裹，避免整页乱滚。
 */
export function CraftingTree({ root, onSelect }: CraftingTreeProps) {
  return (
    <div
      className="relative w-full overflow-x-auto overflow-y-auto rounded-2xl border border-slate-800/80 bg-slate-950/40 shadow-inner"
      style={{
        // 极淡的科技网格底纹
        backgroundImage:
          'linear-gradient(rgba(148,163,184,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(148,163,184,0.04) 1px, transparent 1px)',
        backgroundSize: '24px 24px',
      }}
    >
      <div className="inline-block min-w-full p-8">
        <CraftingNode node={root} depth={0} onSelect={onSelect} />
      </div>
    </div>
  );
}

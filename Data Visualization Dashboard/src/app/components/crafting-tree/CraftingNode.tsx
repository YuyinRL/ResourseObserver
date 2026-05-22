import React from 'react';
import { NodeCard } from './NodeCard';
import type { CraftingItemNode } from './types';

interface CraftingNodeProps {
  node: CraftingItemNode;
  depth: number;
  onSelect?: (node: CraftingItemNode) => void;
}

/**
 * 递归节点：左侧为当前物品卡片，右侧（如有）为子材料列。
 * 用 flex 横向排布，子节点容器通过左侧 border 与每行的水平 stub 形成树状连接线。
 */
export function CraftingNode({ node, depth, onSelect }: CraftingNodeProps) {
  const hasChildren = !!node.children && node.children.length > 0;

  return (
    <div className="flex items-center">
      <NodeCard node={node} depth={depth} onClick={onSelect} />

      {hasChildren ? (
        <div className="relative ml-6 flex flex-col gap-3 border-l border-slate-700/60 pl-6">
          {/* 父节点 → 子列：水平连接线（位于子列垂直中心） */}
          <span
            aria-hidden
            className="pointer-events-none absolute -left-6 top-1/2 h-px w-6 bg-slate-700/60"
          />
          {node.children!.map((child) => (
            <div key={child.id} className="relative flex items-center">
              {/* 子列 → 单个子节点：水平 stub */}
              <span
                aria-hidden
                className="pointer-events-none absolute -left-6 top-1/2 h-px w-6 bg-slate-700/60"
              />
              <CraftingNode node={child} depth={depth + 1} onSelect={onSelect} />
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}

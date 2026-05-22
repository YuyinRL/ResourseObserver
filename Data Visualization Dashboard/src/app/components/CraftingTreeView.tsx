import React from 'react';
import type { CraftingTreeNode } from '../lib/api';
import { ItemIcon } from './ItemIcon';

interface CraftingTreeViewProps {
  root: CraftingTreeNode | null | undefined;
  progressFraction?: number;
  emptyHint?: string;
}

const MAX_DEPTH_RENDER = 16;

function NodeRow({ node, depth }: { node: CraftingTreeNode; depth: number }) {
  const required = Math.max(0, Math.floor(node.requiredAmount || 0));
  const perExec = Math.max(0, Math.floor(node.perExecOutAmount || 0));
  const times = Math.max(0, Math.floor(node.timesExecuted || 0));
  const isInternal = node.children && node.children.length > 0;
  const ratio = isInternal && perExec > 0 && times > 0 && node.children[0]
    ? `${Math.max(1, Math.floor((node.children[0].requiredAmount || 0) / Math.max(1, times)))}→${perExec} × ${times}`
    : null;

  let color = 'text-white';
  if (node.isMissing) color = 'text-rose-300';
  else if (node.isLoop) color = 'text-amber-300';
  else if (isInternal) color = 'text-cyan-200';

  return (
    <div className="flex items-center gap-2 py-0.5" style={{ paddingLeft: `${depth * 16}px` }}>
      <ItemIcon item={{ id: node.itemId, accent: 'text-slate-400' }} />
      <p className={`min-w-0 flex-1 truncate text-[11px] ${color}`} title={node.displayName || node.itemId}>
        {node.displayName || node.itemId}
      </p>
      <p className={`font-mono text-[11px] font-bold ${color}`}>×{required.toLocaleString()}</p>
      {ratio ? (
        <p className="font-mono text-[10px] text-slate-500" title="原材料每次输入 → 每次产出 × 执行次数">
          ({ratio})
        </p>
      ) : null}
      {node.truncated ? <span className="font-mono text-[10px] text-amber-400">…+</span> : null}
      {node.isLoop ? <span className="font-mono text-[10px] text-amber-400">loop</span> : null}
    </div>
  );
}

function renderNodes(node: CraftingTreeNode, depth: number, out: React.ReactElement[]) {
  if (depth > MAX_DEPTH_RENDER) return;
  out.push(<NodeRow key={`${depth}-${out.length}-${node.itemId}`} node={node} depth={depth} />);
  if (node.children && node.children.length > 0) {
    for (const child of node.children) renderNodes(child, depth + 1, out);
  }
}

export function CraftingTreeView({ root, progressFraction, emptyHint }: CraftingTreeViewProps) {
  if (!root) {
    return <p className="text-xs text-slate-500">{emptyHint ?? 'Tree unavailable.'}</p>;
  }
  const nodes: React.ReactElement[] = [];
  renderNodes(root, 0, nodes);
  const pct = progressFraction != null ? Math.round(Math.min(1, Math.max(0, progressFraction)) * 100) : null;
  return (
    <div className="space-y-2">
      {pct != null ? (
        <div className="rounded-md border border-slate-800 bg-slate-950/60 p-2">
          <div className="mb-1 flex justify-between text-[10px] font-mono">
            <span className="text-slate-400">Overall progress</span>
            <span className="text-cyan-300">{pct}%</span>
          </div>
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-800">
            <div className="h-full bg-cyan-500" style={{ width: `${pct}%` }} />
          </div>
        </div>
      ) : null}
      <div className="max-h-[60vh] overflow-y-auto rounded-md border border-slate-800 bg-slate-950/60 p-2">
        {nodes}
      </div>
    </div>
  );
}

export default CraftingTreeView;

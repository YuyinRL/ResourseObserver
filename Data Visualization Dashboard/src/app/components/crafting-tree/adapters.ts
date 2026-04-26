import type { CraftingTreeNode } from '../../lib/api';
import type { CraftingItemNode, CraftingTier } from './types';

/**
 * 后端 CraftingTreeNode → 前端 CraftingItemNode。
 * 稀有度按"语义"映射：
 *  - 根节点 → legendary
 *  - 缺料 (isMissing) → rare（高亮提示）
 *  - 循环/截断 → rare（同样高亮）
 *  - 第 1 层非叶子 → uncommon
 *  - 其他 → common
 *
 * 数量优先用 requiredAmount（= 样板真实总产出/总消耗，已是 perExec × times）。
 */
export function toCraftingItemNode(
  node: CraftingTreeNode,
  depth: number = 0,
  pathPrefix: string = '',
): CraftingItemNode {
  const id = `${pathPrefix}${node.itemId}@${depth}`;
  const tier: CraftingTier = pickTier(node, depth);
  const namePrefix = node.isMissing ? '[缺] ' : node.isLoop ? '[环] ' : node.truncated ? '[…] ' : '';
  return {
    id,
    name: `${namePrefix}${node.displayName || node.itemId}`,
    itemId: node.itemId,
    count: Math.max(1, node.requiredAmount || 0),
    tier,
    children: (node.children && node.children.length > 0)
      ? node.children.map((c, i) => toCraftingItemNode(c, depth + 1, `${id}#${i}/`))
      : undefined,
  };
}

function pickTier(node: CraftingTreeNode, depth: number): CraftingTier {
  if (depth === 0) return 'legendary';
  if (node.isMissing || node.isLoop || node.truncated) return 'rare';
  if (depth === 1 && node.children && node.children.length > 0) return 'uncommon';
  return 'common';
}

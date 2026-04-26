// 横向合成树共用类型
export type CraftingTier = 'common' | 'uncommon' | 'rare' | 'legendary';

export interface CraftingItemNode {
  id: string;
  name: string;
  itemId: string;
  count: number;
  tier: CraftingTier;
  icon?: string;
  children?: CraftingItemNode[];
}

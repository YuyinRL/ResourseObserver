import React, { useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import { Box } from 'lucide-react';
import { api } from '../lib/api';

/**
 * 物品图标组件 —— 优先尝试加载后端 /api/icon/{ns}/{path}，
 * 失败（404 / 专用服务端）时回退到 lucide 图标占位。
 */
export interface ItemIconProps {
  item: { id: string; icon?: LucideIcon; accent?: string };
  size?: number;
}

export function ItemIcon({ item, size = 18 }: ItemIconProps) {
  const url = api.iconUrl(item.id);
  const [failed, setFailed] = useState(false);
  const Icon = item.icon ?? Box;
  const accent = item.accent ?? 'text-cyan-400';

  if (!url || failed) {
    return (
      <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-slate-800 bg-slate-900 ${accent}`}>
        <Icon size={size} />
      </div>
    );
  }
  return (
    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-slate-800 bg-slate-900 p-1">
      <img
        src={url}
        alt={item.id}
        className="h-full w-full object-contain image-rendering-pixelated"
        style={{ imageRendering: 'pixelated' }}
        onError={() => setFailed(true)}
      />
    </div>
  );
}

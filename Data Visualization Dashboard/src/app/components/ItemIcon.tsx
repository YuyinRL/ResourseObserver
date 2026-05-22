import React, { useEffect, useRef, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import { Box } from 'lucide-react';
import { api } from '../lib/api';

/**
 * 物品图标组件 —— 优先尝试加载后端 /api/icon/{ns}/{path}，
 * 失败（404）时进行有限次重试（专用服务端会异步触发客户端镜像，约 1-2s 后图标可用），
 * 多次失败后回退到 lucide 图标占位。
 */
export interface ItemIconProps {
  item: { id: string; icon?: LucideIcon; accent?: string };
  size?: number;
  containerClassName?: string;
}

const RETRY_DELAYS_MS = [750, 1500, 3000, 5000, 8000, 10000];
const DEFAULT_CONTAINER_CLASS =
  'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-slate-800 bg-slate-900 p-1';

export function ItemIcon({ item, size = 18, containerClassName }: ItemIconProps) {
  const baseUrl = api.iconUrl(item.id);
  const [ready, setReady] = useState(false);
  const [bust, setBust] = useState(0);
  const retriesRef = useRef(0);
  const timerRef = useRef<number | null>(null);
  const Icon = item.icon ?? Box;
  const accent = item.accent ?? 'text-cyan-400';
  const containerClass = containerClassName ?? DEFAULT_CONTAINER_CLASS;

  useEffect(() => {
    retriesRef.current = 0;
    setReady(false);
    setBust(0);
    return () => {
      if (timerRef.current != null) {
        window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [item.id]);

  if (!baseUrl) {
    return (
      <div className={`${containerClass} ${accent}`}>
        <Icon size={size} />
      </div>
    );
  }

  const url = bust === 0 ? baseUrl : `${baseUrl}${baseUrl.includes('?') ? '&' : '?'}_=${bust}`;
  return (
    <div className={containerClass}>
      {!ready && (
        <Icon className={`${accent} animate-pulse`} size={size} />
      )}
      <img
        src={url}
        alt={item.id}
        className={`${ready ? 'block' : 'hidden'} h-full w-full object-contain image-rendering-pixelated`}
        style={{ imageRendering: 'pixelated' }}
        onLoad={() => {
          retriesRef.current = 0;
          if (timerRef.current != null) {
            window.clearTimeout(timerRef.current);
            timerRef.current = null;
          }
          setReady(true);
        }}
        onError={() => {
          setReady(false);
          if (timerRef.current != null) {
            return;
          }
          const delay = RETRY_DELAYS_MS[Math.min(retriesRef.current, RETRY_DELAYS_MS.length - 1)];
          retriesRef.current += 1;
          timerRef.current = window.setTimeout(() => {
            timerRef.current = null;
            setBust(Date.now());
          }, delay);
        }}
      />
    </div>
  );
}


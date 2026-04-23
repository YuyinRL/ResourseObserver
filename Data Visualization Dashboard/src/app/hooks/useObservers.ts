import { useCallback } from 'react';
import { api, CraftingResponse, Meta, ObserverDetail, ObserverListResponse } from '../lib/api';
import { usePolling } from './usePolling';

/** Observer 列表 —— 每 5 秒刷新一次。 */
export function useObservers() {
  const fetcher = useCallback(() => api.observers(), []);
  return usePolling<ObserverListResponse>(fetcher, 5000);
}

/** 单个 Observer 详情 —— 需传入 id（"dim/x/y/z"）；未传时禁用轮询。 */
export function useObserverDetail(id: string | null, intervalMs: number = 5000) {
  const fetcher = useCallback(() => api.observerDetail(id ?? ''), [id]);
  return usePolling<ObserverDetail>(fetcher, intervalMs, Boolean(id));
}

/** AE2 合成信息 —— 同上，id 为空时禁用。 */
export function useObserverCrafting(id: string | null, intervalMs: number = 5000) {
  const fetcher = useCallback(() => api.observerCrafting(id ?? ''), [id]);
  return usePolling<CraftingResponse>(fetcher, intervalMs, Boolean(id));
}

/** 元数据 —— 启动时拉取一次即可，传 intervalMs=0 禁用轮询。 */
export function useMeta() {
  const fetcher = useCallback(() => api.meta(), []);
  return usePolling<Meta>(fetcher, 0);
}

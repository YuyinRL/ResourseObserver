import { useCallback } from 'react';
import { api, ChartHistoryResponse, CraftingResponse, HistoryRange, ItemSortField, Meta, ObserverDetail, ObserverItemsResponse, ObserverListResponse, SamplerDebugResponse, SortDirection } from '../lib/api';
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

/** AE2 物品分页 —— 大型网络使用服务端过滤/排序/分页，避免一次性下发全部物品。 */
export function useObserverItems(
  id: string | null,
  params: {
    offset?: number;
    limit?: number;
    q?: string;
    sort?: ItemSortField;
    dir?: SortDirection;
    alertsOnly?: boolean;
  },
  intervalMs: number = 5000,
) {
  const { offset = 0, limit = 100, q = '', sort = 'amount', dir = 'desc', alertsOnly = false } = params;
  const fetcher = useCallback(
    () => api.observerItems(id ?? '', { offset, limit, q, sort, dir, alertsOnly }),
    [id, offset, limit, q, sort, dir, alertsOnly],
  );
  return usePolling<ObserverItemsResponse>(fetcher, intervalMs, Boolean(id));
}

/** 元数据 —— 启动时拉取一次即可，传 intervalMs=0 禁用轮询。 */
export function useMeta() {
  const fetcher = useCallback(() => api.meta(), []);
  return usePolling<Meta>(fetcher, 0);
}

/** 资源流量历史 —— 按时间颗粒度（短/中/长）轮询。intervalMs 默认依 range 调整。 */
export function useObserverHistory(
  id: string | null,
  range: HistoryRange,
  itemId?: string | null,
  intervalMsOverride?: number,
) {
  const interval = intervalMsOverride ?? (range === 'detail' ? 2000 : range === 'short' ? 5000 : range === 'medium' ? 15000 : 30000);
  const safeItem = itemId ?? undefined;
  const fetcher = useCallback(
    () => api.observerHistory(id ?? '', range, safeItem),
    [id, range, safeItem],
  );
  return usePolling<ChartHistoryResponse>(fetcher, interval, Boolean(id));
}

/** Web 高精度采样层调试快照 —— 仅在 enabled 时高频轮询，便于验证高精度是否激活。 */
export function useObserverSamplerDebug(
  id: string | null,
  enabled: boolean,
  intervalMs: number = 1000,
) {
  const fetcher = useCallback(() => api.observerSamplerDebug(id ?? ''), [id]);
  return usePolling<SamplerDebugResponse>(fetcher, intervalMs, Boolean(id) && enabled);
}

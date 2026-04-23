import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * usePolling —— 按固定间隔调用 `fetcher`，返回最新数据、加载/错误状态。
 * <p>
 * 由 Resource Observer Dashboard 各 Panel 调用，数据源都是幂等的 GET，
 * 所以直接轮询即可（默认 5s 间隔）。
 *
 * @param fetcher   返回 Promise 的取数函数；闭包内部不要依赖外部变量，
 *                  或使用 `useCallback` 将其稳定下来。
 * @param intervalMs 轮询间隔，默认 5000ms。传 0 / 负数禁用自动轮询。
 * @param enabled    是否启用；false 时不会发起任何请求。
 */
export function usePolling<T>(
  fetcher: () => Promise<T>,
  intervalMs: number = 5000,
  enabled: boolean = true,
): {
  data: T | null;
  error: Error | null;
  loading: boolean;
  refresh: () => Promise<void>;
} {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<Error | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const cancelledRef = useRef(false);

  const refresh = useCallback(async () => {
    if (!enabled) return;
    setLoading(true);
    try {
      const value = await fetcher();
      if (!cancelledRef.current) {
        setData(value);
        setError(null);
      }
    } catch (e) {
      if (!cancelledRef.current) {
        setError(e instanceof Error ? e : new Error(String(e)));
      }
    } finally {
      if (!cancelledRef.current) setLoading(false);
    }
  }, [fetcher, enabled]);

  useEffect(() => {
    cancelledRef.current = false;
    if (!enabled) return undefined;

    refresh();
    if (intervalMs > 0) {
      const timer = setInterval(refresh, intervalMs);
      return () => {
        cancelledRef.current = true;
        clearInterval(timer);
      };
    }
    return () => {
      cancelledRef.current = true;
    };
  }, [refresh, intervalMs, enabled]);

  return { data, error, loading, refresh };
}

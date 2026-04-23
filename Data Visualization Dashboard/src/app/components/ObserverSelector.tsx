import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ObserverSummary } from '../lib/api';
import { useObservers } from '../hooks/useObservers';
import { useI18n } from '../lib/i18n';

interface ObserverSelectionCtx {
  observers: ObserverSummary[];
  selectedId: string | null;
  setSelectedId: (id: string | null) => void;
  loading: boolean;
  error: Error | null;
}

const Ctx = createContext<ObserverSelectionCtx | null>(null);
const STORAGE_KEY = 'resourceobserver.selectedObserverId';

export const ObserverSelectionProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { data, loading, error } = useObservers();
  const observers = data?.observers ?? [];
  const [selectedId, setSelectedIdRaw] = useState<string | null>(() => {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  });

  useEffect(() => {
    if (observers.length === 0) return;
    if (!selectedId || !observers.some((observer) => observer.id === selectedId)) {
      setSelectedIdRaw(observers[0].id);
    }
  }, [observers, selectedId]);

  const setSelectedId = (id: string | null) => {
    setSelectedIdRaw(id);
    try {
      if (id) localStorage.setItem(STORAGE_KEY, id);
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
  };

  const value = useMemo<ObserverSelectionCtx>(
    () => ({ observers, selectedId, setSelectedId, loading, error }),
    [observers, selectedId, loading, error],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
};

export function useSelectedObserver(): ObserverSelectionCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useSelectedObserver must be used within ObserverSelectionProvider');
  return ctx;
}

export const ObserverSelector: React.FC<{ className?: string }> = ({ className }) => {
  const { t } = useI18n();
  const { observers, selectedId, setSelectedId, loading, error } = useSelectedObserver();

  if (error) {
    return (
      <div className={className}>
        <p className="text-xs text-rose-400">{t('observer.failed', { message: error.message })}</p>
      </div>
    );
  }

  if (loading && observers.length === 0) {
    return (
      <div className={className}>
        <p className="text-xs text-slate-500">{t('observer.loading')}</p>
      </div>
    );
  }

  if (observers.length === 0) {
    return (
      <div className={className}>
        <p className="text-xs text-slate-500">{t('observer.empty')}</p>
      </div>
    );
  }

  return (
    <div className={className}>
      <label className="mb-1 block text-[9px] font-black uppercase tracking-[0.2em] text-slate-600">
        {t('observer.label')}
      </label>
      <select
        value={selectedId ?? ''}
        onChange={(event) => setSelectedId(event.target.value || null)}
        className="w-full rounded-lg border border-slate-800 bg-slate-900 py-2 px-2 text-xs text-slate-300 focus:outline-none focus:ring-1 focus:ring-cyan-500/50"
      >
        {observers.map((observer) => (
          <option key={observer.id} value={observer.id}>
            {observer.displayName} ({observer.bindingCount})
          </option>
        ))}
      </select>
    </div>
  );
};

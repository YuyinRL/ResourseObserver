import React, { useMemo, useState } from 'react';
import { Activity, Bell, Clock, Database, Languages, MoreVertical, Settings, Zap } from 'lucide-react';
import { Sidebar } from './components/Sidebar';
import { Overview } from './components/Overview';
import { PowerNetwork } from './components/PowerNetwork';
import { StorageNetwork } from './components/StorageNetwork';
import { I18nProvider, useI18n, type Language } from './lib/i18n';
import { ObserverSelectionProvider, useSelectedObserver } from './components/ObserverSelector';
import { useMeta, useObserverDetail } from './hooks/useObservers';

export default function App() {
  return (
    <I18nProvider>
      <ObserverSelectionProvider>
        <AppInner />
      </ObserverSelectionProvider>
    </I18nProvider>
  );
}

function AppInner() {
  const [activeTab, setActiveTab] = useState('overview');
  const [storageSubTab, setStorageSubTab] = useState<'items' | 'crafting'>('items');
  const [searchQuery, setSearchQuery] = useState('');
  const { t, language, setLanguage } = useI18n();
  const { data: meta, error: metaError } = useMeta();
  const { observers, selectedId, error: obsError } = useSelectedObserver();
  const { data: detail } = useObserverDetail(selectedId);

  const locale = language === 'zh' ? 'zh-CN' : 'en-US';
  const nowLabel = useMemo(
    () => new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date()),
    [locale],
  );

  const linkOk = !metaError && !obsError && meta != null;
  const linkLabel = linkOk
    ? (selectedId ? t('app.status.linkEstablished') : t('app.status.observersCount', { count: observers.length }))
    : t('app.status.disconnected');
  const linkBadgeClass = linkOk
    ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400 shadow-[0_0_10px_rgba(16,185,129,0.1)]'
    : 'bg-rose-500/10 border-rose-500/20 text-rose-400';

  const pageTitle = activeTab === 'storage'
    ? t('app.page.storage')
    : activeTab === 'power'
      ? t('app.page.power')
      : t('app.page.overview');

  const pageSubtitle = activeTab === 'storage'
    ? t('app.subtitle.storage')
    : activeTab === 'power'
      ? t('app.subtitle.power')
      : t('app.subtitle.overview');

  const renderContent = () => {
    switch (activeTab) {
      case 'storage':
        return (
          <StorageNetwork
            searchQuery={searchQuery}
            activeSubTab={storageSubTab}
            onSubTabChange={setStorageSubTab}
          />
        );
      case 'power':
        return <PowerNetwork searchQuery={searchQuery} />;
      case 'overview':
      default:
        return <Overview searchQuery={searchQuery} />;
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-200 font-sans selection:bg-cyan-900/50 flex flex-col md:flex-row overflow-hidden">
      <Sidebar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        storageSubTab={storageSubTab}
        setStorageSubTab={setStorageSubTab}
        searchQuery={searchQuery}
        setSearchQuery={setSearchQuery}
      />

      <main className="flex-1 h-screen overflow-y-auto overflow-x-hidden bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-slate-900/40 via-slate-950 to-slate-950">
        <header className="sticky top-0 z-30 border-b border-slate-800 bg-slate-950/85 px-6 py-4 backdrop-blur-xl shadow-2xl">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div className="flex items-center gap-4">
              <div className="hidden rounded-xl border border-cyan-500/20 bg-cyan-500/10 p-2.5 text-cyan-400 shadow-[0_0_15px_rgba(6,182,212,0.15)] lg:flex">
                {activeTab === 'storage' ? <Database size={24} /> : activeTab === 'power' ? <Zap size={24} /> : <Activity size={24} />}
              </div>
              <div>
                <h2 className="flex items-center gap-2 text-xl font-black uppercase tracking-tight text-white">
                  {pageTitle}
                  <span className={`rounded-md border px-2 py-0.5 text-[10px] font-bold uppercase tracking-widest ${linkBadgeClass}`}>
                    {linkLabel}
                  </span>
                </h2>
                <p className="mt-0.5 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
                  {pageSubtitle}
                  {detail ? ` · ${detail.displayName}` : ''}
                </p>
              </div>
            </div>

            <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
              <div className="grid grid-cols-3 gap-2 lg:flex lg:items-center lg:gap-4 rounded-xl border border-slate-800 bg-slate-900/80 px-4 py-2 shadow-inner backdrop-blur-sm">
                <Metric label={t('app.metric.bindings')} value={detail ? String(detail.bindingCount) : '0'} />
                <Metric label={t('app.metric.apiVersion')} value={meta ? `v${meta.apiVersion}` : '--'} />
                <Metric label={t('app.metric.polling')} value={t('app.metric.pollingValue')} />
              </div>

              <div className="flex items-center gap-2">
                <div
                  role="group"
                  aria-label={t('app.language.aria')}
                  className="inline-flex items-center gap-1 rounded-xl border border-slate-800 bg-slate-900/80 p-1 shadow-inner"
                >
                  <Languages size={14} className="ml-1.5 text-slate-500" aria-hidden />
                  {(['en', 'zh'] as Language[]).map((code) => {
                    const active = language === code;
                    return (
                      <button
                        key={code}
                        type="button"
                        onClick={() => setLanguage(code)}
                        aria-pressed={active}
                        className={`rounded-lg px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.2em] transition-all ${
                          active
                            ? 'border border-cyan-500/20 bg-cyan-500/10 text-cyan-400 shadow-[0_0_10px_rgba(6,182,212,0.15)]'
                            : 'border border-transparent text-slate-500 hover:text-slate-200'
                        }`}
                      >
                        {code === 'en' ? 'EN' : '中文'}
                      </button>
                    );
                  })}
                </div>
                <button className="rounded-xl border border-slate-800 bg-slate-900/80 p-2 text-slate-500 transition-all hover:border-slate-700 hover:text-white">
                  <Bell size={18} />
                </button>
                <button className="rounded-xl border border-slate-800 bg-slate-900/80 p-2 text-slate-500 transition-all hover:border-slate-700 hover:text-white">
                  <Settings size={18} />
                </button>
                <button className="rounded-xl border border-slate-800 bg-slate-900/80 p-2 text-slate-500 transition-all hover:border-slate-700 hover:text-white lg:hidden">
                  <MoreVertical size={18} />
                </button>
              </div>
            </div>
          </div>
        </header>

        <div className="mx-auto max-w-[1600px] space-y-8 p-6 animate-in fade-in slide-in-from-bottom-2 duration-700">
          {renderContent()}
        </div>

        <footer className="mt-12 flex flex-col gap-3 border-t border-slate-800/50 bg-slate-950/80 p-4 text-[9px] font-bold uppercase tracking-[0.2em] text-slate-600 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex flex-wrap items-center gap-4 lg:gap-6">
            <span>{t('app.footer.link')}</span>
            <span>{t('app.footer.sector')}</span>
            <span className="flex items-center gap-1.5">
              <Clock size={10} />
              {nowLabel}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <div className="h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)]" />
            <span className="text-emerald-500/80">{t('app.footer.nominal')}</span>
          </div>
        </footer>
      </main>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex min-w-[72px] flex-col">
      <span className="text-[9px] font-black uppercase tracking-[0.2em] text-slate-600">{label}</span>
      <span className="text-xs font-bold font-mono text-cyan-400">{value}</span>
    </div>
  );
}

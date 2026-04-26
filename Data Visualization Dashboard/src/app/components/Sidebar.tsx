import React, { useMemo, useState } from 'react';
import {
  Activity,
  Box,
  ChevronDown,
  ChevronRight,
  Clock,
  Cpu,
  Database,
  Factory,
  GitBranch,
  Search,
  ShieldCheck,
  Truck,
  Zap,
} from 'lucide-react';
import { ObserverSelector } from './ObserverSelector';
import { useI18n } from '../lib/i18n';

interface SidebarProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
  storageSubTab: 'items' | 'crafting';
  setStorageSubTab: (tab: 'items' | 'crafting') => void;
  searchQuery: string;
  setSearchQuery: (query: string) => void;
}

export const Sidebar = ({
  activeTab,
  setActiveTab,
  storageSubTab,
  setStorageSubTab,
  searchQuery,
  setSearchQuery,
}: SidebarProps) => {
  const { t, language } = useI18n();
  // 仅 Storage Network 拥有下拉子页（存储 / 合成），Overview 与 Power 不需要
  const [storageExpanded, setStorageExpanded] = useState<boolean>(true);

  const nowLabel = useMemo(
    () => new Intl.DateTimeFormat(language === 'zh' ? 'zh-CN' : 'en-US', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(new Date()),
    [language],
  );

  type NavItem = {
    id: string;
    label: string;
    icon: typeof Activity;
    subGroups?: Array<{ id: 'items' | 'crafting'; label: string; icon: typeof Box }>;
  };

  const navItems: NavItem[] = [
    { id: 'overview', label: t('sidebar.nav.overview'), icon: Activity },
    {
      id: 'storage',
      label: t('sidebar.nav.storage'),
      icon: Database,
      subGroups: [
        { id: 'items', label: t('storage.tab.items'), icon: Box },
        { id: 'crafting', label: t('storage.tab.crafting'), icon: Cpu },
      ],
    },
    { id: 'power', label: t('sidebar.nav.power'), icon: Zap },
    { id: 'craftingTree', label: t('sidebar.nav.craftingTree'), icon: GitBranch },
  ];

  return (
    <aside className="sticky top-0 flex h-screen w-full flex-col border-r border-slate-800 bg-slate-950 md:w-72">
      <div className="flex items-center gap-3 border-b border-slate-800 p-6">
        <div className="rounded-lg border border-cyan-500/20 bg-cyan-500/10 p-2 text-cyan-400">
          <Factory size={24} />
        </div>
        <div>
          <h1 className="text-lg font-bold tracking-tight text-white">Aegis Prime</h1>
          <p className="text-xs font-medium text-slate-500">{t('sidebar.brand.subtitle')}</p>
        </div>
      </div>

      <div className="space-y-3 border-b border-slate-800 p-4">
        <ObserverSelector />
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={16} />
          <input
            type="text"
            placeholder={t('sidebar.search.placeholder')}
            value={searchQuery}
            onChange={(event) => setSearchQuery(event.target.value)}
            className="w-full rounded-lg border border-slate-800 bg-slate-900 py-2 pl-10 pr-4 text-sm text-slate-300 transition-all focus:border-cyan-500/50 focus:outline-none focus:ring-1 focus:ring-cyan-500/50"
          />
        </div>
      </div>

      <nav className="flex-1 space-y-2 overflow-y-auto p-4">
        {navItems.map((group) => {
          const hasSubGroups = (group.subGroups?.length ?? 0) > 0;
          const isActive = activeTab === group.id;
          const isExpanded = group.id === 'storage' ? storageExpanded : false;

          return (
            <div key={group.id} className="space-y-1">
              <button
                onClick={() => {
                  setActiveTab(group.id);
                  if (group.id === 'storage') setStorageExpanded(true);
                }}
                className={`w-full rounded-lg px-3 py-2 text-sm font-semibold transition-colors ${
                  isActive
                    ? 'bg-cyan-500/10 text-cyan-400'
                    : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
                }`}
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <group.icon size={18} />
                    <span>{group.label}</span>
                  </div>
                  {hasSubGroups ? (
                    <span
                      role="button"
                      tabIndex={0}
                      onClick={(e) => {
                        e.stopPropagation();
                        setStorageExpanded((prev) => !prev);
                      }}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          e.stopPropagation();
                          setStorageExpanded((prev) => !prev);
                        }
                      }}
                      className="inline-flex h-5 w-5 items-center justify-center rounded-md hover:bg-slate-800/60"
                      aria-label={isExpanded ? 'Collapse' : 'Expand'}
                    >
                      {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                    </span>
                  ) : null}
                </div>
              </button>

              {hasSubGroups && isExpanded ? (
                <div className="ml-4 mt-1 space-y-1 border-l border-slate-800 pl-4">
                  {group.subGroups!.map((sub) => {
                    const subActive = isActive && storageSubTab === sub.id;
                    return (
                      <button
                        key={sub.id}
                        onClick={() => {
                          setActiveTab(group.id);
                          setStorageSubTab(sub.id);
                        }}
                        className={`flex w-full items-center gap-3 rounded-md px-3 py-1.5 text-left text-xs font-medium transition-colors ${
                          subActive
                            ? 'bg-cyan-500/10 text-cyan-300'
                            : 'text-slate-500 hover:bg-slate-900 hover:text-slate-300'
                        }`}
                      >
                        <sub.icon size={14} />
                        {sub.label}
                      </button>
                    );
                  })}
                </div>
              ) : null}
            </div>
          );
        })}
      </nav>

      <div className="space-y-3 border-t border-slate-800 p-4">
        <div className="space-y-2 rounded-lg bg-slate-900/50 p-3 text-xs">
          <div className="flex items-center justify-between text-slate-400">
            <span className="flex items-center gap-1.5">
              <Clock size={12} />
              {t('sidebar.systemTime')}
            </span>
            <span className="font-mono text-slate-300">{nowLabel}</span>
          </div>
          <div className="flex items-center justify-between text-slate-400">
            <span className="flex items-center gap-1.5">
              <ShieldCheck size={12} />
              {t('sidebar.netHealth')}
            </span>
            <span className="text-emerald-400">{t('sidebar.netHealthValue')}</span>
          </div>
          <div className="flex items-center justify-between text-slate-400">
            <span className="flex items-center gap-1.5">
              <Truck size={12} />
              {t('sidebar.sync')}
            </span>
            <span className="font-mono text-cyan-400">{t('app.metric.pollingValue')}</span>
          </div>
        </div>
      </div>
    </aside>
  );
};

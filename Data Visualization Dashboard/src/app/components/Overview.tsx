import React, { useMemo, useState } from 'react';
import {
  Activity,
  ArrowRightLeft,
  Box,
  Database,
  Factory,
  Layers,
  Maximize,
  Pickaxe,
  ShieldCheck,
  Star,
  TrendingDown,
  TrendingUp,
  Zap,
} from 'lucide-react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useObserverDetail } from '../hooks/useObservers';
import { useI18n } from '../lib/i18n';
import { deriveOverviewKpis, deriveResourceItems, formatBytes, isAe2, isPower } from '../lib/liveAdapter';
import { useSelectedObserver } from './ObserverSelector';
import { Card, KpiCard, ProgressBar, SectionHeader, StatusPill, ModernDialog, DialogSectionTitle, DialogRow, DialogDivider } from './DashboardPrimitives';

interface ResourceRow {
  id: string;
  name: string;
  produced: number;
  consumed: number;
  stock: number;
  capacity: number;
  icon: typeof Pickaxe;
  accent: string;
}

const MOCK_RESOURCES: ResourceRow[] = [
  { id: 'minecraft:iron_ingot', name: 'Iron Ingot', produced: 1200, consumed: 930, stock: 42000, capacity: 54000, icon: Pickaxe, accent: 'text-amber-400' },
  { id: 'minecraft:copper_ingot', name: 'Copper Ingot', produced: 860, consumed: 740, stock: 31000, capacity: 46000, icon: Pickaxe, accent: 'text-orange-400' },
  { id: 'ae2:printed_silicon', name: 'Printed Silicon', produced: 140, consumed: 112, stock: 5200, capacity: 7600, icon: Layers, accent: 'text-cyan-400' },
  { id: 'minecraft:redstone', name: 'Redstone', produced: 980, consumed: 1020, stock: 18000, capacity: 30000, icon: Zap, accent: 'text-rose-400' },
  { id: 'ae2:engineering_processor', name: 'Engineering Processor', produced: 64, consumed: 58, stock: 1800, capacity: 2800, icon: Box, accent: 'text-emerald-400' },
];

function iconForIndex(index: number) {
  if (index < 2) return Pickaxe;
  if (index < 4) return Layers;
  return Box;
}

function accentForIndex(index: number) {
  return ['text-cyan-400', 'text-amber-400', 'text-blue-400', 'text-emerald-400', 'text-rose-400'][index % 5];
}

function buildFlowSeries(production: number, consumption: number) {
  const baseProduction = Math.max(100, production || 1200);
  const baseConsumption = Math.max(80, consumption || 900);
  return Array.from({ length: 12 }, (_, index) => {
    const offset = Math.sin((index / 12) * Math.PI * 2);
    const inverse = Math.cos((index / 12) * Math.PI * 2);
    return {
      time: `${index * 2}:00`,
      produced: Math.round(baseProduction * (1 + offset * 0.14)),
      consumed: Math.round(baseConsumption * (1 + inverse * 0.12)),
    };
  });
}

export const Overview = ({ searchQuery }: { searchQuery: string }) => {
  const { t } = useI18n();
  const { selectedId } = useSelectedObserver();
  const { data: detail } = useObserverDetail(selectedId);
  const liveItems = useMemo(() => deriveResourceItems(detail, 36), [detail]);
  const liveKpis = useMemo(() => deriveOverviewKpis(detail), [detail]);

  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [watchedMap, setWatchedMap] = useState<Record<string, boolean>>({});
  // 对齐 ModernUI 的 KPI 点击下钻：保存当前打开详情弹窗的 KPI 索引
  const [kpiDialogIndex, setKpiDialogIndex] = useState<number | null>(null);

  const resources = useMemo<ResourceRow[]>(() => {
    if (!liveItems || liveItems.length === 0) return MOCK_RESOURCES;
    return liveItems.map((item, index) => ({
      id: item.id,
      name: item.name,
      produced: item.produced,
      consumed: item.consumed,
      stock: item.stock,
      capacity: item.capacity,
      icon: iconForIndex(index),
      accent: accentForIndex(index),
    }));
  }, [liveItems]);

  const filteredResources = useMemo(() => {
    const keyword = searchQuery.trim().toLowerCase();
    if (!keyword) return resources;
    return resources.filter((resource) =>
      resource.name.toLowerCase().includes(keyword) || resource.id.toLowerCase().includes(keyword),
    );
  }, [resources, searchQuery]);

  const watchedResources = useMemo(
    () => filteredResources.filter((resource, index) => watchedMap[resource.id] ?? index < 3),
    [filteredResources, watchedMap],
  );

  const selectedResource = filteredResources.find((resource) => resource.id === selectedItemId) ?? filteredResources[0];
  const flowSeries = useMemo(
    () => buildFlowSeries(selectedResource?.produced ?? 0, selectedResource?.consumed ?? 0),
    [selectedResource],
  );

  const safeBindings = Array.isArray(detail?.bindings) ? detail!.bindings : [];
  const itemBindings = safeBindings.filter(isAe2).length;
  const powerBindings = safeBindings.filter(isPower).length;
  const storageBytes = safeBindings
    .filter(isAe2)
    .reduce((sum, binding) => sum + (binding.cellCapacity?.itemTotalBytes ?? 0), 0);

  const kpiIcons = [Activity, Factory, Database, ShieldCheck] as const;
  const kpiTones = ['cyan', 'amber', 'blue', 'emerald'] as const;
  const kpiLabels = [
    t('overview.kpi.production'),
    t('overview.kpi.consumption'),
    t('overview.kpi.storage'),
    t('overview.kpi.efficiency'),
  ];
  const kpiFallback = [
    { value: '14.2', unit: '/min', helper: t('overview.fallback.itemTypes', { count: 36 }) },
    { value: '12.9', unit: '/min', helper: t('overview.fallback.bindings', { count: 4 }) },
    { value: '74.2', unit: '%', helper: t('overview.fallback.storage', { used: '85.2 MB', total: '114.8 MB' }) },
    { value: '98.5', unit: '%', helper: t('status.bound') },
  ];
  const kpiData = (liveKpis ?? []).map((item) => ({ value: item.value, unit: item.unit, helper: item.change }));

  const toggleWatch = (id: string) => {
    setWatchedMap((prev) => ({ ...prev, [id]: !(prev[id] ?? false) }));
  };

  if (filteredResources.length === 0) {
    return (
      <Card className="p-6">
        <SectionHeader title={t('overview.table.title')} subtitle={t('overview.empty')} icon={ArrowRightLeft} />
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {kpiLabels.map((label, index) => {
          const row = kpiData[index] ?? kpiFallback[index];
          return (
            <KpiCard
              key={label}
              label={label}
              value={row.value}
              suffix={row.unit}
              helper={`${row.helper} · ${t('overview.kpi.cycle')}`}
              icon={kpiIcons[index]}
              tone={kpiTones[index]}
              onClick={() => setKpiDialogIndex(index)}
            />
          );
        })}
      </div>

      {/* ModernUI 对齐：KPI 卡片详情弹窗 */}
      {kpiDialogIndex !== null
        ? (() => {
            const i = kpiDialogIndex;
            const label = kpiLabels[i];
            const row = kpiData[i] ?? kpiFallback[i];
            const liveKpi = liveKpis?.[i];
            return (
              <ModernDialog
                open
                onClose={() => setKpiDialogIndex(null)}
                title={t('overview.kpi.detail.title', { label })}
                width={420}
                height={380}
              >
                <div className="space-y-3">
                  <DialogSectionTitle>{t('overview.kpi.detail.current')}</DialogSectionTitle>
                  <div className="rounded-lg border border-slate-800 bg-slate-900/60 px-4 py-3">
                    <div className="flex items-baseline gap-1">
                      <span className="text-3xl font-bold text-white font-mono">{row.value}</span>
                      {row.unit ? <span className="text-sm text-slate-500 font-medium">{row.unit}</span> : null}
                    </div>
                    <p className="mt-1 text-[11px] font-medium uppercase tracking-[0.18em] text-slate-500">
                      {row.helper}
                    </p>
                  </div>

                  <DialogDivider />
                  <DialogSectionTitle>{t('overview.kpi.detail.trend')}</DialogSectionTitle>
                  {liveKpi ? (
                    <DialogRow
                      label={t('overview.kpi.cycle')}
                      value={liveKpi.change}
                      tone={liveKpi.isPositive ? 'emerald' : 'amber'}
                    />
                  ) : (
                    <p className="text-xs text-slate-500">{t('overview.kpi.detail.empty')}</p>
                  )}

                  {/* STORAGE KPI：展示已用/剩余/总量与占用比（对齐 showStorageDetailDialog） */}
                  {i === 2 && detail ? (
                    <>
                      <DialogDivider />
                      <DialogSectionTitle>{t('overview.kpi.storage')}</DialogSectionTitle>
                      <div className="mt-2 space-y-1">
                        <DialogRow label={t('overview.kpi.detail.storageUsed')} value={formatBytes(storageBytes)} tone="cyan" />
                        <DialogRow
                          label={t('overview.kpi.detail.fill')}
                          value={`${(liveKpis?.[2]?.value ?? '0')}${liveKpis?.[2]?.unit ?? '%'}`}
                        />
                      </div>
                    </>
                  ) : null}

                  {/* 对于产出/消耗 KPI，列出 Top 物品贡献项 */}
                  {(i === 0 || i === 1) && filteredResources.length > 0 ? (
                    <>
                      <DialogDivider />
                      <DialogSectionTitle>{t('overview.kpi.detail.breakdown')}</DialogSectionTitle>
                      <div className="mt-2 space-y-1.5">
                        {[...filteredResources]
                          .sort((a, b) =>
                            (i === 0 ? b.produced - a.produced : b.consumed - a.consumed),
                          )
                          .slice(0, 5)
                          .map((resource) => (
                            <div
                              key={resource.id}
                              className="flex items-center justify-between gap-3 rounded-md border border-slate-800/80 bg-slate-900/40 px-3 py-2"
                            >
                              <div className="flex min-w-0 items-center gap-2">
                                <div className={`flex h-7 w-7 items-center justify-center rounded-md border border-slate-800 bg-slate-900 ${resource.accent}`}>
                                  <resource.icon size={13} />
                                </div>
                                <span className="truncate text-xs font-semibold text-slate-200">{resource.name}</span>
                              </div>
                              <span className={`font-mono text-xs font-bold ${i === 0 ? 'text-cyan-400' : 'text-amber-400'}`}>
                                {(i === 0 ? resource.produced : resource.consumed).toFixed(1)}/m
                              </span>
                            </div>
                          ))}
                      </div>
                    </>
                  ) : null}

                  <DialogDivider />
                  <p className="text-[11px] italic text-slate-500">{t('overview.kpi.detail.hint')}</p>
                </div>
              </ModernDialog>
            );
          })()
        : null}

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
        <Card className="xl:col-span-12 p-6">
          <SectionHeader
            title={t('overview.chart.title')}
            subtitle={t('overview.chart.subtitle')}
            icon={Activity}
            action={
              selectedItemId ? (
                <button
                  onClick={() => setSelectedItemId(null)}
                  className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.2em] text-slate-400 transition-colors hover:text-white"
                >
                  {t('overview.chart.reset')}
                </button>
              ) : null
            }
          />

          <div className="mt-6 flex flex-wrap gap-2">
            <LegendLabel color="bg-cyan-500" label={t('overview.chart.production')} />
            <LegendLabel color="bg-amber-500" label={t('overview.chart.consumption')} />
          </div>

          <div className="mt-4 h-[320px] w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={flowSeries} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="overviewProd" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#06b6d4" stopOpacity={0.24} />
                    <stop offset="95%" stopColor="#06b6d4" stopOpacity={0} />
                  </linearGradient>
                  <linearGradient id="overviewCons" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.22} />
                    <stop offset="95%" stopColor="#f59e0b" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} opacity={0.5} />
                <XAxis dataKey="time" stroke="#475569" fontSize={11} tickLine={false} axisLine={false} />
                <YAxis stroke="#475569" fontSize={11} tickLine={false} axisLine={false} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: '12px', fontSize: '12px' }}
                />
                <Area type="monotone" dataKey="produced" stroke="#06b6d4" strokeWidth={2.5} fill="url(#overviewProd)" />
                <Area type="monotone" dataKey="consumed" stroke="#f59e0b" strokeWidth={2.5} fill="url(#overviewCons)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
        <Card className="xl:col-span-4 p-5">
          <SectionHeader
            title={t('overview.snapshot.title')}
            subtitle={t('overview.snapshot.subtitle')}
            icon={Activity}
            action={
              <StatusPill tone={detail?.bound ? 'emerald' : 'amber'}>
                {detail?.bound ? t('overview.snapshot.bound') : t('overview.snapshot.unbound')}
              </StatusPill>
            }
          />

          <div className="mt-5 space-y-4">
            <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-4">
              <p className="text-sm font-bold text-white">{detail?.displayName ?? t('overview.fallback.observer')}</p>
              <p className="mt-1 text-[11px] uppercase tracking-[0.18em] text-slate-500">{selectedId ?? t('common.none')}</p>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <SnapshotMetric label={t('overview.snapshot.itemBindings')} value={String(itemBindings)} />
              <SnapshotMetric label={t('overview.snapshot.powerBindings')} value={String(powerBindings)} />
              <SnapshotMetric label={t('overview.kpi.storage')} value={formatBytes(storageBytes)} />
              <SnapshotMetric label={t('overview.kpi.efficiency')} value={(liveKpis?.[3]?.value ?? '98.5') + '%'} />
            </div>
          </div>
        </Card>

        <Card className="xl:col-span-8 p-5">
          <SectionHeader
            title={t('overview.snapshot.watchlist')}
            subtitle={selectedResource ? selectedResource.name : t('common.none')}
            icon={Star}
          />

          <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
            {watchedResources.slice(0, 4).map((resource) => {
              const net = resource.produced - resource.consumed;
              return (
                <button
                  key={resource.id}
                  onClick={() => setSelectedItemId(resource.id)}
                  className={`w-full rounded-xl border p-4 text-left transition-all ${
                    selectedResource?.id === resource.id
                      ? 'border-cyan-500/30 bg-cyan-500/10'
                      : 'border-slate-800 bg-slate-950/70 hover:border-slate-700'
                  }`}
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <div className={`rounded-lg border border-slate-800 bg-slate-900 p-2 ${resource.accent}`}>
                        <resource.icon size={16} />
                      </div>
                      <div>
                        <p className="text-sm font-bold text-white">{resource.name}</p>
                        <p className="text-[10px] uppercase tracking-[0.18em] text-slate-500">{resource.id}</p>
                      </div>
                    </div>
                    <button
                      onClick={(event) => {
                        event.stopPropagation();
                        toggleWatch(resource.id);
                      }}
                      className={`${watchedMap[resource.id] ?? true ? 'text-amber-400' : 'text-slate-600 hover:text-slate-400'}`}
                    >
                      <Star size={16} fill={(watchedMap[resource.id] ?? true) ? 'currentColor' : 'none'} />
                    </button>
                  </div>
                  <div className="mt-3 flex items-center justify-between text-xs">
                    <span className={`${net >= 0 ? 'text-emerald-400' : 'text-rose-400'} font-mono font-bold`}>
                      {net >= 0 ? '+' : ''}{net.toFixed(1)}/m
                    </span>
                    <span className="font-mono text-slate-500">{resource.stock.toLocaleString()}</span>
                  </div>
                </button>
              );
            })}
          </div>
        </Card>
      </div>

      <Card className="overflow-hidden">
        <div className="border-b border-slate-800 bg-slate-900/30 p-5">
          <SectionHeader
            title={t('overview.table.title')}
            subtitle={t('overview.table.subtitle')}
            icon={ArrowRightLeft}
            action={
              <button className="rounded-lg p-1.5 text-slate-500 transition-colors hover:text-white">
                <Maximize size={16} />
              </button>
            }
          />
        </div>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-900/20 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">
                <th className="p-4 w-8"></th>
                <th className="p-4">{t('overview.table.resource')}</th>
                <th className="p-4 text-right">{t('overview.table.stock')}</th>
                <th className="p-4 text-right">{t('overview.table.production')}</th>
                <th className="p-4 text-right">{t('overview.table.consumption')}</th>
                <th className="p-4 min-w-[220px]">{t('overview.table.balance')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/50">
              {filteredResources.map((resource) => {
                const net = resource.produced - resource.consumed;
                return (
                  <tr
                    key={resource.id}
                    onClick={() => setSelectedItemId(resource.id)}
                    className={`cursor-pointer transition-colors ${
                      selectedResource?.id === resource.id ? 'bg-cyan-500/10' : 'hover:bg-slate-800/30'
                    }`}
                  >
                    <td className="p-4">
                      <button
                        onClick={(event) => {
                          event.stopPropagation();
                          toggleWatch(resource.id);
                        }}
                        className={`${watchedMap[resource.id] ?? false ? 'text-amber-400' : 'text-slate-700 hover:text-slate-400'}`}
                      >
                        <Star size={16} fill={(watchedMap[resource.id] ?? false) ? 'currentColor' : 'none'} />
                      </button>
                    </td>
                    <td className="p-4">
                      <div className="flex items-center gap-3">
                        <div className={`rounded-lg border border-slate-800 bg-slate-900 p-2 ${resource.accent}`}>
                          <resource.icon size={18} />
                        </div>
                        <div>
                          <p className="text-sm font-bold text-white">{resource.name}</p>
                          <p className="text-[10px] font-mono text-slate-500">{resource.id}</p>
                        </div>
                      </div>
                    </td>
                    <td className="p-4 text-right font-mono text-sm text-white">{resource.stock.toLocaleString()}</td>
                    <td className="p-4 text-right font-mono text-sm text-cyan-400">{resource.produced.toFixed(1)}</td>
                    <td className="p-4 text-right font-mono text-sm text-amber-400">{resource.consumed.toFixed(1)}</td>
                    <td className="p-4">
                      <div className="mb-1.5 flex justify-between text-[10px] font-mono">
                        <span className={`font-bold ${net >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                          {net >= 0 ? <TrendingUp size={12} className="inline mr-1" /> : <TrendingDown size={12} className="inline mr-1" />}
                          {net >= 0 ? '+' : ''}{net.toFixed(1)}/m
                        </span>
                        <span className="text-slate-600">{resource.capacity.toLocaleString()}</span>
                      </div>
                      <ProgressBar
                        value={resource.stock}
                        max={resource.capacity}
                        colorClass={resource.stock / resource.capacity > 0.75 ? 'bg-emerald-500' : resource.stock / resource.capacity > 0.35 ? 'bg-cyan-500' : 'bg-amber-500'}
                      />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};

function SnapshotMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-950/70 p-3">
      <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">{label}</p>
      <p className="mt-1 text-lg font-bold font-mono text-white">{value}</p>
    </div>
  );
}

function LegendLabel({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-1.5 text-xs font-bold text-slate-400">
      <div className={`h-2.5 w-2.5 rounded-full ${color}`} />
      {label}
    </div>
  );
}

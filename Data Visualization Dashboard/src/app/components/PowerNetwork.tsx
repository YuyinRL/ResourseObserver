import React, { useEffect, useMemo, useState } from 'react';
import {
  Activity, Battery, BatteryCharging, Cpu, Gauge, Zap, ZapOff,
} from 'lucide-react';
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { useObserverDetail } from '../hooks/useObservers';
import { useI18n } from '../lib/i18n';
import {
  computePowerExcludedKeys,
  derivePowerAutoDetectedExtIds,
  derivePowerExtCoGroups,
  derivePowerConsumers,
  derivePowerEffectiveStats,
  derivePowerExternalGroups,
  derivePowerKpis,
  derivePowerLoadSegments,
  derivePowerSummary,
  formatEnergy,
  type LivePowerConsumer,
  type LivePowerExternalGroup,
} from '../lib/liveAdapter';
import { useSelectedObserver } from './ObserverSelector';
import {
  Card, KpiCard, SectionHeader, StatusPill,
  ModernDialog, DialogSectionTitle, DialogRow, DialogDivider,
} from './DashboardPrimitives';

const fmtFE = (n: number) => `${formatEnergy(Math.round(n))}/t`;

const palette = ['#06b6d4', '#3b82f6', '#a855f7', '#ec4899', '#f59e0b', '#10b981', '#ef4444', '#64748b'];

interface Props {
  searchQuery: string;
}

export const PowerNetwork: React.FC<Props> = ({ searchQuery }) => {
  const { t } = useI18n();
  const { selectedId } = useSelectedObserver();
  const { data: detail } = useObserverDetail(selectedId);

  const [kpiDialog, setKpiDialog] = useState<number | null>(null);
  const [extDialogOpen, setExtDialogOpen] = useState(false);
  const [selectedExtIds, setSelectedExtIds] = useState<Set<string>>(new Set());
  const [filter, setFilter] = useState<'all' | 'top'>('all');
  const [donutMode, setDonutMode] = useState<'load' | 'capacity'>('load');

  // Persist user manual selection per observer
  useEffect(() => {
    if (!selectedId) { setSelectedExtIds(new Set()); return; }
    try {
      const raw = localStorage.getItem(`ro.power.excluded.${selectedId}`);
      if (raw) setSelectedExtIds(new Set(JSON.parse(raw)));
      else setSelectedExtIds(new Set());
    } catch { setSelectedExtIds(new Set()); }
  }, [selectedId]);
  useEffect(() => {
    if (!selectedId) return;
    try {
      localStorage.setItem(`ro.power.excluded.${selectedId}`,
        JSON.stringify(Array.from(selectedExtIds)));
    } catch { /* ignore */ }
  }, [selectedId, selectedExtIds]);

  const isLive = !!detail;
  const externalGroups = useMemo(() => derivePowerExternalGroups(detail), [detail]);
  const autoDetectedIds = useMemo(() => derivePowerAutoDetectedExtIds(detail), [detail]);
  const coGroups = useMemo(() => derivePowerExtCoGroups(detail), [detail]);

  // Effective excluded = auto ∪ manual
  const effectiveExtIds = useMemo(() => {
    const s = new Set<string>(autoDetectedIds);
    for (const id of selectedExtIds) s.add(id);
    return s;
  }, [autoDetectedIds, selectedExtIds]);

  const effectiveGroups = useMemo(
    () => externalGroups.filter((g) => effectiveExtIds.has(g.extId)),
    [externalGroups, effectiveExtIds],
  );
  const excludedKeys = useMemo(
    () => computePowerExcludedKeys(externalGroups, effectiveExtIds),
    [externalGroups, effectiveExtIds],
  );

  const rawKpi = useMemo(() => derivePowerKpis(detail), [detail]);
  const effStats = useMemo(
    () => derivePowerEffectiveStats(detail, excludedKeys, effectiveGroups),
    [detail, excludedKeys, effectiveGroups],
  );
  const consumers = useMemo(
    () => derivePowerConsumers(detail, excludedKeys),
    [detail, excludedKeys],
  );
  const segments = useMemo(
    () => derivePowerLoadSegments(detail, excludedKeys),
    [detail, excludedKeys],
  );
  const summary = useMemo(() => derivePowerSummary(detail), [detail]);
  const gridLoad = useMemo(() => {
    if (!effStats) return null;
    const capacity = effStats.totalInputPerTick > 0 ? effStats.totalInputPerTick : 1;
    const ratio = Math.min(1, Math.max(0, effStats.totalOutputPerTick / capacity));
    return {
      ratio,
      pct: ratio * 100,
      critical: ratio > 0.9,
      warning: ratio > 0.7,
    };
  }, [effStats]);

  const filteredConsumers = useMemo(() => {
    let list = consumers ?? [];
    const q = searchQuery.trim().toLowerCase();
    if (q) {
      list = list.filter((c) =>
        c.name.toLowerCase().includes(q) || c.modName.toLowerCase().includes(q));
    }
    if (filter === 'top') list = list.slice(0, 10);
    return list;
  }, [consumers, searchQuery, filter]);

  const donutData = useMemo(() => {
    const segs = segments ?? [];
    if (segs.length === 0) {
      return [{ name: t('common.none'), value: 100, color: '#334155', isHeadroom: false }];
    }
    if (donutMode === 'load') {
      return segs.map((s, i) => ({
        name: s.displayName,
        value: Math.max(0.001, s.percentage),
        color: s.color || palette[i % palette.length],
        isHeadroom: false,
      }));
    }
    // capacity 模式：用电负载占总发电功率的比例 + 余量切片
    const input = effStats?.totalInputPerTick ?? 0;
    const output = effStats?.totalOutputPerTick ?? 0;
    const ratio = input > 0 ? Math.min(1, output / input) : 0;
    const headroom = Math.max(0, (1 - ratio) * 100);
    const scaled = segs.map((s, i) => ({
      name: s.displayName,
      value: Math.max(0.001, s.percentage * ratio),
      color: s.color || palette[i % palette.length],
      isHeadroom: false,
    }));
    if (headroom > 0.01) {
      scaled.push({
        name: t('power.distribution.headroom'),
        value: headroom,
        color: '#1e293b',
        isHeadroom: true,
      });
    }
    return scaled;
  }, [segments, donutMode, effStats, t]);

  const donutCenter = useMemo(() => {
    const input = effStats?.totalInputPerTick ?? 0;
    const output = effStats?.totalOutputPerTick ?? 0;
    if (donutMode === 'capacity') {
      const headroom = input > 0 ? Math.max(0, ((input - output) / input) * 100) : 0;
      return { value: `${headroom.toFixed(1)}%`, label: t('power.distribution.headroomLabel') };
    }
    return { value: fmtFE(output), label: t('power.distribution.demand') };
  }, [donutMode, effStats, t]);

  const storedRatio = effStats && effStats.totalCapacity > 0
    ? (effStats.totalStored / effStats.totalCapacity) * 100
    : 0;

  const storageHelper = effStats
    ? `${formatEnergy(effStats.totalStored)} / ${formatEnergy(effStats.totalCapacity)}`
    : '—';

  if (!isLive) {
    return (
      <Card className="p-8 text-center">
        <p className="text-slate-400">{t('power.empty')}</p>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      {/* ========== KPI Row: 有效输入 / 有效输出 / 储能 / 排储速率 ========== */}
      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard
          label={t('power.kpi.input.label')}
          value={fmtFE(effStats?.totalInputPerTick ?? 0)}
          icon={Zap}
          tone="cyan"
          helper={effStats && effStats.excludedInputPerTick > 0 ? `−${fmtFE(effStats.excludedInputPerTick)}` : undefined}
          onClick={() => setKpiDialog(0)}
        />
        <KpiCard
          label={t('power.kpi.output.label')}
          value={fmtFE(effStats?.totalOutputPerTick ?? 0)}
          icon={ZapOff}
          tone="amber"
          helper={effStats && effStats.excludedOutputPerTick > 0 ? `−${fmtFE(effStats.excludedOutputPerTick)}` : undefined}
          onClick={() => setKpiDialog(1)}
        />
        <KpiCard
          label={t('power.kpi.storage.label')}
          value={`${storedRatio.toFixed(1)}%`}
          icon={Battery}
          tone="emerald"
          helper={storageHelper}
          onClick={() => setKpiDialog(2)}
        />
        <KpiCard
          label={t('power.kpi.excludedRate.label')}
          value={fmtFE(effStats?.excludedTotalPerTick ?? 0)}
          icon={BatteryCharging}
          tone="blue"
          helper={t('power.helper.excludedRate')}
          onClick={() => setKpiDialog(3)}
        />
      </div>

      {/* ========== Mid Section: 2 columns ========== */}
      <div className="grid grid-cols-1 xl:grid-cols-12 gap-6">
        {/* Left column: Donut + External Storage Console */}
        <div className="xl:col-span-5 space-y-6">
          <Card className="p-5">
            <SectionHeader title={t('power.distribution.title')} icon={Gauge} />
            {/* Switch: 切换两种视图 —— 占总负载 / 占总发电 */}
            <div className="mt-3 inline-flex rounded-lg border border-slate-700 bg-slate-950/60 p-0.5 text-[11px]">
              <button
                type="button"
                onClick={() => setDonutMode('load')}
                className={`px-2.5 py-1 rounded-md transition ${donutMode === 'load' ? 'bg-cyan-500/20 text-cyan-200' : 'text-slate-400 hover:text-slate-200'}`}
              >
                {t('power.distribution.mode.load')}
              </button>
              <button
                type="button"
                onClick={() => setDonutMode('capacity')}
                className={`px-2.5 py-1 rounded-md transition ${donutMode === 'capacity' ? 'bg-cyan-500/20 text-cyan-200' : 'text-slate-400 hover:text-slate-200'}`}
              >
                {t('power.distribution.mode.capacity')}
              </button>
            </div>
            <div className="mt-4 h-[200px] relative">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={donutData} innerRadius={56} outerRadius={80} paddingAngle={7} dataKey="value" stroke="none">
                    {donutData.map((entry) => (
                      <Cell key={entry.name} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: '12px', fontSize: '12px' }}
                    formatter={(value: number, name: string) => [`${Number(value).toFixed(1)}%`, name]}
                  />
                </PieChart>
              </ResponsiveContainer>
              <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                <span className="text-xl font-bold text-white">{donutCenter.value}</span>
                <span className="text-[9px] font-bold uppercase tracking-[0.2em] text-slate-500">{donutCenter.label}</span>
              </div>
            </div>
            <div className="mt-4 space-y-2">
              {donutData.map((entry) => (
                <div key={entry.name} className="flex items-center gap-2 rounded-lg bg-slate-950/70 px-3 py-2 text-xs">
                  <div className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: entry.color }} />
                  <span className="truncate text-slate-400">{entry.name}</span>
                  <span className="ml-auto font-mono text-slate-300">{Number(entry.value).toFixed(1)}%</span>
                </div>
              ))}
            </div>
          </Card>

          {/* External Storage Console — below donut */}
          <Card className="p-5">
            <SectionHeader
              title={t('power.external.title')}
              icon={Battery}
              action={
                <button
                  type="button"
                  onClick={() => setExtDialogOpen(true)}
                  className="px-3 py-1.5 text-xs font-semibold rounded-md border border-cyan-500/40 bg-cyan-500/10 text-cyan-300 hover:bg-cyan-500/20 transition"
                >
                  {t('power.external.toggle')}
                </button>
              }
            />
            <div className="mt-3 space-y-2 text-xs">
              <div className="flex justify-between text-slate-400">
                <span>{t('power.external.auto')}</span>
                <span className="font-mono text-emerald-300">{autoDetectedIds.size}</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span>{t('power.external.manual')}</span>
                <span className="font-mono text-cyan-300">{selectedExtIds.size}</span>
              </div>
              <div className="flex justify-between text-slate-400 border-t border-slate-800 pt-2">
                <span>{t('power.external.excludedInput')}</span>
                <span className="font-mono text-rose-300">{fmtFE(effStats?.excludedInputPerTick ?? 0)}</span>
              </div>
              <div className="flex justify-between text-slate-400">
                <span>{t('power.external.excludedOutput')}</span>
                <span className="font-mono text-rose-300">{fmtFE(effStats?.excludedOutputPerTick ?? 0)}</span>
              </div>
            </div>
            {effectiveGroups.length > 0 && (
              <div className="mt-3 pt-3 border-t border-slate-800 space-y-1.5 max-h-32 overflow-y-auto thin-scrollbar">
                {effectiveGroups.slice(0, 6).map((g) => (
                  <div key={g.extId} className="flex items-center gap-2 text-xs">
                    {autoDetectedIds.has(g.extId)
                      ? <StatusPill tone="emerald">{t('power.external.auto')}</StatusPill>
                      : <StatusPill tone="cyan">{t('power.external.manual')}</StatusPill>}
                    <span className="truncate text-slate-300">{g.displayName}</span>
                    <span className="ml-auto font-mono text-slate-500">{formatEnergy(g.stored)}</span>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>

        {/* Right column: Grid Load progress bars + Devices table */}
        <div className="xl:col-span-7 space-y-6">
          <Card className="p-5">
            <SectionHeader title={t('power.loadbar.title')} icon={Activity} />
            <div className="mt-4 space-y-3">
              {!effStats || !gridLoad ? (
                <p className="text-slate-500 text-sm">{t('common.none')}</p>
              ) : (
                <>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-slate-500">{t('power.loadbar.generation')}</span>
                    <span className="font-mono text-emerald-300">{fmtFE(effStats.totalInputPerTick)}</span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-slate-500">{t('power.loadbar.peakLoad')}</span>
                    <span className="font-mono text-cyan-300">{fmtFE(effStats.totalOutputPerTick)}</span>
                  </div>
                  {rawKpi && effStats.excludedTotalPerTick > 0 ? (
                    <p className="text-[10px] text-slate-500">
                      {t('power.loadbar.rawHint', {
                        input: formatEnergy(rawKpi.inputPerTick),
                        output: formatEnergy(rawKpi.outputPerTick),
                        excluded: formatEnergy(effStats.excludedTotalPerTick),
                      })}
                    </p>
                  ) : null}
                  <div className="relative mt-2 h-2.5 overflow-hidden rounded-full border border-slate-700/80 bg-slate-950 shadow-inner">
                    <div
                      className={`h-full rounded-full transition-all duration-500 ${
                        gridLoad.critical ? 'bg-rose-500' : gridLoad.warning ? 'bg-amber-500' : 'bg-cyan-500'
                      }`}
                      style={{ width: `${Math.max(gridLoad.pct, effStats.totalOutputPerTick > 0 ? 1 : 0)}%` }}
                    />
                    <div className="absolute right-0 top-0 h-full w-px bg-rose-400" />
                  </div>
                  <p className="text-[10px] text-slate-500">
                    {t('power.loadbar.capUtil')} {gridLoad.pct.toFixed(0)}%
                  </p>
                </>
              )}
            </div>
          </Card>

          {/* Power Devices Table */}
          <Card className="p-5">
            <SectionHeader
              title={t('power.lines.title')}
              icon={Cpu}
              action={
                <div className="flex gap-1">
                  <button
                    type="button"
                    onClick={() => setFilter('all')}
                    className={`px-2.5 py-1 text-[10px] font-semibold rounded border ${filter === 'all' ? 'border-cyan-500/50 bg-cyan-500/15 text-cyan-300' : 'border-slate-700 text-slate-400 hover:border-slate-600'}`}
                  >ALL</button>
                  <button
                    type="button"
                    onClick={() => setFilter('top')}
                    className={`px-2.5 py-1 text-[10px] font-semibold rounded border ${filter === 'top' ? 'border-cyan-500/50 bg-cyan-500/15 text-cyan-300' : 'border-slate-700 text-slate-400 hover:border-slate-600'}`}
                  >TOP 10</button>
                </div>
              }
            />
            <div className="mt-3 max-h-96 overflow-y-auto thin-scrollbar">
              <table className="w-full text-xs">
                <thead className="sticky top-0 bg-slate-900/95 backdrop-blur">
                  <tr className="text-slate-500 uppercase tracking-wider">
                    <th className="text-left py-2 px-2">{t('power.lines.consumer')}</th>
                    <th className="text-right py-2 px-2">{t('power.lines.consumption')}</th>
                    <th className="text-right py-2 px-2">%</th>
                    <th className="text-right py-2 px-2">{t('power.lines.devices')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredConsumers.length === 0 ? (
                    <tr><td colSpan={4} className="py-6 text-center text-slate-500">{t('common.none')}</td></tr>
                  ) : filteredConsumers.map((c: LivePowerConsumer) => (
                    <tr key={c.id} className="border-t border-slate-800/50 hover:bg-slate-800/30">
                      <td className="py-2 px-2">
                        <p className="text-slate-200 truncate max-w-[240px]">{c.name}</p>
                        <p className="text-[10px] text-slate-500 truncate">{c.modName}</p>
                      </td>
                      <td className="py-2 px-2 text-right font-mono text-amber-300">{fmtFE(c.consumption)}</td>
                      <td className="py-2 px-2 text-right font-mono text-slate-400">{c.percentage.toFixed(1)}</td>
                      <td className="py-2 px-2 text-right font-mono text-slate-400">{c.count}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>

      {/* ========== Network Summary (bottom) ========== */}
      {summary && (
        <Card className="p-5">
          <SectionHeader title={t('power.summary.title')} icon={Gauge} />
          <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <p className="text-slate-500 text-xs uppercase tracking-wider">{t('power.summary.stored')}</p>
              <p className="mt-1 font-mono text-cyan-300">{formatEnergy(summary.totalEnergy)}</p>
            </div>
            <div>
              <p className="text-slate-500 text-xs uppercase tracking-wider">{t('power.kpi.capacity')}</p>
              <p className="mt-1 font-mono text-slate-300">{formatEnergy(summary.totalMax)}</p>
            </div>
            <div>
              <p className="text-slate-500 text-xs uppercase tracking-wider">{t('power.summary.input')}</p>
              <p className="mt-1 font-mono text-emerald-300">{fmtFE(summary.input)}</p>
            </div>
            <div>
              <p className="text-slate-500 text-xs uppercase tracking-wider">{t('power.summary.output')}</p>
              <p className="mt-1 font-mono text-amber-300">{fmtFE(summary.output)}</p>
            </div>
          </div>
        </Card>
      )}

      {/* ========== KPI Dialog ========== */}
      <ModernDialog
        open={kpiDialog !== null}
        onClose={() => setKpiDialog(null)}
        title={
          kpiDialog === 0 ? t('power.kpi.input.label')
            : kpiDialog === 1 ? t('power.kpi.output.label')
            : kpiDialog === 2 ? t('power.kpi.storage.label')
            : t('power.kpi.excludedRate.label')
        }
        width={460}
        height={380}
      >
        {effStats && rawKpi && (
          <div className="space-y-2">
            <DialogSectionTitle>{t('power.summary.title')}</DialogSectionTitle>
            <DialogRow label={t('power.kpi.input.label')} value={fmtFE(rawKpi.inputPerTick)} />
            <DialogRow label={t('power.kpi.output.label')} value={fmtFE(rawKpi.outputPerTick)} />
            <DialogDivider />
            <DialogRow label={t('power.external.excludedInput')} value={fmtFE(effStats.excludedInputPerTick)} />
            <DialogRow label={t('power.external.excludedOutput')} value={fmtFE(effStats.excludedOutputPerTick)} />
            <DialogDivider />
            <DialogRow label={t('power.kpi.storage.label')} value={`${storedRatio.toFixed(2)}%`} />
            <DialogRow label="Stored / Capacity" value={storageHelper} />
          </div>
        )}
      </ModernDialog>

      {/* ========== External Storage Dialog ========== */}
      <ModernDialog
        open={extDialogOpen}
        onClose={() => setExtDialogOpen(false)}
        title={t('power.external.dialog.title')}
        width={560}
        height={460}
      >
        <div className="space-y-3">
          <p className="text-xs text-slate-400">{t('power.external.dialog.hint')}</p>
          {externalGroups.length === 0 ? (
            <p className="text-sm text-slate-500 py-6 text-center">{t('common.none')}</p>
          ) : (
            <table className="w-full text-xs">
              <thead>
                <tr className="text-slate-500 uppercase tracking-wider">
                  <th className="text-left py-2 px-1 w-8">""</th>
                  <th className="text-left py-2 px-1">{t('power.lines.consumer')}</th>
                  <th className="text-right py-2 px-1">{t('power.external.col.stored')}</th>
                </tr>
              </thead>
              <tbody>
                {externalGroups.map((g: LivePowerExternalGroup) => {
                  const isAuto = autoDetectedIds.has(g.extId);
                  const checked = effectiveExtIds.has(g.extId);
                  return (
                    <tr key={g.extId} className="border-t border-slate-800/60">
                      <td className="py-2 px-1">
                        <input
                          type="checkbox"
                          checked={checked}
                          disabled={isAuto}
                          onChange={(e) => {
                            const next = new Set(selectedExtIds);
                            const related = coGroups.get(g.extId) ?? new Set([g.extId]);
                            if (e.target.checked) {
                              for (const id of related) next.add(id);
                            } else {
                              for (const id of related) next.delete(id);
                            }
                            setSelectedExtIds(next);
                          }}
                          className="accent-cyan-500"
                        />
                      </td>
                      <td className="py-2 px-1">
                        <div className="flex items-center gap-2">
                          <span className="text-slate-200 truncate">{g.displayName}</span>
                          {isAuto && <StatusPill tone="emerald">{t('power.external.auto')}</StatusPill>}
                        </div>
                        <p className="text-[10px] text-slate-500 truncate">{g.modName}</p>
                      </td>
                      <td className="py-2 px-1 text-right font-mono text-slate-300">{formatEnergy(g.stored)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
          <div className="flex justify-end gap-2 pt-2 border-t border-slate-800">
            <button
              type="button"
              onClick={() => setSelectedExtIds(new Set())}
              className="px-3 py-1.5 text-xs font-semibold rounded-md border border-slate-700 text-slate-400 hover:border-slate-600"
            >{t('power.external.clear')}</button>
          </div>
        </div>
      </ModernDialog>
    </div>
  );
};

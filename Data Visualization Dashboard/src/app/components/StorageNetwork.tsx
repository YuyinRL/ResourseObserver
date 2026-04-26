import React, { useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  Box,
  Cpu,
  Database,
  Layers,
  List,
  LayoutGrid,
  Pencil,
  Pickaxe,
  Search,
} from 'lucide-react';
import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { api } from '../lib/api';
import { useObserverCrafting, useObserverDetail, useObserverItems } from '../hooks/useObservers';
import type { CraftingPlanResult, CraftingTreeNode, ItemSortField, SortDirection } from '../lib/api';
import { useI18n } from '../lib/i18n';
import {
  countBindingsByType,
  deriveCraftingSummary,
  deriveResourceItems,
  deriveStorageNodes,
  formatBytes,
} from '../lib/liveAdapter';
import { useSelectedObserver } from './ObserverSelector';
import { Card, KpiCard, ProgressBar, SectionHeader, SegmentedControl, IconSegmentedControl, StatusPill, ModernDialog, DialogSectionTitle, DialogRow, DialogDivider, HoverCard } from './DashboardPrimitives';
import { ItemIcon } from './ItemIcon';
import { CraftingTreeView } from './CraftingTreeView';
import { CraftingTree } from './crafting-tree/CraftingTree';
import { toCraftingItemNode } from './crafting-tree/adapters';

interface NodeView {
  id: string;
  name: string;
  type: string;
  capacity: number;
  used: number;
  status: 'Healthy' | 'Alert';
  itemIds?: string[];
}

interface InventoryItem {
  id: string;
  name: string;
  stock: number;
  production: number;
  consumption: number;
  net: number;
  capacity: number;
  remainingMinutes: number;
  networkId?: string;
  translationKey?: string;
  icon: typeof Pickaxe;
  accent: string;
}

const MOCK_NODES: NodeView[] = [
  { id: 'central', name: 'Central ME Hub', type: 'AE2 Network', capacity: 1_280_000, used: 920_000, status: 'Healthy' },
  { id: 'buffer', name: 'Processing Buffer', type: 'AE2 Network', capacity: 640_000, used: 605_000, status: 'Alert' },
  { id: 'outpost', name: 'Mining Relay', type: 'AE2 Network', capacity: 320_000, used: 142_000, status: 'Healthy' },
];

const MOCK_ITEMS: InventoryItem[] = [
  { id: 'minecraft:iron_ingot', name: 'Iron Ingot', stock: 42000, production: 1200, consumption: 930, net: 270, capacity: 54000, remainingMinutes: 45, icon: Pickaxe, accent: 'text-amber-400' },
  { id: 'minecraft:copper_ingot', name: 'Copper Ingot', stock: 31000, production: 860, consumption: 740, net: 120, capacity: 46000, remainingMinutes: 42, icon: Pickaxe, accent: 'text-orange-400' },
  { id: 'ae2:printed_silicon', name: 'Printed Silicon', stock: 5200, production: 140, consumption: 112, net: 28, capacity: 7600, remainingMinutes: 46, icon: Layers, accent: 'text-cyan-400' },
  { id: 'ae2:engineering_processor', name: 'Engineering Processor', stock: 1800, production: 64, consumption: 58, net: 6, capacity: 2800, remainingMinutes: 31, icon: Cpu, accent: 'text-emerald-400' },
];

function iconForIndex(index: number) {
  if (index < 2) return Pickaxe;
  if (index < 5) return Layers;
  return Cpu;
}

function accentForIndex(index: number) {
  return ['text-cyan-400', 'text-amber-400', 'text-blue-400', 'text-emerald-400', 'text-rose-400'][index % 5];
}

function formatDuration(minutes: number) {
  if (!Number.isFinite(minutes) || minutes <= 0) return '0m';
  if (minutes >= 60) return `${(minutes / 60).toFixed(1)}h`;
  return `${minutes.toFixed(0)}m`;
}

function formatElapsed(millis: number) {
  if (!Number.isFinite(millis) || millis <= 0) return '';
  const s = Math.floor(millis / 1000);
  if (s < 60) return `${s}s`;
  const m = Math.floor(s / 60);
  const rs = s % 60;
  if (m < 60) return `${m}m${rs.toString().padStart(2, '0')}s`;
  const h = Math.floor(m / 60);
  const rm = m % 60;
  return `${h}h${rm.toString().padStart(2, '0')}m`;
}

function normalizeStatusTone(status: 'Healthy' | 'Alert') {
  return status === 'Alert' ? 'rose' : 'emerald';
}

export const StorageNetwork = ({
  searchQuery,
  activeSubTab: activeSubTabProp,
  onSubTabChange,
}: {
  searchQuery: string;
  activeSubTab?: 'items' | 'crafting';
  onSubTabChange?: (tab: 'items' | 'crafting') => void;
}) => {
  const { t } = useI18n();
  const { selectedId } = useSelectedObserver();
  const { data: detail } = useObserverDetail(selectedId);
  const { data: craftingData } = useObserverCrafting(selectedId);

  // 当父级（Sidebar）提供 activeSubTab 时受控；否则内部维护以便独立使用
  const [internalSubTab, setInternalSubTab] = useState<'items' | 'crafting'>('items');
  const activeTab = activeSubTabProp ?? internalSubTab;
  const setActiveTab = (value: 'items' | 'crafting') => {
    if (onSubTabChange) onSubTabChange(value); else setInternalSubTab(value);
  };
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [filterAlertsOnly, setFilterAlertsOnly] = useState(false);
  const [craftSearch, setCraftSearch] = useState('');
  const [orderTarget, setOrderTarget] = useState<{ networkId: string; itemId: string; displayName: string } | null>(null);
  const [orderAmount, setOrderAmount] = useState<string>('1');
  const [orderState, setOrderState] = useState<{
    phase: 'input' | 'planning' | 'review' | 'submitting' | 'done';
    plan?: CraftingPlanResult;
    result?: { ok: boolean; status: string; message: string };
  }>({ phase: 'input' });
  const [selectedCpuIndex, setSelectedCpuIndex] = useState<number | null>(null);
  const [treeDialog, setTreeDialog] = useState<{
    title: string;
    finalAmount?: number;
    progressFraction?: number;
    root: CraftingTreeNode | null;
    loading?: boolean;
  } | null>(null);
  const [itemOffset, setItemOffset] = useState(0);
  const [itemSort, setItemSort] = useState<ItemSortField>('amount');
  const [itemSortDir, setItemSortDir] = useState<SortDirection>('desc');
  const [itemViewMode, setItemViewMode] = useState<'list' | 'grid'>(() => {
    if (typeof window === 'undefined') return 'list';
    return window.localStorage.getItem('resourceobserver:itemViewMode') === 'grid' ? 'grid' : 'list';
  });
  // ModernUI 对齐：KPI 详情弹窗、节点重命名弹窗
  const [kpiDialogIndex, setKpiDialogIndex] = useState<number | null>(null);
  const [renameTarget, setRenameTarget] = useState<NodeView | null>(null);
  const [renameValue, setRenameValue] = useState('');
  // 本地存储节点自定义名称（模拟 ModernUI 的 RENAME_NETWORK 动作）
  const [nodeNameOverrides, setNodeNameOverrides] = useState<Record<string, string>>({});

  const liveNodes = useMemo(() => deriveStorageNodes(detail), [detail]);
  const liveItems = useMemo(() => deriveResourceItems(detail, 120), [detail]);
  const pageLimit = 100;
  const { data: itemPage } = useObserverItems(selectedId, {
    offset: itemOffset,
    limit: pageLimit,
    q: searchQuery,
    sort: itemSort,
    dir: itemSortDir,
    alertsOnly: filterAlertsOnly,
  });
  const craftingSummary = useMemo(() => deriveCraftingSummary(craftingData ?? null), [craftingData]);
  const bindingCounts = useMemo(() => countBindingsByType(detail ?? null), [detail]);
  // 是否使用 Mock 回退：真实观察者且至少有 AE2 绑定时使用真实数据；否则回退以便独立演示
  const isLive = Boolean(selectedId) && bindingCounts.ae2 > 0;

  useEffect(() => {
    setItemOffset(0);
  }, [selectedId, searchQuery, filterAlertsOnly, itemSort, itemSortDir]);

  const changeItemViewMode = (mode: 'list' | 'grid') => {
    setItemViewMode(mode);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem('resourceobserver:itemViewMode', mode);
    }
  };

  const changeSort = (field: ItemSortField) => {
    if (itemSort === field) {
      setItemSortDir((prev) => prev === 'desc' ? 'asc' : 'desc');
      return;
    }
    setItemSort(field);
    setItemSortDir(field === 'name' || field === 'remainingMinutes' ? 'asc' : 'desc');
  };

  const nodes = useMemo<NodeView[]>(
    () => {
      const base = liveNodes && liveNodes.length > 0
        ? liveNodes.map((node) => ({ ...node }))
        : MOCK_NODES;
      return base.map((node) => ({
        ...node,
        name: nodeNameOverrides[node.id] ?? node.name,
      }));
    },
    [liveNodes, nodeNameOverrides],
  );

  const items = useMemo<InventoryItem[]>(
    () => {
      if (isLive && itemPage) {
        return itemPage.items.map((item, index) => ({
          id: item.id,
          name: item.displayName || item.id,
          stock: item.amount,
          production: item.production,
          consumption: Math.max(1, item.consumption),
          net: item.net,
          capacity: item.capacity ?? Math.max(item.amount * 1.5, 1000),
          remainingMinutes: item.remainingMinutes ?? item.amount / Math.max(item.consumption, 1),
          networkId: item.networkId,
          translationKey: item.translationKey,
          icon: iconForIndex(index),
          accent: accentForIndex(index),
        }));
      }
      return liveItems && liveItems.length > 0
        ? liveItems.map((item, index) => ({
          id: item.id,
          name: item.name,
          stock: item.stock,
          production: item.produced,
          consumption: Math.max(1, item.consumed),
          net: item.produced - item.consumed,
          capacity: item.capacity,
          remainingMinutes: item.stock / Math.max(item.consumed, 1),
          networkId: item.networkId,
          icon: iconForIndex(index),
          accent: accentForIndex(index),
        }))
        : MOCK_ITEMS;
    },
    [isLive, itemPage, liveItems],
  );
  const totalItemCount = isLive && itemPage ? itemPage.total : items.length;

  const totalUsed = nodes.reduce((sum, node) => sum + node.used, 0);
  const totalCapacity = Math.max(1, nodes.reduce((sum, node) => sum + node.capacity, 0));
  const fillRatio = (totalUsed / totalCapacity) * 100;
  const selectedNode = nodes.find((node) => node.id === selectedNodeId) ?? null;

  const filteredItems = useMemo(() => {
    const keyword = searchQuery.trim().toLowerCase();
    return items.filter((item) => {
      if (keyword && !item.name.toLowerCase().includes(keyword) && !item.id.toLowerCase().includes(keyword)) {
        return false;
      }
      if (filterAlertsOnly && (item.stock / Math.max(item.consumption, 1)) >= 30) {
        return false;
      }
      // 选中节点时按 networkId 精确过滤；同名物品跨网络不再互相串台。
      // 仅当物品自身没有携带 networkId（极旧的回退路径）时才退回到 itemIds 模糊匹配。
      if (selectedNode) {
        if (item.networkId) {
          if (item.networkId !== selectedNode.id) return false;
        } else if (selectedNode.itemIds?.length && !selectedNode.itemIds.includes(item.id)) {
          return false;
        }
      }
      return true;
    });
  }, [filterAlertsOnly, items, searchQuery, selectedNode]);

  const distribution = useMemo(() => {
    const top = items.slice(0, 3);
    if (top.length === 0) {
      return [
        { name: 'Core', value: 100, color: '#06b6d4' },
      ];
    }
    const total = top.reduce((sum, item) => sum + item.stock, 0);
    return top.map((item, index) => ({
      name: item.name,
      value: total > 0 ? Math.round((item.stock / total) * 100) : 0,
      color: ['#06b6d4', '#f59e0b', '#3b82f6'][index] ?? '#64748b',
    }));
  }, [items]);

  const craftingJobs = useMemo(
    () => (craftingData?.bindings ?? []).flatMap((binding) =>
      binding.jobs.map((job) => ({ ...job, networkId: binding.networkId })),
    ),
    [craftingData],
  );

  const craftingStorageBytes = useMemo(
    () => (craftingData?.bindings ?? []).reduce((sum, binding) => sum + binding.storage.totalStorageBytes, 0),
    [craftingData],
  );

  const craftables = useMemo(
    () => (craftingData?.bindings ?? []).flatMap((binding) =>
      binding.craftables.map((item) => ({ ...item, networkId: binding.networkId })),
    ),
    [craftingData],
  );

  const filteredCraftables = useMemo(() => {
    const keyword = craftSearch.trim().toLowerCase();
    if (!keyword) return craftables;
    return craftables.filter((item) =>
      item.displayName.toLowerCase().includes(keyword) || item.itemId.toLowerCase().includes(keyword),
    );
  }, [craftSearch, craftables]);

  return (
    <div className="space-y-6">
      {!isLive ? (
        <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 px-4 py-3 text-xs font-semibold text-amber-300">
          {selectedId
            ? t('storage.notice.noAe2', { total: bindingCounts.total })
            : t('storage.notice.noObserver')}
        </div>
      ) : null}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <KpiCard label={t('storage.kpi.networks')} value={String(nodes.length)} helper={selectedNode?.name ?? t('common.none')} icon={Database} tone="cyan" onClick={() => setKpiDialogIndex(0)} />
        <KpiCard label={t('storage.kpi.used')} value={formatBytes(totalUsed)} helper={t('storage.helper.total', { value: formatBytes(totalCapacity) })} icon={Layers} tone="blue" onClick={() => setKpiDialogIndex(1)} />
        <KpiCard label={t('storage.kpi.fill')} value={fillRatio.toFixed(1)} suffix="%" helper={t('storage.helper.alerts', { count: nodes.filter((node) => node.status === 'Alert').length })} icon={AlertTriangle} tone={fillRatio > 85 ? 'amber' : 'emerald'} onClick={() => setKpiDialogIndex(2)} />
        <KpiCard label={t('storage.kpi.items')} value={String(totalItemCount)} helper={t('storage.helper.craftables', { count: craftingSummary?.craftableCount ?? 0 })} icon={Box} tone="emerald" onClick={() => setKpiDialogIndex(3)} />
      </div>

      <div className="flex items-center justify-between gap-4">
        <SegmentedControl
          value={activeTab}
          onChange={(value) => setActiveTab(value as 'items' | 'crafting')}
          items={[
            { value: 'items', label: t('storage.tab.items') },
            { value: 'crafting', label: t('storage.tab.crafting') },
          ]}
        />
        {activeTab === 'items' ? (
          <div className="flex flex-wrap items-center gap-2">
            <IconSegmentedControl
              value={itemViewMode}
              onChange={(value) => changeItemViewMode(value as 'list' | 'grid')}
              items={[
                { value: 'list', label: t('storage.view.list'), icon: List },
                { value: 'grid', label: t('storage.view.grid'), icon: LayoutGrid },
              ]}
            />
            <div className="inline-flex rounded-xl border border-slate-800 bg-slate-950/90 p-1 shadow-inner">
              <button
                onClick={() => setFilterAlertsOnly(false)}
                className={`rounded-lg px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.2em] ${
                  !filterAlertsOnly ? 'border border-cyan-500/20 bg-cyan-500/10 text-cyan-400' : 'text-slate-500 hover:text-slate-300'
                }`}
              >
                {t('storage.filter.all')}
              </button>
              <button
                onClick={() => setFilterAlertsOnly(true)}
                className={`rounded-lg px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.2em] ${
                  filterAlertsOnly ? 'border border-rose-500/20 bg-rose-500/10 text-rose-400' : 'text-slate-500 hover:text-slate-300'
                }`}
              >
                {t('storage.filter.deficits')}
              </button>
            </div>
          </div>
        ) : null}
      </div>

      {activeTab === 'items' ? (
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
          <div className="space-y-6 xl:col-span-4">
            <Card className="p-5">
              <SectionHeader title={t('storage.nodes.title')} icon={Database} />
              <div className="mt-4 space-y-3">
                {nodes.map((node) => {
                  const fill = (node.used / Math.max(node.capacity, 1)) * 100;
                  const active = selectedNodeId === node.id;
                  return (
                    <div
                      key={node.id}
                      onClick={() => setSelectedNodeId(active ? null : node.id)}
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          setSelectedNodeId(active ? null : node.id);
                        }
                      }}
                      className={`w-full cursor-pointer rounded-xl border p-4 text-left transition-all ${
                        active ? 'border-cyan-500/30 bg-cyan-500/10' : 'border-slate-800 bg-slate-950/70 hover:border-slate-700'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-bold text-white">{node.name}</p>
                          <p className="text-[10px] uppercase tracking-[0.18em] text-slate-500">{node.type}</p>
                        </div>
                        <div className="flex items-center gap-2">
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              setRenameTarget(node);
                              setRenameValue(node.name);
                            }}
                            className="rounded-lg border border-slate-800 bg-slate-900/70 p-1.5 text-slate-400 transition-colors hover:border-cyan-500/30 hover:bg-cyan-500/10 hover:text-cyan-400"
                            aria-label={t('storage.node.rename.title')}
                          >
                            <Pencil size={12} />
                          </button>
                          <StatusPill tone={normalizeStatusTone(node.status)}>{t(node.status === 'Alert' ? 'status.alert' : 'status.healthy')}</StatusPill>
                        </div>
                      </div>
                      <div className="mt-3">
                        <div className="mb-1.5 flex justify-between text-[10px] font-mono">
                          <span className="text-slate-400">{formatBytes(node.used)} / {formatBytes(node.capacity)}</span>
                          <span className={fill > 90 ? 'font-bold text-rose-400' : 'text-slate-500'}>{fill.toFixed(1)}%</span>
                        </div>
                        <ProgressBar value={node.used} max={node.capacity} colorClass={fill > 90 ? 'bg-rose-500' : 'bg-cyan-500'} />
                      </div>
                    </div>
                  );
                })}
              </div>
            </Card>

            <Card className="p-5">
              <SectionHeader title={t('storage.usage.title')} subtitle={t('storage.usage.totalUsed')} icon={LayoutGrid} />
              <div className="mt-5 h-[200px] relative">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={distribution} innerRadius={56} outerRadius={80} paddingAngle={7} dataKey="value" stroke="none">
                      {distribution.map((entry) => (
                        <Cell key={entry.name} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: '12px', fontSize: '12px' }} />
                  </PieChart>
                </ResponsiveContainer>
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                  <span className="text-xl font-bold text-white">{fillRatio.toFixed(1)}%</span>
                  <span className="text-[9px] font-bold uppercase tracking-[0.2em] text-slate-500">{t('storage.usage.totalUsed')}</span>
                </div>
              </div>
              <div className="mt-4 space-y-2">
                {distribution.map((entry) => (
                  <div key={entry.name} className="flex items-center gap-2 rounded-lg bg-slate-950/70 px-3 py-2 text-xs">
                    <div className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: entry.color }} />
                    <span className="truncate text-slate-400">{entry.name}</span>
                    <span className="ml-auto font-mono text-slate-300">{entry.value}%</span>
                  </div>
                ))}
              </div>
            </Card>
          </div>

          <Card className="xl:col-span-8 overflow-hidden">
            <div className="border-b border-slate-800 bg-slate-900/30 p-5">
              <SectionHeader
                title={t('storage.items.title')}
                icon={Box}
                action={itemViewMode === 'grid' ? <LayoutGrid size={16} className="text-slate-500" /> : <List size={16} className="text-slate-500" />}
              />
            </div>
            <div className="flex flex-wrap items-center gap-3 border-b border-slate-800 bg-slate-900/10 px-4 py-3">
              <Legend color="bg-emerald-500" label={t('storage.legend.stable')} />
              <Legend color="bg-amber-500" label={t('storage.legend.warning')} />
              <Legend color="bg-rose-500" label={t('storage.legend.critical')} />
              <div className="ml-auto flex flex-wrap items-center gap-2">
                {(['amount', 'production', 'consumption', 'net', 'name'] as ItemSortField[]).map((field) => (
                  <button
                    key={field}
                    onClick={() => changeSort(field)}
                    className={`rounded-lg border px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.16em] transition-colors ${
                      itemSort === field ? 'border-cyan-500/30 bg-cyan-500/10 text-cyan-300' : 'border-slate-800 bg-slate-950 text-slate-500 hover:text-slate-300'
                    }`}
                  >
                    {t(`storage.sort.${field}`)}{itemSort === field ? (itemSortDir === 'desc' ? ' ↓' : ' ↑') : ''}
                  </button>
                ))}
                <span className="text-[10px] font-mono uppercase tracking-[0.18em] text-slate-600">{t('storage.sync.label', { value: t('app.metric.pollingValue') })}</span>
              </div>
            </div>

            {filteredItems.length === 0 ? (
              <div className="p-8 text-center">
                <p className="text-sm font-bold text-slate-300">{searchQuery ? t('storage.items.emptySearch') : t('storage.empty')}</p>
                <p className="mt-1 text-xs text-slate-500">{searchQuery ? t('storage.items.emptySearchHint') : t('storage.items.emptyHint')}</p>
              </div>
            ) : itemViewMode === 'grid' ? (
              <div className="max-h-[60vh] overflow-y-auto p-4">
                <div className="grid grid-cols-2 gap-3 md:grid-cols-3 2xl:grid-cols-4">
                  {filteredItems.map((item) => {
                    const durationMinutes = item.remainingMinutes ?? item.stock / Math.max(item.consumption, 1);
                    const net = item.net ?? item.production - item.consumption;
                    const tone = durationMinutes < 0.5 ? 'border-rose-500/30 bg-rose-500/5' : durationMinutes < 30 ? 'border-amber-500/30 bg-amber-500/5' : 'border-slate-800 bg-slate-950/70';
                    const netTone = net >= 0 ? 'text-emerald-400' : 'text-rose-400';
                    return (
                      <HoverCard
                        key={`${item.networkId ?? 'local'}-${item.id}`}
                        width={260}
                        trigger={({ ref, onMouseEnter, onMouseLeave }) => (
                          <div
                            ref={ref}
                            onMouseEnter={onMouseEnter}
                            onMouseLeave={onMouseLeave}
                            className={`group relative rounded-xl border p-4 transition-all hover:-translate-y-0.5 hover:border-cyan-500/30 ${tone}`}
                          >
                            <div className="flex items-start justify-between gap-3">
                              <ItemIcon item={item} />
                              <span className={`font-mono text-xs font-bold ${netTone}`}>{net >= 0 ? '+' : ''}{net.toFixed(1)}/m</span>
                            </div>
                            <p className="mt-3 truncate text-sm font-bold text-white" title={item.name}>{item.name}</p>
                            <p className="truncate text-[10px] font-mono text-slate-500" title={item.id}>{item.id}</p>
                            <div className="mt-3 flex items-center justify-between text-xs">
                              <span className="font-mono text-slate-300">{item.stock.toLocaleString()}</span>
                              <span className={durationMinutes < 30 ? 'font-mono font-bold text-amber-400' : 'font-mono text-slate-500'}>{formatDuration(durationMinutes)}</span>
                            </div>
                          </div>
                        )}
                      >
                        {() => (
                          <>
                            <p className="truncate text-xs font-bold text-white">{item.name}</p>
                            <p className="mt-1 break-all text-[10px] font-mono text-slate-500">{item.id}</p>
                            <div className="mt-3 grid grid-cols-2 gap-2 text-[11px]">
                              <TooltipMetric label={t('storage.table.stock')} value={item.stock.toLocaleString()} />
                              <TooltipMetric label={t('storage.table.production')} value={`${item.production.toFixed(1)}/m`} tone="text-cyan-400" />
                              <TooltipMetric label={t('storage.table.consumption')} value={`${item.consumption.toFixed(1)}/m`} tone="text-amber-400" />
                              <TooltipMetric label={t('storage.table.net')} value={`${net >= 0 ? '+' : ''}${net.toFixed(1)}/m`} tone={netTone} />
                              <TooltipMetric label={t('storage.buffer.remaining')} value={formatDuration(durationMinutes)} />
                              <TooltipMetric label={t('observer.label')} value={item.networkId ?? t('common.none')} />
                            </div>
                          </>
                        )}
                      </HoverCard>
                    );
                  })}
                </div>
              </div>
            ) : (
              <div className="max-h-[60vh] overflow-x-auto overflow-y-auto">
                <table className="w-full border-collapse text-left">
                  <thead className="sticky top-0 z-10 bg-slate-950/95 backdrop-blur">
                    <tr className="border-b border-slate-800 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">
                      <th className="p-4">{t('storage.table.item')}</th>
                      <th className="p-4 text-right">{t('storage.table.stock')}</th>
                      <th className="p-4 text-right">{t('storage.table.production')}</th>
                      <th className="p-4 text-right">{t('storage.table.burn')}</th>
                      <th className="p-4 text-right">{t('storage.table.net')}</th>
                      <th className="p-4 min-w-[220px]">{t('storage.table.buffer')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/50">
                    {filteredItems.map((item) => {
                      const durationMinutes = item.remainingMinutes ?? item.stock / Math.max(item.consumption, 1);
                      const net = item.net ?? item.production - item.consumption;
                      const tone = durationMinutes < 0.5 ? 'bg-rose-500' : durationMinutes < 30 ? 'bg-amber-500' : 'bg-emerald-500';
                      const textTone = durationMinutes < 0.5 ? 'text-rose-400' : durationMinutes < 30 ? 'text-amber-400' : 'text-emerald-400';
                      return (
                        <tr key={`${item.networkId ?? 'local'}-${item.id}`} className="transition-colors hover:bg-slate-800/30">
                          <td className="p-4">
                            <div className="flex items-center gap-3">
                              <ItemIcon item={item} />
                              <div className="min-w-0 flex-1">
                                <p className="truncate text-sm font-bold text-white" title={item.name}>{item.name}</p>
                                <p className="truncate text-[10px] font-mono text-slate-500" title={item.id}>{item.id}</p>
                              </div>
                            </div>
                          </td>
                          <td className="p-4 text-right font-mono text-sm text-white">{item.stock.toLocaleString()}</td>
                          <td className="p-4 text-right font-mono text-sm text-cyan-400">{item.production.toFixed(1)}/m</td>
                          <td className="p-4 text-right font-mono text-sm text-amber-400">-{item.consumption.toFixed(1)}/m</td>
                          <td className={`p-4 text-right font-mono text-sm ${net >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>{net >= 0 ? '+' : ''}{net.toFixed(1)}/m</td>
                          <td className="p-4">
                            <div className="mb-1.5 flex justify-between text-[10px] font-mono">
                              <span className={`font-bold ${textTone}`}>{formatDuration(durationMinutes)}</span>
                              <span className="text-slate-600">{t('storage.buffer.remaining')}</span>
                            </div>
                            <ProgressBar value={Math.min(120, durationMinutes)} max={120} colorClass={tone} />
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
            {isLive && itemPage ? (
              <div className="flex items-center justify-between border-t border-slate-800 bg-slate-900/20 px-4 py-3 text-xs">
                <span className="font-mono text-slate-500">
                  {t('storage.items.pageInfo', {
                    start: itemPage.total === 0 ? 0 : itemPage.offset + 1,
                    end: Math.min(itemPage.offset + itemPage.limit, itemPage.total),
                    total: itemPage.total,
                  })}
                </span>
                <div className="flex items-center gap-2">
                  <button
                    disabled={itemOffset <= 0}
                    onClick={() => setItemOffset((prev) => Math.max(0, prev - pageLimit))}
                    className="rounded-lg border border-slate-800 px-3 py-1.5 font-bold uppercase tracking-[0.16em] text-slate-400 disabled:cursor-not-allowed disabled:opacity-40 hover:not-disabled:border-cyan-500/30 hover:not-disabled:text-cyan-300"
                  >
                    {t('common.previous')}
                  </button>
                  <button
                    disabled={itemOffset + pageLimit >= itemPage.total}
                    onClick={() => setItemOffset((prev) => prev + pageLimit)}
                    className="rounded-lg border border-slate-800 px-3 py-1.5 font-bold uppercase tracking-[0.16em] text-slate-400 disabled:cursor-not-allowed disabled:opacity-40 hover:not-disabled:border-cyan-500/30 hover:not-disabled:text-cyan-300"
                  >
                    {t('common.next')}
                  </button>
                </div>
              </div>
            ) : null}
          </Card>
        </div>
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
            <KpiCard label={t('storage.crafting.kpi.cpus')} value={String(craftingSummary?.cpuCount ?? 0)} icon={Cpu} tone="cyan" />
            <KpiCard label={t('storage.crafting.kpi.busy')} value={String(craftingSummary?.busyCpuCount ?? 0)} icon={AlertTriangle} tone={(craftingSummary?.busyCpuCount ?? 0) > 0 ? 'amber' : 'emerald'} />
            <KpiCard label={t('storage.crafting.kpi.storage')} value={formatBytes(craftingStorageBytes)} icon={Layers} tone="blue" />
            <KpiCard label={t('storage.crafting.kpi.craftables')} value={String(craftingSummary?.craftableCount ?? craftables.length)} icon={Box} tone="emerald" />
          </div>

          <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
            <Card className="p-5 xl:col-span-4">
              <SectionHeader title={t('storage.crafting.jobs')} icon={Cpu} />
              <div className="mt-4 space-y-3">
                {craftingJobs.length === 0 ? (
                  <p className="text-sm text-slate-500">{t('storage.crafting.jobsEmpty')}</p>
                ) : (
                  craftingJobs.slice(0, 8).map((job) => {
                    const storageBytes = (job as { storageBytes?: number }).storageBytes ?? 0;
                    const coProc = (job as { coProcessors?: number }).coProcessors ?? 0;
                    const progressFraction = (job as { progressFraction?: number }).progressFraction ?? 0;
                    const elapsedMillis = (job as { elapsedMillis?: number }).elapsedMillis ?? 0;
                    const treeId = (job as { treeId?: string }).treeId ?? '';
                    const pct = Math.round(Math.min(1, Math.max(0, progressFraction)) * 100);
                    const elapsedStr = formatElapsed(elapsedMillis);
                    const clickable = !!treeId && !!selectedId;
                    return (
                      <div
                        key={`${job.networkId}-${job.jobId}`}
                        className={`rounded-xl border border-slate-800 bg-slate-950/70 p-4 ${clickable ? 'cursor-pointer hover:border-cyan-700/60 hover:bg-slate-950' : ''}`}
                        onClick={async () => {
                          if (!clickable || !selectedId) return;
                          setTreeDialog({
                            title: job.outputDisplayName || job.outputItemId,
                            finalAmount: job.totalAmount,
                            progressFraction,
                            root: null,
                            loading: true,
                          });
                          const root = await api.fetchCraftingTree(selectedId, treeId);
                          setTreeDialog((prev) => prev ? { ...prev, root, loading: false } : prev);
                        }}
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <p className="truncate text-sm font-bold text-white" title={job.outputDisplayName || job.outputItemId}>{job.outputDisplayName || job.outputItemId || t('status.idle')}</p>
                            <p className="text-[10px] uppercase tracking-[0.18em] text-slate-500">{job.cpuName || 'CPU'}</p>
                            <p className="mt-1 text-[10px] font-mono text-slate-500">
                              {formatBytes(storageBytes)} · {coProc} co-proc
                            </p>
                          </div>
                          <StatusPill tone={job.busy ? 'amber' : 'emerald'}>{job.busy ? t('status.alert') : t('status.idle')}</StatusPill>
                        </div>
                        {job.busy ? (
                          <div className="mt-3">
                            <div className="mb-1.5 flex justify-between text-[10px] font-mono">
                              <span className="text-slate-400">{pct}%{elapsedStr ? ` · ${elapsedStr}` : ''}</span>
                              {job.remainingAmount > 0 ? (
                                <span className="text-slate-600">{t('storage.crafting.left', { count: job.remainingAmount.toLocaleString() })}</span>
                              ) : null}
                            </div>
                            <ProgressBar value={pct} max={100} colorClass="bg-cyan-500" />
                          </div>
                        ) : null}
                      </div>
                    );
                  })
                )}
              </div>
            </Card>

            <Card className="xl:col-span-8 overflow-hidden">
              <div className="border-b border-slate-800 bg-slate-900/30 p-5">
                <SectionHeader
                  title={t('storage.crafting.craftables')}
                  action={
                    <div className="relative">
                      <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" size={14} />
                      <input
                        value={craftSearch}
                        onChange={(event) => setCraftSearch(event.target.value)}
                        placeholder={t('storage.crafting.search')}
                        className="rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-xs text-slate-300 focus:outline-none focus:ring-1 focus:ring-cyan-500/50"
                      />
                    </div>
                  }
                />
              </div>

              <div className="max-h-[60vh] overflow-x-auto overflow-y-auto">
                <table className="w-full border-collapse text-left">
                  <thead className="sticky top-0 z-10 bg-slate-950/95 backdrop-blur">
                    <tr className="border-b border-slate-800 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">
                      <th className="p-4">{t('storage.table.item')}</th>
                      <th className="p-4">{t('observer.label')}</th>
                      <th className="p-4 text-right">{t('storage.crafting.order') ?? 'Order'}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/50">
                    {filteredCraftables.slice(0, 500).map((item) => (
                      <tr key={`${item.networkId}-${item.itemId}`} className="transition-colors hover:bg-slate-800/30">
                        <td className="p-4">
                          <div className="flex items-center gap-3">
                            <ItemIcon item={{ id: item.itemId, accent: 'text-cyan-400' }} />
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-sm font-bold text-white" title={item.displayName}>{item.displayName}</p>
                              <p className="truncate text-[10px] font-mono text-slate-500" title={item.itemId}>{item.itemId}</p>
                            </div>
                          </div>
                        </td>
                        <td className="p-4 text-xs font-mono text-slate-400">{item.networkId}</td>
                        <td className="p-4 text-right">
                          <button
                            onClick={() => {
                              setOrderTarget({ networkId: item.networkId, itemId: item.itemId, displayName: item.displayName });
                              setOrderAmount('1');
                              setOrderState({ phase: 'input' });
                            }}
                            className="rounded-md border border-cyan-500/30 bg-cyan-500/10 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.18em] text-cyan-300 transition-colors hover:bg-cyan-500/20"
                          >
                            {t('storage.crafting.order') ?? 'Order'}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {filteredCraftables.length > 500 ? (
                <div className="border-t border-slate-800 bg-slate-900/20 p-4 text-right text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">
                  {t('storage.crafting.more', { count: filteredCraftables.length - 500 })}
                </div>
              ) : null}
            </Card>
          </div>
        </div>
      )}

      {/* KPI 详情弹窗 —— 对齐 ModernUI 的抽屉/卡片详情样式 */}
      <ModernDialog
        open={kpiDialogIndex !== null}
        onClose={() => setKpiDialogIndex(null)}
        title={
          kpiDialogIndex === 0 ? t('storage.kpi.detail.title', { label: t('storage.kpi.networks') }) :
          kpiDialogIndex === 1 ? t('storage.kpi.detail.title', { label: t('storage.kpi.used') }) :
          kpiDialogIndex === 2 ? t('storage.kpi.detail.title', { label: t('storage.kpi.fill') }) :
          kpiDialogIndex === 3 ? t('storage.kpi.detail.title', { label: t('storage.kpi.items') }) :
          ''
        }
      >
        {kpiDialogIndex === 0 || kpiDialogIndex === 1 || kpiDialogIndex === 2 ? (
          <>
            <DialogSectionTitle>{t('storage.kpi.detail.topNodes')}</DialogSectionTitle>
            {nodes.length === 0 ? (
              <p className="text-xs text-slate-500">{t('common.none')}</p>
            ) : (
              <div className="space-y-2">
                {nodes.map((node) => {
                  const fill = (node.used / Math.max(node.capacity, 1)) * 100;
                  return (
                    <DialogRow key={node.id}>
                      <div className="flex min-w-0 flex-1 items-center gap-2">
                        <span className={`h-2 w-2 rounded-full ${node.status === 'Alert' ? 'bg-rose-500' : 'bg-emerald-500'}`} />
                        <span className="truncate text-xs font-bold text-white">{node.name}</span>
                      </div>
                      <span className="font-mono text-[11px] text-slate-400">{formatBytes(node.used)} / {formatBytes(node.capacity)}</span>
                      <span className={`font-mono text-[11px] ${fill > 90 ? 'text-rose-400' : 'text-cyan-400'}`}>{fill.toFixed(1)}%</span>
                    </DialogRow>
                  );
                })}
              </div>
            )}
            <DialogDivider />
            <DialogSectionTitle>{t('overview.kpi.detail.trend')}</DialogSectionTitle>
            <p className="text-[11px] text-slate-500">{t('storage.helper.total', { value: formatBytes(totalCapacity) })} · {fillRatio.toFixed(1)}%</p>
          </>
        ) : null}
        {kpiDialogIndex === 3 ? (
          <>
            <DialogSectionTitle>{t('storage.kpi.detail.topItems')}</DialogSectionTitle>
            {items.length === 0 ? (
              <p className="text-xs text-slate-500">{t('common.none')}</p>
            ) : (
              <div className="space-y-2">
                {items.slice(0, 10).map((item) => (
                  <DialogRow key={item.id}>
                    <item.icon size={14} className={item.accent} />
                    <span className="truncate text-xs font-bold text-white">{item.name}</span>
                    <span className="font-mono text-[11px] text-slate-400">{item.stock.toLocaleString()}</span>
                  </DialogRow>
                ))}
              </div>
            )}
          </>
        ) : null}
      </ModernDialog>

      {/* 网络重命名弹窗 —— 复刻 StorageNetworkPageBuilder.showRenameNetworkPopup */}
      <ModernDialog
        open={renameTarget !== null}
        onClose={() => setRenameTarget(null)}
        title={t('storage.node.rename.title')}
        width={320}
      >
        <input
          type="text"
          value={renameValue}
          onChange={(e) => setRenameValue(e.target.value)}
          placeholder={t('storage.node.rename.hint')}
          autoFocus
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              if (renameTarget) {
                const trimmed = renameValue.trim();
                setNodeNameOverrides((prev) => {
                  const next = { ...prev };
                  if (trimmed) next[renameTarget.id] = trimmed; else delete next[renameTarget.id];
                  return next;
                });
                setRenameTarget(null);
              }
            } else if (e.key === 'Escape') {
              setRenameTarget(null);
            }
          }}
          className="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-200 placeholder-slate-600 focus:border-cyan-500/50 focus:outline-none focus:ring-1 focus:ring-cyan-500/30"
        />
        <div className="mt-4 flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={() => setRenameTarget(null)}
            className="rounded-lg border border-slate-800 bg-slate-900/70 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-slate-400 transition-colors hover:border-slate-700 hover:text-slate-200"
          >
            {t('storage.node.rename.cancel')}
          </button>
          <button
            type="button"
            onClick={() => {
              if (!renameTarget) return;
              const trimmed = renameValue.trim();
              setNodeNameOverrides((prev) => {
                const next = { ...prev };
                if (trimmed) next[renameTarget.id] = trimmed; else delete next[renameTarget.id];
                return next;
              });
              setRenameTarget(null);
            }}
            className="rounded-lg border border-cyan-500/30 bg-cyan-500/10 px-4 py-2 text-[11px] font-bold uppercase tracking-[0.18em] text-cyan-300 transition-colors hover:border-cyan-400/50 hover:bg-cyan-500/20 hover:text-cyan-200"
          >
            {t('storage.node.rename.confirm')}
          </button>
        </div>
      </ModernDialog>

      {/* 一键下单 弹窗（plan → review → confirm） */}
      <ModernDialog
        open={orderTarget !== null}
        onClose={async () => {
          if (selectedId && orderState.plan?.planId && orderState.phase === 'review') {
            await api.cancelCraftingPlan(selectedId, orderState.plan.planId);
          }
          setOrderTarget(null);
          setOrderState({ phase: 'input' });
          setSelectedCpuIndex(null);
        }}
        title={t('storage.crafting.orderDialog.title') ?? 'Place crafting order'}
        width={720}
        height={560}
      >
        {orderTarget ? (
          <div className="space-y-4">
            <div className="flex items-center gap-3 rounded-lg border border-slate-800 bg-slate-900/60 p-3">
              <ItemIcon item={{ id: orderTarget.itemId, accent: 'text-cyan-400' }} />
              <div className="min-w-0">
                <p className="truncate text-sm font-bold text-white">{orderTarget.displayName}</p>
                <p className="truncate text-[10px] font-mono text-slate-500">{orderTarget.itemId}</p>
                <p className="truncate text-[10px] font-mono text-slate-500">{orderTarget.networkId}</p>
              </div>
            </div>

            {(orderState.phase === 'input' || orderState.phase === 'planning') ? (
              <div>
                <label className="mb-1 block text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">
                  {t('storage.crafting.orderDialog.amount') ?? 'Amount'}
                </label>
                <input
                  type="number"
                  min={1}
                  value={orderAmount}
                  onChange={(e) => setOrderAmount(e.target.value)}
                  disabled={orderState.phase === 'planning'}
                  className="w-full rounded-md border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-white focus:outline-none focus:ring-1 focus:ring-cyan-500/50"
                />
              </div>
            ) : null}

            {orderState.phase === 'review' && orderState.plan ? (
              <PlanReview plan={orderState.plan} selectedCpuIndex={selectedCpuIndex} onSelectCpuIndex={setSelectedCpuIndex} />
            ) : null}

            {orderState.phase === 'done' && orderState.result ? (
              <div
                className={
                  orderState.result.ok
                    ? 'rounded-md border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-300'
                    : 'rounded-md border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-300'
                }
              >
                <p className="font-bold">{orderState.result.status}</p>
                {orderState.result.message ? <p className="mt-1 font-mono">{orderState.result.message}</p> : null}
              </div>
            ) : null}

            <div className="flex justify-end gap-2">
              <button
                onClick={async () => {
                  if (selectedId && orderState.plan?.planId && orderState.phase === 'review') {
                    await api.cancelCraftingPlan(selectedId, orderState.plan.planId);
                  }
                  setOrderTarget(null);
                  setOrderState({ phase: 'input' });
                  setSelectedCpuIndex(null);
                }}
                className="rounded-md border border-slate-700 bg-slate-900 px-4 py-2 text-xs font-bold uppercase tracking-[0.18em] text-slate-300 hover:bg-slate-800"
              >
                {t('common.cancel') ?? 'Cancel'}
              </button>

              {orderState.phase === 'input' || orderState.phase === 'planning' ? (
                <button
                  disabled={orderState.phase === 'planning' || !selectedId}
                  onClick={async () => {
                    if (!selectedId || !orderTarget) return;
                    const amt = Number.parseInt(orderAmount, 10);
                    if (!Number.isFinite(amt) || amt <= 0) {
                      setOrderState({ phase: 'done', result: { ok: false, status: 'BAD_AMOUNT', message: 'invalid amount' } });
                      return;
                    }
                    setOrderState({ phase: 'planning' });
                    try {
                      const plan = await api.planCraftingOrder(selectedId, {
                        networkId: orderTarget.networkId,
                        itemId: orderTarget.itemId,
                        amount: amt,
                      });
                      if (!plan.planId) {
                        setOrderState({ phase: 'done', result: { ok: false, status: plan.status, message: plan.message } });
                      } else {
                        setOrderState({ phase: 'review', plan });
                      }
                    } catch (e) {
                      setOrderState({ phase: 'done', result: { ok: false, status: 'NETWORK_ERROR', message: String(e) } });
                    }
                  }}
                  className="rounded-md border border-cyan-500/30 bg-cyan-500/20 px-4 py-2 text-xs font-bold uppercase tracking-[0.18em] text-cyan-200 hover:bg-cyan-500/30 disabled:opacity-50"
                >
                  {orderState.phase === 'planning' ? (t('common.loading') ?? '...') : (t('storage.crafting.orderDialog.plan') ?? 'Plan')}
                </button>
              ) : null}

              {orderState.phase === 'review' && orderState.plan && orderState.plan.tree ? (
                <button
                  onClick={() => {
                    if (!orderState.plan) return;
                    setTreeDialog({
                      title: orderState.plan.finalOutputDisplayName || orderState.plan.finalOutputItemId,
                      finalAmount: orderState.plan.finalOutputAmount,
                      root: orderState.plan.tree ?? null,
                    });
                  }}
                  className="rounded-md border border-cyan-500/30 bg-cyan-500/10 px-4 py-2 text-xs font-bold uppercase tracking-[0.18em] text-cyan-200 hover:bg-cyan-500/20"
                >
                  {t('storage.crafting.orderDialog.viewTree') ?? 'View Tree'}
                </button>
              ) : null}

              {orderState.phase === 'review' && orderState.plan ? (
                <button
                  disabled={!orderState.plan.planId}
                  onClick={async () => {
                    if (!selectedId || !orderState.plan) return;
                    const planId = orderState.plan.planId;
                    setOrderState({ phase: 'submitting', plan: orderState.plan });
                    try {
                      const cpuName = selectedCpuIndex !== null && orderState.plan.cpus[selectedCpuIndex]
                        ? orderState.plan.cpus[selectedCpuIndex].name
                        : null;
                      const result = await api.confirmCraftingOrder(selectedId, planId, cpuName);
                      setOrderState({ phase: 'done', result });
                    } catch (e) {
                      setOrderState({ phase: 'done', result: { ok: false, status: 'NETWORK_ERROR', message: String(e) } });
                    }
                  }}
                  className={`rounded-md border px-4 py-2 text-xs font-bold uppercase tracking-[0.18em] disabled:opacity-50 ${
                    orderState.plan.simulation
                      ? 'border-amber-500/30 bg-amber-500/20 text-amber-200 hover:bg-amber-500/30'
                      : 'border-emerald-500/30 bg-emerald-500/20 text-emerald-200 hover:bg-emerald-500/30'
                  }`}
                >
                  {orderState.plan.simulation
                    ? (t('storage.crafting.orderDialog.submitAnyway') ?? 'Submit anyway')
                    : (t('storage.crafting.orderDialog.submit') ?? 'Submit')}
                </button>
              ) : null}

              {orderState.phase === 'submitting' ? (
                <button disabled className="rounded-md border border-emerald-500/30 bg-emerald-500/20 px-4 py-2 text-xs font-bold uppercase tracking-[0.18em] text-emerald-200 disabled:opacity-50">
                  {t('common.loading') ?? '...'}
                </button>
              ) : null}
            </div>
          </div>
        ) : null}
      </ModernDialog>

      <ModernDialog
        open={treeDialog !== null}
        onClose={() => setTreeDialog(null)}
        title={treeDialog ? `${treeDialog.title}${treeDialog.finalAmount ? ` ×${treeDialog.finalAmount.toLocaleString()}` : ''}` : ''}
        width={780}
        height={620}
      >
        {treeDialog ? (
          treeDialog.loading ? (
            <p className="text-xs text-slate-500">{t('common.loading') ?? 'Loading...'}</p>
          ) : treeDialog.root ? (
            <div className="h-full">
              <CraftingTree
                root={toCraftingItemNode(treeDialog.root)}
                onSelect={(n) => console.log('[CraftingTree] node clicked', n)}
              />
            </div>
          ) : (
            <CraftingTreeView
              root={treeDialog.root}
              progressFraction={treeDialog.progressFraction}
              emptyHint={t('storage.crafting.orderDialog.treeUnavailable') ?? 'Tree unavailable (external order).'}
            />
          )
        ) : null}
      </ModernDialog>
    </div>
  );
};

function PlanReview({ plan, selectedCpuIndex, onSelectCpuIndex }: { plan: CraftingPlanResult; selectedCpuIndex: number | null; onSelectCpuIndex: (idx: number | null) => void }) {
  const { t } = useI18n();
  const ok = plan.ok && !plan.simulation;
  const fmt = (n: number) => n.toLocaleString();
  const fmtBytes = (n: number) => {
    if (!Number.isFinite(n) || n <= 0) return '0';
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2)}M`;
    if (n >= 1_000) return `${(n / 1_000).toFixed(2)}k`;
    return String(n);
  };
  return (
    <div className="space-y-3">
      <div
        className={
          ok
            ? 'rounded-md border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-300'
            : plan.simulation
              ? 'rounded-md border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-300'
              : 'rounded-md border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-300'
        }
      >
        <p className="font-bold">{plan.status}{plan.simulation ? ` · ${t('storage.crafting.plan.simulationNote')}` : ''}</p>
        <p className="mt-1 font-mono text-[10px]">{t('storage.crafting.plan.bytes')}: {fmt(plan.bytes)}</p>
        {plan.message ? <p className="mt-1 font-mono text-[10px]">{plan.message}</p> : null}
      </div>
      {plan.finalOutputItemId ? (() => {
        const root = plan.tree;
        const patternOut = root && root.timesExecuted > 0 ? root.perExecOutAmount * root.timesExecuted : 0;
        const displayAmount = patternOut > 0 ? patternOut : plan.finalOutputAmount;
        const showPatternHint = root && root.timesExecuted > 0 && root.perExecOutAmount > 0;
        return (
          <div className="rounded-md border border-cyan-500/30 bg-cyan-500/10 p-3">
            <p className="mb-2 text-[10px] font-bold uppercase tracking-[0.18em] text-cyan-300">{t('storage.crafting.plan.outputs')}</p>
            <div className="flex items-center gap-3">
              <ItemIcon item={{ id: plan.finalOutputItemId, accent: 'text-cyan-300' }} size={28} />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-bold text-white" title={plan.finalOutputDisplayName || plan.finalOutputItemId}>
                  {plan.finalOutputDisplayName || plan.finalOutputItemId}
                </p>
                <p className="truncate font-mono text-[10px] text-slate-500">{plan.finalOutputItemId}</p>
                {showPatternHint ? (
                  <p className="mt-0.5 truncate font-mono text-[10px] text-cyan-200/70">
                    {fmt(root!.perExecOutAmount)} / {t('storage.crafting.plan.perExec') ?? 'per exec'} × {fmt(root!.timesExecuted)} {t('storage.crafting.plan.execs') ?? 'execs'}
                  </p>
                ) : null}
              </div>
              <p className="font-mono text-lg font-bold text-cyan-300">×{fmt(displayAmount)}</p>
            </div>
          </div>
        );
      })() : null}
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <PlanStackBlock title={t('storage.crafting.plan.materials')} stacks={plan.usedItems} accent="text-slate-300" />
        <PlanStackBlock title={t('storage.crafting.plan.missing')} stacks={plan.missingItems} accent="text-rose-300" />
        <PlanStackBlock title={t('storage.crafting.plan.byproducts')} stacks={plan.emittedItems} accent="text-emerald-300" />
      </div>
      {plan.tree ? (
        <details className="group rounded-md border border-slate-800 bg-slate-950/40 open:bg-slate-950/60">
          <summary className="cursor-pointer list-none px-3 py-2 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400 hover:text-cyan-300">
            <span className="mr-1.5 inline-block transition-transform group-open:rotate-90">▸</span>
            {t('storage.crafting.orderDialog.viewTree') ?? 'View Tree'}
          </summary>
          <div className="h-[360px] p-2">
            <CraftingTree
              root={toCraftingItemNode(plan.tree)}
              onSelect={(n) => console.log('[CraftingTree] node clicked', n)}
            />
          </div>
        </details>
      ) : null}
      {plan.cpus && plan.cpus.length > 0 ? (
        <div>
          <p className="mb-1 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">{t('storage.crafting.plan.cpu')}</p>
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => onSelectCpuIndex(null)}
              className={`rounded-md border px-3 py-1 text-[11px] font-mono ${
                selectedCpuIndex === null
                  ? 'border-cyan-500/60 bg-cyan-500/20 text-cyan-200'
                  : 'border-slate-800 bg-slate-950/60 text-slate-400 hover:bg-slate-900'
              }`}
            >
              {t('storage.crafting.plan.cpuAuto')}
            </button>
            {plan.cpus.map((c, i) => {
              const name = c.name || `CPU#${i + 1}`;
              const isSel = selectedCpuIndex === i;
              return (
                <button
                  key={`cpu-${i}`}
                  type="button"
                  onClick={() => onSelectCpuIndex(i)}
                  className={`rounded-md border px-3 py-1 text-[11px] font-mono ${
                    isSel
                      ? 'border-cyan-500/60 bg-cyan-500/20 text-cyan-200'
                      : 'border-slate-800 bg-slate-950/60 text-slate-400 hover:bg-slate-900'
                  }`}
                >
                  {name} <span className="text-slate-500">({fmtBytes(c.storageBytes)}/{c.coProcessors}{c.busy ? '★' : ''})</span>
                </button>
              );
            })}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function PlanStackBlock({ title, stacks, accent, amountSuffix = '×' }: { title: string; stacks: { itemId: string; displayName: string; amount: number }[]; accent: string; amountSuffix?: string }) {
  if (!stacks || stacks.length === 0) return null;
  return (
    <div>
      <p className="mb-1 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">{title}</p>
      <div className="max-h-48 space-y-1 overflow-y-auto rounded-md border border-slate-800 bg-slate-950/60 p-2">
        {stacks.map((s, i) => (
          <div key={`${s.itemId}-${i}`} className="flex items-center gap-2">
            <ItemIcon item={{ id: s.itemId, accent: 'text-slate-400' }} />
            <p className="min-w-0 flex-1 truncate text-[11px] text-white" title={s.displayName || s.itemId}>
              {s.displayName || s.itemId}
            </p>
            <p className={`font-mono text-[11px] font-bold ${accent}`}>{amountSuffix}{s.amount.toLocaleString()}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <div className="flex items-center gap-2">
      <div className={`h-3 w-3 rounded-full ${color}`} />
      <span className="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">{label}</span>
    </div>
  );
}

function TooltipMetric({ label, value, tone = 'text-slate-300' }: { label: string; value: string; tone?: string }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/70 px-2 py-1.5">
      <p className="text-[9px] font-bold uppercase tracking-[0.16em] text-slate-600">{label}</p>
      <p className={`mt-0.5 truncate font-mono font-bold ${tone}`} title={value}>{value}</p>
    </div>
  );
}

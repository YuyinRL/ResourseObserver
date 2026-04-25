import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  Activity,
  ArrowRightLeft,
  Box,
  ChevronDown,
  ChevronUp,
  Database,
  Factory,
  Layers,
  List,
  LayoutGrid,
  Pickaxe,
  ShieldCheck,
  Star,
  TrendingDown,
  TrendingUp,
  Zap,
} from 'lucide-react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, XAxis, YAxis } from 'recharts';
import { useObserverDetail, useObserverHistory, useObserverSamplerDebug } from '../hooks/useObservers';
import type { HistoryRange } from '../lib/api';
import { useI18n } from '../lib/i18n';
import { deriveOverviewKpis, deriveResourceItems, formatBytes, isAe2, isPower } from '../lib/liveAdapter';
import { useSelectedObserver } from './ObserverSelector';
import { Card, KpiCard, ProgressBar, SectionHeader, SegmentedControl, IconSegmentedControl, StatusPill, ModernDialog, DialogSectionTitle, DialogRow, DialogDivider, HoverCard } from './DashboardPrimitives';
import { ItemIcon } from './ItemIcon';

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

type OverviewSortField = 'stock' | 'produced' | 'consumed';
type ChartPageMode = 'throughput' | 'stock';
type ChartScrollMode = 'live' | 'history' | 'oldest';

interface FlowPoint {
  x: number;
  produced: number;
  consumed: number;
  net: number;
  stock: number;
  hasFlow?: boolean;
  hasStock?: boolean;
}

const SMOOTH_FLOW_POINT_COUNT = 240;
const FLOW_BUFFER_MULTIPLIER = 5;
const FLOW_HEAD_MASK_PX = 72;
const FLOW_PLOT_LEFT_INSET_PX = 60;
const FLOW_TAIL_MASK_PX = 72;
const FLOW_TOOLTIP_WIDTH = 170;
const FLOW_TOOLTIP_HEIGHT = 96;
const FLOW_TOOLTIP_GAP = 14;
const FLOW_EDGE_EPSILON_SECONDS = 0.5;
const FLOW_OLDEST_EASE_MS = 850;

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

function buildFlowSeries(production: number, consumption: number, nowSeconds = 0, visibleSeconds = 60): FlowPoint[] {
  const baseProduction = Math.max(100, production || 1200);
  const baseConsumption = Math.max(80, consumption || 900);
  return Array.from({ length: SMOOTH_FLOW_POINT_COUNT }, (_, index) => {
    const x = SMOOTH_FLOW_POINT_COUNT <= 1 ? 0 : index / (SMOOTH_FLOW_POINT_COUNT - 1);
    const offset = Math.sin(x * Math.PI * 2);
    const inverse = Math.cos(x * Math.PI * 2);
    return {
      x: nowSeconds - visibleSeconds + x * visibleSeconds,
      produced: Math.round(baseProduction * (1 + offset * 0.14)),
      consumed: Math.round(baseConsumption * (1 + inverse * 0.12)),
      net: Math.round(baseProduction * (1 + offset * 0.14) - baseConsumption * (1 + inverse * 0.12)),
      stock: Math.round((production || consumption || 1200) * 30 * (1 + Math.sin(x * Math.PI) * 0.08)),
      hasFlow: true,
      hasStock: true,
    };
  });
}

function smoothFlowSeries(
  points: Array<{
    x?: number;
    bucket?: number;
    produced?: number;
    consumed?: number;
    net?: number;
    stock?: number;
    hasFlow?: boolean;
    hasStock?: boolean;
    sampleCount?: number;
  }>,
  bucketSeconds: number,
  outputCount: number,
): FlowPoint[] {
  const anchors = points
    .filter((point) => point.hasFlow !== false && Number(point.sampleCount ?? 0) > 0)
    .map((point, index) => ({
      x: Number(point.x ?? ((point.bucket ?? index) * bucketSeconds)),
      produced: Math.max(0, Number(point.produced ?? 0)),
      consumed: Math.max(0, Number(point.consumed ?? 0)),
      net: Number(point.net ?? (Number(point.produced ?? 0) - Number(point.consumed ?? 0))),
      stock: Math.max(0, Number(point.stock ?? 0)),
      hasFlow: point.hasFlow,
      hasStock: point.hasStock,
    }))
    .filter((point) => Number.isFinite(point.x));
  if (anchors.length === 0) return [];
  if (anchors.length === 1) {
    return Array.from({ length: outputCount }, () => ({
      x: anchors[0].x,
      produced: anchors[0].produced,
      consumed: anchors[0].consumed,
      net: anchors[0].net,
      stock: anchors[0].stock,
      hasFlow: true,
      hasStock: anchors[0].hasStock,
    }));
  }
  anchors.sort((a, b) => a.x - b.x);

  // 零相位 EMA：detail 档（≤0.5s）后端 3s 滑动窗口对外部 ~1Hz 突发输入仍会出现
  // ±1 个 fat 桶的边缘抖动，所以这里再叠一层轻 EMA（τ≈1.2s）压平锯齿；
  // short/medium 档桶尺寸 ≥1s 时用 τ≈1.5s。long 档（≥5s/桶）无需再平滑。
  const tau = bucketSeconds <= 0.5 ? 1.2 : bucketSeconds <= 2 ? 1.5 : Number.POSITIVE_INFINITY;
  if (Number.isFinite(tau)) {
    const alpha = 1 - Math.exp(-bucketSeconds / tau);
    // —— 第一遍：前向 EMA，覆盖整段 ——
    let p = anchors[0].produced;
    let c = anchors[0].consumed;
    let n = anchors[0].net;
    for (let i = 0; i < anchors.length; i++) {
      p += alpha * (anchors[i].produced - p);
      c += alpha * (anchors[i].consumed - c);
      n += alpha * (anchors[i].net - n);
      anchors[i] = { ...anchors[i], produced: p, consumed: c, net: n };
    }
    // —— 第二遍：仅对"稳定段"做反向 EMA，尾部 N 点保持纯前向值 ——
    // 反向 EMA 会让"最近点"的平滑值依赖未来上下文。新点到来时这些点会被重新平滑
    // → 视觉抖动。把尾部 settleSpan ≈ 3τ 内的点排除在反向 pass 之外，
    // 它们就只受前向 EMA 影响，不会因新数据进入而跳动。
    const settleSpan = Math.max(1, Math.ceil((3 * tau) / bucketSeconds));
    const tailStart = Math.max(0, anchors.length - settleSpan);
    if (tailStart > 0) {
      p = anchors[tailStart - 1].produced;
      c = anchors[tailStart - 1].consumed;
      n = anchors[tailStart - 1].net;
      for (let i = tailStart - 1; i >= 0; i--) {
        p += alpha * (anchors[i].produced - p);
        c += alpha * (anchors[i].consumed - c);
        n += alpha * (anchors[i].net - n);
        anchors[i] = { ...anchors[i], produced: p, consumed: c, net: n };
      }
    }
  }

  const minX = anchors[0].x;
  const maxX = anchors[anchors.length - 1].x;
  const span = Math.max(0.001, maxX - minX);
  let cursor = 0;
  return Array.from({ length: outputCount }, (_, index) => {
    const x = minX + (index / Math.max(1, outputCount - 1)) * span;
    while (cursor < anchors.length - 2 && anchors[cursor + 1].x < x) {
      cursor++;
    }
    const left = cursor;
    const right = Math.min(anchors.length - 1, cursor + 1);
    const a = anchors[left];
    const b = anchors[right];
    const t = b.x === a.x ? 0 : (x - a.x) / (b.x - a.x);
    return {
      x,
      produced: a.produced + (b.produced - a.produced) * t,
      consumed: a.consumed + (b.consumed - a.consumed) * t,
      net: a.net + (b.net - a.net) * t,
      stock: a.stock + (b.stock - a.stock) * t,
      hasFlow: true,
      hasStock: a.hasStock || b.hasStock,
    };
  });
}

function FlowTooltip({
  point,
  page,
  productionLabel,
  consumptionLabel,
  stockLabel,
}: {
  point: FlowPoint;
  page: ChartPageMode;
  productionLabel: string;
  consumptionLabel: string;
  stockLabel: string;
}) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-950/95 px-3 py-2 text-xs shadow-2xl">
      {page === 'stock' ? (
        <div className="flex min-w-[140px] items-center justify-between gap-4">
          <span className="text-blue-400">{stockLabel}</span>
          <span className="font-mono text-slate-200">{Math.round(point.stock).toLocaleString()}</span>
        </div>
      ) : (
        <>
          <div className="flex min-w-[140px] items-center justify-between gap-4">
            <span className="text-cyan-400">{productionLabel}</span>
            <span className="font-mono text-slate-200">{Math.round(point.produced).toLocaleString()}/m</span>
          </div>
          <div className="flex min-w-[140px] items-center justify-between gap-4">
            <span className="text-amber-400">{consumptionLabel}</span>
            <span className="font-mono text-slate-200">{Math.round(point.consumed).toLocaleString()}/m</span>
          </div>
        </>
      )}
    </div>
  );
}

function rangeVisibleBucketCount(range: HistoryRange): number {
  switch (range) {
    case 'detail':
      return 60;
    case 'medium':
      return 15;
    case 'long':
      return 12;
    default:
      return 6;
  }
}

function rangePollSeconds(range: HistoryRange): number {
  switch (range) {
    case 'detail':
      return 2;
    case 'medium':
      return 15;
    case 'long':
      return 30;
    default:
      return 5;
  }
}

function interpolateFlowPoint(points: FlowPoint[], x: number): FlowPoint | null {
  if (points.length === 0 || x < points[0].x || x > points[points.length - 1].x) {
    return null;
  }
  let low = 0;
  let high = points.length - 1;
  while (high - low > 1) {
    const mid = Math.floor((low + high) / 2);
    if (points[mid].x <= x) {
      low = mid;
    } else {
      high = mid;
    }
  }
  const a = points[low];
  const b = points[Math.min(points.length - 1, low + 1)];
  const t = b.x === a.x ? 0 : (x - a.x) / (b.x - a.x);
  return {
    x,
    produced: a.produced + (b.produced - a.produced) * t,
    consumed: a.consumed + (b.consumed - a.consumed) * t,
    net: a.net + (b.net - a.net) * t,
    stock: a.stock + (b.stock - a.stock) * t,
    hasFlow: true,
    hasStock: a.hasStock || b.hasStock,
  };
}

function paddedDomain(min: number, max: number): [number, number] {
  if (!Number.isFinite(min) || !Number.isFinite(max)) {
    return [0, 1];
  }
  if (Math.abs(max - min) < 0.001) {
    const pad = Math.max(1, Math.abs(max) * 0.2);
    return [min - pad, max + pad];
  }
  const pad = (max - min) * 0.12;
  return [min - pad, max + pad];
}

function valueToChartY(value: number, domain: [number, number], height: number): number {
  const span = Math.max(0.001, domain[1] - domain[0]);
  const ratio = (domain[1] - value) / span;
  return Math.max(8, Math.min(Math.max(8, height - 8), ratio * height));
}

export const Overview = ({ searchQuery }: { searchQuery: string }) => {
  const { t } = useI18n();
  const { selectedId } = useSelectedObserver();
  const { data: detail } = useObserverDetail(selectedId);
  const liveItems = useMemo(() => deriveResourceItems(detail, 36), [detail]);
  const liveKpis = useMemo(() => deriveOverviewKpis(detail), [detail]);

  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [watchedMap, setWatchedMap] = useState<Record<string, boolean>>({});
  const [watchlistCollapsed, setWatchlistCollapsed] = useState(false);
  const [pendingRemovalIds, setPendingRemovalIds] = useState<Record<string, boolean>>({});
  const [enteringWatchIds, setEnteringWatchIds] = useState<Record<string, boolean>>({});
  const [starPulseIds, setStarPulseIds] = useState<Record<string, boolean>>({});
  const [overviewResourceViewMode, setOverviewResourceViewMode] = useState<'list' | 'grid'>(() => {
    if (typeof window === 'undefined') return 'list';
    return window.localStorage.getItem('resourceobserver:overviewResourceViewMode') === 'grid' ? 'grid' : 'list';
  });
  const [overviewSort, setOverviewSort] = useState<OverviewSortField>('stock');
  const [overviewSortDir, setOverviewSortDir] = useState<'asc' | 'desc'>('desc');
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

  const sortedResources = useMemo(() => {
    const dir = overviewSortDir === 'desc' ? -1 : 1;
    return [...filteredResources].sort((a, b) => {
      const av = overviewSort === 'stock' ? a.stock : overviewSort === 'produced' ? a.produced : a.consumed;
      const bv = overviewSort === 'stock' ? b.stock : overviewSort === 'produced' ? b.produced : b.consumed;
      return (av - bv) * dir;
    });
  }, [filteredResources, overviewSort, overviewSortDir]);

  const watchedResources = useMemo(
    () => filteredResources.filter((resource) => watchedMap[resource.id] ?? false),
    [filteredResources, watchedMap],
  );

  const selectedResource = sortedResources.find((resource) => resource.id === selectedItemId) ?? sortedResources[0];
  const chartScopeTitle = selectedItemId ? t('overview.chart.scope.item') : t('overview.chart.scope.global');
  const chartScopeDetail = selectedItemId ? selectedResource?.name ?? selectedItemId : t('overview.chart.title');

  // 时间颗粒度切换 —— 短/中/长三档真实采样数据
  const [granularity, setGranularity] = useState<HistoryRange>('detail');
  const [chartPage, setChartPage] = useState<ChartPageMode>('throughput');
  const { data: history } = useObserverHistory(selectedId, granularity, selectedItemId);
  const [debugOpen, setDebugOpen] = useState(false);
  const { data: samplerDebug } = useObserverSamplerDebug(selectedId, debugOpen, 1000);
  const rangeHistory = history?.range === granularity ? history : null;
  const activeHistory = rangeHistory && (
    selectedItemId
      ? rangeHistory.scope === 'item' && rangeHistory.itemId === selectedItemId
      : rangeHistory.scope === 'global' && !rangeHistory.itemId
  ) ? rangeHistory : null;
  const visibleBucketCount = rangeHistory?.visibleBucketCount ?? rangeVisibleBucketCount(granularity);
  const bucketSeconds = rangeHistory?.bucketSeconds ?? (granularity === 'detail' ? 1 : granularity === 'short' ? 10 : granularity === 'medium' ? 60 : 300);
  const visibleSeconds = Math.max(bucketSeconds, visibleBucketCount * bucketSeconds);
  const smoothPointCount = Math.max(
    SMOOTH_FLOW_POINT_COUNT,
    SMOOTH_FLOW_POINT_COUNT * Math.max(1, rangeHistory?.bufferMultiplier ?? FLOW_BUFFER_MULTIPLIER),
  );
  const flowSeries = useMemo(() => {
    const nowSeconds = rangeHistory?.gameTime != null ? rangeHistory.gameTime * 0.05 : 0;
    if (!activeHistory || !Array.isArray(activeHistory.points) || activeHistory.points.length === 0) {
      if (selectedId) return [];
      return buildFlowSeries(selectedResource?.produced ?? 0, selectedResource?.consumed ?? 0, nowSeconds, visibleSeconds);
    }
    const series = smoothFlowSeries(activeHistory.points, activeHistory.bucketSeconds || bucketSeconds, smoothPointCount);
    return series.length > 0
      ? series
      : selectedId ? [] : buildFlowSeries(selectedResource?.produced ?? 0, selectedResource?.consumed ?? 0, nowSeconds, visibleSeconds);
  }, [activeHistory, rangeHistory, selectedResource, visibleSeconds, bucketSeconds, smoothPointCount, selectedId]);
  const chartDragRef = useRef<{
    active: boolean;
    pointerId: number | null;
    startX: number;
    startDomainRight: number;
    width: number;
  }>({ active: false, pointerId: null, startX: 0, startDomainRight: 0, width: 1 });
  const nowFrameRef = useRef<number | null>(null);
  const oldestFrameRef = useRef<number | null>(null);
  const historyTimeRef = useRef<{ gameSeconds: number; receivedAt: number } | null>(null);
  const [currentTimeSeconds, setCurrentTimeSeconds] = useState(0);
  const [chartScrollMode, setChartScrollMode] = useState<ChartScrollMode>('live');
  const [historyDomainRight, setHistoryDomainRight] = useState<number | null>(null);
  const [oldestAnimatedRight, setOldestAnimatedRight] = useState<number | null>(null);
  const [isChartDragging, setIsChartDragging] = useState(false);
  const [hoverRatio, setHoverRatio] = useState<number | null>(null);
  const [hoverPosition, setHoverPosition] = useState<{ x: number; y: number; width: number; height: number } | null>(null);

  useEffect(() => {
    const gameSeconds = rangeHistory?.gameTime != null ? rangeHistory.gameTime * 0.05 : 0;
    historyTimeRef.current = { gameSeconds, receivedAt: performance.now() };
    setCurrentTimeSeconds(gameSeconds);
  }, [rangeHistory]);

  useEffect(() => {
    const tick = (now: number) => {
      const base = historyTimeRef.current;
      if (base) {
        setCurrentTimeSeconds(base.gameSeconds + (now - base.receivedAt) / 1000);
      }
      nowFrameRef.current = requestAnimationFrame(tick);
    };
    nowFrameRef.current = requestAnimationFrame(tick);
    return () => {
      if (nowFrameRef.current != null) cancelAnimationFrame(nowFrameRef.current);
    };
  }, []);

  const tailLagSeconds = bucketSeconds + rangePollSeconds(granularity) + Math.min(1, bucketSeconds * 0.25);
  const stableWindowRight = Math.max(0, currentTimeSeconds - tailLagSeconds);
  const minLoadedX = flowSeries.length > 0 ? flowSeries[0].x : stableWindowRight - visibleSeconds;
  const maxLoadedX = flowSeries.length > 0 ? flowSeries[flowSeries.length - 1].x : stableWindowRight;
  const liveDomainRight = Math.min(stableWindowRight, maxLoadedX);
  const oldestDomainRight = Math.min(liveDomainRight, minLoadedX + visibleSeconds);
  const oldestDisplayRight = oldestAnimatedRight == null
    ? oldestDomainRight
    : Math.max(oldestDomainRight, Math.min(liveDomainRight, oldestAnimatedRight));
  const chartDomainRight = chartScrollMode === 'live'
    ? liveDomainRight
    : chartScrollMode === 'oldest'
      ? oldestDisplayRight
      : Math.max(oldestDomainRight, Math.min(liveDomainRight, historyDomainRight ?? liveDomainRight));
  const chartDomainLeft = chartDomainRight - visibleSeconds;

  useEffect(() => {
    if (oldestFrameRef.current != null) {
      cancelAnimationFrame(oldestFrameRef.current);
      oldestFrameRef.current = null;
    }
    if (chartScrollMode !== 'oldest') {
      setOldestAnimatedRight(oldestDomainRight);
      return;
    }
    const from = oldestAnimatedRight ?? oldestDomainRight;
    const to = oldestDomainRight;
    if (Math.abs(from - to) <= 0.001) {
      setOldestAnimatedRight(to);
      return;
    }
    const startedAt = performance.now();
    const step = (now: number) => {
      const progress = Math.min(1, (now - startedAt) / FLOW_OLDEST_EASE_MS);
      const eased = 1 - Math.pow(1 - progress, 3);
      setOldestAnimatedRight(from + (to - from) * eased);
      if (progress < 1) {
        oldestFrameRef.current = requestAnimationFrame(step);
      }
    };
    oldestFrameRef.current = requestAnimationFrame(step);
    return () => {
      if (oldestFrameRef.current != null) {
        cancelAnimationFrame(oldestFrameRef.current);
        oldestFrameRef.current = null;
      }
    };
  }, [chartScrollMode, oldestDomainRight]);

  const hoveredFlowPoint = useMemo(() => {
    if (hoverRatio == null) return null;
    const hoverX = chartDomainLeft + hoverRatio * visibleSeconds;
    return interpolateFlowPoint(flowSeries, hoverX);
  }, [flowSeries, hoverRatio, chartDomainLeft, visibleSeconds]);
  const chartDomains = useMemo(() => {
    const visible = flowSeries.filter((point) => point.x >= chartDomainLeft && point.x <= chartDomainRight);
    if (chartPage === 'stock') {
      const stocks = visible.map((point) => point.stock).filter((value) => Number.isFinite(value));
      return {
        main: paddedDomain(0, Math.max(1, ...stocks)),
      };
    }
    const throughputValues = visible
      .flatMap((point) => [point.produced, point.consumed])
      .filter((value) => Number.isFinite(value));
    const maxThroughput = Math.max(1, ...throughputValues);
    return {
      main: [0, maxThroughput * 1.12] as [number, number],
    };
  }, [flowSeries, chartDomainLeft, chartDomainRight, chartPage]);
  const hoverMarkers = useMemo(() => {
    if (!hoveredFlowPoint || !hoverPosition) return [];
    if (chartPage === 'stock') {
      return [{
        id: 'stock',
        color: '#3b82f6',
        y: valueToChartY(hoveredFlowPoint.stock, chartDomains.main, hoverPosition.height),
      }];
    }
    return [
      {
        id: 'produced',
        color: '#06b6d4',
        y: valueToChartY(hoveredFlowPoint.produced, chartDomains.main, hoverPosition.height),
      },
      {
        id: 'consumed',
        color: '#f59e0b',
        y: valueToChartY(hoveredFlowPoint.consumed, chartDomains.main, hoverPosition.height),
      },
    ];
  }, [hoveredFlowPoint, hoverPosition, chartPage, chartDomains]);

  const updateChartHover = (event: React.PointerEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    const ratio = x / Math.max(1, rect.width);
    setHoverRatio(Math.max(0, Math.min(1, ratio)));
    setHoverPosition({
      x: Math.max(0, Math.min(rect.width, x)),
      y: Math.max(0, Math.min(rect.height, y)),
      width: rect.width,
      height: rect.height,
    });
  };

  const handleChartPointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    updateChartHover(event);
    if (liveDomainRight - oldestDomainRight <= FLOW_EDGE_EPSILON_SECONDS) return;
    const rect = event.currentTarget.getBoundingClientRect();
    chartDragRef.current = {
      active: true,
      pointerId: event.pointerId,
      startX: event.clientX,
      startDomainRight: chartDomainRight,
      width: Math.max(1, rect.width),
    };
    setIsChartDragging(true);
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handleChartPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    updateChartHover(event);
    const drag = chartDragRef.current;
    if (!drag.active || drag.pointerId !== event.pointerId) return;
    const deltaSeconds = ((event.clientX - drag.startX) / drag.width) * visibleSeconds;
    const next = Math.max(oldestDomainRight, Math.min(liveDomainRight, drag.startDomainRight - deltaSeconds));
    setHistoryDomainRight(next);
    if (liveDomainRight - next <= FLOW_EDGE_EPSILON_SECONDS) {
      setChartScrollMode('live');
    } else if (next - oldestDomainRight <= FLOW_EDGE_EPSILON_SECONDS) {
      setChartScrollMode('oldest');
    } else {
      setChartScrollMode('history');
    }
  };

  const endChartDrag = (event: React.PointerEvent<HTMLDivElement>) => {
    const drag = chartDragRef.current;
    if (!drag.active || drag.pointerId !== event.pointerId) return;
    chartDragRef.current = { active: false, pointerId: null, startX: 0, startDomainRight: 0, width: 1 };
    setIsChartDragging(false);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  const handleChartPointerLeave = () => {
    if (!chartDragRef.current.active) {
      setHoverRatio(null);
      setHoverPosition(null);
    }
  };

  const selectChartItem = (id: string) => {
    setSelectedItemId(id);
    setChartScrollMode('live');
    setHistoryDomainRight(null);
    setHoverRatio(null);
    setHoverPosition(null);
  };

  const resetChartItem = () => {
    setSelectedItemId(null);
    setChartScrollMode('live');
    setHistoryDomainRight(null);
    setHoverRatio(null);
    setHoverPosition(null);
  };

  useEffect(() => {
    if (chartScrollMode !== 'history') {
      return;
    }
    const next = Math.max(oldestDomainRight, Math.min(liveDomainRight, historyDomainRight ?? liveDomainRight));
    if (next !== historyDomainRight) {
      setHistoryDomainRight(next);
    }
    if (liveDomainRight - next <= FLOW_EDGE_EPSILON_SECONDS) {
      setChartScrollMode('live');
    } else if (next - oldestDomainRight <= FLOW_EDGE_EPSILON_SECONDS) {
      setChartScrollMode('oldest');
    }
  }, [chartScrollMode, historyDomainRight, liveDomainRight, oldestDomainRight]);

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
    const isWatched = watchedMap[id] ?? false;
    setStarPulseIds((prev) => ({ ...prev, [id]: true }));
    window.setTimeout(() => {
      setStarPulseIds((prev) => {
        const next = { ...prev };
        delete next[id];
        return next;
      });
    }, 220);

    if (isWatched) {
      setPendingRemovalIds((prev) => ({ ...prev, [id]: true }));
      window.setTimeout(() => {
        setWatchedMap((prev) => ({ ...prev, [id]: false }));
        setPendingRemovalIds((prev) => {
          const next = { ...prev };
          delete next[id];
          return next;
        });
        setSelectedItemId((prev) => prev === id ? null : prev);
        setChartScrollMode('live');
        setHistoryDomainRight(null);
        setHoverRatio(null);
        setHoverPosition(null);
      }, 220);
      return;
    }

    setWatchlistCollapsed(false);
    setEnteringWatchIds((prev) => ({ ...prev, [id]: true }));
    setPendingRemovalIds((prev) => {
      const next = { ...prev };
      delete next[id];
      return next;
    });
    setWatchedMap((prev) => ({ ...prev, [id]: true }));
    window.setTimeout(() => {
      setEnteringWatchIds((prev) => {
        const next = { ...prev };
        delete next[id];
        return next;
      });
    }, 30);
  };

  const changeOverviewViewMode = (mode: 'list' | 'grid') => {
    setOverviewResourceViewMode(mode);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem('resourceobserver:overviewResourceViewMode', mode);
    }
  };

  const changeOverviewSort = (field: OverviewSortField) => {
    if (overviewSort === field) {
      setOverviewSortDir((prev) => prev === 'desc' ? 'asc' : 'desc');
      return;
    }
    setOverviewSort(field);
    setOverviewSortDir('desc');
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
            icon={Activity}
            action={
              <div className="flex items-center gap-3">
                <SegmentedControl
                  value={chartPage}
                  onChange={(v) => setChartPage(v as ChartPageMode)}
                  items={[
                    { value: 'throughput', label: t('overview.chart.page.throughput') },
                    { value: 'stock', label: t('overview.chart.page.stock') },
                  ]}
                />
                <SegmentedControl
                  value={granularity}
                  onChange={(v) => setGranularity(v as HistoryRange)}
                  items={[
                    { value: 'detail', label: t('overview.chart.granularity.detail') },
                    { value: 'short', label: t('overview.chart.granularity.short') },
                    { value: 'medium', label: t('overview.chart.granularity.medium') },
                    { value: 'long', label: t('overview.chart.granularity.long') },
                  ]}
                />
                {selectedItemId ? (
                  <button
                    onClick={resetChartItem}
                    className="rounded-lg border border-slate-700 bg-slate-900 px-3 py-1 text-[10px] font-bold uppercase tracking-[0.2em] text-slate-400 transition-colors hover:text-white"
                  >
                    {t('overview.chart.reset')}
                  </button>
                ) : null}
                <button
                  onClick={() => setDebugOpen((v) => !v)}
                  className={`rounded-lg border px-3 py-1 text-[10px] font-bold uppercase tracking-[0.2em] transition-colors ${
                    debugOpen
                      ? 'border-emerald-500/60 bg-emerald-500/10 text-emerald-300'
                      : 'border-slate-700 bg-slate-900 text-slate-400 hover:text-white'
                  }`}
                  title="Web 高精度采样层调试面板"
                >
                  {history?.highPrecision ? 'HP·ON' : 'HP'}
                </button>
              </div>
            }
          />

          {debugOpen ? (
            <SamplerDebugPanel
              history={history ?? null}
              debug={samplerDebug ?? null}
              granularity={granularity}
              selectedItemId={selectedItemId ?? null}
            />
          ) : null}

          <div className="mt-6 flex flex-wrap gap-2">
            {chartPage === 'stock' ? (
              <LegendLabel color="bg-blue-500" label={t('overview.chart.stock')} />
            ) : (
              <>
                <LegendLabel color="bg-cyan-500" label={t('overview.chart.production')} />
                <LegendLabel color="bg-amber-500" label={t('overview.chart.consumption')} />
              </>
            )}
          </div>

          <div
            className={`relative mt-4 h-[320px] w-full select-none overflow-hidden ${isChartDragging ? 'cursor-grabbing' : 'cursor-grab'}`}
            style={{ touchAction: 'none' }}
            onPointerDown={handleChartPointerDown}
            onPointerMove={handleChartPointerMove}
            onPointerUp={endChartDrag}
            onPointerCancel={endChartDrag}
            onPointerLeave={handleChartPointerLeave}
          >
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
                  <linearGradient id="overviewStock" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.2} />
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" vertical={false} opacity={0.5} />
                <XAxis
                  dataKey="x"
                  type="number"
                  domain={[chartDomainLeft, chartDomainRight]}
                  allowDataOverflow
                  tick={false}
                  tickLine={false}
                  axisLine={false}
                  height={0}
                />
                <YAxis yAxisId="main" domain={chartDomains.main} stroke="#475569" fontSize={11} tickLine={false} axisLine={false} />
                {chartPage === 'stock' ? (
                  <Area yAxisId="main" type="monotone" dataKey="stock" name={t('overview.chart.stock')} stroke="#3b82f6" strokeWidth={2.5} fill="url(#overviewStock)" isAnimationActive={false} />
                ) : (
                  <>
                    <Area yAxisId="main" type="monotone" dataKey="produced" name={t('overview.chart.production')} stroke="#06b6d4" strokeWidth={2.5} fill="url(#overviewProd)" isAnimationActive={false} />
                    <Area yAxisId="main" type="monotone" dataKey="consumed" name={t('overview.chart.consumption')} stroke="#f59e0b" strokeWidth={2.5} fill="url(#overviewCons)" isAnimationActive={false} />
                  </>
                )}
              </AreaChart>
            </ResponsiveContainer>
            {hoveredFlowPoint && hoverPosition ? (
              <div className="pointer-events-none absolute inset-0 z-10">
                <div
                  className="absolute top-0 h-full w-px bg-slate-700/80"
                  style={{ left: hoverPosition.x }}
                />
                {hoverMarkers.map((marker) => (
                  <span
                    key={marker.id}
                    className="absolute h-2.5 w-2.5 rounded-full border-2 border-slate-950 shadow-lg"
                    style={{
                      left: hoverPosition.x,
                      top: marker.y,
                      backgroundColor: marker.color,
                      transform: 'translate(-50%, -50%)',
                    }}
                  />
                ))}
              </div>
            ) : null}
            <div className="pointer-events-none absolute left-3 top-3 z-10 rounded-lg border border-slate-800 bg-slate-950/80 px-3 py-2 shadow-xl">
              <p className="text-[10px] font-bold uppercase tracking-[0.18em] text-cyan-300">{chartScopeTitle}</p>
              <p className="mt-0.5 max-w-[260px] truncate text-xs font-semibold text-slate-200">{chartScopeDetail}</p>
            </div>
            <div
              className="pointer-events-none absolute inset-y-0 right-0 bg-gradient-to-l from-slate-950 via-slate-950/80 to-transparent"
              style={{ width: FLOW_TAIL_MASK_PX }}
            />
            <div
              className="pointer-events-none absolute inset-y-0 bg-gradient-to-r from-slate-950 via-slate-950/70 to-transparent"
              style={{
                left: FLOW_PLOT_LEFT_INSET_PX,
                width: FLOW_HEAD_MASK_PX,
                opacity: chartScrollMode === 'oldest' ? 1 : 0.35,
                transition: 'opacity 240ms ease',
              }}
            />
            {flowSeries.length === 0 ? (
              <div className="pointer-events-none absolute inset-0 flex items-center justify-center text-xs font-semibold text-slate-500">
                {t('overview.chart.empty')}
              </div>
            ) : null}
            {hoveredFlowPoint && hoverPosition ? (
              <div
                className="pointer-events-none absolute z-10"
                style={{
                  left: hoverPosition.x,
                  top: hoverPosition.y,
                  transform: [
                    hoverPosition.x + FLOW_TOOLTIP_WIDTH + FLOW_TOOLTIP_GAP > hoverPosition.width
                      ? `translateX(calc(-100% - ${FLOW_TOOLTIP_GAP}px))`
                      : `translateX(${FLOW_TOOLTIP_GAP}px)`,
                    hoverPosition.y + FLOW_TOOLTIP_HEIGHT + FLOW_TOOLTIP_GAP > hoverPosition.height
                      ? `translateY(calc(-100% - ${FLOW_TOOLTIP_GAP}px))`
                      : `translateY(${FLOW_TOOLTIP_GAP}px)`,
                  ].join(' '),
                }}
              >
                <FlowTooltip
                  point={hoveredFlowPoint}
                  page={chartPage}
                  productionLabel={t('overview.chart.production')}
                  consumptionLabel={t('overview.chart.consumption')}
                  stockLabel={t('overview.chart.stock')}
                />
              </div>
            ) : null}
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
        <Card className="xl:col-span-4 p-5">
          <SectionHeader
            title={t('overview.snapshot.title')}
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
            subtitle={watchedResources.length > 0 ? t('overview.watchlist.count', { count: watchedResources.length }) : t('overview.watchlist.emptyTitle')}
            icon={Star}
            action={
              <button
                onClick={() => setWatchlistCollapsed((prev) => !prev)}
                className="rounded-lg border border-slate-800 bg-slate-950/70 p-1.5 text-slate-500 transition-colors hover:text-white"
                title={watchlistCollapsed ? t('overview.watchlist.expand') : t('overview.watchlist.collapse')}
              >
                {watchlistCollapsed ? <ChevronDown size={15} /> : <ChevronUp size={15} />}
              </button>
            }
          />

          <div
            className={`mt-4 overflow-hidden transition-all duration-300 ${
              watchlistCollapsed ? 'max-h-0 opacity-0' : 'max-h-[420px] opacity-100'
            }`}
          >
            {watchedResources.length === 0 ? (
              <div className="min-h-[168px] rounded-xl border border-dashed border-slate-800 bg-slate-950/50 p-5">
                <div className="flex h-full min-h-[128px] flex-col items-center justify-center text-center">
                  <div className="flex h-11 w-11 items-center justify-center rounded-full border border-slate-800 bg-slate-900 text-slate-600">
                    <Star size={18} />
                  </div>
                  <p className="mt-3 text-sm font-bold text-slate-200">{t('overview.watchlist.emptyTitle')}</p>
                  <p className="mt-1 max-w-md text-xs leading-5 text-slate-500">{t('overview.watchlist.emptyHint')}</p>
                </div>
              </div>
            ) : (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {watchedResources.slice(0, 4).map((resource) => {
                  const net = resource.produced - resource.consumed;
                  const removing = pendingRemovalIds[resource.id] ?? false;
                  const entering = enteringWatchIds[resource.id] ?? false;
                  return (
                    <div
                      key={resource.id}
                      role="button"
                      tabIndex={0}
                      onClick={() => selectChartItem(resource.id)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          selectChartItem(resource.id);
                        }
                      }}
                      className={`w-full rounded-xl border p-4 text-left transition-all duration-[220ms] ${
                        removing || entering ? 'translate-y-2 scale-95 opacity-0' : 'translate-y-0 scale-100 opacity-100'
                      } ${
                        selectedResource?.id === resource.id
                          ? 'border-cyan-500/30 bg-cyan-500/10 shadow-[0_0_24px_rgba(6,182,212,0.08)]'
                          : 'border-slate-800 bg-slate-950/70 hover:border-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between gap-3">
                        <div className="flex min-w-0 items-center gap-3">
                          <ItemIcon item={{ id: resource.id, icon: resource.icon, accent: resource.accent }} size={16} />
                          <div className="min-w-0">
                            <p className="truncate text-sm font-bold text-white" title={resource.name}>{resource.name}</p>
                            <p className="truncate text-[10px] uppercase tracking-[0.18em] text-slate-500" title={resource.id}>{resource.id}</p>
                          </div>
                        </div>
                        <button
                          onClick={(event) => {
                            event.stopPropagation();
                            toggleWatch(resource.id);
                          }}
                          className={`shrink-0 rounded-full p-1 transition-all duration-200 ${
                            starPulseIds[resource.id] ? 'scale-125 text-amber-300 drop-shadow-[0_0_10px_rgba(251,191,36,0.75)]' : 'scale-100 text-amber-400'
                          }`}
                        >
                          <Star size={16} fill="currentColor" />
                        </button>
                      </div>
                      <div className="mt-3 flex items-center justify-between text-xs">
                        <span className={`${net >= 0 ? 'text-emerald-400' : 'text-rose-400'} font-mono font-bold`}>
                          {net >= 0 ? '+' : ''}{net.toFixed(1)}/m
                        </span>
                        <span className="font-mono text-slate-500">{resource.stock.toLocaleString()}</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </Card>
      </div>

      <Card className="overflow-hidden">
        <div className="border-b border-slate-800 bg-slate-900/30 p-5">
          <SectionHeader
            title={t('overview.table.title')}
            icon={ArrowRightLeft}
            action={
              <IconSegmentedControl
                value={overviewResourceViewMode}
                onChange={(value) => changeOverviewViewMode(value as 'list' | 'grid')}
                items={[
                  { value: 'list', label: t('storage.view.list'), icon: List },
                  { value: 'grid', label: t('storage.view.grid'), icon: LayoutGrid },
                ]}
              />
            }
          />
        </div>
        {overviewResourceViewMode === 'grid' ? (
          <div className="grid grid-cols-2 gap-3 p-4 md:grid-cols-3 xl:grid-cols-5">
            {sortedResources.map((resource) => {
              const net = resource.produced - resource.consumed;
              return (
                <HoverCard
                  key={resource.id}
                  width={260}
                  trigger={({ ref, onMouseEnter, onMouseLeave }) => (
                    <div
                      ref={ref}
                      onMouseEnter={onMouseEnter}
                      onMouseLeave={onMouseLeave}
                      onClick={() => selectChartItem(resource.id)}
                      className={`group relative cursor-pointer rounded-xl border p-4 transition-all hover:-translate-y-0.5 hover:border-cyan-500/30 ${
                        selectedResource?.id === resource.id ? 'border-cyan-500/30 bg-cyan-500/10' : 'border-slate-800 bg-slate-950/70'
                      }`}
                    >
                      <div className="flex items-start justify-between gap-3">
                        <ItemIcon item={{ id: resource.id, icon: resource.icon, accent: resource.accent }} />
                        <button
                          onClick={(event) => {
                            event.stopPropagation();
                            toggleWatch(resource.id);
                          }}
                          className={`rounded-full p-1 transition-all duration-200 ${
                            starPulseIds[resource.id] ? 'scale-125 text-amber-300 drop-shadow-[0_0_10px_rgba(251,191,36,0.75)]' :
                            (watchedMap[resource.id] ?? false) ? 'scale-100 text-amber-400' : 'scale-100 text-slate-700 hover:text-slate-400'
                          }`}
                        >
                          <Star size={15} fill={(watchedMap[resource.id] ?? false) ? 'currentColor' : 'none'} />
                        </button>
                      </div>
                      <p className="mt-3 truncate text-sm font-bold text-white" title={resource.name}>{resource.name}</p>
                      <div className="mt-3 flex items-center justify-between text-xs">
                        <span className="font-mono text-slate-300">{resource.stock.toLocaleString()}</span>
                        <span className={`font-mono font-bold ${net >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                          {net >= 0 ? '+' : ''}{net.toFixed(1)}/m
                        </span>
                      </div>
                    </div>
                  )}
                >
                  {() => (
                    <>
                      <p className="truncate text-xs font-bold text-white">{resource.name}</p>
                      <p className="mt-1 break-all text-[10px] font-mono text-slate-500">{resource.id}</p>
                      <div className="mt-3 grid grid-cols-2 gap-2 text-[11px]">
                        <OverviewTooltipMetric label={t('overview.table.stock')} value={resource.stock.toLocaleString()} />
                        <OverviewTooltipMetric label={t('overview.table.production')} value={`${resource.produced.toFixed(1)}/m`} tone="text-cyan-400" />
                        <OverviewTooltipMetric label={t('overview.table.consumption')} value={`${resource.consumed.toFixed(1)}/m`} tone="text-amber-400" />
                        <OverviewTooltipMetric label={t('overview.table.balance')} value={`${net >= 0 ? '+' : ''}${net.toFixed(1)}/m`} tone={net >= 0 ? 'text-emerald-400' : 'text-rose-400'} />
                      </div>
                    </>
                  )}
                </HoverCard>
              );
            })}
          </div>
        ) : (
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-900/20 text-[10px] font-bold uppercase tracking-[0.18em] text-slate-500">
                <th className="p-4 w-8"></th>
                <th className="p-4">{t('overview.table.resource')}</th>
                <th className="p-4 text-right">
                  <SortButton
                    label={t('overview.table.stock')}
                    active={overviewSort === 'stock'}
                    dir={overviewSortDir}
                    onClick={() => changeOverviewSort('stock')}
                  />
                </th>
                <th className="p-4 text-right">
                  <SortButton
                    label={t('overview.table.production')}
                    active={overviewSort === 'produced'}
                    dir={overviewSortDir}
                    onClick={() => changeOverviewSort('produced')}
                  />
                </th>
                <th className="p-4 text-right">
                  <SortButton
                    label={t('overview.table.consumption')}
                    active={overviewSort === 'consumed'}
                    dir={overviewSortDir}
                    onClick={() => changeOverviewSort('consumed')}
                  />
                </th>
                <th className="p-4 min-w-[220px]">{t('overview.table.balance')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/50">
              {sortedResources.map((resource) => {
                const net = resource.produced - resource.consumed;
                return (
                  <tr
                    key={resource.id}
                    onClick={() => selectChartItem(resource.id)}
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
                        className={`rounded-full p-1 transition-all duration-200 ${
                          starPulseIds[resource.id] ? 'scale-125 text-amber-300 drop-shadow-[0_0_10px_rgba(251,191,36,0.75)]' :
                          (watchedMap[resource.id] ?? false) ? 'scale-100 text-amber-400' : 'scale-100 text-slate-700 hover:text-slate-400'
                        }`}
                      >
                        <Star size={16} fill={(watchedMap[resource.id] ?? false) ? 'currentColor' : 'none'} />
                      </button>
                    </td>
                    <td className="p-4">
                      <div className="flex items-center gap-3">
                        <ItemIcon item={{ id: resource.id, icon: resource.icon, accent: resource.accent }} />
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-bold text-white" title={resource.name}>{resource.name}</p>
                          <p className="truncate text-[10px] font-mono text-slate-500" title={resource.id}>{resource.id}</p>
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
        )}
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

function SortButton({
  label,
  active,
  dir,
  onClick,
}: {
  label: string;
  active: boolean;
  dir: 'asc' | 'desc';
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`font-bold uppercase tracking-[0.18em] transition-colors ${
        active ? 'text-cyan-300' : 'text-slate-500 hover:text-slate-300'
      }`}
    >
      {label}{active ? (dir === 'desc' ? ' ↓' : ' ↑') : ''}
    </button>
  );
}

function OverviewTooltipMetric({ label, value, tone = 'text-slate-300' }: { label: string; value: string; tone?: string }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/70 px-2 py-1.5">
      <p className="text-[9px] font-bold uppercase tracking-[0.16em] text-slate-600">{label}</p>
      <p className={`mt-0.5 truncate font-mono font-bold ${tone}`} title={value}>{value}</p>
    </div>
  );
}


function SamplerDebugPanel({
  history,
  debug,
  granularity,
  selectedItemId,
}: {
  history: import('../lib/api').ChartHistoryResponse | null;
  debug: import('../lib/api').SamplerDebugResponse | null;
  granularity: import('../lib/api').HistoryRange;
  selectedItemId: string | null;
}) {
  const hp = Boolean(history?.highPrecision);
  const interval = debug?.requestedIntervalTicks ?? -1;
  const intervalLabel = interval > 0 ? `${interval} tick / ${(interval * 0.05).toFixed(2)}s` : 'idle (10 tick fallback)';
  const remainingS = debug ? Math.max(0, Math.round(debug.demandRemainingMs / 100) / 10) : 0;
  return (
    <div className="mt-4 rounded-xl border border-emerald-500/30 bg-slate-950/80 p-4 text-[11px] text-slate-300">
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <span className="rounded bg-emerald-500/20 px-2 py-0.5 font-bold uppercase tracking-[0.18em] text-emerald-300">
          Sampler Debug
        </span>
        <span>response.highPrecision = <b className={hp ? 'text-emerald-300' : 'text-amber-300'}>{String(hp)}</b></span>
        <span>range = <b className="text-cyan-300">{granularity}</b></span>
        <span>scope = <b className="text-cyan-300">{selectedItemId ? `item:${selectedItemId}` : 'global'}</b></span>
      </div>
      {!debug ? (
        <p className="text-slate-500">loading sampler diagnostics…</p>
      ) : (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-2 md:grid-cols-4">
            <DebugStat label="interval" value={intervalLabel} />
            <DebugStat label="demand TTL" value={`${remainingS.toFixed(1)} s`} />
            <DebugStat label="latest bucket" value={String(debug.latestBucket)} />
            <DebugStat label="bucket ticks" value={`${debug.bucketTicks} (${(debug.bucketTicks * 0.05).toFixed(2)}s)`} />
            <DebugStat label="buffer buckets" value={String(debug.bufferBuckets)} />
            <DebugStat label="min valid" value={String(debug.minValidBuckets)} />
            <DebugStat label="global demand" value={debug.globalDemandActive ? 'ON' : 'off'} tone={debug.globalDemandActive ? 'text-emerald-300' : 'text-slate-500'} />
            <DebugStat label="item demand" value={debug.itemDemandActive ? 'ON' : 'off'} tone={debug.itemDemandActive ? 'text-emerald-300' : 'text-slate-500'} />
          </div>
          {debug.networks.length === 0 ? (
            <p className="text-slate-500">no AE2 network buffers (sampler hasn't recorded snapshots yet)</p>
          ) : (
            debug.networks.map((net) => (
              <div key={net.networkId} className="rounded-lg border border-slate-800 bg-slate-900/60 p-3">
                <div className="mb-2 flex flex-wrap items-center gap-3 text-[10px] uppercase tracking-[0.16em] text-slate-400">
                  <span className="text-cyan-300">{net.networkId}</span>
                  <span>valid = <b className="text-emerald-300">{net.validBuckets}</b></span>
                  <span>samples = <b className="text-slate-200">{net.totalSamples}</b></span>
                  <span>last gameTime = <b className="text-slate-200">{net.lastSampleGameTime}</b></span>
                  <span>snapshot keys = <b className="text-slate-200">{net.previousSnapshotSize}</b></span>
                </div>
                {net.recentBuckets.length === 0 ? (
                  <p className="text-slate-500">no recent buckets</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full font-mono text-[10px]">
                      <thead>
                        <tr className="text-left text-slate-500">
                          <th className="pr-3">bucket</th>
                          <th className="pr-3">samples</th>
                          <th className="pr-3">obsTicks</th>
                          <th className="pr-3 text-cyan-300">prod/min</th>
                          <th className="pr-3 text-amber-300">cons/min</th>
                          <th>stock</th>
                        </tr>
                      </thead>
                      <tbody>
                        {net.recentBuckets.map((rb) => (
                          <tr key={rb.bucket} className="border-t border-slate-800">
                            <td className="py-0.5 pr-3">{rb.bucket}</td>
                            <td className="py-0.5 pr-3">{rb.sampleCount}</td>
                            <td className="py-0.5 pr-3">{rb.observedTicks}</td>
                            <td className="py-0.5 pr-3 text-cyan-300">{rb.producedPerMinute.toFixed(1)}</td>
                            <td className="py-0.5 pr-3 text-amber-300">{rb.consumedPerMinute.toFixed(1)}</td>
                            <td className="py-0.5">{rb.stock}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}

function DebugStat({ label, value, tone = 'text-slate-200' }: { label: string; value: string; tone?: string }) {
  return (
    <div className="rounded-md border border-slate-800 bg-slate-900/60 px-2 py-1.5">
      <p className="text-[9px] font-bold uppercase tracking-[0.16em] text-slate-500">{label}</p>
      <p className={`mt-0.5 truncate font-mono ${tone}`} title={value}>{value}</p>
    </div>
  );
}

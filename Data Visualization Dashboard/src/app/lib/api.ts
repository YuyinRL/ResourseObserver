/**
 * Resource Observer Web Dashboard API client.
 * 统一封装后端 REST 端点访问 + TypeScript 类型定义。
 */

import { authHeaders, notifyUnauthorized, WhoAmIResponse } from './auth';

// ===== Types: 与 Java 侧 JsonWriter 输出保持一致 =====

export interface Meta {
  modid: string;
  apiVersion: number;
  integrations: {
    ae2: boolean;
    fluxNetworks: boolean;
    mekanism: boolean;
  };
  endpoints: Record<string, string>;
  /** 物品图标接口是否可用（仅集成端单机有效；专用服务端为 false） */
  iconsAvailable?: boolean;
}

export interface HealthStatus {
  status: string;
  modid: string;
  tickCount: number;
  singleplayer: boolean;
}

export interface ObserverSummary {
  id: string;
  dimension: string;
  x: number;
  y: number;
  z: number;
  bound: boolean;
  bindingCount: number;
  displayName: string;
}

export interface ObserverListResponse {
  observers: ObserverSummary[];
  count: number;
}

export type StorageAlertLevel = 'GREEN' | 'YELLOW' | 'RED';

export interface BindingItem {
  id: string;
  amount: number;
  production: number;
  consumption: number;
  net: number;
  capacity?: number;
  remainingMinutes?: number;
  networkId?: string;
  /** 翻译键，如 "item.minecraft.iron_ingot"（后端解析） */
  translationKey?: string;
  /** 服务端语言下的显示名，如 "Iron Ingot" / "铁锭" */
  displayName?: string;
  localAmount?: number;
  globalAmount?: number;
  delta?: number;
  alertLevel?: StorageAlertLevel;
  groupKey?: string;
  burnRatePerMin?: number;
  estimatedBufferText?: string;
  bufferRatio?: number;
  iconSprite?: string;
}

export interface StorageKpi {
  label: string;
  value: string;
  status: 'POSITIVE' | 'NEGATIVE' | 'WARNING' | 'NEUTRAL';
}

export interface StorageUsageSegment {
  groupKey: string;
  displayName: string;
  percentage: number;
  color: string;
}

export interface StorageNodeSummary {
  nodeId: string;
  displayName: string;
  networkType: string;
  iconSprite?: string;
  itemCount: number;
  capacityRatio: number;
  selected: boolean;
  usedFormatted: string;
  totalFormatted: string;
  statusKey: string;
  statusAlert: boolean;
  coordinatesText?: string | null;
}

export interface CellCapacity {
  itemUsedBytes: number;
  itemTotalBytes: number;
  itemUsedTypes: number;
  itemTotalTypes: number;
  itemUsedUnits: number;
  itemMaxUnits: number;
  fluidUsedBytes: number;
  fluidTotalBytes: number;
  fluidUsedUnits: number;
  fluidMaxUnits: number;
  reliable: boolean;
  available: boolean;
}

export interface FluxExternalRef {
  extId: string;
  displayName: string;
  stored: number;
  capacity: number;
  maxAcceptPerTick: number;
}

export interface FluxDeviceSnapshot {
  deviceType: string;
  customName: string;
  transferChange: number;
  transferBuffer: number;
  rawLimit: number;
  maxTransferLimit: number;
  maxEnergyStorage: number;
  posKey: string;
  externalEnergyStored: number;
  externalEnergyCapacity: number;
  externalRefs?: FluxExternalRef[];
}

export interface FluxSnapshot {
  totalEnergy: number;
  totalMaxEnergyStorage: number;
  totalBuffer?: number;
  energyInput: number;
  energyOutput: number;
  networkName: string;
  networkId?: number;
  networkColor?: number;
  plugCount?: number;
  pointCount?: number;
  storageCount?: number;
  controllerCount?: number;
  deviceCount?: number;
  devices?: FluxDeviceSnapshot[];
}

export type OverviewKpiType = 'PRODUCTION' | 'CONSUMPTION' | 'STORAGE' | 'BALANCE';
export type OverviewKpiStatus = 'POSITIVE' | 'NEGATIVE' | 'WARNING' | 'NEUTRAL';
export type OverviewValueKind = 'FLOW_PER_MINUTE' | 'PERCENT' | 'BALANCE_SCORE' | 'COUNT';

export interface OverviewChannelPayload {
  labelKey: string;
  recentRate: number;
  previousRate: number;
  trendPercent: number;
  trendAvailable: boolean;
  available: boolean;
}

export interface OverviewPayload {
  header: {
    titleKey: string;
    subtitleKey: string;
    linkStatusKey: string;
    bindingCount: number;
    hasAe2Binding: boolean;
    hasFluxBinding: boolean;
  };
  kpis: Array<{
    type: OverviewKpiType;
    labelKey: string;
    valueRaw: number | null;
    valueKind: OverviewValueKind;
    trendKey: string | null;
    trendArg: string | null;
    status: OverviewKpiStatus;
    iconSprite?: string | null;
  }>;
  kpiDetails: Array<{
    type: OverviewKpiType;
    titleKey: string;
    hintKey: string;
    status: OverviewKpiStatus;
    itemChannel: OverviewChannelPayload;
    fluidChannel: OverviewChannelPayload;
  }>;
  storageDetail: {
    hasAe2Binding: boolean;
    diskReliable: boolean;
    externalReliable: boolean;
    hintKey: string;
    diskItem: OverviewStorageChannelPayload;
    diskFluid: OverviewStorageChannelPayload;
    externalItem: OverviewStorageChannelPayload;
    externalFluid: OverviewStorageChannelPayload;
  };
  chartWindow: string;
  chartScope: string;
  chartScopeItemId: string | null;
  chartSeries: OverviewChartPointPayload[];
  energyChartSeries: OverviewChartPointPayload[];
  watchlistItems: OverviewWatchlistItemPayload[];
  tableGroups: OverviewTableGroupPayload[];
  uiState: OverviewUiStatePayload;
}

export interface OverviewStorageChannelPayload {
  labelKey: string;
  usedBytes: number;
  totalBytes: number;
  usedTypes: number;
  totalTypes: number;
  available: boolean;
}

export interface OverviewChartPointPayload {
  slotIndex: number;
  bucket: number;
  production: number;
  consumption: number;
  net: number;
  stock: number;
  hasFlow: boolean;
  hasStock: boolean;
  sampleCount: number;
}

export interface OverviewWatchlistItemPayload {
  itemId: string;
  displayName: string;
  netPerMinute: number;
  stock: number;
  iconSprite?: string | null;
}

export interface OverviewTableRowPayload {
  itemId: string;
  displayName: string;
  groupKey: string;
  production: number;
  consumption: number;
  net: number;
  stock: number;
  critical: boolean;
  starred: boolean;
  iconSprite?: string | null;
}

export interface OverviewTableGroupPayload {
  key: string;
  displayName: string;
  rows: OverviewTableRowPayload[];
}

export interface OverviewUiStatePayload {
  groupFilterKey: string;
  groups: Array<{ key: string; displayName: string; systemGroup: boolean }>;
  sortMode: string;
  sortDesc: boolean;
  statusFilter: string;
  watchlistLimit: number;
}

export type PowerAlertLevel = 'NORMAL' | 'WARNING' | 'CRITICAL';
export type PowerLoadCategory = 'MINING' | 'ASSEMBLY' | 'LOGISTICS' | 'OTHER';

export interface PowerDevicePayload {
  nodeId: string;
  displayName: string;
  category: PowerLoadCategory;
  modName: string;
  energyPerTick: number;
  storedEnergy: number;
  maxCapacity: number;
  usageRatio: number;
  alertLevel: PowerAlertLevel;
}

export interface PowerKpiPayload {
  labelKey: string;
  value: string;
  status: 'POSITIVE' | 'NEGATIVE' | 'WARNING' | 'NEUTRAL';
}

export interface PowerLoadSegmentPayload {
  category: PowerLoadCategory;
  displayNameKey: string;
  percentage: number;
}

export interface PowerOverloadAlertPayload {
  nodeId: string;
  displayName: string;
  alertLevel: PowerAlertLevel;
  throughputLoss: number;
  descriptionKey: string;
  descriptionArgs: unknown[];
}

export interface PowerConsumerPayload {
  deviceName: string;
  modName: string;
  consumptionPerTick: number;
  percentage: number;
  supplyRatio: number;
  paletteIndex: number;
  count: number;
}

export interface PowerPayload {
  displayName: string;
  totalInputPerTick: number;
  totalOutputPerTick: number;
  totalStored: number;
  totalCapacity: number;
  timestampMs: number;
  devices: PowerDevicePayload[];
  kpiCards: PowerKpiPayload[];
  loadSegments: PowerLoadSegmentPayload[];
  overloadAlerts: PowerOverloadAlertPayload[];
  overloadInfo: {
    headroomPercent: number;
    reservePerTick: number;
  };
  consumers: PowerConsumerPayload[];
}

export interface ObserverBinding {
  networkType: string;
  networkId: string;
  targetBlockId: string;
  currentValue: number;
  capacity: number;
  totalProduced: number;
  totalConsumed: number;
  items?: BindingItem[];
  kpis?: StorageKpi[];
  usageSegments?: StorageUsageSegment[];
  nodeSummary?: StorageNodeSummary | null;
  cellCapacity?: CellCapacity;
  flux?: FluxSnapshot;
}

export interface ObserverDetail extends ObserverSummary {
  overview?: OverviewPayload;
  power?: PowerPayload;
  bindings: ObserverBinding[];
}

export type ItemSortField = 'amount' | 'production' | 'consumption' | 'net' | 'name' | 'remainingMinutes';
export type SortDirection = 'asc' | 'desc';

export interface ObserverItemsResponse {
  total: number;
  offset: number;
  limit: number;
  sort: ItemSortField;
  dir: SortDirection;
  items: BindingItem[];
}

export interface CraftingBinding {
  networkId: string;
  storage: {
    cpuCount: number;
    busyCpuCount: number;
    totalStorageBytes: number;
    totalCoProcessors: number;
    reliable: boolean;
  };
  jobs: Array<{
    cpuName: string;
    jobId: string;
    outputItemId: string;
    outputDisplayName: string;
    totalAmount: number;
    remainingAmount: number;
    busy: boolean;
    storageBytes?: number;
    coProcessors?: number;
    progressFraction?: number;
    elapsedMillis?: number;
    treeId?: string;
  }>;
  craftables: Array<{
    itemId: string;
    displayName: string;
    entryType?: 'ITEM' | 'FLUID';
  }>;
}

export interface CraftingResponse {
  bindings: CraftingBinding[];
}

export interface CraftingPlanStack {
  itemId: string;
  displayName: string;
  entryType?: 'ITEM' | 'FLUID';
  amount: number;
}

export interface CraftingPlanCpu {
  name: string;
  storageBytes: number;
  coProcessors: number;
  busy: boolean;
}

export interface CraftingTreeNode {
  itemId: string;
  displayName: string;
  entryType?: 'ITEM' | 'FLUID';
  requiredAmount: number;
  perExecOutAmount: number;
  timesExecuted: number;
  isMissing: boolean;
  isLoop: boolean;
  truncated: boolean;
  children: CraftingTreeNode[];
}

export interface CraftingPlanResult {
  ok: boolean;
  status: string;
  message: string;
  planId: string;
  simulation: boolean;
  bytes: number;
  finalOutputItemId: string;
  finalOutputDisplayName: string;
  finalOutputAmount: number;
  usedItems: CraftingPlanStack[];
  missingItems: CraftingPlanStack[];
  emittedItems: CraftingPlanStack[];
  patternTimes: CraftingPlanStack[];
  cpus: CraftingPlanCpu[];
  treeId?: string;
  tree?: CraftingTreeNode | null;
}

export interface CraftingOrderResult {
  ok: boolean;
  status: string;
  message: string;
  linkId: string;
}

export interface ChartHistoryPoint {
  /** Bucket 索引（0 = 最早） */
  t: number;
  /** 绝对 bucket 编号（服务端 gameTime / bucketTicks） */
  bucket?: number;
  /** 图表绝对时间（秒），用于连续滚动窗口 */
  x?: number;
  /** 该桶产出（每分钟单位） */
  produced: number;
  /** 该桶消耗（每分钟单位） */
  consumed: number;
  /** 该桶净变化（每分钟单位） */
  net?: number;
  /** 该桶库存快照 */
  stock?: number;
  hasFlow?: boolean;
  hasStock?: boolean;
  sampleCount?: number;
  highPrecision?: boolean;
}

export type HistoryRange = 'detail' | 'short' | 'medium' | 'long';

export interface SamplerDebugRecentBucket {
  bucket: number;
  sampleCount: number;
  observedTicks: number;
  producedPerMinute: number;
  consumedPerMinute: number;
  stock: number;
}

export interface SamplerDebugNetwork {
  networkId: string;
  validBuckets: number;
  totalSamples: number;
  lastSampleGameTime: number;
  previousSnapshotSize: number;
  recentBuckets: SamplerDebugRecentBucket[];
}

export interface SamplerDebugResponse {
  active: boolean;
  requestedIntervalTicks: number;
  globalDemandActive: boolean;
  itemDemandActive: boolean;
  demandRemainingMs: number;
  latestBucket: number;
  bufferBuckets: number;
  bucketTicks: number;
  minValidBuckets: number;
  gameTime: number;
  serverTimeMs: number;
  networks: SamplerDebugNetwork[];
}

export interface ChartHistoryResponse {
  range: HistoryRange;
  bucketCount: number;
  visibleBucketCount?: number;
  bufferMultiplier?: number;
  /** 每个 bucket 时长（秒） */
  bucketSeconds: number;
  /** 服务端 gameTime（tick），用于前端连续时间轴 */
  gameTime?: number;
  latestBucket?: number;
  serverTimeMs?: number;
  highPrecision?: boolean;
  points: ChartHistoryPoint[];
  scope: 'global' | 'item';
  itemId?: string;
}

async function apiGet<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    credentials: 'include',
    ...init,
    headers: authHeaders({ Accept: 'application/json', ...(init?.headers ?? {}) }),
  });
  if (res.status === 401) {
    notifyUnauthorized();
    throw new Error(`GET ${url} unauthorized`);
  }
  if (!res.ok) {
    throw new Error(`GET ${url} failed: ${res.status} ${res.statusText}`);
  }
  return res.json() as Promise<T>;
}

async function authedFetch(url: string, init?: RequestInit): Promise<Response> {
  const res = await fetch(url, {
    credentials: 'include',
    ...init,
    headers: authHeaders(init?.headers),
  });
  if (res.status === 401) {
    notifyUnauthorized();
  }
  return res;
}

export const api = {
  health: (): Promise<HealthStatus> => apiGet('/api/health'),
  meta: (): Promise<Meta> => apiGet('/api/meta'),
  whoami: (): Promise<WhoAmIResponse> => apiGet('/api/auth/whoami'),
  observers: (): Promise<ObserverListResponse> => apiGet('/api/observers'),
  observerDetail: (id: string): Promise<ObserverDetail> =>
    apiGet(`/api/observers/${id}`),
  observerCrafting: (id: string): Promise<CraftingResponse> =>
    apiGet(`/api/observers/${id}/crafting`),
  planCraftingOrder: async (
    id: string,
    body: { networkId: string; itemId: string; amount: number },
  ): Promise<CraftingPlanResult> => {
    const res = await authedFetch(`/api/observers/${id}/crafting/plan`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body),
    });
    const text = await res.text();
    let parsed: Partial<CraftingPlanResult> = {};
    try { parsed = text ? JSON.parse(text) : {}; } catch { /* ignore */ }
    return {
      ok: parsed.ok ?? false,
      status: parsed.status ?? (res.ok ? 'SUCCESS' : 'SUBMIT_FAILED'),
      message: parsed.message ?? '',
      planId: parsed.planId ?? '',
      simulation: parsed.simulation ?? false,
      bytes: parsed.bytes ?? 0,
      finalOutputItemId: parsed.finalOutputItemId ?? '',
      finalOutputDisplayName: parsed.finalOutputDisplayName ?? '',
      finalOutputAmount: parsed.finalOutputAmount ?? 0,
      usedItems: parsed.usedItems ?? [],
      missingItems: parsed.missingItems ?? [],
      emittedItems: parsed.emittedItems ?? [],
      patternTimes: parsed.patternTimes ?? [],
      cpus: parsed.cpus ?? [],
      treeId: parsed.treeId ?? '',
      tree: parsed.tree ?? null,
    };
  },
  confirmCraftingOrder: async (
    id: string,
    planId: string,
    cpu?: string | null,
  ): Promise<CraftingOrderResult> => {
    const res = await authedFetch(`/api/observers/${id}/crafting/confirm`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ planId, cpu: cpu ?? '' }),
    });
    const text = await res.text();
    let parsed: Partial<CraftingOrderResult> = {};
    try { parsed = text ? JSON.parse(text) : {}; } catch { /* ignore */ }
    return {
      ok: parsed.ok ?? res.ok,
      status: parsed.status ?? (res.ok ? 'SUCCESS' : 'SUBMIT_FAILED'),
      message: parsed.message ?? '',
      linkId: parsed.linkId ?? '',
    };
  },
  cancelCraftingPlan: async (id: string, planId: string): Promise<void> => {
    if (!planId) return;
    try {
      await authedFetch(`/api/observers/${id}/crafting/cancel`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ planId }),
      });
    } catch { /* ignore */ }
  },
  fetchCraftingTree: async (id: string, treeId: string): Promise<CraftingTreeNode | null> => {
    if (!treeId) return null;
    try {
      const res = await authedFetch(`/api/observers/${id}/crafting/tree`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ treeId }),
      });
      if (!res.ok) return null;
      const data = await res.json();
      return (data?.root ?? null) as CraftingTreeNode | null;
    } catch {
      return null;
    }
  },
  placeCraftingOrder: async (
    id: string,
    body: { networkId: string; itemId: string; amount: number },
  ): Promise<CraftingOrderResult> => {
    // 兼容旧调用：先 plan 后 confirm。即使 simulation 也尝试 confirm（AE2 允许挂起任务）。
    const plan = await api.planCraftingOrder(id, body);
    if (!plan.planId) {
      return {
        ok: false,
        status: plan.status || 'SUBMIT_FAILED',
        message: plan.message || '',
        linkId: '',
      };
    }
    return api.confirmCraftingOrder(id, plan.planId);
  },
  observerItems: (
    id: string,
    params: {
      offset?: number;
      limit?: number;
      q?: string;
      sort?: ItemSortField;
      dir?: SortDirection;
      alertsOnly?: boolean;
    } = {},
  ): Promise<ObserverItemsResponse> => {
    const qs = new URLSearchParams();
    if (params.offset != null) qs.set('offset', String(params.offset));
    if (params.limit != null) qs.set('limit', String(params.limit));
    if (params.q) qs.set('q', params.q);
    if (params.sort) qs.set('sort', params.sort);
    if (params.dir) qs.set('dir', params.dir);
    if (params.alertsOnly != null) qs.set('alertsOnly', String(params.alertsOnly));
    const suffix = qs.toString();
    return apiGet(`/api/observers/${id}/items${suffix ? `?${suffix}` : ''}`);
  },
  observerHistory: (id: string, range: HistoryRange, itemId?: string): Promise<ChartHistoryResponse> => {
    const qs = new URLSearchParams({ range });
    if (itemId) qs.set('item', itemId);
    return apiGet(`/api/observers/${id}/history?${qs.toString()}`);
  },
  observerSamplerDebug: (id: string): Promise<SamplerDebugResponse> => {
    return apiGet(`/api/observers/${id}/debug/sampler`);
  },
  /** 拼接一个图标 URL；前端可加 onerror 占位 */
  iconUrl: (itemId: string): string | null => {
    const idx = itemId.indexOf(':');
    if (idx < 0) return null;
    const ns = encodeURIComponent(itemId.substring(0, idx));
    const path = encodeURIComponent(itemId.substring(idx + 1));
    return `/api/icon/${ns}/${path}`;
  },
};

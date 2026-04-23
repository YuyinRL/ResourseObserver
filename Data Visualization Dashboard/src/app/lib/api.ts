/**
 * Resource Observer Web Dashboard API client.
 * 统一封装后端 REST 端点访问 + TypeScript 类型定义。
 */

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

export interface BindingItem {
  id: string;
  amount: number;
  production: number;
  consumption: number;
  net: number;
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

export interface ObserverBinding {
  networkType: string;
  networkId: string;
  targetBlockId: string;
  currentValue: number;
  capacity: number;
  totalProduced: number;
  totalConsumed: number;
  items?: BindingItem[];
  cellCapacity?: CellCapacity;
  flux?: FluxSnapshot;
}

export interface ObserverDetail extends ObserverSummary {
  bindings: ObserverBinding[];
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
  }>;
  craftables: Array<{
    itemId: string;
    displayName: string;
  }>;
}

export interface CraftingResponse {
  bindings: CraftingBinding[];
}

async function apiGet<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...init,
    headers: { Accept: 'application/json', ...(init?.headers ?? {}) },
  });
  if (!res.ok) {
    throw new Error(`GET ${url} failed: ${res.status} ${res.statusText}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  health: (): Promise<HealthStatus> => apiGet('/api/health'),
  meta: (): Promise<Meta> => apiGet('/api/meta'),
  observers: (): Promise<ObserverListResponse> => apiGet('/api/observers'),
  observerDetail: (id: string): Promise<ObserverDetail> =>
    apiGet(`/api/observers/${id}`),
  observerCrafting: (id: string): Promise<CraftingResponse> =>
    apiGet(`/api/observers/${id}/crafting`),
};

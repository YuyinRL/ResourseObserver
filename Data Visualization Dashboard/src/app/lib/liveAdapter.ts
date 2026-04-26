/**
 * 将后端 ObserverDetail / CraftingResponse 转换成现有面板所使用的形状。
 * 当未选中 Observer 或数据未就绪时返回 null，让面板回退到内置 mock。
 */
import type { ObserverDetail, CraftingResponse, ObserverBinding, FluxDeviceSnapshot } from './api';

export interface LiveKpi {
  label: string;
  value: string;
  unit: string;
  change: string;
  isPositive: boolean;
}

export interface LiveStorageNode {
  id: string;
  name: string;
  type: string;
  capacity: number;
  used: number;
  status: 'Healthy' | 'Alert';
  itemIds?: string[];
  itemCount?: number;
  coordinates?: string;
}

export interface LivePowerLine {
  id: string;
  name: string;
  itemProduced: string;
  consumption: number;
  capacity: number;
  status: 'Normal' | 'Overload';
  devices: number;
  category: string;
}

/** 真实用电器条目 —— 由 FLUX 输出端 Point 设备的相邻方块聚合而来。 */
export interface LivePowerConsumer {
  id: string;
  name: string;
  modName: string;
  category: string;
  consumption: number;     // 总消耗 FE/t
  count: number;            // 聚合的物理设备数
  percentage: number;       // 占总消耗的百分比
  supplyRatio: number;      // 占总输入的百分比
}

/** Power Network 4 张 KPI（与游戏内 PowerNetworkPageBuilder 对齐）。 */
export interface LivePowerKpis {
  inputPerTick: number;
  outputPerTick: number;
  utilizationPercent: number;   // outputPerTick / inputPerTick
  reservePerTick: number;       // inputPerTick - outputPerTick (>=0)
  headroomPercent: number;      // (input - output) / input
  totalStored: number;
  totalCapacity: number;
}

/** 由设备类别聚合得到的电力分布段（与游戏内 LoadSegment 对齐）。 */
export interface LivePowerLoadSegment {
  category: string;
  displayName: string;
  percentage: number;
  color: string;
}

/**
 * 外部储能组（External Storage Group）—— 由 FLUX 设备的 externalRefs 按 extId 聚合而来。
 * 选中后，其关联接口（posKey）将从 KPI / Consumer 计算中排除，
 * 用于过滤掉「为外部储能充放电」的接口，避免与真实负载混淆。
 *
 * 移植自 PowerNetworkViewModelMapper.buildExternalGroups + ResourceTerminalScreen.computeEffectivePowerStats。
 */
export interface LivePowerExternalGroup {
  extId: string;
  displayName: string;
  modName: string;
  stored: number;
  capacity: number;
  /** 关联的设备 posKey 集合 —— 用于排除接口的传输速率 */
  interfaceKeys: string[];
}

/** 排除选定外部储能组后的有效电力统计 —— 与游戏内 EffectiveStats 对齐。 */
export interface LivePowerEffectiveStats {
  totalInputPerTick: number;
  totalOutputPerTick: number;
  excludedInputPerTick: number;
  excludedOutputPerTick: number;
  excludedTotalPerTick: number;
  utilizationPercent: number;
  reservePerTick: number;
  headroomPercent: number;
  totalStored: number;
  totalCapacity: number;
}

export interface LiveResourceItem {
  id: string;
  name: string;
  produced: number;
  consumed: number;
  stock: number;
  capacity: number;
  /** 所属 AE 网络 id；Storage Page 选中节点时按此过滤，避免同名物品跨网络互相串台。 */
  networkId?: string;
}

function shortenNetwork(id: string): string {
  if (!id) return 'network';
  const tail = id.split(/[:\\/]/).pop() ?? id;
  return tail.length > 24 ? tail.slice(0, 24) + '…' : tail;
}

function niceItemName(id: string, displayName?: string, translationKey?: string): string {
  if (displayName && displayName.trim().length > 0) return displayName;
  if (translationKey) {
    const last = translationKey.split('.').pop();
    if (last) {
      return last
        .split(/[_\-/]/)
        .filter(Boolean)
        .map(s => s.charAt(0).toUpperCase() + s.slice(1))
        .join(' ');
    }
  }
  // 流体 id 形如 "fluid:minecraft:water"——剥掉前缀后再取最末段，避免出现 "Minecraft Water"
  let raw = id;
  if (raw.startsWith('fluid:')) raw = raw.substring('fluid:'.length);
  const tail = raw.includes(':') ? raw.substring(raw.indexOf(':') + 1) : raw;
  return tail
    .split(/[_\-/]/)
    .filter(Boolean)
    .map(s => s.charAt(0).toUpperCase() + s.slice(1))
    .join(' ');
}

export function isAe2(b: ObserverBinding): boolean {
  return b.networkType === 'AE2_ITEMS';
}

export function isFlux(b: ObserverBinding): boolean {
  return b.networkType === 'FLUX_ENERGY';
}

export function isPower(b: ObserverBinding): boolean {
  return b.networkType === 'FLUX_ENERGY' || b.networkType === 'MEK_ENERGY';
}

/** 根据 detail 推导 Overview 页的 4 张 KPI。 */
export function deriveOverviewKpis(detail: ObserverDetail | null): LiveKpi[] | null {
  const bindings = detail?.bindings;
  if (!detail || !Array.isArray(bindings) || bindings.length === 0) return null;

  let netProduction = 0;
  let netConsumption = 0;
  let storageUsed = 0;
  let storageMax = 0;
  let totalItemTypes = 0;

  for (const b of bindings) {
    if (isAe2(b) && b.items) {
      for (const it of b.items) {
        netProduction += it.production;
        netConsumption += it.consumption;
      }
      totalItemTypes += b.items.length;
      if (b.cellCapacity) {
        storageUsed += b.cellCapacity.itemUsedBytes;
        storageMax += b.cellCapacity.itemTotalBytes;
      }
    } else if (isPower(b) && b.flux) {
      storageUsed += b.flux.totalEnergy;
      storageMax += b.flux.totalMaxEnergyStorage;
    }
  }

  const storagePct = storageMax > 0 ? (storageUsed / storageMax) * 100 : 0;
  const efficiency =
    netProduction > 0 ? Math.min(100, (netProduction / Math.max(1, netConsumption)) * 100) : 0;

  return [
    {
      label: 'Net Production',
      value: netProduction.toFixed(1),
      unit: '/min',
      change: `${totalItemTypes} item types`,
      isPositive: true,
    },
    {
      label: 'Net Consumption',
      value: netConsumption.toFixed(1),
      unit: '/min',
      change: `${bindings.length} bindings`,
      isPositive: false,
    },
    {
      label: 'Storage Status',
      value: storagePct.toFixed(1),
      unit: '%',
      change: storageMax > 0 ? `${formatBytes(storageUsed)} / ${formatBytes(storageMax)}` : 'n/a',
      isPositive: storagePct < 90,
    },
    {
      label: 'System Efficiency',
      value: efficiency.toFixed(1),
      unit: '%',
      change: detail.bound ? 'Bound' : 'Idle',
      isPositive: detail.bound && efficiency >= 95,
    },
  ];
}

/** 将 AE2 绑定物品映射为 Overview 资源条目。 */
export function deriveResourceItems(detail: ObserverDetail | null, limit = 500): LiveResourceItem[] | null {
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return null;
  const out: LiveResourceItem[] = [];
  for (const b of bindings) {
    if (!isAe2(b) || !b.items) continue;
    for (const it of b.items) {
      out.push({
        id: it.id,
        name: niceItemName(it.id, it.displayName, it.translationKey),
        produced: it.production,
        consumed: it.consumption,
        stock: it.amount,
        capacity: Math.max(it.amount * 1.5, 1000),
        networkId: b.networkId,
      });
      if (out.length >= limit) return out;
    }
  }
  return out.length > 0 ? out : null;
}

/**
 * 聚合版资源条目：按物品 id 合并多个 AE 网络的计量值。
 * <p>Overview 页跨网络展示，必须按物品 id 合并；Storage 页保留各网络分组用 deriveResourceItems。
 */
export function deriveAggregatedResourceItems(
  detail: ObserverDetail | null,
  limit = 500,
): LiveResourceItem[] | null {
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return null;
  const map = new Map<string, LiveResourceItem>();
  for (const b of bindings) {
    if (!isAe2(b) || !b.items) continue;
    for (const it of b.items) {
      const existing = map.get(it.id);
      if (existing) {
        existing.produced += it.production;
        existing.consumed += it.consumption;
        existing.stock += it.amount;
        existing.capacity = Math.max(existing.capacity, Math.max(it.amount * 1.5, 1000));
      } else {
        map.set(it.id, {
          id: it.id,
          name: niceItemName(it.id, it.displayName, it.translationKey),
          produced: it.production,
          consumed: it.consumption,
          stock: it.amount,
          capacity: Math.max(it.amount * 1.5, 1000),
        });
      }
    }
  }
  if (map.size === 0) return null;
  const out = Array.from(map.values());
  return out.length > limit ? out.slice(0, limit) : out;
}

/** 将每个 AE2 绑定视作一个存储节点。 */
export function deriveStorageNodes(detail: ObserverDetail | null): LiveStorageNode[] | null {
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return null;
  const out: LiveStorageNode[] = [];
  for (const b of bindings) {
    if (!isAe2(b)) continue;
    const used = b.cellCapacity?.itemUsedBytes ?? 0;
    const cap = b.cellCapacity?.itemTotalBytes ?? 1;
    const pct = (used / cap) * 100;
    out.push({
      id: b.networkId,
      name: shortenNetwork(b.targetBlockId || b.networkId),
      type: 'AE2 Network',
      capacity: cap,
      used,
      status: pct > 90 ? 'Alert' : 'Healthy',
      itemIds: (b.items ?? []).map((it) => it.id),
      itemCount: (b.items ?? []).length,
    });
  }
  return out.length > 0 ? out : null;
}

/** 将每个 Flux/Mek 能源绑定视作一条生产线（粗粒度，按网络）。 */
export function derivePowerLines(detail: ObserverDetail | null): LivePowerLine[] | null {
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return null;
  const out: LivePowerLine[] = [];
  for (const b of bindings) {
    if (!isPower(b)) continue;
    const current = b.currentValue;
    const cap = Math.max(b.capacity, 1);
    const pct = (current / cap) * 100;
    const netName = b.flux?.networkName ?? shortenNetwork(b.networkId);
    const realDevices = b.flux?.deviceCount
      ?? ((b.flux?.plugCount ?? 0) + (b.flux?.pointCount ?? 0)
          + (b.flux?.storageCount ?? 0) + (b.flux?.controllerCount ?? 0));
    out.push({
      id: b.networkId,
      name: netName || 'Power Network',
      itemProduced: b.networkType === 'FLUX_ENERGY' ? 'Flux' : 'Mekanism FE',
      consumption: b.flux?.energyOutput ?? 0,
      capacity: cap,
      status: pct > 95 ? 'Overload' : 'Normal',
      devices: realDevices > 0 ? realDevices : 1,
      category: b.networkType === 'FLUX_ENERGY' ? 'Flux' : 'Mekanism',
    });
  }
  return out.length > 0 ? out : null;
}

// ── 设备类别推断（移植自 PowerNetworkViewModelMapper.inferCategory）──
function inferCategory(name: string): string {
  if (!name) return 'other';
  const lower = name.toLowerCase();
  if (/(miner|quarry|drill|pump|excavat)/.test(lower)) return 'mining';
  if (/(assembl|craft|inscriber|press|furnace|smelter|grinder|crusher|machine)/.test(lower)) return 'assembly';
  if (/(bus|interface|import|export|pipe|duct|conveyor|router)/.test(lower)) return 'logistics';
  if (/(cube|cell|battery|storage|capacitor|accumulator)/.test(lower)) return 'storage';
  return 'other';
}

const POWER_CATEGORY_COLORS: Record<string, string> = {
  mining: '#06b6d4',
  assembly: '#f59e0b',
  logistics: '#10b981',
  storage: '#3b82f6',
  other: '#64748b',
};

function powerCategoryDisplayName(category: string): string {
  switch (category) {
    case 'mining': return 'Mining';
    case 'assembly': return 'Assembly';
    case 'logistics': return 'Logistics';
    case 'storage': return 'Storage';
    default: return 'Other';
  }
}

/** "modid:block|Block Name @ [x, y, z]" → "Block Name" */
function stripCoordinates(rawName: string | undefined | null): string {
  if (!rawName) return 'Unknown';
  const pipeIdx = rawName.indexOf('|');
  const cleaned = pipeIdx >= 0 ? rawName.substring(pipeIdx + 1) : rawName;
  const atIdx = cleaned.lastIndexOf(' @ ');
  return atIdx > 0 ? cleaned.substring(0, atIdx).trim() : cleaned.trim();
}

/** 从 "modid:block|..." 提取 mod 命名空间 */
function extractModFromRefName(rawName: string | undefined | null): string {
  if (!rawName) return '';
  const pipeIdx = rawName.indexOf('|');
  if (pipeIdx <= 0) return '';
  const regId = rawName.substring(0, pipeIdx);
  const colonIdx = regId.indexOf(':');
  return colonIdx > 0 ? regId.substring(0, colonIdx) : regId;
}

/**
 * 根据 FLUX Point 设备的相邻方块聚合用电器列表。
 * 移植自 PowerNetworkViewModelMapper.buildConsumers。
 *
 * 算法：
 *   1. 第一遍按 extId（基于坐标的唯一标识）去重，计算每个物理设备的实际消耗 = transferChange / refs.length
 *   2. 第二遍按设备类型名（去坐标）分组展示，count 为去重后的物理设备数
 */
export function derivePowerConsumers(
  detail: ObserverDetail | null,
  excludedKeys?: ReadonlySet<string> | null,
): LivePowerConsumer[] | null {
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return null;

  // 收集所有 FLUX 网络的 Point（输出）设备 + 总输入用于 supplyRatio
  const outputDevices: FluxDeviceSnapshot[] = [];
  let totalInputPerTick = 0;
  for (const b of bindings) {
    if (!isFlux(b) || !b.flux) continue;
    totalInputPerTick += b.flux.energyInput ?? 0;
    if (Array.isArray(b.flux.devices)) {
      for (const d of b.flux.devices) {
        if (d.deviceType === 'point') {
          outputDevices.push(d);
        }
      }
    }
  }
  if (outputDevices.length === 0) return null;

  // 第一遍：按 extId 去重，分摊 transferRate 到每个 ref
  const uniqueDevice = new Map<string, { rate: number; displayName: string; mod: string }>();
  for (const dev of outputDevices) {
    if (excludedKeys && dev.posKey && excludedKeys.has(dev.posKey)) continue;
    const rate = Math.abs(dev.transferChange ?? 0);
    const refs = dev.externalRefs ?? [];
    if (refs.length > 0) {
      const perRef = rate > 0 ? Math.floor(rate / refs.length) : 0;
      const remainder = rate > 0 ? rate - perRef * refs.length : 0;
      refs.forEach((ref, i) => {
        const dedupeKey = ref.extId && ref.extId.length > 0 ? ref.extId : ref.displayName;
        const share = perRef + (i === 0 ? remainder : 0);
        const existing = uniqueDevice.get(dedupeKey);
        if (existing) {
          existing.rate += share;
        } else {
          uniqueDevice.set(dedupeKey, {
            rate: share,
            displayName: ref.displayName,
            mod: extractModFromRefName(ref.displayName),
          });
        }
      });
    } else if (rate > 0) {
      const name = dev.customName && dev.customName.length > 0 ? dev.customName : 'Unknown';
      const existing = uniqueDevice.get(name);
      if (existing) existing.rate += rate;
      else uniqueDevice.set(name, { rate, displayName: name, mod: '' });
    }
  }

  // 第二遍：按 cleanName 分组
  const consumerMap = new Map<string, { rate: number; count: number; mod: string }>();
  for (const [, val] of uniqueDevice) {
    const cleanName = stripCoordinates(val.displayName);
    const ex = consumerMap.get(cleanName);
    if (ex) {
      ex.rate += val.rate;
      ex.count += 1;
    } else {
      consumerMap.set(cleanName, { rate: val.rate, count: 1, mod: val.mod });
    }
  }

  if (consumerMap.size === 0) return null;

  let grandTotal = 0;
  for (const v of consumerMap.values()) grandTotal += v.rate;

  const out: LivePowerConsumer[] = [];
  let id = 0;
  for (const [name, v] of consumerMap) {
    const cat = inferCategory(name);
    out.push({
      id: `consumer-${id++}`,
      name,
      modName: v.mod,
      category: cat,
      consumption: v.rate,
      count: v.count,
      percentage: grandTotal > 0 ? (v.rate * 100) / grandTotal : 0,
      supplyRatio: totalInputPerTick > 0 ? (v.rate * 100) / totalInputPerTick : 0,
    });
  }
  out.sort((a, b) => b.consumption - a.consumption);
  return out;
}

/** 按设备类别聚合分布段 —— 用于饼图。 */
export function derivePowerLoadSegments(
  detail: ObserverDetail | null,
  excludedKeys?: ReadonlySet<string> | null,
): LivePowerLoadSegment[] {
  const consumers = derivePowerConsumers(detail, excludedKeys);
  if (!consumers || consumers.length === 0) return [];
  const totals = new Map<string, number>();
  let grand = 0;
  for (const c of consumers) {
    totals.set(c.category, (totals.get(c.category) ?? 0) + c.consumption);
    grand += c.consumption;
  }
  if (grand <= 0) return [];
  const segments: LivePowerLoadSegment[] = [];
  for (const [cat, val] of totals) {
    const pct = (val * 100) / grand;
    if (pct < 0.5) continue;
    segments.push({
      category: cat,
      displayName: powerCategoryDisplayName(cat),
      percentage: pct,
      color: POWER_CATEGORY_COLORS[cat] ?? POWER_CATEGORY_COLORS.other,
    });
  }
  segments.sort((a, b) => b.percentage - a.percentage);
  return segments;
}

/** 4 张 Power KPI —— 与游戏内 PowerNetworkPageBuilder 对齐。 */
export function derivePowerKpis(detail: ObserverDetail | null): LivePowerKpis | null {
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return null;
  let input = 0;
  let output = 0;
  let stored = 0;
  let capacity = 0;
  let any = false;
  for (const b of bindings) {
    if (!isPower(b) || !b.flux) continue;
    any = true;
    input += b.flux.energyInput ?? 0;
    output += b.flux.energyOutput ?? 0;
    stored += b.flux.totalEnergy ?? 0;
    capacity += b.flux.totalMaxEnergyStorage ?? 0;
  }
  if (!any) return null;
  const utilization = input > 0 ? Math.min(100, (output / input) * 100) : 0;
  const reserve = Math.max(0, input - output);
  const headroom = input > 0 ? Math.max(0, ((input - output) / input) * 100) : 0;
  return {
    inputPerTick: input,
    outputPerTick: output,
    utilizationPercent: utilization,
    reservePerTick: reserve,
    headroomPercent: headroom,
    totalStored: stored,
    totalCapacity: capacity,
  };
}

/**
 * 聚合所有 FLUX 网络中的外部储能组（按 ref.extId 分组）。
 * 移植自 PowerNetworkViewModelMapper.buildExternalGroups。
 *
 * 一个外部储能组 = 同一坐标的外部方块（如 Mekanism Energy Cube），
 * 它可以被多个 Plug/Point 接口同时检测到，故需按 extId 去重并合并 interfaceKey。
 */
/**
 * 自动识别「双向储能」外部容器。
 * 移植自 PowerNetworkPageBuilder.computeAutoDetectedStorageIds。
 *
 * 判定：若一个 ext 同时被 PLUG（输入侧）和 POINT（输出侧）接口检测到，
 * 即网络既能从中抽取又能向其输出，则视为双向储能 → 应自动从负载统计中排除。
 */
export function derivePowerAutoDetectedExtIds(detail: ObserverDetail | null): Set<string> {
  const result = new Set<string>();
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return result;

  // 收集每个 ext 被哪些设备类型检测到
  const seenByPlug = new Map<string, boolean>();
  const seenByPoint = new Map<string, boolean>();
  for (const b of bindings) {
    if (!isFlux(b) || !b.flux || !Array.isArray(b.flux.devices)) continue;
    for (const dev of b.flux.devices) {
      if (dev.deviceType !== 'plug' && dev.deviceType !== 'point') continue;
      const refs = dev.externalRefs ?? [];
      for (const r of refs) {
        if (!r.extId) continue;
        if (dev.deviceType === 'plug') seenByPlug.set(r.extId, true);
        else if (dev.deviceType === 'point') seenByPoint.set(r.extId, true);
      }
    }
  }
  for (const id of seenByPlug.keys()) {
    if (seenByPoint.has(id)) result.add(id);
  }
  return result;
}

/**
 * 计算 extId 共现关系：返回 extId → 与之同设备的 extIds 集合。
 * 用于「点击一个外储 = 一并切换其同设备的所有 extIds」的群组联动行为，
 * 与 PowerNetworkPageBuilder.toggleExternalGroupSelection(relatedIds) 对齐。
 */
export function derivePowerExtCoGroups(detail: ObserverDetail | null): Map<string, Set<string>> {
  const map = new Map<string, Set<string>>();
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return map;

  // 并查集：把所有出现在同一设备 externalRefs 中的 extId 归入一个连通分量
  const parent = new Map<string, string>();
  const find = (x: string): string => {
    let p = parent.get(x) ?? x;
    if (p !== x) {
      const root = find(p);
      parent.set(x, root);
      return root;
    }
    return x;
  };
  const union = (a: string, b: string) => {
    const ra = find(a), rb = find(b);
    if (ra !== rb) parent.set(ra, rb);
  };

  for (const b of bindings) {
    if (!isFlux(b) || !b.flux || !Array.isArray(b.flux.devices)) continue;
    for (const dev of b.flux.devices) {
      const refs = dev.externalRefs ?? [];
      const ids = refs.map((r) => r.extId).filter((s): s is string => !!s);
      for (const id of ids) {
        if (!parent.has(id)) parent.set(id, id);
      }
      for (let i = 1; i < ids.length; i++) union(ids[0], ids[i]);
    }
  }

  const buckets = new Map<string, Set<string>>();
  for (const id of parent.keys()) {
    const root = find(id);
    let s = buckets.get(root);
    if (!s) { s = new Set(); buckets.set(root, s); }
    s.add(id);
  }
  for (const [id] of parent) {
    map.set(id, buckets.get(find(id)) ?? new Set([id]));
  }
  return map;
}

export function derivePowerExternalGroups(detail: ObserverDetail | null): LivePowerExternalGroup[] {
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return [];

  type Acc = {
    extId: string;
    displayName: string;
    mod: string;
    stored: number;
    capacity: number;
    interfaceKeys: Set<string>;
  };
  const grouped = new Map<string, Acc>();

  for (const b of bindings) {
    if (!isFlux(b) || !b.flux || !Array.isArray(b.flux.devices)) continue;
    for (const dev of b.flux.devices) {
      // 仅 PLUG/POINT 才有效（与游戏内一致）
      if (dev.deviceType !== 'plug' && dev.deviceType !== 'point') continue;
      const refs = dev.externalRefs ?? [];
      for (const ref of refs) {
        if (!ref.extId || ref.extId.length === 0) continue;
        let acc = grouped.get(ref.extId);
        if (!acc) {
          const cleanName = stripCoordinates(ref.displayName) || ref.extId;
          acc = {
            extId: ref.extId,
            displayName: cleanName,
            mod: extractModFromRefName(ref.displayName),
            stored: ref.stored ?? 0,
            capacity: ref.capacity ?? 0,
            interfaceKeys: new Set<string>(),
          };
          grouped.set(ref.extId, acc);
        } else {
          // 同一 extId 多次出现时取较大值（不同接口可能读到的瞬时值不同）
          acc.stored = Math.max(acc.stored, ref.stored ?? 0);
          acc.capacity = Math.max(acc.capacity, ref.capacity ?? 0);
        }
        if (dev.posKey) acc.interfaceKeys.add(dev.posKey);
      }
    }
  }

  const out: LivePowerExternalGroup[] = [];
  for (const acc of grouped.values()) {
    out.push({
      extId: acc.extId,
      displayName: acc.displayName,
      modName: acc.mod,
      stored: acc.stored,
      capacity: acc.capacity,
      interfaceKeys: Array.from(acc.interfaceKeys),
    });
  }
  // 容量降序，名称升序
  out.sort((a, b) => (b.capacity - a.capacity) || a.displayName.localeCompare(b.displayName));
  return out;
}

/**
 * 把选中的 extId 集合展开成被排除的接口 posKey 集合。
 * 移植自 PowerNetworkPageBuilder.computeEffectiveStats 的前半段。
 */
export function computePowerExcludedKeys(
  groups: readonly LivePowerExternalGroup[],
  selectedExtIds: ReadonlySet<string>,
): Set<string> {
  const keys = new Set<string>();
  if (selectedExtIds.size === 0) return keys;
  for (const g of groups) {
    if (selectedExtIds.has(g.extId)) {
      for (const k of g.interfaceKeys) keys.add(k);
    }
  }
  return keys;
}

/**
 * 在原始 KPI 之上扣除被排除的接口流量，得到「有效电力统计」。
 * 移植自 ResourceTerminalScreen.computeEffectivePowerStats。
 */
export function derivePowerEffectiveStats(
  detail: ObserverDetail | null,
  excludedKeys?: ReadonlySet<string> | null,
  effectiveGroups?: readonly LivePowerExternalGroup[] | null,
): LivePowerEffectiveStats | null {
  const raw = derivePowerKpis(detail);
  if (!raw) return null;
  const bindings = detail?.bindings ?? [];

  let excludedInput = 0;
  let excludedOutput = 0;
  if (excludedKeys && excludedKeys.size > 0) {
    for (const b of bindings) {
      if (!isFlux(b) || !b.flux || !Array.isArray(b.flux.devices)) continue;
      for (const dev of b.flux.devices) {
        if (!dev.posKey || !excludedKeys.has(dev.posKey)) continue;
        const rate = Math.abs(dev.transferChange ?? 0);
        if (dev.deviceType === 'plug') excludedInput += rate;
        else if (dev.deviceType === 'point') excludedOutput += rate;
      }
    }
  }

  const effIn = Math.max(0, raw.inputPerTick - excludedInput);
  const effOut = Math.max(0, raw.outputPerTick - excludedOutput);
  const utilization = effIn > 0 ? Math.min(100, (effOut / effIn) * 100) : 0;
  const reserve = Math.max(0, effIn - effOut);
  const headroom = effIn > 0 ? Math.max(0, ((effIn - effOut) / effIn) * 100) : 0;

  // 合并储能 = 网络内部储能 + 被识别为「外储」的外部容器储能
  let combinedStored = raw.totalStored;
  let combinedCapacity = raw.totalCapacity;
  if (effectiveGroups && effectiveGroups.length > 0) {
    for (const g of effectiveGroups) {
      combinedStored += Math.max(0, g.stored);
      combinedCapacity += Math.max(0, g.capacity);
    }
  }

  return {
    totalInputPerTick: effIn,
    totalOutputPerTick: effOut,
    excludedInputPerTick: excludedInput,
    excludedOutputPerTick: excludedOutput,
    excludedTotalPerTick: excludedInput + excludedOutput,
    utilizationPercent: utilization,
    reservePerTick: reserve,
    headroomPercent: headroom,
    totalStored: combinedStored,
    totalCapacity: combinedCapacity,
  };
}

export interface LivePowerSummary {
  totalEnergy: number;
  totalMax: number;
  input: number;
  output: number;
}

export function derivePowerSummary(detail: ObserverDetail | null): LivePowerSummary | null {
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return null;
  let totalEnergy = 0;
  let totalMax = 0;
  let input = 0;
  let output = 0;
  let any = false;
  for (const b of bindings) {
    if (!isPower(b) || !b.flux) continue;
    any = true;
    totalEnergy += b.flux.totalEnergy;
    totalMax += b.flux.totalMaxEnergyStorage;
    input += b.flux.energyInput;
    output += b.flux.energyOutput;
  }
  if (!any) return null;
  return { totalEnergy, totalMax, input, output };
}

export interface LiveCraftingSummary {
  cpuCount: number;
  busyCpuCount: number;
  craftableCount: number;
  activeJobs: number;
}

export function deriveCraftingSummary(c: CraftingResponse | null): LiveCraftingSummary | null {
  const bindings = c?.bindings;
  if (!Array.isArray(bindings) || bindings.length === 0) return null;
  let cpuCount = 0;
  let busyCpuCount = 0;
  const craftableKeys = new Set<string>();
  let activeJobs = 0;
  for (const b of bindings) {
    cpuCount += b.storage.cpuCount;
    busyCpuCount += b.storage.busyCpuCount;
    for (const craftable of b.craftables) {
      craftableKeys.add(`${b.networkId}::${craftable.itemId}`);
    }
    activeJobs += b.jobs.filter(j => j.busy).length;
  }
  return { cpuCount, busyCpuCount, craftableCount: craftableKeys.size, activeJobs };
}

export function countBindingsByType(detail: ObserverDetail | null): {
  ae2: number;
  flux: number;
  mek: number;
  total: number;
} {
  const bindings = detail?.bindings;
  if (!Array.isArray(bindings)) return { ae2: 0, flux: 0, mek: 0, total: 0 };
  let ae2 = 0;
  let flux = 0;
  let mek = 0;
  for (const b of bindings) {
    if (b.networkType === 'AE2_ITEMS') ae2++;
    else if (b.networkType === 'FLUX_ENERGY') flux++;
    else if (b.networkType === 'MEK_ENERGY') mek++;
  }
  return { ae2, flux, mek, total: bindings.length };
}

export function formatBytes(n: number): string {
  if (n < 1024) return `${n.toFixed(0)}B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)}KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)}MB`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)}GB`;
}

export function formatEnergy(fe: number): string {
  if (fe < 1000) return `${fe.toFixed(0)}FE`;
  if (fe < 1_000_000) return `${(fe / 1000).toFixed(1)}kFE`;
  if (fe < 1_000_000_000) return `${(fe / 1_000_000).toFixed(1)}MFE`;
  return `${(fe / 1_000_000_000).toFixed(2)}GFE`;
}

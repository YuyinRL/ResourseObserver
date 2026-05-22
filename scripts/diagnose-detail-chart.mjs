// 诊断 detail 档（1m/0.25s）流量曲线数据。
// 用法：node scripts/diagnose-detail-chart.mjs [--host=127.0.0.1] [--port=28080] [--samples=10] [--interval=1000]
//
// 输出：
//   1. 命中的 Observer
//   2. /history?range=detail 的最近 N 个点（produced/consumed/net + raw 字段）
//   3. /debug/sampler 的 recentBuckets 原始数据（每桶 sampleCount/observedTicks/perMinute）
//   4. 统计：rolling-window 重算、bucket 中位数、空桶比例

const args = Object.fromEntries(
  process.argv.slice(2).map((a) => {
    const m = a.match(/^--([^=]+)=(.*)$/);
    return m ? [m[1], m[2]] : [a.replace(/^--/, ''), 'true'];
  })
);
const host = args.host || '127.0.0.1';
const port = parseInt(args.port || '28080', 10);
const samples = parseInt(args.samples || '6', 10);
const intervalMs = parseInt(args.interval || '1500', 10);
const base = `http://${host}:${port}`;

async function getJson(path) {
  const url = `${base}${path}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url} → HTTP ${res.status}`);
  return res.json();
}

function fmt(n, digits = 1) {
  if (n == null || Number.isNaN(n)) return '   N/A';
  const s = Number(n).toFixed(digits);
  return s.padStart(8, ' ');
}

function pct(a, b) {
  if (!b) return '0%';
  return `${((a / b) * 100).toFixed(1)}%`;
}

async function main() {
  console.log(`[diag] base=${base}`);
  const list = await getJson('/api/observers');
  if (!list.observers?.length) {
    console.error('[diag] No observers found. Place an observer block and bind a network first.');
    process.exit(1);
  }
  const obs = list.observers[0];
  const obsPath = `/api/observers/${encodeURIComponent(obs.dimension)}/${obs.x}/${obs.y}/${obs.z}`;
  console.log(`[diag] observer=${obs.dimension} (${obs.x},${obs.y},${obs.z})`);

  for (let round = 0; round < samples; round++) {
    console.log(`\n=== round ${round + 1}/${samples} ===`);
    const history = await getJson(`${obsPath}/history?range=detail`);
    const debug = await getJson(`${obsPath}/debug/sampler`).catch((e) => ({ error: e.message }));

    console.log(
      `[history] highPrecision=${history.highPrecision} ` +
        `rateWindow=${history.rateWindowSeconds}s bucketSec=${history.bucketSeconds} ` +
        `points=${history.points?.length} latestBucket=${history.latestBucket}`
    );

    const points = history.points || [];
    const tail = points.slice(-12);
    console.log('[history.tail] last 12 points:');
    console.log('  bucket | produced | consumed |   net    |  stock   | hasFlow | sampleCount');
    for (const p of tail) {
      console.log(
        `  ${String(p.bucket).padStart(6)} | ${fmt(p.produced)} | ${fmt(p.consumed)} | ${fmt(p.net)} | ${fmt(
          p.stock,
          0
        )} | ${String(p.hasFlow).padEnd(7)} | ${p.sampleCount}`
      );
    }
    const last = tail[tail.length - 1];
    if (last) {
      console.log(
        `[history.last] produced/min=${fmt(last.produced)} consumed/min=${fmt(last.consumed)} net=${fmt(last.net)}`
      );
    }

    if (debug.error) {
      console.log(`[debug] error: ${debug.error}`);
      continue;
    }
    console.log(
      `[debug] requestedInterval=${debug.requestedIntervalTicks}t active=${debug.active} ` +
        `globalDemand=${debug.globalDemandActive} itemDemand=${debug.itemDemandActive} ` +
        `bucketTicks=${debug.bucketTicks} bufferBuckets=${debug.bufferBuckets}`
    );
    for (const net of debug.networks || []) {
      console.log(
        `[debug.net ${net.networkId.slice(0, 40)}] validBuckets=${net.validBuckets} ` +
          `totalSamples=${net.totalSamples} lastSampleGameTime=${net.lastSampleGameTime} ` +
          `prevSnapshotSize=${net.previousSnapshotSize}`
      );
      const recent = net.recentBuckets || [];
      const empty = recent.filter((b) => b.observedTicks === 0).length;
      console.log(`  recentBuckets count=${recent.length} emptyBuckets=${empty} (${pct(empty, recent.length)})`);
      console.log('  bucket | samples | obsTicks |  prod/min |  cons/min |  stock');
      for (const b of recent.slice(-16)) {
        console.log(
          `  ${String(b.bucket).padStart(6)} | ${String(b.sampleCount).padStart(7)} | ${String(b.observedTicks).padStart(
            8
          )} | ${fmt(b.producedPerMinute)} | ${fmt(b.consumedPerMinute)} | ${fmt(b.stock, 0)}`
        );
      }
      // 简单的滑动窗口重算：用最后 12 桶的 (sampleCount * (perMinute * obsTicks / 1200))
      // 反推 producedRaw，与 obsTicks 一起做 sliding window
      const rwBuckets = Math.max(1, Math.round(3.0 / (debug.bucketTicks * 0.05)));
      const window = recent.slice(-rwBuckets);
      let sumProd = 0;
      let sumCons = 0;
      let sumTicks = 0;
      for (const b of window) {
        // perMinute = (raw / observedTicks) * 1200  →  raw = perMinute * observedTicks / 1200
        const prodRaw = (b.producedPerMinute * b.observedTicks) / 1200;
        const consRaw = (b.consumedPerMinute * b.observedTicks) / 1200;
        sumProd += prodRaw;
        sumCons += consRaw;
        sumTicks += b.observedTicks;
      }
      const recomputedProd = sumTicks > 0 ? (sumProd / sumTicks) * 1200 : 0;
      const recomputedCons = sumTicks > 0 ? (sumCons / sumTicks) * 1200 : 0;
      console.log(
        `  [reconstruct rolling-${rwBuckets}b] sumProd=${sumProd.toFixed(1)} sumTicks=${sumTicks} ` +
          `→ produced/min=${fmt(recomputedProd)} consumed/min=${fmt(recomputedCons)}`
      );
    }

    if (round + 1 < samples) await new Promise((r) => setTimeout(r, intervalMs));
  }
}

main().catch((e) => {
  console.error('[diag] FATAL:', e);
  process.exit(1);
});

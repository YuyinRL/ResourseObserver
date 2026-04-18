package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.animation.PropertyValuesHolder;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.graphics.drawable.GradientDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.*;

/**
 * Power Network 页面构建器 —— 在内容区域中构建电力网络仪表板。
 * <p>
 * 布局：全宽有效 KPI 卡片 → 双列（左 33%: 负载概要 + 过载风险 + 外部控制台 | 右 67%: 电网图表 + 设备列表）
 *      → 全宽电力统计摘要。
 * <p>
 * 从 {@link com.yuyinrl.resourceobserver.client.screen.ResourceTerminalScreen} 的
 * renderPowerNetworkPage / computePowerContentLayout 完整移植。
 * <p>
 * 数据来源为 {@link PowerNetworkViewModel}，选中外部存储组后会计算有效统计值
 * （排除选中外部组的输入/输出）用于 KPI 卡片和过载分析。
 */
final class PowerNetworkPageBuilder {

    private static final int DEVICE_ROW_HEIGHT_DP = 22;
    private static final int DEBUG_ROW_HEIGHT_DP = 14;

    // View tags for incremental update (avoid full rebuild on data refresh)
    private static final int TAG_DONUT = 0x7F_0101;
    private static final int TAG_KPI_VALUES = 0x7F_0102;
    private static final int TAG_SUMMARY_GEN = 0x7F_0103;
    private static final int TAG_SUMMARY_LOAD = 0x7F_0104;
    private static final int TAG_RISK_LINE = 0x7F_0105;
    private static final int TAG_GRID_GEN_TV = 0x7F_0106;
    private static final int TAG_GRID_LOAD_TV = 0x7F_0107;
    private static final int TAG_GRID_UTIL_TV = 0x7F_0108;
    private static final int TAG_GRID_BAR_FILL = 0x7F_0109;
    private static final int TAG_DEVICE_SECTION = 0x7F_010B;

    private PowerNetworkPageBuilder() {}

    // ===================== Effective Stats（移植自 vanilla Screen） =====================

    private record EffectiveStats(
            long totalInputPerTick,
            long totalOutputPerTick,
            long excludedInputPerTick,
            long excludedOutputPerTick,
            long excludedTotalPerTick,
            long totalDemandPerTick,
            PowerNetworkViewModel.OverloadInfo overloadInfo
    ) {}

    /**
     * 计算有效电力统计值 —— 排除被选中的外部存储组所关联的接口设备。
     * 移植自 ResourceTerminalScreen.computeEffectivePowerStats。
     */
    private static EffectiveStats computeEffectiveStats(PowerNetworkViewModel vm, Set<String> selectedExtIds) {
        if (vm == null) {
            return new EffectiveStats(0L, 0L, 0L, 0L, 0L, 0L,
                    new PowerNetworkViewModel.OverloadInfo(0.0, 0L));
        }

        PowerNetworkViewModel.DebugSnapshot debugSnapshot = vm.debugSnapshot();
        long rawInput = Math.max(0L, vm.totalInputPerTick());
        long rawOutput = Math.max(0L, vm.totalOutputPerTick());

        if (debugSnapshot == null) {
            double headroom = rawInput > 0
                    ? Math.max(0.0, (rawInput - rawOutput) * 100.0 / rawInput) : 0.0;
            long reserve = Math.max(0L, rawInput - rawOutput);
            return new EffectiveStats(rawInput, rawOutput, 0L, 0L, 0L, rawOutput,
                    new PowerNetworkViewModel.OverloadInfo(headroom, reserve));
        }

        LinkedHashSet<String> excludedKeys = new LinkedHashSet<>();
        if (selectedExtIds != null && !selectedExtIds.isEmpty()) {
            for (PowerNetworkViewModel.ExternalGroup group : debugSnapshot.externalGroups()) {
                if (selectedExtIds.contains(group.extId())) {
                    excludedKeys.addAll(group.interfaceKeys());
                }
            }
        }

        long excludedInput = 0L;
        for (PowerNetworkViewModel.DeviceDebugEntry dev : debugSnapshot.inputDevices()) {
            if (excludedKeys.contains(dev.interfaceKey())) {
                excludedInput = saturatingAdd(excludedInput, Math.abs(dev.transferRate()));
            }
        }

        long excludedOutput = 0L;
        for (PowerNetworkViewModel.DeviceDebugEntry dev : debugSnapshot.outputDevices()) {
            if (excludedKeys.contains(dev.interfaceKey())) {
                excludedOutput = saturatingAdd(excludedOutput, Math.abs(dev.transferRate()));
            }
        }

        long effectiveIn = Math.max(0L, rawInput - excludedInput);
        long effectiveOut = Math.max(0L, rawOutput - excludedOutput);
        long excludedTotal = saturatingAdd(excludedInput, excludedOutput);

        double headroom = effectiveIn > 0
                ? Math.max(0.0, (effectiveIn - effectiveOut) * 100.0 / effectiveIn) : 0.0;
        long reserve = Math.max(0L, effectiveIn - effectiveOut);

        return new EffectiveStats(effectiveIn, effectiveOut, excludedInput, excludedOutput,
                excludedTotal, effectiveOut,
                new PowerNetworkViewModel.OverloadInfo(headroom, reserve));
    }

    // ── Consumer color palette (matches PowerNetworkViewModelMapper.CONSUMER_COLORS) ──
    private static final int[] CONSUMER_COLORS = {
            UiThemeTokens.CYAN, UiThemeTokens.AMBER, UiThemeTokens.EMERALD, UiThemeTokens.BLUE,
            UiThemeTokens.ROSE, 0xFF818CF8, 0xFFA78BFA, 0xFF2DD4BF,
    };

    /**
     * 构建有效用电器列表 —— 排除属于 effectiveExtIds 外部存储组的输出接口。
     * <p>
     * 只统计真正消耗能量的输出接口（Point），而非输送到外部储能的接口。
     */
    private static List<PowerNetworkViewModel.ConsumerEntry> computeEffectiveConsumers(
            PowerNetworkViewModel vm, Set<String> effectiveExtIds, long totalEffectiveInput) {
        PowerNetworkViewModel.DebugSnapshot snap = vm.debugSnapshot();
        if (snap == null) return List.of();

        List<PowerNetworkViewModel.DeviceDebugEntry> outputDevices = snap.outputDevices();
        if (outputDevices == null || outputDevices.isEmpty()) return List.of();

        // Collect interface keys belonging to effective external storage groups
        Set<String> excludedKeys = new HashSet<>();
        if (effectiveExtIds != null && !effectiveExtIds.isEmpty()) {
            for (PowerNetworkViewModel.ExternalGroup g : snap.externalGroups()) {
                if (effectiveExtIds.contains(g.extId()) && g.interfaceKeys() != null) {
                    excludedKeys.addAll(g.interfaceKeys());
                }
            }
        }

        // 第一遍：按唯一物理设备聚合（extId 基于坐标，同一方块位置只算一次）
        // 同一个用电设备可能被多个输出接口检测到，此处先按 extId 去重
        Map<String, long[]> uniqueDeviceMap = new LinkedHashMap<>();
        Map<String, String> deviceModMap = new LinkedHashMap<>();
        Map<String, String> deviceDisplayNameMap = new LinkedHashMap<>();
        for (PowerNetworkViewModel.DeviceDebugEntry device : outputDevices) {
            if (excludedKeys.contains(device.interfaceKey())) continue;
            long rate = Math.abs(device.transferRate());

            List<PowerNetworkViewModel.ExternalRef> refs = device.externalRefs();
            if (refs != null && !refs.isEmpty()) {
                long perRef = refs.size() > 0 && rate > 0 ? rate / refs.size() : 0;
                long remainder = rate > 0 ? rate - perRef * refs.size() : 0;
                for (int i = 0; i < refs.size(); i++) {
                    PowerNetworkViewModel.ExternalRef ref = refs.get(i);
                    // 使用 extId（基于坐标的唯一标识）作为去重 key
                    String dedupeKey = ref.extId() != null && !ref.extId().isBlank()
                            ? ref.extId() : ref.displayName();
                    long share = perRef + (i == 0 ? remainder : 0);
                    uniqueDeviceMap.computeIfAbsent(dedupeKey, k -> new long[1]);
                    uniqueDeviceMap.get(dedupeKey)[0] += share;
                    // 保存 displayName 和 mod 信息用于第二遍
                    deviceDisplayNameMap.putIfAbsent(dedupeKey, ref.displayName());
                    deviceModMap.putIfAbsent(dedupeKey, extractModFromRefName(ref.displayName()));
                }
            } else if (rate > 0) {
                String name = device.deviceName() != null && !device.deviceName().isBlank()
                        ? device.deviceName() : "Unknown";
                uniqueDeviceMap.computeIfAbsent(name, k -> new long[1]);
                uniqueDeviceMap.get(name)[0] += rate;
                deviceDisplayNameMap.putIfAbsent(name, name);
            }
        }

        // 第二遍：按设备类型名（去坐标）分组展示，count 为去重后的物理设备数
        Map<String, long[]> consumerMap = new LinkedHashMap<>();
        Map<String, String> modNameMap = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> ue : uniqueDeviceMap.entrySet()) {
            String dedupeKey = ue.getKey();
            String rawDisplayName = deviceDisplayNameMap.getOrDefault(dedupeKey, dedupeKey);
            String cleanName = stripCoordinates(rawDisplayName);
            long consumption = ue.getValue()[0];
            consumerMap.computeIfAbsent(cleanName, k -> new long[2]);
            consumerMap.get(cleanName)[0] += consumption;
            consumerMap.get(cleanName)[1]++;
            modNameMap.putIfAbsent(cleanName, deviceModMap.getOrDefault(dedupeKey, ""));
        }

        if (consumerMap.isEmpty()) return List.of();
        long grandTotal = 0L;
        for (long[] val : consumerMap.values()) grandTotal += val[0];

        List<PowerNetworkViewModel.ConsumerEntry> result = new ArrayList<>();
        int colorIdx = 0;
        for (Map.Entry<String, long[]> entry : consumerMap.entrySet()) {
            long consumption = entry.getValue()[0];
            int count = (int) entry.getValue()[1];
            double pct = grandTotal > 0 ? (consumption * 100.0) / grandTotal : 0.0;
            // 供能占用 = 设备消耗 / 总有效输入
            double supplyRatio = totalEffectiveInput > 0
                    ? (consumption * 100.0) / totalEffectiveInput : 0.0;
            int color = CONSUMER_COLORS[colorIdx % CONSUMER_COLORS.length];
            colorIdx++;
            String modName = modNameMap.getOrDefault(entry.getKey(), "");
            result.add(new PowerNetworkViewModel.ConsumerEntry(
                    entry.getKey(), modName, consumption, pct, supplyRatio, color, count));
        }
        result.sort(Comparator.comparingLong(
                PowerNetworkViewModel.ConsumerEntry::consumptionPerTick).reversed());
        return result;
    }

    /**
     * 从带注册 ID 前缀的 displayName 中提取模组命名空间。
     * 格式："mekanism:energy_cube|Energy Cube @ [1, 2, 3]" → "mekanism"
     */
    private static String extractModFromRefName(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";
        int pipeIdx = rawName.indexOf('|');
        if (pipeIdx <= 0) return "";
        String regId = rawName.substring(0, pipeIdx);
        int colonIdx = regId.indexOf(':');
        return colonIdx > 0 ? regId.substring(0, colonIdx) : regId;
    }

    /**
     * 从 "registryId|Block Name @ [x, y, z]" 格式中提取干净的设备名称。
     * 先去除注册 ID 前缀，再去除坐标后缀。
     */
    private static String stripCoordinates(String rawName) {
        if (rawName == null || rawName.isBlank()) return "Unknown";
        // 去除注册 ID 前缀
        int pipeIdx = rawName.indexOf('|');
        String cleaned = pipeIdx >= 0 ? rawName.substring(pipeIdx + 1) : rawName;
        // 去除坐标后缀
        int atIdx = cleaned.lastIndexOf(" @ ");
        return atIdx > 0 ? cleaned.substring(0, atIdx).trim() : cleaned.trim();
    }

    /**
     * 构建有效 KPI 列表（4 张卡片：有效输入、有效输出、储能、外部排除量）。
     * 移植自 ResourceTerminalScreen.buildEffectivePowerKpis，并加入 mapper 原有的储能 KPI。
     */
    private static List<PowerNetworkViewModel.PowerKpi> buildEffectiveKpis(EffectiveStats stats,
                                                                           PowerNetworkViewModel vm,
                                                                           Set<String> effectiveExtIds) {
        OverviewViewModel.Status inputStatus = stats.totalInputPerTick() > 0
                ? OverviewViewModel.Status.POSITIVE : OverviewViewModel.Status.NEUTRAL;

        OverviewViewModel.Status outputStatus = stats.totalOutputPerTick() > stats.totalInputPerTick()
                ? OverviewViewModel.Status.WARNING
                : (stats.totalOutputPerTick() > 0 ? OverviewViewModel.Status.POSITIVE : OverviewViewModel.Status.NEUTRAL);

        // 储能状态：双来源合并（网络内部 + 已选中的外部容器）
        long[] combined = computeCombinedStorage(vm, effectiveExtIds);
        long totalStored = combined[0];
        long totalCapacity = combined[1];
        double storedRatio = totalCapacity > 0 ? (double) totalStored / totalCapacity : 0.0;
        OverviewViewModel.Status storedStatus;
        if (storedRatio <= 0.1) {
            storedStatus = OverviewViewModel.Status.NEGATIVE;
        } else if (storedRatio >= 0.9) {
            storedStatus = OverviewViewModel.Status.WARNING;
        } else {
            storedStatus = OverviewViewModel.Status.POSITIVE;
        }

        OverviewViewModel.Status excludedStatus = stats.excludedTotalPerTick() > 0
                ? OverviewViewModel.Status.WARNING : OverviewViewModel.Status.NEUTRAL;

        return List.of(
                new PowerNetworkViewModel.PowerKpi(
                        tr("screen.resourceobserver.power.kpi.effective_input"),
                        compact(stats.totalInputPerTick()) + " FE/t",
                        inputStatus),
                new PowerNetworkViewModel.PowerKpi(
                        tr("screen.resourceobserver.power.kpi.effective_output"),
                        compact(stats.totalOutputPerTick()) + " FE/t",
                        outputStatus),
                new PowerNetworkViewModel.PowerKpi(
                        tr("screen.resourceobserver.power.kpi.stored_energy"),
                        compact(totalStored) + " / " + compact(totalCapacity) + " FE",
                        storedStatus),
                new PowerNetworkViewModel.PowerKpi(
                        tr("screen.resourceobserver.power.kpi.external_excluded"),
                        compact(stats.excludedTotalPerTick()) + " FE/t",
                        excludedStatus)
        );
    }

    private static long saturatingAdd(long a, long b) {
        if (b <= 0L) return a;
        if (Long.MAX_VALUE - a < b) return Long.MAX_VALUE;
        return a + b;
    }

    /**
     * 计算自动识别的双向储能外部容器 ID 集合。
     * <p>
     * 判定逻辑：如果一个外部容器的 {@code interfaceKeys} 同时包含
     * Plug（inputDevices）和 Point（outputDevices）侧的设备——即网络既能从中抽取能量、
     * 又能向其输出能量——则认定为外部储能设备。
     *
     * @return 自动识别的外部储能 extId 集合（可能为空，不为 null）
     */
    private static Set<String> computeAutoDetectedStorageIds(PowerNetworkViewModel.DebugSnapshot snap) {
        if (snap == null || snap.externalGroups() == null
                || snap.inputDevices() == null || snap.outputDevices() == null) {
            return Set.of();
        }
        Set<String> plugKeys = new HashSet<>();
        for (PowerNetworkViewModel.DeviceDebugEntry d : snap.inputDevices()) {
            if (d.interfaceKey() != null) plugKeys.add(d.interfaceKey());
        }
        Set<String> pointKeys = new HashSet<>();
        for (PowerNetworkViewModel.DeviceDebugEntry d : snap.outputDevices()) {
            if (d.interfaceKey() != null) pointKeys.add(d.interfaceKey());
        }
        Set<String> result = new LinkedHashSet<>();
        for (PowerNetworkViewModel.ExternalGroup g : snap.externalGroups()) {
            if (isBidirectionalStorage(g, plugKeys, pointKeys)) {
                result.add(g.extId());
            }
        }
        return result;
    }

    /**
     * 计算双来源合并储能/容量：网络内部容器 + 有效外部储能容器。
     * <p>
     * 来源 1：{@code vm.totalStored()} / {@code vm.totalCapacity()} —— Flux 网络内部储能方块。
     * 来源 2：{@code debugSnapshot.externalGroups()} 中属于 effectiveExtIds 的外部容器。
     * <p>
     * effectiveExtIds = 自动识别的双向储能 ∪ 用户手动选择，由调用方负责合并。
     *
     * @return {@code long[2]}：{combinedStored, combinedCapacity}
     */
    private static long[] computeCombinedStorage(PowerNetworkViewModel vm, Set<String> effectiveExtIds) {
        long stored = vm.totalStored();
        long capacity = vm.totalCapacity();
        if (effectiveExtIds != null && !effectiveExtIds.isEmpty()) {
            PowerNetworkViewModel.DebugSnapshot snap = vm.debugSnapshot();
            if (snap != null && snap.externalGroups() != null) {
                for (PowerNetworkViewModel.ExternalGroup g : snap.externalGroups()) {
                    if (effectiveExtIds.contains(g.extId())) {
                        stored = saturatingAdd(stored, Math.max(0L, g.stored()));
                        capacity = saturatingAdd(capacity, Math.max(0L, g.capacity()));
                    }
                }
            }
        }
        return new long[]{stored, capacity};
    }

    /**
     * 判断一个外部容器组是否为双向储能设备。
     * 条件：其 interfaceKeys 同时包含至少一个 Plug 侧设备和至少一个 Point 侧设备。
     */
    private static boolean isBidirectionalStorage(PowerNetworkViewModel.ExternalGroup group,
                                                   Set<String> plugKeys, Set<String> pointKeys) {
        if (group.interfaceKeys() == null || group.interfaceKeys().isEmpty()) return false;
        boolean seenByPlug = false;
        boolean seenByPoint = false;
        for (String key : group.interfaceKeys()) {
            if (plugKeys.contains(key)) seenByPlug = true;
            if (pointKeys.contains(key)) seenByPoint = true;
            if (seenByPlug && seenByPoint) return true;
        }
        return false;
    }

    // ===================== Incremental Update（增量刷新，保留 hover 状态） =====================

    /**
     * 尝试增量刷新 Power Network 页面 —— 只更新数据，不销毁/重建 View 树。
     * 这样 DonutChartView 的 hover 状态、tooltip 不会因刷新而丢失。
     *
     * @return true 如果增量刷新成功，false 需要完整重建
     */
    static boolean tryIncrementalUpdate(ResourceTerminalFragment terminal) {
        PowerNetworkViewModel vm = terminal.getBridge().getPowerViewModel();
        if (vm == null) return false;

        FrameLayout container = terminal.getContentContainer();
        if (container == null || container.getChildCount() == 0) return false;

        // Find the DonutChartView by tag — if missing, need full rebuild
        DonutChartView donut = container.findViewWithTag(TAG_DONUT);
        if (donut == null) return false;

        // Recompute effective stats (same logic as build)
        terminal.getBridge().sanitizeSelectedExternalGroups();
        Set<String> selectedExtIds = terminal.getBridge().getSelectedExternalGroupIds();
        Set<String> autoDetectedIds = computeAutoDetectedStorageIds(vm.debugSnapshot());
        Set<String> effectiveExtIds = new LinkedHashSet<>(autoDetectedIds);
        if (selectedExtIds != null) effectiveExtIds.addAll(selectedExtIds);
        EffectiveStats stats = computeEffectiveStats(vm, effectiveExtIds);

        // ── Update donut chart data (in-place, preserves hover state) ──
        PowerNetworkViewModel.OverloadInfo info = stats.overloadInfo();
        double headroom = info != null ? info.headroomPercent() : 100.0;
        boolean critical = headroom < 5;
        boolean warning = headroom < 15;
        int accentColor = critical ? UiThemeTokens.ROSE
                : (warning ? UiThemeTokens.AMBER : UiThemeTokens.EMERALD);

        List<PowerNetworkViewModel.ConsumerEntry> effectiveConsumers =
                computeEffectiveConsumers(vm, effectiveExtIds, stats.totalInputPerTick());
        List<DonutChartView.Segment> donutSegs = buildDonutSegments(vm, stats, effectiveConsumers);
        String centerValue = String.format(Locale.ROOT, "%.1f%%", headroom);
        String centerLabel = tr("screen.resourceobserver.power.section.load_chart");
        donut.setData(donutSegs, headroom, centerValue, centerLabel, accentColor);

        // ── Update KPI text values ──
        List<PowerNetworkViewModel.PowerKpi> kpis = buildEffectiveKpis(stats, vm, effectiveExtIds);
        updateTaggedTextViews(container, TAG_KPI_VALUES, kpis);

        // ── Update summary gen/load text ──
        updateTaggedText(container, TAG_SUMMARY_GEN, compact(stats.totalInputPerTick()) + " FE/t");
        updateTaggedText(container, TAG_SUMMARY_LOAD, compact(stats.totalOutputPerTick()) + " FE/t");

        // ── Update risk line ──
        String riskLabel = critical
                ? tr("screen.resourceobserver.power.overload.risk.critical")
                : (warning ? tr("screen.resourceobserver.power.overload.risk.warning")
                        : tr("screen.resourceobserver.power.overload.risk.low"));
        long reserve = info != null ? info.reservePerTick() : 0L;
        String riskLine = riskLabel + "  |  " + tr("screen.resourceobserver.power.overload.reserve",
                compact(reserve) + " FE/t");
        TextView riskTv = container.findViewWithTag(TAG_RISK_LINE);
        if (riskTv != null) {
            riskTv.setText(riskLine);
            riskTv.setTextColor(accentColor);
        }

        // ── Update grid chart values ──
        updateTaggedText(container, TAG_GRID_GEN_TV, compact(stats.totalInputPerTick()) + " FE/t");
        updateTaggedText(container, TAG_GRID_LOAD_TV, compact(stats.totalOutputPerTick()) + " FE/t");
        long cap = stats.totalInputPerTick() > 0 ? stats.totalInputPerTick() : 1;
        double loadRatio = Math.min(1.0, (double) stats.totalOutputPerTick() / cap);
        updateTaggedText(container, TAG_GRID_UTIL_TV,
                tr("screen.resourceobserver.power.col.cap_util") + " "
                        + String.format(Locale.ROOT, "%.0f%%", loadRatio * 100));

        // ── Update grid load bar fill (宽度 + 颜色) ──
        View barFill = container.findViewWithTag(TAG_GRID_BAR_FILL);
        if (barFill != null) {
            ViewGroup.LayoutParams fillLp = barFill.getLayoutParams();
            if (fillLp != null) {
                // 从父容器获取进度条总宽度
                ViewGroup barParent = (ViewGroup) barFill.getParent();
                int barTotalW = barParent != null ? barParent.getWidth() : 0;
                if (barTotalW > 0) {
                    fillLp.width = Math.max(1, (int) (barTotalW * loadRatio));
                    barFill.setLayoutParams(fillLp);
                }
            }
            int fillColor = loadRatio > 0.9 ? UiThemeTokens.ROSE
                    : (loadRatio > 0.7 ? UiThemeTokens.AMBER : UiThemeTokens.CYAN);
            barFill.setBackground(colorDot(barFill, fillColor));
        }

        // ── Update device list (rebuild in-place) ──
        View deviceView = container.findViewWithTag(TAG_DEVICE_SECTION);
        if (deviceView instanceof LinearLayout deviceSection) {
            int sectionWidth = deviceSection.getWidth();
            if (sectionWidth <= 0) {
                // 尚未完成布局，使用 LayoutParams 中的宽度
                ViewGroup.LayoutParams lp = deviceSection.getLayoutParams();
                if (lp != null && lp.width > 0) sectionWidth = lp.width;
            }
            if (sectionWidth > 0) {
                deviceSection.removeAllViews();
                buildDeviceList(terminal, deviceSection, sectionWidth, vm, effectiveConsumers);
            }
        }

        return true;
    }

    /**
     * Build donut segments: prefer effective consumers (filtered to exclude storage),
     * fall back to category-based LoadSegments.
     */
    private static List<DonutChartView.Segment> buildDonutSegments(
            PowerNetworkViewModel vm, EffectiveStats stats,
            List<PowerNetworkViewModel.ConsumerEntry> effectiveConsumers) {
        long totalOutput = stats.totalOutputPerTick();
        List<DonutChartView.Segment> donutSegs = new ArrayList<>();

        // Prefer effective consumer entries (granular per-device view, storage excluded)
        if (effectiveConsumers != null && !effectiveConsumers.isEmpty()) {
            for (PowerNetworkViewModel.ConsumerEntry c : effectiveConsumers) {
                String label = c.count() > 1
                        ? c.deviceName() + " x" + c.count()
                        : c.deviceName();
                donutSegs.add(new DonutChartView.Segment(
                        label, c.percentage(), c.consumptionPerTick(), c.color()));
            }
            return donutSegs;
        }

        // Fallback: category-based LoadSegments
        List<PowerNetworkViewModel.LoadSegment> loadSegs = vm.loadSegments();
        if (loadSegs != null) {
            for (PowerNetworkViewModel.LoadSegment seg : loadSegs) {
                long feTick = Math.round(seg.percentage() / 100.0 * totalOutput);
                donutSegs.add(new DonutChartView.Segment(
                        seg.displayName(), seg.percentage(), feTick, seg.color()));
            }
        }
        return donutSegs;
    }

    private static void updateTaggedText(ViewGroup root, int tag, String text) {
        View v = root.findViewWithTag(tag);
        if (v instanceof TextView tv) tv.setText(text);
    }

    private static void updateTaggedTextViews(ViewGroup root, int tag,
                                               List<PowerNetworkViewModel.PowerKpi> kpis) {
        if (kpis == null) return;
        for (int i = 0; i < kpis.size(); i++) {
            int itemTag = tag + (i << 16);
            View v = root.findViewWithTag(itemTag);
            if (v instanceof TextView tv) tv.setText(kpis.get(i).value());
        }
    }

    // ===================== Main Build =====================

    static void build(ResourceTerminalFragment terminal, FrameLayout container,
                      int contentW, int contentH, boolean animate) {
        PowerNetworkViewModel vm = terminal.getBridge().getPowerViewModel();
        if (vm == null) return;

        terminal.getBridge().sanitizeSelectedExternalGroups();
        Set<String> selectedExtIds = terminal.getBridge().getSelectedExternalGroupIds();
        // 合并：自动识别的双向储能 ∪ 手动选择 → 统一有效排除/储能集合
        Set<String> autoDetectedIds = computeAutoDetectedStorageIds(vm.debugSnapshot());
        Set<String> effectiveExtIds = new LinkedHashSet<>(autoDetectedIds);
        if (selectedExtIds != null) effectiveExtIds.addAll(selectedExtIds);
        EffectiveStats effectiveStats = computeEffectiveStats(vm, effectiveExtIds);
        List<PowerNetworkViewModel.PowerKpi> effectiveKpis = buildEffectiveKpis(effectiveStats, vm, effectiveExtIds);
        List<PowerNetworkViewModel.ConsumerEntry> effectiveConsumers =
                computeEffectiveConsumers(vm, effectiveExtIds, effectiveStats.totalInputPerTick());

        int sectionGap = terminal.dp(6);
        int scrollbarW = terminal.dp(8);
        int innerW = Math.max(terminal.dp(160), contentW - scrollbarW);
        int kpiH = clamp(Math.round(contentH * 0.15f), terminal.dp(54), terminal.dp(100));

        // Column dimensions: left 33%, right 67%（移植自 computePowerContentLayout 的 12-column grid）
        int colGap = terminal.dp(6);
        int leftW = Math.max(terminal.dp(80), (int) (innerW * 0.33f));
        int rightW = Math.max(terminal.dp(80), innerW - leftW - colGap);

        // Scroll container
        ScrollView scroll = new ScrollView(terminal.getContext());
        scroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(scroll);

        LinearLayout content = new LinearLayout(terminal.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(content);

        // ===== 1. KPI section（全宽，有效 KPI 卡片） =====
        LinearLayout kpiSection = section(terminal, innerW, kpiH);
        buildKpiSection(terminal, kpiSection, innerW, kpiH, effectiveKpis);
        addSection(content, kpiSection, 0);

        // ===== 2. Two-column container =====
        LinearLayout columns = new LinearLayout(terminal.getContext());
        columns.setOrientation(LinearLayout.HORIZONTAL);

        // ---- Left column (33%): 容量余量饼图 + 外部控制台 ----
        LinearLayout leftCol = new LinearLayout(terminal.getContext());
        leftCol.setOrientation(LinearLayout.VERTICAL);

        int pieH = terminal.dp(200);
        LinearLayout pieSection = section(terminal, leftW, pieH);
        buildCapacityPieChart(terminal, pieSection, leftW, pieH, vm, effectiveStats, effectiveConsumers);
        addSection(leftCol, pieSection, 0);

        LinearLayout debugSection = section(terminal, leftW, ViewGroup.LayoutParams.WRAP_CONTENT);
        buildDebugPanel(terminal, debugSection, leftW, vm, selectedExtIds, autoDetectedIds);
        addSection(leftCol, debugSection, sectionGap);

        columns.addView(leftCol, new LinearLayout.LayoutParams(leftW, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- Right column (67%): 电网图表 + 设备列表 ----
        LinearLayout rightCol = new LinearLayout(terminal.getContext());
        rightCol.setOrientation(LinearLayout.VERTICAL);

        int chartH = clamp(Math.round(contentH * 0.20f), terminal.dp(60), terminal.dp(140));
        LinearLayout chartSection = section(terminal, rightW, chartH);
        buildGridChart(terminal, chartSection, rightW, chartH, effectiveStats, vm);
        addSection(rightCol, chartSection, 0);

        int deviceCount = effectiveConsumers != null ? effectiveConsumers.size() : 0;
        int deviceListH = terminal.dp(30) + deviceCount * scaledDp(terminal, DEVICE_ROW_HEIGHT_DP);
        LinearLayout deviceSection = section(terminal, rightW, deviceListH);
        deviceSection.setTag(TAG_DEVICE_SECTION);
        buildDeviceList(terminal, deviceSection, rightW, vm, effectiveConsumers);
        addSection(rightCol, deviceSection, sectionGap);

        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                rightW, ViewGroup.LayoutParams.WRAP_CONTENT);
        rightParams.leftMargin = colGap;
        columns.addView(rightCol, rightParams);

        addSection(content, columns, sectionGap);

        // 仅在首次加载 / 切换页面时播放瀑布入场动画
        if (animate) {
            staggerSlideIn(content, 0, 40, 250);
        }
    }

    // ===================== KPI Cards（有效 KPI） =====================

    private static void buildKpiSection(ResourceTerminalFragment terminal, LinearLayout section,
                                        int sectionWidth, int sectionHeight,
                                        List<PowerNetworkViewModel.PowerKpi> kpis) {
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        if (kpis == null || kpis.isEmpty()) return;

        int gap = terminal.dp(8);
        int cardCount = kpis.size();
        int innerW = sectionWidth - terminal.dp(16);
        int cardH = Math.max(terminal.dp(40), sectionHeight - terminal.dp(16));

        // Last card (excluded rate) is secondary — narrower and smaller text
        boolean hasSecondary = cardCount >= 4;
        int secondaryW = hasSecondary ? terminal.dp(72) : 0;
        int primaryCount = hasSecondary ? cardCount - 1 : cardCount;
        int primaryInnerW = innerW - (cardCount - 1) * gap - secondaryW;
        int primaryCardW = Math.max(terminal.dp(80), primaryInnerW / primaryCount);

        for (int i = 0; i < cardCount; i++) {
            PowerNetworkViewModel.PowerKpi kpi = kpis.get(i);
            boolean isSecondary = hasSecondary && i == cardCount - 1;

            LinearLayout cardView = new LinearLayout(terminal.getContext());
            cardView.setOrientation(LinearLayout.VERTICAL);
            cardView.setGravity(Gravity.CENTER_VERTICAL);
            cardView.setBackground(cardBackgroundStateful(cardView));
            addHoverScaleEffect(cardView);

            if (isSecondary) {
                cardView.setPadding(terminal.dp(6), terminal.dp(4), terminal.dp(6), terminal.dp(4));
                addText(cardView, kpi.label(), UiThemeTokens.TEXT_MUTED, 7, 0,
                        ViewGroup.LayoutParams.MATCH_PARENT, true);
                TextView valTv = addText(cardView, kpi.value(), statusColor(kpi.status()), 10, terminal.dp(1),
                        ViewGroup.LayoutParams.MATCH_PARENT, true);
                valTv.setTag(TAG_KPI_VALUES + (i << 16));
            } else {
                cardView.setPadding(terminal.dp(8), terminal.dp(6), terminal.dp(8), terminal.dp(6));
                addText(cardView, kpi.label(), UiThemeTokens.TEXT_MUTED, 9, 0,
                        ViewGroup.LayoutParams.MATCH_PARENT, true);
                TextView valTv = addText(cardView, kpi.value(), statusColor(kpi.status()), 14, terminal.dp(2),
                        ViewGroup.LayoutParams.MATCH_PARENT, true);
                valTv.setTag(TAG_KPI_VALUES + (i << 16));
            }

            int w = isSecondary ? secondaryW : primaryCardW;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(w, cardH);
            if (i > 0) params.leftMargin = gap;
            section.addView(cardView, params);

            // KPI 卡片点击 → 打开详情弹窗
            final int kpiIndex = i;
            cardView.setClickable(true);
            cardView.setFocusable(true);
            addPressScaleEffect(cardView);
            cardView.setOnClickListener(v -> showKpiDetailDialog(terminal, kpiIndex));
        }
    }

    // ===================== KPI Detail Dialog（KPI 卡片详情弹窗） =====================

    /**
     * 根据 KPI 索引打开对应的详情弹窗。
     * 0=有效输入  1=有效输出  2=储能  3=外储排除
     */
    private static void showKpiDetailDialog(ResourceTerminalFragment terminal, int kpiIndex) {
        if (terminal.isDialogOpen()) return;

        PowerNetworkViewModel vm = terminal.getBridge().getPowerViewModel();
        if (vm == null) return;

        // 计算有效统计
        terminal.getBridge().sanitizeSelectedExternalGroups();
        Set<String> selectedExtIds = terminal.getBridge().getSelectedExternalGroupIds();
        Set<String> autoDetectedIds = computeAutoDetectedStorageIds(vm.debugSnapshot());
        Set<String> effectiveExtIds = new LinkedHashSet<>(autoDetectedIds);
        if (selectedExtIds != null) effectiveExtIds.addAll(selectedExtIds);
        EffectiveStats stats = computeEffectiveStats(vm, effectiveExtIds);

        FrameLayout overlay = terminal.getDialogOverlay();
        final FrameLayout container = overlay != null ? overlay : terminal.getContentContainer();
        if (container == null) return;
        int cw = container.getWidth();
        int ch = container.getHeight();
        if (cw <= 0 || ch <= 0) return;

        // ---------- 半透明遮罩层 ----------
        View dimOverlay = new View(terminal.getContext());
        ShapeDrawable dimBg = new ShapeDrawable();
        dimBg.setColor(0xAA04070E);
        dimOverlay.setBackground(dimBg);
        dimOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---------- 对话框卡片 ----------
        int dialogW = Math.min(terminal.dp(400), cw - terminal.dp(20));
        int dialogH = Math.min(terminal.dp(320), ch - terminal.dp(20));

        LinearLayout dialog = new LinearLayout(terminal.getContext());
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setBackground(makeBackground(dialog, UiThemeTokens.PANEL_BG, UiThemeTokens.SECTION_BORDER, 10, 1));
        dialog.setClipChildren(false);
        dialog.setClipToPadding(false);
        dialog.setClickable(true);

        FrameLayout.LayoutParams dialogLp = new FrameLayout.LayoutParams(dialogW, dialogH);
        dialogLp.gravity = Gravity.CENTER;

        // 关闭动画
        Runnable dismiss = () -> {
            ObjectAnimator dimOut = ObjectAnimator.ofFloat(dimOverlay, View.ALPHA, 1f, 0f);
            dimOut.setDuration(150);
            dimOut.start();
            ObjectAnimator dialogOut = ObjectAnimator.ofPropertyValuesHolder(dialog,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.85f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.85f));
            dialogOut.setDuration(180);
            dialogOut.setInterpolator(TimeInterpolator.ACCELERATE);
            dialogOut.start();
            dialog.postDelayed(() -> {
                container.removeView(dimOverlay);
                container.removeView(dialog);
                terminal.setDialogOpen(false);
            }, 200);
        };

        dimOverlay.setOnClickListener(v -> dismiss.run());

        // ---------- 标题栏 ----------
        String[] titles = {
                tr("screen.resourceobserver.power.kpi.effective_input"),
                tr("screen.resourceobserver.power.kpi.effective_output"),
                tr("screen.resourceobserver.power.kpi.stored_energy"),
                tr("screen.resourceobserver.power.kpi.external_excluded")
        };
        String dialogTitle = kpiIndex >= 0 && kpiIndex < titles.length
                ? titles[kpiIndex] : titles[0];

        LinearLayout titleBar = new LinearLayout(terminal.getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(terminal.dp(12), terminal.dp(10), terminal.dp(12), 0);
        titleBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(terminal.getContext());
        title.setText(dialogTitle);
        title.setTextColor(UiThemeTokens.TITLE);
        title.setTextSize(12 * getTextScale());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleLp.setMarginEnd(terminal.dp(8));
        title.setLayoutParams(titleLp);
        titleBar.addView(title);

        TextView closeBtn = new TextView(terminal.getContext());
        closeBtn.setText("\u2715");
        closeBtn.setTextSize(11 * getTextScale());
        closeBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(terminal.dp(3), terminal.dp(3), terminal.dp(3), terminal.dp(3));
        closeBtn.setBackground(statefulBackground(closeBtn, 0x4016253C, UiThemeTokens.DIVIDER, 4, 1));
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        addPressScaleEffect(closeBtn);
        closeBtn.setOnClickListener(v -> dismiss.run());
        titleBar.addView(closeBtn, new LinearLayout.LayoutParams(terminal.dp(22), terminal.dp(22)));
        dialog.addView(titleBar);

        // ---------- 可滚动内容区 ----------
        FrameLayout scrollWrapper = new FrameLayout(terminal.getContext());
        scrollWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        ScrollView scroll = new ScrollView(terminal.getContext());
        scroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout body = new LinearLayout(terminal.getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(terminal.dp(12), terminal.dp(6), terminal.dp(12), terminal.dp(10));
        body.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(body);
        scrollWrapper.addView(scroll);
        addScrollFadeOverlays(terminal, scrollWrapper);
        dialog.addView(scrollWrapper);

        // 根据 KPI 类型填充内容
        populateKpiDialogBody(terminal, body, kpiIndex, vm, stats, effectiveExtIds);

        // 注册实时刷新回调
        terminal.setDialogContentRefresher(() -> {
            PowerNetworkViewModel latestVm = terminal.getBridge().getPowerViewModel();
            if (latestVm == null) return;
            Set<String> latestSelected = terminal.getBridge().getSelectedExternalGroupIds();
            Set<String> latestAuto = computeAutoDetectedStorageIds(latestVm.debugSnapshot());
            Set<String> latestEffective = new LinkedHashSet<>(latestAuto);
            if (latestSelected != null) latestEffective.addAll(latestSelected);
            EffectiveStats latestStats = computeEffectiveStats(latestVm, latestEffective);
            body.removeAllViews();
            populateKpiDialogBody(terminal, body, kpiIndex, latestVm, latestStats, latestEffective);
        });

        // ---------- 入场动画 ----------
        terminal.setDialogOpen(true);
        container.addView(dimOverlay);
        container.addView(dialog, dialogLp);

        dimOverlay.setAlpha(0f);
        ObjectAnimator dimIn = ObjectAnimator.ofFloat(dimOverlay, View.ALPHA, 0f, 1f);
        dimIn.setDuration(200);
        dimIn.start();

        dialog.setAlpha(0f);
        dialog.setScaleX(0.8f);
        dialog.setScaleY(0.8f);
        ObjectAnimator dialogIn = ObjectAnimator.ofPropertyValuesHolder(dialog,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 1f));
        dialogIn.setDuration(250);
        dialogIn.setInterpolator(TimeInterpolator.DECELERATE);
        dialogIn.start();
    }

    /**
     * 根据 KPI 索引填充弹窗内容。
     */
    private static void populateKpiDialogBody(ResourceTerminalFragment terminal, LinearLayout body,
                                               int kpiIndex, PowerNetworkViewModel vm,
                                               EffectiveStats stats, Set<String> effectiveExtIds) {
        switch (kpiIndex) {
            case 0 -> populateInputDetail(terminal, body, vm, stats, effectiveExtIds);
            case 1 -> populateOutputDetail(terminal, body, vm, stats, effectiveExtIds);
            case 2 -> populateStorageDetail(terminal, body, vm, stats, effectiveExtIds);
            case 3 -> populateExcludedDetail(terminal, body, vm, stats, effectiveExtIds);
        }
    }

    // ---- KPI 0: 有效输入详情 ----
    private static void populateInputDetail(ResourceTerminalFragment terminal, LinearLayout body,
                                             PowerNetworkViewModel vm, EffectiveStats stats,
                                             Set<String> effectiveExtIds) {
        PowerNetworkViewModel.DebugSnapshot snap = vm.debugSnapshot();
        if (snap == null) return;

        // 收集被排除外储组关联的接口 key，用于标注排除条目
        Set<String> excludedInterfaceKeys = new HashSet<>();
        if (snap.externalGroups() != null && effectiveExtIds != null) {
            for (PowerNetworkViewModel.ExternalGroup g : snap.externalGroups()) {
                if (effectiveExtIds.contains(g.extId()) && g.interfaceKeys() != null) {
                    excludedInterfaceKeys.addAll(g.interfaceKeys());
                }
            }
        }

        // 总输入概览
        addKpiSummaryRow(terminal, body,
                tr("screen.resourceobserver.power.kpi.total_input"),
                compact(vm.totalInputPerTick()) + " FE/t", UiThemeTokens.TEXT);
        addKpiSummaryRow(terminal, body,
                tr("screen.resourceobserver.power.kpi.effective_input"),
                compact(stats.totalInputPerTick()) + " FE/t", UiThemeTokens.EMERALD);
        if (stats.excludedInputPerTick() > 0) {
            addKpiSummaryRow(terminal, body,
                    tr("screen.resourceobserver.power.kpi_dialog.excluded_input"),
                    compact(stats.excludedInputPerTick()) + " FE/t", UiThemeTokens.TEXT_MUTED);
        }

        addDivider(terminal, body);

        // 输入设备列表（Plug）
        addText(body, tr("screen.resourceobserver.power.kpi_dialog.input_devices"),
                UiThemeTokens.TEXT, 10, terminal.dp(4), ViewGroup.LayoutParams.MATCH_PARENT, false);

        List<PowerNetworkViewModel.DeviceDebugEntry> inputs = snap.inputDevices();
        if (inputs == null || inputs.isEmpty()) {
            addText(body, tr("screen.resourceobserver.power.no_devices"),
                    UiThemeTokens.TEXT_MUTED, 9, terminal.dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);
            return;
        }

        long totalInput = stats.totalInputPerTick();
        for (PowerNetworkViewModel.DeviceDebugEntry dev : inputs) {
            long rate = Math.abs(dev.transferRate());
            double pct = totalInput > 0 ? (rate * 100.0) / totalInput : 0.0;
            String name = dev.deviceName() != null && !dev.deviceName().isBlank()
                    ? dev.deviceName() : dev.interfaceKey();
            boolean isExcluded = excludedInterfaceKeys.contains(dev.interfaceKey());

            LinearLayout row = new LinearLayout(terminal.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, terminal.dp(2), 0, terminal.dp(2));

            // 排除标记：左侧添加删除线风格标记
            if (isExcluded) {
                TextView tag = addText(row,
                        tr("screen.resourceobserver.power.kpi_dialog.tag_excluded"),
                        UiThemeTokens.ROSE, 7, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
                tag.setBackground(makeBackground(tag, 0x30FF6B6B, 0, 3, 0));
                tag.setPadding(terminal.dp(3), terminal.dp(1), terminal.dp(3), terminal.dp(1));
                LinearLayout.LayoutParams tagLp = (LinearLayout.LayoutParams) tag.getLayoutParams();
                tagLp.setMarginEnd(terminal.dp(4));
                tag.setLayoutParams(tagLp);
            }

            // 设备名（排除条目使用 muted 颜色）
            int nameColor = isExcluded ? UiThemeTokens.TEXT_MUTED : UiThemeTokens.TEXT;
            addText(row, name, nameColor, 9, 0,
                    0, true).setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            // 传输速率（排除条目使用 muted 颜色）
            int rateColor = isExcluded ? UiThemeTokens.TEXT_MUTED : UiThemeTokens.EMERALD;
            addText(row, compact(rate) + " FE/t", rateColor, 9, 0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, true);

            // 百分比
            TextView pctTv = addText(row, String.format(Locale.ROOT, " %.1f%%", pct),
                    UiThemeTokens.TEXT_MUTED, 8, 0, terminal.dp(45), true);
            pctTv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

            body.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // Plug 数量统计
        addDivider(terminal, body);
        addText(body, snap.plugCount() + " " + tr("screen.resourceobserver.power.kpi_dialog.plug_count"),
                UiThemeTokens.TEXT_MUTED, 8, terminal.dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);
    }

    // ---- KPI 1: 有效输出详情 ----
    private static void populateOutputDetail(ResourceTerminalFragment terminal, LinearLayout body,
                                              PowerNetworkViewModel vm, EffectiveStats stats,
                                              Set<String> effectiveExtIds) {
        PowerNetworkViewModel.DebugSnapshot snap = vm.debugSnapshot();
        if (snap == null) return;

        // 总输出概览
        addKpiSummaryRow(terminal, body,
                tr("screen.resourceobserver.power.kpi.total_output"),
                compact(vm.totalOutputPerTick()) + " FE/t", UiThemeTokens.TEXT);
        addKpiSummaryRow(terminal, body,
                tr("screen.resourceobserver.power.kpi.effective_output"),
                compact(stats.totalOutputPerTick()) + " FE/t", UiThemeTokens.AMBER);
        if (stats.excludedOutputPerTick() > 0) {
            addKpiSummaryRow(terminal, body,
                    tr("screen.resourceobserver.power.kpi_dialog.excluded_output"),
                    compact(stats.excludedOutputPerTick()) + " FE/t", UiThemeTokens.TEXT_MUTED);
        }

        addDivider(terminal, body);

        // 用电器列表（按类型聚合）
        addText(body, tr("screen.resourceobserver.power.kpi_dialog.consumer_breakdown"),
                UiThemeTokens.TEXT, 10, terminal.dp(4), ViewGroup.LayoutParams.MATCH_PARENT, false);

        List<PowerNetworkViewModel.ConsumerEntry> consumers =
                computeEffectiveConsumers(vm, effectiveExtIds, stats.totalInputPerTick());

        if (consumers == null || consumers.isEmpty()) {
            addText(body, tr("screen.resourceobserver.power.no_data"),
                    UiThemeTokens.TEXT_MUTED, 9, terminal.dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);
            return;
        }

        for (PowerNetworkViewModel.ConsumerEntry c : consumers) {
            LinearLayout row = new LinearLayout(terminal.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, terminal.dp(2), 0, terminal.dp(2));

            // 颜色圆点
            View dot = new View(terminal.getContext());
            dot.setBackground(colorDot(dot, c.color()));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(terminal.dp(6), terminal.dp(6));
            dotLp.setMarginEnd(terminal.dp(4));
            row.addView(dot, dotLp);

            // 设备名 + 数量
            String label = c.count() > 1 ? c.deviceName() + " ×" + c.count() : c.deviceName();
            addText(row, label, UiThemeTokens.TEXT, 9, 0,
                    0, true).setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            // 模组
            if (c.modName() != null && !c.modName().isBlank()) {
                TextView modTv = addText(row, c.modName(), UiThemeTokens.TEXT_MUTED, 7,
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
                LinearLayout.LayoutParams modLp = (LinearLayout.LayoutParams) modTv.getLayoutParams();
                modLp.setMarginEnd(terminal.dp(6));
                modTv.setLayoutParams(modLp);
            }

            // 消耗速率
            addText(row, compact(c.consumptionPerTick()) + " FE/t", UiThemeTokens.AMBER, 9, 0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, true);

            // 占比
            TextView pctTv = addText(row, String.format(Locale.ROOT, " %.1f%%", c.percentage()),
                    UiThemeTokens.TEXT_MUTED, 8, 0, terminal.dp(45), true);
            pctTv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

            body.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // Point 数量统计
        addDivider(terminal, body);
        addText(body, snap.pointCount() + " " + tr("screen.resourceobserver.power.kpi_dialog.point_count"),
                UiThemeTokens.TEXT_MUTED, 8, terminal.dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);
    }

    // ---- KPI 2: 储能详情 ----
    private static void populateStorageDetail(ResourceTerminalFragment terminal, LinearLayout body,
                                               PowerNetworkViewModel vm, EffectiveStats stats,
                                               Set<String> effectiveExtIds) {
        PowerNetworkViewModel.DebugSnapshot snap = vm.debugSnapshot();

        // ===== 网络内部储能 =====
        addText(body, tr("screen.resourceobserver.power.kpi_dialog.internal_storage"),
                UiThemeTokens.TEXT, 10, terminal.dp(2), ViewGroup.LayoutParams.MATCH_PARENT, false);

        long internalStored = vm.totalStored();
        long internalCap = vm.totalCapacity();
        int storageBlockCount = snap != null ? snap.storageCount() : 0;

        if (storageBlockCount <= 0) {
            // 网络没有放置储能方块——特别标注
            addNoCapacityBanner(terminal, body,
                    tr("screen.resourceobserver.power.kpi_dialog.no_internal_capacity"));
        } else {
            double internalRatio = (double) internalStored / internalCap;
            addKpiSummaryRow(terminal, body,
                    tr("screen.resourceobserver.power.kpi_dialog.stored"),
                    compact(internalStored) + " / " + compact(internalCap) + " FE",
                    internalRatio > 0.9 ? UiThemeTokens.EMERALD
                            : (internalRatio > 0.1 ? UiThemeTokens.CYAN : UiThemeTokens.ROSE));
            addFillBar(terminal, body, internalRatio, UiThemeTokens.CYAN);

            if (storageBlockCount > 0) {
                addText(body, storageBlockCount + " " + tr("screen.resourceobserver.power.kpi_dialog.storage_count"),
                        UiThemeTokens.TEXT_MUTED, 8, terminal.dp(1), ViewGroup.LayoutParams.MATCH_PARENT, true);
            }
        }

        // ===== 外部容器储能 =====
        if (snap != null && snap.externalGroups() != null && effectiveExtIds != null) {
            List<PowerNetworkViewModel.ExternalGroup> extGroups = snap.externalGroups();
            boolean hasEffectiveExt = false;
            for (PowerNetworkViewModel.ExternalGroup g : extGroups) {
                if (effectiveExtIds.contains(g.extId())) {
                    hasEffectiveExt = true;
                    break;
                }
            }

            if (hasEffectiveExt) {
                addDivider(terminal, body);
                addText(body, tr("screen.resourceobserver.power.kpi_dialog.external_storage"),
                        UiThemeTokens.TEXT, 10, terminal.dp(4), ViewGroup.LayoutParams.MATCH_PARENT, false);

                long extTotalStored = 0, extTotalCap = 0;
                for (PowerNetworkViewModel.ExternalGroup g : extGroups) {
                    if (!effectiveExtIds.contains(g.extId())) continue;
                    long gs = Math.max(0L, g.stored());
                    long gc = Math.max(0L, g.capacity());
                    extTotalStored += gs;
                    extTotalCap += gc;

                    String gName = g.displayName() != null ? g.displayName() : g.extId();

                    if (gc <= 0) {
                        // 外储容器无容量——特别标注
                        LinearLayout noCapRow = new LinearLayout(terminal.getContext());
                        noCapRow.setOrientation(LinearLayout.HORIZONTAL);
                        noCapRow.setGravity(Gravity.CENTER_VERTICAL);
                        noCapRow.setPadding(0, terminal.dp(3), 0, terminal.dp(3));

                        addText(noCapRow, gName, UiThemeTokens.TEXT_MUTED, 9, 0,
                                0, true).setLayoutParams(new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                        // 无容量标签
                        TextView tag = addText(noCapRow,
                                tr("screen.resourceobserver.power.kpi_dialog.no_capacity"),
                                UiThemeTokens.AMBER, 7, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
                        tag.setBackground(makeBackground(tag, 0x30FFB020, 0, 3, 0));
                        tag.setPadding(terminal.dp(3), terminal.dp(1), terminal.dp(3), terminal.dp(1));

                        body.addView(noCapRow, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    } else {
                        double ratio = (double) gs / gc;

                        LinearLayout row = new LinearLayout(terminal.getContext());
                        row.setOrientation(LinearLayout.VERTICAL);
                        row.setPadding(0, terminal.dp(3), 0, terminal.dp(3));

                        // 名称 + 数值
                        LinearLayout nameRow = new LinearLayout(terminal.getContext());
                        nameRow.setOrientation(LinearLayout.HORIZONTAL);
                        nameRow.setGravity(Gravity.CENTER_VERTICAL);
                        addText(nameRow, gName, UiThemeTokens.TEXT, 9, 0,
                                0, true).setLayoutParams(new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                        addText(nameRow, compact(gs) + " / " + compact(gc) + " FE",
                                UiThemeTokens.CYAN, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
                        addText(nameRow, String.format(Locale.ROOT, " %.0f%%", ratio * 100),
                                UiThemeTokens.TEXT_MUTED, 8, 0, terminal.dp(36), true);
                        row.addView(nameRow, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                        // 进度条
                        addFillBar(terminal, row, ratio, UiThemeTokens.CYAN);

                        body.addView(row, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    }
                }

                // 外储合计
                addDivider(terminal, body);
                addKpiSummaryRow(terminal, body,
                        tr("screen.resourceobserver.power.kpi_dialog.external_total"),
                        compact(extTotalStored) + " / " + compact(extTotalCap) + " FE",
                        UiThemeTokens.CYAN);
            }
        }

        // ===== 总计（内部 + 外部） =====
        long[] combined = computeCombinedStorage(vm, effectiveExtIds);
        boolean hasAnyRealStorage = storageBlockCount > 0 || (snap != null && snap.externalGroups() != null
                && snap.externalGroups().stream().anyMatch(g -> effectiveExtIds.contains(g.extId()) && g.capacity() > 0));
        if (!hasAnyRealStorage) {
            // 完全无储能容量（网络没有储能方块，也没有有容量的外储）
            addDivider(terminal, body);
            addNoCapacityBanner(terminal, body,
                    tr("screen.resourceobserver.power.kpi_dialog.no_total_capacity"));
        } else {
            double combinedRatio = combined[1] > 0 ? (double) combined[0] / combined[1] : 0.0;
            addDivider(terminal, body);
            addKpiSummaryRow(terminal, body,
                    tr("screen.resourceobserver.power.kpi_dialog.combined_total"),
                    compact(combined[0]) + " / " + compact(combined[1]) + " FE",
                    combinedRatio > 0.5 ? UiThemeTokens.EMERALD : UiThemeTokens.AMBER);
            addFillBar(terminal, body, combinedRatio,
                    combinedRatio > 0.5 ? UiThemeTokens.EMERALD : UiThemeTokens.AMBER);
        }
    }

    // ---- KPI 3: 外储排除详情 ----
    private static void populateExcludedDetail(ResourceTerminalFragment terminal, LinearLayout body,
                                                PowerNetworkViewModel vm, EffectiveStats stats,
                                                Set<String> effectiveExtIds) {
        // 排除概览
        addKpiSummaryRow(terminal, body,
                tr("screen.resourceobserver.power.kpi_dialog.excluded_input"),
                compact(stats.excludedInputPerTick()) + " FE/t", UiThemeTokens.AMBER);
        addKpiSummaryRow(terminal, body,
                tr("screen.resourceobserver.power.kpi_dialog.excluded_output"),
                compact(stats.excludedOutputPerTick()) + " FE/t", UiThemeTokens.AMBER);
        addKpiSummaryRow(terminal, body,
                tr("screen.resourceobserver.power.kpi_dialog.excluded_total"),
                compact(stats.excludedTotalPerTick()) + " FE/t", UiThemeTokens.ROSE);

        addDivider(terminal, body);

        // 排除的组列表
        addText(body, tr("screen.resourceobserver.power.kpi_dialog.excluded_groups"),
                UiThemeTokens.TEXT, 10, terminal.dp(4), ViewGroup.LayoutParams.MATCH_PARENT, false);

        PowerNetworkViewModel.DebugSnapshot snap = vm.debugSnapshot();
        if (snap == null || snap.externalGroups() == null || effectiveExtIds == null || effectiveExtIds.isEmpty()) {
            addText(body, tr("screen.resourceobserver.power.external_console.empty.groups"),
                    UiThemeTokens.TEXT_MUTED, 9, terminal.dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);
            return;
        }

        // 找出每个被排除组的接口及其速率
        Set<String> excludedKeys = new HashSet<>();
        for (PowerNetworkViewModel.ExternalGroup g : snap.externalGroups()) {
            if (effectiveExtIds.contains(g.extId()) && g.interfaceKeys() != null) {
                excludedKeys.addAll(g.interfaceKeys());
            }
        }

        // 按组统计排除的输入/输出速率
        for (PowerNetworkViewModel.ExternalGroup g : snap.externalGroups()) {
            if (!effectiveExtIds.contains(g.extId())) continue;

            String gName = g.displayName() != null ? g.displayName() : g.extId();

            // 计算该组的输入/输出速率
            long groupInput = 0, groupOutput = 0;
            if (g.interfaceKeys() != null) {
                for (String key : g.interfaceKeys()) {
                    // 在输入设备中查找
                    if (snap.inputDevices() != null) {
                        for (PowerNetworkViewModel.DeviceDebugEntry dev : snap.inputDevices()) {
                            if (key.equals(dev.interfaceKey())) {
                                groupInput += Math.abs(dev.transferRate());
                            }
                        }
                    }
                    // 在输出设备中查找
                    if (snap.outputDevices() != null) {
                        for (PowerNetworkViewModel.DeviceDebugEntry dev : snap.outputDevices()) {
                            if (key.equals(dev.interfaceKey())) {
                                groupOutput += Math.abs(dev.transferRate());
                            }
                        }
                    }
                }
            }

            LinearLayout row = new LinearLayout(terminal.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, terminal.dp(3), 0, terminal.dp(3));

            // 组名
            addText(row, gName, UiThemeTokens.TEXT, 9, 0,
                    0, true).setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            // 输入/输出
            if (groupInput > 0) {
                addText(row, "↓" + compact(groupInput), UiThemeTokens.EMERALD, 8, 0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, true);
            }
            if (groupOutput > 0) {
                TextView outTv = addText(row, " ↑" + compact(groupOutput), UiThemeTokens.AMBER, 8, 0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, true);
            }

            // 储能状态
            double ratio = g.capacity() > 0 ? (double) g.stored() / g.capacity() : 0.0;
            addText(row, String.format(Locale.ROOT, " %.0f%%", ratio * 100),
                    UiThemeTokens.TEXT_MUTED, 8, 0, terminal.dp(36), true);

            body.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    // ---- KPI 弹窗辅助方法 ----

    /** 概览行：标签 + 值，左右分布 */
    private static void addKpiSummaryRow(ResourceTerminalFragment terminal, LinearLayout parent,
                                          String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(terminal.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, terminal.dp(2), 0, terminal.dp(2));

        addText(row, label, UiThemeTokens.TEXT_MUTED, 9, 0,
                0, true).setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        addText(row, value, valueColor, 10, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 细线分割线 */
    private static void addDivider(ResourceTerminalFragment terminal, LinearLayout parent) {
        View divider = new View(terminal.getContext());
        ShapeDrawable bg = new ShapeDrawable();
        bg.setColor(UiThemeTokens.DIVIDER);
        divider.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = terminal.dp(4);
        lp.bottomMargin = terminal.dp(4);
        parent.addView(divider, lp);
    }

    /** 无容量提示（低调 info 风格） */
    private static void addNoCapacityBanner(ResourceTerminalFragment terminal, LinearLayout parent, String message) {
        TextView hint = addText(parent, "ℹ " + message, UiThemeTokens.TEXT_MUTED, 8,
                terminal.dp(3), ViewGroup.LayoutParams.MATCH_PARENT, true);
        hint.setPadding(terminal.dp(6), terminal.dp(3), terminal.dp(6), terminal.dp(3));
    }

    /** 储能进度条（使用 weight 方式避免刷新闪烁） */
    private static void addFillBar(ResourceTerminalFragment terminal, LinearLayout parent,
                                    double ratio, int fillColor) {
        int barH = terminal.dp(4);
        double clampedRatio = Math.max(0.0, Math.min(1.0, ratio));

        // 使用 LinearLayout + weightSum 直接设定比例，无需 post 延迟
        LinearLayout barTrack = new LinearLayout(terminal.getContext());
        barTrack.setOrientation(LinearLayout.HORIZONTAL);
        barTrack.setWeightSum(1.0f);
        barTrack.setBackground(makeBackground(barTrack, UiThemeTokens.CARD_BG, 0, 2, 0));

        // 填充部分
        if (clampedRatio > 0.001) {
            View fill = new View(terminal.getContext());
            ShapeDrawable fillBg = new ShapeDrawable();
            fillBg.setColor(fillColor);
            fillBg.setCornerRadius(terminal.dp(2));
            fill.setBackground(fillBg);
            barTrack.addView(fill, new LinearLayout.LayoutParams(
                    0, barH, (float) clampedRatio));
        }

        // 空白部分
        float remaining = 1.0f - (float) clampedRatio;
        if (remaining > 0.001f) {
            View spacer = new View(terminal.getContext());
            barTrack.addView(spacer, new LinearLayout.LayoutParams(
                    0, barH, remaining));
        }

        LinearLayout.LayoutParams containerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barH);
        containerLp.topMargin = terminal.dp(2);
        containerLp.bottomMargin = terminal.dp(1);
        parent.addView(barTrack, containerLp);
    }

    // ===================== Capacity Donut Chart（容量余量甜甜圈图，左面板） =====================

    /**
     * 容量甜甜圈图 —— 以发电量为 100%，展示各负载段占比与余量。
     * 合并了原 buildLoadSummary + buildOverloadAlertCard。
     */
    private static void buildCapacityPieChart(ResourceTerminalFragment terminal, LinearLayout section,
                                               int sectionWidth, int sectionHeight,
                                               PowerNetworkViewModel vm, EffectiveStats stats,
                                               List<PowerNetworkViewModel.ConsumerEntry> effectiveConsumers) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(6), terminal.dp(6), terminal.dp(6), terminal.dp(6));
        section.setGravity(Gravity.CENTER_HORIZONTAL);

        PowerNetworkViewModel.OverloadInfo info = stats.overloadInfo();
        double headroom = info != null ? info.headroomPercent() : 100.0;

        boolean critical = headroom < 5;
        boolean warning = headroom < 15;
        int accentColor = critical ? UiThemeTokens.ROSE
                : (warning ? UiThemeTokens.AMBER : UiThemeTokens.EMERALD);

        // ── Build donut segments (prefers effective consumers, falls back to categories) ──
        List<DonutChartView.Segment> donutSegs = buildDonutSegments(vm, stats, effectiveConsumers);

        // ── Center text ──
        String centerValue = String.format(Locale.ROOT, "%.1f%%", headroom);
        String centerLabel = tr("screen.resourceobserver.power.section.load_chart");

        // ── DonutChartView ──
        DonutChartView donut = new DonutChartView(terminal.getContext());
        donut.setTag(TAG_DONUT);
        donut.setData(donutSegs, headroom, centerValue, centerLabel, accentColor);

        int pieSize = Math.min(sectionWidth - terminal.dp(16), sectionHeight - terminal.dp(56));
        pieSize = Math.max(terminal.dp(64), pieSize);
        LinearLayout.LayoutParams donutLp = new LinearLayout.LayoutParams(pieSize, pieSize);
        donutLp.gravity = Gravity.CENTER_HORIZONTAL;
        donutLp.topMargin = terminal.dp(4);
        section.addView(donut, donutLp);

        // ── Summary line: Generation | Load | Risk ──
        String genText = compact(stats.totalInputPerTick()) + " FE/t";
        String loadText = compact(stats.totalOutputPerTick()) + " FE/t";
        String riskLabel = critical
                ? tr("screen.resourceobserver.power.overload.risk.critical")
                : (warning ? tr("screen.resourceobserver.power.overload.risk.warning")
                        : tr("screen.resourceobserver.power.overload.risk.low"));

        LinearLayout summaryRow = new LinearLayout(terminal.getContext());
        summaryRow.setOrientation(LinearLayout.HORIZONTAL);
        summaryRow.setGravity(Gravity.CENTER);

        addText(summaryRow, tr("screen.resourceobserver.power.generation") + " ",
                UiThemeTokens.TEXT_MUTED, 8, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        TextView genTv = addText(summaryRow, genText,
                UiThemeTokens.EMERALD, 8, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        genTv.setTag(TAG_SUMMARY_GEN);
        addText(summaryRow, "  " + tr("screen.resourceobserver.power.peak_load") + " ",
                UiThemeTokens.TEXT_MUTED, 8, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        TextView loadTv = addText(summaryRow, loadText,
                UiThemeTokens.AMBER, 8, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        loadTv.setTag(TAG_SUMMARY_LOAD);

        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sumLp.topMargin = terminal.dp(4);
        sumLp.gravity = Gravity.CENTER_HORIZONTAL;
        section.addView(summaryRow, sumLp);

        // ── Risk + Reserve line ──
        long reserve = info != null ? info.reservePerTick() : 0L;
        String riskLine = riskLabel + "  |  " + tr("screen.resourceobserver.power.overload.reserve",
                compact(reserve) + " FE/t");
        TextView riskTv = addText(section, riskLine, accentColor, 7, terminal.dp(2),
                ViewGroup.LayoutParams.MATCH_PARENT, true);
        riskTv.setTag(TAG_RISK_LINE);

        // ── Legend (derived from donut segments — may be consumers or categories) ──
        if (!donutSegs.isEmpty()) {
            // Use wrap layout: multiple rows if needed
            LinearLayout legendContainer = new LinearLayout(terminal.getContext());
            legendContainer.setOrientation(LinearLayout.VERTICAL);
            legendContainer.setGravity(Gravity.CENTER_HORIZONTAL);

            LinearLayout legendRow = new LinearLayout(terminal.getContext());
            legendRow.setOrientation(LinearLayout.HORIZONTAL);
            legendRow.setGravity(Gravity.CENTER);
            int rowItemCount = 0;
            int maxPerRow = 3;

            for (int i = 0; i < donutSegs.size(); i++) {
                DonutChartView.Segment seg = donutSegs.get(i);
                if (rowItemCount >= maxPerRow) {
                    legendContainer.addView(legendRow, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    legendRow = new LinearLayout(terminal.getContext());
                    legendRow.setOrientation(LinearLayout.HORIZONTAL);
                    legendRow.setGravity(Gravity.CENTER);
                    rowItemCount = 0;
                }
                View legDot = new View(terminal.getContext());
                legDot.setBackground(colorDot(legDot, seg.color()));
                LinearLayout.LayoutParams ldp = new LinearLayout.LayoutParams(terminal.dp(5), terminal.dp(5));
                if (rowItemCount > 0) ldp.leftMargin = terminal.dp(6);
                ldp.topMargin = terminal.dp(1);
                legendRow.addView(legDot, ldp);
                addText(legendRow, seg.label(),
                        UiThemeTokens.TEXT_MUTED, 7, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
                rowItemCount++;
            }
            if (rowItemCount > 0) {
                legendContainer.addView(legendRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            LinearLayout.LayoutParams legendParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            legendParams.topMargin = terminal.dp(2);
            legendParams.gravity = Gravity.CENTER_HORIZONTAL;
            section.addView(legendContainer, legendParams);
        }
    }

            // ===================== Debug Panel / External Console（外部控制台，左面板） =====================

    /**
     * 外部控制台面板 —— 显示网络统计概览 + 齿轮按钮打开接口列表 Dialog。
     * 移植自 PowerDebugPanelRenderer。
     */
    private static void buildDebugPanel(ResourceTerminalFragment terminal, LinearLayout section,
                                        int sectionWidth, PowerNetworkViewModel vm,
                                        Set<String> selectedExtIds,
                                        Set<String> autoDetectedIds) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(6), terminal.dp(6), terminal.dp(6), terminal.dp(6));
        section.setBackground(makeBackground(section, 0xCC0A1324, 0xFF284364, 6, 1));

        PowerNetworkViewModel.DebugSnapshot snap = vm.debugSnapshot();
        if (snap == null) {
            addText(section, tr("screen.resourceobserver.power.external_console.title"),
                    UiThemeTokens.TEXT, 10, 0, ViewGroup.LayoutParams.MATCH_PARENT, true);
            addText(section, tr("screen.resourceobserver.power.no_data"),
                    UiThemeTokens.TEXT_MUTED, 9, terminal.dp(2),
                    ViewGroup.LayoutParams.MATCH_PARENT, true);
            return;
        }

        // Title row: 外储控制台 + ⚙ gear button
        LinearLayout titleRow = new LinearLayout(terminal.getContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        addText(titleRow, tr("screen.resourceobserver.power.external_console.title"),
                UiThemeTokens.CYAN, 10, 0, 0, true);
        View titleLabel = titleRow.getChildAt(titleRow.getChildCount() - 1);
        if (titleLabel != null) {
            titleLabel.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        }

        TextView gearBtn = new TextView(terminal.getContext());
        gearBtn.setText("\u2699");
        gearBtn.setTextSize(11 * getTextScale());
        gearBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
        gearBtn.setGravity(Gravity.CENTER);
        gearBtn.setPadding(terminal.dp(3), terminal.dp(1), terminal.dp(3), terminal.dp(1));
        gearBtn.setBackground(statefulBackground(gearBtn, 0x3016253C, UiThemeTokens.DIVIDER, 4, 1));
        gearBtn.setClickable(true);
        gearBtn.setFocusable(true);
        addPressScaleEffect(gearBtn);
        gearBtn.setOnClickListener(v ->
                showInterfaceListDialog(terminal, vm, selectedExtIds, autoDetectedIds));
        titleRow.addView(gearBtn, new LinearLayout.LayoutParams(terminal.dp(22), terminal.dp(22)));

        section.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Metrics line: IN | OUT
        String metricsLine = tr("screen.resourceobserver.power.external_console.metric.input")
                + " " + compact(snap.inputRate()) + " FE/t  "
                + tr("screen.resourceobserver.power.external_console.metric.output")
                + " " + compact(snap.outputRate()) + " FE/t";
        addText(section, metricsLine, UiThemeTokens.TEXT_MUTED, 8, terminal.dp(2),
                ViewGroup.LayoutParams.MATCH_PARENT, true);

        // Container summary: "5 个容器 ▸ 自动:2 手动:1"
        int autoCount = autoDetectedIds != null ? autoDetectedIds.size() : 0;
        int manualCount = selectedExtIds != null ? selectedExtIds.size() : 0;
        int groupCount = snap.externalGroups() != null ? snap.externalGroups().size() : 0;
        String summaryText = groupCount + " "
                + tr("screen.resourceobserver.power.external_console.summary.containers")
                + "  \u25B8 " + tr("screen.resourceobserver.power.external_console.summary.auto") + ":" + autoCount
                + "  " + tr("screen.resourceobserver.power.external_console.summary.manual") + ":" + manualCount;
        addText(section, summaryText, UiThemeTokens.TEXT_MUTED, 8, terminal.dp(1),
                ViewGroup.LayoutParams.MATCH_PARENT, true);
    }

    // ===================== Interface List Dialog（接口列表弹窗） =====================

    /**
     * 显示接口列表弹窗 —— 包含所有 Flux 接口设备和外部储能组。
     * 玩家可在此手动选择/取消选择外部存储组。
     */
    private static void showInterfaceListDialog(ResourceTerminalFragment terminal,
                                                PowerNetworkViewModel vm,
                                                Set<String> selectedExtIds,
                                                Set<String> autoDetectedIds) {
        FrameLayout overlay = terminal.getDialogOverlay();
        final FrameLayout container = overlay != null ? overlay : terminal.getContentContainer();
        if (container == null) return;

        int cw = container.getWidth();
        int ch = container.getHeight();
        if (cw <= 0 || ch <= 0) return;

        // ---------- 半透明遮罩层 ----------
        View dimOverlay = new View(terminal.getContext());
        ShapeDrawable dimBg = new ShapeDrawable();
        dimBg.setColor(0xAA04070E);
        dimOverlay.setBackground(dimBg);
        dimOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---------- 对话框卡片 ----------
        int dialogW = Math.min(terminal.dp(380), cw - terminal.dp(20));
        int dialogH = Math.min(terminal.dp(300), ch - terminal.dp(20));

        LinearLayout dialog = new LinearLayout(terminal.getContext());
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setBackground(makeBackground(dialog, UiThemeTokens.PANEL_BG, UiThemeTokens.SECTION_BORDER, 10, 1));
        dialog.setClipChildren(false);
        dialog.setClipToPadding(false);
        dialog.setClickable(true);

        FrameLayout.LayoutParams dialogLp = new FrameLayout.LayoutParams(dialogW, dialogH);
        dialogLp.gravity = Gravity.CENTER;

        // 关闭动画 & 移除
        Runnable dismiss = () -> {
            ObjectAnimator dimOut = ObjectAnimator.ofFloat(dimOverlay, View.ALPHA, 1f, 0f);
            dimOut.setDuration(150);
            dimOut.start();
            ObjectAnimator dialogOut = ObjectAnimator.ofPropertyValuesHolder(dialog,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.85f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.85f));
            dialogOut.setDuration(180);
            dialogOut.setInterpolator(TimeInterpolator.ACCELERATE);
            dialogOut.start();
            dialog.postDelayed(() -> {
                container.removeView(dimOverlay);
                container.removeView(dialog);
                terminal.setDialogOpen(false);
                View root = terminal.getView();
                if (root != null) {
                    root.post(() -> terminal.onDataChanged());
                }
            }, 200);
        };

        dimOverlay.setOnClickListener(v -> dismiss.run());

        // ---------- 标题栏 ----------
        LinearLayout titleBar = new LinearLayout(terminal.getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(terminal.dp(12), terminal.dp(10), terminal.dp(12), 0);
        titleBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(terminal.getContext());
        title.setText(tr("screen.resourceobserver.power.external_console.section.interfaces"));
        title.setTextColor(UiThemeTokens.TITLE);
        title.setTextSize(12 * getTextScale());
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleLp.setMarginEnd(terminal.dp(8));
        title.setLayoutParams(titleLp);
        titleBar.addView(title);

        TextView closeBtn = new TextView(terminal.getContext());
        closeBtn.setText("\u2715");
        closeBtn.setTextSize(11 * getTextScale());
        closeBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(terminal.dp(3), terminal.dp(3), terminal.dp(3), terminal.dp(3));
        closeBtn.setBackground(statefulBackground(closeBtn, 0x4016253C, UiThemeTokens.DIVIDER, 4, 1));
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        addPressScaleEffect(closeBtn);
        closeBtn.setOnClickListener(v -> dismiss.run());
        titleBar.addView(closeBtn, new LinearLayout.LayoutParams(terminal.dp(22), terminal.dp(22)));
        dialog.addView(titleBar);

        // ---------- 可滚动内容区（带上下渐隐遮罩） ----------
        FrameLayout scrollWrapper = new FrameLayout(terminal.getContext());
        scrollWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        ScrollView scroll = new ScrollView(terminal.getContext());
        scroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout body = new LinearLayout(terminal.getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(terminal.dp(12), terminal.dp(6), terminal.dp(12), terminal.dp(10));
        body.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(body);
        scrollWrapper.addView(scroll);
        addScrollFadeOverlays(terminal, scrollWrapper);
        dialog.addView(scrollWrapper);

        // 填充初始内容
        populateInterfaceListBody(terminal, body, vm, selectedExtIds, autoDetectedIds);

        // 注册实时刷新回调
        terminal.setDialogContentRefresher(() -> {
            PowerNetworkViewModel latestVm = terminal.getBridge().getPowerViewModel();
            if (latestVm == null) return;
            Set<String> latestSelected = terminal.getBridge().getSelectedExternalGroupIds();
            Set<String> latestAuto = computeAutoDetectedStorageIds(
                    latestVm.debugSnapshot());
            body.removeAllViews();
            populateInterfaceListBody(terminal, body, latestVm, latestSelected, latestAuto);
        });

        // ---------- 添加到容器并播放入场动画 ----------
        terminal.setDialogOpen(true);
        container.addView(dimOverlay);
        container.addView(dialog, dialogLp);

        dimOverlay.setAlpha(0f);
        ObjectAnimator dimIn = ObjectAnimator.ofFloat(dimOverlay, View.ALPHA, 0f, 1f);
        dimIn.setDuration(200);
        dimIn.start();

        dialog.setAlpha(0f);
        dialog.setScaleX(0.8f);
        dialog.setScaleY(0.8f);
        ObjectAnimator dialogIn = ObjectAnimator.ofPropertyValuesHolder(dialog,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.8f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.8f, 1f));
        dialogIn.setDuration(250);
        dialogIn.setInterpolator(TimeInterpolator.DECELERATE);
        dialogIn.start();
    }

            /**
     * 填充接口列表 Dialog 的内容。
     */
    private static void populateInterfaceListBody(ResourceTerminalFragment terminal,
                                                  LinearLayout body,
                                                  PowerNetworkViewModel vm,
                                                  Set<String> selectedExtIds,
                                                  Set<String> autoDetectedIds) {
        PowerNetworkViewModel.DebugSnapshot snap = vm.debugSnapshot();
        if (snap == null) {
            addText(body, tr("screen.resourceobserver.power.no_data"),
                    UiThemeTokens.TEXT_MUTED, 9, 0,
                    ViewGroup.LayoutParams.MATCH_PARENT, true);
            return;
        }

        // ===== 接口设备列表 =====
        addText(body, tr("screen.resourceobserver.power.external_console.section.interfaces"),
                UiThemeTokens.TEXT, 9, 0, ViewGroup.LayoutParams.MATCH_PARENT, true);

        List<PowerNetworkViewModel.DeviceDebugEntry> allDevices = new ArrayList<>();
        if (snap.inputDevices() != null) allDevices.addAll(snap.inputDevices());
        if (snap.outputDevices() != null) allDevices.addAll(snap.outputDevices());

        for (PowerNetworkViewModel.DeviceDebugEntry dev : allDevices) {
            List<String> relatedIds = new ArrayList<>();
            if (dev.externalRefs() != null) {
                for (PowerNetworkViewModel.ExternalRef ref : dev.externalRefs()) {
                    relatedIds.add(ref.extId());
                }
            }

            boolean isInput = snap.inputDevices() != null && snap.inputDevices().contains(dev);
            boolean isManuallySelected = selectedExtIds != null && !relatedIds.isEmpty()
                    && selectedExtIds.containsAll(relatedIds);
            boolean isAutoDetected = autoDetectedIds != null && !relatedIds.isEmpty()
                    && autoDetectedIds.containsAll(relatedIds);
            boolean isSelected = isManuallySelected || isAutoDetected;

            LinearLayout row = new LinearLayout(terminal.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(terminal.dp(4), terminal.dp(2), terminal.dp(4), terminal.dp(2));

            if (isSelected) {
                row.setBackground(makeBackground(row, 0x3A1F4E82, 0xFF55D4FF, 4, 1));
            } else {
                row.setBackground(statefulBackground(row, 0x2A244563, 0, 4, 0));
            }

            // Direction label (IN / OUT)
            String dirLabel = isInput ? "IN" : "OUT";
            int dirColor = isInput ? UiThemeTokens.EMERALD : UiThemeTokens.AMBER;
            addText(row, dirLabel, dirColor, 8, 0, terminal.dp(24), true);

            // Device name
            addText(row, dev.deviceName(), UiThemeTokens.TEXT, 8, 0, 0, true);
            View nameView = row.getChildAt(row.getChildCount() - 1);
            if (nameView != null) {
                nameView.setLayoutParams(new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            }

            // Transfer rate + external ref count
            String rateText = compact(Math.abs(dev.transferRate())) + " FE/t";
            int extCount = dev.externalRefs() != null ? dev.externalRefs().size() : 0;
            if (extCount > 0) rateText += " [" + extCount + "]";
            addText(row, rateText, UiThemeTokens.TEXT_MUTED, 8, 0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, true);

            // Selection badge
            if (isAutoDetected && !isManuallySelected) {
                addText(row, " [A]", UiThemeTokens.CYAN, 7, 0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, true);
            } else if (isSelected) {
                addText(row, " \u2713", UiThemeTokens.CYAN, 8, 0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, true);
            }

            // Click handler: toggle manual selection
            if (!relatedIds.isEmpty()) {
                List<String> capturedIds = List.copyOf(relatedIds);
                row.setClickable(true);
                row.setFocusable(true);
                row.setOnClickListener(v ->
                        terminal.getBridge().toggleExternalGroupSelection(capturedIds));
            }

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, scaledDp(terminal, DEBUG_ROW_HEIGHT_DP));
            rowParams.topMargin = terminal.dp(1);
            body.addView(row, rowParams);
        }

        // ===== 分隔线 =====
        View sep = new View(terminal.getContext());
        sep.setBackground(colorDot(sep, UiThemeTokens.DIVIDER));
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        sepParams.topMargin = terminal.dp(6);
        body.addView(sep, sepParams);

        // ===== 外部储能组列表 =====
        addText(body, tr("screen.resourceobserver.power.external_console.section.external"),
                UiThemeTokens.TEXT, 9, terminal.dp(4), ViewGroup.LayoutParams.MATCH_PARENT, true);

        Set<String> effectiveStorageIds = new LinkedHashSet<>();
        if (autoDetectedIds != null) effectiveStorageIds.addAll(autoDetectedIds);
        if (selectedExtIds != null) effectiveStorageIds.addAll(selectedExtIds);
        if (snap.externalGroups() != null && !effectiveStorageIds.isEmpty()) {
            List<PowerNetworkViewModel.ExternalGroup> effectiveGroups = new ArrayList<>();
            for (PowerNetworkViewModel.ExternalGroup g : snap.externalGroups()) {
                if (effectiveStorageIds.contains(g.extId())) {
                    effectiveGroups.add(g);
                }
            }
            effectiveGroups.sort((a, b) -> {
                int cmp = Long.compare(b.capacity(), a.capacity());
                return cmp != 0 ? cmp : a.displayName().compareTo(b.displayName());
            });

            for (PowerNetworkViewModel.ExternalGroup group : effectiveGroups) {
                LinearLayout gRow = new LinearLayout(terminal.getContext());
                gRow.setOrientation(LinearLayout.HORIZONTAL);
                gRow.setGravity(Gravity.CENTER_VERTICAL);
                gRow.setPadding(terminal.dp(4), terminal.dp(1), terminal.dp(4), terminal.dp(1));

                boolean isAuto = autoDetectedIds != null && autoDetectedIds.contains(group.extId());
                boolean isManual = selectedExtIds != null && selectedExtIds.contains(group.extId());
                String tag = isAuto && isManual ? "[A+M] " : isAuto ? "[Auto] " : "[Manual] ";
                addText(gRow, tag + group.displayName(), UiThemeTokens.TEXT, 8, 0, 0, true);
                View gName = gRow.getChildAt(gRow.getChildCount() - 1);
                if (gName != null) {
                    gName.setLayoutParams(new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
                }

                double pct = group.capacity() > 0
                        ? group.stored() * 100.0 / group.capacity() : 0.0;
                String groupInfo = compact(group.stored()) + "/" + compact(group.capacity())
                        + " (" + String.format(Locale.ROOT, "%.0f%%", pct) + ")";
                addText(gRow, groupInfo, UiThemeTokens.TEXT_MUTED, 8, 0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, true);

                addText(gRow, " IF:" + group.interfaceKeys().size(), UiThemeTokens.TEXT_MUTED, 7, 0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, true);

                LinearLayout.LayoutParams gParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                gParams.topMargin = terminal.dp(1);
                body.addView(gRow, gParams);
            }
        } else {
            addText(body, tr("screen.resourceobserver.power.external_console.empty.selected"),
                    UiThemeTokens.TEXT_MUTED, 8, terminal.dp(2),
                    ViewGroup.LayoutParams.MATCH_PARENT, true);
        }
    }

            // ===================== Grid Chart（电网图表，右面板） =====================

    /**
     * 电网负载分析 —— 显示发电容量 vs 消耗负载 + 利用率进度条。
     * 移植自 PowerGridChartRenderer（简化为 bar 可视化）。
     */
    private static void buildGridChart(ResourceTerminalFragment terminal, LinearLayout section,
                                       int sectionWidth, int sectionHeight,
                                       EffectiveStats stats, PowerNetworkViewModel vm) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        // Title
        addText(section, tr("screen.resourceobserver.power.section.grid_load"),
                UiThemeTokens.TEXT, 12, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);

        // Generation capacity label
        LinearLayout genRow = new LinearLayout(terminal.getContext());
        genRow.setOrientation(LinearLayout.HORIZONTAL);
        genRow.setGravity(Gravity.CENTER_VERTICAL);
        addText(genRow, tr("screen.resourceobserver.power.generation") + ": ",
                UiThemeTokens.TEXT_MUTED, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        TextView gridGenTv = addText(genRow, compact(stats.totalInputPerTick()) + " FE/t",
                UiThemeTokens.EMERALD, 10, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        gridGenTv.setTag(TAG_GRID_GEN_TV);
        LinearLayout.LayoutParams genParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        genParams.topMargin = terminal.dp(4);
        section.addView(genRow, genParams);

        // Peak load label
        LinearLayout loadRow = new LinearLayout(terminal.getContext());
        loadRow.setOrientation(LinearLayout.HORIZONTAL);
        loadRow.setGravity(Gravity.CENTER_VERTICAL);
        addText(loadRow, tr("screen.resourceobserver.power.peak_load") + ": ",
                UiThemeTokens.TEXT_MUTED, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        TextView gridLoadTv = addText(loadRow, compact(stats.totalOutputPerTick()) + " FE/t",
                UiThemeTokens.CYAN, 10, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        gridLoadTv.setTag(TAG_GRID_LOAD_TV);
        LinearLayout.LayoutParams loadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loadParams.topMargin = terminal.dp(2);
        section.addView(loadRow, loadParams);

        // Raw/excluded hint (only when external groups are excluded)
        if (stats.excludedTotalPerTick() > 0) {
            String hint = tr("screen.resourceobserver.power.grid_load.raw_hint",
                    compact(vm.totalInputPerTick()), compact(vm.totalOutputPerTick()),
                    compact(stats.excludedTotalPerTick()));
            addText(section, hint, UiThemeTokens.TEXT_MUTED, 7, terminal.dp(2),
                    ViewGroup.LayoutParams.MATCH_PARENT, true);
        }

        // Load/capacity bar visualization
        int barW = sectionWidth - terminal.dp(16);
        int barH = terminal.dp(10);
        FrameLayout barContainer = new FrameLayout(terminal.getContext());
        barContainer.setBackground(makeBackground(barContainer, 0x5A0D1628, 0x773A5478, 6, 1));

        long capacity = stats.totalInputPerTick() > 0 ? stats.totalInputPerTick() : 1;
        double loadRatio = Math.min(1.0, (double) stats.totalOutputPerTick() / capacity);
        int fillW = Math.max(1, (int) (barW * loadRatio));
        View fillView = new View(terminal.getContext());
        int fillColor = loadRatio > 0.9 ? UiThemeTokens.ROSE
                : (loadRatio > 0.7 ? UiThemeTokens.AMBER : UiThemeTokens.CYAN);
        fillView.setBackground(colorDot(fillView, fillColor));
        fillView.setTag(TAG_GRID_BAR_FILL);
        barContainer.addView(fillView, new FrameLayout.LayoutParams(fillW, barH));

        // Capacity line marker (right edge)
        View capLine = new View(terminal.getContext());
        capLine.setBackground(colorDot(capLine, UiThemeTokens.ROSE));
        FrameLayout.LayoutParams capLineParams = new FrameLayout.LayoutParams(
                terminal.dp(1), barH);
        capLineParams.leftMargin = barW - terminal.dp(1);
        barContainer.addView(capLine, capLineParams);

        LinearLayout.LayoutParams barContainerParams = new LinearLayout.LayoutParams(barW, barH);
        barContainerParams.topMargin = terminal.dp(6);
        section.addView(barContainer, barContainerParams);

        // Bar legend
        String loadPctStr = String.format(Locale.ROOT, "%.0f%%", loadRatio * 100);
        TextView utilTv = addText(section, tr("screen.resourceobserver.power.col.cap_util") + " " + loadPctStr,
                UiThemeTokens.TEXT_MUTED, 8, terminal.dp(2), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        utilTv.setTag(TAG_GRID_UTIL_TV);
    }

    // ===================== Device List（统一设备列表，右面板） =====================

    /**
     * 将模组命名空间格式化为可读名称。
     */
    private static String formatModName(String modNamespace) {
        if (modNamespace == null || modNamespace.isBlank()) return "—";
        return switch (modNamespace.toLowerCase(Locale.ROOT)) {
            case "minecraft" -> "Minecraft";
            case "ae2", "appeng" -> "AE2";
            case "mekanism" -> "Mekanism";
            case "fluxnetworks" -> "Flux Networks";
            case "resourceobserver" -> "Resource Observer";
            default -> {
                String name = modNamespace.replace('_', ' ');
                yield Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        };
    }

    /**
     * 统一设备列表 —— 仅显示实际用电设备（ConsumerEntry），外储接口已排除。
     * <p>
     * DeviceEntry 代表 Flux 网络级别的聚合绑定（包含内部储能数据），
     * 不作为独立行显示；其数据由 KPI 卡片、饼图和过载告警等其他组件使用。
     * <p>
     * 负载占比 = 该设备消耗 / 网络总消耗（即 ConsumerEntry.percentage）。
     * 供能占用 = 设备消耗 / 总有效输入。
     * 列：设备名称 | 所属模组 | FE/t | 负载占比 | 供能占用
     */
    private static void buildDeviceList(ResourceTerminalFragment terminal, LinearLayout section,
                                        int sectionWidth, PowerNetworkViewModel vm,
                                        List<PowerNetworkViewModel.ConsumerEntry> effectiveConsumers) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        addText(section, tr("screen.resourceobserver.power.section.devices"),
                UiThemeTokens.TEXT, 12, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);

        boolean hasConsumers = effectiveConsumers != null && !effectiveConsumers.isEmpty();

        if (!hasConsumers) {
            addText(section, tr("screen.resourceobserver.power.no_devices"),
                    UiThemeTokens.TEXT_MUTED, 10, terminal.dp(4),
                    ViewGroup.LayoutParams.WRAP_CONTENT, false);
            return;
        }

        // 列宽：设备名 26% | 模组 16% | FE/t 16% | 负载占比 21% | 供能占用 21%
        int innerW = sectionWidth - terminal.dp(16);
        int nameW = (int) (innerW * 0.26);
        int modW = (int) (innerW * 0.16);
        int energyW = (int) (innerW * 0.16);
        int shareW = (int) (innerW * 0.21);
        int utilW = (int) (innerW * 0.21);

        // 表头
        LinearLayout header = new LinearLayout(terminal.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(terminal.dp(4), terminal.dp(2), terminal.dp(4), terminal.dp(2));
        header.setBackground(makeBackground(header, UiThemeTokens.HEADER_BG, UiThemeTokens.DIVIDER, 4, 1));

        addText(header, tr("screen.resourceobserver.power.col.device"), UiThemeTokens.TEXT_MUTED, 9, 0, nameW, true);
        addText(header, tr("screen.resourceobserver.power.col.category"), UiThemeTokens.TEXT_MUTED, 9, 0, modW, true);
        addText(header, tr("screen.resourceobserver.power.col.energy"), UiThemeTokens.TEXT_MUTED, 9, 0, energyW, true);
        addText(header, tr("screen.resourceobserver.power.col.load_share"), UiThemeTokens.TEXT_MUTED, 9, 0, shareW, true);
        addText(header, tr("screen.resourceobserver.power.col.load_util"), UiThemeTokens.TEXT_MUTED, 9, 0, utilW, true);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = terminal.dp(4);
        section.addView(header, headerParams);

        int rowH = scaledDp(terminal, DEVICE_ROW_HEIGHT_DP);

        // ── 用电设备行（来自 effective consumers，外储已排除） ──
        // 累计偏移量：用于上下文条中定位当前设备在总消耗中的位置
        double cumulativeOffset = 0.0;
        for (PowerNetworkViewModel.ConsumerEntry c : effectiveConsumers) {
            LinearLayout row = new LinearLayout(terminal.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(terminal.dp(4), terminal.dp(2), terminal.dp(4), terminal.dp(2));

            // 设备名 + 色标圆点 + 数量
            LinearLayout nameCell = new LinearLayout(terminal.getContext());
            nameCell.setOrientation(LinearLayout.HORIZONTAL);
            nameCell.setGravity(Gravity.CENTER_VERTICAL);

            View dot = new View(terminal.getContext());
            dot.setBackground(colorDot(dot, c.color()));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(terminal.dp(5), terminal.dp(5));
            dotLp.rightMargin = terminal.dp(3);
            nameCell.addView(dot, dotLp);

            String nameText = c.count() > 1
                    ? c.deviceName() + " x" + c.count()
                    : c.deviceName();
            addText(nameCell, nameText, UiThemeTokens.TEXT, 9, 0, 0, true);
            nameCell.setLayoutParams(new LinearLayout.LayoutParams(nameW, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(nameCell);

            // 所属模组（从注册 ID 前缀中提取）
            String modLabel = formatModName(c.modName());
            addText(row, modLabel,
                    "—".equals(modLabel) ? UiThemeTokens.TEXT_MUTED : UiThemeTokens.TEXT,
                    8, 0, modW, true);
            // FE/t
            addText(row, compact(c.consumptionPerTick()) + " FE/t", UiThemeTokens.CYAN, 9, 0, energyW, true);

            // 负载占比 — 上下文条（高亮自己在总负载中的位置）
            double shareRatio = c.percentage() / 100.0;
            buildShareContextBar(terminal, row, shareW, cumulativeOffset, shareRatio, c.color());
            cumulativeOffset += shareRatio;

            // 供能占用 — 分段式仪表条（阈值变色）
            double utilRatio = c.supplyRatio() / 100.0;
            buildSupplyGauge(terminal, row, utilW, utilRatio);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowH);
            section.addView(row, rowParams);
        }
    }

    /**
     * 负载占比单元格 — 上下文条。
     * 全宽底条代表 100% 总消耗，其中设备色高亮段表示该设备的占比，
     * 灰色段为其他设备，一目了然"我在总负载中的位置与大小"。
     * 上方显示百分比文字。
     *
     * @param offset      该设备在总消耗中的累计起始偏移 (0.0~1.0)
     * @param ratio       该设备占比 (0.0~1.0)
     * @param deviceColor 设备色（与饼图一致）
     */
    private static void buildShareContextBar(ResourceTerminalFragment terminal, LinearLayout row,
                                             int cellW, double offset, double ratio, int deviceColor) {
        LinearLayout cell = new LinearLayout(terminal.getContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);

        // 百分比文字（设备色）
        String text = String.format(Locale.ROOT, "%.1f%%", ratio * 100);
        addText(cell, text, deviceColor, 8, 0, 0, true);

        // 上下文条：[灰色前段] [设备色高亮段] [灰色后段]
        int barW = cellW - terminal.dp(6);
        int barH = terminal.dp(4);
        FrameLayout barContainer = new FrameLayout(terminal.getContext());
        // 暗色底条 = 100% 总消耗
        barContainer.setBackground(makeBackground(barContainer, 0xFF0D1628, 0x20FFFFFF, 1000, 0));

        // 高亮段：设备自身占比
        int startPx = (int) (barW * Math.min(1.0, offset));
        int fillPx = Math.max(1, (int) (barW * Math.min(1.0, ratio)));
        // 避免溢出
        if (startPx + fillPx > barW) fillPx = barW - startPx;

        View highlight = new View(terminal.getContext());
        highlight.setBackground(colorDot(highlight, deviceColor));
        FrameLayout.LayoutParams hlLp = new FrameLayout.LayoutParams(fillPx, barH);
        hlLp.leftMargin = startPx;
        barContainer.addView(highlight, hlLp);

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(barW, barH);
        barLp.topMargin = terminal.dp(1);
        cell.addView(barContainer, barLp);

        cell.setLayoutParams(new LinearLayout.LayoutParams(cellW, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(cell);
    }

    /**
     * 供能占用单元格 — 分段式仪表条。
     * 10 段小方块模拟信号强度指示器，颜色按阈值分级变化：
     *   ≤40% → 翡翠绿，≤70% → 青色，≤90% → 琥珀黄，>90% → 玫红。
     * 百分比文字颜色与仪表条同步，直观反映供能压力。
     */
    private static void buildSupplyGauge(ResourceTerminalFragment terminal, LinearLayout row,
                                         int cellW, double ratio) {
        LinearLayout gauge = new LinearLayout(terminal.getContext());
        gauge.setOrientation(LinearLayout.VERTICAL);
        gauge.setGravity(Gravity.CENTER_HORIZONTAL);

        // 阈值变色
        int threshColor = ratio > 0.9 ? UiThemeTokens.ROSE
                : (ratio > 0.7 ? UiThemeTokens.AMBER
                : (ratio > 0.4 ? UiThemeTokens.CYAN : UiThemeTokens.EMERALD));

        // 百分比文字（阈值色）
        String text = String.format(Locale.ROOT, "%.1f%%", ratio * 100);
        addText(gauge, text, threshColor, 8, 0, 0, true);

        // 分段仪表条：10 段小方块
        int segCount = 10;
        int gap = terminal.dp(1);
        int barW = cellW - terminal.dp(6);
        int segW = Math.max(1, (barW - gap * (segCount - 1)) / segCount);
        int segH = terminal.dp(3);
        int filledCount = (int) Math.round(Math.min(1.0, ratio) * segCount);

        LinearLayout segBar = new LinearLayout(terminal.getContext());
        segBar.setOrientation(LinearLayout.HORIZONTAL);

        int emptyColor = 0xFF0D1628; // 未填充段：深色底
        for (int i = 0; i < segCount; i++) {
            View seg = new View(terminal.getContext());
            int segColor = i < filledCount ? threshColor : emptyColor;
            seg.setBackground(makeBackground(seg, segColor, segColor, 2, 0));
            LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(segW, segH);
            if (i > 0) segLp.leftMargin = gap;
            segBar.addView(seg, segLp);
        }

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(barW, segH);
        barLp.topMargin = terminal.dp(1);
        gauge.addView(segBar, barLp);

        gauge.setLayoutParams(new LinearLayout.LayoutParams(cellW, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(gauge);
    }

    /**
     * 在 FrameLayout 内添加顶部和底部渐隐遮罩（GradientDrawable），
     * 用于暗示 ScrollView 内容可继续滚动，并防止内容与标题重叠。
     */
    private static void addScrollFadeOverlays(ResourceTerminalFragment terminal, FrameLayout wrapper) {
        int fadeH = terminal.dp(14);
        int panelBg = UiThemeTokens.PANEL_BG;
        int transparent = panelBg & 0x00FFFFFF;

        View topFade = new View(terminal.getContext());
        GradientDrawable topGrad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{panelBg, transparent});
        topGrad.setCornerRadius(0);
        topFade.setBackground(topGrad);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, fadeH);
        topLp.gravity = Gravity.TOP;
        wrapper.addView(topFade, topLp);

        View bottomFade = new View(terminal.getContext());
        GradientDrawable bottomGrad = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{transparent, panelBg});
        bottomGrad.setCornerRadius(0);
        bottomFade.setBackground(bottomGrad);
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, fadeH);
        bottomLp.gravity = Gravity.BOTTOM;
        wrapper.addView(bottomFade, bottomLp);
    }
}
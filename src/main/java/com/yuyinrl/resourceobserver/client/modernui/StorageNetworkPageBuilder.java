package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModelMapper;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.network.UiActionType;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.PopupWindow;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.*;

/**
 * Storage Network 页面构建器 —— 在内容区域中构建存储网络仪表板。
 * <p>
 * 布局：全宽 KPI 卡片 → 双列（左 33%: 节点列表 + 用量摘要 | 右 67%: 警报筛选 + 物品列表）。
 * <p>
 * 从 {@link com.yuyinrl.resourceobserver.client.screen.ResourceTerminalScreen} 的
 * renderStorageNetworkPage / computeStorageContentLayout 完整移植。
 * <p>
 * 数据来源为 {@link StorageNetworkViewModel}。
 */
final class StorageNetworkPageBuilder {

    private static final int ITEM_ROW_HEIGHT_DP = 18;

    // View tags for incremental update (avoid full rebuild on data refresh)
    private static final int TAG_KPI_SECTION    = 0x7F_0201;
    private static final int TAG_NODE_SECTION   = 0x7F_0202;
    private static final int TAG_USAGE_SECTION  = 0x7F_0203;
    private static final int TAG_FILTER_BAR     = 0x7F_0204;
    private static final int TAG_ITEM_SECTION   = 0x7F_0205;
    // Per-KPI value tags: TAG_KPI_VALUE_BASE + kpiIndex
    private static final int TAG_KPI_VALUE_BASE = 0x7F_0210;

    private StorageNetworkPageBuilder() {}

    /**
     * 构建存储网络页面（移植自 ResourceTerminalScreen 的 12 列网格布局）。
     * <p>
     * 结构：
     * <pre>
     *   ┌──────────── KPI 卡片（全宽） ────────────┐
     *   ├──── 左列 33% ────┬──── 右列 67% ────────┤
     *   │ 节点列表          │ 警报筛选栏            │
     *   │                  │ 物品健康表格           │
     *   │ 用量摘要          │                      │
     *   └──────────────────┴──────────────────────┘
     * </pre>
     */
    static void build(ResourceTerminalFragment terminal, FrameLayout container, int contentW, int contentH, boolean animate) {
        StorageNetworkViewModel vm = terminal.getBridge().getStorageViewModel();
        if (vm == null) return;

        int sectionGap = terminal.dp(6);
        int scrollbarW = terminal.dp(8);
        int innerW = Math.max(terminal.dp(160), contentW - scrollbarW);

        int kpiH = clamp(Math.round(contentH * 0.15f), terminal.dp(54), terminal.dp(100));

        // 12-column grid: left 4 cols (33%), right 8 cols (67%)（移植自 computeStorageContentLayout）
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

        // ===== 1. KPI section（全宽，存储 KPI 卡片） =====
        LinearLayout kpiSection = section(terminal, innerW, kpiH);
        kpiSection.setTag(TAG_KPI_SECTION);
        buildKpiSection(terminal, kpiSection, innerW, kpiH, vm);
        addSection(content, kpiSection, 0);

        // ===== 2. Two-column container（12 列网格：左 4 + 右 8） =====
        LinearLayout columns = new LinearLayout(terminal.getContext());
        columns.setOrientation(LinearLayout.HORIZONTAL);

        // ---- 左列 (33%): 节点列表 + 用量摘要 ----
        LinearLayout leftCol = new LinearLayout(terminal.getContext());
        leftCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout nodeSection = section(terminal, leftW, ViewGroup.LayoutParams.WRAP_CONTENT);
        nodeSection.setTag(TAG_NODE_SECTION);
        Map<String, View[]> nodeRefs = new LinkedHashMap<>();
        buildNodeListWithCellRefs(terminal, nodeSection, leftW, vm, nodeRefs);
        terminal.setStorageNodeCells(nodeRefs);
        terminal.setStorageNodeStructuralKey(computeNodeStructuralKey(vm));
        addSection(leftCol, nodeSection, 0);

        int usageH = terminal.dp(80);
        LinearLayout usageSection = section(terminal, leftW, ViewGroup.LayoutParams.WRAP_CONTENT);
        usageSection.setTag(TAG_USAGE_SECTION);
        buildUsageSummary(terminal, usageSection, leftW, usageH, vm);
        addSection(leftCol, usageSection, sectionGap);

        columns.addView(leftCol, new LinearLayout.LayoutParams(leftW, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 右列 (67%): 警报筛选 + 物品列表 ----
        LinearLayout rightCol = new LinearLayout(terminal.getContext());
        rightCol.setOrientation(LinearLayout.VERTICAL);

        // 警报筛选栏
        LinearLayout filterBar = new LinearLayout(terminal.getContext());
        filterBar.setTag(TAG_FILTER_BAR);
        buildFilterBar(terminal, filterBar, vm);
        addSection(rightCol, filterBar, 0);

        // 物品健康表格
        LinearLayout itemSection = section(terminal, rightW, ViewGroup.LayoutParams.WRAP_CONTENT);
        itemSection.setTag(TAG_ITEM_SECTION);
        // Build with cell refs for incremental updates
        Map<String, TextView[]> cellRefs = new LinkedHashMap<>();
        Map<String, View[]> barRefs = new LinkedHashMap<>();
        buildItemListWithCellRefs(terminal, itemSection, rightW, vm, cellRefs, barRefs);
        terminal.setStorageItemCells(cellRefs);
        terminal.setStorageItemBufferBars(barRefs);
        terminal.setStorageStructuralKey(computeStorageStructuralKey(vm));
        addSection(rightCol, itemSection, sectionGap);

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

    // ===================== Incremental Update =====================

    /**
     * 增量刷新：保留 ScrollView 和布局结构，仅更新各 section 的数据。
     * <p>
     * KPI 卡片、物品行等使用 tagged TextView / cell ref 就地 setText，
     * 避免 removeAllViews 破坏 hover、tooltip、动画状态。
     *
     * @return true 增量刷新成功；false 需要完整重建
     */
    static boolean tryIncrementalUpdate(ResourceTerminalFragment terminal) {
        StorageNetworkViewModel vm = terminal.getBridge().getStorageViewModel();
        if (vm == null) return false;

        FrameLayout container = terminal.getContentContainer();
        if (container == null || container.getChildCount() == 0) return false;

        // Find all tagged sections — if any is missing, fall back to full rebuild
        LinearLayout kpiSection   = container.findViewWithTag(TAG_KPI_SECTION);
        LinearLayout nodeSection  = container.findViewWithTag(TAG_NODE_SECTION);
        LinearLayout usageSection = container.findViewWithTag(TAG_USAGE_SECTION);
        LinearLayout itemSection  = container.findViewWithTag(TAG_ITEM_SECTION);
        if (kpiSection == null || nodeSection == null || usageSection == null || itemSection == null) {
            return false;
        }

        // Read widths/heights from existing layout
        int kpiW = kpiSection.getWidth();
        int kpiH = kpiSection.getHeight();
        int nodeW = nodeSection.getWidth();
        int usageW = usageSection.getWidth();
        int itemW = itemSection.getWidth();
        if (kpiW <= 0 || nodeW <= 0 || itemW <= 0) return false;

        int usageH = terminal.dp(80);

        // ---- 1. KPI cards: in-place value + color update ----
        List<StorageNetworkViewModel.StorageKpi> kpis = vm.kpiCards();
        boolean kpiStructureOk = kpis != null && kpis.size() == kpiSection.getChildCount();
        if (kpiStructureOk) {
            for (int i = 0; i < kpis.size(); i++) {
                TextView valueTv = kpiSection.findViewWithTag(TAG_KPI_VALUE_BASE + i);
                if (valueTv != null) {
                    StorageNetworkViewModel.StorageKpi kpi = kpis.get(i);
                    valueTv.setText(kpi.value());
                    valueTv.setTextColor(statusColor(kpi.status()));
                }
            }
        } else {
            kpiSection.removeAllViews();
            buildKpiSection(terminal, kpiSection, kpiW, kpiH, vm);
        }

        // ---- 2. Node list: in-place cell update when structure unchanged ----
        String newNodeKey = computeNodeStructuralKey(vm);
        String oldNodeKey = terminal.getStorageNodeStructuralKey();
        Map<String, View[]> nodeCellMap = terminal.getStorageNodeCells();

        if (newNodeKey.equals(oldNodeKey) && nodeCellMap != null) {
            updateNodeCellsInPlace(terminal, vm, nodeCellMap);
        } else {
            nodeSection.removeAllViews();
            Map<String, View[]> newNodeRefs = new LinkedHashMap<>();
            buildNodeListWithCellRefs(terminal, nodeSection, nodeW, vm, newNodeRefs);
            terminal.setStorageNodeCells(newNodeRefs);
            terminal.setStorageNodeStructuralKey(newNodeKey);
        }

        // ---- 3. Usage summary: structural rebuild (segments change) ----
        usageSection.removeAllViews();
        buildUsageSummary(terminal, usageSection, usageW, usageH, vm);

        // ---- 4. Filter bar: rebuild to reflect current filter state and item count ----
        LinearLayout filterBar = container.findViewWithTag(TAG_FILTER_BAR);
        if (filterBar != null) {
            filterBar.removeAllViews();
            buildFilterBar(terminal, filterBar, vm);
        }

        // ---- 5. Item list: in-place cell update when structure unchanged ----
        String newKey = computeStorageStructuralKey(vm);
        String oldKey = terminal.getStorageStructuralKey();
        Map<String, TextView[]> cellMap = terminal.getStorageItemCells();
        Map<String, View[]> barMap = terminal.getStorageItemBufferBars();

        if (newKey.equals(oldKey) && cellMap != null && barMap != null) {
            // Structure unchanged — update cell text/colors in-place
            updateItemCellsInPlace(terminal, vm, cellMap, barMap);
        } else {
            // Structure changed — full item list rebuild + re-populate cell maps
            itemSection.removeAllViews();
            Map<String, TextView[]> newCellMap = new LinkedHashMap<>();
            Map<String, View[]> newBarMap = new LinkedHashMap<>();
            buildItemListWithCellRefs(terminal, itemSection, itemW, vm, newCellMap, newBarMap);
            terminal.setStorageItemCells(newCellMap);
            terminal.setStorageItemBufferBars(newBarMap);
            terminal.setStorageStructuralKey(newKey);
        }

        return true;
    }

    /**
     * Compute a structural key for the item list — ordered item IDs joined.
     * If IDs/order change, we need a full item rebuild.
     */
    private static String computeStorageStructuralKey(StorageNetworkViewModel vm) {
        List<StorageNetworkViewModel.ItemRow> items = vm.items();
        if (items == null || items.isEmpty()) return "";
        StringJoiner sj = new StringJoiner(",");
        for (StorageNetworkViewModel.ItemRow item : items) {
            sj.add(item.itemId());
        }
        return sj.toString();
    }

    /**
     * In-place update of item cell text/colors without touching the view tree.
     * Preserves hover listeners, tooltip state, and animation state.
     */
    private static void updateItemCellsInPlace(ResourceTerminalFragment terminal,
                                                StorageNetworkViewModel vm,
                                                Map<String, TextView[]> cellMap,
                                                Map<String, View[]> barMap) {
        List<StorageNetworkViewModel.ItemRow> items = vm.items();
        if (items == null) return;
        for (StorageNetworkViewModel.ItemRow item : items) {
            // Cell map: [0]=localAmount, [1]=delta, [2]=burnRate, [3]=bufferText
            TextView[] cells = cellMap.get(item.itemId());
            if (cells != null) {
                cells[0].setText(compact(item.localAmount()));

                double delta = item.delta();
                int deltaColor = delta > 0 ? UiThemeTokens.EMERALD
                        : (delta < 0 ? UiThemeTokens.ROSE : UiThemeTokens.TEXT);
                cells[1].setText((delta > 0 ? "+" : "") + compact(Math.round(delta)));
                cells[1].setTextColor(deltaColor);

                cells[2].setText(compact(Math.round(item.burnRatePerMin())));
                cells[3].setText(item.estimatedBufferText());
            }

            // Bar map: [0]=fillView, [1]=barContainer (for width reference)
            View[] bars = barMap.get(item.itemId());
            if (bars != null && bars[1].getWidth() > 0) {
                int barW = bars[1].getWidth();
                int fillW = Math.max(1, (int) (barW * Math.min(1.0, item.bufferRatio())));
                int bufColor = item.bufferRatio() < 0.2 ? UiThemeTokens.ROSE
                        : (item.bufferRatio() < 0.5 ? UiThemeTokens.AMBER : UiThemeTokens.EMERALD);
                ViewGroup.LayoutParams flp = bars[0].getLayoutParams();
                if (flp != null) {
                    flp.width = fillW;
                    bars[0].setLayoutParams(flp);
                }
                bars[0].setBackground(colorDot(bars[0], bufColor));
            }
        }
    }

    /**
     * 每秒倒计时 tick —— 基于 EMA 锚点和系统时钟更新所有物品的缓冲文字 + 进度条。
     * <p>
     * 此方法不触碰 view tree 结构，仅修改已有 TextView 的文字和 bar 的宽度/颜色，
     * 因此不会影响 hover、tooltip 等交互状态。
     *
     * @return true 如果成功执行了一次 tick（有 cell 可更新）
     */
    static boolean tickBufferCountdown(ResourceTerminalFragment terminal) {
        Map<String, TextView[]> cellMap = terminal.getStorageItemCells();
        Map<String, View[]> barMap = terminal.getStorageItemBufferBars();
        if (cellMap == null || barMap == null || cellMap.isEmpty()) return false;

        Map<String, double[]> ema = terminal.getBridge().getBufferEma();
        if (ema.isEmpty()) return false;

        for (Map.Entry<String, TextView[]> entry : cellMap.entrySet()) {
            String itemId = entry.getKey();
            TextView[] cells = entry.getValue();

            double countdownSec = StorageNetworkViewModelMapper.computeCountdownSeconds(itemId, ema);
            if (countdownSec < 0) {
                // ∞ — no consumption or no anchor
                if (cells[3] != null) cells[3].setText("∞");
                // bar stays at previous state (full)
                continue;
            }

            // Update buffer text
            if (cells[3] != null) {
                cells[3].setText(StorageNetworkViewModelMapper.formatBufferSeconds(Math.round(countdownSec)));
            }

            // Update buffer bar
            View[] bars = barMap.get(itemId);
            if (bars != null && bars[1] != null && bars[1].getWidth() > 0) {
                double ratio = StorageNetworkViewModelMapper.bufferRatioFromSeconds(countdownSec);
                int barW = bars[1].getWidth();
                int fillW = Math.max(1, (int) (barW * Math.min(1.0, ratio)));
                int bufColor = ratio < 0.2 ? UiThemeTokens.ROSE
                        : (ratio < 0.5 ? UiThemeTokens.AMBER : UiThemeTokens.EMERALD);
                ViewGroup.LayoutParams flp = bars[0].getLayoutParams();
                if (flp != null) {
                    flp.width = fillW;
                    bars[0].setLayoutParams(flp);
                }
                bars[0].setBackground(colorDot(bars[0], bufColor));
            }
        }
        return true;
    }

    /**
     * Selection changes the row background, so it counts as structural.
     */
    private static String computeNodeStructuralKey(StorageNetworkViewModel vm) {
        List<StorageNetworkViewModel.NodeEntry> nodes = vm.nodes();
        if (nodes == null || nodes.isEmpty()) return "";
        StringJoiner sj = new StringJoiner(",");
        for (StorageNetworkViewModel.NodeEntry node : nodes) {
            sj.add(node.nodeId() + ":" + (node.selected() ? "1" : "0"));
        }
        return sj.toString();
    }

    /**
     * In-place update of node row cells without rebuilding the view tree.
     * Updates: capacity text, status dot color, capacity bar fill.
     * Cell map: nodeId → [0]=capacityTv, [1]=statusDot, [2]=capFillView, [3]=capBarContainer
     */
    private static void updateNodeCellsInPlace(ResourceTerminalFragment terminal,
                                                StorageNetworkViewModel vm,
                                                Map<String, View[]> nodeCellMap) {
        List<StorageNetworkViewModel.NodeEntry> nodes = vm.nodes();
        if (nodes == null) return;
        for (StorageNetworkViewModel.NodeEntry node : nodes) {
            View[] cells = nodeCellMap.get(node.nodeId());
            if (cells == null) continue;

            // [0] capacity text
            if (cells[0] instanceof TextView tv) {
                tv.setText(node.usedFormatted() + " / " + node.totalFormatted());
            }

            // [1] status dot color
            int dotColor = node.statusAlert() ? UiThemeTokens.ROSE : UiThemeTokens.EMERALD;
            cells[1].setBackground(colorDot(cells[1], dotColor));

            // [2] capacity fill bar width + color, [3] bar container for width reference
            if (cells[3].getWidth() > 0) {
                int barW = cells[3].getWidth();
                int fillW = Math.max(1, (int) (barW * Math.min(1.0, node.capacityRatio())));
                int barColor = node.capacityRatio() > 0.9 ? UiThemeTokens.ROSE
                        : (node.capacityRatio() > 0.7 ? UiThemeTokens.AMBER : UiThemeTokens.CYAN);
                ViewGroup.LayoutParams flp = cells[2].getLayoutParams();
                if (flp != null) {
                    flp.width = fillW;
                    cells[2].setLayoutParams(flp);
                }
                cells[2].setBackground(colorDot(cells[2], barColor));
            }
        }
    }

    /**
     * Same as {@link #buildNodeList} but also populates cell reference map
     * for subsequent in-place incremental updates.
     * <p>
     * nodeRefs: nodeId → [capacityTv, statusDot, capFillView, capBarContainer]
     */
    private static void buildNodeListWithCellRefs(ResourceTerminalFragment terminal,
                                                   LinearLayout section, int sectionWidth,
                                                   StorageNetworkViewModel vm,
                                                   Map<String, View[]> nodeRefs) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        addText(section, tr("screen.resourceobserver.storage.section.nodes"),
                UiThemeTokens.TEXT, 12, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);

        List<StorageNetworkViewModel.NodeEntry> nodes = vm.nodes();
        if (nodes == null || nodes.isEmpty()) {
            addText(section, tr("screen.resourceobserver.storage.no_items"),
                    UiThemeTokens.TEXT_MUTED, 10, terminal.dp(4),
                    ViewGroup.LayoutParams.WRAP_CONTENT, false);
            return;
        }

        int nodeRowH = ModernUiTheme.scaledDp(terminal, 28);
        for (StorageNetworkViewModel.NodeEntry node : nodes) {
            LinearLayout row = new LinearLayout(terminal.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(terminal.dp(6), terminal.dp(3), terminal.dp(6), terminal.dp(3));

            boolean selected = node.selected();
            row.setBackground(statefulBackground(row,
                    selected ? 0xCC142338 : UiThemeTokens.CARD_BG,
                    selected ? UiThemeTokens.CYAN : UiThemeTokens.CARD_BORDER, 6, 1));

            // Status dot
            View dot = new View(terminal.getContext());
            int dotColor = node.statusAlert() ? UiThemeTokens.ROSE : UiThemeTokens.EMERALD;
            dot.setBackground(colorDot(dot, dotColor));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(terminal.dp(6), terminal.dp(6));
            dotParams.rightMargin = terminal.dp(4);
            row.addView(dot, dotParams);

            // Node info
            LinearLayout info = new LinearLayout(terminal.getContext());
            info.setOrientation(LinearLayout.VERTICAL);
            addText(info, node.displayName(), UiThemeTokens.TEXT, 10, 0, 0, true);
            if (node.coordinatesText() != null) {
                addText(info, "§ " + node.coordinatesText(), UiThemeTokens.TEXT_MUTED, 8, 0, 0, true);
            }
            TextView capacityTv = addText(info, node.usedFormatted() + " / " + node.totalFormatted(),
                    UiThemeTokens.TEXT_MUTED, 8, 0, 0, true);
            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            // Rename button
            final String nodeNetworkId = node.nodeId();
            final String currentDisplayName = node.displayName();
            TextView renameBtn = tinyButton(row, "✎");
            renameBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
            renameBtn.setOnClickListener(v -> showRenameNetworkPopup(terminal, nodeNetworkId, currentDisplayName));
            LinearLayout.LayoutParams renameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            renameLp.rightMargin = terminal.dp(4);
            row.addView(renameBtn, renameLp);

            // Capacity bar
            int barW = terminal.dp(60);
            int barH = terminal.dp(6);
            FrameLayout barContainer = new FrameLayout(terminal.getContext());
            barContainer.setBackground(makeBackground(barContainer, 0xFF0A1020, UiThemeTokens.DIVIDER, 1000, 1));

            int fillW = Math.max(1, (int) (barW * Math.min(1.0, node.capacityRatio())));
            View fill = new View(terminal.getContext());
            int barColor = node.capacityRatio() > 0.9 ? UiThemeTokens.ROSE
                    : (node.capacityRatio() > 0.7 ? UiThemeTokens.AMBER : UiThemeTokens.CYAN);
            fill.setBackground(colorDot(fill, barColor));
            barContainer.addView(fill, new FrameLayout.LayoutParams(fillW, barH));

            row.addView(barContainer, new LinearLayout.LayoutParams(barW, barH));

            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> terminal.getBridge().updateStorageFilter(
                    selected ? null : nodeNetworkId,
                    terminal.getBridge().isStorageAlertFilterActive()));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, nodeRowH);
            rowParams.topMargin = terminal.dp(3);
            section.addView(row, rowParams);

            // Store cell references: [capacityTv, statusDot, capFill, capBarContainer]
            nodeRefs.put(node.nodeId(), new View[]{capacityTv, dot, fill, barContainer});
        }
    }

    private static void buildKpiSection(ResourceTerminalFragment terminal, LinearLayout section,
                                        int sectionWidth, int sectionHeight, StorageNetworkViewModel vm) {
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        List<StorageNetworkViewModel.StorageKpi> kpis = vm.kpiCards();
        if (kpis == null || kpis.isEmpty()) return;

        int gap = terminal.dp(8);
        int cardCount = kpis.size();
        int innerW = sectionWidth - terminal.dp(16);
        int cardW = Math.max(terminal.dp(100), (innerW - (cardCount - 1) * gap) / cardCount);
        int cardH = Math.max(terminal.dp(40), sectionHeight - terminal.dp(16));

        for (int i = 0; i < cardCount; i++) {
            StorageNetworkViewModel.StorageKpi kpi = kpis.get(i);
            LinearLayout cardView = new LinearLayout(terminal.getContext());
            cardView.setOrientation(LinearLayout.VERTICAL);
            cardView.setPadding(terminal.dp(8), terminal.dp(6), terminal.dp(8), terminal.dp(6));
            cardView.setBackground(cardBackgroundStateful(cardView));
            addHoverScaleEffect(cardView);

            addText(cardView, tr(kpi.label()), UiThemeTokens.TEXT_MUTED, 9, 0,
                    ViewGroup.LayoutParams.MATCH_PARENT, true);
            TextView valueTv = addText(cardView, kpi.value(), statusColor(kpi.status()), 14, terminal.dp(2),
                    ViewGroup.LayoutParams.MATCH_PARENT, true);
            valueTv.setTag(TAG_KPI_VALUE_BASE + i);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardW, cardH);
            if (i > 0) params.leftMargin = gap;
            section.addView(cardView, params);
        }
    }

    // ===================== Node List（左列） =====================

    /**
     * 构建节点列表 —— 左列上半部分（移植自 StorageNodeListRenderer）。
     * 每个节点行包含：状态圆点 + 名称/容量信息 + 容量进度条。
     */
    private static void buildNodeList(ResourceTerminalFragment terminal, LinearLayout section,
                                      int sectionWidth, StorageNetworkViewModel vm) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        addText(section, tr("screen.resourceobserver.storage.section.nodes"),
                UiThemeTokens.TEXT, 12, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);

        List<StorageNetworkViewModel.NodeEntry> nodes = vm.nodes();
        if (nodes == null || nodes.isEmpty()) {
            addText(section, tr("screen.resourceobserver.storage.no_items"),
                    UiThemeTokens.TEXT_MUTED, 10, terminal.dp(4),
                    ViewGroup.LayoutParams.WRAP_CONTENT, false);
            return;
        }

        int nodeRowH = ModernUiTheme.scaledDp(terminal, 28);
        for (StorageNetworkViewModel.NodeEntry node : nodes) {
            LinearLayout row = new LinearLayout(terminal.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(terminal.dp(6), terminal.dp(3), terminal.dp(6), terminal.dp(3));

            boolean selected = node.selected();
            row.setBackground(statefulBackground(row,
                    selected ? 0xCC142338 : UiThemeTokens.CARD_BG,
                    selected ? UiThemeTokens.CYAN : UiThemeTokens.CARD_BORDER, 6, 1));

            // Status dot
            View dot = new View(terminal.getContext());
            int dotColor = node.statusAlert() ? UiThemeTokens.ROSE : UiThemeTokens.EMERALD;
            dot.setBackground(colorDot(dot, dotColor));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(terminal.dp(6), terminal.dp(6));
            dotParams.rightMargin = terminal.dp(4);
            row.addView(dot, dotParams);

            // Node info
            LinearLayout info = new LinearLayout(terminal.getContext());
            info.setOrientation(LinearLayout.VERTICAL);
            addText(info, node.displayName(), UiThemeTokens.TEXT, 10, 0, 0, true);
            if (node.coordinatesText() != null) {
                addText(info, "§ " + node.coordinatesText(), UiThemeTokens.TEXT_MUTED, 8, 0, 0, true);
            }
            addText(info, node.usedFormatted() + " / " + node.totalFormatted(),
                    UiThemeTokens.TEXT_MUTED, 8, 0, 0, true);
            row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            // Rename button（✎ pencil icon）
            final String nodeNetworkId = node.nodeId();
            final String currentDisplayName = node.displayName();
            TextView renameBtn = tinyButton(row, "✎");
            renameBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
            renameBtn.setOnClickListener(v -> showRenameNetworkPopup(terminal, nodeNetworkId, currentDisplayName));
            LinearLayout.LayoutParams renameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            renameLp.rightMargin = terminal.dp(4);
            row.addView(renameBtn, renameLp);

            // Capacity bar
            int barW = terminal.dp(60);
            int barH = terminal.dp(6);
            FrameLayout barContainer = new FrameLayout(terminal.getContext());
            barContainer.setBackground(makeBackground(barContainer, 0xFF0A1020, UiThemeTokens.DIVIDER, 1000, 1));

            int fillW = Math.max(1, (int) (barW * Math.min(1.0, node.capacityRatio())));
            View fill = new View(terminal.getContext());
            int barColor = node.capacityRatio() > 0.9 ? UiThemeTokens.ROSE
                    : (node.capacityRatio() > 0.7 ? UiThemeTokens.AMBER : UiThemeTokens.CYAN);
            fill.setBackground(colorDot(fill, barColor));
            barContainer.addView(fill, new FrameLayout.LayoutParams(fillW, barH));

            row.addView(barContainer, new LinearLayout.LayoutParams(barW, barH));

            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> terminal.getBridge().updateStorageFilter(
                    selected ? null : nodeNetworkId,
                    terminal.getBridge().isStorageAlertFilterActive()));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, nodeRowH);
            rowParams.topMargin = terminal.dp(3);
            section.addView(row, rowParams);
        }
    }

    // ===================== Usage Summary（左列） =====================

    /**
     * 构建用量摘要 —— 左列下半部分（移植自 StorageUsageSummaryRenderer）。
     * 包含：总使用率百分比 + 分段使用率条形图 + 图例。
     */
    private static void buildUsageSummary(ResourceTerminalFragment terminal, LinearLayout section,
                                          int sectionWidth, int sectionHeight, StorageNetworkViewModel vm) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(6), terminal.dp(8), terminal.dp(6));

        // Total usage percentage
        String usageText = String.format(Locale.ROOT, "%.1f%%",
                vm.totalUsedRatio() * 100.0);
        addText(section, tr("screen.resourceobserver.storage.section.usage") + " " + usageText,
                UiThemeTokens.TEXT, 10, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        // Segmented usage bar
        int barW = sectionWidth - terminal.dp(16);
        int barH = terminal.dp(8);
        FrameLayout bar = new FrameLayout(terminal.getContext());
        bar.setBackground(makeBackground(bar, 0xFF0A1020, UiThemeTokens.DIVIDER, 1000, 1));

        List<StorageNetworkViewModel.UsageSegment> segments = vm.usageSegments();
        if (segments != null) {
            int x = 0;
            for (StorageNetworkViewModel.UsageSegment seg : segments) {
                int segW = Math.max(1, (int) (barW * seg.percentage() / 100.0));
                View segView = new View(terminal.getContext());
                segView.setBackground(colorDot(segView, seg.color()));
                FrameLayout.LayoutParams segParams = new FrameLayout.LayoutParams(segW, barH);
                segParams.leftMargin = x;
                bar.addView(segView, segParams);
                x += segW;
            }
        }

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(barW, barH);
        barParams.topMargin = terminal.dp(4);
        section.addView(bar, barParams);

        // Legend（自动换行：使用垂直布局，每行最多容纳宽度内的图例项）
        if (segments != null && !segments.isEmpty()) {
            LinearLayout legend = new LinearLayout(terminal.getContext());
            legend.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < segments.size(); i++) {
                StorageNetworkViewModel.UsageSegment seg = segments.get(i);
                LinearLayout legendRow = new LinearLayout(terminal.getContext());
                legendRow.setOrientation(LinearLayout.HORIZONTAL);
                legendRow.setGravity(Gravity.CENTER_VERTICAL);

                View legDot = new View(terminal.getContext());
                legDot.setBackground(colorDot(legDot, seg.color()));
                LinearLayout.LayoutParams legDotParams = new LinearLayout.LayoutParams(terminal.dp(6), terminal.dp(6));
                legDotParams.rightMargin = terminal.dp(4);
                legendRow.addView(legDot, legDotParams);

                addText(legendRow, seg.displayName() + " " + String.format(Locale.ROOT, "%.0f%%", seg.percentage()),
                        UiThemeTokens.TEXT_MUTED, 8, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);

                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowParams.topMargin = terminal.dp(1);
                legend.addView(legendRow, rowParams);
            }
            LinearLayout.LayoutParams legendParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            legendParams.topMargin = terminal.dp(2);
            section.addView(legend, legendParams);
        }
    }

    // ===================== Alert Filter Bar（右列） =====================

    /**
     * 构建警报筛选栏 —— 右列顶部（移植自 StorageItemListRenderer 的 alertFilterButton）。
     */
    private static void buildFilterBar(ResourceTerminalFragment terminal, LinearLayout filterBar, StorageNetworkViewModel vm) {
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setGravity(Gravity.CENTER_VERTICAL);
        filterBar.setPadding(terminal.dp(8), terminal.dp(4), terminal.dp(8), terminal.dp(4));

        addText(filterBar,
                tr("screen.resourceobserver.storage.items_count", vm.totalItemCount()),
                UiThemeTokens.TEXT_MUTED, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        if (vm.criticalItemCount() > 0) {
            View critDot = new View(terminal.getContext());
            critDot.setBackground(colorDot(critDot, UiThemeTokens.ROSE));
            LinearLayout.LayoutParams critDotParams = new LinearLayout.LayoutParams(terminal.dp(5), terminal.dp(5));
            critDotParams.leftMargin = terminal.dp(6);
            critDotParams.rightMargin = terminal.dp(3);
            filterBar.addView(critDot, critDotParams);

            addText(filterBar,
                    tr("screen.resourceobserver.storage.below_threshold", vm.criticalItemCount()),
                    UiThemeTokens.ROSE, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        }

        filterBar.addView(spacer(terminal), spacerParams());

        boolean alertActive = terminal.getBridge().isStorageAlertFilterActive();
        String alertLabel = alertActive
                ? tr("screen.resourceobserver.storage.filter.deficits_only")
                : tr("screen.resourceobserver.storage.filter.all_status");
        TextView alertBtn = tinyButton(terminal, alertLabel);
        alertBtn.setTextColor(alertActive ? UiThemeTokens.AMBER : UiThemeTokens.TEXT_MUTED);
        alertBtn.setOnClickListener(v -> {
            // 始终读取 bridge 中的当前状态（避免旧闭包读到过期值）
            boolean newFilterState = !terminal.getBridge().isStorageAlertFilterActive();
            terminal.getBridge().updateStorageFilter(
                    terminal.getBridge().getStorageSelectedNodeId(),
                    newFilterState);
            // 直接触发重建，绕过 onDataChanged 的延迟抑制，确保按钮状态立即刷新
            terminal.rebuildContent();
        });
        filterBar.addView(alertBtn);
    }

    // ===================== Item List（右列） =====================

    /**
     * 构建物品健康表格 —— 右列主体（移植自 StorageItemListRenderer）。
     * 列：图标/名称 | 本地数量 | 变化量 | 消耗速率 | 缓冲条。
     */
    private static void buildItemList(ResourceTerminalFragment terminal, LinearLayout section,
                                      int sectionWidth, StorageNetworkViewModel vm) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        addText(section, tr("screen.resourceobserver.storage.section.inventory_health"),
                UiThemeTokens.TEXT, 12, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);

        List<StorageNetworkViewModel.ItemRow> items = vm.items();
        if (items == null || items.isEmpty()) {
            addText(section, tr("screen.resourceobserver.storage.no_items"),
                    UiThemeTokens.TEXT_MUTED, 10, terminal.dp(4),
                    ViewGroup.LayoutParams.WRAP_CONTENT, false);
            return;
        }

        // Column widths
        int innerW = sectionWidth - terminal.dp(16);
        int nameW = (int) (innerW * 0.30);
        int amountW = (int) (innerW * 0.15);
        int deltaW = (int) (innerW * 0.12);
        int burnW = (int) (innerW * 0.15);
        int bufferW = (int) (innerW * 0.28);

        // Header
        LinearLayout header = new LinearLayout(terminal.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(terminal.dp(4), terminal.dp(2), terminal.dp(4), terminal.dp(2));
        header.setBackground(makeBackground(header, UiThemeTokens.HEADER_BG, UiThemeTokens.DIVIDER, 4, 1));

        addText(header, tr("screen.resourceobserver.storage.col.identity"), UiThemeTokens.TEXT_MUTED, 9, 0, nameW, true);
        addText(header, tr("screen.resourceobserver.storage.col.stock"), UiThemeTokens.TEXT_MUTED, 9, 0, amountW, true);
        addText(header, tr("screen.resourceobserver.storage.col.delta"), UiThemeTokens.TEXT_MUTED, 9, 0, deltaW, true);
        addText(header, tr("screen.resourceobserver.storage.col.burn_rate"), UiThemeTokens.TEXT_MUTED, 9, 0, burnW, true);
        addText(header, tr("screen.resourceobserver.storage.col.buffer"), UiThemeTokens.TEXT_MUTED, 9, 0, bufferW, true);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = terminal.dp(4);
        section.addView(header, headerParams);

        // Rows
        int rowH = ModernUiTheme.scaledDp(terminal, ITEM_ROW_HEIGHT_DP);
        for (StorageNetworkViewModel.ItemRow item : items) {
            LinearLayout row = new LinearLayout(terminal.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(terminal.dp(4), terminal.dp(2), terminal.dp(4), terminal.dp(2));

            // Alert level coloring
            int alertBg = switch (item.alertLevel()) {
                case RED -> 0x30FF2020;
                case YELLOW -> 0x20FFB020;
                case GREEN -> 0x00000000;
            };
            if (alertBg != 0) {
                row.setBackground(makeBackground(row, alertBg, 0, 4, 0));
            }

            // Icon + name (strictly matches Overview's table pattern)
            int iconSize = ModernUiTheme.scaledDp(terminal, 12);
            boolean hasAlertDot = item.alertLevel() != StorageNetworkViewModel.AlertLevel.GREEN;
            int alertDotSpace = hasAlertDot ? (terminal.dp(4) + terminal.dp(3)) : 0;
            int textW = nameW - alertDotSpace - iconSize - terminal.dp(2);

            LinearLayout nameCell = new LinearLayout(terminal.getContext());
            nameCell.setOrientation(LinearLayout.HORIZONTAL);
            nameCell.setGravity(Gravity.CENTER_VERTICAL);

            if (hasAlertDot) {
                View alertDot = new View(terminal.getContext());
                int alertColor = item.alertLevel() == StorageNetworkViewModel.AlertLevel.RED
                        ? UiThemeTokens.ROSE : UiThemeTokens.AMBER;
                alertDot.setBackground(colorDot(alertDot, alertColor));
                LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(terminal.dp(4), terminal.dp(4));
                adParams.rightMargin = terminal.dp(3);
                nameCell.addView(alertDot, adParams);
            }

            InlineItemIconView icon = new InlineItemIconView(terminal.getContext());
            icon.setItemId(item.itemId());
            nameCell.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

            TextView nameTv = addText(nameCell, item.displayName(), UiThemeTokens.TEXT, 9, 0, textW, true);
            ((LinearLayout.LayoutParams) nameTv.getLayoutParams()).leftMargin = terminal.dp(2);

            nameCell.setLayoutParams(new LinearLayout.LayoutParams(nameW, ViewGroup.LayoutParams.WRAP_CONTENT));
            OverviewPageBuilder.attachItemHover(nameCell, terminal, item.itemId(), item.globalAmount(), item.displayName());
            row.addView(nameCell);

            addText(row, compact(item.localAmount()), UiThemeTokens.TEXT, 9, 0, amountW, true);

            double delta = item.delta();
            int deltaColor = delta > 0 ? UiThemeTokens.EMERALD : (delta < 0 ? UiThemeTokens.ROSE : UiThemeTokens.TEXT);
            addText(row, (delta > 0 ? "+" : "") + compact(Math.round(delta)), deltaColor, 9, 0, deltaW, true);

            addText(row, compact(Math.round(item.burnRatePerMin())), UiThemeTokens.TEXT_MUTED, 9, 0, burnW, true);

            // Buffer bar + text
            LinearLayout bufferCell = new LinearLayout(terminal.getContext());
            bufferCell.setOrientation(LinearLayout.VERTICAL);

            addText(bufferCell, item.estimatedBufferText(), UiThemeTokens.TEXT_MUTED, 8, 0, 0, true);

            int barW = bufferW - terminal.dp(4);
            int barH = terminal.dp(4);
            FrameLayout barContainer = new FrameLayout(terminal.getContext());
            barContainer.setBackground(makeBackground(barContainer, 0xFF0A1020, UiThemeTokens.DIVIDER, 1000, 0));

            int fillW = Math.max(1, (int) (barW * Math.min(1.0, item.bufferRatio())));
            View fillView = new View(terminal.getContext());
            int bufColor = item.bufferRatio() < 0.2 ? UiThemeTokens.ROSE
                    : (item.bufferRatio() < 0.5 ? UiThemeTokens.AMBER : UiThemeTokens.EMERALD);
            fillView.setBackground(colorDot(fillView, bufColor));
            barContainer.addView(fillView, new FrameLayout.LayoutParams(fillW, barH));

            LinearLayout.LayoutParams barLP = new LinearLayout.LayoutParams(barW, barH);
            barLP.topMargin = terminal.dp(1);
            bufferCell.addView(barContainer, barLP);

            bufferCell.setLayoutParams(new LinearLayout.LayoutParams(bufferW, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(bufferCell);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowH);
            section.addView(row, rowParams);
        }
    }

    /**
     * Same as {@link #buildItemList} but also populates cell reference maps
     * for subsequent in-place incremental updates.
     * <p>
     * cellRefs: itemId → [localAmount, delta, burnRate, bufferText] TextViews<br>
     * barRefs:  itemId → [fillView, barContainer] Views
     */
    private static void buildItemListWithCellRefs(ResourceTerminalFragment terminal,
                                                   LinearLayout section, int sectionWidth,
                                                   StorageNetworkViewModel vm,
                                                   Map<String, TextView[]> cellRefs,
                                                   Map<String, View[]> barRefs) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        addText(section, tr("screen.resourceobserver.storage.section.inventory_health"),
                UiThemeTokens.TEXT, 12, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);

        List<StorageNetworkViewModel.ItemRow> items = vm.items();
        if (items == null || items.isEmpty()) {
            addText(section, tr("screen.resourceobserver.storage.no_items"),
                    UiThemeTokens.TEXT_MUTED, 10, terminal.dp(4),
                    ViewGroup.LayoutParams.WRAP_CONTENT, false);
            return;
        }

        int innerW = sectionWidth - terminal.dp(16);
        int nameW = (int) (innerW * 0.30);
        int amountW = (int) (innerW * 0.15);
        int deltaW = (int) (innerW * 0.12);
        int burnW = (int) (innerW * 0.15);
        int bufferW = (int) (innerW * 0.28);

        // Header
        LinearLayout header = new LinearLayout(terminal.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(terminal.dp(4), terminal.dp(2), terminal.dp(4), terminal.dp(2));
        header.setBackground(makeBackground(header, UiThemeTokens.HEADER_BG, UiThemeTokens.DIVIDER, 4, 1));

        addText(header, tr("screen.resourceobserver.storage.col.identity"), UiThemeTokens.TEXT_MUTED, 9, 0, nameW, true);
        addText(header, tr("screen.resourceobserver.storage.col.stock"), UiThemeTokens.TEXT_MUTED, 9, 0, amountW, true);
        addText(header, tr("screen.resourceobserver.storage.col.delta"), UiThemeTokens.TEXT_MUTED, 9, 0, deltaW, true);
        addText(header, tr("screen.resourceobserver.storage.col.burn_rate"), UiThemeTokens.TEXT_MUTED, 9, 0, burnW, true);
        addText(header, tr("screen.resourceobserver.storage.col.buffer"), UiThemeTokens.TEXT_MUTED, 9, 0, bufferW, true);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = terminal.dp(4);
        section.addView(header, headerParams);

        // Rows
        int rowH = ModernUiTheme.scaledDp(terminal, ITEM_ROW_HEIGHT_DP);
        for (StorageNetworkViewModel.ItemRow item : items) {
            LinearLayout row = new LinearLayout(terminal.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(terminal.dp(4), terminal.dp(2), terminal.dp(4), terminal.dp(2));

            int alertBg = switch (item.alertLevel()) {
                case RED -> 0x30FF2020;
                case YELLOW -> 0x20FFB020;
                case GREEN -> 0x00000000;
            };
            if (alertBg != 0) {
                row.setBackground(makeBackground(row, alertBg, 0, 4, 0));
            }

            int iconSize = ModernUiTheme.scaledDp(terminal, 12);
            boolean hasAlertDot = item.alertLevel() != StorageNetworkViewModel.AlertLevel.GREEN;
            int alertDotSpace = hasAlertDot ? (terminal.dp(4) + terminal.dp(3)) : 0;
            int textW = nameW - alertDotSpace - iconSize - terminal.dp(2);

            LinearLayout nameCell = new LinearLayout(terminal.getContext());
            nameCell.setOrientation(LinearLayout.HORIZONTAL);
            nameCell.setGravity(Gravity.CENTER_VERTICAL);

            if (hasAlertDot) {
                View alertDot = new View(terminal.getContext());
                int alertColor = item.alertLevel() == StorageNetworkViewModel.AlertLevel.RED
                        ? UiThemeTokens.ROSE : UiThemeTokens.AMBER;
                alertDot.setBackground(colorDot(alertDot, alertColor));
                LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(terminal.dp(4), terminal.dp(4));
                adParams.rightMargin = terminal.dp(3);
                nameCell.addView(alertDot, adParams);
            }

            InlineItemIconView icon = new InlineItemIconView(terminal.getContext());
            icon.setItemId(item.itemId());
            nameCell.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

            TextView nameTv = addText(nameCell, item.displayName(), UiThemeTokens.TEXT, 9, 0, textW, true);
            ((LinearLayout.LayoutParams) nameTv.getLayoutParams()).leftMargin = terminal.dp(2);

            nameCell.setLayoutParams(new LinearLayout.LayoutParams(nameW, ViewGroup.LayoutParams.WRAP_CONTENT));
            OverviewPageBuilder.attachItemHover(nameCell, terminal, item.itemId(), item.globalAmount(), item.displayName());
            row.addView(nameCell);

            TextView amountTv = addText(row, compact(item.localAmount()), UiThemeTokens.TEXT, 9, 0, amountW, true);

            double delta = item.delta();
            int deltaColor = delta > 0 ? UiThemeTokens.EMERALD : (delta < 0 ? UiThemeTokens.ROSE : UiThemeTokens.TEXT);
            TextView deltaTv = addText(row, (delta > 0 ? "+" : "") + compact(Math.round(delta)), deltaColor, 9, 0, deltaW, true);

            TextView burnTv = addText(row, compact(Math.round(item.burnRatePerMin())), UiThemeTokens.TEXT_MUTED, 9, 0, burnW, true);

            LinearLayout bufferCell = new LinearLayout(terminal.getContext());
            bufferCell.setOrientation(LinearLayout.VERTICAL);

            TextView bufferTextTv = addText(bufferCell, item.estimatedBufferText(), UiThemeTokens.TEXT_MUTED, 8, 0, 0, true);

            int barW = bufferW - terminal.dp(4);
            int barH = terminal.dp(4);
            FrameLayout barContainer = new FrameLayout(terminal.getContext());
            barContainer.setBackground(makeBackground(barContainer, 0xFF0A1020, UiThemeTokens.DIVIDER, 1000, 0));

            int fillW = Math.max(1, (int) (barW * Math.min(1.0, item.bufferRatio())));
            View fillView = new View(terminal.getContext());
            int bufColor = item.bufferRatio() < 0.2 ? UiThemeTokens.ROSE
                    : (item.bufferRatio() < 0.5 ? UiThemeTokens.AMBER : UiThemeTokens.EMERALD);
            fillView.setBackground(colorDot(fillView, bufColor));
            barContainer.addView(fillView, new FrameLayout.LayoutParams(fillW, barH));

            LinearLayout.LayoutParams barLP = new LinearLayout.LayoutParams(barW, barH);
            barLP.topMargin = terminal.dp(1);
            bufferCell.addView(barContainer, barLP);

            bufferCell.setLayoutParams(new LinearLayout.LayoutParams(bufferW, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(bufferCell);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowH);
            section.addView(row, rowParams);

            // Store cell references for in-place updates
            cellRefs.put(item.itemId(), new TextView[]{amountTv, deltaTv, burnTv, bufferTextTv});
            barRefs.put(item.itemId(), new View[]{fillView, barContainer});
        }
    }

    /**
     * 弹出网络重命名输入框 —— 使用 PopupWindow + EditText 实现。
     * 模式与 OverviewPageBuilder.showGroupNameInputPopup 保持一致。
     */
    private static void showRenameNetworkPopup(ResourceTerminalFragment terminal,
                                               String networkId, String currentName) {
        View rootView = terminal.getView();
        if (rootView == null) return;

        int dp4 = terminal.dp(4);
        int dp8 = terminal.dp(8);
        int dp12 = terminal.dp(12);

        // Outer card container
        LinearLayout container = new LinearLayout(terminal.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp12, dp12, dp12, dp12);
        ShapeDrawable cardBg = new ShapeDrawable();
        cardBg.setColor(UiThemeTokens.PANEL_BG);
        cardBg.setCornerRadius(terminal.dp(10));
        cardBg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        container.setBackground(cardBg);

        // Title
        TextView titleView = createText(rootView,
                tr("screen.resourceobserver.storage.node.rename_title"),
                UiThemeTokens.TITLE, 11, true);
        titleView.setPadding(0, 0, 0, dp8);
        container.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // EditText for network name
        EditText input = new EditText(terminal.getContext());
        input.setHint(tr("screen.resourceobserver.storage.node.rename_hint"));
        input.setText(currentName);
        input.setSingleLine(true);
        input.setTextSize(10 * getTextScale());
        input.setTextColor(UiThemeTokens.TEXT);
        ShapeDrawable inputBg = new ShapeDrawable();
        inputBg.setColor(UiThemeTokens.SECTION_BG);
        inputBg.setCornerRadius(dp4);
        inputBg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        input.setBackground(inputBg);
        input.setPadding(dp8, dp4, dp8, dp4);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = dp4;
        container.addView(input, inputLp);

        // Button row
        LinearLayout buttonRow = new LinearLayout(terminal.getContext());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = dp8;

        PopupWindow popup = new PopupWindow(container, terminal.dp(200), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setElevation(terminal.dp(8));
        popup.setOnDismissListener(() -> {
            terminal.setTextInputActive(false);
            if (OverviewPageBuilder.sActivePopupWindow == popup) {
                OverviewPageBuilder.sActivePopupWindow = null;
            }
        });

        // Cancel button
        TextView cancelBtn = tinyButton(rootView,
                tr("screen.resourceobserver.storage.node.rename_cancel"));
        cancelBtn.setOnClickListener(v -> popup.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.rightMargin = dp8;
        buttonRow.addView(cancelBtn, cancelLp);

        // Confirm button
        TextView confirmBtn = tinyButton(rootView,
                tr("screen.resourceobserver.storage.node.rename_confirm"));
        confirmBtn.setTextColor(UiThemeTokens.CYAN);
        confirmBtn.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            popup.dismiss();
            // Send rename action (empty name clears custom name)
            terminal.sendUiAction(UiActionType.RENAME_NETWORK, networkId, name);
        });
        buttonRow.addView(confirmBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        container.addView(buttonRow, brLp);

        // Register with global active popup so onPause() can dismiss it
        OverviewPageBuilder.sActivePopupWindow = popup;
        // Show centered
        terminal.setTextInputActive(true);
        popup.showAtLocation(rootView, Gravity.CENTER, 0, 0);
        input.requestFocus();
    }
}

package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.client.ui.render.ChartMath;
import com.yuyinrl.resourceobserver.client.ui.render.ChartRenderer;
import com.yuyinrl.resourceobserver.client.ui.render.RenderUtils;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.UiActionType;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.PropertyValuesHolder;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.graphics.drawable.GradientDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.Menu;
import icyllis.modernui.view.MenuItem;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.SubMenu;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.menu.MenuBuilder;
import icyllis.modernui.view.menu.MenuPopupHelper;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.PopupMenu;
import icyllis.modernui.widget.PopupWindow;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.*;

/**
 * Overview 页面构建器 —— 在 ResourceTerminalFragment 的内容区域中构建总览仪表板。
 * <p>
 * 包含区段：Header、KPI 卡片、Chart 占位、Watchlist、Observer 表格。
 * 数据来源为 {@link OverviewViewModel}，通过 {@link ViewModelBridge} 获取。
 */
final class OverviewPageBuilder {

    private static final int TABLE_ROW_HEIGHT_DP = 18;

    /** Currently showing context menu helper — used for manual outside-click dismiss. */
    private static MenuPopupHelper sActiveMenuHelper;
    /** Transparent overlay added to the root FrameLayout to catch outside-menu clicks. */
    private static View sDismissOverlay;
    /** Currently showing PopupMenu (group filter / status filter). */
    private static PopupMenu sActivePopupMenu;
    /** Currently showing PopupWindow (group name input / rename). Package-visible for cross-page popups. */
    static PopupWindow sActivePopupWindow;

    // Context menu item IDs
    private static final int MENU_CREATE_GROUP = 1;
    private static final int MENU_MOVE_TO = 2;
    private static final int MENU_CLEAR_GROUP = 3;
    private static final int MENU_ASSIGN_BASE = 100;
    private static final int MENU_RENAME_GROUP = 10;
    private static final int MENU_DELETE_GROUP = 11;

    private OverviewPageBuilder() {}

    static void build(ResourceTerminalFragment terminal, FrameLayout container, int contentW, int contentH, boolean animate) {
        OverviewViewModel vm = terminal.getBridge().getOverviewViewModel();
        if (vm == null) return;

        // Initialize cell reference maps for incremental update
        terminal.setOverviewTableCells(new HashMap<>());
        terminal.setOverviewWatchCells(new HashMap<>());

        int dp4 = terminal.dp(4);
        int dp6 = terminal.dp(6);
        int dp8 = terminal.dp(8);
        int sectionGap = dp6;
        int scrollbarW = terminal.dp(8);
        int innerW = Math.max(terminal.dp(160), contentW - scrollbarW);

        // 计算区段高度（响应式）
        int headerH = clamp(Math.round(contentH * 0.12f), terminal.dp(44), terminal.dp(90));
        int kpiH = clamp(Math.round(contentH * 0.18f), terminal.dp(64), terminal.dp(130));
        int chartH = clamp(Math.round(contentH * 0.32f), terminal.dp(100), terminal.dp(240));
        int watchH = clamp(Math.round(contentH * 0.18f), terminal.dp(64), terminal.dp(130));

        // ScrollView —— 保留 clip 以裁切未滚动到的内容
        ScrollView scroll = new ScrollView(terminal.getContext());
        scroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(scroll);

        LinearLayout content = new LinearLayout(terminal.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        // section 内部已关闭 clip（section() 中设置），content 级别保留 clip 防止溢出
        content.setLayoutParams(new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.addView(content);

        // Header section
        LinearLayout headerSection = section(terminal, innerW, headerH);
        buildHeaderSection(terminal, headerSection, innerW, vm);
        addSection(content, headerSection, 0);

        // KPI section
        LinearLayout kpiSection = section(terminal, innerW, kpiH);
        buildKpiSection(terminal, kpiSection, innerW, kpiH, vm);
        addSection(content, kpiSection, sectionGap);

        // Chart section
        LinearLayout chartSection = section(terminal, innerW, chartH);
        buildChartSection(terminal, chartSection, innerW, chartH, vm);
        addSection(content, chartSection, sectionGap);

        // Watchlist section
        if (vm.watchlistItems() != null && !vm.watchlistItems().isEmpty()) {
            LinearLayout watchSection = section(terminal, innerW, watchH);
            buildWatchlistSection(terminal, watchSection, innerW, watchH, vm);
            addSection(content, watchSection, sectionGap);
        }

        // Table section
        int tableHeight = measureTableHeight(terminal, vm);
        LinearLayout tableSection = section(terminal, innerW, tableHeight);
        buildTableSection(terminal, tableSection, innerW, tableHeight, vm);
        addSection(content, tableSection, sectionGap);

        // Store structural key for incremental update detection
        terminal.setOverviewStructuralKey(computeStructuralKey(terminal, vm));

        // 仅在首次加载 / 切换页面时播放瀑布入场动画
        if (animate) {
            staggerSlideIn(content, 0, 40, 250);
        }
    }

    // ===================== Incremental Update =====================

    /**
     * 计算 Overview 页面的"结构指纹" —— 捕获会影响视图树结构的状态，
     * 忽略仅影响数值文本的数据变化。结构相同时可执行原地增量刷新。
     */
    private static String computeStructuralKey(ResourceTerminalFragment terminal, OverviewViewModel vm) {
        StringBuilder sb = new StringBuilder(256);
        // KPI card types
        if (vm.kpis() != null) {
            for (OverviewViewModel.KpiMetric k : vm.kpis()) {
                sb.append(k.type()).append(',');
            }
        }
        sb.append('|');
        // Link status presence
        sb.append(vm.linkStatus() != null ? "linked" : "unlinked");
        sb.append('|');
        // Chart window & scope (affect chart toolbar labels)
        sb.append(vm.chartWindow()).append(',').append(vm.chartScopeItemId());
        sb.append('|');
        // UI state (filter, sort, groups)
        if (vm.uiState() != null) {
            OverviewViewModel.UiState ui = vm.uiState();
            sb.append(ui.groupFilterKey()).append(',');
            sb.append(ui.sortMode()).append(',');
            sb.append(ui.sortDesc()).append(',');
            sb.append(ui.statusFilter()).append(',');
            sb.append(ui.watchlistLimit());
            if (ui.groups() != null) {
                sb.append('[');
                for (OverviewViewModel.GroupOption g : ui.groups()) {
                    sb.append(g.key()).append(',');
                }
                sb.append(']');
            }
        }
        sb.append('|');
        // Watchlist item IDs (order matters for pagination)
        if (vm.watchlistItems() != null) {
            for (OverviewViewModel.WatchlistItem w : vm.watchlistItems()) {
                sb.append(w.itemId()).append(',');
            }
        }
        sb.append('|');
        // Table group structure: group keys + row item IDs
        if (vm.tableGroups() != null) {
            for (OverviewViewModel.TableGroup g : vm.tableGroups()) {
                sb.append(g.key()).append(':');
                if (g.rows() != null) {
                    for (OverviewViewModel.TableRow r : g.rows()) {
                        sb.append(r.itemId()).append(',');
                    }
                }
                sb.append(';');
            }
        }
        sb.append('|');
        // Local UI state that affects rendered views
        sb.append(terminal.getSelectedItemId());
        sb.append('|');
        sb.append(terminal.getWatchlistPageIndex());
        return sb.toString();
    }

    /**
     * 尝试对 Overview 页面执行增量刷新 —— 仅原地更新数值文本，不重建视图树。
     * <p>
     * 成功条件：页面结构指纹未变化（只有数值内容变了）。
     * 成功时更新 KPI 卡片、图表、表格单元格、收藏列表单元格的文本。
     *
     * @return true 增量刷新成功, false 结构已变需要完整重建
     */
    static boolean tryIncrementalUpdate(ResourceTerminalFragment terminal) {
        OverviewViewModel vm = terminal.getBridge().getOverviewViewModel();
        if (vm == null) return false;

        KpiSectionState kpiState = terminal.getKpiSectionState();
        ChartSectionState chartState = terminal.getChartSectionState();
        if (kpiState == null || chartState == null) return false;

        // Compare structural key — if structure changed, need full rebuild
        String newKey = computeStructuralKey(terminal, vm);
        String oldKey = terminal.getOverviewStructuralKey();
        if (oldKey == null || !newKey.equals(oldKey)) return false;

        // ── KPI cards: update value + trend text + color ──
        List<OverviewViewModel.KpiMetric> kpis = vm.kpis();
        if (kpis != null) {
            for (int i = 0; i < Math.min(kpis.size(), kpiState.count); i++) {
                OverviewViewModel.KpiMetric kpi = kpis.get(i);
                if (kpiState.valueTvs[i] != null) {
                    boolean changed = !kpi.value().equals(kpiState.prevValues[i])
                            || !Objects.equals(kpi.trend(), kpiState.prevTrends[i]);
                    kpiState.valueTvs[i].setText(kpi.value());
                    if (kpiState.trendTvs[i] != null) {
                        kpiState.trendTvs[i].setText(kpi.trend());
                        kpiState.trendTvs[i].setTextColor(statusColor(kpi.status()));
                    }
                    if (changed && kpiState.cards[i] != null) {
                        playKpiPulse(kpiState.cards[i], kpiState.valueTvs[i]);
                    }
                    kpiState.prevValues[i] = kpi.value();
                    kpiState.prevTrends[i] = kpi.trend();
                }
            }
        }

        // ── Chart: in-place refresh via existing infrastructure ──
        refreshChartSection(terminal, chartState);

        // ── Table cells: update production/consumption/net/stock text ──
        Map<String, TextView[]> tableCells = terminal.getOverviewTableCells();
        if (tableCells != null && vm.tableGroups() != null) {
            for (OverviewViewModel.TableGroup group : vm.tableGroups()) {
                if (group.rows() == null) continue;
                for (OverviewViewModel.TableRow row : group.rows()) {
                    TextView[] cells = tableCells.get(row.itemId());
                    if (cells == null || cells.length < 4) continue;
                    cells[0].setText(compact(row.production()));
                    cells[1].setText(compact(row.consumption()));
                    long net = row.net();
                    int netColor = net > 0 ? UiThemeTokens.EMERALD
                            : (net < 0 ? UiThemeTokens.ROSE : UiThemeTokens.TEXT);
                    cells[2].setText((net > 0 ? "+" : "") + compact(net));
                    cells[2].setTextColor(netColor);
                    cells[3].setText(compact(row.stock()));
                }
            }
        }

        // ── Watchlist cells: update net rate + stock text ──
        Map<String, TextView[]> watchCells = terminal.getOverviewWatchCells();
        if (watchCells != null && vm.watchlistItems() != null) {
            for (OverviewViewModel.WatchlistItem item : vm.watchlistItems()) {
                TextView[] cells = watchCells.get(item.itemId());
                if (cells == null || cells.length < 2) continue;
                int wlNetColor = item.netPerMinute() >= 0 ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE;
                String netText = (item.netPerMinute() >= 0 ? "+" : "") + item.netPerMinute() + "/min";
                cells[0].setText(tr("screen.resourceobserver.overview.watchlist.net", netText));
                cells[0].setTextColor(wlNetColor);
                cells[1].setText(tr("screen.resourceobserver.overview.watchlist.stock", compact(item.stock())));
            }
        }

        // Update structural key for next cycle
        terminal.setOverviewStructuralKey(newKey);
        return true;
    }

    // ===================== Header =====================

    private static void buildHeaderSection(ResourceTerminalFragment terminal, LinearLayout section,
                                           int sectionWidth, OverviewViewModel vm) {
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setPadding(terminal.dp(12), terminal.dp(8), terminal.dp(12), terminal.dp(8));

        // Left: title + subtitle
        LinearLayout left = new LinearLayout(terminal.getContext());
        left.setOrientation(LinearLayout.VERTICAL);
        section.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        addText(left, vm.headerTitle(), UiThemeTokens.TITLE, 14, 0,
                ViewGroup.LayoutParams.WRAP_CONTENT, false);

        // Right: link status card
        int cardW = clamp(sectionWidth / 3, terminal.dp(140), terminal.dp(280));
        LinearLayout statusCard = new LinearLayout(terminal.getContext());
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setPadding(terminal.dp(8), terminal.dp(4), terminal.dp(8), terminal.dp(4));

        boolean linked = vm.linkStatus() != null;
        int statusBorder = linked ? UiThemeTokens.CYAN : UiThemeTokens.ROSE;
        statusCard.setBackground(makeBackground(statusCard, 0xE0182A41, statusBorder, 6, 1));
        section.addView(statusCard, new LinearLayout.LayoutParams(cardW, terminal.dp(36)));

        // Status dot
        View dot = new View(terminal.getContext());
        dot.setBackground(colorDot(dot, linked ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(terminal.dp(6), terminal.dp(6));
        dotParams.topMargin = terminal.dp(4);
        dotParams.rightMargin = terminal.dp(4);
        statusCard.addView(dot, dotParams);

        LinearLayout textColumn = new LinearLayout(terminal.getContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        statusCard.addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        addText(textColumn, vm.linkStatus(), linked ? UiThemeTokens.CYAN : UiThemeTokens.ROSE,
                9, 0, ViewGroup.LayoutParams.MATCH_PARENT, true);
    }

    // ===================== KPI Cards =====================

    /** 保存 KPI 卡片的状态引用，用于增量刷新时原地更新数值与动画 */
    static final class KpiSectionState {
        final int count;
        final LinearLayout[] cards;
        final TextView[] valueTvs;
        final TextView[] trendTvs;
        final String[] prevValues;
        final String[] prevTrends;

        KpiSectionState(int count) {
            this.count = count;
            cards = new LinearLayout[count];
            valueTvs = new TextView[count];
            trendTvs = new TextView[count];
            prevValues = new String[count];
            prevTrends = new String[count];
        }
    }

    private static void buildKpiSection(ResourceTerminalFragment terminal, LinearLayout section,
                                        int sectionWidth, int sectionHeight, OverviewViewModel vm) {
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        List<OverviewViewModel.KpiMetric> kpis = vm.kpis();
        if (kpis == null || kpis.isEmpty()) return;

        int gap = terminal.dp(8);
        int cardCount = kpis.size();

        KpiSectionState prevState = terminal.getKpiSectionState();
        KpiSectionState state = new KpiSectionState(cardCount);

        int innerW = sectionWidth - terminal.dp(16);
        int cardW = Math.max(terminal.dp(100), (innerW - (cardCount - 1) * gap) / cardCount);
        int cardH = Math.max(terminal.dp(50), sectionHeight - terminal.dp(16));

        for (int i = 0; i < cardCount; i++) {
            OverviewViewModel.KpiMetric kpi = kpis.get(i);
            LinearLayout cardView = new LinearLayout(terminal.getContext());
            cardView.setOrientation(LinearLayout.VERTICAL);
            cardView.setPadding(terminal.dp(8), terminal.dp(6), terminal.dp(8), terminal.dp(6));

            boolean selected = kpi.type() != null
                    && kpi.type().name().equalsIgnoreCase(
                    terminal.getSelectedItemId() != null ? terminal.getSelectedItemId() : "");
            int cardBg = selected ? 0xEE24466C : UiThemeTokens.CARD_BG;
            int cardBorder = selected ? UiThemeTokens.CYAN : UiThemeTokens.CARD_BORDER;
            cardView.setBackground(statefulBackground(cardView, cardBg, cardBorder, 8, 1));
            addHoverScaleEffect(cardView);
            cardView.setClickable(true);
            cardView.setFocusable(true);
            addPressScaleEffect(cardView);

            // 点击 KPI 卡片 → 弹出详情对话框（STORAGE 走专用弹窗）
            final OverviewViewModel.KpiType kpiType = kpi.type();
            cardView.setOnClickListener(v -> {
                if (kpiType == OverviewViewModel.KpiType.STORAGE) {
                    showStorageDetailDialog(terminal, vm);
                } else {
                    showKpiDetailDialog(terminal, vm, kpiType);
                }
            });

            addText(cardView, kpi.label(), UiThemeTokens.TEXT_MUTED, 9, 0,
                    ViewGroup.LayoutParams.MATCH_PARENT, true);
            TextView valueTv = addText(cardView, kpi.value(), UiThemeTokens.TITLE, 14, terminal.dp(2),
                    ViewGroup.LayoutParams.MATCH_PARENT, true);
            TextView trendTv = addText(cardView, kpi.trend(), statusColor(kpi.status()), 9, terminal.dp(2),
                    ViewGroup.LayoutParams.MATCH_PARENT, true);

            // 保存卡片引用和当前值（用于增量刷新与动画检测）
            state.cards[i] = cardView;
            state.valueTvs[i] = valueTv;
            state.trendTvs[i] = trendTv;
            state.prevValues[i] = kpi.value();
            state.prevTrends[i] = kpi.trend();

            // Icon placeholder
            View iconBox = new View(terminal.getContext());
            iconBox.setBackground(makeBackground(iconBox, 0x4016253C, 0x4016253C, 4, 0));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(terminal.dp(16), terminal.dp(16));
            iconParams.topMargin = terminal.dp(4);
            cardView.addView(iconBox, iconParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardW, cardH);
            if (i > 0) params.leftMargin = gap;
            section.addView(cardView, params);

            // KPI 卡片值变化动画
            if (prevState != null && i < prevState.count && prevState.prevValues[i] != null) {
                boolean changed = !kpi.value().equals(prevState.prevValues[i])
                        || !Objects.equals(kpi.trend(), prevState.prevTrends[i]);
                if (changed) {
                    playKpiPulse(cardView, valueTv);
                }
            }
        }
        terminal.setKpiSectionState(state);
    }

    /**
     * KPI 值变化脉冲动画 —— 卡片微缩放 + 数值闪烁，时长短以适配高频刷新。
     */
    private static void playKpiPulse(LinearLayout card, TextView valueTv) {
        // 卡片弹跳
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(card,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.04f, 1.0f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.04f, 1.0f));
        pulse.setDuration(250);
        pulse.setInterpolator(TimeInterpolator.DECELERATE);
        pulse.start();

        // 数值闪烁
        ObjectAnimator flash = ObjectAnimator.ofPropertyValuesHolder(valueTv,
                PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.35f, 1.0f));
        flash.setDuration(220);
        flash.start();
    }

    // ===================== Chart Section =====================

    /** Holds references to mutable chart section UI elements for local refresh. */
    static final class ChartSectionState {
        TextView windowBtn;
        icyllis.modernui.widget.RadioGroup dataTypeGroup;
        icyllis.modernui.widget.RadioButton dataItemsBtn;
        icyllis.modernui.widget.RadioButton dataEnergyBtn;
        TextView pageBtn;
        TextView modeBtn;
        TextView smoothBtn;
        TextView subtitleTv;
        LinearLayout legendLayout;
        ChartView chartView;
    }

    private static final int ID_DATA_ITEMS = 1001;
    private static final int ID_DATA_ENERGY = 1002;

    private static void buildChartSection(ResourceTerminalFragment terminal, LinearLayout section,
                                          int sectionWidth, int sectionHeight, OverviewViewModel vm) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        ChartSectionState state = new ChartSectionState();

        // --- Title row with buttons ---
        LinearLayout titleRow = new LinearLayout(terminal.getContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        addText(titleRow, tr("screen.resourceobserver.overview.section.chart"),
                UiThemeTokens.TEXT, 12, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);

        View spacer = new View(terminal.getContext());
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1);
        spacerParams.weight = 1.0f;
        titleRow.addView(spacer, spacerParams);

        LinearLayout buttons = new LinearLayout(terminal.getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        // Window button
        state.windowBtn = chartToggleButton(terminal, "", true);
        state.windowBtn.setOnClickListener(v -> {
            terminal.setChartWindow(terminal.getChartWindow().next());
            terminal.sendChartWindowChange();
            refreshChartSection(terminal, state);
        });
        buttons.addView(state.windowBtn);

        // Page button (throughput / stock)
        state.pageBtn = chartToggleButton(terminal, "", true);
        state.pageBtn.setOnClickListener(v -> {
            terminal.setChartPage(terminal.getChartPage().next());
            refreshChartSection(terminal, state);
        });
        buttons.addView(state.pageBtn, leftGap(terminal.dp(4)));

        // Line mode button — opens dropdown popup
        state.modeBtn = chartToggleButton(terminal, "", true);
        state.modeBtn.setOnClickListener(v -> {
            if (terminal.getChartPage() == ChartRenderer.ChartPage.THROUGHPUT) {
                showLineModePopup(terminal, v, state);
            }
        });
        buttons.addView(state.modeBtn, leftGap(terminal.dp(4)));

        // Smoothing button
        state.smoothBtn = chartToggleButton(terminal, "", true);
        state.smoothBtn.setOnClickListener(v -> {
            terminal.setChartSmoothingMode(terminal.getChartSmoothingMode().next());
            refreshChartSection(terminal, state);
        });
        buttons.addView(state.smoothBtn, leftGap(terminal.dp(4)));

        titleRow.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(titleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- Subtitle ---
        state.subtitleTv = new TextView(terminal.getContext());
        state.subtitleTv.setTextSize(10 * getTextScale());
        state.subtitleTv.setSingleLine();
        state.subtitleTv.setTextColor(UiThemeTokens.TEXT_MUTED);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = terminal.dp(2);
        section.addView(state.subtitleTv, subLp);

        // --- Chart view ---
        int plotH = Math.max(terminal.dp(80), sectionHeight - terminal.dp(70));
        int plotW = sectionWidth - terminal.dp(16);
        state.chartView = new ChartView(terminal.getContext());
        state.chartView.setBackground(makeBackground(state.chartView, 0x5A0D1628, 0x773A5478, 6, 1));
        LinearLayout.LayoutParams plotParams = new LinearLayout.LayoutParams(plotW, plotH);
        plotParams.topMargin = terminal.dp(4);
        section.addView(state.chartView, plotParams);
        terminal.setActiveChartView(state.chartView);
        terminal.setChartSectionState(state);

        // --- Bottom row: legend (left) + data type radio group (right) ---
        LinearLayout bottomRow = new LinearLayout(terminal.getContext());
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomParams.topMargin = terminal.dp(3);

        state.legendLayout = new LinearLayout(terminal.getContext());
        state.legendLayout.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.addView(state.legendLayout, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // Radio group: Items / Energy
        boolean hasEnergy = vm != null && vm.energyChartSeries() != null && !vm.energyChartSeries().isEmpty();
        state.dataTypeGroup = new icyllis.modernui.widget.RadioGroup(terminal.getContext());
        state.dataTypeGroup.setOrientation(LinearLayout.HORIZONTAL);

        state.dataItemsBtn = createRadioButton(terminal, ID_DATA_ITEMS,
                tr("screen.resourceobserver.overview.chart.data.items"), true);
        state.dataTypeGroup.addView(state.dataItemsBtn);

        state.dataEnergyBtn = createRadioButton(terminal, ID_DATA_ENERGY,
                tr("screen.resourceobserver.overview.chart.data.energy"), hasEnergy);
        icyllis.modernui.widget.RadioGroup.LayoutParams energyLp =
                new icyllis.modernui.widget.RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        energyLp.leftMargin = terminal.dp(6);
        state.dataTypeGroup.addView(state.dataEnergyBtn, energyLp);

        // Set initial checked state
        state.dataTypeGroup.check(terminal.getChartDataType() == ChartRenderer.ChartDataType.ITEMS
                ? ID_DATA_ITEMS : ID_DATA_ENERGY);

        state.dataTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            ChartRenderer.ChartDataType newType = checkedId == ID_DATA_ENERGY
                    ? ChartRenderer.ChartDataType.ENERGY : ChartRenderer.ChartDataType.ITEMS;
            if (terminal.getChartDataType() != newType) {
                terminal.setChartDataType(newType);
                refreshChartSection(terminal, state);
            }
        });

        bottomRow.addView(state.dataTypeGroup, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(bottomRow, bottomParams);

        // Initial population
        refreshChartSection(terminal, state);
    }

    /**
     * 局部刷新图表区段 —— 更新按钮标签、副标题、图例和图表数据，
     * 不触发整页 rebuildUi。
     */
    static void refreshChartSection(ResourceTerminalFragment terminal, ChartSectionState state) {
        if (state == null || state.chartView == null) return;

        ChartWindow chartWindow = terminal.getChartWindow();
        ChartRenderer.ChartPage chartPage = terminal.getChartPage();
        ChartRenderer.LineMode lineMode = terminal.getChartLineMode();
        ChartRenderer.SmoothingMode smoothingMode = terminal.getChartSmoothingMode();
        ChartRenderer.ChartDataType chartDataType = terminal.getChartDataType();

        OverviewViewModel vm = terminal.getBridge().getOverviewViewModel();
        boolean hasEnergy = vm != null && vm.energyChartSeries() != null && !vm.energyChartSeries().isEmpty();

        // --- Update button labels and styles ---
        state.windowBtn.setText(tr(chartWindow.translationKey()));

        // Update radio group enabled state
        state.dataEnergyBtn.setEnabled(hasEnergy);
        state.dataEnergyBtn.setTextColor(hasEnergy ? UiThemeTokens.TEXT : UiThemeTokens.TEXT_MUTED);
        // Sync checked state without re-triggering listener
        int expectedId = chartDataType == ChartRenderer.ChartDataType.ENERGY ? ID_DATA_ENERGY : ID_DATA_ITEMS;
        if (state.dataTypeGroup.getCheckedId() != expectedId) {
            state.dataTypeGroup.check(expectedId);
        }

        String pageLabel = chartPage == ChartRenderer.ChartPage.THROUGHPUT
                ? tr("screen.resourceobserver.overview.chart.tab.throughput")
                : tr("screen.resourceobserver.overview.chart.tab.stock");
        state.pageBtn.setText(pageLabel);

        boolean lineModeEnabled = chartPage == ChartRenderer.ChartPage.THROUGHPUT;
        state.modeBtn.setText(lineModeLabel(lineMode));
        updateButtonStyle(terminal, state.modeBtn, lineModeEnabled);

        String smoothLabel = smoothingMode == ChartRenderer.SmoothingMode.SMOOTH
                ? tr("screen.resourceobserver.overview.chart.smoothing.smooth")
                : tr("screen.resourceobserver.overview.chart.smoothing.raw");
        state.smoothBtn.setText(smoothLabel);

        // --- Update subtitle ---
        String selectedLabel = terminal.getSelectedItemDisplayName();
        String scopeLabel = (selectedLabel == null || selectedLabel.isBlank())
                ? tr("screen.resourceobserver.overview.chart.scope.global")
                : selectedLabel;
        String subtitle = chartPage == ChartRenderer.ChartPage.STOCK
                ? tr("screen.resourceobserver.overview.chart.subtitle.stock", scopeLabel, chartWindow.shortLabel())
                : tr("screen.resourceobserver.overview.chart.subtitle.throughput", scopeLabel, chartWindow.shortLabel());
        state.subtitleTv.setText(subtitle);

        // --- Update legend ---
        state.legendLayout.removeAllViews();
        if (chartPage == ChartRenderer.ChartPage.STOCK) {
            addText(state.legendLayout, tr("screen.resourceobserver.overview.chart.legend.stock"),
                    UiThemeTokens.BLUE, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        } else {
            if (lineMode.showProduction()) {
                addText(state.legendLayout, tr("screen.resourceobserver.overview.chart.legend.production"),
                        UiThemeTokens.CYAN, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);
            }
            if (lineMode.showConsumption()) {
                TextView c = addText(state.legendLayout, tr("screen.resourceobserver.overview.chart.legend.consumption"),
                        UiThemeTokens.AMBER, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);
                if (lineMode.showProduction()) {
                    ((LinearLayout.LayoutParams) c.getLayoutParams()).leftMargin = terminal.dp(10);
                }
            }
            if (lineMode.showNet()) {
                TextView n = addText(state.legendLayout, tr("screen.resourceobserver.overview.chart.legend.net"),
                        UiThemeTokens.EMERALD, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);
                if (lineMode.showProduction() || lineMode.showConsumption()) {
                    ((LinearLayout.LayoutParams) n.getLayoutParams()).leftMargin = terminal.dp(10);
                }
            }

        }

        // --- Update chart data ---
        if (vm != null) {
            java.util.List<OverviewViewModel.FlowPoint> series =
                    chartDataType == ChartRenderer.ChartDataType.ENERGY
                            ? vm.energyChartSeries() : vm.chartSeries();
            if (series != null && !series.isEmpty()) {
                state.chartView.setChartData(series, chartPage, lineMode, smoothingMode);
            }
        }
    }

    private static void updateButtonStyle(ResourceTerminalFragment terminal, TextView btn, boolean enabled) {
        btn.setTextColor(enabled ? UiThemeTokens.TEXT : UiThemeTokens.TEXT_MUTED);
        btn.setBackground(makeBackground(btn,
                enabled ? 0xAA23456A : UiThemeTokens.TAB_INACTIVE,
                enabled ? UiThemeTokens.CYAN : UiThemeTokens.SECTION_BORDER,
                4, 1));
        btn.setEnabled(enabled);
    }

    private static TextView chartToggleButton(ResourceTerminalFragment terminal, String text, boolean enabled) {
        TextView btn = new TextView(terminal.getContext());
        btn.setText(text);
        btn.setTextSize(9 * getTextScale());
        btn.setSingleLine();
        btn.setGravity(Gravity.CENTER);
        btn.setTextColor(enabled ? UiThemeTokens.TEXT : UiThemeTokens.TEXT_MUTED);
        btn.setPadding(terminal.dp(6), terminal.dp(2), terminal.dp(6), terminal.dp(2));
        btn.setBackground(makeBackground(btn,
                enabled ? 0xAA23456A : UiThemeTokens.TAB_INACTIVE,
                enabled ? UiThemeTokens.CYAN : UiThemeTokens.SECTION_BORDER,
                4, 1));
        btn.setEnabled(enabled);
        btn.setClickable(true);
        btn.setFocusable(true);
        return btn;
    }

    private static String lineModeLabel(ChartRenderer.LineMode mode) {
        return switch (mode) {
            case ALL -> tr("screen.resourceobserver.overview.chart.mode.all");
            case PRODUCTION -> tr("screen.resourceobserver.overview.chart.mode.production");
            case CONSUMPTION -> tr("screen.resourceobserver.overview.chart.mode.consumption");
            case NET -> tr("screen.resourceobserver.overview.chart.mode.net");
        };
    }

    private static icyllis.modernui.widget.RadioButton createRadioButton(
            ResourceTerminalFragment terminal, int id, String text, boolean enabled) {
        icyllis.modernui.widget.RadioButton rb = new icyllis.modernui.widget.RadioButton(terminal.getContext());
        rb.setId(id);
        rb.setText(text);
        rb.setTextSize(9 * getTextScale());
        rb.setTextColor(enabled ? UiThemeTokens.TEXT : UiThemeTokens.TEXT_MUTED);
        rb.setEnabled(enabled);
        rb.setPadding(terminal.dp(2), 0, terminal.dp(4), 0);
        return rb;
    }

    private static void showLineModePopup(ResourceTerminalFragment terminal, View anchor,
                                          ChartSectionState state) {
        PopupMenu popup = new PopupMenu(terminal.getContext(), anchor);
        Menu menu = popup.getMenu();
        ChartRenderer.LineMode[] modes = ChartRenderer.LineMode.values();
        ChartRenderer.LineMode current = terminal.getChartLineMode();
        for (int i = 0; i < modes.length; i++) {
            String label = lineModeLabel(modes[i]);
            if (modes[i] == current) label = "• " + label;
            menu.add(0, i, i, label);
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id >= 0 && id < modes.length) {
                terminal.setChartLineMode(modes[id]);
                refreshChartSection(terminal, state);
            }
            return true;
        });
        popup.setOnDismissListener(m -> { if (sActivePopupMenu == popup) sActivePopupMenu = null; });
        sActivePopupMenu = popup;
        popup.show();
    }

    // ===================== Watchlist =====================

    private static void buildWatchlistSection(ResourceTerminalFragment terminal, LinearLayout section,
                                              int sectionWidth, int sectionHeight, OverviewViewModel vm) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        List<OverviewViewModel.WatchlistItem> items = vm.watchlistItems();
        int gap = terminal.dp(8);
        int listWidth = sectionWidth - terminal.dp(16);
        int perPage = sectionWidth >= terminal.dp(500) ? 3 : (sectionWidth >= terminal.dp(340) ? 2 : 1);
        int totalPages = Math.max(1, (items.size() + perPage - 1) / perPage);

        // 保证 pageIndex 不越界（删除最后一页全部卡片后回退）
        int pageIndex = Math.min(terminal.getWatchlistPageIndex(), totalPages - 1);
        pageIndex = Math.max(0, pageIndex);
        terminal.setWatchlistPageIndex(pageIndex);

        int startIdx = pageIndex * perPage;
        int endIdx = Math.min(startIdx + perPage, items.size());
        // 卡片宽度始终按 perPage 计算，保证每页卡片等宽、不因数量变化而缩放
        int cardW = (listWidth - (perPage - 1) * gap) / perPage;

        // ---------- 标题行：标题 + 翻页控件 ----------
        LinearLayout titleBar = new LinearLayout(terminal.getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        section.addView(titleBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 标题占满剩余空间，将翻页控件推到右侧
        TextView titleTv = addText(titleBar, tr("screen.resourceobserver.overview.section.watchlist"),
                UiThemeTokens.TEXT, 12, 0, 0, false);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        if (totalPages > 1) {
            final int curPage = pageIndex;
            int btnSize = terminal.dp(18);
            int btnMargin = terminal.dp(4);

            // ◀ 上一页
            TextView prevBtn = new TextView(terminal.getContext());
            prevBtn.setText("◀");
            prevBtn.setTextSize(9 * getTextScale());
            prevBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
            prevBtn.setGravity(Gravity.CENTER);
            prevBtn.setBackground(statefulBackground(prevBtn, UiThemeTokens.CARD_BG, UiThemeTokens.DIVIDER, 1000, 1));
            prevBtn.setClickable(true);
            prevBtn.setFocusable(true);
            addPressScaleEffect(prevBtn);
            prevBtn.setAlpha(curPage > 0 ? 1f : 0.3f);
            prevBtn.setOnClickListener(v -> {
                if (curPage > 0) {
                    terminal.setWatchlistPageIndex(curPage - 1);
                    terminal.onDataChanged(); // 本地重建，无需网络请求
                }
            });
            LinearLayout.LayoutParams prevP = new LinearLayout.LayoutParams(btnSize, btnSize);
            titleBar.addView(prevBtn, prevP);

            // 页码文字
            TextView pageTv = new TextView(terminal.getContext());
            pageTv.setText((curPage + 1) + "/" + totalPages);
            pageTv.setTextSize(9 * getTextScale());
            pageTv.setTextColor(UiThemeTokens.TEXT_MUTED);
            pageTv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams pageP = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pageP.leftMargin = btnMargin;
            pageP.rightMargin = btnMargin;
            titleBar.addView(pageTv, pageP);

            // ▶ 下一页
            TextView nextBtn = new TextView(terminal.getContext());
            nextBtn.setText("▶");
            nextBtn.setTextSize(9 * getTextScale());
            nextBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
            nextBtn.setGravity(Gravity.CENTER);
            nextBtn.setBackground(statefulBackground(nextBtn, UiThemeTokens.CARD_BG, UiThemeTokens.DIVIDER, 1000, 1));
            nextBtn.setClickable(true);
            nextBtn.setFocusable(true);
            addPressScaleEffect(nextBtn);
            nextBtn.setAlpha(curPage < totalPages - 1 ? 1f : 0.3f);
            nextBtn.setOnClickListener(v -> {
                if (curPage < totalPages - 1) {
                    terminal.setWatchlistPageIndex(curPage + 1);
                    terminal.onDataChanged(); // 本地重建，无需网络请求
                }
            });
            LinearLayout.LayoutParams nextP = new LinearLayout.LayoutParams(btnSize, btnSize);
            titleBar.addView(nextBtn, nextP);
        }

        // ---------- 卡片区域 ----------
        LinearLayout row = new LinearLayout(terminal.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        int listHeight = Math.max(terminal.dp(20), sectionHeight - terminal.dp(50));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(listWidth, listHeight);
        rowParams.topMargin = terminal.dp(4);
        section.addView(row, rowParams);

        for (int i = startIdx; i < endIdx; i++) {
            OverviewViewModel.WatchlistItem item = items.get(i);
            LinearLayout card = new LinearLayout(terminal.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(terminal.dp(8), terminal.dp(6), terminal.dp(8), terminal.dp(6));

            boolean selected = Objects.equals(item.itemId(), terminal.getSelectedItemId());
            int bg = selected ? 0xCC142338 : UiThemeTokens.CARD_BG;
            int border = selected ? UiThemeTokens.CYAN : UiThemeTokens.CARD_BORDER;
            card.setBackground(statefulBackground(card, bg, border, 8, 1));

            final String itemId = item.itemId();
            card.setClickable(true);
            card.setFocusable(true);
            // hover lift effect removed: attachItemHover below sets hover listener (only one allowed)
            card.setOnClickListener(v -> {
                terminal.setSelectedItemId(itemId);
                terminal.requestRefreshNow();
            });

            // Title row: icon + name + remove button
            LinearLayout cardTitleRow = new LinearLayout(terminal.getContext());
            cardTitleRow.setOrientation(LinearLayout.HORIZONTAL);
            cardTitleRow.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(cardTitleRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            int wlIconSize = ModernUiTheme.scaledDp(terminal, 12);
            InlineItemIconView wlIcon = new InlineItemIconView(terminal.getContext());
            wlIcon.setItemId(item.itemId());
            LinearLayout.LayoutParams wlIconParams = new LinearLayout.LayoutParams(wlIconSize, wlIconSize);
            wlIconParams.rightMargin = terminal.dp(3);
            cardTitleRow.addView(wlIcon, wlIconParams);

            TextView name = addText(cardTitleRow, item.displayName(), UiThemeTokens.TEXT, 10, 0, 0, true);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            attachItemHover(card, terminal, item.itemId(), item.stock(), item.displayName());

            TextView remove = addText(cardTitleRow, "✕", UiThemeTokens.ROSE, 10, 0, terminal.dp(22), false);
            remove.setMinimumWidth(terminal.dp(22));
            remove.setMinimumHeight(terminal.dp(22));
            remove.setPadding(terminal.dp(3), terminal.dp(3), terminal.dp(3), terminal.dp(3));
            remove.setGravity(Gravity.CENTER);
            remove.setBackground(statefulBackground(remove, 0x552A1018, 0xAA7F1D28, 1000, 1));
            remove.setClickable(true);
            remove.setFocusable(true);
            final int pageForRemove = pageIndex;
            remove.setOnClickListener(v -> {
                animateCardRemove(card, () -> {
                    ViewGroup parent = (ViewGroup) card.getParent();
                    if (parent != null) parent.removeView(card);
                    // 删除后如果当前页变空，自动回到上一页
                    if (parent instanceof LinearLayout r && r.getChildCount() == 0 && pageForRemove > 0) {
                        terminal.setWatchlistPageIndex(pageForRemove - 1);
                    }
                    terminal.sendUiAction(UiActionType.TOGGLE_WATCH, itemId, "");
                });
            });

            // Net per minute — capture references for incremental update
            int netColor = item.netPerMinute() >= 0 ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE;
            String netText = (item.netPerMinute() >= 0 ? "+" : "") + item.netPerMinute() + "/min";
            TextView wlNetTv = addText(card, tr("screen.resourceobserver.overview.watchlist.net", netText),
                    netColor, 9, terminal.dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);
            TextView wlStockTv = addText(card, tr("screen.resourceobserver.overview.watchlist.stock", compact(item.stock())),
                    UiThemeTokens.TEXT_MUTED, 9, terminal.dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);

            // Store cell refs for incremental update
            Map<String, TextView[]> watchCells = terminal.getOverviewWatchCells();
            if (watchCells != null) {
                watchCells.put(item.itemId(), new TextView[]{wlNetTv, wlStockTv});
            }

            int cardIdx = i - startIdx;
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.MATCH_PARENT);
            if (cardIdx > 0) cardParams.leftMargin = gap;
            row.addView(card, cardParams);

            // 入场动画：从右侧滑入 + 淡入
            final int delay = cardIdx * 60;
            card.setAlpha(0f);
            card.setTranslationX(terminal.dp(30));
            card.post(() -> {
                ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(card,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X, terminal.dp(30), 0f));
                anim.setStartDelay(delay);
                anim.setDuration(250);
                anim.setInterpolator(TimeInterpolator.DECELERATE);
                anim.start();
            });
        }
    }

    // ===================== Table =====================

    private static void buildTableSection(ResourceTerminalFragment terminal, LinearLayout section,
                                          int sectionWidth, int sectionHeight, OverviewViewModel vm) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(terminal.dp(8), terminal.dp(8), terminal.dp(8), terminal.dp(8));

        // Filter toolbar
        LinearLayout toolbar = new LinearLayout(terminal.getContext());
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        OverviewViewModel.UiState uiState = vm.uiState();

        // Status filter button
        TextView statusBtn = tinyButton(terminal, tr(uiState.statusFilter().translationKey()));
        statusBtn.setOnClickListener(v -> showStatusFilterPopup(terminal, v, uiState));
        toolbar.addView(statusBtn);

        // 搜索框
        buildSearchBox(terminal, toolbar);

        toolbar.addView(spacer(terminal), spacerParams());

        // Reset button — 重置筛选条件并清除单物品图表选择，恢复全局视图
        TextView resetBtn = tinyButton(terminal, tr("screen.resourceobserver.overview.filter.reset"));
        resetBtn.setOnClickListener(v -> {
            // 先清除选择，确保后续 sendUiAction 以 GLOBAL 作用域发送
            terminal.setSelectedItemId(null);
            terminal.sendUiAction(UiActionType.RESET_FILTERS, "", "");
        });
        toolbar.addView(resetBtn);

        section.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Table header row
        int colW = (sectionWidth - terminal.dp(24)) / 5;
        LinearLayout headerRow = new LinearLayout(terminal.getContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setPadding(terminal.dp(4), terminal.dp(4), terminal.dp(4), terminal.dp(4));
        headerRow.setBackground(makeBackground(headerRow, UiThemeTokens.HEADER_BG, UiThemeTokens.DIVIDER, 4, 1));

        addSortableHeader(terminal, headerRow, tr("screen.resourceobserver.overview.table.col_node"), colW, null, uiState);
        addSortableHeader(terminal, headerRow, tr("screen.resourceobserver.overview.filter.sort.production"),
                colW, TableSortMode.PRODUCTION, uiState);
        addSortableHeader(terminal, headerRow, tr("screen.resourceobserver.overview.filter.sort.consumption"),
                colW, TableSortMode.CONSUMPTION, uiState);
        addSortableHeader(terminal, headerRow, tr("screen.resourceobserver.overview.filter.sort.net"),
                colW, TableSortMode.NET, uiState);
        addSortableHeader(terminal, headerRow, tr("screen.resourceobserver.overview.filter.sort.stock"),
                colW, TableSortMode.STOCK, uiState);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = terminal.dp(4);
        section.addView(headerRow, headerParams);

        // Table body — group by group (use persisted expand state from fragment)
        Map<String, Boolean> expandedState = terminal.getTableExpandedState();
        for (OverviewViewModel.TableGroup group : vm.tableGroups()) {
            expandedState.putIfAbsent(group.key(), true);
            buildTableGroup(terminal, section, sectionWidth, group, expandedState);
        }
    }

    /**
     * 构建搜索框 —— 内联于筛选工具栏，按物品名称实时过滤表格内容。
     * 搜索词通过 {@link ViewModelBridge} 在 Overview 和 Storage Network 页面间共享。
     */
    private static void buildSearchBox(ResourceTerminalFragment terminal, LinearLayout toolbar) {
        int dp4 = terminal.dp(4);
        int dp6 = terminal.dp(6);
        int dp8 = terminal.dp(8);

        LinearLayout searchContainer = new LinearLayout(terminal.getContext());
        searchContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchContainer.setGravity(Gravity.CENTER_VERTICAL);

        EditText searchEdit = new EditText(terminal.getContext());
        searchEdit.setHint(tr("screen.resourceobserver.search.hint"));
        searchEdit.setSingleLine(true);
        searchEdit.setTextSize(10 * getTextScale());
        searchEdit.setTextColor(UiThemeTokens.TEXT);
        searchEdit.setHintTextColor(UiThemeTokens.TEXT_MUTED);

        ShapeDrawable searchBg = new ShapeDrawable();
        searchBg.setColor(UiThemeTokens.SECTION_BG);
        searchBg.setCornerRadius(dp4);
        searchBg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        searchEdit.setBackground(searchBg);
        searchEdit.setPadding(dp8, dp4, dp8, dp4);

        // 从 bridge 恢复当前搜索词（页面重建后保持一致）
        String currentQuery = terminal.getBridge().getSearchQuery();
        if (!currentQuery.isEmpty()) {
            searchEdit.setText(currentQuery);
        }

        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                terminal.dp(100), ViewGroup.LayoutParams.WRAP_CONTENT);
        editLp.leftMargin = dp6;
        searchContainer.addView(searchEdit, editLp);

        // "×" 清除按钮
        TextView clearBtn = new TextView(terminal.getContext());
        clearBtn.setText("×");
        clearBtn.setTextSize(12 * getTextScale());
        clearBtn.setTextColor(UiThemeTokens.TEXT_MUTED);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(dp4, 0, dp4, 0);
        clearBtn.setClickable(true);
        clearBtn.setFocusable(true);
        clearBtn.setVisibility(currentQuery.isEmpty() ? View.GONE : View.VISIBLE);
        clearBtn.setOnClickListener(v -> {
            searchEdit.setText("");
            terminal.scheduleSearchUpdate("");
        });
        searchContainer.addView(clearBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 文字变化监听 —— 200ms 防抖后触发 ViewModel 重建
        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString();
                clearBtn.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
                if (!text.equals(terminal.getBridge().getSearchQuery())) {
                    terminal.scheduleSearchUpdate(text);
                }
            }
        });

        // 跟踪焦点状态，页面重建后据此恢复焦点（重建期间的失焦事件忽略）
        searchEdit.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                terminal.getBridge().setSearchBoxFocused(true);
            } else if (!terminal.isRebuilding()) {
                terminal.getBridge().setSearchBoxFocused(false);
            }
        });

        // 页面重建后恢复焦点（无论搜索词是否为空）
        if (terminal.getBridge().isSearchBoxFocused()) {
            searchEdit.post(() -> {
                searchEdit.requestFocus();
                searchEdit.setSelection(searchEdit.getText().length());
            });
        }

        toolbar.addView(searchContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void addSortableHeader(ResourceTerminalFragment terminal, LinearLayout parent,
                                           String label, int colW, TableSortMode sortMode,
                                           OverviewViewModel.UiState uiState) {
        boolean isActive = sortMode != null && uiState.sortMode() == sortMode;
        String display = isActive ? label + (uiState.sortDesc() ? " ▼" : " ▲") : label;
        TextView tv = addText(parent, display,
                isActive ? UiThemeTokens.CYAN : UiThemeTokens.TEXT_MUTED, 9, 0, colW, true);
        if (sortMode != null) {
            tv.setClickable(true);
            tv.setFocusable(true);
            tv.setBackground(headerCellBackgroundStateful(tv));
            tv.setOnClickListener(v -> {
                terminal.sendUiAction(UiActionType.SET_SORT_MODE, "", sortMode.key());
            });
        }
    }

    private static void buildTableGroup(ResourceTerminalFragment terminal, LinearLayout parent,
                                        int sectionWidth, OverviewViewModel.TableGroup group,
                                        Map<String, Boolean> expandedState) {
        boolean expanded = expandedState.getOrDefault(group.key(), true);
        int colW = (sectionWidth - terminal.dp(24)) / 5;
        int rowH = ModernUiTheme.scaledDp(terminal, TABLE_ROW_HEIGHT_DP);

        // Group header
        LinearLayout groupHeader = new LinearLayout(terminal.getContext());
        groupHeader.setOrientation(LinearLayout.HORIZONTAL);
        groupHeader.setGravity(Gravity.CENTER_VERTICAL);
        groupHeader.setPadding(terminal.dp(4), terminal.dp(2), terminal.dp(4), terminal.dp(2));
        groupHeader.setBackground(groupHeaderBackgroundStateful(groupHeader));

        String arrow = expanded ? "▼ " : "▶ ";
        TextView groupTitle = addText(groupHeader, arrow + group.title(), UiThemeTokens.TEXT, 10,
                0, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        groupHeader.addView(spacer(terminal), spacerParams());

        TextView countLabel = addText(groupHeader, String.valueOf(group.rows().size()),
                UiThemeTokens.TEXT_MUTED, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, true);

        LinearLayout.LayoutParams ghParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowH);
        ghParams.topMargin = terminal.dp(2);
        parent.addView(groupHeader, ghParams);

        // Row container — 始终构建所有行，用 visibility 控制折叠
        LinearLayout rowContainer = new LinearLayout(terminal.getContext());
        rowContainer.setOrientation(LinearLayout.VERTICAL);

        for (OverviewViewModel.TableRow row : group.rows()) {
            LinearLayout dataRow = buildDataRow(terminal, rowContainer, row, colW, rowH, parent);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, rowH);
            rowContainer.addView(dataRow, rowParams);
        }

        parent.addView(rowContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!expanded) {
            rowContainer.setVisibility(View.GONE);
        }

        // 点击折叠/展开 — 本地动画，不触发全局重建
        groupHeader.setClickable(true);
        groupHeader.setFocusable(true);
        groupHeader.setOnClickListener(v -> {
            boolean wasExpanded = expandedState.getOrDefault(group.key(), true);
            boolean nowExpanded = !wasExpanded;
            expandedState.put(group.key(), nowExpanded);
            groupTitle.setText((nowExpanded ? "▼ " : "▶ ") + group.title());
            if (nowExpanded) {
                expandRows(rowContainer);
            } else {
                collapseRows(rowContainer);
            }
        });

        // Right-click: context popup for rename/delete (non-system groups only)
        groupHeader.setOnTouchListener(new View.OnTouchListener() {
            private boolean rightDown = false;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN
                        && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                    rightDown = true;
                    showGroupHeaderContextPopup(terminal, v, group,
                            Math.round(event.getRawX()), Math.round(event.getRawY()));
                    return true;
                }
                if (rightDown && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
                    rightDown = false;
                    return true;
                }
                return false;
            }
        });
    }

    /** 构建单个数据行（从 buildTableGroup 提取） */
    private static LinearLayout buildDataRow(ResourceTerminalFragment terminal, ViewGroup rowContainer,
                                              OverviewViewModel.TableRow row, int colW, int rowH,
                                              LinearLayout tableParent) {
        LinearLayout dataRow = new LinearLayout(terminal.getContext());
        dataRow.setOrientation(LinearLayout.HORIZONTAL);
        dataRow.setGravity(Gravity.CENTER_VERTICAL);
        dataRow.setPadding(terminal.dp(4), terminal.dp(2), terminal.dp(4), terminal.dp(2));

        dataRow.setBackground(rowBackgroundStateful(dataRow, row.critical()));

        // Star indicator
        int starW = terminal.dp(16);
        String starText = row.starred() ? "★" : "☆";
        TextView star = addText(dataRow, starText,
                row.starred() ? UiThemeTokens.AMBER : UiThemeTokens.TEXT_MUTED,
                9, 0, starW, false);
        star.setGravity(Gravity.CENTER);
        star.setOnClickListener(v -> {
            animateStarToggle(star);
            terminal.sendUiAction(UiActionType.TOGGLE_WATCH, row.itemId(), "");
        });
        // Forward right-click on the star to the row's context popup
        star.setOnTouchListener(new View.OnTouchListener() {
            private boolean rightDown = false;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN
                        && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                    rightDown = true;
                    showRowContextPopup(terminal, dataRow, row,
                            Math.round(event.getRawX()), Math.round(event.getRawY()));
                    return true;
                }
                if (rightDown && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
                    rightDown = false;
                    return true;
                }
                return false;
            }
        });

        // Item icon + name
        int iconSize = ModernUiTheme.scaledDp(terminal, 12);
        int nameW = colW - starW - iconSize - terminal.dp(2);

        LinearLayout nameCell = new LinearLayout(terminal.getContext());
        nameCell.setOrientation(LinearLayout.HORIZONTAL);
        nameCell.setGravity(Gravity.CENTER_VERTICAL);

        InlineItemIconView icon = new InlineItemIconView(terminal.getContext());
        icon.setItemId(row.itemId());
        nameCell.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView nameTv = addText(nameCell, row.displayName(), UiThemeTokens.TEXT, 9, 0, nameW, true);
        ((LinearLayout.LayoutParams) nameTv.getLayoutParams()).leftMargin = terminal.dp(2);

        nameCell.setLayoutParams(new LinearLayout.LayoutParams(colW - starW, ViewGroup.LayoutParams.WRAP_CONTENT));
        attachItemHover(nameCell, terminal, row.itemId(), row.stock(), row.displayName());
        dataRow.addView(nameCell);

        // Data columns — capture references for incremental update
        TextView prodTv = addText(dataRow, compact(row.production()), UiThemeTokens.CYAN, 9, 0, colW, true);
        TextView consTv = addText(dataRow, compact(row.consumption()), UiThemeTokens.AMBER, 9, 0, colW, true);

        long net = row.net();
        int netColor = net > 0 ? UiThemeTokens.EMERALD : (net < 0 ? UiThemeTokens.ROSE : UiThemeTokens.TEXT);
        TextView netTv = addText(dataRow, (net > 0 ? "+" : "") + compact(net), netColor, 9, 0, colW, true);
        TextView stockTv = addText(dataRow, compact(row.stock()), UiThemeTokens.TEXT, 9, 0, colW, true);

        // Store cell refs for incremental update
        Map<String, TextView[]> cellMap = terminal.getOverviewTableCells();
        if (cellMap != null) {
            cellMap.put(row.itemId(), new TextView[]{prodTv, consTv, netTv, stockTv});
        }

        dataRow.setClickable(true);
        dataRow.setFocusable(true);

        dataRow.setOnClickListener(v -> {
            terminal.setSelectedItemId(row.itemId());
            terminal.requestRefreshNow();
        });

        // Right-click: open context popup at cursor position
        dataRow.setOnTouchListener(new View.OnTouchListener() {
            private boolean rightDown = false;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN
                        && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                    rightDown = true;
                    showRowContextPopup(terminal, v, row,
                            Math.round(event.getRawX()), Math.round(event.getRawY()));
                    return true;
                }
                if (rightDown && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
                    rightDown = false;
                    return true;
                }
                return false;
            }
        });

        return dataRow;
    }

    // ===================== Popup menus =====================

    private static void showGroupFilterPopup(ResourceTerminalFragment terminal, View anchor,
                                             OverviewViewModel.UiState uiState) {
        PopupMenu popup = new PopupMenu(terminal.getContext(), anchor);
        Menu menu = popup.getMenu();

        menu.add(0, 0, 0, tr("screen.resourceobserver.overview.filter.group.all"));
        List<OverviewViewModel.GroupOption> groups = uiState.groups();
        for (int i = 0; i < groups.size(); i++) {
            menu.add(0, i + 1, i + 1, groups.get(i).displayName());
        }

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            String key = id == 0 ? PlayerUiPrefsSavedData.GROUP_FILTER_ALL
                    : (id - 1 < groups.size() ? groups.get(id - 1).key() : "");
            terminal.sendUiAction(UiActionType.SET_GROUP_FILTER_KEY, "", key);
            return true;
        });
        popup.setOnDismissListener(m -> { if (sActivePopupMenu == popup) sActivePopupMenu = null; });
        sActivePopupMenu = popup;
        popup.show();
    }

    private static void showStatusFilterPopup(ResourceTerminalFragment terminal, View anchor,
                                              OverviewViewModel.UiState uiState) {
        PopupMenu popup = new PopupMenu(terminal.getContext(), anchor);
        Menu menu = popup.getMenu();

        TableStatusFilter[] filters = TableStatusFilter.values();
        for (int i = 0; i < filters.length; i++) {
            menu.add(0, i, i, tr(filters[i].translationKey()));
        }

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id >= 0 && id < filters.length) {
                terminal.sendUiAction(UiActionType.SET_STATUS_FILTER, "", filters[id].key());
            }
            return true;
        });
        popup.setOnDismissListener(m -> { if (sActivePopupMenu == popup) sActivePopupMenu = null; });
        sActivePopupMenu = popup;
        popup.show();
    }

    // ===================== Row Context Menu (PopupWindow) =====================

    /**
     * 数据行右键菜单 —— 新建分组 + 移动到（二级子菜单含所有分组）。
     * 使用 MenuBuilder + MenuPopupHelper.show(x,y) 在光标位置弹出，支持二级菜单，
     * 点击外部自动关闭。
     */
    private static void showRowContextPopup(ResourceTerminalFragment terminal, View anchor,
                                            OverviewViewModel.TableRow row, int rawX, int rawY) {
        OverviewViewModel vm = terminal.getBridge().getOverviewViewModel();
        if (vm == null) return;

        MenuBuilder menu = new MenuBuilder(terminal.getContext());

        // ── 新建分组 ──
        menu.add(Menu.NONE, MENU_CREATE_GROUP, 0,
                tr("screen.resourceobserver.overview.group.menu.create"));

        // ── 移动到 → 二级子菜单 ──
        SubMenu moveToMenu = menu.addSubMenu(Menu.NONE, MENU_MOVE_TO, 1,
                tr("screen.resourceobserver.overview.group.menu.moveto"));

        List<OverviewViewModel.GroupOption> groups = vm.uiState().groups();
        int id = MENU_ASSIGN_BASE;
        for (OverviewViewModel.GroupOption group : groups) {
            String marker = Objects.equals(row.groupKey(), group.key()) ? "• " : "";
            moveToMenu.add(Menu.NONE, id, id - MENU_ASSIGN_BASE,
                    marker + group.displayName());
            id++;
        }

        menu.setCallback(new MenuBuilder.Callback() {
            @Override
            public boolean onMenuItemSelected(MenuBuilder m, MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == MENU_CREATE_GROUP) {
                    showGroupNameInputPopup(terminal,
                            tr("screen.resourceobserver.overview.group.input.create_title"), "",
                            name -> terminal.sendUiAction(UiActionType.CREATE_GROUP, row.itemId(), name));
                } else if (itemId >= MENU_ASSIGN_BASE) {
                    int idx = itemId - MENU_ASSIGN_BASE;
                    if (idx >= 0 && idx < groups.size()) {
                        terminal.sendUiAction(UiActionType.ASSIGN_ITEM_GROUP, row.itemId(), groups.get(idx).key());
                    }
                }
                return true;
            }
            @Override
            public void onMenuModeChange(MenuBuilder m) {}
        });

        MenuPopupHelper helper = new MenuPopupHelper(terminal.getContext(), menu, anchor);
        helper.setForceShowIcon(false);
        // show(x,y) treats coords as offsets from anchor — convert raw screen coords
        int[] anchorLoc = new int[2];
        anchor.getLocationInWindow(anchorLoc);
        showMenuWithOutsideDismiss(helper, terminal, rawX - anchorLoc[0], rawY - anchorLoc[1]);
    }

    /**
     * 分组标题行右键菜单 —— 重命名 / 删除分组。
     * 系统分组不弹出菜单。
     */
    private static void showGroupHeaderContextPopup(ResourceTerminalFragment terminal, View anchor,
                                                    OverviewViewModel.TableGroup group,
                                                    int rawX, int rawY) {
        OverviewViewModel vm = terminal.getBridge().getOverviewViewModel();
        if (vm == null) return;

        // Check if this is a system group
        boolean isSystem = false;
        for (OverviewViewModel.GroupOption opt : vm.uiState().groups()) {
            if (Objects.equals(opt.key(), group.key())) {
                isSystem = opt.systemGroup();
                break;
            }
        }
        if (isSystem) return;

        MenuBuilder menu = new MenuBuilder(terminal.getContext());

        menu.add(Menu.NONE, MENU_RENAME_GROUP, 0,
                tr("screen.resourceobserver.overview.group.menu.rename"));
        menu.add(Menu.NONE, MENU_DELETE_GROUP, 1,
                tr("screen.resourceobserver.overview.group.menu.delete"));

        menu.setCallback(new MenuBuilder.Callback() {
            @Override
            public boolean onMenuItemSelected(MenuBuilder m, MenuItem item) {
                if (item.getItemId() == MENU_RENAME_GROUP) {
                    showGroupNameInputPopup(terminal,
                            tr("screen.resourceobserver.overview.group.input.rename_title"),
                            group.title(),
                            name -> terminal.sendUiAction(UiActionType.RENAME_GROUP, group.key(), name));
                } else if (item.getItemId() == MENU_DELETE_GROUP) {
                    terminal.sendUiAction(UiActionType.DELETE_GROUP, group.key(), "");
                }
                return true;
            }
            @Override
            public void onMenuModeChange(MenuBuilder m) {}
        });

        MenuPopupHelper helper = new MenuPopupHelper(terminal.getContext(), menu, anchor);
        helper.setForceShowIcon(false);
        int[] anchorLoc = new int[2];
        anchor.getLocationInWindow(anchorLoc);
        showMenuWithOutsideDismiss(helper, terminal, rawX - anchorLoc[0], rawY - anchorLoc[1]);
    }

    /**
     * Shows a MenuPopupHelper at (offsetX, offsetY) and adds a transparent overlay to the
     * root FrameLayout so that clicking anywhere outside the menu (including when a submenu
     * is open) will dismiss the entire menu hierarchy.
     *
     * The overlay sits on top of all content but behind PopupWindows, so menu interaction
     * is unaffected while "outside" clicks are reliably caught.
     */
    private static void showMenuWithOutsideDismiss(MenuPopupHelper helper,
                                                   ResourceTerminalFragment terminal,
                                                   int offsetX, int offsetY) {
        // Dismiss any previously active menu
        dismissActiveMenu(terminal);

        sActiveMenuHelper = helper;

        // Add a transparent overlay to the root FrameLayout (stage)
        View rootView = terminal.getView();
        if (rootView instanceof ViewGroup rootGroup) {
            View overlay = new View(terminal.getContext());
            overlay.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overlay.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dismissActiveMenu(terminal);
                }
                return true; // consume so content behind doesn't react
            });
            rootGroup.addView(overlay);
            sDismissOverlay = overlay;
        }

        // Also clean up overlay when menu is dismissed by item selection
        helper.setOnDismissListener(() -> removeOverlay(terminal));

        helper.show(offsetX, offsetY);
    }

    /** Dismiss the currently active context menu and remove the overlay. Called from fragment lifecycle. */
    static void dismissActiveMenu(ResourceTerminalFragment terminal) {
        if (sActiveMenuHelper != null) {
            if (sActiveMenuHelper.isShowing()) {
                sActiveMenuHelper.dismiss();
            }
            sActiveMenuHelper = null;
        }
        if (sActivePopupMenu != null) {
            sActivePopupMenu.dismiss();
            sActivePopupMenu = null;
        }
        if (sActivePopupWindow != null) {
            if (sActivePopupWindow.isShowing()) {
                sActivePopupWindow.dismiss();
            }
            sActivePopupWindow = null;
        }
        removeOverlay(terminal);
    }

    /** Remove the transparent dismiss-overlay from the root FrameLayout. */
    private static void removeOverlay(ResourceTerminalFragment terminal) {
        if (sDismissOverlay != null) {
            View rootView = terminal.getView();
            if (rootView instanceof ViewGroup rootGroup) {
                rootGroup.removeView(sDismissOverlay);
            }
            sDismissOverlay = null;
        }
    }

    /**
     * 弹出分组名称输入框 —— 用于新建分组和重命名分组。
     * 使用 PopupWindow + EditText 实现，居中显示在屏幕中央。
     */
    private static void showGroupNameInputPopup(ResourceTerminalFragment terminal,
                                                String title, String initialText,
                                                java.util.function.Consumer<String> onConfirm) {
        View rootView = terminal.getView();
        if (rootView == null) return;

        int dp4 = terminal.dp(4);
        int dp8 = terminal.dp(8);
        int dp12 = terminal.dp(12);

        // Outer container with card-like background
        LinearLayout container = new LinearLayout(terminal.getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp12, dp12, dp12, dp12);
        ShapeDrawable cardBg = new ShapeDrawable();
        cardBg.setColor(UiThemeTokens.PANEL_BG);
        cardBg.setCornerRadius(terminal.dp(10));
        cardBg.setStroke(terminal.dp(1), UiThemeTokens.DIVIDER);
        container.setBackground(cardBg);

        // Title
        TextView titleView = createText(rootView, title, UiThemeTokens.TITLE, 11, true);
        titleView.setPadding(0, 0, 0, dp8);
        container.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // EditText for group name
        EditText input = new EditText(terminal.getContext());
        input.setHint(tr("screen.resourceobserver.overview.group.input.name_label"));
        input.setText(initialText);
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

        PopupWindow popup = new PopupWindow(container, terminal.dp(180), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setElevation(terminal.dp(8));
        popup.setOnDismissListener(() -> {
            terminal.setTextInputActive(false);
            if (sActivePopupWindow == popup) sActivePopupWindow = null;
        });

        // Cancel button
        TextView cancelBtn = tinyButton(rootView, tr("screen.resourceobserver.overview.group.input.cancel"));
        cancelBtn.setOnClickListener(v -> popup.dismiss());
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.rightMargin = dp8;
        buttonRow.addView(cancelBtn, cancelLp);

        // Confirm button
        TextView confirmBtn = tinyButton(rootView, tr("screen.resourceobserver.overview.group.input.confirm"));
        confirmBtn.setTextColor(UiThemeTokens.CYAN);
        confirmBtn.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                popup.dismiss();
                onConfirm.accept(name);
            }
        });
        buttonRow.addView(confirmBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        container.addView(buttonRow, brLp);

        // Show centered on the root view
        terminal.setTextInputActive(true);
        sActivePopupWindow = popup;
        popup.showAtLocation(rootView, Gravity.CENTER, 0, 0);
        input.requestFocus();
    }

    // ===================== KPI Detail Dialog =====================

    /** 显示 KPI 详情弹窗（带动画的模态对话框） */
    private static void showKpiDetailDialog(ResourceTerminalFragment terminal,
                                            OverviewViewModel vm,
                                            OverviewViewModel.KpiType type) {
        if (type == null) return;
        OverviewViewModel.KpiDetail detail = vm.kpiDetailFor(type);
        if (detail == null) return;

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
        int dialogW = Math.min(terminal.dp(340), cw - terminal.dp(20));
        int dialogH = Math.min(terminal.dp(230), ch - terminal.dp(20));

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
                // 弹窗关闭后触发一次内容重建，刷新被抑制期间的数据变更
                View root = terminal.getView();
                if (root != null) {
                    root.post(() -> terminal.onDataChanged());
                }
            }, 200);
        };

        // 点击遮罩关闭
        dimOverlay.setOnClickListener(v -> dismiss.run());

        // ---------- 标题栏（不随内容滚动） ----------
        LinearLayout titleBar = new LinearLayout(terminal.getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(terminal.dp(12), terminal.dp(10), terminal.dp(12), 0);
        titleBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int titleColor = statusColor(detail.status());
        TextView title = new TextView(terminal.getContext());
        title.setText(detail.title());
        title.setTextColor(UiThemeTokens.TITLE);
        title.setTextSize(12 * getTextScale());
        title.setSingleLine(true);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        titleBar.addView(title);

        TextView closeBtn = new TextView(terminal.getContext());
        closeBtn.setText("✕");
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
        populateKpiDetailBody(terminal, body, detail);

        // 注册实时刷新回调 —— 数据更新时重建 body 内容
        final OverviewViewModel.KpiType dialogType = type;
        terminal.setDialogContentRefresher(() -> {
            OverviewViewModel latestVm = terminal.getBridge().getOverviewViewModel();
            if (latestVm == null) return;
            OverviewViewModel.KpiDetail latestDetail = latestVm.kpiDetailFor(dialogType);
            if (latestDetail == null) return;
            body.removeAllViews();
            populateKpiDetailBody(terminal, body, latestDetail);
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
        dialogIn.setInterpolator(TimeInterpolator.OVERSHOOT);
        dialogIn.start();
    }

    /** 填充 KPI 详情弹窗的 body 内容（提示 + 分隔线 + 物品/流体通道） */
    private static void populateKpiDetailBody(ResourceTerminalFragment terminal,
                                              LinearLayout body,
                                              OverviewViewModel.KpiDetail detail) {
        int titleColor = statusColor(detail.status());
        // 提示文字
        String hint = detail.hintText() == null || detail.hintText().isBlank()
                ? tr("screen.resourceobserver.overview.kpi.trend.unavailable")
                : detail.hintText();
        TextView hintTv = new TextView(terminal.getContext());
        hintTv.setText(hint);
        hintTv.setTextColor(titleColor);
        hintTv.setTextSize(9 * getTextScale());
        hintTv.setSingleLine(true);
        body.addView(hintTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 分隔线
        View divider = new View(terminal.getContext());
        ShapeDrawable divBg = new ShapeDrawable();
        divBg.setColor(UiThemeTokens.DIVIDER);
        divider.setBackground(divBg);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, terminal.dp(1));
        divLp.topMargin = terminal.dp(6);
        body.addView(divider, divLp);

        // 物品通道
        buildKpiChannelBlock(terminal, body, detail.itemChannel());
        // 流体通道
        buildKpiChannelBlock(terminal, body, detail.fluidChannel());
    }

    /** 构建 KPI 通道详情块（物品/流体） */
    private static void buildKpiChannelBlock(ResourceTerminalFragment terminal,
                                             LinearLayout parent,
                                             OverviewViewModel.KpiDetailChannel channel) {
        if (channel == null || !channel.available()) return;

        // 通道标签
        TextView label = new TextView(terminal.getContext());
        label.setText(channel.label());
        label.setTextColor(UiThemeTokens.TEXT);
        label.setTextSize(10 * getTextScale());
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = terminal.dp(6);
        parent.addView(label, labelLp);

        // 最近值
        addDetailLine(terminal, parent, channel.recentText(), UiThemeTokens.TITLE, 2);
        // 上期值
        addDetailLine(terminal, parent, channel.previousText(), UiThemeTokens.TEXT_MUTED, 1);
        // 趋势
        addDetailLine(terminal, parent, channel.trendText(), UiThemeTokens.TEXT_MUTED, 1);
    }

    /**
     * 在 FrameLayout 内添加顶部和底部渐隐遮罩（GradientDrawable），
     * 用于暗示 ScrollView 内容可继续滚动。
     */
    private static void addScrollFadeOverlays(ResourceTerminalFragment terminal, FrameLayout wrapper) {
        int fadeH = terminal.dp(14);
        int panelBg = UiThemeTokens.PANEL_BG;
        int transparent = panelBg & 0x00FFFFFF; // 同色，alpha=0

        // 顶部渐隐：不透明 → 透明
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

        // 底部渐隐：透明 → 不透明
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

    /** 添加详情文本行 */
    private static void addDetailLine(ResourceTerminalFragment terminal, LinearLayout parent,
                                      String text, int color, int topDp) {
        if (text == null || text.isBlank()) return;
        TextView tv = new TextView(terminal.getContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(9 * getTextScale());
        tv.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = terminal.dp(topDp);
        parent.addView(tv, lp);
    }

    // ===================== Storage Detail Dialog =====================

    /** 显示存储状态 KPI 详情弹窗（完整迁移原始 renderStorageDetailDialog） */
    private static void showStorageDetailDialog(ResourceTerminalFragment terminal,
                                                OverviewViewModel vm) {
        OverviewViewModel.StorageDetail detail = vm.storageDetail();
        if (detail == null) return;

        FrameLayout overlay = terminal.getDialogOverlay();
        final FrameLayout container = overlay != null ? overlay : terminal.getContentContainer();
        if (container == null) return;
        int cw = container.getWidth();
        int ch = container.getHeight();
        if (cw <= 0 || ch <= 0) return;

        // 取 STORAGE KPI 的状态颜色
        int titleColor = vm.kpis().stream()
                .filter(k -> k.type() == OverviewViewModel.KpiType.STORAGE)
                .findFirst()
                .map(k -> statusColor(k.status()))
                .orElse(UiThemeTokens.TEXT);

        // ---------- 半透明遮罩层 ----------
        View dimOverlay = new View(terminal.getContext());
        ShapeDrawable dimBg = new ShapeDrawable();
        dimBg.setColor(0xAA04070E);
        dimOverlay.setBackground(dimBg);
        dimOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---------- 对话框外壳 ----------
        int dialogW = Math.min(terminal.dp(360), cw - terminal.dp(20));
        int dialogH = Math.min(terminal.dp(280), ch - terminal.dp(20));

        LinearLayout dialog = new LinearLayout(terminal.getContext());
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setBackground(makeBackground(dialog, UiThemeTokens.PANEL_BG, UiThemeTokens.SECTION_BORDER, 10, 1));
        dialog.setClipChildren(false);
        dialog.setClipToPadding(false);
        dialog.setClickable(true);

        FrameLayout.LayoutParams dialogLp = new FrameLayout.LayoutParams(dialogW, dialogH);
        dialogLp.gravity = Gravity.CENTER;

        // --- 关闭逻辑 ---
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
                // 弹窗关闭后触发一次内容重建，刷新被抑制期间的数据变更
                View root = terminal.getView();
                if (root != null) {
                    root.post(() -> terminal.onDataChanged());
                }
            }, 200);
        };
        dimOverlay.setOnClickListener(v -> dismiss.run());

        // ---------- 标题栏（不随内容滚动） ----------
        LinearLayout titleBar = new LinearLayout(terminal.getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(terminal.dp(12), terminal.dp(10), terminal.dp(12), 0);
        titleBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(terminal.getContext());
        title.setText(tr("screen.resourceobserver.overview.storage.detail.title"));
        title.setTextColor(UiThemeTokens.TITLE);
        title.setTextSize(12 * getTextScale());
        title.setSingleLine(true);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        titleBar.addView(title);

        TextView closeBtn = new TextView(terminal.getContext());
        closeBtn.setText("✕");
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
        populateStorageDetailBody(terminal, body, vm);

        // 注册实时刷新回调 —— 数据更新时重建 body 内容
        terminal.setDialogContentRefresher(() -> {
            OverviewViewModel latestVm = terminal.getBridge().getOverviewViewModel();
            if (latestVm == null) return;
            OverviewViewModel.StorageDetail latestDetail = latestVm.storageDetail();
            if (latestDetail == null) return;
            body.removeAllViews();
            populateStorageDetailBody(terminal, body, latestVm);
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
        dialogIn.setInterpolator(TimeInterpolator.OVERSHOOT);
        dialogIn.start();
    }

    /** 填充存储详情弹窗的 body 内容 */
    private static void populateStorageDetailBody(ResourceTerminalFragment terminal,
                                                  LinearLayout body,
                                                  OverviewViewModel vm) {
        OverviewViewModel.StorageDetail detail = vm.storageDetail();
        if (detail == null) return;

        int titleColor = vm.kpis().stream()
                .filter(k -> k.type() == OverviewViewModel.KpiType.STORAGE)
                .findFirst()
                .map(k -> statusColor(k.status()))
                .orElse(UiThemeTokens.TEXT);

        // 提示文字
        String hint = detail.hintText() == null || detail.hintText().isBlank()
                ? tr("screen.resourceobserver.overview.kpi.storage.normal")
                : detail.hintText();
        addStorageLine(terminal, body, hint, titleColor, 0, false, null);

        // 分隔线
        addDivider(terminal, body);

        // 磁盘存储区段
        addStorageLine(terminal, body,
                tr("screen.resourceobserver.overview.storage.detail.section.disk"),
                UiThemeTokens.TEXT, 8, false, null);
        buildStorageChannelRows(terminal, body, detail.diskItem());
        buildStorageChannelRows(terminal, body, detail.diskFluid());

        // 分隔线
        addDivider(terminal, body);

        // 外部存储区段
        addStorageLine(terminal, body,
                tr("screen.resourceobserver.overview.storage.detail.section.external"),
                UiThemeTokens.TEXT, 8, false, null);
        buildStorageChannelRows(terminal, body, detail.externalItem());
        buildStorageChannelRows(terminal, body, detail.externalFluid());

        // 分隔线
        addDivider(terminal, body);

        // 可靠性状态行
        String diskBool = boolLabel(detail.diskReliable());
        String extBool = boolLabel(detail.externalReliable());
        String reliableLine = tr("screen.resourceobserver.overview.storage.detail.reliability",
                diskBool, extBool);
        addStorageLine(terminal, body, reliableLine, UiThemeTokens.TEXT_MUTED, 6, false, null);
    }

    /** 添加弹窗内容分隔线 */
    private static void addDivider(ResourceTerminalFragment terminal, LinearLayout parent) {
        View div = new View(terminal.getContext());
        ShapeDrawable divBg = new ShapeDrawable();
        divBg.setColor(UiThemeTokens.DIVIDER);
        div.setBackground(divBg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, terminal.dp(1));
        lp.topMargin = terminal.dp(6);
        parent.addView(div, lp);
    }

    /** 构建存储通道行（label + usageText + typesText），带 tooltip 详情 */
    private static void buildStorageChannelRows(ResourceTerminalFragment terminal,
                                                LinearLayout parent,
                                                OverviewViewModel.StorageChannel channel) {
        if (channel == null) return;

        // 通道标签
        addStorageLine(terminal, parent, channel.label(), UiThemeTokens.TEXT_MUTED, 4, false, null);

        // 用量摘要（悬浮显示详情 tooltip）
        String usage = channel.usageText() == null || channel.usageText().isBlank() ? "N/A" : channel.usageText();
        String usageTooltip = channel.usageDetailText() != null && !channel.usageDetailText().isBlank()
                ? channel.label() + "\n" + channel.usageDetailText() : null;
        addStorageLine(terminal, parent, usage, UiThemeTokens.TITLE, 1, true, usageTooltip);

        // 类型摘要（悬浮显示详情 tooltip）
        String types = channel.typesText() == null || channel.typesText().isBlank() ? "N/A" : channel.typesText();
        String typesTooltip = channel.typesDetailText() != null && !channel.typesDetailText().isBlank()
                ? channel.label() + "\n" + channel.typesDetailText() : null;
        addStorageLine(terminal, parent, types, UiThemeTokens.TEXT_MUTED, 1, true, typesTooltip);
    }

    /** 添加存储详情文本行（可选 tooltip） */
    private static void addStorageLine(ResourceTerminalFragment terminal, LinearLayout parent,
                                       String text, int color, int topDp,
                                       boolean singleLine, String tooltipText) {
        if (text == null || text.isBlank()) return;
        TextView tv = new TextView(terminal.getContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(9 * getTextScale());
        tv.setSingleLine(singleLine);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = terminal.dp(topDp);
        parent.addView(tv, lp);
        if (tooltipText != null) {
            tv.setTooltipText(tooltipText);
        }
    }

    /** 布尔值国际化标签 */
    private static String boolLabel(boolean value) {
        return tr(value ? "message.resourceobserver.debug.bool.true"
                        : "message.resourceobserver.debug.bool.false");
    }

    // ===================== Measurement =====================

    // ===================== Item Tooltip =====================

    /**
     * 为视图添加物品悬浮监听，鼠标悬停时设置 Fragment 的 hover 状态，
     * 由 ClientKeyBindings 中的 ScreenEvent.Render.Post 处理器绘制原版风格 Tooltip。
     * @param displayName 显示名称，当无法从注册表解析物品/流体时用作后备
     */
    static void attachItemHover(View view, ResourceTerminalFragment terminal,
                                String itemId, long stock, String displayName) {
        // Pre-build static tooltip lines (item info — doesn't change across refreshes)
        // 不使用 getTooltipLines — 它会触发 NeoForge ItemTooltipEvent，
        // 导致 AE2 等 mod 注入无关行（如 "Hold [G] to open guide"）。
        // 改为手动构建：getHoverName + appendHoverText，只包含物品自身信息。
        List<Component> staticLines = new java.util.ArrayList<>();
        ItemStack stack = RenderUtils.itemStackFromItemId(itemId);
        if (!stack.isEmpty()) {
            staticLines.add(stack.getHoverName());
            stack.getItem().appendHoverText(stack,
                    net.minecraft.world.item.Item.TooltipContext.EMPTY,
                    staticLines,
                    net.minecraft.world.item.TooltipFlag.NORMAL);
        } else {
            // 尝试流体
            var fluidStack = RenderUtils.fluidStackFromItemId(itemId);
            if (!fluidStack.isEmpty()) {
                staticLines.add(fluidStack.getHoverName());
            }
        }
        // 后备：如果注册表解析失败，使用传入的显示名称
        if (staticLines.isEmpty() && displayName != null && !displayName.isBlank()) {
            staticLines.add(Component.literal(displayName));
        }
        if (staticLines.isEmpty()) return;

        // 库存行在悬停时动态查询最新值，确保增量刷新后数据不过期
        final List<Component> baseLines = List.copyOf(staticLines);
        final String hoverItemId = itemId;
        final long fallbackStock = stock;

        view.setOnHoverListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
                long currentStock = lookupCurrentStock(terminal, hoverItemId, fallbackStock);
                List<Component> lines = new java.util.ArrayList<>(baseLines);
                lines.add(Component.literal(
                        "§7" + tr("screen.resourceobserver.overview.watchlist.stock", compact(currentStock))));
                terminal.setHoveredTooltipLines(lines);
            } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
                terminal.clearHoveredTooltip();
            }
            return true; // 消费事件，防止子视图截获导致 tooltip 闪烁
        });
    }

    /** 从当前 ViewModel 动态查询物品的最新库存量 */
    private static long lookupCurrentStock(ResourceTerminalFragment terminal, String itemId, long fallback) {
        OverviewViewModel vm = terminal.getBridge().getOverviewViewModel();
        if (vm == null) return fallback;
        if (vm.tableGroups() != null) {
            for (OverviewViewModel.TableGroup g : vm.tableGroups()) {
                if (g.rows() == null) continue;
                for (OverviewViewModel.TableRow r : g.rows()) {
                    if (itemId.equals(r.itemId())) return r.stock();
                }
            }
        }
        if (vm.watchlistItems() != null) {
            for (OverviewViewModel.WatchlistItem w : vm.watchlistItems()) {
                if (itemId.equals(w.itemId())) return w.stock();
            }
        }
        return fallback;
    }

    /** 兼容旧调用：无 displayName 时传 null */
    static void attachItemHover(View view, ResourceTerminalFragment terminal,
                                String itemId, long stock) {
        attachItemHover(view, terminal, itemId, stock, null);
    }

    private static int measureTableHeight(ResourceTerminalFragment terminal, OverviewViewModel vm) {
        int rows = 0;
        for (OverviewViewModel.TableGroup group : vm.tableGroups()) {
            rows += 1 + group.rows().size(); // group header + all rows (default expanded)
        }
        return terminal.dp(50) + rows * ModernUiTheme.scaledDp(terminal, TABLE_ROW_HEIGHT_DP);
    }
}

package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.client.ui.render.ChartRenderer;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.Menu;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.PopupMenu;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class OverviewPreviewFragment extends Fragment {

    private static final int TABLE_HEADER_HEIGHT_DP = 20;
    private static final int TABLE_ROW_HEIGHT_DP = 16;
    private static final int TABLE_GROUP_HEIGHT_DP = 18;

    private static final int MENU_CLEAR_GROUP = 1;
    private static final int MENU_ASSIGN_BASE = 100;
    private static final int MENU_CREATE_GROUP = 900;
    private static final int MENU_RENAME_GROUP = 901;
    private static final int MENU_DELETE_GROUP = 902;

    /** S / M / L = 屏幕占比 85% / 90% / 95% */
    private static final float[] SCALE_MODES = new float[] {0.85f, 0.90f, 0.95f};
    private static final String[] SCALE_LABELS = new String[] {"S", "M", "L"};

    private final OverviewPreviewState sourceState;
    private final List<OverviewPreviewState.KpiCard> kpis = new ArrayList<>();
    private final Map<String, MutableGroup> groups = new LinkedHashMap<>();
    private final List<MutableTableEntry> tableEntries = new ArrayList<>();
    private final Map<String, MutableWatchEntry> watchEntries = new LinkedHashMap<>();
    private final LinkedHashSet<String> watchOrder = new LinkedHashSet<>();
    private final Map<String, Boolean> groupExpanded = new HashMap<>();

    private FrameLayout stage;
    private int lastLayoutW;
    private int lastLayoutH;

    private PreviewTab activeTab = PreviewTab.OVERVIEW;
    private int scaleModeIndex = 1;
    private float manualScale = SCALE_MODES[1];

    private ChartWindow chartWindow = ChartWindow.DAY_24H_5M;
    private ChartRenderer.ChartPage chartPage = ChartRenderer.ChartPage.THROUGHPUT;
    private ChartRenderer.LineMode chartLineMode = ChartRenderer.LineMode.ALL;
    private ChartRenderer.SmoothingMode smoothingMode = ChartRenderer.SmoothingMode.SMOOTH;
    private ChartRenderer.ChartDataType chartDataType = ChartRenderer.ChartDataType.ITEMS;

    private String selectedItemId;
    private String groupFilterKey = PlayerUiPrefsSavedData.GROUP_FILTER_ALL;
    private TableStatusFilter statusFilter = TableStatusFilter.ALL;
    private TableSortMode sortMode = TableSortMode.NET;
    private boolean sortDesc = true;
    private OverviewViewModel.KpiType selectedKpiType;

    public OverviewPreviewFragment(OverviewPreviewState state) {
        this.sourceState = Objects.requireNonNull(state, "state");
        initRuntimeState(state);
    }

    /** dp → Modern UI pixels */
    private int dp(float dpVal) {
        return stage != null ? stage.dp(dpVal) : Math.round(dpVal);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable DataSet savedInstanceState
    ) {
        stage = new FrameLayout(getContext());
        stage.setBackground(makeBackground(stage, 0xAA070D1A, 0xAA070D1A, 0, 0));
        stage.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int w = right - left;
            int h = bottom - top;
            if (w > 0 && h > 0 && (w != lastLayoutW || h != lastLayoutH)) {
                lastLayoutW = w;
                lastLayoutH = h;
                rebuildUi();
            }
        });
        return stage;
    }

    private void initRuntimeState(OverviewPreviewState state) {
        kpis.clear();
        kpis.addAll(state.kpis());

        groups.clear();
        for (OverviewPreviewState.GroupOption group : state.groups()) {
            groups.put(group.key(), new MutableGroup(group.key(), group.displayName(), group.systemGroup()));
        }
        groups.putIfAbsent(
                PlayerUiPrefsSavedData.GROUP_UNGROUPED,
                new MutableGroup(PlayerUiPrefsSavedData.GROUP_UNGROUPED, "Ungrouped", true)
        );

        tableEntries.clear();
        for (OverviewPreviewState.TableEntry row : state.table()) {
            String groupKey = normalizeGroupKey(row.groupKey());
            if (!groups.containsKey(groupKey)) {
                groupKey = PlayerUiPrefsSavedData.GROUP_UNGROUPED;
            }
            tableEntries.add(new MutableTableEntry(
                    row.itemId(),
                    row.displayName(),
                    groupKey,
                    row.production(),
                    row.consumption(),
                    row.stock(),
                    row.critical(),
                    row.starred()
            ));
        }

        watchEntries.clear();
        watchOrder.clear();
        for (OverviewPreviewState.WatchEntry watch : state.watchlist()) {
            watchEntries.put(watch.itemId(), new MutableWatchEntry(
                    watch.itemId(),
                    watch.displayName(),
                    watch.netPerMinute(),
                    watch.stock()
            ));
            watchOrder.add(watch.itemId());
        }

        for (MutableTableEntry row : tableEntries) {
            if (row.starred && watchOrder.size() < PlayerUiPrefsSavedData.WATCHLIST_LIMIT) {
                watchOrder.add(row.itemId);
            }
            watchEntries.putIfAbsent(row.itemId, new MutableWatchEntry(row.itemId, row.displayName, row.net(), row.stock));
            groupExpanded.putIfAbsent(row.groupKey, true);
        }
        for (String key : groups.keySet()) {
            groupExpanded.putIfAbsent(key, true);
        }
        selectedItemId = watchOrder.isEmpty() ? null : watchOrder.iterator().next();
    }

    private void rebuildUi() {
        if (stage == null) {
            return;
        }
        stage.removeAllViews();

        int screenW = stage.getWidth();
        int screenH = stage.getHeight();
        if (screenW <= 0 || screenH <= 0) {
            return;
        }
        ResponsiveLayout layout = computeResponsiveLayout(screenW, screenH);

        int panelW = layout.panelWidth();
        int panelH = layout.panelHeight();
        int margin = layout.margin();
        int sectionGap = layout.sectionGap();
        int contentW = layout.contentWidth();

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(margin, margin, margin, margin);
        panel.setBackground(makeBackground(panel, UiThemeTokens.PANEL_BG, UiThemeTokens.PANEL_BORDER, 0, 1));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(panelW, panelH, Gravity.CENTER);
        stage.addView(panel, panelParams);

        LinearLayout chrome = new LinearLayout(getContext());
        chrome.setOrientation(LinearLayout.HORIZONTAL);
        chrome.setPadding(dp(8), dp(4), dp(8), dp(4));
        chrome.setBackground(makeBackground(chrome, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER, 0, 1));
        panel.addView(chrome, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, layout.chromeHeight()));
        buildChrome(chrome, layout.chromeHeight() - dp(6));

        if (activeTab != PreviewTab.OVERVIEW) {
            LinearLayout placeholder = section(contentW, 0, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
            placeholder.setPadding(dp(12), dp(12), dp(12), dp(12));
            placeholder.setOrientation(LinearLayout.VERTICAL);
            addSection(panel, placeholder, sectionGap, 0, 1.0f);
            addText(
                    placeholder,
                    tr("screen.resourceobserver.modernui.preview.title"),
                    UiThemeTokens.TITLE,
                    14,
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    false
            );
            String label = switch (activeTab) {
                case STORAGE_NETWORK -> tr("screen.resourceobserver.storage.tab.storage_network");
                case POWER_NETWORK -> tr("screen.resourceobserver.storage.tab.power_network");
                case OVERVIEW -> tr("screen.resourceobserver.storage.tab.overview");
            };
            addText(
                    placeholder,
                    tr("screen.resourceobserver.overview.header.subtitle") + " [" + label + "]",
                    UiThemeTokens.TEXT_MUTED,
                    11,
                    dp(8),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    false
            );
            return;
        }

        ScrollView scroll = new ScrollView(getContext());
        addSection(panel, scroll, sectionGap, 0, 1.0f);

        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new FrameLayout.LayoutParams(contentW, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout headerSection = section(contentW, layout.headerHeight(), UiThemeTokens.HEADER_BG, UiThemeTokens.SECTION_BORDER);
        buildHeaderSection(headerSection, contentW);
        addSection(content, headerSection, 0, 0, 0.0f);

        LinearLayout kpiSection = section(contentW, layout.kpiHeight(), UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        buildKpiSection(kpiSection, contentW);
        addSection(content, kpiSection, sectionGap, 0, 0.0f);

        LinearLayout chartSection = section(contentW, layout.chartHeight(), UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        buildChartSection(chartSection, contentW, layout.chartHeight());
        addSection(content, chartSection, sectionGap, 0, 0.0f);

        if (!watchOrder.isEmpty()) {
            LinearLayout watchSection = section(contentW, layout.watchlistHeight(), UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
            buildWatchlistSection(watchSection, contentW, layout.watchlistHeight());
            addSection(content, watchSection, sectionGap, 0, 0.0f);
        }

        List<DisplayGroup> displayed = buildDisplayedGroups();
        int tableHeight = measureTableHeight(displayed);
        LinearLayout tableSection = section(contentW, tableHeight, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        buildTableSection(tableSection, contentW, tableHeight, displayed);
        addSection(content, tableSection, sectionGap, 0, 0.0f);
    }

    private ResponsiveLayout computeResponsiveLayout(int screenW, int screenH) {
        // 面板 = 屏幕 × manualScale（默认90%，即两侧各预留5%）
        int panelW = Math.round(screenW * manualScale);
        int panelH = Math.round(screenH * manualScale);

        int margin = dp(10);
        int sectionGap = dp(6);
        int chromeH = dp(28);
        int scrollbarW = dp(8);

        int contentW = Math.max(dp(160), panelW - margin * 2 - scrollbarW);
        int scrollH = Math.max(dp(180), panelH - margin * 2 - chromeH - sectionGap);

        int headerH = clampInt(Math.round(scrollH * 0.12f), dp(44), dp(90));
        int kpiH = clampInt(Math.round(scrollH * 0.18f), dp(64), dp(130));
        int chartH = clampInt(Math.round(scrollH * 0.28f), dp(100), dp(220));
        int watchH = clampInt(Math.round(scrollH * 0.18f), dp(64), dp(130));

        return new ResponsiveLayout(panelW, panelH, margin, sectionGap, chromeH, scrollbarW, contentW, headerH, kpiH, chartH, watchH);
    }

    private void buildChrome(LinearLayout chrome, int tabHeight) {
        chrome.addView(tabButton(chrome, tr("screen.resourceobserver.storage.tab.overview"), activeTab == PreviewTab.OVERVIEW, PreviewTab.OVERVIEW, tabHeight));
        chrome.addView(tabButton(chrome, tr("screen.resourceobserver.storage.tab.storage_network"), activeTab == PreviewTab.STORAGE_NETWORK, PreviewTab.STORAGE_NETWORK, tabHeight), leftGap(dp(4)));
        chrome.addView(tabButton(chrome, tr("screen.resourceobserver.storage.tab.power_network"), activeTab == PreviewTab.POWER_NETWORK, PreviewTab.POWER_NETWORK, tabHeight), leftGap(dp(4)));

        View spacer = new View(chrome.getContext());
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1);
        spacerParams.weight = 1.0f;
        chrome.addView(spacer, spacerParams);

        TextView sizeBtn = tinyButton(chrome, SCALE_LABELS[scaleModeIndex]);
        sizeBtn.setOnClickListener(v -> {
            scaleModeIndex = (scaleModeIndex + 1) % SCALE_MODES.length;
            manualScale = SCALE_MODES[scaleModeIndex];
            rebuildUi();
        });
        chrome.addView(sizeBtn);

        TextView closeBtn = tinyButton(chrome, "X");
        closeBtn.setOnClickListener(v -> Minecraft.getInstance().setScreen(null));
        chrome.addView(closeBtn, leftGap(dp(6)));
    }

    private TextView tabButton(LinearLayout parent, String text, boolean active, PreviewTab tab, int tabHeight) {
        TextView button = new TextView(parent.getContext());
        button.setText(text);
        button.setTextSize(10);
        button.setIncludeFontPadding(false);
        button.setSingleLine();
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(active ? UiThemeTokens.CYAN : UiThemeTokens.TEXT);
        button.setPadding(dp(8), dp(3), dp(8), dp(3));
        button.setBackground(makeBackground(button, active ? UiThemeTokens.TAB_ACTIVE : UiThemeTokens.TAB_INACTIVE, UiThemeTokens.DIVIDER, 0, 1));
        button.setOnClickListener(v -> {
            activeTab = tab;
            rebuildUi();
        });
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, tabHeight));
        return button;
    }

    private TextView tinyButton(LinearLayout parent, String label) {
        TextView button = new TextView(parent.getContext());
        button.setText(label);
        button.setTextSize(10);
        button.setIncludeFontPadding(false);
        button.setSingleLine();
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextColor(UiThemeTokens.TEXT);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), dp(3), dp(8), dp(3));
        button.setBackground(makeBackground(button, UiThemeTokens.TAB_INACTIVE, UiThemeTokens.DIVIDER, 0, 1));
        return button;
    }

    private void buildHeaderSection(LinearLayout section, int sectionWidth) {
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout left = new LinearLayout(section.getContext());
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        leftParams.weight = 1.0f;
        section.addView(left, leftParams);

        addText(left, tr("screen.resourceobserver.overview.header.title"), UiThemeTokens.TITLE, 14, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        addText(left, tr("screen.resourceobserver.overview.header.subtitle"), UiThemeTokens.TEXT_MUTED, 10, dp(2), ViewGroup.LayoutParams.WRAP_CONTENT, false);

        int cardW = clampInt(sectionWidth / 3, dp(140), dp(280));

        LinearLayout statusCard = new LinearLayout(section.getContext());
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setPadding(dp(8), dp(4), dp(8), dp(4));
        int statusBorder = sourceState.linked() ? UiThemeTokens.CYAN : UiThemeTokens.ROSE;
        statusCard.setBackground(makeBackground(statusCard, 0xE0182A41, statusBorder, 0, 1));
        section.addView(statusCard, new LinearLayout.LayoutParams(cardW, dp(36)));

        View dot = new View(section.getContext());
        dot.setBackground(makeBackground(dot, sourceState.linked() ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE, 0, 0, 0));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotParams.topMargin = dp(4);
        dotParams.rightMargin = dp(4);
        statusCard.addView(dot, dotParams);

        LinearLayout textColumn = new LinearLayout(section.getContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        statusCard.addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        String statusText = sourceState.linked() ? tr("screen.resourceobserver.overview.link_status") : tr("screen.resourceobserver.overview.link_status_unbound");
        String coords = tr("screen.resourceobserver.overview.header.coords", sourceState.observerX(), sourceState.observerY(), sourceState.observerZ());
        addText(textColumn, statusText, sourceState.linked() ? UiThemeTokens.CYAN : UiThemeTokens.ROSE, 9, 0, ViewGroup.LayoutParams.MATCH_PARENT, true);
        addText(textColumn, coords, UiThemeTokens.TEXT_MUTED, 9, 0, ViewGroup.LayoutParams.MATCH_PARENT, true);
    }

    private void buildKpiSection(LinearLayout section, int sectionWidth) {
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setPadding(dp(8), dp(8), dp(8), dp(8));

        int gap = dp(8);
        int cardCount = Math.max(1, kpis.size());
        int innerW = sectionWidth - dp(16);
        int cardW = Math.max(dp(100), (innerW - (cardCount - 1) * gap) / cardCount);
        int cardH = Math.max(dp(50), section.getLayoutParams().height - dp(16));

        for (int i = 0; i < cardCount; i++) {
            OverviewPreviewState.KpiCard card = kpis.get(i);
            LinearLayout cardView = new LinearLayout(section.getContext());
            cardView.setOrientation(LinearLayout.VERTICAL);
            cardView.setPadding(dp(8), dp(6), dp(8), dp(6));

            boolean selected = selectedKpiType == card.type();
            int cardBg = selected ? 0xEE24466C : UiThemeTokens.CARD_BG;
            int cardBorder = selected ? UiThemeTokens.CYAN : UiThemeTokens.CARD_BORDER;
            cardView.setBackground(makeBackground(cardView, cardBg, cardBorder, 0, 1));
            cardView.setOnClickListener(v -> {
                selectedKpiType = selectedKpiType == card.type() ? null : card.type();
                rebuildUi();
            });

            addText(cardView, tr(card.labelKey()), UiThemeTokens.TEXT_MUTED, 9, 0, ViewGroup.LayoutParams.MATCH_PARENT, true);
            addText(cardView, card.value(), UiThemeTokens.TITLE, 14, dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);
            addText(cardView, card.trend(), statusColor(card.status()), 9, dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);

            View iconBox = new View(section.getContext());
            iconBox.setBackground(makeBackground(iconBox, 0x4016253C, 0x4016253C, 0, 0));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(16), dp(16));
            iconParams.topMargin = dp(4);
            cardView.addView(iconBox, iconParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardW, cardH);
            if (i > 0) {
                params.leftMargin = gap;
            }
            section.addView(cardView, params);
        }
    }

    private void buildChartSection(LinearLayout section, int sectionWidth, int sectionHeight) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout titleRow = new LinearLayout(section.getContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        addText(titleRow, tr("screen.resourceobserver.overview.section.chart"), UiThemeTokens.TEXT, 12, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);

        View spacer = new View(section.getContext());
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1);
        spacerParams.weight = 1.0f;
        titleRow.addView(spacer, spacerParams);

        LinearLayout buttons = new LinearLayout(section.getContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);

        TextView windowBtn = chartButton(buttons, tr(chartWindow.translationKey()), true);
        windowBtn.setOnClickListener(v -> {
            chartWindow = chartWindow.next();
            rebuildUi();
        });
        buttons.addView(windowBtn);

        TextView dataBtn = chartButton(buttons,
                chartDataType == ChartRenderer.ChartDataType.ENERGY
                        ? tr("screen.resourceobserver.overview.chart.data.energy")
                        : tr("screen.resourceobserver.overview.chart.data.items"),
                true);
        dataBtn.setOnClickListener(v -> {
            chartDataType = chartDataType.next();
            rebuildUi();
        });
        buttons.addView(dataBtn, leftGap(dp(4)));

        TextView pageBtn = chartButton(buttons,
                chartPage == ChartRenderer.ChartPage.THROUGHPUT
                        ? tr("screen.resourceobserver.overview.chart.tab.throughput")
                        : tr("screen.resourceobserver.overview.chart.tab.stock"),
                true);
        pageBtn.setOnClickListener(v -> {
            chartPage = chartPage.next();
            rebuildUi();
        });
        buttons.addView(pageBtn, leftGap(dp(4)));

        boolean lineModeEnabled = chartPage == ChartRenderer.ChartPage.THROUGHPUT;
        TextView modeBtn = chartButton(buttons, lineModeLabel(chartLineMode), lineModeEnabled);
        modeBtn.setOnClickListener(v -> {
            if (chartPage == ChartRenderer.ChartPage.THROUGHPUT) {
                chartLineMode = chartLineMode.next();
                rebuildUi();
            }
        });
        buttons.addView(modeBtn, leftGap(dp(4)));

        TextView smoothBtn = chartButton(buttons,
                smoothingMode == ChartRenderer.SmoothingMode.SMOOTH
                        ? tr("screen.resourceobserver.overview.chart.smoothing.smooth")
                        : tr("screen.resourceobserver.overview.chart.smoothing.raw"),
                true);
        smoothBtn.setOnClickListener(v -> {
            smoothingMode = smoothingMode.next();
            rebuildUi();
        });
        buttons.addView(smoothBtn, leftGap(dp(4)));

        titleRow.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        section.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String scopeLabel = selectedItemDisplayName();
        String subtitle = chartPage == ChartRenderer.ChartPage.STOCK
                ? tr("screen.resourceobserver.overview.chart.subtitle.stock", scopeLabel, chartWindow.shortLabel())
                : tr("screen.resourceobserver.overview.chart.subtitle.throughput", scopeLabel, chartWindow.shortLabel());
        addText(section, subtitle, UiThemeTokens.TEXT_MUTED, 10, dp(2), ViewGroup.LayoutParams.WRAP_CONTENT, false);

        int plotHeight = Math.max(dp(40), sectionHeight - dp(50));
        int plotWidth = sectionWidth - dp(16);
        FrameLayout plot = new FrameLayout(section.getContext());
        plot.setBackground(makeBackground(plot, 0x5A0D1628, 0x773A5478, 0, 1));
        LinearLayout.LayoutParams plotParams = new LinearLayout.LayoutParams(plotWidth, plotHeight);
        plotParams.topMargin = dp(4);
        section.addView(plot, plotParams);
        buildChartGrid(plot, plotWidth, plotHeight);
        buildChartPoints(plot, plotWidth, plotHeight);

        LinearLayout legend = new LinearLayout(section.getContext());
        legend.setOrientation(LinearLayout.HORIZONTAL);
        addText(legend, tr("screen.resourceobserver.overview.chart.legend.production"), UiThemeTokens.CYAN, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        TextView c = addText(legend, tr("screen.resourceobserver.overview.chart.legend.consumption"), UiThemeTokens.AMBER, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        TextView n = addText(legend, tr("screen.resourceobserver.overview.chart.legend.net"), UiThemeTokens.EMERALD, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        ((LinearLayout.LayoutParams) c.getLayoutParams()).leftMargin = dp(10);
        ((LinearLayout.LayoutParams) n.getLayoutParams()).leftMargin = dp(10);
        LinearLayout.LayoutParams legendParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        legendParams.topMargin = dp(3);
        section.addView(legend, legendParams);
    }

    private void buildChartGrid(FrameLayout plot, int width, int height) {
        int hs = Math.max(8, height / 5);
        int vs = Math.max(12, width / 10);

        for (int y = hs; y < height - 1; y += hs) {
            View line = new View(plot.getContext());
            line.setBackground(makeBackground(line, 0x223E5C84, 0x223E5C84, 0, 0));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width - 2, 1);
            params.leftMargin = 1;
            params.topMargin = y;
            plot.addView(line, params);
        }
        for (int x = vs; x < width - 1; x += vs) {
            View line = new View(plot.getContext());
            line.setBackground(makeBackground(line, 0x152F4668, 0x152F4668, 0, 0));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(1, height - 2);
            params.leftMargin = x;
            params.topMargin = 1;
            plot.addView(line, params);
        }
    }

    private void buildChartPoints(FrameLayout plot, int width, int height) {
        int points = 28;
        long productionSeed = 31L;
        long consumptionSeed = 57L;
        long netSeed = 91L;
        for (int i = 0; i < points; i++) {
            float t = i / (float) (points - 1);
            int x = Math.min(width - 3, Math.max(2, Math.round(3 + t * (width - 7))));
            int yProd = Math.round(height * (0.62f - 0.18f * wave(i, productionSeed)));
            int yCons = Math.round(height * (0.66f - 0.16f * wave(i, consumptionSeed)));
            int yNet = Math.round(height * (0.58f - 0.20f * wave(i, netSeed)));
            addPoint(plot, x, yProd, UiThemeTokens.CYAN);
            if (chartPage == ChartRenderer.ChartPage.THROUGHPUT) {
                addPoint(plot, x, yCons, UiThemeTokens.AMBER);
                addPoint(plot, x, yNet, UiThemeTokens.EMERALD);
            } else {
                addPoint(plot, x, Math.round(height * (0.55f - 0.22f * wave(i, 121L))), UiThemeTokens.BLUE);
            }
        }
    }

    private void addPoint(FrameLayout plot, int x, int y, int color) {
        View dot = new View(plot.getContext());
        dot.setBackground(makeBackground(dot, color, color, 0, 0));
        int dotSize = Math.max(2, dp(2));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dotSize, dotSize);
        params.leftMargin = x;
        params.topMargin = Math.max(2, y);
        plot.addView(dot, params);
    }

    private float wave(int i, long seed) {
        long v = (i * 1103515245L + seed) & 0x7fffffffL;
        return (v % 2000) / 1000.0f - 1.0f;
    }

    private void buildWatchlistSection(LinearLayout section, int sectionWidth, int sectionHeight) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(8), dp(8), dp(8), dp(8));
        addText(section, tr("screen.resourceobserver.overview.section.watchlist"), UiThemeTokens.TEXT, 12, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);

        int listHeight = Math.max(dp(20), sectionHeight - dp(30));
        int maxColumns = sectionWidth >= dp(500) ? 3 : (sectionWidth >= dp(340) ? 2 : 1);
        int count = Math.max(1, Math.min(maxColumns, watchOrder.size()));
        int gap = dp(8);
        int listWidth = sectionWidth - dp(16);
        int cardW = (listWidth - (count - 1) * gap) / count;

        LinearLayout row = new LinearLayout(section.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(listWidth, listHeight);
        rowParams.topMargin = dp(4);
        section.addView(row, rowParams);

        List<String> ids = new ArrayList<>(watchOrder);
        for (int i = 0; i < count && i < ids.size(); i++) {
            MutableWatchEntry entry = watchEntries.get(ids.get(i));
            if (entry == null) {
                continue;
            }
            LinearLayout card = new LinearLayout(section.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(8), dp(6), dp(8), dp(6));

            boolean selected = entry.itemId.equals(selectedItemId);
            int bg = selected ? 0xCC142338 : UiThemeTokens.CARD_BG;
            int border = selected ? UiThemeTokens.CYAN : UiThemeTokens.CARD_BORDER;
            card.setBackground(makeBackground(card, bg, border, 0, 1));
            card.setOnClickListener(v -> {
                selectedItemId = entry.itemId;
                rebuildUi();
            });

            LinearLayout titleRow = new LinearLayout(section.getContext());
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            card.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView name = addText(titleRow, entry.displayName, UiThemeTokens.TEXT, 10, 0, 0, true);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            name.setLayoutParams(nameParams);

            TextView remove = addText(titleRow, "x", UiThemeTokens.ROSE, 10, 0, dp(16), false);
            remove.setGravity(Gravity.CENTER);
            remove.setBackground(makeBackground(remove, 0x552A1018, 0xAA7F1D28, 0, 1));
            remove.setOnClickListener(v -> {
                watchOrder.remove(entry.itemId);
                MutableTableEntry rowEntry = rowById(entry.itemId);
                if (rowEntry != null) {
                    rowEntry.starred = false;
                }
                if (Objects.equals(selectedItemId, entry.itemId)) {
                    selectedItemId = watchOrder.isEmpty() ? null : watchOrder.iterator().next();
                }
                rebuildUi();
            });

            int netColor = entry.netPerMinute >= 0 ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE;
            String netText = (entry.netPerMinute >= 0 ? "+" : "") + entry.netPerMinute + "/min";
            addText(card, tr("screen.resourceobserver.overview.watchlist.net", netText), netColor, 9, dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);
            addText(card, tr("screen.resourceobserver.overview.watchlist.stock", compact(entry.stock)), UiThemeTokens.TEXT_MUTED, 9, dp(2), ViewGroup.LayoutParams.MATCH_PARENT, true);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardW, listHeight);
            if (i > 0) {
                params.leftMargin = gap;
            }
            row.addView(card, params);
        }
    }

    private void buildTableSection(LinearLayout section, int sectionWidth, int sectionHeight, List<DisplayGroup> displayedGroups) {
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(8), dp(8), dp(8), dp(8));

        int rowH = dp(TABLE_ROW_HEIGHT_DP);
        int headerH = dp(TABLE_HEADER_HEIGHT_DP);
        int groupH = dp(TABLE_GROUP_HEIGHT_DP);

        LinearLayout titleRow = new LinearLayout(section.getContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        addText(titleRow, tr("screen.resourceobserver.overview.section.table"), UiThemeTokens.TEXT, 12, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        TextView countLabel = addText(titleRow, tr("screen.resourceobserver.overview.table.items_active", totalDisplayedRows(displayedGroups)), UiThemeTokens.TEXT_MUTED, 9, 0, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        ((LinearLayout.LayoutParams) countLabel.getLayoutParams()).leftMargin = dp(8);

        View spacer = new View(section.getContext());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 1);
        sp.weight = 1.0f;
        titleRow.addView(spacer, sp);

        TextView groupBtn = filterButton(titleRow, tr("screen.resourceobserver.overview.filter.group.button") + ": " + groupFilterLabel());
        groupBtn.setOnClickListener(v -> openGroupFilterMenu(groupBtn));
        titleRow.addView(groupBtn);

        TextView statusBtn = filterButton(titleRow, tr("screen.resourceobserver.overview.filter.status.button") + ": " + tr(statusFilter.translationKey()));
        statusBtn.setOnClickListener(v -> openStatusFilterMenu(statusBtn));
        titleRow.addView(statusBtn, leftGap(dp(4)));

        TextView resetBtn = filterButton(titleRow, tr("screen.resourceobserver.overview.filter.reset"));
        resetBtn.setOnClickListener(v -> {
            groupFilterKey = PlayerUiPrefsSavedData.GROUP_FILTER_ALL;
            statusFilter = TableStatusFilter.ALL;
            sortMode = TableSortMode.NET;
            sortDesc = true;
            rebuildUi();
        });
        titleRow.addView(resetBtn, leftGap(dp(4)));

        section.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int tableHeight = Math.max(dp(24), sectionHeight - dp(46));
        int tableInnerW = sectionWidth - dp(16);
        LinearLayout table = new LinearLayout(section.getContext());
        table.setOrientation(LinearLayout.VERTICAL);
        table.setBackground(makeBackground(table, 0x5510182C, UiThemeTokens.DIVIDER, 0, 1));
        LinearLayout.LayoutParams tableParams = new LinearLayout.LayoutParams(tableInnerW, tableHeight);
        tableParams.topMargin = dp(4);
        section.addView(table, tableParams);

        int starW = dp(16);
        int colProdX = (int) (tableInnerW * 0.40f);
        int colConsX = (int) (tableInnerW * 0.56f);
        int colNetX = (int) (tableInnerW * 0.70f);
        int colInvX = (int) (tableInnerW * 0.83f);

        int nameW = Math.max(dp(24), colProdX - starW - dp(6));
        int prodW = Math.max(dp(24), colConsX - colProdX - dp(2));
        int consW = Math.max(dp(24), colNetX - colConsX - dp(2));
        int netW = Math.max(dp(24), colInvX - colNetX - dp(2));
        int invW = Math.max(dp(24), tableInnerW - colInvX - dp(6));

        LinearLayout header = new LinearLayout(table.getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(4), dp(4), dp(4), dp(2));
        addText(header, tr("screen.resourceobserver.overview.table.col_node"), UiThemeTokens.TEXT_MUTED, 9, 0, nameW + starW, true);
        header.addView(sortHeader(tr("screen.resourceobserver.overview.table.col_production"), TableSortMode.PRODUCTION, prodW));
        header.addView(sortHeader(tr("screen.resourceobserver.overview.table.col_consumption"), TableSortMode.CONSUMPTION, consW));
        header.addView(sortHeader(tr("screen.resourceobserver.overview.table.col_net"), TableSortMode.NET, netW));
        header.addView(sortHeader(tr("screen.resourceobserver.overview.table.col_inventory"), TableSortMode.STOCK, invW));
        table.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, headerH));

        View divider = new View(table.getContext());
        divider.setBackground(makeBackground(divider, UiThemeTokens.DIVIDER, UiThemeTokens.DIVIDER, 0, 0));
        table.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        for (DisplayGroup group : displayedGroups) {
            LinearLayout groupRow = new LinearLayout(table.getContext());
            groupRow.setOrientation(LinearLayout.HORIZONTAL);
            groupRow.setPadding(dp(6), dp(2), dp(6), dp(2));
            groupRow.setBackground(makeBackground(groupRow, 0x33202A40, 0x33202A40, 0, 0));

            boolean expanded = groupExpanded.getOrDefault(group.key, true);
            addText(groupRow, expanded ? "[-]" : "[+]", UiThemeTokens.CYAN, 9, 0, dp(22), false);
            addText(groupRow, group.title, UiThemeTokens.CYAN, 9, 0, ViewGroup.LayoutParams.MATCH_PARENT, true);
            groupRow.setOnClickListener(v -> {
                groupExpanded.put(group.key, !expanded);
                rebuildUi();
            });
            table.addView(groupRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, groupH));

            if (!expanded) {
                continue;
            }

            for (MutableTableEntry row : group.rows) {
                LinearLayout line = new LinearLayout(table.getContext());
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setPadding(dp(4), dp(2), dp(4), dp(2));
                if (Objects.equals(selectedItemId, row.itemId)) {
                    line.setBackground(makeBackground(line, 0x3322D3EE, 0x3322D3EE, 0, 0));
                }

                TextView star = addText(line, row.starred ? "★" : "☆", UiThemeTokens.TEXT, 9, 0, starW, false);
                star.setGravity(Gravity.CENTER);
                star.setOnClickListener(v -> {
                    row.starred = !row.starred;
                    if (row.starred) {
                        if (watchOrder.size() < PlayerUiPrefsSavedData.WATCHLIST_LIMIT) {
                            watchOrder.add(row.itemId);
                        } else {
                            row.starred = false;
                        }
                    } else {
                        watchOrder.remove(row.itemId);
                    }
                    rebuildUi();
                });

                addText(line, row.displayName, UiThemeTokens.TEXT, 9, 0, nameW, true);
                addText(line, Long.toString(row.production), UiThemeTokens.CYAN, 9, 0, prodW, true);
                addText(line, Long.toString(row.consumption), UiThemeTokens.AMBER, 9, 0, consW, true);
                String netText = (row.net() >= 0 ? "+" : "") + row.net() + "/m";
                if (row.critical) {
                    netText = "!" + netText;
                }
                int netColor = row.net() >= 0 ? UiThemeTokens.EMERALD : UiThemeTokens.ROSE;
                addText(line, netText, netColor, 9, 0, netW, true);
                addText(line, compact(row.stock), UiThemeTokens.TEXT_MUTED, 9, 0, invW, true);

                line.setOnClickListener(v -> {
                    selectedItemId = row.itemId;
                    rebuildUi();
                });
                line.setOnContextClickListener(v -> {
                    openRowMenu(line, row);
                    return true;
                });
                line.setOnLongClickListener(v -> {
                    openRowMenu(line, row);
                    return true;
                });

                table.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH));
            }
        }
    }

    private TextView sortHeader(String label, TableSortMode mode, int width) {
        boolean active = displayedSortMode(sortMode) == mode;
        String suffix = active ? (sortDesc ? " ↓" : " ↑") : "";
        int color = switch (mode) {
            case PRODUCTION -> active ? UiThemeTokens.CYAN : UiThemeTokens.TEXT_MUTED;
            case CONSUMPTION -> active ? UiThemeTokens.AMBER : UiThemeTokens.TEXT_MUTED;
            case NET -> active ? UiThemeTokens.EMERALD : UiThemeTokens.TEXT_MUTED;
            case STOCK, NET_ABS -> active ? UiThemeTokens.CYAN : UiThemeTokens.TEXT_MUTED;
        };
        TextView tv = textView(label + suffix, color, 9, width, true);
        tv.setOnClickListener(v -> {
            if (displayedSortMode(sortMode) == mode) {
                sortDesc = !sortDesc;
            } else {
                sortMode = mode;
                sortDesc = true;
            }
            rebuildUi();
        });
        return tv;
    }

    private void openGroupFilterMenu(View anchor) {
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        Map<Integer, String> keysById = new HashMap<>();
        int id = 1;
        popup.getMenu().add(Menu.NONE, id, id, selectedMarker(PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(groupFilterKey), tr("screen.resourceobserver.overview.filter.group.all")));
        keysById.put(id, PlayerUiPrefsSavedData.GROUP_FILTER_ALL);
        id++;

        for (MutableGroup group : groups.values()) {
            popup.getMenu().add(Menu.NONE, id, id, selectedMarker(Objects.equals(groupFilterKey, group.key), groupDisplayName(group)));
            keysById.put(id, group.key);
            id++;
        }

        popup.setOnMenuItemClickListener(item -> {
            String next = keysById.get(item.getItemId());
            if (next != null) {
                groupFilterKey = next;
                rebuildUi();
            }
            return true;
        });
        popup.show();
    }

    private void openStatusFilterMenu(View anchor) {
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        Map<Integer, TableStatusFilter> filtersById = new HashMap<>();
        int id = 1;
        for (TableStatusFilter filter : TableStatusFilter.values()) {
            popup.getMenu().add(Menu.NONE, id, id, selectedMarker(filter == statusFilter, tr(filter.translationKey())));
            filtersById.put(id, filter);
            id++;
        }
        popup.setOnMenuItemClickListener(item -> {
            TableStatusFilter next = filtersById.get(item.getItemId());
            if (next != null) {
                statusFilter = next;
                rebuildUi();
            }
            return true;
        });
        popup.show();
    }

    private void openRowMenu(View anchor, MutableTableEntry row) {
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        Map<Integer, String> assignTargets = new HashMap<>();

        popup.getMenu().add(Menu.NONE, MENU_CLEAR_GROUP, 0, tr("screen.resourceobserver.overview.group.menu.clear"));

        int order = 10;
        int id = MENU_ASSIGN_BASE;
        for (MutableGroup group : groups.values()) {
            popup.getMenu().add(Menu.NONE, id, order, selectedMarker(Objects.equals(row.groupKey, group.key), tr("screen.resourceobserver.overview.group.menu.assign", groupDisplayName(group))));
            assignTargets.put(id, group.key);
            id++;
            order++;
        }

        popup.getMenu().add(Menu.NONE, MENU_CREATE_GROUP, 1000, tr("screen.resourceobserver.overview.group.menu.create"));

        MutableGroup current = groups.getOrDefault(row.groupKey, groups.get(PlayerUiPrefsSavedData.GROUP_UNGROUPED));
        if (current != null && !current.systemGroup) {
            popup.getMenu().add(Menu.NONE, MENU_RENAME_GROUP, 1010, tr("screen.resourceobserver.overview.group.menu.rename"));
            popup.getMenu().add(Menu.NONE, MENU_DELETE_GROUP, 1020, tr("screen.resourceobserver.overview.group.menu.delete"));
        }

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == MENU_CLEAR_GROUP) {
                row.groupKey = PlayerUiPrefsSavedData.GROUP_UNGROUPED;
            } else if (itemId == MENU_CREATE_GROUP) {
                String created = createGroupForRow();
                row.groupKey = created;
            } else if (itemId == MENU_RENAME_GROUP) {
                renameGroup(row.groupKey);
            } else if (itemId == MENU_DELETE_GROUP) {
                deleteGroup(row.groupKey);
                row.groupKey = PlayerUiPrefsSavedData.GROUP_UNGROUPED;
            } else {
                String target = assignTargets.get(itemId);
                if (target != null) {
                    row.groupKey = target;
                }
            }
            rebuildUi();
            return true;
        });
        popup.show();
    }

    private String createGroupForRow() {
        int index = 1;
        String key = "custom_" + index;
        while (groups.containsKey(key)) {
            index++;
            key = "custom_" + index;
        }
        groups.put(key, new MutableGroup(key, "Custom " + index, false));
        groupExpanded.put(key, true);
        return key;
    }

    private void renameGroup(String groupKey) {
        MutableGroup group = groups.get(groupKey);
        if (group == null || group.systemGroup) {
            return;
        }
        if (!group.displayName.endsWith(" *")) {
            group.displayName = group.displayName + " *";
        }
    }

    private void deleteGroup(String groupKey) {
        MutableGroup group = groups.get(groupKey);
        if (group == null || group.systemGroup) {
            return;
        }
        groups.remove(groupKey);
        groupExpanded.remove(groupKey);
        for (MutableTableEntry row : tableEntries) {
            if (Objects.equals(row.groupKey, groupKey)) {
                row.groupKey = PlayerUiPrefsSavedData.GROUP_UNGROUPED;
            }
        }
        if (Objects.equals(groupFilterKey, groupKey)) {
            groupFilterKey = PlayerUiPrefsSavedData.GROUP_FILTER_ALL;
        }
    }

    private List<DisplayGroup> buildDisplayedGroups() {
        Map<String, DisplayGroup> displayed = new LinkedHashMap<>();
        for (MutableGroup group : groups.values()) {
            displayed.put(group.key, new DisplayGroup(group.key, groupDisplayName(group), new ArrayList<>()));
        }

        for (MutableTableEntry row : tableEntries) {
            if (!matchesGroupFilter(row.groupKey)) {
                continue;
            }
            if (!matchesStatusFilter(row)) {
                continue;
            }
            DisplayGroup group = displayed.get(row.groupKey);
            if (group == null) {
                group = new DisplayGroup(row.groupKey, row.groupKey, new ArrayList<>());
                displayed.put(row.groupKey, group);
            }
            group.rows.add(row);
        }

        Comparator<MutableTableEntry> comparator = switch (sortMode) {
            case PRODUCTION -> Comparator.comparingLong(entry -> entry.production);
            case CONSUMPTION -> Comparator.comparingLong(entry -> entry.consumption);
            case STOCK -> Comparator.comparingLong(entry -> entry.stock);
            case NET -> Comparator.comparingLong(MutableTableEntry::net);
            case NET_ABS -> Comparator.comparingLong(entry -> Math.abs(entry.net()));
        };
        if (sortDesc) {
            comparator = comparator.reversed();
        }

        List<DisplayGroup> result = new ArrayList<>();
        for (DisplayGroup group : displayed.values()) {
            if (group.rows.isEmpty()) {
                if (!PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(groupFilterKey) && Objects.equals(groupFilterKey, group.key)) {
                    result.add(group);
                }
                continue;
            }
            group.rows.sort(comparator);
            result.add(group);
        }
        return result;
    }

    private int measureTableHeight(List<DisplayGroup> groupsToRender) {
        int rows = 0;
        for (DisplayGroup group : groupsToRender) {
            rows += 1;
            if (groupExpanded.getOrDefault(group.key, true)) {
                rows += group.rows.size();
            }
        }
        return dp(50) + rows * dp(TABLE_ROW_HEIGHT_DP);
    }

    private boolean matchesGroupFilter(String groupKey) {
        return PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(groupFilterKey) || Objects.equals(groupFilterKey, groupKey);
    }

    private boolean matchesStatusFilter(MutableTableEntry row) {
        return switch (statusFilter) {
            case ALL -> true;
            case SURPLUS -> row.net() > 0;
            case DEFICIT -> row.net() < 0;
            case CRITICAL -> row.critical || row.net() < 0 && row.stock < 2000;
        };
    }

    private int totalDisplayedRows(List<DisplayGroup> displayed) {
        int total = 0;
        for (DisplayGroup group : displayed) {
            total += group.rows.size();
        }
        return total;
    }

    private String groupFilterLabel() {
        if (PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(groupFilterKey)) {
            return tr("screen.resourceobserver.overview.filter.group.all");
        }
        MutableGroup group = groups.get(groupFilterKey);
        return group == null ? groupFilterKey : groupDisplayName(group);
    }

    private String groupDisplayName(MutableGroup group) {
        return switch (group.key) {
            case "raw" -> tr("screen.resourceobserver.overview.group.raw");
            case "intermediate" -> tr("screen.resourceobserver.overview.group.intermediate");
            case "finished" -> tr("screen.resourceobserver.overview.group.finished");
            case "common_parts" -> tr("screen.resourceobserver.overview.group.common_parts");
            case "ungrouped" -> tr("screen.resourceobserver.overview.group.ungrouped");
            default -> group.displayName;
        };
    }

    private MutableTableEntry rowById(String itemId) {
        for (MutableTableEntry row : tableEntries) {
            if (Objects.equals(row.itemId, itemId)) {
                return row;
            }
        }
        return null;
    }

    private String selectedItemDisplayName() {
        if (selectedItemId == null || selectedItemId.isBlank()) {
            return tr("screen.resourceobserver.overview.chart.scope.global");
        }
        MutableWatchEntry watch = watchEntries.get(selectedItemId);
        if (watch != null) {
            return watch.displayName;
        }
        MutableTableEntry row = rowById(selectedItemId);
        if (row != null) {
            return row.displayName;
        }
        return selectedItemId;
    }

    private String selectedMarker(boolean selected, String text) {
        return (selected ? "• " : "") + text;
    }

    private int statusColor(OverviewViewModel.Status status) {
        return switch (status) {
            case POSITIVE -> UiThemeTokens.EMERALD;
            case WARNING -> UiThemeTokens.AMBER;
            case NEGATIVE -> UiThemeTokens.ROSE;
            case NEUTRAL -> UiThemeTokens.TEXT;
        };
    }

    private String lineModeLabel(ChartRenderer.LineMode mode) {
        return switch (mode) {
            case ALL -> tr("screen.resourceobserver.overview.chart.mode.all");
            case PRODUCTION -> tr("screen.resourceobserver.overview.chart.mode.production");
            case CONSUMPTION -> tr("screen.resourceobserver.overview.chart.mode.consumption");
            case NET -> tr("screen.resourceobserver.overview.chart.mode.net");
        };
    }

    private TableSortMode displayedSortMode(TableSortMode mode) {
        return mode == TableSortMode.NET_ABS ? TableSortMode.NET : mode;
    }

    private String compact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }

    private String normalizeGroupKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return PlayerUiPrefsSavedData.GROUP_UNGROUPED;
        }
        return raw;
    }

    private LinearLayout section(int width, int height, int fill, int border) {
        LinearLayout section = new LinearLayout(getContext());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackground(makeBackground(section, fill, border, 0, 1));
        section.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return section;
    }

    private void addSection(ViewGroup parent, View child, int topMargin, int height, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height == 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : height
        );
        if (weight > 0.0f) {
            params.height = 0;
            params.weight = weight;
        }
        params.topMargin = topMargin;
        parent.addView(child, params);
    }

    private TextView chartButton(LinearLayout parent, String text, boolean enabled) {
        TextView btn = textView(text, enabled ? UiThemeTokens.TEXT : UiThemeTokens.TEXT_MUTED, 9, 0, true);
        btn.setPadding(dp(6), dp(3), dp(6), dp(3));
        btn.setBackground(makeBackground(btn, UiThemeTokens.TAB_INACTIVE, UiThemeTokens.DIVIDER, 0, 1));
        btn.setEnabled(enabled);
        return btn;
    }

    private TextView filterButton(LinearLayout parent, String text) {
        TextView btn = textView(text, UiThemeTokens.TEXT_MUTED, 9, 0, true);
        btn.setPadding(dp(6), dp(3), dp(6), dp(3));
        btn.setBackground(makeBackground(btn, UiThemeTokens.TAB_INACTIVE, UiThemeTokens.DIVIDER, 0, 1));
        return btn;
    }

    private LinearLayout.LayoutParams leftGap(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = margin;
        return params;
    }

    private TextView addText(LinearLayout parent, String text, int color, int sp, int topMargin, int width, boolean singleLine) {
        TextView tv = textView(text, color, sp, width, singleLine);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width <= 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        parent.addView(tv, lp);
        return tv;
    }

    private TextView textView(String text, int color, int sp, int width, boolean singleLine) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(sp);
        tv.setIncludeFontPadding(false);
        tv.setMaxLines(singleLine ? 1 : Integer.MAX_VALUE);
        if (singleLine) {
            tv.setSingleLine();
            tv.setEllipsize(TextUtils.TruncateAt.END);
        }
        if (width > 0) {
            tv.setMaxWidth(width);
        }
        return tv;
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ShapeDrawable makeBackground(View view, int fillColor, int borderColor, int radiusDp, int borderWidthDp) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(fillColor);
        if (radiusDp > 0) {
            drawable.setCornerRadius(view.dp(radiusDp));
        }
        if (borderWidthDp > 0) {
            drawable.setStroke(view.dp(borderWidthDp), borderColor);
        }
        return drawable;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private record ResponsiveLayout(
            int panelWidth,
            int panelHeight,
            int margin,
            int sectionGap,
            int chromeHeight,
            int scrollbarWidth,
            int contentWidth,
            int headerHeight,
            int kpiHeight,
            int chartHeight,
            int watchlistHeight
    ) {
    }

    private enum PreviewTab {
        OVERVIEW,
        STORAGE_NETWORK,
        POWER_NETWORK
    }

    private static final class MutableGroup {
        private final String key;
        private String displayName;
        private final boolean systemGroup;

        private MutableGroup(String key, String displayName, boolean systemGroup) {
            this.key = key;
            this.displayName = displayName;
            this.systemGroup = systemGroup;
        }
    }

    private static final class MutableWatchEntry {
        private final String itemId;
        private final String displayName;
        private final long netPerMinute;
        private final long stock;

        private MutableWatchEntry(String itemId, String displayName, long netPerMinute, long stock) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.netPerMinute = netPerMinute;
            this.stock = stock;
        }
    }

    private static final class MutableTableEntry {
        private final String itemId;
        private final String displayName;
        private String groupKey;
        private final long production;
        private final long consumption;
        private final long stock;
        private final boolean critical;
        private boolean starred;

        private MutableTableEntry(String itemId, String displayName, String groupKey, long production, long consumption, long stock, boolean critical, boolean starred) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.groupKey = groupKey;
            this.production = production;
            this.consumption = consumption;
            this.stock = stock;
            this.critical = critical;
            this.starred = starred;
        }

        private long net() {
            return production - consumption;
        }
    }

    private static final class DisplayGroup {
        private final String key;
        private final String title;
        private final List<MutableTableEntry> rows;

        private DisplayGroup(String key, String title, List<MutableTableEntry> rows) {
            this.key = key;
            this.title = title;
            this.rows = rows;
        }
    }
}

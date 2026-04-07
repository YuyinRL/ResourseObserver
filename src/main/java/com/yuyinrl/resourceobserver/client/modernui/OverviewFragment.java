package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.modernui.view.ChartView;
import com.yuyinrl.resourceobserver.client.modernui.view.HeaderView;
import com.yuyinrl.resourceobserver.client.modernui.view.KpiSectionView;
import com.yuyinrl.resourceobserver.client.modernui.view.TabBarView;
import com.yuyinrl.resourceobserver.client.modernui.view.TableView;
import com.yuyinrl.resourceobserver.client.modernui.view.WatchlistView;
import com.yuyinrl.resourceobserver.client.screen.ResourceTerminalScreen;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModelMapper;
import com.yuyinrl.resourceobserver.client.ui.TerminalPage;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.network.ObserverRefreshRequestPayload;
import com.yuyinrl.resourceobserver.network.ObserverUiActionPayload;
import com.yuyinrl.resourceobserver.network.UiActionType;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Modern UI Overview Fragment —— 基于 Modern UI Fragment 的总览仪表板页面。
 * <p>
 * 视图层次：
 * <pre>
 * FrameLayout (root, 面板背景)
 * └── LinearLayout (VERTICAL)
 *     ├── TabBarView        — 标签页栏（Overview / Storage / Power）
 *     └── ScrollView
 *         └── LinearLayout (VERTICAL, contentColumn)
 *             ├── HeaderView       — 页眉（标题 + 连接状态）
 *             ├── KpiSectionView   — KPI 指标卡行
 *             ├── ChartView        — 流量/库存趋势图
 *             ├── WatchlistView    — 关注列表
 *             └── TableView        — 分组物品表格
 * </pre>
 * <p>
 * 数据流：
 * <ol>
 *   <li>构造时接收 {@link ObserverDataPayload}</li>
 *   <li>通过 {@link OverviewViewModelMapper#fromPayload} 转换为 {@link OverviewViewModel}</li>
 *   <li>ViewModel 数据分发到各子 View</li>
 *   <li>定时发送 {@link ObserverRefreshRequestPayload} 刷新数据</li>
 *   <li>用户操作通过 {@link ObserverUiActionPayload} 发送到服务端</li>
 * </ol>
 */
public class OverviewFragment extends Fragment implements ScreenCallback {

    private static final int REFRESH_INTERVAL_MS = 500; // ~10 ticks

    // ========== 数据 ==========
    private ObserverDataPayload payload;
    private OverviewViewModel viewModel;

    // ========== 图表状态 ==========
    private ChartWindow chartWindow = ChartWindow.DAY_24H_5M;
    private ChartScope chartScope = ChartScope.GLOBAL;
    private String chartScopeItemId = "";
    private String selectedItemId;

    // ========== 子 View 引用 ==========
    private HeaderView headerView;
    private KpiSectionView kpiSectionView;
    private ChartView chartView;
    private WatchlistView watchlistView;
    private TableView tableView;
    private TabBarView tabBarView;

    // ========== 刷新定时器 ==========
    private final Runnable refreshRunnable = this::requestRefresh;

    public OverviewFragment(ObserverDataPayload payload) {
        this.payload = payload;
        this.chartWindow = payload.chartWindow();
        this.chartScope = payload.chartScope();
        this.chartScopeItemId = payload.chartScopeItemId() != null ? payload.chartScopeItemId() : "";
        rebuildViewModel();
    }

    /** 判断是否关联指定观察者方块 */
    public boolean matchesObserver(BlockPos pos) {
        return payload != null && payload.observerPos().equals(pos);
    }

    /** 应用新的服务端数据 */
    public void applyPayload(ObserverDataPayload newPayload) {
        this.payload = newPayload;
        this.chartWindow = newPayload.chartWindow();
        this.chartScope = newPayload.chartScope();
        this.chartScopeItemId = newPayload.chartScopeItemId() != null ? newPayload.chartScopeItemId() : "";
        rebuildViewModel();
        updateViews();
    }

    private void rebuildViewModel() {
        if (payload != null) {
            viewModel = OverviewViewModelMapper.fromPayload(payload);
        }
    }

    // ========== Fragment 生命周期 ==========

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        Context context = requireContext();

        // === 根容器（面板背景） ===
        var root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        ShapeDrawable panelBg = new ShapeDrawable();
        panelBg.setCornerRadius(root.dp(8));
        panelBg.setColor(UiThemeTokens.PANEL_BG);
        panelBg.setStroke(root.dp(1), UiThemeTokens.PANEL_BORDER);
        root.setBackground(panelBg);

        var rootParams = new FrameLayout.LayoutParams(
                root.dp(980),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP
        );
        rootParams.setMargins(0, root.dp(16), 0, root.dp(16));
        root.setLayoutParams(rootParams);
        root.setPadding(root.dp(8), root.dp(8), root.dp(8), root.dp(8));

        // === 标签页栏 ===
        tabBarView = new TabBarView(context, TerminalPage.OVERVIEW, page -> {
            if (page != TerminalPage.OVERVIEW) {
                switchToNativePage(page);
            }
        });
        var tabParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tabBarView.dp(28)
        );
        tabParams.bottomMargin = root.dp(4);
        root.addView(tabBarView, tabParams);

        // === 滚动视图 ===
        var scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(true);

        var contentColumn = new LinearLayout(context);
        contentColumn.setOrientation(LinearLayout.VERTICAL);

        int sectionGap = root.dp(6);

        // --- Header ---
        headerView = new HeaderView(context);
        var headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                headerView.dp(48)
        );
        headerParams.bottomMargin = sectionGap;
        contentColumn.addView(headerView, headerParams);

        // --- KPI Section ---
        kpiSectionView = new KpiSectionView(context, this::onKpiCardClicked);
        var kpiParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                kpiSectionView.dp(80)
        );
        kpiParams.bottomMargin = sectionGap;
        contentColumn.addView(kpiSectionView, kpiParams);

        // --- Chart ---
        chartView = new ChartView(context, new ChartView.Callback() {
            @Override
            public void onWindowChanged(ChartWindow window) {
                chartWindow = window;
                requestRefresh();
            }

            @Override
            public void onResetScope() {
                chartScope = ChartScope.GLOBAL;
                chartScopeItemId = "";
                selectedItemId = null;
                if (watchlistView != null) {
                    watchlistView.setSelectedItemId(null);
                }
                requestRefresh();
            }
        });
        var chartParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                chartView.dp(160)
        );
        chartParams.bottomMargin = sectionGap;
        contentColumn.addView(chartView, chartParams);

        // --- Watchlist ---
        watchlistView = new WatchlistView(context, new WatchlistView.Callback() {
            @Override
            public void onItemSelected(String itemId) {
                selectedItemId = itemId;
                chartScope = ChartScope.ITEM;
                chartScopeItemId = itemId;
                requestRefresh();
            }

            @Override
            public void onItemRemoved(String itemId) {
                sendUiAction(UiActionType.TOGGLE_WATCH, itemId, List.of(), "");
            }
        });
        var watchlistParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                watchlistView.dp(80)
        );
        watchlistParams.bottomMargin = sectionGap;
        contentColumn.addView(watchlistView, watchlistParams);

        // --- Table ---
        tableView = new TableView(context, new TableView.Callback() {
            @Override
            public void onItemSelected(String itemId) {
                selectedItemId = itemId;
                chartScope = ChartScope.ITEM;
                chartScopeItemId = itemId;
                if (watchlistView != null) {
                    watchlistView.setSelectedItemId(itemId);
                }
                requestRefresh();
            }

            @Override
            public void onToggleStar(String itemId) {
                sendUiAction(UiActionType.TOGGLE_WATCH, itemId, List.of(), "");
            }

            @Override
            public void onSortChanged(String sortMode) {
                sendUiAction(UiActionType.SET_SORT_MODE, "", List.of(), sortMode);
            }

            @Override
            public void onGroupFilterChanged(String groupKey) {
                sendUiAction(UiActionType.SET_GROUP_FILTER_KEY, "", List.of(), groupKey);
            }

            @Override
            public void onStatusFilterChanged(String statusFilter) {
                sendUiAction(UiActionType.SET_STATUS_FILTER, "", List.of(), statusFilter);
            }

            @Override
            public void onResetFilters() {
                sendUiAction(UiActionType.RESET_FILTERS, "", List.of(), "");
            }
        });
        var tableParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tableParams.bottomMargin = sectionGap;
        contentColumn.addView(tableView, tableParams);

        scrollView.addView(contentColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // 初始数据填充
        updateViews();

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        scheduleRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        View view = getView();
        if (view != null) {
            view.removeCallbacks(refreshRunnable);
        }
    }

    // ========== ScreenCallback ==========

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean hasDefaultBackground() {
        return true;
    }

    @Override
    public boolean shouldBlurBackground() {
        return true;
    }

    // ========== 数据更新 ==========

    private void updateViews() {
        if (viewModel == null) {
            return;
        }
        if (headerView != null) {
            headerView.update(
                    payload.observerPos(),
                    payload.isBound()
            );
        }
        if (kpiSectionView != null) {
            kpiSectionView.update(viewModel.kpis());
        }
        if (chartView != null) {
            chartView.update(
                    viewModel.chartSeries(),
                    viewModel.energyChartSeries(),
                    chartWindow,
                    selectedItemId
            );
        }
        if (watchlistView != null) {
            watchlistView.update(viewModel.watchlistItems(), selectedItemId);
        }
        if (tableView != null) {
            tableView.update(viewModel.tableGroups(), viewModel.uiState());
        }
    }

    // ========== 网络通信 ==========

    private void scheduleRefresh() {
        View view = getView();
        if (view != null) {
            view.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        }
    }

    private void requestRefresh() {
        if (payload == null) {
            return;
        }
        try {
            PacketDistributor.sendToServer(new ObserverRefreshRequestPayload(
                    payload.observerPos(),
                    chartWindow,
                    chartScope,
                    chartScopeItemId
            ));
        } catch (Exception ignored) {
            // 网络未就绪时静默忽略
        }
        scheduleRefresh();
    }

    private void sendUiAction(UiActionType actionType, String itemId,
                              List<String> itemIds, String actionValue) {
        if (payload == null) {
            return;
        }
        try {
            PacketDistributor.sendToServer(new ObserverUiActionPayload(
                    payload.observerPos(),
                    actionType,
                    itemId,
                    itemIds,
                    actionValue,
                    chartWindow,
                    chartScope,
                    chartScopeItemId
            ));
        } catch (Exception ignored) {
        }
    }

    // ========== 交互回调 ==========

    private void onKpiCardClicked(OverviewViewModel.KpiType type) {
        if (viewModel == null) {
            return;
        }
        if (type == OverviewViewModel.KpiType.STORAGE) {
            // 打开存储详情弹窗
            if (viewModel.storageDetail() != null) {
                var dialog = new StorageDetailDialogFragment(viewModel.storageDetail());
                dialog.show(getChildFragmentManager(), "storage_detail");
            }
        } else {
            // 打开 KPI 详情弹窗
            OverviewViewModel.KpiDetail detail = viewModel.kpiDetailFor(type);
            if (detail != null) {
                var dialog = new KpiDetailDialogFragment(detail);
                dialog.show(getChildFragmentManager(), "kpi_detail");
            }
        }
    }

    /**
     * 切换到原生页面 —— 关闭 Modern UI 屏幕并打开 ResourceTerminalScreen。
     */
    private void switchToNativePage(TerminalPage targetPage) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ResourceTerminalScreen(payload, targetPage));
    }
}

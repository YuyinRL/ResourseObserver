package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.DevMode;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.TerminalPage;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.client.ui.render.ChartRenderer;
import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.network.ObserverRefreshRequestPayload;
import com.yuyinrl.resourceobserver.network.ObserverUiActionPayload;
import com.yuyinrl.resourceobserver.network.UiActionType;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.animation.ObjectAnimator;
import icyllis.modernui.animation.PropertyValuesHolder;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.view.ViewTreeObserver;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.PopupWindow;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.yuyinrl.resourceobserver.client.modernui.ModernUiTheme.*;

/**
 * ModernUI 主容器 Fragment —— 资源终端的 ModernUI 版入口。
 * <p>
 * 职责：
 * - 管理顶部标签栏（Overview / Storage Network / Power Network）
 * - 协调子页面的显示（内容区域）
 * - 定时向服务端请求数据刷新
 * - 持有 {@link ViewModelBridge} 供子页面访问数据
 */
public class ResourceTerminalFragment extends Fragment implements ViewModelBridge.DataChangeListener {

    private static final long REFRESH_INTERVAL_FAST_MS = 500;
    private static final long REFRESH_INTERVAL_NORMAL_MS = 1000;
    private static final float PANEL_SCALE = 0.90f;
    private static final String[] SCALE_LABELS = {"S", "M", "L"};
    /** 文字缩放倍率：S=小号，M=中号（默认），L=大号 */
    private static final float[] TEXT_SCALES  = {1.0f, 1.2f, 1.45f};
    /** 布局缩放倍率（图标、行高等） */
    private static final float[] LAYOUT_SCALES = {1.0f, 1.2f, 1.45f};

    /** 全局活动实例引用，供 {@link com.yuyinrl.resourceobserver.client.ClientPayloadHandler} 定位 */
    private static volatile ResourceTerminalFragment activeInstance;

    private final ViewModelBridge bridge;
    private TerminalPage activePage = TerminalPage.OVERVIEW;
    private ChartWindow chartWindow;
    private ChartRenderer.ChartPage chartPage = ChartRenderer.ChartPage.THROUGHPUT;
    private ChartRenderer.LineMode chartLineMode = ChartRenderer.LineMode.ALL;
    private ChartRenderer.SmoothingMode chartSmoothingMode = ChartRenderer.SmoothingMode.SMOOTH;
    private ChartRenderer.ChartDataType chartDataType = ChartRenderer.ChartDataType.ITEMS;
    private String selectedItemId;
    private final Set<String> selectedItemIds = new LinkedHashSet<>();

    private int scaleModeIndex = 1;

    // Root view hierarchy
    private FrameLayout stage;
    private FrameLayout contentContainer;
    /** 弹窗专用叠加层 —— 与 contentContainer 同级，不受内容重建影响 */
    private FrameLayout dialogOverlay;
    private ChartView activeChartView;
    private OverviewPageBuilder.ChartSectionState chartSectionState;
    private OverviewPageBuilder.KpiSectionState kpiSectionState;

    // Incremental update state for Overview page
    private String overviewStructuralKey;
    private Map<String, TextView[]> overviewTableCells;
    private Map<String, TextView[]> overviewWatchCells;

    // Incremental update state for Storage Network page
    private String storageStructuralKey;
    private Map<String, TextView[]> storageItemCells;
    private Map<String, View[]> storageItemBufferBars;
    private String storageNodeStructuralKey;
    private Map<String, View[]> storageNodeCells;

    // Scroll position preservation across content rebuilds
    private int savedScrollY = 0;

    // 增量刷新：跟踪上次构建时使用的 ViewModel，跳过无变化的重建
    private Object lastBuiltViewModel;
    // 控制入场动画：仅在切换页面 / 首次加载时播放瀑布动画
    private boolean contentNeedsStagger = true;

    // Persisted table group expand/collapse state across data rebuilds
    private final Map<String, Boolean> tableExpandedState = new HashMap<>();

    /** 跟踪当前所有打开的 PopupWindow（Dialog/菜单），按 E 关界面时统一关闭。 */
    private final java.util.List<java.lang.ref.WeakReference<PopupWindow>> trackedPopups = new java.util.ArrayList<>();

    /** 注册一个 PopupWindow 由本 Fragment 生命周期管理。Fragment 暂停/销毁时会自动 dismiss。 */
    public void trackPopup(PopupWindow popup) {
        if (popup == null) return;
        // 清理已失效引用
        trackedPopups.removeIf(ref -> {
            PopupWindow p = ref.get();
            return p == null || !p.isShowing();
        });
        trackedPopups.add(new java.lang.ref.WeakReference<>(popup));
    }

    /** 关闭所有由本 Fragment 跟踪的 PopupWindow。 */
    public void dismissAllPopups() {
        for (var ref : trackedPopups) {
            PopupWindow p = ref.get();
            if (p != null && p.isShowing()) {
                try { p.dismiss(); } catch (Throwable ignored) {}
            }
        }
        trackedPopups.clear();
    }

    // 收藏列表当前翻页索引（跨数据刷新保持）
    private int watchlistPageIndex = 0;

    // Tab references for highlight updates
    private TextView tabOverview;
    private TextView tabStorage;
    private TextView tabPower;
    private TextView tabDev;

    private boolean refreshInFlight;

    /** 弹窗打开期间为 true */
    private volatile boolean dialogOpen = false;

    /** 弹窗内容刷新回调 —— 数据更新时调用以同步弹窗显示内容 */
    private volatile Runnable dialogContentRefresher;

    /** Whether a text input (e.g. group name popup) is currently active — suppresses inventory-key close */
    private boolean textInputActive = false;

    /** 页面重建中标志 —— 防止旧视图销毁时的失焦事件清除 searchBoxFocused 状态 */
    private boolean rebuilding = false;

    // ---- Hover tooltip state (vanilla-style tooltip via ScreenEvent.Render.Post) ----
    /** Pre-built tooltip lines for the currently hovered item/fluid (empty = no tooltip) */
    private volatile List<net.minecraft.network.chat.Component> hoveredTooltipLines = List.of();

    /** Timestamp of last full UI rebuild — used to suppress rapid data-change rebuilds on open */
    private long lastFullRebuildTime;

    /** 搜索防抖回调 —— 延迟触发 ViewModel 重建，避免每次按键都重建页面 */
    private Runnable searchDebounceRunnable;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            requestRefreshNow();
            View root = getView();
            if (root != null) {
                root.postDelayed(this, currentRefreshInterval());
            }
        }
    };

    /** 1-second countdown tick for smooth buffer time display (independent of data refresh). */
    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            StorageNetworkPageBuilder.tickBufferCountdown(ResourceTerminalFragment.this);
            View root = getView();
            if (root != null) {
                root.postDelayed(this, 1000L);
            }
        }
    };

    public ResourceTerminalFragment(ObserverDataPayload initialPayload) {
        this.bridge = new ViewModelBridge(initialPayload);
        this.chartWindow = initialPayload.chartWindow();
        syncScopeFromPayload(initialPayload);
    }

    /** dp → Modern UI pixels（同 OverviewPreviewFragment 的实现方式） */
    public int dp(float dpVal) {
        return stage != null ? stage.dp(dpVal) : Math.round(dpVal);
    }

    // ===================== Static accessor =====================

    public static ResourceTerminalFragment getActiveInstance() {
        return activeInstance;
    }

    public boolean matchesObserver(BlockPos observerPos) {
        return bridge.getObserverPos().equals(observerPos);
    }

    /** 由 ClientPayloadHandler 调用以更新数据 */
    public void applyPayload(ObserverDataPayload payload) {
        refreshInFlight = false;
        this.chartWindow = payload.chartWindow();
        syncScopeFromPayload(payload);
        bridge.applyPayload(payload);
    }

    /** 当前活跃的合成计划结果回调（由 CraftingSubTabBuilder 在用户点击 Order 时注册）。 */
    private java.util.function.Consumer<com.yuyinrl.resourceobserver.network.CraftingPlanResultPayload> craftingPlanCallback;

    public void setCraftingPlanCallback(
            java.util.function.Consumer<com.yuyinrl.resourceobserver.network.CraftingPlanResultPayload> cb) {
        this.craftingPlanCallback = cb;
    }

    /** 服务端回传计划摘要时由 ClientPayloadHandler 调用。 */
    public void applyCraftingPlanResult(com.yuyinrl.resourceobserver.network.CraftingPlanResultPayload payload) {
        var cb = this.craftingPlanCallback;
        if (cb != null) {
            this.craftingPlanCallback = null;
            View root = getView();
            if (root != null) {
                // PopupWindow.showAtLocation 必须在 ModernUI 的 UI 线程；
                // 网络 payload 在 Minecraft 渲染线程执行，需要 post 切换。
                root.post(() -> cb.accept(payload));
            } else {
                cb.accept(payload);
            }
        }
    }

    /** 当前活跃的合成树响应回调（由 CraftingSubTabBuilder 在用户请求树时注册）。 */
    private java.util.function.Consumer<com.yuyinrl.resourceobserver.network.CraftingTreeResponsePayload> craftingTreeCallback;

    public void setCraftingTreeCallback(
            java.util.function.Consumer<com.yuyinrl.resourceobserver.network.CraftingTreeResponsePayload> cb) {
        this.craftingTreeCallback = cb;
    }

    /** 服务端回传合成树时由 ClientPayloadHandler 调用。 */
    public void applyCraftingTreeResponse(com.yuyinrl.resourceobserver.network.CraftingTreeResponsePayload payload) {
        var cb = this.craftingTreeCallback;
        if (cb != null) {
            this.craftingTreeCallback = null;
            View root = getView();
            if (root != null) {
                root.post(() -> cb.accept(payload));
            } else {
                cb.accept(payload);
            }
        }
    }

    public boolean isTextInputActive() { return textInputActive; }
    public void setTextInputActive(boolean active) { this.textInputActive = active; }

    boolean isRebuilding() { return rebuilding; }

    /**
     * 防抖更新搜索词 —— 延迟 200ms 后才触发 ViewModel 重建 + 页面刷新，
     * 连续快速输入时只有最后一次生效，避免每次按键都触发完整重建。
     */
    void scheduleSearchUpdate(String query) {
        View root = getView();
        if (root == null) return;
        if (searchDebounceRunnable != null) {
            root.removeCallbacks(searchDebounceRunnable);
        }
        searchDebounceRunnable = () -> bridge.setSearchQuery(query);
        root.postDelayed(searchDebounceRunnable, 200L);
    }

    // ---- Hover tooltip accessors (read on render thread via ScreenEvent) ----
    public List<net.minecraft.network.chat.Component> getHoveredTooltipLines() { return hoveredTooltipLines; }
    public void setHoveredTooltipLines(List<net.minecraft.network.chat.Component> lines) {
        this.hoveredTooltipLines = lines;
    }
    public void clearHoveredTooltip() {
        this.hoveredTooltipLines = List.of();
    }

    // ===================== Public accessors for child pages =====================

    public ViewModelBridge getBridge() { return bridge; }
    public ChartWindow getChartWindow() { return chartWindow; }
    public void setChartWindow(ChartWindow w) { this.chartWindow = w; }

    public ChartRenderer.ChartPage getChartPage() { return chartPage; }
    public void setChartPage(ChartRenderer.ChartPage p) { this.chartPage = p; }

    public ChartRenderer.LineMode getChartLineMode() { return chartLineMode; }
    public void setChartLineMode(ChartRenderer.LineMode m) { this.chartLineMode = m; }

    public ChartRenderer.SmoothingMode getChartSmoothingMode() { return chartSmoothingMode; }
    public void setChartSmoothingMode(ChartRenderer.SmoothingMode s) { this.chartSmoothingMode = s; }

    public ChartRenderer.ChartDataType getChartDataType() { return chartDataType; }
    public void setChartDataType(ChartRenderer.ChartDataType dt) { this.chartDataType = dt; }

    public String getSelectedItemId() { return selectedItemId; }
    public void setSelectedItemId(String id) {
        this.selectedItemId = id;
        if (id != null) selectedItemIds.add(id);
    }
    public Set<String> getSelectedItemIds() { return selectedItemIds; }

    public void setActiveChartView(ChartView v) { this.activeChartView = v; }
    public void setChartSectionState(OverviewPageBuilder.ChartSectionState s) { this.chartSectionState = s; }
    public OverviewPageBuilder.ChartSectionState getChartSectionState() { return chartSectionState; }

    public void setKpiSectionState(OverviewPageBuilder.KpiSectionState s) { this.kpiSectionState = s; }
    public OverviewPageBuilder.KpiSectionState getKpiSectionState() { return kpiSectionState; }

    // Incremental update state accessors
    public String getOverviewStructuralKey() { return overviewStructuralKey; }
    public void setOverviewStructuralKey(String key) { this.overviewStructuralKey = key; }
    public Map<String, TextView[]> getOverviewTableCells() { return overviewTableCells; }
    public void setOverviewTableCells(Map<String, TextView[]> m) { this.overviewTableCells = m; }
    public Map<String, TextView[]> getOverviewWatchCells() { return overviewWatchCells; }
    public void setOverviewWatchCells(Map<String, TextView[]> m) { this.overviewWatchCells = m; }

    // Storage Network incremental update state accessors
    public String getStorageStructuralKey() { return storageStructuralKey; }
    public void setStorageStructuralKey(String key) { this.storageStructuralKey = key; }
    public Map<String, TextView[]> getStorageItemCells() { return storageItemCells; }
    public void setStorageItemCells(Map<String, TextView[]> m) { this.storageItemCells = m; }
    public Map<String, View[]> getStorageItemBufferBars() { return storageItemBufferBars; }
    public void setStorageItemBufferBars(Map<String, View[]> m) { this.storageItemBufferBars = m; }
    public String getStorageNodeStructuralKey() { return storageNodeStructuralKey; }
    public void setStorageNodeStructuralKey(String key) { this.storageNodeStructuralKey = key; }
    public Map<String, View[]> getStorageNodeCells() { return storageNodeCells; }
    public void setStorageNodeCells(Map<String, View[]> m) { this.storageNodeCells = m; }

    /** 根据当前时间窗口返回适配的刷新间隔：细粒度窗口使用快速刷新 */
    private long currentRefreshInterval() {
        return (chartWindow == ChartWindow.HOUR_1H_1M || chartWindow == ChartWindow.DEBUG_10M_5S)
                ? REFRESH_INTERVAL_FAST_MS : REFRESH_INTERVAL_NORMAL_MS;
    }

    /** Display name for selected item, or null if global scope. */
    public String getSelectedItemDisplayName() {
        if (selectedItemId == null || selectedItemId.isBlank()) return null;
        OverviewViewModel vm = bridge.getOverviewViewModel();
        if (vm == null || vm.tableGroups() == null) return selectedItemId;
        for (OverviewViewModel.TableGroup group : vm.tableGroups()) {
            for (OverviewViewModel.TableRow row : group.rows()) {
                if (selectedItemId.equals(row.itemId())) return row.displayName();
            }
        }
        return selectedItemId;
    }

    /** Notifies server about chart window change. */
    public void sendChartWindowChange() { requestRefreshNow(); }

    // ===================== Lifecycle =====================

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        stage = new FrameLayout(getContext());
        stage.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stage.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) {
                rebuildUi();
            }
        });
        bridge.addListener(this);
        return stage;
    }

    @Override
    public void onResume() {
        super.onResume();
        activeInstance = this;
        // Don't call rebuildUi() here — the layout change listener handles the
        // initial build. Calling it here too causes a double-refresh on re-open
        // because Modern UI may pre-size the stage before the first layout pass.
        View root = getView();
        if (root != null) {
            root.postDelayed(refreshRunnable, currentRefreshInterval());
            root.postDelayed(countdownRunnable, 1000L);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (activeInstance == this) {
            activeInstance = null;
        }
        // Dismiss any open popup menus / context menus / input dialogs
        OverviewPageBuilder.dismissActiveMenu(this);
        // 统一关闭所有跟踪的 Dialog / Popup（合成下单、Review、合成树等）
        dismissAllPopups();
        View root = getView();
        if (root != null) {
            root.removeCallbacks(refreshRunnable);
            root.removeCallbacks(countdownRunnable);
        }
        // Explicitly re-grab mouse on the game thread to prevent lingering cursor after screen close
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.mouseHandler.grabMouse();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bridge.removeListener(this);
        // 界面销毁时清空图标纹理缓存，释放 GPU 资源
        ItemTextureCache.getInstance().clear();
    }

    // ===================== DataChangeListener =====================

    @Override
    public void onDataChanged() {
        // Suppress data-change rebuilds for 1.5s after a full UI rebuild to avoid
        // a visible double-refresh on open (the initial build already shows the data).
        if (System.currentTimeMillis() - lastFullRebuildTime < currentRefreshInterval() * 3) {
            return;
        }
        // 将重建推迟到下一帧，避免在触摸事件处理过程中销毁视图树导致点击丢失
        View root = getView();
        if (root != null) {
            root.post(this::rebuildContent);
            // 弹窗内容同步刷新
            Runnable refresher = dialogContentRefresher;
            if (refresher != null) {
                root.post(refresher);
            }
        }
    }

    // ===================== UI Build =====================

    void rebuildUi() {
        if (stage == null || stage.getWidth() == 0) {
            return;
        }
        // 确保 UI 缩放与当前档位同步
        ModernUiTheme.setUiScale(TEXT_SCALES[scaleModeIndex], LAYOUT_SCALES[scaleModeIndex]);

        lastFullRebuildTime = System.currentTimeMillis();
        stage.removeAllViews();
        dialogOpen = false; // full rebuild destroys any open dialog
        dialogContentRefresher = null;
        lastBuiltViewModel = null;
        contentNeedsStagger = true;
        overviewStructuralKey = null;
        overviewTableCells = null;
        overviewWatchCells = null;
        storageStructuralKey = null;
        storageItemCells = null;
        storageItemBufferBars = null;
        storageNodeStructuralKey = null;
        storageNodeCells = null;

        int screenW = stage.getWidth();
        int screenH = stage.getHeight();
        int panelW = Math.round(screenW * PANEL_SCALE);
        int panelH = Math.round(screenH * PANEL_SCALE);
        int margin = dp(10);
        int chromeH = ModernUiTheme.scaledDp(this, 28);
        int sectionGap = dp(6);

        // Centered panel
        FrameLayout panel = new FrameLayout(getContext());
        panel.setBackground(panelBackground(panel));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(panelW, panelH);
        panelParams.gravity = Gravity.CENTER;
        stage.addView(panel, panelParams);

        // Chrome bar (tabs + controls)
        LinearLayout chrome = new LinearLayout(getContext());
        chrome.setOrientation(LinearLayout.HORIZONTAL);
        chrome.setGravity(Gravity.CENTER_VERTICAL);
        chrome.setPadding(0, 0, 0, 0);
        chrome.setLayoutTransition(smoothLayoutTransition());
        FrameLayout.LayoutParams chromeParams = new FrameLayout.LayoutParams(
                panelW - margin * 2, chromeH);
        chromeParams.leftMargin = margin;
        chromeParams.topMargin = margin;
        panel.addView(chrome, chromeParams);
        buildChrome(chrome, chromeH - dp(4));

        // Content container
        contentContainer = new FrameLayout(getContext());
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                panelW - margin * 2,
                panelH - margin * 2 - chromeH - sectionGap);
        contentParams.leftMargin = margin;
        contentParams.topMargin = margin + chromeH + sectionGap;
        panel.addView(contentContainer, contentParams);

        // Dialog overlay (same position/size as content, sits on top)
        dialogOverlay = new FrameLayout(getContext());
        panel.addView(dialogOverlay, new FrameLayout.LayoutParams(contentParams));

        // Build active page content
        rebuildContent();
    }

    /** 重建当前页面内容（数据更新时调用）*/
    void rebuildContent() {
        if (contentContainer == null || contentContainer.getWidth() == 0 && contentContainer.getHeight() == 0) {
            // 在第一次布局前不构建内容，等 rebuildUi 触发
            if (contentContainer != null && stage != null && stage.getWidth() > 0) {
                buildPageContent();
            }
            return;
        }
        buildPageContent();
    }

    private void buildPageContent() {
        if (contentContainer == null) return;
        rebuilding = true;
        try {
            buildPageContentInner();
        } finally {
            rebuilding = false;
        }
    }

    private void buildPageContentInner() {

        // Save scroll position before removing children
        if (contentContainer.getChildCount() > 0) {
            View first = contentContainer.getChildAt(0);
            if (first instanceof ScrollView sv) {
                savedScrollY = sv.getScrollY();
            }
        }

        int contentW = contentContainer.getLayoutParams().width;
        int contentH = contentContainer.getLayoutParams().height;
        if (contentW <= 0) contentW = contentContainer.getWidth();
        if (contentH <= 0) contentH = contentContainer.getHeight();

        // —— 增量刷新：如果当前页面的 ViewModel 没有变化，跳过重建 ——
        Object currentVm = getActivePageViewModel();
        boolean hasExistingContent = contentContainer.getChildCount() > 0;
        if (hasExistingContent && currentVm != null
                && java.util.Objects.equals(currentVm, lastBuiltViewModel)) {
            return; // 数据未变，跳过重建
        }

        // —— Overview 增量刷新：结构不变时原地更新数值，不销毁视图树 ——
        if (activePage == TerminalPage.OVERVIEW && hasExistingContent
                && currentVm instanceof OverviewViewModel
                && OverviewPageBuilder.tryIncrementalUpdate(this)) {
            lastBuiltViewModel = currentVm;
            return; // 增量刷新成功，跳过完整重建
        }

        // —— Power Network 增量刷新：保留 DonutChartView 的 hover/tooltip 状态 ——
        if (activePage == TerminalPage.POWER_NETWORK && hasExistingContent
                && currentVm instanceof PowerNetworkViewModel
                && PowerNetworkPageBuilder.tryIncrementalUpdate(this)) {
            lastBuiltViewModel = currentVm;
            return;
        }

        // —— Storage Network 增量刷新：保留 ScrollView，避免滚动条闪烁 ——
        if (activePage == TerminalPage.STORAGE_NETWORK && hasExistingContent
                && currentVm instanceof StorageNetworkViewModel
                && StorageNetworkPageBuilder.tryIncrementalUpdate(this)) {
            lastBuiltViewModel = currentVm;
            return;
        }

        lastBuiltViewModel = currentVm;

        // 清除 tooltip 状态（在确认需要重建后再清除）
        clearHoveredTooltip();

        boolean animate = contentNeedsStagger;
        contentNeedsStagger = false;

        // Full rebuild — invalidate incremental state
        overviewStructuralKey = null;
        overviewTableCells = null;
        overviewWatchCells = null;
        storageStructuralKey = null;
        storageItemCells = null;
        storageItemBufferBars = null;
        storageNodeStructuralKey = null;
        storageNodeCells = null;

        contentContainer.removeAllViews();

        switch (activePage) {
            case OVERVIEW -> buildOverviewPage(contentW, contentH, animate);
            case STORAGE_NETWORK -> buildStorageNetworkPage(contentW, contentH, animate);
            case POWER_NETWORK -> buildPowerNetworkPage(contentW, contentH, animate);
            case DEV_COMPONENTS -> buildDevComponentsPage(contentW, contentH);
        }

        // Restore scroll position using OnPreDrawListener(fires before draw, no visible flash)
        if (savedScrollY > 0 && contentContainer.getChildCount() > 0) {
            View first = contentContainer.getChildAt(0);
            if (first instanceof ScrollView sv) {
                int scrollTarget = savedScrollY;
                sv.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        sv.getViewTreeObserver().removeOnPreDrawListener(this);
                        sv.scrollTo(0, scrollTarget);
                        return true;
                    }
                });
            }
        } else if (animate && contentContainer.getChildCount() > 0) {
            // 仅在首次加载 / 切换页面时淡入
            fadeIn(contentContainer.getChildAt(0), 200);
        }
    }

    /** 获取当前活动页面的 ViewModel 用于变化检测（record 自带 equals） */
    private Object getActivePageViewModel() {
        return switch (activePage) {
            case OVERVIEW -> bridge.getOverviewViewModel();
            case STORAGE_NETWORK -> bridge.getStorageViewModel();
            case POWER_NETWORK -> bridge.getPowerViewModel();
            case DEV_COMPONENTS -> null; // 开发页面无 ViewModel，总是重建
        };
    }

    // ===================== Chrome =====================

    private void buildChrome(LinearLayout chrome, int tabHeight) {
        tabOverview = tabButton(tr("screen.resourceobserver.storage.tab.overview"),
                activePage == TerminalPage.OVERVIEW, tabHeight);
        tabOverview.setOnClickListener(v -> switchPage(TerminalPage.OVERVIEW));
        chrome.addView(tabOverview);

        tabStorage = tabButton(tr("screen.resourceobserver.storage.tab.storage_network"),
                activePage == TerminalPage.STORAGE_NETWORK, tabHeight);
        tabStorage.setOnClickListener(v -> switchPage(TerminalPage.STORAGE_NETWORK));
        chrome.addView(tabStorage, leftGap(dp(4)));

        tabPower = tabButton(tr("screen.resourceobserver.storage.tab.power_network"),
                activePage == TerminalPage.POWER_NETWORK, tabHeight);
        tabPower.setOnClickListener(v -> switchPage(TerminalPage.POWER_NETWORK));
        chrome.addView(tabPower, leftGap(dp(4)));

        // Dev 标签页仅在开发环境下显示
        if (DevMode.isDev()) {
            tabDev = tabButton("Dev", activePage == TerminalPage.DEV_COMPONENTS, tabHeight);
            tabDev.setOnClickListener(v -> switchPage(TerminalPage.DEV_COMPONENTS));
            chrome.addView(tabDev, leftGap(dp(4)));
        }

        chrome.addView(spacer(chrome), spacerParams());

        TextView sizeBtn = chromeIconButton(this, SCALE_LABELS[scaleModeIndex], false);
        sizeBtn.setOnClickListener(v -> {
            scaleModeIndex = (scaleModeIndex + 1) % SCALE_LABELS.length;
            ModernUiTheme.setUiScale(TEXT_SCALES[scaleModeIndex], LAYOUT_SCALES[scaleModeIndex]);
            rebuildUi();
        });
        chrome.addView(sizeBtn);
    }

    private void switchPage(TerminalPage page) {
        if (activePage == page) return;
        activePage = page;
        savedScrollY = 0;
        contentNeedsStagger = true;
        lastBuiltViewModel = null;
        updateTabHighlights();
        rebuildContent();
    }

    /** 就地更新标签栏高亮，无需重建整个 chrome */
    private void updateTabHighlights() {
        if (tabOverview == null) return;
        applyTabStyle(tabOverview, activePage == TerminalPage.OVERVIEW);
        applyTabStyle(tabStorage, activePage == TerminalPage.STORAGE_NETWORK);
        applyTabStyle(tabPower, activePage == TerminalPage.POWER_NETWORK);
        if (tabDev != null) {
            applyTabStyle(tabDev, activePage == TerminalPage.DEV_COMPONENTS);
        }
    }

    private void applyTabStyle(TextView tab, boolean active) {
        tab.setTextColor(active ? UiThemeTokens.CYAN : UiThemeTokens.TEXT);
        tab.setBackground(tabRippleBackground(tab, active));
    }

    private TextView tabButton(String text, boolean active, int tabHeight) {
        TextView button = new TextView(getContext());
        button.setText(text);
        button.setTextSize(10 * ModernUiTheme.getTextScale());
        button.setIncludeFontPadding(false);
        button.setSingleLine();
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(active ? UiThemeTokens.CYAN : UiThemeTokens.TEXT);
        button.setPadding(dp(12), dp(3), dp(12), dp(3));
        button.setBackground(tabRippleBackground(button, active));
        button.setClickable(true);
        button.setFocusable(true);
        addHoverScaleEffect(button);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, tabHeight));
        return button;
    }

    // ===================== Page builders (delegate to page classes) =====================

    private void buildOverviewPage(int w, int h, boolean animate) {
        OverviewPageBuilder.build(this, contentContainer, w, h, animate);
    }

    private void buildStorageNetworkPage(int w, int h, boolean animate) {
        StorageNetworkPageBuilder.build(this, contentContainer, w, h, animate);
    }

    private void buildPowerNetworkPage(int w, int h, boolean animate) {
        PowerNetworkPageBuilder.build(this, contentContainer, w, h, animate);
    }

    private void buildDevComponentsPage(int w, int h) {
        DevComponentsPageBuilder.build(this, contentContainer, w, h);
    }

    // ===================== Network =====================

    public void requestRefreshNow() {
        ObserverDataPayload p = bridge.getPayload();
        if (p == null || refreshInFlight) return;
        refreshInFlight = true;
        ChartScope scope = (selectedItemId == null || selectedItemId.isBlank())
                ? ChartScope.GLOBAL : ChartScope.ITEM;
        try {
            PacketDistributor.sendToServer(new ObserverRefreshRequestPayload(
                    p.observerPos(), chartWindow, scope,
                    scope == ChartScope.ITEM ? selectedItemId : ""));
        } catch (Exception e) {
            refreshInFlight = false;
            ResourceObserverMod.LOGGER.debug("Refresh request failed", e);
        }
    }

    public void sendUiAction(UiActionType actionType, String itemId, String actionValue) {
        sendUiAction(actionType, itemId, List.of(), actionValue);
    }

    public void sendUiAction(UiActionType actionType, String itemId,
                             List<String> itemIds, String actionValue) {
        ObserverDataPayload p = bridge.getPayload();
        if (p == null) return;
        // 抑制刷新请求直到操作响应到达，避免旧数据覆盖操作结果
        refreshInFlight = true;
        ChartScope scope = (selectedItemId == null || selectedItemId.isBlank())
                ? ChartScope.GLOBAL : ChartScope.ITEM;
        try {
            PacketDistributor.sendToServer(new ObserverUiActionPayload(
                    p.observerPos(), actionType,
                    itemId == null ? "" : itemId,
                    itemIds == null ? List.of() : itemIds,
                    actionValue == null ? "" : actionValue,
                    chartWindow, scope,
                    scope == ChartScope.ITEM ? selectedItemId : ""));
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.debug("UI action failed", e);
        }
    }

    /** Returns the persisted expand/collapse state map for table groups. */
    public Map<String, Boolean> getTableExpandedState() {
        return tableExpandedState;
    }

    public FrameLayout getContentContainer() { return contentContainer; }

    /** 获取弹窗专用叠加层（不受内容重建影响） */
    public FrameLayout getDialogOverlay() { return dialogOverlay; }

    /** 标记弹窗已打开 */
    public void setDialogOpen(boolean open) {
        this.dialogOpen = open;
        if (!open) this.dialogContentRefresher = null;
    }
    /** 弹窗是否处于打开状态 */
    public boolean isDialogOpen() { return dialogOpen; }

    /** 设置弹窗内容刷新回调 */
    public void setDialogContentRefresher(Runnable refresher) { this.dialogContentRefresher = refresher; }

    public int getWatchlistPageIndex() { return watchlistPageIndex; }
    public void setWatchlistPageIndex(int idx) {
        this.watchlistPageIndex = idx;
        this.lastBuiltViewModel = null; // 强制下次重建
    }

    private void syncScopeFromPayload(ObserverDataPayload payload) {
        if (payload.chartScope() == ChartScope.ITEM
                && payload.chartScopeItemId() != null
                && !payload.chartScopeItemId().isBlank()) {
            selectedItemId = payload.chartScopeItemId();
            selectedItemIds.add(selectedItemId);
        }
    }

    // ===================== 启动入口 =====================

    /**
     * 打开 ModernUI 版资源终端界面。
     */
    public static void open(ObserverDataPayload payload) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ResourceTerminalFragment fragment = new ResourceTerminalFragment(payload);
            ScreenCallback callback = new ScreenCallback() {
                @Override
                public boolean isPauseScreen() {
                    return false;
                }
            };
            var screen = MuiModApi.get().createScreen(
                    fragment, callback, mc.screen,
                    Component.translatable("screen.resourceobserver.terminal_title").getString());
            mc.setScreen(screen);
        } catch (Throwable t) {
            ResourceObserverMod.LOGGER.error("Failed to open ModernUI terminal", t);
        }
    }
}

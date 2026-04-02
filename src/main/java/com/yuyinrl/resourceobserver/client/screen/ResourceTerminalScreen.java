package com.yuyinrl.resourceobserver.client.screen;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModelMapper;
import com.yuyinrl.resourceobserver.client.ui.UiLayoutSpec;
import com.yuyinrl.resourceobserver.client.ui.UiLayoutState;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.client.ui.render.ChartRenderer;
import com.yuyinrl.resourceobserver.client.ui.render.HeaderRenderer;
import com.yuyinrl.resourceobserver.client.ui.render.KpiRenderer;
import com.yuyinrl.resourceobserver.client.ui.render.RenderUtils;
import com.yuyinrl.resourceobserver.client.ui.render.TableRenderer;
import com.yuyinrl.resourceobserver.client.ui.render.WatchlistRenderer;
import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.network.ObserverRefreshRequestPayload;
import com.yuyinrl.resourceobserver.network.ObserverUiActionPayload;
import com.yuyinrl.resourceobserver.network.UiActionType;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import com.yuyinrl.resourceobserver.world.ui.PlayerUiPrefsSavedData;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资源终端主屏幕 —— 终端界面的核心控制器。
 * <p>
 * 职责：
 * - 管理界面布局（响应式自适应、尺寸切换 S/M/L）
 * - 管理滚动状态（主页滚动 + 弹出菜单滚动）
 * - 处理所有用户交互（鼠标点击、拖拽、滚轮、键盘）
 * - 协调各子渲染器（Header/KPI/Chart/Watchlist/Table）
 * - 与服务端通信（定时刷新数据、发送 UI 操作指令）
 * - 管理弹出菜单（分组筛选/排序/状态筛选/右键菜单）
 * - 管理分组输入面板（创建/重命名分组）
 * <p>
 * 数据流：
 * 1. 服务端 → ObserverDataPayload → OverviewViewModelMapper → OverviewViewModel
 * 2. OverviewViewModel → 各渲染器绘制 → 收集热区
 * 3. 用户交互 → ObserverUiActionPayload → 服务端处理 → 返回新 Payload
 */
public class ResourceTerminalScreen extends Screen {
    private static final int REFRESH_INTERVAL_TICKS = 10;  // 数据自动刷新间隔（tick）
    private static final int PAGE_SCROLL_STEP = 24;        // 页面滚动步进（像素）
    private static final int POPUP_ROW_HEIGHT = 14;        // 弹出菜单行高
    private static final int POPUP_MAX_HEIGHT = 172;       // 弹出菜单最大高度
    private static final int POPUP_SCROLL_STEP = 20;       // 弹出菜单滚动步进

    /** 面板尺寸模式枚举（S/M/L 三档） */
    private enum SizeMode {
        SMALL("S", 0.96f),
        MEDIUM("M", 1.00f),
        LARGE("L", 1.04f);

        private final String label;
        private final float factor;

        SizeMode(String label, float factor) {
            this.label = label;
            this.factor = factor;
        }

        public SizeMode next() {
            return switch (this) {
                case SMALL -> MEDIUM;
                case MEDIUM -> LARGE;
                case LARGE -> SMALL;
            };
        }
    }

    /** 弹出菜单类型 */
    private enum PopupMenuType {
        NONE,            // 无菜单
        GROUP_FILTER,    // 分组筛选菜单
        SORT,            // 排序模式菜单
        STATUS,          // 状态筛选菜单
        ROW_GROUP        // 行右键分组菜单
    }

    /** 弹出菜单特殊操作类型 */
    private enum PopupSpecial {
        NONE,                // 普通选项
        OPEN_CREATE_GROUP,   // 打开创建分组面板
        OPEN_RENAME_GROUP    // 打开重命名分组面板
    }

    /** 分组输入面板模式 */
    private enum GroupInputMode {
        NONE,    // 不显示
        CREATE,  // 创建新分组
        RENAME   // 重命名分组
    }

    // ========== 数据与视图模型 ==========
    private ObserverDataPayload payload;           // 当前服务端数据
    private OverviewViewModel viewModel;           // 当前视图模型
    private final UiLayoutSpec layoutSpec = UiLayoutSpec.defaultOverview();
    private UiLayoutState layoutState;             // 当前布局状态
    private SizeMode sizeMode = SizeMode.MEDIUM;   // 当前尺寸模式
    private final ChartRenderer.ChartRenderCache chartRenderCache = new ChartRenderer.ChartRenderCache();

    // ========== 图表状态 ==========
    private ChartWindow chartWindow = ChartWindow.DAY_24H_5M;
    private ChartRenderer.LineMode chartLineMode = ChartRenderer.LineMode.ALL;
    private ChartRenderer.ChartPage chartPage = ChartRenderer.ChartPage.THROUGHPUT;
    private ChartRenderer.SmoothingMode smoothingMode = ChartRenderer.SmoothingMode.SMOOTH;

    // ========== 交互状态 ==========
    private int refreshCounter;                          // 刷新计数器
    private boolean refreshInFlight;                     // 是否有刷新请求在途
    private int pageScrollPx;                            // 当前页面滚动位置
    private int maxPageScrollPx;                         // 最大页面滚动量
    private String selectedItemId;                       // 图表选中物品 ID
    private final LinkedHashSet<String> selectedItemIds = new LinkedHashSet<>();  // 多选物品 ID 集合

    private final Map<String, Boolean> groupExpandedState = new HashMap<>();  // 分组折叠状态

    // ========== 热区引用（每帧渲染后更新） ==========
    private UiRect resetButtonHitbox;
    private UiRect chartPageToggleHitbox;
    private UiRect chartSmoothingButtonHitbox;
    private UiRect chartWindowButtonHitbox;
    private UiRect chartLineModeButtonHitbox;
    private UiRect pageScrollTrackHitbox;
    private UiRect pageScrollThumbHitbox;
    private boolean pageScrollDragging;
    private int pageScrollDragOffset;
    private List<WatchlistRenderer.Hitbox> watchlistHitboxes = List.of();
    private List<WatchlistRenderer.RemoveHitbox> watchlistRemoveHitboxes = List.of();
    private List<TableRenderer.GroupHitbox> groupHitboxes = List.of();
    private List<TableRenderer.RowHitbox> rowHitboxes = List.of();
    private List<TableRenderer.RowStarHitbox> rowStarHitboxes = List.of();
    private List<TableRenderer.SortHeaderHitbox> sortHeaderHitboxes = List.of();
    private TableRenderer.FilterButtonHitbox groupFilterButtonHitbox;
    private TableRenderer.FilterButtonHitbox statusButtonHitbox;
    private TableRenderer.ResetHitbox resetFiltersHitbox;

    // ========== 弹出菜单状态 ==========
    private PopupMenuType popupMenuType = PopupMenuType.NONE;
    private int popupAnchorX;
    private int popupAnchorContentTopY;
    private int popupAnchorContentBottomY;
    private String popupRowItemId;
    private List<String> popupRowItemIds = List.of();
    private String popupRowGroupKey;
    private List<PopupOption> popupOptions = List.of();
    private List<PopupOptionHitbox> popupOptionHitboxes = List.of();
    private UiRect popupMenuRect;
    private UiRect popupContentRect;
    private UiRect popupScrollTrackHitbox;
    private UiRect popupScrollThumbHitbox;
    private int popupScrollPx;
    private int popupMaxScrollPx;
    private boolean popupScrollDragging;
    private int popupScrollDragOffset;

    // ========== 分组输入面板状态 ==========
    private GroupInputMode groupInputMode = GroupInputMode.NONE;
    private String groupInputItemId = "";
    private List<String> groupInputItemIds = List.of();
    private String groupInputTargetGroupKey = "";
    private UiRect groupInputPanel;
    private EditBox groupNameEdit;
    private Button groupInputConfirmButton;
    private Button groupInputCancelButton;

    // ========== Widget 引用 ==========
    private Button closeButton;
    private Button sizeModeButton;
    private long groupButtonPressedUntilMs;
    private long statusButtonPressedUntilMs;
    private long resetButtonPressedUntilMs;

    /**
     * 构造资源终端屏幕。
     * @param payload 初始数据载荷（从服务端接收）
     */
    public ResourceTerminalScreen(ObserverDataPayload payload) {
        super(Component.translatable("screen.resourceobserver.terminal_title"));
        this.payload = payload;
        this.chartWindow = payload.chartWindow();
        syncScopeFromPayload(payload);
        rebuildViewModel();
    }

    /** 判断此屏幕是否关联指定的观察者方块 */
    public boolean matchesObserver(net.minecraft.core.BlockPos observerPos) {
        return payload.observerPos().equals(observerPos);
    }

    /** 应用从服务端接收的新数据载荷 */
    public void applyPayload(ObserverDataPayload payload) {
        refreshInFlight = false;
        if (payload.equals(this.payload)) {
            return;
        }
        this.payload = payload;
        this.chartWindow = payload.chartWindow();
        syncScopeFromPayload(payload);
        rebuildViewModel();
        clampPageScroll();
        updateButtonStates();
    }

    @Override
    protected void init() {
        super.init();
        rebuildLayoutAndWidgets();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        rebuildLayoutAndWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        refreshCounter++;
        if (!refreshInFlight && refreshCounter >= REFRESH_INTERVAL_TICKS) {
            refreshCounter = 0;
            requestRefreshNow();
        }
        updateButtonStates();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (groupInputMode != GroupInputMode.NONE
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            submitGroupInput();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (groupInputMode != GroupInputMode.NONE) {
                closeGroupInput();
                return true;
            }
            if (popupMenuType != PopupMenuType.NONE) {
                closePopupMenu();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 鼠标点击处理 —— 按优先级检测交互目标。
     * 优先级：分组输入面板 → 弹出菜单 → 滚动条 → 图表按钮 →
     *         关注列表移除 → 表格星标 → 关注列表选中 → 分组标题 → 表格行
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (groupInputMode != GroupInputMode.NONE) {
            if (groupInputPanel != null && !groupInputPanel.contains(mouseX, mouseY)) {
                closeGroupInput();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (handlePopupMouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (pageScrollThumbHitbox != null && pageScrollThumbHitbox.contains(mouseX, mouseY)) {
            pageScrollDragging = true;
            pageScrollDragOffset = (int) mouseY - pageScrollThumbHitbox.y();
            return true;
        }
        if (pageScrollTrackHitbox != null && pageScrollTrackHitbox.contains(mouseX, mouseY)) {
            jumpPageScrollTo((int) mouseY);
            return true;
        }

        if (layoutState != null && layoutState.scrollViewport().contains(mouseX, mouseY)) {
            if (button == 0 && chartPageToggleHitbox != null && chartPageToggleHitbox.contains(mouseX, mouseY)) {
                chartPage = chartPage.next();
                return true;
            }

            if (button == 0 && chartSmoothingButtonHitbox != null && chartSmoothingButtonHitbox.contains(mouseX, mouseY)) {
                smoothingMode = smoothingMode.next();
                return true;
            }

            if (button == 0 && chartWindowButtonHitbox != null && chartWindowButtonHitbox.contains(mouseX, mouseY)) {
                chartWindow = chartWindow.next();
                requestRefreshNow();
                return true;
            }

            if (button == 0 && chartLineModeButtonHitbox != null && chartLineModeButtonHitbox.contains(mouseX, mouseY)) {
                if (chartPage == ChartRenderer.ChartPage.THROUGHPUT) {
                    chartLineMode = chartLineMode.next();
                }
                return true;
            }

            if (button == 0 && resetFiltersHitbox != null && resetFiltersHitbox.rect().contains(mouseX, mouseY)) {
                resetButtonPressedUntilMs = Util.getMillis() + 120L;
                sendUiAction(UiActionType.RESET_FILTERS, "", "");
                closePopupMenu();
                return true;
            }

            if (button == 0 && resetButtonHitbox != null && resetButtonHitbox.contains(mouseX, mouseY)) {
                selectedItemId = null;
                selectedItemIds.clear();
                requestRefreshNow();
                return true;
            }

            if (button == 0 && handleFilterButtonClick(mouseX, mouseY)) {
                return true;
            }

            if (button == 0 && handleSortHeaderClick(mouseX, mouseY)) {
                return true;
            }

            if (button == 0) {
                for (WatchlistRenderer.RemoveHitbox hitbox : watchlistRemoveHitboxes) {
                    if (hitbox.rect().contains(mouseX, mouseY)) {
                        sendUiAction(UiActionType.TOGGLE_WATCH, hitbox.itemId(), "");
                        return true;
                    }
                }
            }

            if (button == 0) {
                for (TableRenderer.RowStarHitbox hitbox : rowStarHitboxes) {
                    if (hitbox.rect().contains(mouseX, mouseY)) {
                        sendUiAction(UiActionType.TOGGLE_WATCH, hitbox.itemId(), "");
                        return true;
                    }
                }
            }

            if (button == 0) {
                for (WatchlistRenderer.Hitbox hitbox : watchlistHitboxes) {
                    if (hitbox.rect().contains(mouseX, mouseY)) {
                        selectedItemId = hitbox.itemId();
                        selectedItemIds.clear();
                        selectedItemIds.add(hitbox.itemId());
                        requestRefreshNow();
                        return true;
                    }
                }
            }

            if (button == 0) {
                for (TableRenderer.GroupHitbox hitbox : groupHitboxes) {
                    if (hitbox.rect().contains(mouseX, mouseY)) {
                        boolean current = groupExpandedState.getOrDefault(hitbox.groupKey(), true);
                        groupExpandedState.put(hitbox.groupKey(), !current);
                        clampPageScroll();
                        return true;
                    }
                }
            }

            for (TableRenderer.RowHitbox hitbox : rowHitboxes) {
                if (!hitbox.rect().contains(mouseX, mouseY)) {
                    continue;
                }
                if (button == 1) {
                    List<String> targets;
                    if (selectedItemIds.size() > 1 && selectedItemIds.contains(hitbox.itemId())) {
                        targets = List.copyOf(selectedItemIds);
                    } else {
                        targets = List.of(hitbox.itemId());
                    }
                    openRowGroupPopup(targets, hitbox.groupKey(), (int) mouseX, (int) mouseY);
                    return true;
                }
                if (button == 0) {
                    if (hasControlDown()) {
                        if (selectedItemIds.contains(hitbox.itemId())) {
                            selectedItemIds.remove(hitbox.itemId());
                            if (hitbox.itemId().equals(selectedItemId)) {
                                selectedItemId = null;
                            }
                        } else {
                            selectedItemIds.add(hitbox.itemId());
                        }
                    } else {
                        selectedItemId = hitbox.itemId();
                        selectedItemIds.clear();
                        selectedItemIds.add(hitbox.itemId());
                        requestRefreshNow();
                    }
                    return true;
                }
            }
        } else {
            closePopupMenu();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pageScrollDragging = false;
        popupScrollDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (popupScrollDragging && popupScrollTrackHitbox != null && popupScrollThumbHitbox != null) {
            dragPopupScrollTo((int) mouseY - popupScrollDragOffset);
            return true;
        }
        if (pageScrollDragging && pageScrollTrackHitbox != null && pageScrollThumbHitbox != null) {
            dragPageScrollTo((int) mouseY - pageScrollDragOffset);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (popupMenuType != PopupMenuType.NONE && popupMenuRect != null && popupMenuRect.contains(mouseX, mouseY)) {
            int delta = scrollY > 0 ? -POPUP_SCROLL_STEP : (scrollY < 0 ? POPUP_SCROLL_STEP : 0);
            popupScrollPx = Math.max(0, Math.min(popupMaxScrollPx, popupScrollPx + delta));
            return true;
        }
        if (layoutState != null && layoutState.scrollViewport().contains(mouseX, mouseY)) {
            int delta = scrollY > 0 ? -PAGE_SCROLL_STEP : (scrollY < 0 ? PAGE_SCROLL_STEP : 0);
            pageScrollPx = Math.max(0, Math.min(maxPageScrollPx, pageScrollPx + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * 主渲染方法 —— 按层次绘制终端界面。
     * 绘制顺序：面板背景 → 标题栏 → 可滚动内容（Header/KPI/Chart/Watchlist/Table）
     *           → 页面滚动条 → 尺寸/关闭按钮 → 弹出菜单或分组输入面板
     */
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);
        if (viewModel == null) {
            rebuildViewModel();
        }

        RenderUtils.fillPanel(gfx, layoutState.panel(), UiThemeTokens.PANEL_BG, UiThemeTokens.PANEL_BORDER);
        RenderUtils.fillPanel(gfx, layoutState.fixedChrome(), UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);

        ContentLayout content = computeContentLayout(pageScrollPx);
        maxPageScrollPx = Math.max(0, content.totalHeight() - layoutState.scrollViewport().height());
        int clampedScroll = Math.max(0, Math.min(pageScrollPx, maxPageScrollPx));
        if (clampedScroll != pageScrollPx) {
            pageScrollPx = clampedScroll;
            content = computeContentLayout(pageScrollPx);
        }

        UiRect viewport = layoutState.scrollViewport();
        gfx.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());

        HeaderRenderer.render(gfx, font, content.headerArea(), payload.observerPos(), payload.isBound());
        KpiRenderer.render(gfx, font, content.kpiArea(), viewModel.kpis());
        ChartRenderer.RenderResult chartResult = ChartRenderer.render(
                gfx,
                font,
                content.chartArea(),
                viewModel.chartSeries(),
                selectedItemDisplayName(),
                chartWindow,
                chartLineMode,
                chartPage,
                smoothingMode,
                chartRenderCache
        );
        chartWindowButtonHitbox = chartResult.windowToggle();
        chartPageToggleHitbox = chartResult.pageToggle();
        chartLineModeButtonHitbox = chartResult.lineModeButton();
        chartSmoothingButtonHitbox = chartResult.smoothingButton();
        resetButtonHitbox = chartResult.resetButton();

        if (content.watchlistVisible()) {
            WatchlistRenderer.RenderResult watchResult = WatchlistRenderer.render(
                    gfx,
                    font,
                    content.watchlistArea(),
                    viewModel.watchlistItems(),
                    selectedItemId
            );
            watchlistHitboxes = watchResult.itemHitboxes();
            watchlistRemoveHitboxes = watchResult.removeHitboxes();
        } else {
            watchlistHitboxes = List.of();
            watchlistRemoveHitboxes = List.of();
        }

        TableRenderer.RenderResult tableResult = TableRenderer.render(
                gfx,
                font,
                content.tableArea(),
                viewModel.tableGroups(),
                groupExpandedState,
                selectedItemId,
                Set.copyOf(selectedItemIds),
                viewModel.uiState(),
                mouseX,
                mouseY,
                new TableRenderer.FilterPressState(
                        isFilterPressed(groupButtonPressedUntilMs),
                        isFilterPressed(statusButtonPressedUntilMs),
                        isFilterPressed(resetButtonPressedUntilMs)
                )
        );
        groupHitboxes = tableResult.groupHitboxes();
        rowHitboxes = tableResult.rowHitboxes();
        rowStarHitboxes = tableResult.rowStarHitboxes();
        sortHeaderHitboxes = tableResult.sortHeaderHitboxes();
        groupFilterButtonHitbox = tableResult.groupButtonHitbox();
        statusButtonHitbox = tableResult.statusButtonHitbox();
        resetFiltersHitbox = tableResult.resetHitbox();
        gfx.disableScissor();

        renderPageScrollbar(gfx);

        sizeModeButton.render(gfx, mouseX, mouseY, partialTick);
        closeButton.render(gfx, mouseX, mouseY, partialTick);

        if (groupInputMode != GroupInputMode.NONE) {
            renderGroupInputOverlay(gfx, mouseX, mouseY, partialTick);
        } else {
            renderPopupMenu(gfx, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        chartRenderCache.clear();
    }

    /** 重建布局并重新初始化所有 Widget */
    private void rebuildLayoutAndWidgets() {
        clearWidgets();
        layoutState = UiLayoutState.compute(width, height, layoutSpec, sizeMode.factor);
        chartRenderCache.clear();

        closeButton = addRenderableWidget(Button.builder(Component.literal("X"), btn -> onClose())
                .bounds(layoutState.closeButton().x(), layoutState.closeButton().y(), layoutState.closeButton().width(), layoutState.closeButton().height())
                .build());

        sizeModeButton = addRenderableWidget(Button.builder(Component.literal(sizeMode.label), btn -> {
                    sizeMode = sizeMode.next();
                    rebuildLayoutAndWidgets();
                })
                .bounds(layoutState.sizeButton().x(), layoutState.sizeButton().y(), layoutState.sizeButton().width(), layoutState.sizeButton().height())
                .build());

        initGroupInputWidgets();
        updateGroupInputLayout();
        clampPageScroll();
        updateButtonStates();
    }

    private void initGroupInputWidgets() {
        int panelX = layoutState.panel().x() + (layoutState.panel().width() - 230) / 2;
        int panelY = layoutState.panel().y() + (layoutState.panel().height() - 82) / 2;

        groupNameEdit = addRenderableWidget(new EditBox(font, panelX + 12, panelY + 28, 206, 16, Component.empty()));
        groupInputConfirmButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.resourceobserver.overview.group.input.confirm"),
                        btn -> submitGroupInput())
                .bounds(panelX + 12, panelY + 52, 96, 16)
                .build());
        groupInputCancelButton = addRenderableWidget(Button.builder(
                        Component.translatable("screen.resourceobserver.overview.group.input.cancel"),
                        btn -> closeGroupInput())
                .bounds(panelX + 122, panelY + 52, 96, 16)
                .build());
        setGroupInputVisible(false);
    }

    private void updateGroupInputLayout() {
        if (groupNameEdit == null || groupInputConfirmButton == null || groupInputCancelButton == null) {
            return;
        }
        int panelX = layoutState.panel().x() + (layoutState.panel().width() - 230) / 2;
        int panelY = layoutState.panel().y() + (layoutState.panel().height() - 82) / 2;
        groupInputPanel = new UiRect(panelX, panelY, 230, 82);
        groupNameEdit.setX(panelX + 12);
        groupNameEdit.setY(panelY + 28);
        groupInputConfirmButton.setX(panelX + 12);
        groupInputConfirmButton.setY(panelY + 52);
        groupInputCancelButton.setX(panelX + 122);
        groupInputCancelButton.setY(panelY + 52);
    }

    private void setGroupInputVisible(boolean visible) {
        if (groupNameEdit == null || groupInputConfirmButton == null || groupInputCancelButton == null) {
            return;
        }
        groupNameEdit.visible = visible;
        groupNameEdit.active = visible;
        groupInputConfirmButton.visible = visible;
        groupInputConfirmButton.active = visible;
        groupInputCancelButton.visible = visible;
        groupInputCancelButton.active = visible;
        if (!visible) {
            setFocused(null);
        }
    }

    /** 从 Payload 重建 OverviewViewModel */
    private void rebuildViewModel() {
        viewModel = OverviewViewModelMapper.fromPayload(payload);
        for (OverviewViewModel.TableGroup group : viewModel.tableGroups()) {
            groupExpandedState.putIfAbsent(group.key(), true);
        }
        clampSelectedRowsToCurrentData();
    }

    /** 清理无效的选中状态（数据更新后可能有物品消失） */
    private void clampSelectedRowsToCurrentData() {
        if (viewModel == null) {
            selectedItemIds.clear();
            selectedItemId = null;
            return;
        }
        Set<String> validItemIds = new LinkedHashSet<>();
        for (OverviewViewModel.TableGroup group : viewModel.tableGroups()) {
            for (OverviewViewModel.TableRow row : group.rows()) {
                validItemIds.add(row.itemId());
            }
        }
        selectedItemIds.removeIf(id -> !validItemIds.contains(id));
        if (selectedItemId != null && !validItemIds.contains(selectedItemId)) {
            selectedItemId = null;
        }
        if (selectedItemId != null) {
            selectedItemIds.add(selectedItemId);
        }
    }

    /** 计算各区段在可滚动内容中的布局位置 */
    private ContentLayout computeContentLayout(int scrollPx) {
        UiRect viewport = layoutState.scrollViewport();
        int contentX = viewport.x();
        int contentY = viewport.y() - scrollPx;
        int contentW = Math.max(120, viewport.width() - layoutState.scrollbarWidth() - 4);
        int gap = layoutState.sectionGap();

        UiRect headerArea = new UiRect(contentX, contentY, contentW, layoutState.headerHeight());
        int y = headerArea.bottom() + gap;
        UiRect kpiArea = new UiRect(contentX, y, contentW, layoutState.kpiHeight());
        y = kpiArea.bottom() + gap;
        UiRect chartArea = new UiRect(contentX, y, contentW, layoutState.chartHeight());
        y = chartArea.bottom() + gap;

        boolean watchlistVisible = viewModel != null && !viewModel.watchlistItems().isEmpty();
        int watchHeight = watchlistVisible ? layoutState.watchlistHeight() : 0;
        UiRect watchlistArea = new UiRect(contentX, y, contentW, watchHeight);
        if (watchlistVisible) {
            y = watchlistArea.bottom() + gap;
        }

        int tableHeight = TableRenderer.measureHeight(viewModel.tableGroups(), groupExpandedState);
        UiRect tableArea = new UiRect(contentX, y, contentW, tableHeight);
        int totalHeight = tableArea.bottom() - contentY;
        return new ContentLayout(headerArea, kpiArea, chartArea, watchlistArea, tableArea, totalHeight, watchlistVisible);
    }

    /** 渲染页面垂直滚动条 */
    private void renderPageScrollbar(GuiGraphics gfx) {
        UiRect viewport = layoutState.scrollViewport();
        int trackW = Math.max(4, layoutState.scrollbarWidth() - 2);
        int trackX = viewport.right() - trackW;
        int trackY = viewport.y();
        int trackH = viewport.height();
        pageScrollTrackHitbox = new UiRect(trackX, trackY, trackW, trackH);
        gfx.fill(pageScrollTrackHitbox.x(), pageScrollTrackHitbox.y(), pageScrollTrackHitbox.right(), pageScrollTrackHitbox.bottom(), 0x55334455);

        if (maxPageScrollPx <= 0) {
            pageScrollThumbHitbox = null;
            return;
        }
        int thumbH = Math.max(18, Math.round((viewport.height() / (float) (viewport.height() + maxPageScrollPx)) * trackH));
        int travel = Math.max(1, trackH - thumbH);
        int thumbY = trackY + Math.round((pageScrollPx / (float) maxPageScrollPx) * travel);
        pageScrollThumbHitbox = new UiRect(trackX, thumbY, trackW, thumbH);
        gfx.fill(pageScrollThumbHitbox.x(), pageScrollThumbHitbox.y(), pageScrollThumbHitbox.right(), pageScrollThumbHitbox.bottom(), UiThemeTokens.CYAN);
    }

    /** 向服务端发送数据刷新请求 */
    private void requestRefreshNow() {
        if (payload == null) {
            return;
        }
        refreshInFlight = true;
        ChartScope scope = selectedItemId == null || selectedItemId.isBlank() ? ChartScope.GLOBAL : ChartScope.ITEM;
        PacketDistributor.sendToServer(new ObserverRefreshRequestPayload(
                payload.observerPos(),
                chartWindow,
                scope,
                scope == ChartScope.ITEM ? selectedItemId : ""
        ));
    }

    /** 向服务端发送 UI 操作指令 */
    private void sendUiAction(UiActionType actionType, String itemId, String actionValue) {
        sendUiAction(actionType, itemId, List.of(), actionValue);
    }

    private void sendUiAction(UiActionType actionType, List<String> itemIds, String actionValue) {
        String primary = itemIds == null || itemIds.isEmpty() ? "" : itemIds.get(0);
        sendUiAction(actionType, primary, itemIds == null ? List.of() : itemIds, actionValue);
    }

    private void sendUiAction(UiActionType actionType, String itemId, List<String> itemIds, String actionValue) {
        if (payload == null) {
            return;
        }
        ChartScope scope = selectedItemId == null || selectedItemId.isBlank() ? ChartScope.GLOBAL : ChartScope.ITEM;
        PacketDistributor.sendToServer(new ObserverUiActionPayload(
                payload.observerPos(),
                actionType,
                itemId == null ? "" : itemId,
                itemIds == null ? List.of() : itemIds,
                actionValue == null ? "" : actionValue,
                chartWindow,
                scope,
                scope == ChartScope.ITEM ? selectedItemId : ""
        ));
    }

    private void syncScopeFromPayload(ObserverDataPayload payload) {
        if (payload.chartScope() == ChartScope.ITEM && payload.chartScopeItemId() != null && !payload.chartScopeItemId().isBlank()) {
            selectedItemId = payload.chartScopeItemId();
            selectedItemIds.add(selectedItemId);
        } else {
            selectedItemId = null;
        }
    }

    private String selectedItemDisplayName() {
        if (selectedItemId == null || viewModel == null) {
            return null;
        }
        for (OverviewViewModel.TableGroup group : viewModel.tableGroups()) {
            for (OverviewViewModel.TableRow row : group.rows()) {
                if (selectedItemId.equals(row.itemId())) {
                    return row.displayName();
                }
            }
        }
        return selectedItemId;
    }

    /** 处理筛选按钮点击（打开对应弹出菜单） */
    private boolean handleFilterButtonClick(double mouseX, double mouseY) {
        if (groupFilterButtonHitbox != null && groupFilterButtonHitbox.rect().contains(mouseX, mouseY)) {
            markFilterPressed(TableRenderer.MenuType.GROUP);
            togglePopupMenu(PopupMenuType.GROUP_FILTER, groupFilterButtonHitbox.rect(), buildGroupFilterOptions());
            return true;
        }
        if (statusButtonHitbox != null && statusButtonHitbox.rect().contains(mouseX, mouseY)) {
            markFilterPressed(TableRenderer.MenuType.STATUS);
            togglePopupMenu(PopupMenuType.STATUS, statusButtonHitbox.rect(), buildStatusOptions());
            return true;
        }
        return false;
    }

    private boolean handleSortHeaderClick(double mouseX, double mouseY) {
        for (TableRenderer.SortHeaderHitbox hitbox : sortHeaderHitboxes) {
            if (!hitbox.rect().contains(mouseX, mouseY)) {
                continue;
            }
            closePopupMenu();
            sendUiAction(UiActionType.SET_SORT_MODE, "", hitbox.sortMode().key());
            return true;
        }
        return false;
    }

    /** 打开行右键分组菜单（支持多选） */
    private void openRowGroupPopup(List<String> itemIds, String groupKey, int x, int y) {
        if (viewModel == null || itemIds == null || itemIds.isEmpty()) {
            return;
        }
        popupRowItemIds = List.copyOf(itemIds);
        popupRowItemId = popupRowItemIds.get(0);
        popupRowGroupKey = groupKey == null || groupKey.isBlank()
                ? PlayerUiPrefsSavedData.GROUP_UNGROUPED
                : groupKey;
        UiRect anchor = new UiRect(x, y, 1, 1);
        togglePopupMenu(PopupMenuType.ROW_GROUP, anchor, buildRowGroupOptions(popupRowItemIds, popupRowGroupKey));
    }

    private void togglePopupMenu(PopupMenuType type, UiRect anchor, List<PopupOption> options) {
        int anchorContentTop = anchor.y() + pageScrollPx;
        int anchorContentBottom = anchor.bottom() + pageScrollPx;
        if (popupMenuType == type
                && popupAnchorX == anchor.x()
                && popupAnchorContentTopY == anchorContentTop
                && popupAnchorContentBottomY == anchorContentBottom) {
            closePopupMenu();
            return;
        }
        popupMenuType = type;
        popupAnchorX = anchor.x();
        popupAnchorContentTopY = anchorContentTop;
        popupAnchorContentBottomY = anchorContentBottom;
        popupOptions = options;
        popupOptionHitboxes = List.of();
        popupScrollPx = 0;
        popupMaxScrollPx = 0;
        popupScrollDragging = false;
        popupScrollDragOffset = 0;
    }

    private void closePopupMenu() {
        popupMenuType = PopupMenuType.NONE;
        popupAnchorX = 0;
        popupAnchorContentTopY = 0;
        popupAnchorContentBottomY = 0;
        popupOptions = List.of();
        popupOptionHitboxes = List.of();
        popupMenuRect = null;
        popupContentRect = null;
        popupScrollTrackHitbox = null;
        popupScrollThumbHitbox = null;
        popupScrollPx = 0;
        popupMaxScrollPx = 0;
        popupScrollDragging = false;
        popupScrollDragOffset = 0;
        popupRowItemId = null;
        popupRowItemIds = List.of();
        popupRowGroupKey = null;
    }

    private boolean handlePopupMouseClicked(double mouseX, double mouseY, int button) {
        if (popupMenuType == PopupMenuType.NONE || popupMenuRect == null) {
            return false;
        }

        if (button == 0 && popupScrollThumbHitbox != null && popupScrollThumbHitbox.contains(mouseX, mouseY)) {
            popupScrollDragging = true;
            popupScrollDragOffset = (int) mouseY - popupScrollThumbHitbox.y();
            return true;
        }
        if (button == 0 && popupScrollTrackHitbox != null && popupScrollTrackHitbox.contains(mouseX, mouseY)) {
            jumpPopupScrollTo((int) mouseY);
            return true;
        }

        if (!popupMenuRect.contains(mouseX, mouseY)) {
            closePopupMenu();
            return false;
        }

        if (button != 0) {
            return true;
        }

        PopupOption option = popupOptionAt(mouseX, mouseY);
        if (option != null) {
            applyPopupOption(option);
            return true;
        }
        return true;
    }

    private PopupOption popupOptionAt(double mouseX, double mouseY) {
        if (popupContentRect == null || !popupContentRect.contains(mouseX, mouseY)) {
            return null;
        }
        int localY = (int) mouseY - popupContentRect.y() + popupScrollPx;
        int index = localY / POPUP_ROW_HEIGHT;
        if (index < 0 || index >= popupOptions.size()) {
            return null;
        }
        return popupOptions.get(index);
    }

    private void applyPopupOption(PopupOption option) {
        if (option.special() == PopupSpecial.OPEN_CREATE_GROUP) {
            openGroupInput(GroupInputMode.CREATE, popupRowItemIds, "", "");
            return;
        }
        if (option.special() == PopupSpecial.OPEN_RENAME_GROUP) {
            String groupKey = option.itemId() == null ? "" : option.itemId();
            openGroupInput(GroupInputMode.RENAME, List.of(), groupKey, groupDisplayNameByKey(groupKey));
            return;
        }

        sendUiAction(option.actionType(), option.itemId(), option.itemIds(), option.actionValue());
        closePopupMenu();
    }

    /** 构建分组筛选弹出菜单选项 */
    private List<PopupOption> buildGroupFilterOptions() {
        if (viewModel == null) {
            return List.of();
        }
        List<PopupOption> options = new ArrayList<>();
        String current = viewModel.uiState().groupFilterKey();
        options.add(PopupOption.action(
                selectedMarker(PlayerUiPrefsSavedData.GROUP_FILTER_ALL.equals(current),
                        Component.translatable("screen.resourceobserver.overview.filter.group.all").getString()),
                UiActionType.SET_GROUP_FILTER_KEY,
                "",
                PlayerUiPrefsSavedData.GROUP_FILTER_ALL
        ));

        for (OverviewViewModel.GroupOption group : viewModel.uiState().groups()) {
            options.add(PopupOption.action(
                    selectedMarker(group.key().equals(current), group.displayName()),
                    UiActionType.SET_GROUP_FILTER_KEY,
                    "",
                    group.key()
            ));
        }
        return options;
    }

    /** 构建排序模式弹出菜单选项 */
    /** 构建状态筛选弹出菜单选项 */
    private List<PopupOption> buildStatusOptions() {
        if (viewModel == null) {
            return List.of();
        }
        List<PopupOption> options = new ArrayList<>();
        TableStatusFilter current = viewModel.uiState().statusFilter();
        for (TableStatusFilter filter : TableStatusFilter.values()) {
            options.add(PopupOption.action(
                    selectedMarker(filter == current, Component.translatable(filter.translationKey()).getString()),
                    UiActionType.SET_STATUS_FILTER,
                    "",
                    filter.key()
            ));
        }
        return options;
    }

    /** 构建行右键分组菜单选项（包含分组分配、创建、重命名、删除） */
    private List<PopupOption> buildRowGroupOptions(List<String> itemIds, String groupKey) {
        if (viewModel == null || itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        String normalizedGroupKey = groupKey == null || groupKey.isBlank()
                ? PlayerUiPrefsSavedData.GROUP_UNGROUPED
                : groupKey;
        String primaryItemId = itemIds.get(0);
        List<PopupOption> options = new ArrayList<>();
        for (OverviewViewModel.GroupOption group : viewModel.uiState().groups()) {
            boolean selected = group.key().equals(normalizedGroupKey);
            if (PlayerUiPrefsSavedData.GROUP_UNGROUPED.equals(group.key())) {
                options.add(PopupOption.action(
                        selectedMarker(selected,
                                Component.translatable("screen.resourceobserver.overview.group.menu.clear").getString()),
                        UiActionType.CLEAR_ITEM_GROUP,
                        primaryItemId,
                        itemIds,
                        ""
                ));
                continue;
            }
            options.add(PopupOption.action(
                    selectedMarker(selected,
                            Component.translatable("screen.resourceobserver.overview.group.menu.assign", group.displayName()).getString()),
                    UiActionType.ASSIGN_ITEM_GROUP,
                    primaryItemId,
                    itemIds,
                    group.key()
            ));
        }

        options.add(PopupOption.special(
                Component.translatable("screen.resourceobserver.overview.group.menu.create").getString(),
                PopupSpecial.OPEN_CREATE_GROUP
        ));

        OverviewViewModel.GroupOption currentGroup = groupByKey(normalizedGroupKey);
        if (currentGroup != null && !currentGroup.systemGroup()) {
            options.add(PopupOption.special(
                    Component.translatable("screen.resourceobserver.overview.group.menu.rename").getString(),
                    PopupSpecial.OPEN_RENAME_GROUP,
                    currentGroup.key()
            ));
            options.add(PopupOption.action(
                    Component.translatable("screen.resourceobserver.overview.group.menu.delete").getString(),
                    UiActionType.DELETE_GROUP,
                    currentGroup.key(),
                    "",
                    true
            ));
        }
        return options;
    }

    private String selectedMarker(boolean selected, String label) {
        return (selected ? "* " : "  ") + label;
    }

    private String withSelectedMarker(boolean selected, String label) {
        return (selected ? "• " : "  ") + label;
    }

    private OverviewViewModel.GroupOption groupByKey(String key) {
        if (viewModel == null || key == null) {
            return null;
        }
        for (OverviewViewModel.GroupOption group : viewModel.uiState().groups()) {
            if (key.equals(group.key())) {
                return group;
            }
        }
        return null;
    }

    private String groupDisplayNameByKey(String key) {
        OverviewViewModel.GroupOption group = groupByKey(key);
        return group == null ? "" : group.displayName();
    }

    /** 渲染弹出菜单（带滚动支持） */
    private void renderPopupMenu(GuiGraphics gfx, int mouseX, int mouseY) {
        popupOptionHitboxes = List.of();
        popupMenuRect = null;
        popupContentRect = null;
        popupScrollTrackHitbox = null;
        popupScrollThumbHitbox = null;

        if (popupMenuType == PopupMenuType.NONE || popupOptions.isEmpty() || layoutState == null) {
            return;
        }

        int anchorTopY = popupAnchorContentTopY - pageScrollPx;
        int anchorBottomY = popupAnchorContentBottomY - pageScrollPx;
        UiRect viewport = layoutState.scrollViewport();
        if (anchorBottomY < viewport.y() - 12 || anchorTopY > viewport.bottom() + 12) {
            closePopupMenu();
            return;
        }

        gfx.pose().pushPose();
        gfx.pose().translate(0.0f, 0.0f, 280.0f);

        int menuWidth = 120;
        for (PopupOption option : popupOptions) {
            menuWidth = Math.max(menuWidth, font.width(option.label()) + 12);
        }
        int maxWidth = Math.max(140, Math.min(320, layoutState.panel().width() - 20));
        menuWidth = Math.min(menuWidth, maxWidth);

        int fullContentHeight = popupOptions.size() * POPUP_ROW_HEIGHT;
        int desiredHeight = Math.min(POPUP_MAX_HEIGHT, fullContentHeight + 4);

        int x = Math.max(4, Math.min(width - menuWidth - 4, popupAnchorX));
        int panelMinY = layoutState.panel().y() + 2;
        int panelMaxY = layoutState.panel().bottom() - 2;
        int y = anchorBottomY + 1;
        int belowSpace = panelMaxY - y;
        int aboveSpace = anchorTopY - panelMinY;
        int menuHeight = desiredHeight;
        if (menuHeight > belowSpace && aboveSpace >= belowSpace) {
            menuHeight = Math.max(48, Math.min(desiredHeight, aboveSpace));
            y = anchorTopY - menuHeight - 1;
        } else if (menuHeight > belowSpace) {
            menuHeight = Math.max(48, belowSpace);
        }
        if (y < panelMinY) {
            y = panelMinY;
        }

        popupMenuRect = new UiRect(x, y, menuWidth, menuHeight);
        RenderUtils.fillPanel(gfx, popupMenuRect, 0xF0142236, 0xFF2A4F76);

        UiRect inner = popupMenuRect.inset(2);
        boolean needsScroll = fullContentHeight > inner.height();
        int contentWidth = needsScroll ? Math.max(24, inner.width() - 8) : inner.width();
        popupContentRect = new UiRect(inner.x(), inner.y(), contentWidth, inner.height());
        popupMaxScrollPx = Math.max(0, fullContentHeight - popupContentRect.height());
        popupScrollPx = Math.max(0, Math.min(popupMaxScrollPx, popupScrollPx));

        if (needsScroll) {
            popupScrollTrackHitbox = new UiRect(inner.right() - 6, inner.y(), 6, inner.height());
            gfx.fill(
                    popupScrollTrackHitbox.x(),
                    popupScrollTrackHitbox.y(),
                    popupScrollTrackHitbox.right(),
                    popupScrollTrackHitbox.bottom(),
                    0x55334455
            );

            int thumbH = Math.max(16, Math.round((popupContentRect.height() / (float) fullContentHeight) * popupScrollTrackHitbox.height()));
            int travel = Math.max(1, popupScrollTrackHitbox.height() - thumbH);
            int thumbY = popupScrollTrackHitbox.y() + Math.round((popupScrollPx / (float) Math.max(1, popupMaxScrollPx)) * travel);
            popupScrollThumbHitbox = new UiRect(popupScrollTrackHitbox.x(), thumbY, popupScrollTrackHitbox.width(), thumbH);
            gfx.fill(
                    popupScrollThumbHitbox.x(),
                    popupScrollThumbHitbox.y(),
                    popupScrollThumbHitbox.right(),
                    popupScrollThumbHitbox.bottom(),
                    UiThemeTokens.CYAN
            );
        }

        gfx.enableScissor(
                popupContentRect.x(),
                popupContentRect.y(),
                popupContentRect.right(),
                popupContentRect.bottom()
        );

        List<PopupOptionHitbox> hitboxes = new ArrayList<>();
        for (int i = 0; i < popupOptions.size(); i++) {
            PopupOption option = popupOptions.get(i);
            int rowY = popupContentRect.y() - popupScrollPx + i * POPUP_ROW_HEIGHT;
            if (rowY + POPUP_ROW_HEIGHT <= popupContentRect.y() || rowY >= popupContentRect.bottom()) {
                continue;
            }

            UiRect rowRect = new UiRect(popupContentRect.x(), rowY, popupContentRect.width(), POPUP_ROW_HEIGHT);
            boolean hovered = rowRect.contains(mouseX, mouseY);
            int rowBg = hovered ? 0xAA21456A : 0x00000000;
            if (rowBg != 0) {
                gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), rowBg);
            }

            int textColor = option.destructive() ? UiThemeTokens.ROSE : UiThemeTokens.TEXT;
            String line = RenderUtils.ellipsis(font, option.label(), rowRect.width() - 8);
            gfx.drawString(font, line, rowRect.x() + 4, rowRect.y() + 3, textColor);
            hitboxes.add(new PopupOptionHitbox(rowRect, i));
        }
        popupOptionHitboxes = hitboxes;
        gfx.disableScissor();
        gfx.pose().popPose();
    }

    /** 打开分组输入面板 */
    private void openGroupInput(GroupInputMode mode, List<String> itemIds, String targetGroupKey, String initialName) {
        groupInputMode = mode;
        groupInputItemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
        groupInputItemId = groupInputItemIds.isEmpty() ? "" : groupInputItemIds.get(0);
        groupInputTargetGroupKey = targetGroupKey == null ? "" : targetGroupKey;
        closePopupMenu();
        setGroupInputVisible(true);

        if (groupNameEdit != null) {
            groupNameEdit.setValue(initialName == null ? "" : initialName);
            groupNameEdit.setFocused(true);
            setFocused(groupNameEdit);
        }
    }

    private void closeGroupInput() {
        groupInputMode = GroupInputMode.NONE;
        groupInputItemId = "";
        groupInputItemIds = List.of();
        groupInputTargetGroupKey = "";
        setGroupInputVisible(false);
    }

    private void submitGroupInput() {
        if (groupInputMode == GroupInputMode.NONE || groupNameEdit == null) {
            return;
        }
        String rawName = groupNameEdit.getValue() == null ? "" : groupNameEdit.getValue().trim();
        if (groupInputMode == GroupInputMode.CREATE) {
            sendUiAction(UiActionType.CREATE_GROUP, groupInputItemId, groupInputItemIds, rawName);
        } else if (groupInputMode == GroupInputMode.RENAME) {
            sendUiAction(UiActionType.RENAME_GROUP, groupInputTargetGroupKey, rawName);
        }
        closeGroupInput();
    }

    private void renderGroupInputOverlay(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (groupInputPanel == null) {
            return;
        }
        gfx.fill(layoutState.panel().x(), layoutState.panel().y(), layoutState.panel().right(), layoutState.panel().bottom(), 0xAA04070E);
        RenderUtils.fillPanel(gfx, groupInputPanel, UiThemeTokens.PANEL_BG, UiThemeTokens.SECTION_BORDER);

        String titleKey = groupInputMode == GroupInputMode.RENAME
                ? "screen.resourceobserver.overview.group.input.rename_title"
                : "screen.resourceobserver.overview.group.input.create_title";
        gfx.drawString(font, Component.translatable(titleKey), groupInputPanel.x() + 12, groupInputPanel.y() + 8, UiThemeTokens.TITLE);
        gfx.drawString(
                font,
                Component.translatable("screen.resourceobserver.overview.group.input.name_label"),
                groupInputPanel.x() + 12,
                groupInputPanel.y() + 19,
                UiThemeTokens.TEXT_MUTED
        );

        groupNameEdit.render(gfx, mouseX, mouseY, partialTick);
        groupInputConfirmButton.render(gfx, mouseX, mouseY, partialTick);
        groupInputCancelButton.render(gfx, mouseX, mouseY, partialTick);
    }

    private void clampPageScroll() {
        if (layoutState == null || viewModel == null) {
            pageScrollPx = 0;
            maxPageScrollPx = 0;
            return;
        }
        ContentLayout content = computeContentLayout(0);
        maxPageScrollPx = Math.max(0, content.totalHeight() - layoutState.scrollViewport().height());
        pageScrollPx = Math.max(0, Math.min(maxPageScrollPx, pageScrollPx));
    }

    private void jumpPageScrollTo(int mouseY) {
        if (pageScrollTrackHitbox == null || pageScrollThumbHitbox == null || maxPageScrollPx <= 0) {
            return;
        }
        int targetThumbTop = mouseY - pageScrollTrackHitbox.y() - pageScrollThumbHitbox.height() / 2;
        setPageScrollFromThumbTop(targetThumbTop);
    }

    private void dragPageScrollTo(int targetThumbTop) {
        setPageScrollFromThumbTop(targetThumbTop - pageScrollTrackHitbox.y());
    }

    private void setPageScrollFromThumbTop(int thumbTopRelative) {
        if (pageScrollTrackHitbox == null || pageScrollThumbHitbox == null || maxPageScrollPx <= 0) {
            pageScrollPx = 0;
            return;
        }
        int travel = pageScrollTrackHitbox.height() - pageScrollThumbHitbox.height();
        if (travel <= 0) {
            pageScrollPx = 0;
            return;
        }
        int clamped = Math.max(0, Math.min(travel, thumbTopRelative));
        float ratio = clamped / (float) travel;
        pageScrollPx = Math.round(ratio * maxPageScrollPx);
    }

    private void jumpPopupScrollTo(int mouseY) {
        if (popupScrollTrackHitbox == null || popupScrollThumbHitbox == null || popupMaxScrollPx <= 0) {
            return;
        }
        int targetThumbTop = mouseY - popupScrollTrackHitbox.y() - popupScrollThumbHitbox.height() / 2;
        setPopupScrollFromThumbTop(targetThumbTop);
    }

    private void dragPopupScrollTo(int targetThumbTop) {
        if (popupScrollTrackHitbox == null) {
            return;
        }
        setPopupScrollFromThumbTop(targetThumbTop - popupScrollTrackHitbox.y());
    }

    private void setPopupScrollFromThumbTop(int thumbTopRelative) {
        if (popupScrollTrackHitbox == null || popupScrollThumbHitbox == null || popupMaxScrollPx <= 0) {
            popupScrollPx = 0;
            return;
        }
        int travel = popupScrollTrackHitbox.height() - popupScrollThumbHitbox.height();
        if (travel <= 0) {
            popupScrollPx = 0;
            return;
        }
        int clamped = Math.max(0, Math.min(travel, thumbTopRelative));
        float ratio = clamped / (float) travel;
        popupScrollPx = Math.round(ratio * popupMaxScrollPx);
    }

    private void markFilterPressed(TableRenderer.MenuType menuType) {
        long until = Util.getMillis() + 120L;
        switch (menuType) {
            case GROUP -> groupButtonPressedUntilMs = until;
            case STATUS -> statusButtonPressedUntilMs = until;
        }
    }

    private boolean isFilterPressed(long untilMs) {
        return Util.getMillis() < untilMs;
    }

    private void updateButtonStates() {
        if (sizeModeButton != null) {
            sizeModeButton.setMessage(Component.literal(sizeMode.label));
            sizeModeButton.active = groupInputMode == GroupInputMode.NONE;
        }
        if (closeButton != null) {
            closeButton.active = true;
        }
    }

    /**
     * 可滚动内容布局 —— 各区段的矩形位置。
     */
    private record ContentLayout(
            UiRect headerArea,      // 页眉区域
            UiRect kpiArea,         // KPI 区域
            UiRect chartArea,       // 图表区域
            UiRect watchlistArea,   // 关注列表区域
            UiRect tableArea,       // 表格区域
            int totalHeight,        // 总内容高度
            boolean watchlistVisible // 关注列表是否显示
    ) {
    }

    /**
     * 弹出菜单选项。
     * 可以是普通动作选项（发送 UI 操作到服务端）或特殊选项（打开输入面板）。
     */
    private record PopupOption(
            String label,
            UiActionType actionType,
            String itemId,
            List<String> itemIds,
            String actionValue,
            PopupSpecial special,
            boolean destructive
    ) {
        private static PopupOption action(String label, UiActionType actionType, String itemId, String actionValue) {
            return new PopupOption(label, actionType, itemId, List.of(), actionValue, PopupSpecial.NONE, false);
        }

        private static PopupOption action(String label, UiActionType actionType, String itemId, List<String> itemIds, String actionValue) {
            return new PopupOption(label, actionType, itemId, itemIds == null ? List.of() : List.copyOf(itemIds), actionValue, PopupSpecial.NONE, false);
        }

        private static PopupOption action(String label, UiActionType actionType, String itemId, String actionValue, boolean destructive) {
            return new PopupOption(label, actionType, itemId, List.of(), actionValue, PopupSpecial.NONE, destructive);
        }

        private static PopupOption special(String label, PopupSpecial special) {
            return new PopupOption(label, UiActionType.TOGGLE_WATCH, "", List.of(), "", special, false);
        }

        private static PopupOption special(String label, PopupSpecial special, String itemId) {
            return new PopupOption(label, UiActionType.TOGGLE_WATCH, itemId, List.of(), "", special, false);
        }
    }

    /** 弹出菜单选项热区 */
    private record PopupOptionHitbox(UiRect rect, int optionIndex) {
    }
}

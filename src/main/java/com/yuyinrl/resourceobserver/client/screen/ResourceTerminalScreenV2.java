package com.yuyinrl.resourceobserver.client.screen;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.client.screen.dialog.DialogHost;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.v2.LatestSnapshotProvider;
import com.yuyinrl.resourceobserver.client.screen.v2.overview.OverviewPanel;
import com.yuyinrl.resourceobserver.client.screen.v2.power.PowerPanel;
import com.yuyinrl.resourceobserver.client.screen.v2.storage.StoragePanel;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.Tabs;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModelMapper;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModelMapper;
import com.yuyinrl.resourceobserver.client.ui.StorageNetworkViewModelMapper;
import com.yuyinrl.resourceobserver.client.ui.snapshot.ClientOverviewLocalizer;
import com.yuyinrl.resourceobserver.network.ChartScope;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import com.yuyinrl.resourceobserver.network.CraftingPlanResultPayload;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;
import com.yuyinrl.resourceobserver.network.ObserverRefreshRequestPayload;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshotBuilder;
import com.yuyinrl.resourceobserver.service.snapshot.power.PowerSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.power.PowerSnapshotBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Resource Terminal V2 —— 新 Vanilla UI 框架的终端 Screen 骨架。
 *
 * <p>含三个 Panel（Overview / Storage / Power）+ Tabs 切换 + Dialog 模态浮层。</p>
 */
public class ResourceTerminalScreenV2 extends Screen {

    /** 与 ModernUI {@code ResourceTerminalFragment.PANEL_SCALE} 保持一致：占屏 90%。 */
    private static final float PANEL_SCALE = 0.90F;
    /** 防止超大屏（4K+）下面板被拉得过宽过高的硬上限。 */
    private static final int CONTENT_HARD_MAX_WIDTH = 720;
    private static final int CONTENT_HARD_MAX_HEIGHT = 420;
    private static final int CONTENT_MIN_WIDTH = 320;
    private static final int CONTENT_MIN_HEIGHT = 200;

    private Tabs tabs;
    private Rect contentRect = Rect.EMPTY;
    private DialogHost dialogHost;

    /**
     * G2.4 全局回调：当客户端收到 {@link CraftingPlanResultPayload} 时若 V2 终端处于打开状态，
     * 由 {@code ClientPayloadHandler} 路由进来。订阅者由 {@code CraftOrderDialog#onConfirm} 临时设置。
     */
    public static volatile Consumer<CraftingPlanResultPayload> v2PlanCallback;

    /** 当前活跃 V2 终端实例（用于服务端回包路由）。 */
    private static volatile ResourceTerminalScreenV2 activeInstance;

    /** V2 定期刷新间隔，单位 tick（20 tick = 1 秒）。 */
    private static final int REFRESH_INTERVAL_TICKS = 20;

    private int refreshTick = 0;

    /**
     * 当 V2 终端活跃时，将 payload 写入 {@link LatestSnapshotProvider}，让所有 panel 自然刷新。
     *
     * @return true 表示 V2 接管，调用方不应再打开 ModernUI
     */
    public static boolean applyPayloadIfActive(ObserverDataPayload payload) {
        ResourceTerminalScreenV2 inst = activeInstance;
        Minecraft mc = Minecraft.getInstance();
        if (inst == null || mc.screen != inst) {
            return false;
        }
        publishSnapshot(payload);
        return true;
    }

    private static void publishSnapshot(ObserverDataPayload payload) {
        Map<String, double[]> bufferEma = LatestSnapshotProvider.copyBufferEma();
        LatestSnapshotProvider.set(payload, bufferEma);
        if (payload != null) {
            OverviewSnapshot ov = OverviewSnapshotBuilder.fromPayload(payload, ClientOverviewLocalizer.INSTANCE);
            LatestSnapshotProvider.setOverviewSnapshot(ov);
            LatestSnapshotProvider.setOverviewViewModel(OverviewViewModelMapper.fromPayload(payload, ""));
            LatestSnapshotProvider.setStorageNetworkViewModel(StorageNetworkViewModelMapper.fromPayload(
                    payload, null, false, bufferEma, ""));
            PowerSnapshot pw = PowerSnapshotBuilder.fromPayload(payload);
            LatestSnapshotProvider.setPowerSnapshot(pw);
            LatestSnapshotProvider.setPowerNetworkViewModel(PowerNetworkViewModelMapper.fromPayload(payload));
        } else {
            LatestSnapshotProvider.setOverviewSnapshot(null);
            LatestSnapshotProvider.setOverviewViewModel(null);
            LatestSnapshotProvider.setStorageNetworkViewModel(null);
            LatestSnapshotProvider.setPowerSnapshot(null);
            LatestSnapshotProvider.setPowerNetworkViewModel(null);
        }
    }

    public ResourceTerminalScreenV2() {
        super(Component.translatable("screen.resourceobserver.terminal_title"));
    }

    @Override
    protected void init() {
        super.init();
        activeInstance = this;

        int w = Math.max(CONTENT_MIN_WIDTH,
                Math.min(CONTENT_HARD_MAX_WIDTH, Math.round(this.width * PANEL_SCALE)));
        int h = Math.max(CONTENT_MIN_HEIGHT,
                Math.min(CONTENT_HARD_MAX_HEIGHT, Math.round(this.height * PANEL_SCALE)));
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        this.contentRect = new Rect(x, y, w, h);

        // 与 ModernUI 对齐：外边距≈dp(10)，title 上方留 14px，下方 sectionGap≈dp(6)
        final int outerMargin = 10;
        final int titleH = 14;
        final int sectionGap = 6;
        Rect inner = contentRect.shrink(outerMargin);
        Rect tabsRect = new Rect(
                inner.x(),
                inner.y() + titleH + sectionGap,
                inner.width(),
                Math.max(0, inner.height() - titleH - sectionGap));

        tabs = new Tabs(VanillaTheme.TAB_HEIGHT)
                .addTab(Component.translatable("screen.resourceobserver.storage.tab.overview"),
                        new OverviewPanel())
                .addTab(Component.translatable("screen.resourceobserver.storage.tab.storage_network"),
                        new StoragePanel())
                .addTab(Component.translatable("screen.resourceobserver.storage.tab.power_network"),
                        new PowerPanel());
        tabs.setBounds(tabsRect);

        addRenderableWidget(tabs);

        // Dialog 模态浮层管理器（每次 init 重建，install 为当前活跃 host）
        if (dialogHost != null) {
            dialogHost.uninstall();
        }
        dialogHost = new DialogHost(this);
        dialogHost.install();

        // 打开时立即向服务端请求最新数据
        refreshTick = REFRESH_INTERVAL_TICKS - 1; // 下一 tick 即触发首次刷新
    }

    @Override
    public void tick() {
        super.tick();
        // 传播 tick 到当前活跃 tab 内容（用于 LatestSnapshotProvider 版本检测）
        if (tabs != null) {
            tabs.tick();
        }
        if (dialogHost != null) {
            dialogHost.tick();
        }
        // 定期向服务端请求最新数据
        refreshTick++;
        if (refreshTick >= REFRESH_INTERVAL_TICKS) {
            refreshTick = 0;
            sendRefreshRequest();
        }
    }

    /** 向服务端发送数据刷新请求（若 LatestSnapshotProvider 已有载荷可用）。 */
    private void sendRefreshRequest() {
        ObserverDataPayload payload = LatestSnapshotProvider.getPayload();
        if (payload == null) return;
        try {
            PacketDistributor.sendToServer(new ObserverRefreshRequestPayload(
                    payload.observerPos(), ChartWindow.DAY_24H_5M, ChartScope.GLOBAL, ""));
        } catch (Exception e) {
            ResourceObserverMod.LOGGER.debug("[V2] 刷新请求发送失败", e);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 让 vanilla 先完成 blur 后处理（仅作用于游戏世界画面），再叠一层 dim
        // 让背景更暗，最后用不透明色填充主面板区域 —— 此时 blur 只会出现在
        // 主面板"外侧"（即外框之外的游戏世界），主面板与外框本身保持清晰。
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, this.width, this.height, VanillaTheme.COLOR_BG_DIM);
        g.fill(contentRect.x(), contentRect.y(),
                contentRect.right(), contentRect.bottom(),
                VanillaTheme.COLOR_PANEL);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        drawCrispOuterFrame(g);
        g.drawCenteredString(this.font, this.title,
                contentRect.x() + contentRect.width() / 2,
                contentRect.y() + 5,
                VanillaTheme.COLOR_TEXT_PRIMARY);
        g.flush();
        if (dialogHost != null && dialogHost.isAnyOpen()) {
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 400.0F);
            dialogHost.render(g, mouseX, mouseY, partialTick);
            g.flush();
            g.pose().popPose();
        }
    }

    private void drawCrispOuterFrame(GuiGraphics g) {
        g.flush();
        int x1 = contentRect.x();
        int y1 = contentRect.y();
        int x2 = contentRect.right();
        int y2 = contentRect.bottom();
        int outer = VanillaTheme.COLOR_BUTTON_BORDER;

        g.fill(x1, y1, x2, y1 + 1, outer);
        g.fill(x1, y2 - 1, x2, y2, outer);
        g.fill(x1, y1, x1 + 1, y2, outer);
        g.fill(x2 - 1, y1, x2, y2, outer);
        g.flush();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (dialogHost != null && dialogHost.isAnyOpen()) {
            return dialogHost.mouseClicked(mx, my, btn);
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (dialogHost != null && dialogHost.isAnyOpen()) {
            return dialogHost.mouseReleased(mx, my, btn);
        }
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dialogHost != null && dialogHost.isAnyOpen()) {
            return dialogHost.mouseDragged(mx, my, btn, dx, dy);
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sdx, double sdy) {
        if (dialogHost != null && dialogHost.isAnyOpen()) {
            return dialogHost.mouseScrolled(mx, my, sdx, sdy);
        }
        return super.mouseScrolled(mx, my, sdx, sdy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dialogHost != null && dialogHost.isAnyOpen()) {
            return dialogHost.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (dialogHost != null && dialogHost.isAnyOpen()) {
            return dialogHost.charTyped(c, modifiers);
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public void removed() {
        if (activeInstance == this) {
            activeInstance = null;
        }
        if (dialogHost != null) {
            dialogHost.uninstall();
            dialogHost = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}



package com.yuyinrl.resourceobserver.client.screen.v2.power;

import com.yuyinrl.resourceobserver.client.screen.dialog.Dialog;
import com.yuyinrl.resourceobserver.client.screen.dialog.DialogHost;
import com.yuyinrl.resourceobserver.client.screen.dialog.InterfaceListDialog;
import com.yuyinrl.resourceobserver.client.screen.dialog.KpiDetailDialog;
import com.yuyinrl.resourceobserver.client.screen.dialog.builder.KpiDetailLines;
import com.yuyinrl.resourceobserver.client.screen.theme.NinePatch;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.v2.LatestSnapshotProvider;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.ButtonWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.Column;
import com.yuyinrl.resourceobserver.client.screen.widget.DonutChart;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.Row;
import com.yuyinrl.resourceobserver.client.screen.widget.ScrollView;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.format.FormatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vanilla V2 Power 页，直接消费 PowerNetworkViewModel。
 */
public class PowerPanel extends BaseWidget {

    private static final int HEADER_H = 22;
    private static final int KPI_H = 36;
    private static final int LOAD_H = 28;
    private static final int SECTION_H = 14;
    private static final int ROW_H = 24;
    /** 负载条右侧 donut 边长（与 LOAD_H 等宽，正方形）。 */
    private static final int DONUT_SIZE = 48;

    private final PowerKpiCard kpiA = new PowerKpiCard();
    private final PowerKpiCard kpiB = new PowerKpiCard();
    private final PowerKpiCard kpiC = new PowerKpiCard();
    private final PowerKpiCard kpiD = new PowerKpiCard();
    private final PowerLoadSegmentBar loadBar = new PowerLoadSegmentBar();
    private final DonutChart loadDonut = new DonutChart();
    private final ButtonWidget donutToggle = new ButtonWidget(Component.empty(), b -> togglePowerDonut());
    private final ScrollView scrollView = new ScrollView();
    private final ScrollView pageScroll = new ScrollView();
    private final PowerContent pageContent = new PowerContent();

    private PowerNetworkViewModel viewModel;
    private long providerVersion = -1L;
    private int rowsContentHeight;
    private int kpiSectionH;
    private int columnsH;
    private int leftColW;
    private int rightColW;
    private int chartSectionH;

    @Override
    protected void onBoundsChanged() {
        layoutChildren();
    }

    @Override
    public void tick() {
        refreshSnapshotIfNeeded();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        refreshSnapshotIfNeeded();
        Rect b = bounds().shrink(VanillaTheme.SPACING_S);
        if (viewModel == null || viewModel.devices().isEmpty()) {
            renderEmpty(g, b, "暂无 Flux 网络数据");
            return;
        }

        pageScroll.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return pageScroll.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isMouseOver(mouseX, mouseY)) return false;
        return pageScroll.mouseClicked(mouseX, mouseY, button);
    }

    /** 右键 KPI 卡片 C（外储/Stored）打开接口列表弹窗。 */
    private boolean tryOpenInterfaceList(double mouseX, double mouseY) {
        if (!kpiC.isMouseOver(mouseX, mouseY)) return false;
        DialogHost host = DialogHost.current();
        if (host == null) return false;
        var payload = LatestSnapshotProvider.getPayload();
        if (payload == null) return false;
        host.open(new InterfaceListDialog(payload.observerPos()));
        return true;
    }

    private boolean tryOpenKpiDetail(double mouseX, double mouseY) {
        int idx = -1;
        if (kpiA.isMouseOver(mouseX, mouseY)) idx = 0;
        else if (kpiB.isMouseOver(mouseX, mouseY)) idx = 1;
        else if (kpiC.isMouseOver(mouseX, mouseY)) idx = 2;
        else if (kpiD.isMouseOver(mouseX, mouseY)) idx = 3;
        if (idx < 0) return false;
        DialogHost host = DialogHost.current();
        if (host == null) return false;
        final int kpiIndex = idx;
        Dialog.Size size = new Dialog.Size(
                VanillaTheme.DIALOG_KPI_SMALL_W, VanillaTheme.DIALOG_KPI_SMALL_H);
        host.open(new KpiDetailDialog(
                KpiDetailLines.powerTitle(LatestSnapshotProvider.getPowerNetworkViewModel(), kpiIndex),
                size,
                () -> KpiDetailLines.forPower(
                        LatestSnapshotProvider.getPowerNetworkViewModel(), kpiIndex)));
        return true;
    }

    private void refreshSnapshotIfNeeded() {
        LatestSnapshotProvider.State state = LatestSnapshotProvider.get();
        if (providerVersion == state.version()) return;
        providerVersion = state.version();
        viewModel = LatestSnapshotProvider.getPowerNetworkViewModel();
        applyViewModel();
    }

    private void applyViewModel() {
        if (viewModel == null) {
            rebuildRows(List.of(), List.of(), List.of());
            applyDonut();
            return;
        }
        applyKpis(viewModel.kpiCards());
        loadBar.setSegments(viewModel.loadSegments());
        applyDonut();
        rebuildRows(viewModel.overloadAlerts(), viewModel.devices(), viewModel.consumers());
        layoutChildren();
    }

    /** 按 ModernUI 规则填充甜甜圈：优先用具体用电器，缺失时回退到负载分类。 */
    private void applyDonut() {
        updateDonutToggleLabel();
        if (viewModel == null) {
            loadDonut.setData(List.of(), 100.0, "", "", VanillaTheme.COLOR_TEXT_PRIMARY);
            return;
        }

        List<DonutChart.Segment> donutSegs = new ArrayList<>();
        List<PowerNetworkViewModel.ConsumerEntry> consumers = viewModel.consumers();
        if (consumers != null && !consumers.isEmpty()) {
            for (PowerNetworkViewModel.ConsumerEntry consumer : consumers) {
                String label = consumer.count() > 1
                        ? safeText(consumer.deviceName()) + " x" + consumer.count()
                        : safeText(consumer.deviceName());
                donutSegs.add(new DonutChart.Segment(
                        label, consumer.percentage(), consumer.consumptionPerTick(), consumer.color()));
            }
        } else if (viewModel.loadSegments() != null) {
            for (PowerNetworkViewModel.LoadSegment segment : viewModel.loadSegments()) {
                String label = safeText(segment.displayName());
                long value = Math.round(segment.percentage() / 100.0 * Math.max(0L, viewModel.totalOutputPerTick()));
                donutSegs.add(new DonutChart.Segment(label, segment.percentage(), value, segment.color()));
            }
        }

        double headroom = computeHeadroomPercent();
        boolean usedOnly = LatestSnapshotProvider.isPowerDonutUsedOnly();
        double effectiveHeadroom = usedOnly ? 0.0 : headroom;
        double utilization = Math.max(0.0, 100.0 - headroom);
        String centerText = String.format(Locale.ROOT, "%.1f%%", usedOnly ? utilization : headroom);
        String centerLabel = usedOnly
                ? Component.translatable("screen.resourceobserver.power.donut.center_utilization").getString()
                : Component.translatable("screen.resourceobserver.power.donut.center_headroom").getString();
        loadDonut.setData(donutSegs, effectiveHeadroom, centerText, centerLabel,
                VanillaTheme.COLOR_TEXT_PRIMARY);
    }

    private double computeHeadroomPercent() {
        PowerNetworkViewModel.OverloadInfo info = viewModel.overloadInfo();
        if (info != null) {
            return Math.max(0.0, info.headroomPercent());
        }
        long input = Math.max(0L, viewModel.totalInputPerTick());
        long output = Math.max(0L, viewModel.totalOutputPerTick());
        if (input <= 0L) {
            return 0.0;
        }
        return Math.max(0.0, (input - output) * 100.0 / input);
    }

    private void togglePowerDonut() {
        LatestSnapshotProvider.setPowerDonutUsedOnly(!LatestSnapshotProvider.isPowerDonutUsedOnly());
        applyDonut();
    }

    private void updateDonutToggleLabel() {
        boolean usedOnly = LatestSnapshotProvider.isPowerDonutUsedOnly();
        String base = Component.translatable("screen.resourceobserver.power.donut.toggle.used_only").getString();
        donutToggle.setLabel(Component.literal(base + (usedOnly ? " ✓" : " ✗")));
        donutToggle.setTextColor(usedOnly ? VanillaTheme.COLOR_STATUS_INFO : VanillaTheme.COLOR_TEXT_SECONDARY);
    }

    private void applyKpis(List<PowerNetworkViewModel.PowerKpi> kpis) {
        kpiA.setKpi(kpis != null && kpis.size() > 0 ? kpis.get(0) : null);
        kpiB.setKpi(kpis != null && kpis.size() > 1 ? kpis.get(1) : null);
        kpiC.setKpi(kpis != null && kpis.size() > 2 ? kpis.get(2) : null);
        // G7：服务端现在始终下发第 4 张 KPI（external_excluded=0），直接使用
        kpiD.setKpi(kpis != null && kpis.size() > 3 ? kpis.get(3) : null);
    }

    private void rebuildRows(
            List<PowerNetworkViewModel.OverloadAlert> alerts,
            List<PowerNetworkViewModel.DeviceEntry> devices,
            List<PowerNetworkViewModel.ConsumerEntry> consumers
    ) {
        Column rows = new Column(VanillaTheme.SPACING_XS);
        int height = 0;

        if (alerts != null && !alerts.isEmpty()) {
            rows.addFixed(new SectionHeader(
                    "screen.resourceobserver.power.section.overload", HeaderKind.ALERTS), SECTION_H);
            height += SECTION_H + VanillaTheme.SPACING_XS;
            for (PowerNetworkViewModel.OverloadAlert alert : alerts) {
                rows.addFixed(new PowerOverloadAlertRow(alert), ROW_H);
                height += ROW_H + VanillaTheme.SPACING_XS;
            }
        }

        rows.addFixed(new SectionHeader(
                "screen.resourceobserver.power.section.devices", HeaderKind.DEVICES), SECTION_H);
        height += SECTION_H + VanillaTheme.SPACING_XS;
        if (devices != null) {
            for (PowerNetworkViewModel.DeviceEntry device : devices) {
                rows.addFixed(new PowerDeviceRow(device), ROW_H);
                height += ROW_H + VanillaTheme.SPACING_XS;
            }
        }

        if (consumers != null && !consumers.isEmpty()) {
            rows.addFixed(new SectionHeader(
                    "screen.resourceobserver.power.section.consumers", HeaderKind.CONSUMERS), SECTION_H);
            height += SECTION_H + VanillaTheme.SPACING_XS;
            for (PowerNetworkViewModel.ConsumerEntry consumer : consumers) {
                rows.addFixed(new PowerConsumerRow(consumer), ROW_H);
                height += ROW_H + VanillaTheme.SPACING_XS;
            }
        }

        rowsContentHeight = Math.max(0, height - VanillaTheme.SPACING_XS);
        scrollView.setChild(rows, rowsContentHeight);
    }

    private void layoutChildren() {
        Rect b = bounds().shrink(VanillaTheme.SPACING_S);
        kpiSectionH = clamp(Math.round(b.height() * 0.15f), 54, 100);
        int colGap = 6;
        leftColW = Math.max(80, Math.round(b.width() * 0.33f));
        rightColW = Math.max(80, b.width() - leftColW - colGap);
        chartSectionH = clamp(Math.round(b.height() * 0.20f), 60, 140);
        int pieH = 220;
        int rightH = chartSectionH + VanillaTheme.SPACING_S
                + SECTION_H + Math.max(ROW_H, rowsContentHeight);
        columnsH = Math.max(pieH, rightH);
        int pageH = kpiSectionH + VanillaTheme.SPACING_S + columnsH;
        pageScroll.setBounds(b);
        pageScroll.setChild(pageContent, pageH);
    }

    private void layoutPage(Rect b) {
        int y = b.y();

        new Row(8)
                .addFlex(kpiA, 1)
                .addFlex(kpiB, 1)
                .addFlex(kpiC, 1)
                .addFlex(kpiD, 1)
                .setBounds(new Rect(b.x(), y, b.width(), kpiSectionH));
        y += kpiSectionH + VanillaTheme.SPACING_S;

        int colGap = 6;
        int leftX = b.x();
        int rightX = leftX + leftColW + colGap;
        int toggleW = Math.min(Math.max(82, leftColW - 24), 130);
        donutToggle.setBounds(new Rect(leftX + (leftColW - toggleW) / 2, y + 54, toggleW, 18));
        int donutSize = Math.min(130, Math.max(72, leftColW - 24));
        loadDonut.setBounds(new Rect(leftX + (leftColW - donutSize) / 2, y + 78, donutSize, donutSize));
        loadBar.setBounds(new Rect(rightX + 10, y + 34, Math.max(0, rightColW - 20), LOAD_H));
        scrollView.setBounds(new Rect(rightX + 6, y + chartSectionH + VanillaTheme.SPACING_S + SECTION_H,
                Math.max(0, rightColW - 12), Math.max(ROW_H, rowsContentHeight)));
    }

    private void renderHeader(GuiGraphics g, Rect b) {
        var font = Minecraft.getInstance().font;
        String title = viewModel.devices().isEmpty() ? "Flux 网络" : viewModel.devices().get(0).displayName();
        String totals = storedText(viewModel.totalStored(), viewModel.totalCapacity());
        String flow = FormatUtils.formatCompact(viewModel.totalInputPerTick()) + " in · "
                + FormatUtils.formatCompact(viewModel.totalOutputPerTick()) + " out FE/t";
        g.drawString(font, fit(title, b.width() / 2), b.x(), b.y() + 1, VanillaTheme.COLOR_TEXT_PRIMARY, true);
        g.drawString(font, flow, b.x(), b.y() + 12, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        g.drawString(font, totals, b.right() - font.width(totals), b.y() + 6, VanillaTheme.COLOR_STATUS_INFO, true);
    }

    private void renderLoadTitle(GuiGraphics g) {
        Rect b = loadBar.bounds();
        String title = Component.translatable("screen.resourceobserver.power.section.load_chart").getString();
        g.drawString(Minecraft.getInstance().font, title, b.x(), b.y() - 10, VanillaTheme.COLOR_TEXT_SECONDARY, false);
    }

    private static String storedText(long stored, long capacity) {
        return FormatUtils.formatCompact(stored) + " / " + FormatUtils.formatCompact(capacity) + " FE";
    }

    private static String safeText(String text) {
        return text == null ? "" : text;
    }

    private static void renderEmpty(GuiGraphics g, Rect rect, String text) {
        var font = Minecraft.getInstance().font;
        int tx = rect.x() + (rect.width() - font.width(text)) / 2;
        int ty = rect.y() + (rect.height() - VanillaTheme.FONT_HEIGHT) / 2;
        g.drawString(font, text, tx, ty, VanillaTheme.COLOR_TEXT_SECONDARY, true);
    }

    private static String fit(String text, int width) {
        String safe = text == null ? "" : text;
        var font = Minecraft.getInstance().font;
        if (font.width(safe) <= width) return safe;
        return font.plainSubstrByWidth(safe, Math.max(0, width - font.width("…"))) + "…";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private class PowerContent extends BaseWidget {
        @Override
        protected void onBoundsChanged() {
            layoutPage(bounds());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) return;
            Rect b = bounds();
            int y = b.y() + kpiSectionH + VanillaTheme.SPACING_S;
            int colGap = 6;
            Rect left = new Rect(b.x(), y, leftColW, columnsH);
            Rect right = new Rect(left.right() + colGap, y, rightColW, columnsH);
            NinePatch.framedFill(g, left, 0xFF_20_20_20, VanillaTheme.COLOR_DIVIDER);
            NinePatch.framedFill(g, right, 0xFF_20_20_20, VanillaTheme.COLOR_DIVIDER);
            var font = Minecraft.getInstance().font;
            g.drawString(font, "容量余量", left.x() + 6, left.y() + 6,
                    VanillaTheme.COLOR_TEXT_SECONDARY, false);
            renderHeader(g, new Rect(left.x() + 6, left.y() + 18,
                    Math.max(0, left.width() - 12), 34));
            g.drawString(font, Component.translatable("screen.resourceobserver.power.section.grid_load").getString(),
                    right.x() + 6, right.y() + 6, VanillaTheme.COLOR_TEXT_SECONDARY, false);
            kpiA.render(g, mouseX, mouseY, partialTick);
            kpiB.render(g, mouseX, mouseY, partialTick);
            kpiC.render(g, mouseX, mouseY, partialTick);
            kpiD.render(g, mouseX, mouseY, partialTick);
            donutToggle.render(g, mouseX, mouseY, partialTick);
            loadDonut.render(g, mouseX, mouseY, partialTick);
            loadBar.render(g, mouseX, mouseY, partialTick);
            scrollView.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && donutToggle.mouseClicked(mouseX, mouseY, button)) return true;
            if (button == 1 && tryOpenInterfaceList(mouseX, mouseY)) return true;
            if (button == 0 && tryOpenKpiDetail(mouseX, mouseY)) return true;
            return scrollView.mouseClicked(mouseX, mouseY, button);
        }
    }

    private enum HeaderKind {
        ALERTS,
        DEVICES,
        CONSUMERS
    }

    private static class SectionHeader extends BaseWidget {

        private final String titleKey;
        private final HeaderKind kind;

        SectionHeader(String titleKey, HeaderKind kind) {
            this.titleKey = titleKey;
            this.kind = kind;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) return;
            Rect b = bounds();
            NinePatch.framedFill(g, b, 0xFF_22_22_22, VanillaTheme.COLOR_DIVIDER);
            var font = Minecraft.getInstance().font;
            String title = Component.translatable(titleKey).getString();
            g.drawString(font, title, b.x() + 4, b.y() + 3, VanillaTheme.COLOR_TEXT_SECONDARY, false);
            renderColumns(g, b);
        }

        private void renderColumns(GuiGraphics g, Rect b) {
            if (kind == HeaderKind.ALERTS) return;
            var font = Minecraft.getInstance().font;
            if (kind == HeaderKind.DEVICES) {
                g.drawString(font, trans("screen.resourceobserver.power.col.category"), b.right() - 244, b.y() + 3,
                        VanillaTheme.COLOR_TEXT_DISABLED, false);
                g.drawString(font, trans("screen.resourceobserver.power.col.energy"), b.right() - 186, b.y() + 3,
                        VanillaTheme.COLOR_TEXT_DISABLED, false);
                g.drawString(font, trans("screen.resourceobserver.power.col.capacity"), b.right() - 118, b.y() + 3,
                        VanillaTheme.COLOR_TEXT_DISABLED, false);
                g.drawString(font, trans("screen.resourceobserver.power.col.status"), b.right() - 42, b.y() + 3,
                        VanillaTheme.COLOR_TEXT_DISABLED, false);
                return;
            }
            g.drawString(font, "Mod", b.right() - 238, b.y() + 3, VanillaTheme.COLOR_TEXT_DISABLED, false);
            g.drawString(font, trans("screen.resourceobserver.power.col.consumption"), b.right() - 176, b.y() + 3,
                    VanillaTheme.COLOR_TEXT_DISABLED, false);
            g.drawString(font, trans("screen.resourceobserver.power.col.load_share"), b.right() - 100, b.y() + 3,
                    VanillaTheme.COLOR_TEXT_DISABLED, false);
        }

        private static String trans(String key) {
            return Component.translatable(key).getString();
        }
    }
}


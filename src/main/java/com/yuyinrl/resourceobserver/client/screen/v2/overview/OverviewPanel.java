package com.yuyinrl.resourceobserver.client.screen.v2.overview;

import com.yuyinrl.resourceobserver.client.screen.dialog.Dialog;
import com.yuyinrl.resourceobserver.client.screen.dialog.DialogHost;
import com.yuyinrl.resourceobserver.client.screen.dialog.KpiDetailDialog;
import com.yuyinrl.resourceobserver.client.screen.dialog.StorageDetailDialog;
import com.yuyinrl.resourceobserver.client.screen.dialog.builder.KpiDetailLines;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.screen.v2.LatestSnapshotProvider;
import com.yuyinrl.resourceobserver.client.screen.widget.BaseWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.ButtonWidget;
import com.yuyinrl.resourceobserver.client.screen.widget.LineChart;
import com.yuyinrl.resourceobserver.client.screen.widget.Rect;
import com.yuyinrl.resourceobserver.client.screen.widget.Row;
import com.yuyinrl.resourceobserver.client.screen.widget.ScrollView;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla V2 Overview 页，直接消费 OverviewViewModel。
 */
public class OverviewPanel extends BaseWidget {

    private static final int CHART_BUTTON_W = 64;

    private final OverviewKpiCard productionCard = new OverviewKpiCard();
    private final OverviewKpiCard consumptionCard = new OverviewKpiCard();
    private final OverviewKpiCard storageCard = new OverviewKpiCard();
    private final OverviewKpiCard balanceCard = new OverviewKpiCard();
    private final LineChart chart = new LineChart();
    private final ButtonWidget chartToggle = new ButtonWidget(Component.literal("流量"), b -> toggleChart());
    private final OverviewWatchlist watchlist = new OverviewWatchlist();
    private final OverviewTableSection tableSection = new OverviewTableSection();
    private final ScrollView pageScroll = new ScrollView();
    private final OverviewContent pageContent = new OverviewContent();

    private OverviewViewModel viewModel;
    private long providerVersion = -1L;
    private boolean energyChart;
    private OverviewViewModel.KpiType selectedKpi = OverviewViewModel.KpiType.PRODUCTION;
    private int headerH;
    private int kpiH;
    private int chartH;
    private int watchH;
    private int tableH;

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
        if (viewModel == null) {
            renderEmpty(g, b, "暂无数据，请先在 ModernUI 终端打开 Observer");
            return;
        }

        pageScroll.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible() || !isMouseOver(mouseX, mouseY)) return false;
        return pageScroll.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tableSection.isMouseOver(mouseX, mouseY)
                && tableSection.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return pageScroll.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return tableSection.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return tableSection.charTyped(codePoint, modifiers);
    }

    private void refreshSnapshotIfNeeded() {
        LatestSnapshotProvider.State state = LatestSnapshotProvider.get();
        if (providerVersion == state.version()) return;
        providerVersion = state.version();
        viewModel = LatestSnapshotProvider.getOverviewViewModel();
        applyViewModel();
    }

    private void applyViewModel() {
        if (viewModel == null) return;
        applyKpis(viewModel.kpis());
        watchlist.setItems(viewModel.watchlistItems());
        tableSection.setViewModel(viewModel);
        updateChart();
        layoutChildren();
    }

    private void applyKpis(List<OverviewViewModel.KpiMetric> kpis) {
        productionCard.setKpi(findKpi(kpis, OverviewViewModel.KpiType.PRODUCTION));
        consumptionCard.setKpi(findKpi(kpis, OverviewViewModel.KpiType.CONSUMPTION));
        storageCard.setKpi(findKpi(kpis, OverviewViewModel.KpiType.STORAGE));
        balanceCard.setKpi(findKpi(kpis, OverviewViewModel.KpiType.BALANCE));
        updateSelectedKpi();
    }

    private static OverviewViewModel.KpiMetric findKpi(
            List<OverviewViewModel.KpiMetric> kpis, OverviewViewModel.KpiType type) {
        if (kpis == null) return null;
        for (OverviewViewModel.KpiMetric kpi : kpis) {
            if (kpi.type() == type) return kpi;
        }
        return null;
    }

    private void updateSelectedKpi() {
        productionCard.setSelected(selectedKpi == OverviewViewModel.KpiType.PRODUCTION);
        consumptionCard.setSelected(selectedKpi == OverviewViewModel.KpiType.CONSUMPTION);
        storageCard.setSelected(selectedKpi == OverviewViewModel.KpiType.STORAGE);
        balanceCard.setSelected(selectedKpi == OverviewViewModel.KpiType.BALANCE);
    }

    private boolean trySelectKpi(double mouseX, double mouseY) {
        OverviewViewModel.KpiType clicked = null;
        if (productionCard.isMouseOver(mouseX, mouseY)) clicked = OverviewViewModel.KpiType.PRODUCTION;
        else if (consumptionCard.isMouseOver(mouseX, mouseY)) clicked = OverviewViewModel.KpiType.CONSUMPTION;
        else if (storageCard.isMouseOver(mouseX, mouseY)) clicked = OverviewViewModel.KpiType.STORAGE;
        else if (balanceCard.isMouseOver(mouseX, mouseY)) clicked = OverviewViewModel.KpiType.BALANCE;
        if (clicked == null) return false;
        selectedKpi = clicked;
        updateSelectedKpi();
        updateChart();
        openKpiDetailDialog(clicked);
        return true;
    }

    private void openKpiDetailDialog(OverviewViewModel.KpiType type) {
        DialogHost host = DialogHost.current();
        if (host == null) return;
        if (type == OverviewViewModel.KpiType.STORAGE) {
            int titleColor = statusColorForKpi(type);
            host.open(new StorageDetailDialog(
                    KpiDetailLines.overviewTitle(type),
                    titleColor,
                    () -> {
                        OverviewSnapshot s = LatestSnapshotProvider.getOverviewSnapshot();
                        return s == null ? null : s.storageDetail();
                    }));
            return;
        }
        Dialog.Size size = new Dialog.Size(
                VanillaTheme.DIALOG_KPI_SMALL_W, VanillaTheme.DIALOG_KPI_SMALL_H);
        host.open(new KpiDetailDialog(
                KpiDetailLines.overviewTitle(type),
                size,
                () -> KpiDetailLines.forOverview(
                        LatestSnapshotProvider.getOverviewViewModel(), type)));
    }

    private int statusColorForKpi(OverviewViewModel.KpiType type) {
        if (viewModel == null || viewModel.kpis() == null) return VanillaTheme.COLOR_TEXT_PRIMARY;
        for (OverviewViewModel.KpiMetric kpi : viewModel.kpis()) {
            if (kpi.type() == type) {
                return switch (kpi.status()) {
                    case POSITIVE -> VanillaTheme.COLOR_STATUS_OK;
                    case WARNING -> VanillaTheme.COLOR_STATUS_WARN;
                    case NEGATIVE -> VanillaTheme.COLOR_STATUS_ERROR;
                    case NEUTRAL -> VanillaTheme.COLOR_TEXT_PRIMARY;
                };
            }
        }
        return VanillaTheme.COLOR_TEXT_PRIMARY;
    }

    private void toggleChart() {
        energyChart = !energyChart;
        updateChart();
    }

    private void updateChart() {
        if (viewModel == null) return;
        List<OverviewViewModel.FlowPoint> source = energyChart
                ? viewModel.energyChartSeries()
                : viewModel.chartSeries();
        chart.setSeries(buildChartSeries(source));
        chartToggle.setLabel(Component.literal(energyChart ? "能量" : "流量"));
    }

    private List<LineChart.Series> buildChartSeries(List<OverviewViewModel.FlowPoint> source) {
        List<LineChart.DataPoint> primary = new ArrayList<>();
        List<LineChart.DataPoint> secondary = new ArrayList<>();
        if (source != null) {
            for (int i = 0; i < source.size(); i++) {
                OverviewViewModel.FlowPoint point = source.get(i);
                double y = chartValue(point);
                primary.add(new LineChart.DataPoint(i, y));
                secondary.add(new LineChart.DataPoint(i, point.stock()));
            }
        }
        int line = energyChart ? VanillaTheme.COLOR_STATUS_INFO : kpiColor(selectedKpi);
        List<LineChart.Series> result = new ArrayList<>();
        result.add(new LineChart.Series(primary, line, 0x24_55_AA_E0, true));
        if (!energyChart && selectedKpi == OverviewViewModel.KpiType.BALANCE) {
            result.add(new LineChart.Series(secondary, VanillaTheme.COLOR_TEXT_SECONDARY, 0x00_00_00_00, false));
        }
        return result;
    }

    private double chartValue(OverviewViewModel.FlowPoint point) {
        if (point == null) return 0.0d;
        if (energyChart) return point.net();
        return switch (selectedKpi) {
            case PRODUCTION -> point.production();
            case CONSUMPTION -> point.consumption();
            case STORAGE -> point.stock();
            case BALANCE -> point.net();
        };
    }

    private static int kpiColor(OverviewViewModel.KpiType type) {
        return switch (type) {
            case PRODUCTION -> VanillaTheme.COLOR_STATUS_OK;
            case CONSUMPTION -> VanillaTheme.COLOR_STATUS_ERROR;
            case STORAGE -> VanillaTheme.COLOR_STATUS_INFO;
            case BALANCE -> VanillaTheme.COLOR_STATUS_WARN;
        };
    }

    private void layoutChildren() {
        Rect b = bounds().shrink(VanillaTheme.SPACING_S);
        int sectionGap = 6;
        headerH = clamp(Math.round(b.height() * 0.12f), 44, 90);
        kpiH = clamp(Math.round(b.height() * 0.18f), 64, 130);
        chartH = clamp(Math.round(b.height() * 0.32f), 100, 240);
        watchH = watchlist.hasItems() ? watchlist.preferredHeight() : 0;
        tableH = tableSection.preferredHeight();
        int contentH = headerH + sectionGap + kpiH + sectionGap + chartH
                + (watchH > 0 ? sectionGap + watchH : 0)
                + sectionGap + tableH;
        pageScroll.setBounds(b);
        pageScroll.setChild(pageContent, contentH);
    }

    private void layoutPage(Rect b) {
        int sectionGap = 6;
        int y = b.y();

        Row kpiRow = new Row(8)
                .addFlex(productionCard, 1)
                .addFlex(consumptionCard, 1)
                .addFlex(storageCard, 1)
                .addFlex(balanceCard, 1);
        y += headerH + sectionGap;
        kpiRow.setBounds(new Rect(b.x(), y, b.width(), kpiH));
        y += kpiH + sectionGap;

        chart.setBounds(new Rect(b.x(), y, b.width(), chartH));
        chartToggle.setBounds(new Rect(b.right() - CHART_BUTTON_W - 8, y + 8, CHART_BUTTON_W, 20));
        y += chartH + sectionGap;

        if (watchH > 0) {
            watchlist.setBounds(new Rect(b.x(), y, b.width(), watchH));
            y += watchH + sectionGap;
        } else {
            watchlist.setBounds(Rect.EMPTY);
        }
        tableSection.setBounds(new Rect(b.x(), y, b.width(), tableH));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void renderHeader(GuiGraphics g, Rect b) {
        var font = Minecraft.getInstance().font;
        String title = viewModel.headerTitle() == null ? "" : viewModel.headerTitle();
        String subtitle = viewModel.headerSubtitle() == null ? "" : viewModel.headerSubtitle();
        String status = viewModel.linkStatus() == null ? "" : viewModel.linkStatus();
        g.drawString(font, title, b.x(), b.y() + 1, VanillaTheme.COLOR_TEXT_PRIMARY, true);
        g.drawString(font, subtitle, b.x(), b.y() + 12, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        int color = viewModel.kpis() == null || viewModel.kpis().isEmpty()
                ? VanillaTheme.COLOR_STATUS_WARN
                : VanillaTheme.COLOR_STATUS_OK;
        g.drawString(font, status, b.right() - font.width(status), b.y() + 6, color, true);
    }

    private void renderEmpty(GuiGraphics g, Rect rect, String text) {
        var font = Minecraft.getInstance().font;
        int tx = rect.x() + (rect.width() - font.width(text)) / 2;
        int ty = rect.y() + (rect.height() - VanillaTheme.FONT_HEIGHT) / 2;
        g.drawString(font, text, tx, ty, VanillaTheme.COLOR_TEXT_SECONDARY, true);
    }

    private class OverviewContent extends BaseWidget {
        @Override
        protected void onBoundsChanged() {
            layoutPage(bounds());
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) return;
            Rect header = new Rect(bounds().x(), bounds().y(), bounds().width(), headerH);
            renderHeader(g, header);
            productionCard.render(g, mouseX, mouseY, partialTick);
            consumptionCard.render(g, mouseX, mouseY, partialTick);
            storageCard.render(g, mouseX, mouseY, partialTick);
            balanceCard.render(g, mouseX, mouseY, partialTick);
            chart.render(g, mouseX, mouseY, partialTick);
            chartToggle.render(g, mouseX, mouseY, partialTick);
            if (watchH > 0) {
                watchlist.render(g, mouseX, mouseY, partialTick);
            }
            tableSection.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (chartToggle.mouseClicked(mouseX, mouseY, button)) return true;
            if (watchH > 0 && watchlist.mouseClicked(mouseX, mouseY, button)) return true;
            if (button == 0 && trySelectKpi(mouseX, mouseY)) return true;
            return tableSection.mouseClicked(mouseX, mouseY, button);
        }
    }
}

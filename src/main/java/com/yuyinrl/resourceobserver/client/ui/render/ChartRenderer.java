package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public final class ChartRenderer {
    private static final int BUTTON_GAP = 4;
    private static final int WINDOW_W = 68;
    private static final int PAGE_W = 76;
    private static final int MODE_W = 56;
    private static final int SMOOTH_W = 62;

    private ChartRenderer() {
    }

    public static RenderResult render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            List<OverviewViewModel.FlowPoint> series,
            String selectedItemLabel,
            ChartWindow chartWindow,
            LineMode lineMode,
            ChartPage chartPage,
            SmoothingMode smoothingMode,
            ChartRenderCache cache
    ) {
        RenderUtils.fillPanel(gfx, area, UiThemeTokens.SECTION_BG, UiThemeTokens.SECTION_BORDER);
        UiRect content = area.inset(8);

        String scopeLabel = selectedItemLabel == null || selectedItemLabel.isBlank()
                ? Component.translatable("screen.resourceobserver.overview.chart.scope.global").getString()
                : selectedItemLabel;
        String subtitle = Component.translatable(
                chartPage == ChartPage.STOCK
                        ? "screen.resourceobserver.overview.chart.subtitle.stock"
                        : "screen.resourceobserver.overview.chart.subtitle.throughput",
                scopeLabel,
                chartWindow.shortLabel()
        ).getString();

        gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.section.chart"), content.x(), content.y(), UiThemeTokens.TEXT);
        gfx.drawString(font, subtitle, content.x(), content.y() + 11, UiThemeTokens.TEXT_MUTED);

        int controlsRight = content.right();
        int totalW = WINDOW_W + BUTTON_GAP + PAGE_W + BUTTON_GAP + MODE_W + BUTTON_GAP + SMOOTH_W;
        int controlsStartX = controlsRight - totalW;
        int btnY = content.y();

        UiRect windowToggle = new UiRect(controlsStartX, btnY, WINDOW_W, 14);
        UiRect pageToggle = new UiRect(windowToggle.right() + BUTTON_GAP, btnY, PAGE_W, 14);
        UiRect lineModeButton = new UiRect(pageToggle.right() + BUTTON_GAP, btnY, MODE_W, 14);
        UiRect smoothingButton = new UiRect(lineModeButton.right() + BUTTON_GAP, btnY, SMOOTH_W, 14);

        drawHeaderButton(
                gfx,
                font,
                windowToggle,
                Component.translatable(chartWindow.translationKey()).getString(),
                true,
                true
        );
        drawHeaderButton(
                gfx,
                font,
                pageToggle,
                chartPage == ChartPage.THROUGHPUT
                        ? Component.translatable("screen.resourceobserver.overview.chart.tab.throughput").getString()
                        : Component.translatable("screen.resourceobserver.overview.chart.tab.stock").getString(),
                true,
                true
        );
        boolean modeEnabled = chartPage == ChartPage.THROUGHPUT;
        drawHeaderButton(
                gfx,
                font,
                lineModeButton,
                lineModeLabel(lineMode),
                modeEnabled,
                modeEnabled
        );
        drawHeaderButton(
                gfx,
                font,
                smoothingButton,
                Component.translatable(
                        smoothingMode == SmoothingMode.SMOOTH
                                ? "screen.resourceobserver.overview.chart.smoothing.smooth"
                                : "screen.resourceobserver.overview.chart.smoothing.raw"
                ).getString(),
                true,
                true
        );

        UiRect resetButton = null;
        if (selectedItemLabel != null && !selectedItemLabel.isBlank()) {
            int resetW = 88;
            int resetX = controlsStartX - BUTTON_GAP - resetW;
            if (resetX >= content.x()) {
                resetButton = new UiRect(resetX, btnY, resetW, 14);
                drawHeaderButton(
                        gfx,
                        font,
                        resetButton,
                        Component.translatable("screen.resourceobserver.overview.chart.reset").getString(),
                        false,
                        true
                );
            }
        }

        UiRect plot = new UiRect(content.x(), content.y() + 24, content.width(), Math.max(42, content.height() - 28));
        drawPlotFrame(gfx, plot);

        CacheKey key = new CacheKey(
                plot.x(),
                plot.y(),
                plot.width(),
                plot.height(),
                chartWindow,
                chartPage,
                lineMode,
                smoothingMode,
                seriesFingerprint(series)
        );

        PreparedPlot prepared = cache.getOrBuild(
                key,
                () -> buildPreparedPlot(plot, series, lineMode, chartPage, smoothingMode)
        );
        drawPreparedPlot(gfx, plot, prepared);

        return new RenderResult(
                windowToggle,
                pageToggle,
                lineModeButton,
                smoothingButton,
                resetButton,
                modeEnabled
        );
    }

    private static void drawPlotFrame(GuiGraphics gfx, UiRect plot) {
        gfx.fill(plot.x(), plot.y(), plot.right(), plot.bottom(), 0x5A0D1628);
        RenderUtils.drawBorder(gfx, plot, 0x773A5478);
        int innerLeft = plot.x() + 1;
        int innerRight = plot.right() - 1;
        int hStep = Math.max(8, plot.height() / 5);
        int vStep = Math.max(12, plot.width() / 10);
        for (int y = plot.y() + hStep; y < plot.bottom() - 1; y += hStep) {
            gfx.fill(innerLeft, y, innerRight, y + 1, 0x223E5C84);
        }
        for (int x = plot.x() + vStep; x < plot.right() - 1; x += vStep) {
            gfx.fill(x, plot.y() + 1, x + 1, plot.bottom() - 1, 0x152F4668);
        }
    }

    private static PreparedPlot buildPreparedPlot(
            UiRect plot,
            List<OverviewViewModel.FlowPoint> series,
            LineMode lineMode,
            ChartPage chartPage,
            SmoothingMode smoothingMode
    ) {
        double[] productionValues = new double[series.size()];
        double[] consumptionValues = new double[series.size()];
        double[] netValues = new double[series.size()];
        double[] stockValues = new double[series.size()];
        boolean[] flowMask = new boolean[series.size()];
        boolean[] stockMask = new boolean[series.size()];
        for (int i = 0; i < series.size(); i++) {
            OverviewViewModel.FlowPoint point = series.get(i);
            productionValues[i] = point.production();
            consumptionValues[i] = point.consumption();
            netValues[i] = point.net();
            stockValues[i] = point.stock();
            flowMask[i] = point.hasFlow();
            stockMask[i] = point.hasStock();
        }

        boolean smoothGeometry = smoothingMode == SmoothingMode.SMOOTH;
        if (chartPage == ChartPage.THROUGHPUT) {
            double[] prod = smoothGeometry ? smoothSeries(productionValues, flowMask) : Arrays.copyOf(productionValues, productionValues.length);
            double[] cons = smoothGeometry ? smoothSeries(consumptionValues, flowMask) : Arrays.copyOf(consumptionValues, consumptionValues.length);
            double[] net = smoothGeometry ? smoothSeries(netValues, flowMask) : Arrays.copyOf(netValues, netValues.length);
            Range range = rangeForThroughput(lineMode, prod, cons, net, flowMask);

            PreparedSeries production = lineMode.showProduction()
                    ? buildSeries(plot, prod, flowMask, range, smoothGeometry, true)
                    : PreparedSeries.empty();
            PreparedSeries consumption = lineMode.showConsumption()
                    ? buildSeries(plot, cons, flowMask, range, smoothGeometry, true)
                    : PreparedSeries.empty();
            PreparedSeries netSeries = lineMode.showNet()
                    ? buildSeries(plot, net, flowMask, range, smoothGeometry, false)
                    : PreparedSeries.empty();

            int zeroY = Integer.MIN_VALUE;
            if (range.min < 0.0 && range.max > 0.0) {
                zeroY = clamp((int) Math.round(valueToY(0.0, range.min, range.max, plot)), plot.y() + 1, plot.bottom() - 2);
            }
            return new PreparedPlot(
                    ChartPage.THROUGHPUT,
                    lineMode,
                    production,
                    consumption,
                    netSeries,
                    PreparedSeries.empty(),
                    zeroY
            );
        }

        double[] stock = smoothGeometry ? smoothSeries(stockValues, stockMask) : Arrays.copyOf(stockValues, stockValues.length);
        Range range = rangeForSingle(stock, stockMask);
        PreparedSeries stockSeries = buildSeries(plot, stock, stockMask, range, smoothGeometry, true);
        return new PreparedPlot(
                ChartPage.STOCK,
                lineMode,
                PreparedSeries.empty(),
                PreparedSeries.empty(),
                PreparedSeries.empty(),
                stockSeries,
                Integer.MIN_VALUE
        );
    }

    private static PreparedSeries buildSeries(
            UiRect plot,
            double[] values,
            boolean[] validMask,
            Range range,
            boolean smoothGeometry,
            boolean withArea
    ) {
        List<List<Point>> segments = buildCurveSegments(plot, values, validMask, range.min, range.max, smoothGeometry);
        int[] areaTop = withArea ? buildAreaTop(plot, segments) : null;
        return new PreparedSeries(segments, areaTop);
    }

    private static int[] buildAreaTop(UiRect plot, List<List<Point>> segments) {
        int[] topByX = new int[plot.width()];
        Arrays.fill(topByX, Integer.MAX_VALUE);
        int minX = plot.x() + 1;
        int maxX = plot.right() - 2;
        for (List<Point> segment : segments) {
            if (segment.size() < 2) {
                continue;
            }
            for (int i = 0; i < segment.size() - 1; i++) {
                Point a = segment.get(i);
                Point b = segment.get(i + 1);
                if (Math.abs(b.x - a.x) < 1.0E-6) {
                    int x = clamp((int) Math.round(a.x), minX, maxX);
                    int y = clamp((int) Math.round(Math.min(a.y, b.y)), plot.y() + 1, plot.bottom() - 2);
                    int idx = x - plot.x();
                    if (idx >= 0 && idx < topByX.length) {
                        topByX[idx] = Math.min(topByX[idx], y);
                    }
                    continue;
                }
                int x0 = clamp((int) Math.ceil(Math.min(a.x, b.x)), minX, maxX);
                int x1 = clamp((int) Math.floor(Math.max(a.x, b.x)), minX, maxX);
                for (int x = x0; x <= x1; x++) {
                    double t = (x - a.x) / (b.x - a.x);
                    if (t < 0.0 || t > 1.0) {
                        continue;
                    }
                    int y = clamp((int) Math.round(lerp(a.y, b.y, t)), plot.y() + 1, plot.bottom() - 2);
                    int idx = x - plot.x();
                    if (idx >= 0 && idx < topByX.length) {
                        topByX[idx] = Math.min(topByX[idx], y);
                    }
                }
            }
        }

        for (int i = 0; i < topByX.length; i++) {
            if (topByX[i] == Integer.MAX_VALUE) {
                topByX[i] = -1;
            }
        }
        return topByX;
    }

    private static void drawPreparedPlot(GuiGraphics gfx, UiRect plot, PreparedPlot prepared) {
        if (prepared.page == ChartPage.THROUGHPUT) {
            if (prepared.zeroY != Integer.MIN_VALUE) {
                gfx.fill(plot.x() + 1, prepared.zeroY, plot.right() - 1, prepared.zeroY + 1, 0x40BBD5F3);
            }
            if (prepared.lineMode.showProduction()) {
                drawSeriesArea(gfx, plot, prepared.production, 0x1A22D3EE);
                drawSeriesLines(gfx, prepared.production, UiThemeTokens.CYAN);
            }
            if (prepared.lineMode.showConsumption()) {
                drawSeriesArea(gfx, plot, prepared.consumption, 0x1CF59E0B);
                drawSeriesLines(gfx, prepared.consumption, UiThemeTokens.AMBER);
            }
            if (prepared.lineMode.showNet()) {
                drawSeriesLines(gfx, prepared.net, UiThemeTokens.EMERALD);
            }
            return;
        }

        drawSeriesArea(gfx, plot, prepared.stock, 0x1A60A5FA);
        drawSeriesLines(gfx, prepared.stock, UiThemeTokens.BLUE);
    }

    private static void drawSeriesArea(GuiGraphics gfx, UiRect plot, PreparedSeries series, int color) {
        if (series.areaTopByX == null) {
            return;
        }
        int bottom = plot.bottom() - 1;
        for (int i = 0; i < series.areaTopByX.length; i++) {
            int top = series.areaTopByX[i];
            if (top < 0 || top >= bottom) {
                continue;
            }
            int x = plot.x() + i;
            gfx.fill(x, top, x + 1, bottom, color);
        }
    }

    private static void drawSeriesLines(GuiGraphics gfx, PreparedSeries series, int color) {
        int shadowDown = withAlpha(color, 36);
        int shadowUp = withAlpha(color, 28);
        int core = withAlpha(color, 235);
        for (List<Point> segment : series.segments) {
            if (segment.size() < 2) {
                continue;
            }
            for (int i = 0; i < segment.size() - 1; i++) {
                Point a = segment.get(i);
                Point b = segment.get(i + 1);
                int x0 = (int) Math.round(a.x);
                int y0 = (int) Math.round(a.y);
                int x1 = (int) Math.round(b.x);
                int y1 = (int) Math.round(b.y);
                RenderUtils.drawLine(gfx, x0, y0 + 1, x1, y1 + 1, shadowDown);
                RenderUtils.drawLine(gfx, x0, y0 - 1, x1, y1 - 1, shadowUp);
                RenderUtils.drawLine(gfx, x0, y0, x1, y1, core);
            }
        }
    }

    private static List<List<Point>> buildCurveSegments(
            UiRect plot,
            double[] values,
            boolean[] validMask,
            double min,
            double max,
            boolean smoothGeometry
    ) {
        List<List<Point>> segments = new ArrayList<>();
        int idx = 0;
        while (idx < values.length) {
            while (idx < values.length && !validMask[idx]) {
                idx++;
            }
            if (idx >= values.length) {
                break;
            }
            int start = idx;
            while (idx < values.length && validMask[idx]) {
                idx++;
            }
            int end = idx - 1;
            List<Point> base = new ArrayList<>(end - start + 1);
            for (int i = start; i <= end; i++) {
                base.add(new Point(
                        toX(i, values.length, plot),
                        valueToY(values[i], min, max, plot)
                ));
            }
            if (smoothGeometry && base.size() >= 3) {
                segments.add(interpolateCurve(base));
            } else {
                segments.add(base);
            }
        }
        return segments;
    }

    private static List<Point> interpolateCurve(List<Point> base) {
        List<Point> out = new ArrayList<>();
        int last = base.size() - 1;
        for (int i = 0; i < last; i++) {
            Point p0 = base.get(Math.max(0, i - 1));
            Point p1 = base.get(i);
            Point p2 = base.get(i + 1);
            Point p3 = base.get(Math.min(last, i + 2));
            double span = Math.max(1.0, p2.x - p1.x);
            int steps = Math.max(2, (int) Math.ceil(span * 1.8));
            int startStep = i == 0 ? 0 : 1;
            for (int s = startStep; s <= steps; s++) {
                double t = s / (double) steps;
                double x = lerp(p1.x, p2.x, t);
                double y = catmullRom(p0.y, p1.y, p2.y, p3.y, t);
                out.add(new Point(x, y));
            }
        }
        return out;
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1)
                + (-p0 + p2) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    private static Range rangeForThroughput(
            LineMode mode,
            double[] production,
            double[] consumption,
            double[] net,
            boolean[] validMask
    ) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < production.length; i++) {
            if (!validMask[i]) {
                continue;
            }
            if (mode.showProduction()) {
                min = Math.min(min, production[i]);
                max = Math.max(max, production[i]);
            }
            if (mode.showConsumption()) {
                min = Math.min(min, consumption[i]);
                max = Math.max(max, consumption[i]);
            }
            if (mode.showNet()) {
                min = Math.min(min, net[i]);
                max = Math.max(max, net[i]);
            }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return new Range(0.0, 1.0);
        }
        if (Math.abs(max - min) < 1.0E-6) {
            return new Range(min, min + 1.0);
        }
        return new Range(min, max);
    }

    private static Range rangeForSingle(double[] values, boolean[] validMask) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < values.length; i++) {
            if (!validMask[i]) {
                continue;
            }
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return new Range(0.0, 1.0);
        }
        if (Math.abs(max - min) < 1.0E-6) {
            return new Range(min, min + 1.0);
        }
        return new Range(min, max);
    }

    private static double[] smoothSeries(double[] values, boolean[] validMask) {
        double[] out = Arrays.copyOf(values, values.length);
        int i = 0;
        while (i < values.length) {
            while (i < values.length && !validMask[i]) {
                i++;
            }
            if (i >= values.length) {
                break;
            }
            int start = i;
            while (i < values.length && validMask[i]) {
                i++;
            }
            int end = i - 1;
            double ema = values[start];
            out[start] = ema;
            for (int j = start + 1; j <= end; j++) {
                ema = ema * 0.55 + values[j] * 0.45;
                out[j] = ema;
            }
        }
        return out;
    }

    private static long seriesFingerprint(List<OverviewViewModel.FlowPoint> series) {
        long hash = 0xCBF29CE484222325L;
        for (OverviewViewModel.FlowPoint point : series) {
            hash ^= point.slotIndex();
            hash *= 0x100000001B3L;
            hash = mix(hash, point.production());
            hash = mix(hash, point.consumption());
            hash = mix(hash, point.net());
            hash = mix(hash, point.stock());
            hash ^= point.hasFlow() ? 0x9E3779B97F4A7C15L : 0xC2B2AE3D27D4EB4FL;
            hash *= 0x100000001B3L;
            hash ^= point.hasStock() ? 0x165667B19E3779F9L : 0x85EBCA77C2B2AE63L;
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    private static long mix(long seed, double value) {
        long bits = Double.doubleToLongBits(value);
        seed ^= bits + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2);
        return seed;
    }

    private static double toX(int index, int size, UiRect plot) {
        if (size <= 1) {
            return plot.x() + 1.0;
        }
        return plot.x() + 1.0 + (index / (double) (size - 1)) * (plot.width() - 3.0);
    }

    private static double valueToY(double value, double min, double max, UiRect plot) {
        double range = Math.max(1.0E-6, max - min);
        double ratio = (value - min) / range;
        return plot.bottom() - 2.0 - ratio * (plot.height() - 3.0);
    }

    private static int withAlpha(int color, int alpha) {
        return (clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String lineModeLabel(LineMode mode) {
        return switch (mode) {
            case ALL -> Component.translatable("screen.resourceobserver.overview.chart.mode.all").getString();
            case PRODUCTION -> Component.translatable("screen.resourceobserver.overview.chart.mode.production").getString();
            case CONSUMPTION -> Component.translatable("screen.resourceobserver.overview.chart.mode.consumption").getString();
            case NET -> Component.translatable("screen.resourceobserver.overview.chart.mode.net").getString();
        };
    }

    private static void drawHeaderButton(
            GuiGraphics gfx,
            Font font,
            UiRect rect,
            String text,
            boolean active,
            boolean enabled
    ) {
        int bg = active ? 0xAA23456A : UiThemeTokens.TAB_INACTIVE;
        if (!enabled) {
            bg = 0x66192334;
        }
        int border = active ? UiThemeTokens.CYAN : UiThemeTokens.SECTION_BORDER;
        if (!enabled) {
            border = 0x664A5B72;
        }
        int textColor = active ? UiThemeTokens.TEXT : UiThemeTokens.TEXT_MUTED;
        if (!enabled) {
            textColor = 0xFF8A97AA;
        }
        gfx.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
        RenderUtils.drawBorder(gfx, rect, border);
        gfx.drawString(font, RenderUtils.ellipsis(font, text, rect.width() - 8), rect.x() + 4, rect.y() + 3, textColor);
    }

    public static final class ChartRenderCache {
        private CacheKey key;
        private PreparedPlot prepared;

        public PreparedPlot getOrBuild(CacheKey nextKey, Supplier<PreparedPlot> supplier) {
            if (prepared == null || key == null || !key.equals(nextKey)) {
                prepared = supplier.get();
                key = nextKey;
            }
            return prepared;
        }

        public void clear() {
            key = null;
            prepared = null;
        }
    }

    private record CacheKey(
            int x,
            int y,
            int width,
            int height,
            ChartWindow window,
            ChartPage page,
            LineMode lineMode,
            SmoothingMode smoothingMode,
            long seriesFingerprint
    ) {
    }

    private record Point(double x, double y) {
    }

    private record Range(double min, double max) {
    }

    private record PreparedSeries(
            List<List<Point>> segments,
            int[] areaTopByX
    ) {
        private static PreparedSeries empty() {
            return new PreparedSeries(List.of(), null);
        }
    }

    private record PreparedPlot(
            ChartPage page,
            LineMode lineMode,
            PreparedSeries production,
            PreparedSeries consumption,
            PreparedSeries net,
            PreparedSeries stock,
            int zeroY
    ) {
    }

    public enum ChartPage {
        THROUGHPUT,
        STOCK;

        public ChartPage next() {
            return this == THROUGHPUT ? STOCK : THROUGHPUT;
        }
    }

    public enum SmoothingMode {
        SMOOTH,
        RAW;

        public SmoothingMode next() {
            return this == SMOOTH ? RAW : SMOOTH;
        }
    }

    public enum LineMode {
        ALL,
        PRODUCTION,
        CONSUMPTION,
        NET;

        public LineMode next() {
            return switch (this) {
                case ALL -> PRODUCTION;
                case PRODUCTION -> CONSUMPTION;
                case CONSUMPTION -> NET;
                case NET -> ALL;
            };
        }

        public boolean showProduction() {
            return this == ALL || this == PRODUCTION;
        }

        public boolean showConsumption() {
            return this == ALL || this == CONSUMPTION;
        }

        public boolean showNet() {
            return this == ALL || this == NET;
        }
    }

    public record RenderResult(
            UiRect windowToggle,
            UiRect pageToggle,
            UiRect lineModeButton,
            UiRect smoothingButton,
            UiRect resetButton,
            boolean lineModeEnabled
    ) {
    }
}

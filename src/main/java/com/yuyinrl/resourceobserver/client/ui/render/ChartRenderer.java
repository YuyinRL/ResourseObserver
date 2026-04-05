package com.yuyinrl.resourceobserver.client.ui.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.yuyinrl.resourceobserver.client.ChartRenderShaders;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * 图表渲染器 —— 绘制资源流量/库存趋势图。
 * <p>
 * 渲染管线说明：
 * <ol>
 *   <li>数据序列经可选平滑处理后计算数值范围</li>
 *   <li>使用 Monotone Hermite 插值生成平滑折线</li>
 *   <li>通过自定义着色器绘制抗锯齿线段（支持核心线 + 辉光效果）</li>
 *   <li>结果缓存在 PreparedChart 中避免重复计算</li>
 * </ol>
 * <p>
 * 支持两种页面模式：
 * - THROUGHPUT（吞吐量）：同时显示生产/消耗/净流量曲线
 * - STOCK（库存）：显示库存量变化曲线
 * <p>
 * 支持四种线条模式：ALL / PRODUCTION / CONSUMPTION / NET
 * 支持两种平滑模式：SMOOTH（EMA 平滑）/ RAW（原始数据）
 */
public final class ChartRenderer {
    // ========== 布局常量 ==========
    private static final int BUTTON_GAP = 4;     // 按钮间距
    private static final int WINDOW_W = 68;      // 时间窗口按钮宽度
    private static final int DATA_TYPE_W = 60;   // 数据类型切换按钮宽度（物品/电量）
    private static final int PAGE_W = 76;        // 页面切换按钮宽度
    private static final int MODE_W = 56;        // 线条模式按钮宽度
    private static final int SMOOTH_W = 62;      // 平滑模式按钮宽度

    // ========== 渲染管线参数 ==========
    private static final int DEFAULT_SUPERSAMPLE = 4;       // 默认超采样倍率
    private static final double PLOT_PAD_X_SCALE = 1.0;     // 绘图区水平边距倍率
    private static final double PLOT_PAD_TOP_SCALE = 2.5;   // 绘图区顶部边距倍率
    private static final double PLOT_PAD_END_SCALE = 2.0;   // 绘图区末端边距倍率

    // ========== 线条样式预设 ==========
    /** 默认线条样式（超采样抗锯齿） */
    private static final LineStyle DEFAULT_LINE_STYLE = new LineStyle(DEFAULT_SUPERSAMPLE, 1.0f, 0.82f, 1.26f, 0.50f, 0.75f, 0.92f, 0.20f, 0.34f, GL11C.GL_LINEAR, 1.0f, 1.0f, 0.0f);
    /** 调试模式直接渲染线条样式 */
    private static final LineStyle DEBUG_DIRECT_LINE_STYLE = new LineStyle(1, 16.0f, 0.80f, 0.96f, 0.40f, 0.72f, 1.00f, 0.00f, 0.28f, GL11C.GL_LINEAR, 1.0f, 1.0f, 0.0f);
    private static final boolean ENABLE_AREA_FILL = false; // TODO 将当前填充路径替换为无接缝着色器填充后恢复此功能

    /** 弱引用缓存集合，用于统一失效所有缓存 */
    private static final Set<ChartRenderCache> LIVE_CACHES = Collections.newSetFromMap(new WeakHashMap<>());

    private ChartRenderer() {
    }

    /** 使所有图表渲染缓存失效（数据更新时调用） */
    public static void invalidateAllCaches() {
        synchronized (LIVE_CACHES) {
            for (ChartRenderCache cache : new ArrayList<>(LIVE_CACHES)) {
                if (cache != null) {
                    cache.clear();
                }
            }
        }
    }

    /**
     * 渲染图表区域。
     * @param series           图表数据点序列
     * @param selectedItemLabel 当前选中物品名称（显示在副标题中）
     * @param chartWindow      时间窗口（1m/5m/30m/1h/6h）
     * @param lineMode         线条显示模式（全部/生产/消耗/净流量）
     * @param chartPage        页面类型（吞吐量/库存）
     * @param smoothingMode    数据平滑模式
     * @param chartDataType    数据类型（物品/电量）
     * @param hasEnergySeries  是否存在电量数据系列（控制切换按钮是否可用）
     * @param cache            渲染缓存（避免逐帧重新计算）
     * @return 渲染结果，包含各按钮热区
     */
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
            ChartDataType chartDataType,
            boolean hasEnergySeries,
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

        int right = content.right();
        int rowW = WINDOW_W + BUTTON_GAP + DATA_TYPE_W + BUTTON_GAP + PAGE_W + BUTTON_GAP + MODE_W + BUTTON_GAP + SMOOTH_W;
        int x0 = right - rowW;
        int by = content.y();
        UiRect windowToggle = new UiRect(x0, by, WINDOW_W, 14);
        UiRect dataTypeToggle = new UiRect(windowToggle.right() + BUTTON_GAP, by, DATA_TYPE_W, 14);
        UiRect pageToggle = new UiRect(dataTypeToggle.right() + BUTTON_GAP, by, PAGE_W, 14);
        UiRect modeButton = new UiRect(pageToggle.right() + BUTTON_GAP, by, MODE_W, 14);
        UiRect smoothButton = new UiRect(modeButton.right() + BUTTON_GAP, by, SMOOTH_W, 14);

        drawHeaderButton(gfx, font, windowToggle, Component.translatable(chartWindow.translationKey()).getString(), true, true);
        // 数据类型切换按钮（物品/电量）
        String dataTypeLabel = chartDataType == ChartDataType.ENERGY
                ? Component.translatable("screen.resourceobserver.overview.chart.data.energy").getString()
                : Component.translatable("screen.resourceobserver.overview.chart.data.items").getString();
        drawHeaderButton(gfx, font, dataTypeToggle, dataTypeLabel, true, true);
        drawHeaderButton(
                gfx, font, pageToggle,
                chartPage == ChartPage.THROUGHPUT
                        ? Component.translatable("screen.resourceobserver.overview.chart.tab.throughput").getString()
                        : Component.translatable("screen.resourceobserver.overview.chart.tab.stock").getString(),
                true, true
        );
        boolean lineModeEnabled = chartPage == ChartPage.THROUGHPUT;
        drawHeaderButton(gfx, font, modeButton, lineModeLabel(lineMode), lineModeEnabled, lineModeEnabled);
        drawHeaderButton(
                gfx, font, smoothButton,
                Component.translatable(
                        smoothingMode == SmoothingMode.SMOOTH
                                ? "screen.resourceobserver.overview.chart.smoothing.smooth"
                                : "screen.resourceobserver.overview.chart.smoothing.raw"
                ).getString(),
                true, true
        );

        UiRect resetButton = null;
        if (selectedItemLabel != null && !selectedItemLabel.isBlank()) {
            int rw = 88;
            int rx = x0 - BUTTON_GAP - rw;
            if (rx >= content.x()) {
                resetButton = new UiRect(rx, by, rw, 14);
                drawHeaderButton(
                        gfx, font, resetButton,
                        Component.translatable("screen.resourceobserver.overview.chart.reset").getString(),
                        false, true
                );
            }
        }

        UiRect plot = new UiRect(content.x(), content.y() + 24, content.width(), Math.max(42, content.height() - 28));
        drawPlotFrame(gfx, plot);
        LineStyle lineStyle = lineStyleFor(chartWindow);

        CacheKey key = new CacheKey(
                plot.width(),
                plot.height(),
                lineStyle.supersample(),
                chartWindow,
                chartPage,
                lineMode,
                smoothingMode,
                seriesFingerprint(series)
        );

        gfx.flush();
        PreparedChart prepared = cache.getOrBuild(
                key,
                () -> prepareChart(plot.width(), plot.height(), series, chartPage, lineMode, smoothingMode, lineStyle)
        );
        List<ChartHoverPoint> hoverPoints = List.of();
        if (prepared != null && prepared != PreparedChart.EMPTY && prepared.hasVisibleContent()) {
            drawPreparedChartVector(gfx, plot, prepared, lineStyle);
            hoverPoints = prepared.hoverPoints();
        }
        drawLegend(gfx, font, plot, chartPage, lineMode);

        return new RenderResult(windowToggle, dataTypeToggle, pageToggle, modeButton, smoothButton, resetButton, lineModeEnabled, plot, hoverPoints);
    }

    private static void drawLegend(GuiGraphics gfx, Font font, UiRect plot, ChartPage page, LineMode mode) {
        int x = plot.x() + 6;
        int y = plot.y() + 4;
        if (page == ChartPage.STOCK) {
            gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.chart.legend.stock"), x, y, UiThemeTokens.BLUE);
            return;
        }
        if (mode.showProduction()) {
            gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.chart.legend.production"), x, y, UiThemeTokens.CYAN);
            x += 74;
        }
        if (mode.showConsumption()) {
            gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.chart.legend.consumption"), x, y, UiThemeTokens.AMBER);
            x += 84;
        }
        if (mode.showNet()) {
            gfx.drawString(font, Component.translatable("screen.resourceobserver.overview.chart.legend.net"), x, y, UiThemeTokens.EMERALD);
        }
    }

    private static void drawPlotFrame(GuiGraphics gfx, UiRect plot) {
        gfx.fill(plot.x(), plot.y(), plot.right(), plot.bottom(), 0x5A0D1628);
        RenderUtils.drawBorder(gfx, plot, 0x773A5478);
        int left = plot.x() + 1;
        int right = plot.right() - 1;
        int hs = Math.max(8, plot.height() / 5);
        int vs = Math.max(12, plot.width() / 10);
        for (int y = plot.y() + hs; y < plot.bottom() - 1; y += hs) {
            gfx.fill(left, y, right, y + 1, 0x223E5C84);
        }
        for (int x = plot.x() + vs; x < plot.right() - 1; x += vs) {
            gfx.fill(x, plot.y() + 1, x + 1, plot.bottom() - 1, 0x152F4668);
        }
    }

    private static void renderToTexture(
            CachedChartTexture texture,
            List<OverviewViewModel.FlowPoint> series,
            ChartWindow chartWindow,
            ChartPage page,
            LineMode lineMode,
            SmoothingMode smoothingMode,
            LineStyle lineStyle
    ) {
        clearTarget(texture.highRes);
        clearTarget(texture.lowRes);
        texture.lowRes.setFilterMode(lineStyle.lowResFilter());

        int n = series.size();
        double[] p = new double[n];
        double[] c = new double[n];
        double[] net = new double[n];
        double[] stock = new double[n];
        boolean[] fm = new boolean[n];
        boolean[] sm = new boolean[n];
        for (int i = 0; i < n; i++) {
            OverviewViewModel.FlowPoint point = series.get(i);
            p[i] = point.production();
            c[i] = point.consumption();
            net[i] = point.net();
            stock[i] = point.stock();
            fm[i] = point.hasFlow();
            sm[i] = point.hasStock();
        }

        boolean smooth = smoothingMode == SmoothingMode.SMOOTH;
        RectBounds bounds = new RectBounds(
                plotMinX(texture.sampleScale),
                plotMinY(texture.sampleScale),
                maxCoordX(texture.highResWidth, texture.sampleScale),
                maxCoordY(texture.highResHeight, texture.sampleScale)
        );

        withTarget(texture.highRes, texture.highResWidth, texture.highResHeight, bounds, () -> {
            if (page == ChartPage.THROUGHPUT) {
                double[] ps = smooth ? smoothSeries(p, fm) : Arrays.copyOf(p, n);
                double[] cs = smooth ? smoothSeries(c, fm) : Arrays.copyOf(c, n);
                double[] ns = smooth ? smoothSeries(net, fm) : Arrays.copyOf(net, n);
                Range r = rangeThroughput(lineMode, ps, cs, ns, fm);

                if (r.min < 0.0 && r.max > 0.0) {
                    double zy = clampD(valueY4(0.0, r.min, r.max, texture.highResHeight, texture.sampleScale), bounds.minY, bounds.maxY);
                    drawAaLine(new Point(bounds.minX, zy), new Point(bounds.maxX, zy), 0xBBD5F3, lineStyle.zeroWidth(), lineStyle.zeroWidth(), lineStyle.feather(), lineStyle.zeroAlpha(), 0.0f, bounds, texture.sampleScale);
                }
                if (lineMode.showProduction()) {
                    List<List<Point>> seg = buildPolylines(texture.highResWidth, texture.highResHeight, texture.sampleScale, lineStyle.curveSubdivision(), ps, fm, r.min, r.max, smooth);
                    if (ENABLE_AREA_FILL) {
                        fillArea(seg, bounds, 0x1A22D3EE);
                    }
                    drawSeries(seg, bounds, UiThemeTokens.CYAN, lineStyle, texture.sampleScale);
                }
                if (lineMode.showConsumption()) {
                    List<List<Point>> seg = buildPolylines(texture.highResWidth, texture.highResHeight, texture.sampleScale, lineStyle.curveSubdivision(), cs, fm, r.min, r.max, smooth);
                    if (ENABLE_AREA_FILL) {
                        fillArea(seg, bounds, 0x1CF59E0B);
                    }
                    drawSeries(seg, bounds, UiThemeTokens.AMBER, lineStyle, texture.sampleScale);
                }
                if (lineMode.showNet()) {
                    drawSeries(buildPolylines(texture.highResWidth, texture.highResHeight, texture.sampleScale, lineStyle.curveSubdivision(), ns, fm, r.min, r.max, smooth), bounds, UiThemeTokens.EMERALD, lineStyle, texture.sampleScale);
                }
            } else {
                double[] ss = smooth ? smoothSeries(stock, sm) : Arrays.copyOf(stock, n);
                Range r = rangeSingle(ss, sm);
                List<List<Point>> seg = buildPolylines(texture.highResWidth, texture.highResHeight, texture.sampleScale, lineStyle.curveSubdivision(), ss, sm, r.min, r.max, smooth);
                if (ENABLE_AREA_FILL) {
                    fillArea(seg, bounds, 0x1A60A5FA);
                }
                drawSeries(seg, bounds, UiThemeTokens.BLUE, lineStyle, texture.sampleScale);
            }
        });

        boolean highResVisible = detectVisibleContent(texture.highRes, texture.highResWidth, texture.highResHeight, false);
        texture.setHighResVisible(highResVisible);

        downsample(texture.highRes, texture.lowRes, texture.highResWidth, texture.highResHeight, texture.width, texture.height, lineStyle);
        boolean lowResVisible = detectVisibleContent(texture.lowRes, texture.width, texture.height, true);
        if (!lowResVisible && highResVisible) {
            downsampleFallback(texture.highRes, texture.lowRes, texture.width, texture.height);
            lowResVisible = detectVisibleContent(texture.lowRes, texture.width, texture.height, true);
        }
        if (lowResVisible && !highResVisible) {
            highResVisible = true;
            texture.setHighResVisible(true);
        }
        texture.setLowResVisible(lowResVisible);
        texture.setHasVisibleContent(lowResVisible);
    }

    /**
     * 预计算图表数据（纯 CPU 端），生成 PreparedChart。
     * 包括：数据平滑、范围计算、折线插值、零轴位置。
     */
    private static PreparedChart prepareChart(
            int plotWidth,
            int plotHeight,
            List<OverviewViewModel.FlowPoint> series,
            ChartPage page,
            LineMode lineMode,
            SmoothingMode smoothingMode,
            LineStyle lineStyle
    ) {
        if (plotWidth <= 0 || plotHeight <= 0 || series.isEmpty()) {
            return PreparedChart.EMPTY;
        }

        int n = series.size();
        double[] p = new double[n];
        double[] c = new double[n];
        double[] net = new double[n];
        double[] stock = new double[n];
        boolean[] fm = new boolean[n];
        boolean[] sm = new boolean[n];
        for (int i = 0; i < n; i++) {
            OverviewViewModel.FlowPoint point = series.get(i);
            p[i] = point.production();
            c[i] = point.consumption();
            net[i] = point.net();
            stock[i] = point.stock();
            fm[i] = point.hasFlow();
            sm[i] = point.hasStock();
        }

        int sampleScale = lineStyle.supersample();
        RectBounds bounds = new RectBounds(
                plotMinX(sampleScale),
                plotMinY(sampleScale),
                maxCoordX(plotWidth, sampleScale),
                maxCoordY(plotHeight, sampleScale)
        );
        boolean smooth = smoothingMode == SmoothingMode.SMOOTH;
        List<PreparedSeries> preparedSeries = new ArrayList<>();
        List<ChartHoverPoint> hoverPoints = new ArrayList<>();
        Double zeroAxisY = null;

        if (page == ChartPage.THROUGHPUT) {
            double[] ps = smooth ? smoothSeries(p, fm) : Arrays.copyOf(p, n);
            double[] cs = smooth ? smoothSeries(c, fm) : Arrays.copyOf(c, n);
            double[] ns = smooth ? smoothSeries(net, fm) : Arrays.copyOf(net, n);
            Range range = rangeThroughput(lineMode, ps, cs, ns, fm);
            if (range.min < 0.0 && range.max > 0.0) {
                zeroAxisY = clampD(valueY4(0.0, range.min, range.max, plotHeight, sampleScale), bounds.minY, bounds.maxY);
            }
            if (lineMode.showProduction()) {
                List<List<Point>> segments = buildPolylines(plotWidth, plotHeight, sampleScale, lineStyle.curveSubdivision(), ps, fm, range.min, range.max, smooth);
                if (!segments.isEmpty()) {
                    preparedSeries.add(new PreparedSeries(segments, UiThemeTokens.CYAN));
                }
                appendHoverPoints(hoverPoints, ChartSeriesType.PRODUCTION, series, ps, fm, range.min, range.max, plotWidth, plotHeight, sampleScale);
            }
            if (lineMode.showConsumption()) {
                List<List<Point>> segments = buildPolylines(plotWidth, plotHeight, sampleScale, lineStyle.curveSubdivision(), cs, fm, range.min, range.max, smooth);
                if (!segments.isEmpty()) {
                    preparedSeries.add(new PreparedSeries(segments, UiThemeTokens.AMBER));
                }
                appendHoverPoints(hoverPoints, ChartSeriesType.CONSUMPTION, series, cs, fm, range.min, range.max, plotWidth, plotHeight, sampleScale);
            }
            if (lineMode.showNet()) {
                List<List<Point>> segments = buildPolylines(plotWidth, plotHeight, sampleScale, lineStyle.curveSubdivision(), ns, fm, range.min, range.max, smooth);
                if (!segments.isEmpty()) {
                    preparedSeries.add(new PreparedSeries(segments, UiThemeTokens.EMERALD));
                }
                appendHoverPoints(hoverPoints, ChartSeriesType.NET, series, ns, fm, range.min, range.max, plotWidth, plotHeight, sampleScale);
            }
        } else {
            double[] ss = smooth ? smoothSeries(stock, sm) : Arrays.copyOf(stock, n);
            Range range = rangeSingle(ss, sm);
            List<List<Point>> segments = buildPolylines(plotWidth, plotHeight, sampleScale, lineStyle.curveSubdivision(), ss, sm, range.min, range.max, smooth);
            if (!segments.isEmpty()) {
                preparedSeries.add(new PreparedSeries(segments, UiThemeTokens.BLUE));
            }
            appendHoverPoints(hoverPoints, ChartSeriesType.STOCK, series, ss, sm, range.min, range.max, plotWidth, plotHeight, sampleScale);
        }

        return new PreparedChart(
                bounds,
                zeroAxisY,
                preparedSeries,
                List.copyOf(hoverPoints),
                zeroAxisY != null || !preparedSeries.isEmpty()
        );
    }

    /** 使用预计算数据在屏幕空间直接绘制向量图表 */
    private static void drawPreparedChartVector(GuiGraphics gfx, UiRect plot, PreparedChart prepared, LineStyle lineStyle) {
        if (prepared == null || prepared == PreparedChart.EMPTY || !prepared.hasVisibleContent()) {
            return;
        }

        // 将预计算的绘图区边界偏移到屏幕坐标
        RectBounds bounds = new RectBounds(
                plot.x() + prepared.bounds().minX,
                plot.y() + prepared.bounds().minY,
                plot.x() + prepared.bounds().maxX,
                plot.y() + prepared.bounds().maxY
        );

        gfx.enableScissor(plot.x() + 1, plot.y() + 1, plot.right() - 1, plot.bottom() - 1);
        ProjectionState projectionState = pushProjection(gfx.guiWidth(), gfx.guiHeight());
        try {
            RenderSystem.enableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            // 预乘 alpha 混合：Src=ONE, Dst=ONE_MINUS_SRC_ALPHA
            // 线段颜色已在 shader 中预乘 alpha，可防止多层叠加时边缘亮度异常
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );

            if (prepared.zeroAxisY() != null) {
                double zeroY = plot.y() + prepared.zeroAxisY();
                drawAaLine(
                        new Point(bounds.minX, zeroY),
                        new Point(bounds.maxX, zeroY),
                        0xBBD5F3,
                        lineStyle.zeroWidth(),
                        lineStyle.zeroWidth(),
                        lineStyle.feather(),
                        lineStyle.zeroAlpha(),
                        0.0f,
                        bounds,
                        lineStyle.supersample()
                );
            }

            for (PreparedSeries series : prepared.series()) {
                drawSeriesAtOffset(series.segments(), bounds, series.color(), lineStyle, lineStyle.supersample(), plot.x(), plot.y());
            }
        } finally {
            popProjection(projectionState);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gfx.disableScissor();
        }
    }
    /**
     * 将数据序列构建为多段折线。
     * 跳过无效数据点，将连续有效段分别转为折线；
     * 若启用平滑且段长>=3，则使用 Monotone Hermite 插值生成曲线。
     */
    private static List<List<Point>> buildPolylines(
            int hw,
            int hh,
            int sampleScale,
            float curveSubdivision,
            double[] values,
            boolean[] valid,
            double min,
            double max,
            boolean smooth
    ) {
        List<List<Point>> out = new ArrayList<>();
        int i = 0;
        while (i < values.length) {
            while (i < values.length && !valid[i]) i++;
            if (i >= values.length) break;
            int s = i;
            while (i < values.length && valid[i]) i++;
            int e = i - 1;

            List<Point> base = new ArrayList<>(e - s + 1);
            for (int k = s; k <= e; k++) {
                base.add(new Point(x4(k, values.length, hw, sampleScale), valueY4(values[k], min, max, hh, sampleScale)));
            }
            out.add(smooth && base.size() >= 3 ? monotone(base, hw, hh, sampleScale, curveSubdivision) : clampPoints(base, hw, hh, sampleScale));
        }
        return out;
    }

    private static void appendHoverPoints(
            List<ChartHoverPoint> out,
            ChartSeriesType type,
            List<OverviewViewModel.FlowPoint> source,
            double[] values,
            boolean[] valid,
            double min,
            double max,
            int plotWidth,
            int plotHeight,
            int sampleScale
    ) {
        if (out == null || source == null || values == null || valid == null) {
            return;
        }
        int size = Math.min(Math.min(source.size(), values.length), valid.length);
        if (size <= 0) {
            return;
        }
        for (int i = 0; i < size; i++) {
            if (!valid[i]) {
                continue;
            }
            OverviewViewModel.FlowPoint point = source.get(i);
            double localX = x4(i, size, plotWidth, sampleScale) / sampleScale;
            double localY = valueY4(values[i], min, max, plotHeight, sampleScale) / sampleScale;
            out.add(new ChartHoverPoint(type, localX, localY, point.slotIndex(), values[i]));
        }
    }

    private static List<Point> clampPoints(List<Point> in, int hw, int hh, int sampleScale) {
        List<Point> out = new ArrayList<>(in.size());
        double minX = plotMinX(sampleScale);
        double maxX = maxCoordX(hw, sampleScale);
        double minY = plotMinY(sampleScale);
        double maxY = maxCoordY(hh, sampleScale);
        for (Point p : in) {
            out.add(new Point(clampD(p.x, minX, maxX), clampD(p.y, minY, maxY)));
        }
        return out;
    }

    /**
     * Monotone Hermite 三次插值 —— 生成保持单调性的平滑曲线。
     * <p>
     * 算法流程：
     * <ol>
     *   <li>计算每段斜率 d[i]</li>
     *   <li>用相邻段斜率均值估计端点切线 m[i]</li>
     *   <li>Fritsch-Carlson 单调性约束：若 α² + β² > 9 则缩放切线</li>
     *   <li>三次 Hermite 基函数插值 (h00, h10, h01, h11) 生成子像素点</li>
     * </ol>
     * <p>
     * 魔法数字说明：
     * - 9.0：Fritsch-Carlson 定理的单调性阈值（α² + β² ≤ 9 保证单调）
     * - 1.0E-9：斜率为零的判定精度（避免浮点误差）
     * - 1.0E-6：相邻点最小间距（防除零）
     */
    private static List<Point> monotone(List<Point> base, int hw, int hh, int sampleScale, float curveSubdivision) {
        int n = base.size();
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = base.get(i).x;
            y[i] = base.get(i).y;
        }
        // 第 1 步：计算每段差商斜率
        double[] d = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            double h = Math.max(1.0E-6, x[i + 1] - x[i]);
            d[i] = (y[i + 1] - y[i]) / h;
        }
        // 第 2 步：估计端点切线（内点取相邻斜率均值，端点直接取相邻斜率）
        double[] m = new double[n];
        m[0] = d[0];
        m[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) m[i] = 0.5 * (d[i - 1] + d[i]);
        // 第 3 步：Fritsch-Carlson 单调性约束
        for (int i = 0; i < n - 1; i++) {
            if (Math.abs(d[i]) < 1.0E-9) {
                // 平坦段：两端切线归零
                m[i] = 0.0;
                m[i + 1] = 0.0;
            } else {
                double a = m[i] / d[i];       // α = m[i] / d[i]
                double b = m[i + 1] / d[i];   // β = m[i+1] / d[i]
                double s = a * a + b * b;
                if (s > 9.0) {
                    // 超出单调性圆，按 τ = 3/√(α²+β²) 缩放切线
                    double t = 3.0 / Math.sqrt(s);
                    m[i] = t * a * d[i];
                    m[i + 1] = t * b * d[i];
                }
            }
        }
        double minX = plotMinX(sampleScale);
        double maxX = maxCoordX(hw, sampleScale);
        double minY = plotMinY(sampleScale);
        double maxY = maxCoordY(hh, sampleScale);
        // 第 4 步：三次 Hermite 基函数插值
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            double x0 = x[i];
            double x1 = x[i + 1];
            double y0 = y[i];
            double y1 = y[i + 1];
            double h = Math.max(1.0E-6, x1 - x0);
            // 细分步数 = 段长 × 细分密度，保证曲线足够平滑
            int steps = Math.max(1, (int) Math.ceil(h * Math.max(1.0f, curveSubdivision)));
            int from = i == 0 ? 0 : 1; // 首段从 t=0 开始，后续段从 t=1/steps 开始避免重复端点
            for (int step = from; step <= steps; step++) {
                double t = step / (double) steps;
                double t2 = t * t;
                double t3 = t2 * t;
                // Hermite 基函数：h00(值起点), h10(切线起点), h01(值终点), h11(切线终点)
                double h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
                double h10 = t3 - 2.0 * t2 + t;
                double h01 = -2.0 * t3 + 3.0 * t2;
                double h11 = t3 - t2;
                double xx = x0 + h * t;
                double yy = h00 * y0 + h10 * h * m[i] + h01 * y1 + h11 * h * m[i + 1];
                out.add(new Point(clampD(xx, minX, maxX), clampD(yy, minY, maxY)));
            }
        }
        return out;
    }

    /**
     * 面积填充 —— 将折线下方区域填充为半透明色。
     * 实现方式：按列光栅化，对每列求折线的最高 Y 值，然后从该点向下填充到底部。
     */
    private static void fillArea(List<List<Point>> segments, RectBounds bounds, int color) {
        int premult = premultiply(color);
        int r = (premult >>> 16) & 0xFF;
        int g = (premult >>> 8) & 0xFF;
        int b = premult & 0xFF;
        int a = (premult >>> 24) & 0xFF;
        double bottom = bounds.maxY;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean hasGeometry = false;
        for (List<Point> segment : segments) {
            if (segment.size() < 2) {
                continue;
            }
            int startColumn = clamp((int) Math.floor(segment.getFirst().x), (int) Math.floor(bounds.minX), (int) Math.ceil(bounds.maxX));
            int endColumn = clamp((int) Math.ceil(segment.getLast().x), (int) Math.floor(bounds.minX), (int) Math.ceil(bounds.maxX));
            if (endColumn <= startColumn) {
                continue;
            }

            double[] top = new double[endColumn - startColumn];
            Arrays.fill(top, Double.NaN);

            for (int i = 0; i + 1 < segment.size(); i++) {
                Point p0 = clampPoint(segment.get(i), bounds);
                Point p1 = clampPoint(segment.get(i + 1), bounds);
                if (Math.abs(p1.x - p0.x) < 1.0E-6 && Math.abs(p1.y - p0.y) < 1.0E-6) {
                    continue;
                }
                rasterizeAreaColumns(top, startColumn, endColumn, p0, p1, bounds);
            }

            for (int column = startColumn; column < endColumn; column++) {
                double yTop = top[column - startColumn];
                if (!Double.isFinite(yTop)) {
                    continue;
                }
                double xl = clampD(column, bounds.minX, bounds.maxX);
                double xr = clampD(column + 1.0, bounds.minX, bounds.maxX);
                if (xr - xl < 1.0E-6) {
                    continue;
                }
                addFillQuad(buffer, xl, xr, yTop, yTop, bottom, r, g, b, a);
                hasGeometry = true;
            }
        }
        if (!hasGeometry) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        MeshData mesh = buffer.buildOrThrow();
        BufferUploader.drawWithShader(mesh);
    }

    /**
     * 绘制折线系列。
     * 对每段线段使用自定义着色器绘制抗锯齿线（核心线 + 辉光）。
     */
    private static void drawSeries(List<List<Point>> segments, RectBounds bounds, int color, LineStyle lineStyle, int sampleScale) {
        int rgb = color & 0x00FFFFFF;
        for (List<Point> segment : segments) {
            for (int i = 0; i + 1 < segment.size(); i++) {
                ClippedSegment clipped = clipSegment(segment.get(i), segment.get(i + 1), bounds);
                if (clipped == null) {
                    continue;
                }
                drawAaLine(
                        clipped.a,
                        clipped.b,
                        rgb,
                        lineStyle.coreWidth(),
                        lineStyle.glowWidth(),
                        lineStyle.feather(),
                        lineStyle.coreAlpha(),
                        lineStyle.glowAlpha(),
                        bounds,
                        sampleScale
                );
            }
        }
    }

    /** 在指定偏移位置绘制折线系列 */
    private static void drawSeriesAtOffset(
            List<List<Point>> segments,
            RectBounds bounds,
            int color,
            LineStyle lineStyle,
            int sampleScale,
            double offsetX,
            double offsetY
    ) {
        int rgb = color & 0x00FFFFFF;
        for (List<Point> segment : segments) {
            for (int i = 0; i + 1 < segment.size(); i++) {
                Point a = new Point(segment.get(i).x + offsetX, segment.get(i).y + offsetY);
                Point b = new Point(segment.get(i + 1).x + offsetX, segment.get(i + 1).y + offsetY);
                ClippedSegment clipped = clipSegment(a, b, bounds);
                if (clipped == null) {
                    continue;
                }
                drawAaLine(
                        clipped.a,
                        clipped.b,
                        rgb,
                        lineStyle.coreWidth(),
                        lineStyle.glowWidth(),
                        lineStyle.feather(),
                        lineStyle.coreAlpha(),
                        lineStyle.glowAlpha(),
                        bounds,
                        sampleScale
                );
            }
        }
    }

    /**
     * 使用自定义着色器绘制单条抗锯齿线段。
     * 线段被扩展为四边形，通过 shader 实现距离场抗锯齿。
     * 无着色器可用时回退到简易矩形绘制。
     */
    private static void drawAaLine(
            Point a,
            Point b,
            int rgb,
            float coreWidth,
            float glowWidth,
            float feather,
            float coreAlpha,
            float glowAlpha,
            RectBounds bounds,
            int sampleScale
    ) {
        ShaderInstance shader = ChartRenderShaders.chartLineShader();
        float scaledCoreWidth = coreWidth * sampleScale;
        float scaledGlowWidth = glowWidth * sampleScale;
        float scaledFeather = feather * sampleScale;
        if (shader == null) {
            drawFallbackLine(a, b, rgb, scaledGlowWidth, glowAlpha);
            drawFallbackLine(a, b, rgb, scaledCoreWidth, coreAlpha);
            return;
        }

        float outerHalf = Math.max(scaledCoreWidth, scaledGlowWidth) * 0.5f + scaledFeather;
        // 计算线段的切线方向 (tx, ty) 和法线方向 (nx, ny)
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double len = Math.hypot(dx, dy);
        double tx = len > 1.0E-6 ? dx / len : 1.0;
        double ty = len > 1.0E-6 ? dy / len : 0.0;
        double nx = -ty;  // 法线 = 切线旋转 90°
        double ny = tx;

        // 沿切线/法线方向向外扩展 outerHalf，构成包含线段+辉光+羽化的四边形
        Point s = new Point(a.x - tx * outerHalf, a.y - ty * outerHalf);
        Point e = new Point(b.x + tx * outerHalf, b.y + ty * outerHalf);
        Point v0 = new Point(s.x - nx * outerHalf, s.y - ny * outerHalf);
        Point v1 = new Point(e.x - nx * outerHalf, e.y - ny * outerHalf);
        Point v2 = new Point(e.x + nx * outerHalf, e.y + ny * outerHalf);
        Point v3 = new Point(s.x + nx * outerHalf, s.y + ny * outerHalf);

        // 设置自定义 shader uniform：线段端点、宽度参数、颜色
        // shader 根据片元到线段的距离场计算透明度，实现核心线+辉光效果

        RenderSystem.setShader(() -> shader);
        setUniform(shader, "LineStartEnd", (float) a.x, (float) a.y, (float) b.x, (float) b.y);
        setUniform(shader, "LineMetrics", scaledCoreWidth * 0.5f, scaledGlowWidth * 0.5f, scaledFeather, glowAlpha);
        setUniform(shader, "LineColor", red01(rgb), green01(rgb), blue01(rgb), coreAlpha);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        addPositionVertex(buffer, v0);
        addPositionVertex(buffer, v1);
        addPositionVertex(buffer, v2);
        addPositionVertex(buffer, v3);
        MeshData mesh = buffer.buildOrThrow();
        BufferUploader.drawWithShader(mesh);
    }

    /**
     * 超采样降采样。
     * 使用自定义 downsample shader 将高分辨率纹理缩放到显示分辨率。
     */
    private static void downsample(RenderTarget source, RenderTarget target, int sourceW, int sourceH, int targetW, int targetH, LineStyle lineStyle) {
        ShaderInstance shader = ChartRenderShaders.chartDownsampleShader();
        if (shader == null) {
            downsampleFallback(source, target, targetW, targetH);
            return;
        }

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        ScissorState scissorState = captureScissorState();
        target.bindWrite(true);
        RenderSystem.viewport(0, 0, targetW, targetH);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.disableScissor();

        ProjectionState projectionState = pushProjection(targetW, targetH);
        try {
            clearTarget(target);
            target.bindWrite(true);
            RenderSystem.viewport(0, 0, targetW, targetH);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, source.getColorTextureId());
            setUniform(shader, "InvSourceSize", 1.0f / sourceW, 1.0f / sourceH);
            setUniform(shader, "SampleScale", (float) lineStyle.supersample());
            setUniform(shader, "ResolveParams", lineStyle.resolveEdgeWeight(), lineStyle.resolveInnerWeight(), lineStyle.resolveAlphaBoost(), 0.0f);

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            buffer.addVertex(0.0f, 0.0f, 0.0f);
            buffer.addVertex(0.0f, targetH, 0.0f);
            buffer.addVertex(targetW, targetH, 0.0f);
            buffer.addVertex(targetW, 0.0f, 0.0f);
            MeshData mesh = buffer.buildOrThrow();
            BufferUploader.drawWithShader(mesh);
        } finally {
            popProjection(projectionState);
            main.bindWrite(true);
            restoreScissorState(scissorState);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** 回退直线绘制（无自定义着色器时使用） */
    private static void drawFallbackLine(Point a, Point b, int rgb, float width, float alpha) {
        if (alpha <= 0.0f || width <= 0.0f) {
            return;
        }

        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double len = Math.hypot(dx, dy);
        if (len <= 1.0E-6) {
            return;
        }

        double half = width * 0.5;
        double nx = -dy / len * half;
        double ny = dx / len * half;
        int argb = ((clamp(Math.round(alpha * 255.0f), 0, 255) & 0xFF) << 24) | (rgb & 0x00FFFFFF);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int bColor = argb & 0xFF;
        int aColor = (argb >>> 24) & 0xFF;
        addColorVertex(buffer, (float) (a.x - nx), (float) (a.y - ny), r, g, bColor, aColor);
        addColorVertex(buffer, (float) (b.x - nx), (float) (b.y - ny), r, g, bColor, aColor);
        addColorVertex(buffer, (float) (b.x + nx), (float) (b.y + ny), r, g, bColor, aColor);
        addColorVertex(buffer, (float) (a.x + nx), (float) (a.y + ny), r, g, bColor, aColor);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        MeshData mesh = buffer.buildOrThrow();
        BufferUploader.drawWithShader(mesh);
    }

    /** 回退降采样（使用 GL_LINEAR 直接缩放） */
    private static void downsampleFallback(RenderTarget source, RenderTarget target, int targetW, int targetH) {
        source.setFilterMode(GL11C.GL_LINEAR);

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        ScissorState scissorState = captureScissorState();
        target.bindWrite(true);
        RenderSystem.viewport(0, 0, targetW, targetH);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.disableScissor();

        ProjectionState projectionState = pushProjection(targetW, targetH);
        try {
            clearTarget(target);
            target.bindWrite(true);
            RenderSystem.viewport(0, 0, targetW, targetH);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.setShaderTexture(0, source.getColorTextureId());

            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            buffer.addVertex(0.0f, 0.0f, 0.0f).setUv(0.0f, 1.0f);
            buffer.addVertex(0.0f, targetH, 0.0f).setUv(0.0f, 0.0f);
            buffer.addVertex(targetW, targetH, 0.0f).setUv(1.0f, 0.0f);
            buffer.addVertex(targetW, 0.0f, 0.0f).setUv(1.0f, 1.0f);
            MeshData mesh = buffer.buildOrThrow();
            BufferUploader.drawWithShader(mesh);
        } finally {
            popProjection(projectionState);
            main.bindWrite(true);
            restoreScissorState(scissorState);
            source.setFilterMode(GL11C.GL_NEAREST);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** 将缓存的纹理图层绘制到屏幕 */
    private static void drawLayer(GuiGraphics gfx, UiRect plot, CachedChartTexture layer) {
        if (layer == null || layer == CachedChartTexture.EMPTY || layer.lowRes == null) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, layer.lowRes.getColorTextureId());

        Matrix4f pose = gfx.pose().last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(pose, plot.x(), plot.y(), 0.0f).setUv(0.0f, 1.0f);
        buffer.addVertex(pose, plot.x(), plot.bottom(), 0.0f).setUv(0.0f, 0.0f);
        buffer.addVertex(pose, plot.right(), plot.bottom(), 0.0f).setUv(1.0f, 0.0f);
        buffer.addVertex(pose, plot.right(), plot.y(), 0.0f).setUv(1.0f, 1.0f);
        MeshData mesh = buffer.buildOrThrow();
        BufferUploader.drawWithShader(mesh);
    }

    /** 调试模式：直接向量绘制（不使用纹理缓存） */
    private static void drawDirectVectorDebug(
            GuiGraphics gfx,
            Font font,
            UiRect plot,
            List<OverviewViewModel.FlowPoint> series,
            ChartPage page,
            LineMode lineMode,
            SmoothingMode smoothingMode,
            LineStyle lineStyle
    ) {
        int n = series.size();
        double[] p = new double[n];
        double[] c = new double[n];
        double[] net = new double[n];
        double[] stock = new double[n];
        boolean[] fm = new boolean[n];
        boolean[] sm = new boolean[n];
        for (int i = 0; i < n; i++) {
            OverviewViewModel.FlowPoint point = series.get(i);
            p[i] = point.production();
            c[i] = point.consumption();
            net[i] = point.net();
            stock[i] = point.stock();
            fm[i] = point.hasFlow();
            sm[i] = point.hasStock();
        }

        boolean smooth = smoothingMode == SmoothingMode.SMOOTH;
        RectBounds bounds = new RectBounds(
                plot.x() + plotMinX(1),
                plot.y() + plotMinY(1),
                plot.x() + maxCoordX(plot.width(), 1),
                plot.y() + maxCoordY(plot.height(), 1)
        );

        gfx.enableScissor(plot.x() + 1, plot.y() + 1, plot.right() - 1, plot.bottom() - 1);
        ProjectionState projectionState = pushProjection(gfx.guiWidth(), gfx.guiHeight());
        try {
            RenderSystem.enableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );

            if (page == ChartPage.THROUGHPUT) {
                double[] ps = smooth ? smoothSeries(p, fm) : Arrays.copyOf(p, n);
                double[] cs = smooth ? smoothSeries(c, fm) : Arrays.copyOf(c, n);
                double[] ns = smooth ? smoothSeries(net, fm) : Arrays.copyOf(net, n);
                Range range = rangeThroughput(lineMode, ps, cs, ns, fm);
                if (range.min < 0.0 && range.max > 0.0) {
                    double zy = plot.y() + valueY4(0.0, range.min, range.max, plot.height(), 1);
                    drawAaLine(
                            new Point(bounds.minX, zy),
                            new Point(bounds.maxX, zy),
                            0xBBD5F3,
                            lineStyle.zeroWidth(),
                            lineStyle.zeroWidth(),
                            lineStyle.feather(),
                            lineStyle.zeroAlpha(),
                            0.0f,
                            bounds,
                            1
                    );
                }
                if (lineMode.showProduction()) {
                    drawSeries(offsetSegments(buildPolylines(plot.width(), plot.height(), 1, lineStyle.curveSubdivision(), ps, fm, range.min, range.max, smooth), plot.x(), plot.y()), bounds, UiThemeTokens.CYAN, lineStyle, 1);
                }
                if (lineMode.showConsumption()) {
                    drawSeries(offsetSegments(buildPolylines(plot.width(), plot.height(), 1, lineStyle.curveSubdivision(), cs, fm, range.min, range.max, smooth), plot.x(), plot.y()), bounds, UiThemeTokens.AMBER, lineStyle, 1);
                }
                if (lineMode.showNet()) {
                    drawSeries(offsetSegments(buildPolylines(plot.width(), plot.height(), 1, lineStyle.curveSubdivision(), ns, fm, range.min, range.max, smooth), plot.x(), plot.y()), bounds, UiThemeTokens.EMERALD, lineStyle, 1);
                }
                return;
            }

            double[] ss = smooth ? smoothSeries(stock, sm) : Arrays.copyOf(stock, n);
            Range range = rangeSingle(ss, sm);
            drawSeries(offsetSegments(buildPolylines(plot.width(), plot.height(), 1, lineStyle.curveSubdivision(), ss, sm, range.min, range.max, smooth), plot.x(), plot.y()), bounds, UiThemeTokens.BLUE, lineStyle, 1);
        } finally {
            popProjection(projectionState);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gfx.disableScissor();
        }
    }

    /** 调试模式/回退模式：覆盖渲染 */
    private static void drawOverlayFallback(
            GuiGraphics gfx,
            Font font,
            UiRect plot,
            List<OverviewViewModel.FlowPoint> series,
            ChartPage page,
            LineMode lineMode,
            SmoothingMode smoothingMode,
            LineStyle lineStyle,
            boolean debugMode,
            boolean drawSeriesFallback
    ) {
        int n = series.size();
        double[] p = new double[n];
        double[] c = new double[n];
        double[] net = new double[n];
        double[] stock = new double[n];
        boolean[] fm = new boolean[n];
        boolean[] sm = new boolean[n];
        for (int i = 0; i < n; i++) {
            OverviewViewModel.FlowPoint point = series.get(i);
            p[i] = point.production();
            c[i] = point.consumption();
            net[i] = point.net();
            stock[i] = point.stock();
            fm[i] = point.hasFlow();
            sm[i] = point.hasStock();
        }

        boolean smooth = smoothingMode == SmoothingMode.SMOOTH;
        if (debugMode) {
            gfx.fill(plot.right() - 4, plot.y() + 2, plot.right() - 2, plot.y() + 4, 0xFFFF4DFF);
        }

        if (page == ChartPage.THROUGHPUT) {
            double[] ps = smooth ? smoothSeries(p, fm) : Arrays.copyOf(p, n);
            double[] cs = smooth ? smoothSeries(c, fm) : Arrays.copyOf(c, n);
            double[] ns = smooth ? smoothSeries(net, fm) : Arrays.copyOf(net, n);
            Range range = rangeThroughput(lineMode, ps, cs, ns, fm);
            if (drawSeriesFallback && lineMode.showProduction()) {
                drawDebugSeries(gfx, plot, buildPolylines(plot.width() * lineStyle.supersample(), plot.height() * lineStyle.supersample(), lineStyle.supersample(), lineStyle.curveSubdivision(), ps, fm, range.min, range.max, smooth), UiThemeTokens.CYAN, debugMode, lineStyle.supersample());
            }
            if (drawSeriesFallback && lineMode.showConsumption()) {
                drawDebugSeries(gfx, plot, buildPolylines(plot.width() * lineStyle.supersample(), plot.height() * lineStyle.supersample(), lineStyle.supersample(), lineStyle.curveSubdivision(), cs, fm, range.min, range.max, smooth), UiThemeTokens.AMBER, debugMode, lineStyle.supersample());
            }
            if (drawSeriesFallback && lineMode.showNet()) {
                drawDebugSeries(gfx, plot, buildPolylines(plot.width() * lineStyle.supersample(), plot.height() * lineStyle.supersample(), lineStyle.supersample(), lineStyle.curveSubdivision(), ns, fm, range.min, range.max, smooth), UiThemeTokens.EMERALD, debugMode, lineStyle.supersample());
            }
            if (debugMode) {
                drawDebugScale(gfx, font, plot, range, lastValidValue(lineMode, ps, cs, ns, fm));
            }
            return;
        }

        double[] ss = smooth ? smoothSeries(stock, sm) : Arrays.copyOf(stock, n);
        Range range = rangeSingle(ss, sm);
        if (drawSeriesFallback) {
            drawDebugSeries(gfx, plot, buildPolylines(plot.width() * lineStyle.supersample(), plot.height() * lineStyle.supersample(), lineStyle.supersample(), lineStyle.curveSubdivision(), ss, sm, range.min, range.max, smooth), UiThemeTokens.BLUE, debugMode, lineStyle.supersample());
        }
        if (debugMode) {
            drawDebugScale(gfx, font, plot, range, lastValidValue(ss, sm));
        }
    }

    private static void drawDebugSeries(GuiGraphics gfx, UiRect plot, List<List<Point>> segments, int color, boolean debugMode, int sampleScale) {
        int core = 0xFF000000 | (color & 0x00FFFFFF);
        for (List<Point> segment : segments) {
            for (int i = 0; i + 1 < segment.size(); i++) {
                Point a = segment.get(i);
                Point b = segment.get(i + 1);
                int x0 = plot.x() + clamp((int) Math.round(a.x / sampleScale), 1, Math.max(1, plot.width() - 2));
                int y0 = plot.y() + clamp((int) Math.round(a.y / sampleScale), 1, Math.max(1, plot.height() - 2));
                int x1 = plot.x() + clamp((int) Math.round(b.x / sampleScale), 1, Math.max(1, plot.width() - 2));
                int y1 = plot.y() + clamp((int) Math.round(b.y / sampleScale), 1, Math.max(1, plot.height() - 2));
                RenderUtils.drawLine(gfx, x0, y0, x1, y1, core);
                if (debugMode) {
                    gfx.fill(x1 - 1, y1 - 1, x1 + 1, y1 + 1, 0xAAFFFFFF);
                }
            }
        }
    }

    private static void drawDebugScale(GuiGraphics gfx, Font font, UiRect plot, Range range, double last) {
        String maxText = "max " + formatMetric(range.max);
        String minText = "min " + formatMetric(range.min);
        String lastText = "last " + formatMetric(last);
        gfx.drawString(font, maxText, plot.x() + 6, plot.y() + 14, 0xFFD7E7FF);
        gfx.drawString(font, minText, plot.x() + 6, plot.bottom() - 10, 0xFFB8C4D4);
        gfx.drawString(font, lastText, plot.right() - font.width(lastText) - 6, plot.y() + 14, 0xFFFFE083);
    }

    private static void drawPipelineBadge(GuiGraphics gfx, Font font, UiRect plot, CachedChartTexture layer, boolean useDirectFallback) {
        String suffix = layer == null || layer == CachedChartTexture.EMPTY
                ? "H0 L0"
                : "H" + (layer.highResVisible() ? "1" : "0") + " L" + (layer.lowResVisible() ? "1" : "0");
        String text = (useDirectFallback ? "PIPELINE: FALLBACK " : "PIPELINE: GPU ") + suffix;
        int textColor = useDirectFallback ? 0xFFFFA6A6 : 0xFF9FFFC7;
        int bgColor = useDirectFallback ? 0xCC4A1620 : 0xCC143524;
        int borderColor = useDirectFallback ? 0xFFE45A7A : 0xFF38D980;
        int width = font.width(text) + 8;
        UiRect badge = new UiRect(plot.right() - width - 6, plot.bottom() - 16, width, 12);
        gfx.fill(badge.x(), badge.y(), badge.right(), badge.bottom(), bgColor);
        RenderUtils.drawBorder(gfx, badge, borderColor);
        gfx.drawString(font, text, badge.x() + 4, badge.y() + 2, textColor);
    }

    private static void drawPipelineBadge(GuiGraphics gfx, Font font, UiRect plot, String text) {
        int width = font.width(text) + 8;
        UiRect badge = new UiRect(plot.right() - width - 6, plot.bottom() - 16, width, 12);
        gfx.fill(badge.x(), badge.y(), badge.right(), badge.bottom(), 0xCC143524);
        RenderUtils.drawBorder(gfx, badge, 0xFF38D980);
        gfx.drawString(font, text, badge.x() + 4, badge.y() + 2, 0xFF9FFFC7);
    }

    /** 获取最后一个有效数据值（调试显示用） */
    private static double lastValidValue(LineMode lineMode, double[] p, double[] c, double[] n, boolean[] mask) {
        double last = 0.0;
        for (int i = 0; i < mask.length; i++) {
            if (!mask[i]) {
                continue;
            }
            if (lineMode.showNet()) {
                last = n[i];
            } else if (lineMode.showConsumption()) {
                last = c[i];
            } else {
                last = p[i];
            }
        }
        return last;
    }

    /** 获取最后一个有效数据值（调试显示用） */
    private static double lastValidValue(double[] values, boolean[] mask) {
        double last = 0.0;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) {
                last = values[i];
            }
        }
        return last;
    }

    /** 对线段列表整体施加偏移（纹理坐标 → 屏幕坐标） */
    private static List<List<Point>> offsetSegments(List<List<Point>> segments, double dx, double dy) {
        List<List<Point>> shifted = new ArrayList<>(segments.size());
        for (List<Point> segment : segments) {
            List<Point> shiftedSegment = new ArrayList<>(segment.size());
            for (Point point : segment) {
                shiftedSegment.add(new Point(point.x + dx, point.y + dy));
            }
            shifted.add(shiftedSegment);
        }
        return shifted;
    }

    /** 格式化数值为紧凑表示（用于调试标签） */
    private static String formatMetric(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000.0) {
            return String.format(java.util.Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000.0) {
            return String.format(java.util.Locale.ROOT, "%.2fM", value / 1_000_000.0);
        }
        if (abs >= 1_000.0) {
            return String.format(java.util.Locale.ROOT, "%.2fK", value / 1_000.0);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /** 计算吞吐量模式下所有可见系列的数值范围 */
    private static Range rangeThroughput(LineMode mode, double[] p, double[] c, double[] n, boolean[] mask) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < p.length; i++) {
            if (!mask[i]) continue;
            if (mode.showProduction()) {
                min = Math.min(min, p[i]);
                max = Math.max(max, p[i]);
            }
            if (mode.showConsumption()) {
                min = Math.min(min, c[i]);
                max = Math.max(max, c[i]);
            }
            if (mode.showNet()) {
                min = Math.min(min, n[i]);
                max = Math.max(max, n[i]);
            }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) return new Range(0.0, 1.0);
        if (Math.abs(max - min) < 1.0E-6) return new Range(min, min + 1.0);
        return new Range(min, max);
    }

    /** 计算单序列的数值范围 */
    private static Range rangeSingle(double[] values, boolean[] mask) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < values.length; i++) {
            if (!mask[i]) continue;
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) return new Range(0.0, 1.0);
        if (Math.abs(max - min) < 1.0E-6) return new Range(min, min + 1.0);
        return new Range(min, max);
    }

    /**
     * EMA 指数移动平均平滑。
     * 在每个连续有效段内独立应用，平滑因子 α=0.45。
     */
    private static double[] smoothSeries(double[] values, boolean[] validMask) {
        double[] out = Arrays.copyOf(values, values.length);
        int i = 0;
        while (i < values.length) {
            while (i < values.length && !validMask[i]) i++;
            if (i >= values.length) break;
            int s = i;
            while (i < values.length && validMask[i]) i++;
            int e = i - 1;
            double ema = values[s];
            out[s] = ema;
            for (int j = s + 1; j <= e; j++) {
                ema = ema * 0.55 + values[j] * 0.45;
                out[j] = ema;
            }
        }
        return out;
    }

    /** FNV-1a 风格的数据指纹计算（用于缓存键） */
    private static long seriesFingerprint(List<OverviewViewModel.FlowPoint> series) {
        long hash = 0xCBF29CE484222325L;
        for (OverviewViewModel.FlowPoint p : series) {
            hash ^= p.slotIndex();
            hash *= 0x100000001B3L;
            hash = mix(hash, p.production());
            hash = mix(hash, p.consumption());
            hash = mix(hash, p.net());
            hash = mix(hash, p.stock());
            hash ^= p.hasFlow() ? 0x9E3779B97F4A7C15L : 0xC2B2AE3D27D4EB4FL;
            hash *= 0x100000001B3L;
            hash ^= p.hasStock() ? 0x165667B19E3779F9L : 0x85EBCA77C2B2AE63L;
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    private static long mix(long seed, double value) {
        long bits = Double.doubleToLongBits(value);
        seed ^= bits + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2);
        return seed;
    }

    /**
     * 将数据索引映射为超采样坐标系的 X 值。
     * 三套坐标系：
     * 1. 数据索引 [0, size-1]
     * 2. 超采样坐标 [plotMinX, maxCoordX]（比显示分辨率大 sampleScale 倍）
     * 3. 屏幕坐标（最终显示，= 超采样/sampleScale + plot偏移）
     */
    private static double x4(int index, int size, int hw, int sampleScale) {
        double min = plotMinX(sampleScale);
        double max = maxCoordX(hw, sampleScale);
        if (size <= 1) return min;
        return min + (index / (double) (size - 1)) * (max - min);
    }

    /** 将数据值映射为超采样坐标系的 Y 值（Y 轴反向：值越大 Y 越小） */
    private static double valueY4(double value, double min, double max, int hh, int sampleScale) {
        double low = plotMinY(sampleScale);
        double high = maxCoordY(hh, sampleScale);
        double range = Math.max(1.0E-6, max - min);
        double ratio = (value - min) / range;
        return high - ratio * (high - low);
    }

    private static double plotMinX(int sampleScale) {
        return sampleScale * PLOT_PAD_X_SCALE;
    }

    private static double plotMinY(int sampleScale) {
        return sampleScale * PLOT_PAD_TOP_SCALE;
    }

    private static double maxCoordX(int extent, int sampleScale) {
        return Math.max(plotMinX(sampleScale), extent - sampleScale * PLOT_PAD_END_SCALE);
    }

    private static double maxCoordY(int extent, int sampleScale) {
        return Math.max(plotMinY(sampleScale), extent - sampleScale * PLOT_PAD_END_SCALE);
    }

    /** 绘制图表区域顶部的控制按钮 */
    private static void drawHeaderButton(GuiGraphics gfx, Font font, UiRect rect, String text, boolean active, boolean enabled) {
        int bg = active ? 0xAA23456A : UiThemeTokens.TAB_INACTIVE;
        int border = active ? UiThemeTokens.CYAN : UiThemeTokens.SECTION_BORDER;
        int textColor = active ? UiThemeTokens.TEXT : UiThemeTokens.TEXT_MUTED;
        if (!enabled) {
            bg = 0x66192334;
            border = 0x664A5B72;
            textColor = 0xFF8A97AA;
        }
        gfx.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), bg);
        RenderUtils.drawBorder(gfx, rect, border);
        gfx.drawString(font, RenderUtils.ellipsis(font, text, rect.width() - 8), rect.x() + 4, rect.y() + 3, textColor);
    }

    /** 在指定渲染目标上执行绘制操作（临时切换绑定） */
    private static void withTarget(RenderTarget target, int width, int height, RectBounds bounds, Runnable runnable) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        ScissorState scissorState = captureScissorState();
        target.bindWrite(true);
        RenderSystem.viewport(0, 0, width, height);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );

        ProjectionState projectionState = pushProjection(width, height);
        try {
            runnable.run();
        } finally {
            popProjection(projectionState);
            main.bindWrite(true);
            restoreScissorState(scissorState);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /** 捕获当前裁剪测试状态 */
    private static ScissorState captureScissorState() {
        boolean enabled = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        int[] box = new int[4];
        GL11C.glGetIntegerv(GL11C.GL_SCISSOR_BOX, box);
        return new ScissorState(enabled, box[0], box[1], box[2], box[3]);
    }

    /** 恢复裁剪测试状态 */
    private static void restoreScissorState(ScissorState state) {
        if (state == null || !state.enabled) {
            RenderSystem.disableScissor();
            return;
        }
        RenderSystem.enableScissor(state.x, state.y, state.width, state.height);
    }

    /**
     * 检测渲染目标是否包含可见内容。
     * <p>
     * 双策略检测：
     * - exhaustive=true：读取全部像素，阈值 alpha>1（用于降采样后的低分辨率纹理）
     * - exhaustive=false：采样网格检测，阈值 alpha>8（用于高分辨率纹理的快速检查）
     * - 采样网格参数：列数=width/24（最少 4 最多 10），行数=height/18（最少 3 最多 8）
     */
    private static boolean detectVisibleContent(RenderTarget target, int width, int height, boolean exhaustive) {
        if (target == null || width <= 0 || height <= 0) {
            return false;
        }

        ScissorState scissorState = captureScissorState();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();

        try {
            target.bindWrite(false);
            RenderSystem.disableScissor();
            if (exhaustive) {
                ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
                GL11C.glReadPixels(0, 0, width, height, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixels);
                for (int i = 3; i < pixels.limit(); i += 4) {
                    if ((pixels.get(i) & 0xFF) > 1) {
                        return true;
                    }
                }
                return false;
            }

            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            int sampleCols = Math.max(4, Math.min(10, width / 24));
            int sampleRows = Math.max(3, Math.min(8, height / 18));
            for (int row = 0; row < sampleRows; row++) {
                int py = sampleRows == 1
                        ? height / 2
                        : Math.round(row * (Math.max(1, height - 1)) / (float) (sampleRows - 1));
                for (int col = 0; col < sampleCols; col++) {
                    int px = sampleCols == 1
                            ? width / 2
                            : Math.round(col * (Math.max(1, width - 1)) / (float) (sampleCols - 1));
                    pixel.clear();
                    GL11C.glReadPixels(
                            clamp(px, 0, Math.max(0, width - 1)),
                            clamp(py, 0, Math.max(0, height - 1)),
                            1,
                            1,
                            GL11C.GL_RGBA,
                            GL11C.GL_UNSIGNED_BYTE,
                            pixel
                    );
                    if ((pixel.get(3) & 0xFF) > 8) {
                        return true;
                    }
                }
            }
            return false;
        } finally {
            main.bindWrite(true);
            restoreScissorState(scissorState);
        }
    }

    /** 推入正交投影矩阵 */
    private static ProjectionState pushProjection(int width, int height) {
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0f, width, height, 0.0f, -1000.0f, 1000.0f), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        return new ProjectionState(modelView);
    }

    /** 弹出并恢复投影矩阵 */
    private static void popProjection(ProjectionState state) {
        state.modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    /** 清除渲染目标为全透明 */
    private static void clearTarget(RenderTarget target) {
        if (target == null) {
            return;
        }
        ScissorState scissorState = captureScissorState();
        RenderSystem.disableScissor();
        target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        try {
            target.clear(Minecraft.ON_OSX);
        } finally {
            restoreScissorState(scissorState);
        }
    }

    private static void addColorVertex(BufferBuilder buffer, float x, float y, int r, int g, int b, int a) {
        buffer.addVertex(x, y, 0.0f).setColor(r, g, b, a);
    }

    private static void addFillQuad(BufferBuilder buffer, double xl, double xr, double yLeft, double yRight, double bottom, int r, int g, int b, int a) {
        addColorVertex(buffer, (float) xl, (float) bottom, r, g, b, a);
        addColorVertex(buffer, (float) xl, (float) yLeft, r, g, b, a);
        addColorVertex(buffer, (float) xr, (float) yRight, r, g, b, a);
        addColorVertex(buffer, (float) xr, (float) bottom, r, g, b, a);
    }

    /** 对单条线段进行列光栅化 —— 在每列的中心点(column+0.5)处采样线段高度 */
    private static void rasterizeAreaColumns(double[] top, int startColumn, int endColumn, Point p0, Point p1, RectBounds bounds) {
        // 垂直或近垂直线段：直接取较小 Y 写入所在列
        if (Math.abs(p1.x - p0.x) < 1.0E-6) {
            int column = clamp((int) Math.floor(p0.x), startColumn, Math.max(startColumn, endColumn - 1));
            writeColumnTop(top, startColumn, column, Math.min(p0.y, p1.y));
            return;
        }

        double left = Math.max(Math.min(p0.x, p1.x), startColumn);
        double right = Math.min(Math.max(p0.x, p1.x), endColumn);
        int colStart = clamp((int) Math.floor(left), startColumn, endColumn);
        int colEnd = clamp((int) Math.ceil(right), startColumn, endColumn);
        double dx = p1.x - p0.x;
        double dy = p1.y - p0.y;
        for (int column = colStart; column < colEnd; column++) {
            // 中心采样：在列中点 x=column+0.5 处计算线段的 Y 值
            double sampleX = clampD(column + 0.5, Math.min(p0.x, p1.x), Math.max(p0.x, p1.x));
            double t = (sampleX - p0.x) / dx;
            double y = clampD(p0.y + dy * t, bounds.minY, bounds.maxY);
            writeColumnTop(top, startColumn, column, y);
        }
    }

    private static void writeColumnTop(double[] top, int startColumn, int column, double y) {
        int index = column - startColumn;
        if (index < 0 || index >= top.length) {
            return;
        }
        if (!Double.isFinite(top[index])) {
            top[index] = y;
            return;
        }
        top[index] = Math.min(top[index], y);
    }

    private static void addPositionVertex(BufferBuilder buffer, Point point) {
        buffer.addVertex((float) point.x, (float) point.y, 0.0f);
    }

    private static void setUniform(ShaderInstance shader, String name, float v0, float v1) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(v0, v1);
        }
    }

    private static void setUniform(ShaderInstance shader, String name, float v0) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(v0);
        }
    }

    private static void setUniform(ShaderInstance shader, String name, float v0, float v1, float v2, float v3) {
        if (shader.getUniform(name) != null) {
            shader.getUniform(name).set(v0, v1, v2, v3);
        }
    }

    private static Point clampPoint(Point point, RectBounds bounds) {
        return new Point(
                clampD(point.x, bounds.minX, bounds.maxX),
                clampD(point.y, bounds.minY, bounds.maxY)
        );
    }

    /**
     * Liang-Barsky 线段裁剪算法 —— 将线段裁剪到矩形边界内。
     * <p>
     * p[]、q[] 分别编码四个边界的方向和位置约束：
     * i=0: 左边界（-dx, x-minX）
     * i=1: 右边界（+dx, maxX-x）
     * i=2: 上边界（-dy, y-minY）
     * i=3: 下边界（+dy, maxY-y）
     * t0/t1 跟踪裁剪后的参数范围 [t0, t1] ⊆ [0, 1]
     */
    private static ClippedSegment clipSegment(Point a, Point b, RectBounds bounds) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double t0 = 0.0;
        double t1 = 1.0;

        double[] p = {-dx, dx, -dy, dy};
        double[] q = {a.x - bounds.minX, bounds.maxX - a.x, a.y - bounds.minY, bounds.maxY - a.y};
        for (int i = 0; i < 4; i++) {
            double pi = p[i];
            double qi = q[i];
            if (Math.abs(pi) < 1.0E-9) {
                if (qi < 0.0) {
                    return null;
                }
                continue;
            }
            double t = qi / pi;
            if (pi < 0.0) {
                if (t > t1) return null;
                if (t > t0) t0 = t;
            } else {
                if (t < t0) return null;
                if (t < t1) t1 = t;
            }
        }
        if (t1 < t0) {
            return null;
        }
        return new ClippedSegment(
                new Point(a.x + dx * t0, a.y + dy * t0),
                new Point(a.x + dx * t1, a.y + dy * t1)
        );
    }

    /** 颜色预乘 alpha（用于面积填充） */
    private static int premultiply(int color) {
        int a = (color >>> 24) & 0xFF;
        int r = (int) Math.round(((color >>> 16) & 0xFF) * (a / 255.0));
        int g = (int) Math.round(((color >>> 8) & 0xFF) * (a / 255.0));
        int b = (int) Math.round((color & 0xFF) * (a / 255.0));
        return (a << 24) | (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8) | clamp(b, 0, 255);
    }

    private static float red01(int rgb) {
        return ((rgb >>> 16) & 0xFF) / 255.0f;
    }

    private static float green01(int rgb) {
        return ((rgb >>> 8) & 0xFF) / 255.0f;
    }

    private static float blue01(int rgb) {
        return (rgb & 0xFF) / 255.0f;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clampD(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 获取线条模式的本地化标签 */
    private static String lineModeLabel(LineMode mode) {
        return switch (mode) {
            case ALL -> Component.translatable("screen.resourceobserver.overview.chart.mode.all").getString();
            case PRODUCTION -> Component.translatable("screen.resourceobserver.overview.chart.mode.production").getString();
            case CONSUMPTION -> Component.translatable("screen.resourceobserver.overview.chart.mode.consumption").getString();
            case NET -> Component.translatable("screen.resourceobserver.overview.chart.mode.net").getString();
        };
    }

    /** 根据时间窗口选择线条样式（使用直接向量渲染路径，supersample=1） */
    private static LineStyle lineStyleFor(ChartWindow chartWindow) {
        // 向量路径直接在屏幕空间渲染，无需超采样。
        return DEBUG_DIRECT_LINE_STYLE;
    }

    /**
     * 图表渲染缓存 —— 缓存 PreparedChart 避免逐帧重新计算。
     * 使用 CacheKey 判断缓存是否有效。
     */
    public static final class ChartRenderCache {
        private CacheKey key;
        private PreparedChart layer;

        public ChartRenderCache() {
            synchronized (LIVE_CACHES) {
                LIVE_CACHES.add(this);
            }
        }

        public PreparedChart getOrBuild(CacheKey nextKey, Supplier<PreparedChart> renderer) {
            if (layer == null || key == null || !key.equals(nextKey)) {
                layer = renderer.get();
                key = nextKey;
            }
            return layer;
        }

        public void clear() {
            key = null;
            layer = null;
        }
    }

    /** 缓存键 —— 包含影响渲染结果的所有参数 */
    private record CacheKey(
            int width,
            int height,
            int supersample,
            ChartWindow window,
            ChartPage page,
            LineMode lineMode,
            SmoothingMode smoothingMode,
            long seriesFingerprint
    ) {
    }

    /**
     * 缓存图表纹理 —— 包含高分辨率和低分辨率两层渲染目标。
     * 高分辨率用于超采样绘制，低分辨率用于最终显示。
     */
    private static final class CachedChartTexture implements AutoCloseable {
        private static final CachedChartTexture EMPTY = new CachedChartTexture(null, null, 0, 0, 0, 0, 0);

        private final RenderTarget highRes;
        private final RenderTarget lowRes;
        private final int width;
        private final int height;
        private final int highResWidth;
        private final int highResHeight;
        private final int sampleScale;
        private boolean hasVisibleContent;
        private boolean highResVisible;
        private boolean lowResVisible;

        private CachedChartTexture(RenderTarget highRes, RenderTarget lowRes, int width, int height, int highResWidth, int highResHeight, int sampleScale) {
            this.highRes = highRes;
            this.lowRes = lowRes;
            this.width = width;
            this.height = height;
            this.highResWidth = highResWidth;
            this.highResHeight = highResHeight;
            this.sampleScale = sampleScale;
            this.hasVisibleContent = false;
            this.highResVisible = false;
            this.lowResVisible = false;
        }

        private static final int MAX_TEXTURE_SIZE = 8192; // 保守限制，低于 GPU 最大值 (16384) 以避免边界情况

        private static CachedChartTexture create(int width, int height, int sampleScale) {
            if (width <= 0 || height <= 0) {
                return EMPTY;
            }
            int highResWidth = Math.max(1, width * sampleScale);
            int highResHeight = Math.max(1, height * sampleScale);
            // 限制纹理尺寸以防止高 DPI / 大视口下的 OpenGL 崩溃
            if (highResWidth > MAX_TEXTURE_SIZE || highResHeight > MAX_TEXTURE_SIZE) {
                int clampedScale = sampleScale;
                while (clampedScale > 1 && (width * clampedScale > MAX_TEXTURE_SIZE || height * clampedScale > MAX_TEXTURE_SIZE)) {
                    clampedScale--;
                }
                sampleScale = clampedScale;
                highResWidth = Math.min(MAX_TEXTURE_SIZE, Math.max(1, width * sampleScale));
                highResHeight = Math.min(MAX_TEXTURE_SIZE, Math.max(1, height * sampleScale));
            }
            int clampedWidth = Math.min(width, MAX_TEXTURE_SIZE);
            int clampedHeight = Math.min(height, MAX_TEXTURE_SIZE);
            TextureTarget highRes = new TextureTarget(highResWidth, highResHeight, false, Minecraft.ON_OSX);
            highRes.setFilterMode(GL11C.GL_NEAREST);
            TextureTarget lowRes = new TextureTarget(clampedWidth, clampedHeight, false, Minecraft.ON_OSX);
            lowRes.setFilterMode(GL11C.GL_LINEAR);
            return new CachedChartTexture(highRes, lowRes, clampedWidth, clampedHeight, highResWidth, highResHeight, sampleScale);
        }

        private boolean hasVisibleContent() {
            return hasVisibleContent;
        }

        private void setHasVisibleContent(boolean hasVisibleContent) {
            this.hasVisibleContent = hasVisibleContent;
        }

        private boolean highResVisible() {
            return highResVisible;
        }

        private void setHighResVisible(boolean highResVisible) {
            this.highResVisible = highResVisible;
        }

        private boolean lowResVisible() {
            return lowResVisible;
        }

        private void setLowResVisible(boolean lowResVisible) {
            this.lowResVisible = lowResVisible;
        }

        @Override
        public void close() {
            if (highRes != null) {
                highRes.destroyBuffers();
            }
            if (lowRes != null) {
                lowRes.destroyBuffers();
            }
        }
    }

    /** 预计算图表数据 —— 包含边界、零轴位置和所有系列的折线段 */
    private record PreparedChart(
            RectBounds bounds,
            Double zeroAxisY,
            List<PreparedSeries> series,
            List<ChartHoverPoint> hoverPoints,
            boolean hasVisibleContent
    ) {
        private static final PreparedChart EMPTY = new PreparedChart(
                new RectBounds(0.0, 0.0, 0.0, 0.0),
                null,
                List.of(),
                List.of(),
                false
        );
    }

    /** 预计算单系列 —— 折线段列表和颜色 */
    private record PreparedSeries(List<List<Point>> segments, int color) {
    }

    /** 二维点 */
    private record Point(double x, double y) {
    }

    /** 裁剪测试状态快照 */
    private record ScissorState(boolean enabled, int x, int y, int width, int height) {
    }

    /**
     * 线条渲染样式参数。
     * @param supersample       超采样倍率
     * @param curveSubdivision  曲线细分密度
     * @param coreWidth         核心线宽
     * @param glowWidth         辉光线宽
     * @param feather           边缘羽化距离
     * @param zeroWidth         零轴线宽
     * @param coreAlpha         核心线透明度
     * @param glowAlpha         辉光透明度
     * @param zeroAlpha         零轴线透明度
     * @param lowResFilter      低分辨率纹理过滤模式
     * @param resolveEdgeWeight  降采样边缘权重
     * @param resolveInnerWeight 降采样内部权重
     * @param resolveAlphaBoost  降采样 alpha 增强
     */
    private record LineStyle(
            int supersample,
            float curveSubdivision,
            float coreWidth,
            float glowWidth,
            float feather,
            float zeroWidth,
            float coreAlpha,
            float glowAlpha,
            float zeroAlpha,
            int lowResFilter,
            float resolveEdgeWeight,
            float resolveInnerWeight,
            float resolveAlphaBoost
    ) {
    }

    /** 裁剪后的线段 */
    private record ClippedSegment(Point a, Point b) {
    }

    /** 矩形边界（用于绘图区范围限定） */
    private record RectBounds(double minX, double minY, double maxX, double maxY) {
    }

    /** 数据范围（最小值/最大值） */
    private record Range(double min, double max) {
    }

    /** 投影矩阵栈状态快照 */
    private record ProjectionState(Matrix4fStack modelView) {
    }

    /**
     * 图表数据类型枚举。
     * - ITEMS：物品吞吐/库存数据（AE2 网络）
     * - ENERGY：电量吞吐/储量数据（Flux 能量网络）
     */
    public enum ChartDataType {
        ITEMS,
        ENERGY;

        public ChartDataType next() {
            return this == ITEMS ? ENERGY : ITEMS;
        }
    }

    /**
     * 图表页面类型枚举。
     * - THROUGHPUT：吞吐量视图（生产/消耗/净流量）
     * - STOCK：库存视图（库存量变化）
     */
    public enum ChartPage {
        THROUGHPUT,
        STOCK;

        public ChartPage next() {
            return this == THROUGHPUT ? STOCK : THROUGHPUT;
        }
    }

    /**
     * 平滑模式枚举。
     * - SMOOTH：EMA 指数移动平均平滑
     * - RAW：原始数据直接绘制
     */
    public enum SmoothingMode {
        SMOOTH,
        RAW;

        public SmoothingMode next() {
            return this == SMOOTH ? RAW : SMOOTH;
        }
    }

    /**
     * 线条显示模式枚举。
     * - ALL：显示全部曲线
     * - PRODUCTION：仅显示生产曲线
     * - CONSUMPTION：仅显示消耗曲线
     * - NET：仅显示净流量曲线
     */
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

    /** 图表渲染结果 —— 包含各控制按钮的热区 */
    public enum ChartSeriesType {
        PRODUCTION,
        CONSUMPTION,
        NET,
        STOCK
    }

    public record ChartHoverPoint(
            ChartSeriesType seriesType,
            double x,
            double y,
            int slotIndex,
            double value
    ) {
    }

    public record RenderResult(
            UiRect windowToggle,
            UiRect dataTypeToggle,
            UiRect pageToggle,
            UiRect lineModeButton,
            UiRect smoothingButton,
            UiRect resetButton,
            boolean lineModeEnabled,
            UiRect plotRect,
            List<ChartHoverPoint> hoverPoints
    ) {
    }
}

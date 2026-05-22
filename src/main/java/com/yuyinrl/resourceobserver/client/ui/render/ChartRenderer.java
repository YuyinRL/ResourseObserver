package com.yuyinrl.resourceobserver.client.ui.render;

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
import com.yuyinrl.resourceobserver.client.ui.render.chart.ChartDataType;
import com.yuyinrl.resourceobserver.client.ui.render.chart.ChartHoverPoint;
import com.yuyinrl.resourceobserver.client.ui.render.chart.ChartPage;
import com.yuyinrl.resourceobserver.client.ui.render.chart.ChartSeriesType;
import com.yuyinrl.resourceobserver.client.ui.render.chart.LineMode;
import com.yuyinrl.resourceobserver.client.ui.render.chart.RenderResult;
import com.yuyinrl.resourceobserver.client.ui.render.chart.SmoothingMode;
import com.yuyinrl.resourceobserver.network.ChartWindow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11C;

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
    private static final double PLOT_PAD_X_SCALE = 1.0;     // 绘图区水平边距倍率
    private static final double PLOT_PAD_TOP_SCALE = 2.5;   // 绘图区顶部边距倍率
    private static final double PLOT_PAD_END_SCALE = 2.0;   // 绘图区末端边距倍率

    // ========== 线条样式预设 ==========
    /** 调试模式直接渲染线条样式 */
    private static final LineStyle DEBUG_DIRECT_LINE_STYLE = new LineStyle(1, 16.0f, 0.80f, 0.96f, 0.40f, 0.72f, 1.00f, 0.00f, 0.28f, GL11C.GL_LINEAR, 1.0f, 1.0f, 0.0f);

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
        buffer.addVertex((float) (a.x - nx), (float) (a.y - ny), 0.0f).setColor(r, g, bColor, aColor);
        buffer.addVertex((float) (b.x - nx), (float) (b.y - ny), 0.0f).setColor(r, g, bColor, aColor);
        buffer.addVertex((float) (b.x + nx), (float) (b.y + ny), 0.0f).setColor(r, g, bColor, aColor);
        buffer.addVertex((float) (a.x + nx), (float) (a.y + ny), 0.0f).setColor(r, g, bColor, aColor);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        MeshData mesh = buffer.buildOrThrow();
        BufferUploader.drawWithShader(mesh);
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
    /**
     * 图表数据类型枚举。
     * - ITEMS：物品吞吐/库存数据（AE2 网络）
     * - ENERGY：电量吞吐/储量数据（Flux 能量网络）
     */
}

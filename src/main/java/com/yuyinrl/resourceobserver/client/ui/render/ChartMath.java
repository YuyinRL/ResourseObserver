package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 图表数学计算工具类 —— 提取自 {@link ChartRenderer} 的纯 CPU 端数据处理逻辑。
 * <p>
 * 供原版 Screen ({@link ChartRenderer}) 和 Modern UI ({@code ChartView}) 共享。
 * 所有方法均为无副作用的纯函数。
 */
public final class ChartMath {

    private ChartMath() {}

    // ========== 数据类型 ==========

    /** 二维点 */
    public record Point(double x, double y) {}

    /** 矩形边界 */
    public record RectBounds(double minX, double minY, double maxX, double maxY) {}

    /** 数据范围（最小值/最大值） */
    public record Range(double min, double max) {}

    /** 预计算单系列 —— 折线段列表和颜色 */
    public record PreparedSeries(List<List<Point>> segments, int color) {}

    /** 裁剪后的线段 */
    public record ClippedSegment(Point a, Point b) {}

    /** 悬停点数据 */
    public record HoverPoint(
            ChartRenderer.ChartSeriesType seriesType,
            double x, double y,
            int slotIndex, double value
    ) {}

    /** 预计算图表数据 */
    public record PreparedChart(
            RectBounds bounds,
            Double zeroAxisY,
            List<PreparedSeries> series,
            List<HoverPoint> hoverPoints,
            boolean hasVisibleContent
    ) {
        public static final PreparedChart EMPTY = new PreparedChart(
                new RectBounds(0, 0, 0, 0), null, List.of(), List.of(), false
        );
    }

    // ========== 布局常量 ==========

    private static final double PLOT_PAD_X_SCALE = 1.0;
    private static final double PLOT_PAD_TOP_SCALE = 2.5;
    private static final double PLOT_PAD_END_SCALE = 2.0;

    // ========== 坐标映射 ==========

    /**
     * 数据索引 → X 坐标。
     * @param sampleScale 采样倍率（Modern UI 用 1，原版用超采样倍率）
     */
    public static double x4(int index, int size, int width, int sampleScale) {
        double min = plotMinX(sampleScale);
        double max = maxCoordX(width, sampleScale);
        if (size <= 1) return min;
        return min + (index / (double) (size - 1)) * (max - min);
    }

    /** 数据值 → Y 坐标（Y 轴反向：值越大 Y 越小） */
    public static double valueY4(double value, double min, double max, int height, int sampleScale) {
        double low = plotMinY(sampleScale);
        double high = maxCoordY(height, sampleScale);
        double range = Math.max(1.0E-6, max - min);
        double ratio = (value - min) / range;
        return high - ratio * (high - low);
    }

    public static double plotMinX(int sampleScale) {
        return sampleScale * PLOT_PAD_X_SCALE;
    }

    public static double plotMinY(int sampleScale) {
        return sampleScale * PLOT_PAD_TOP_SCALE;
    }

    public static double maxCoordX(int extent, int sampleScale) {
        return Math.max(plotMinX(sampleScale), extent - sampleScale * PLOT_PAD_END_SCALE);
    }

    public static double maxCoordY(int extent, int sampleScale) {
        return Math.max(plotMinY(sampleScale), extent - sampleScale * PLOT_PAD_END_SCALE);
    }

    // ========== 折线构建 ==========

    /**
     * 将数据序列构建为多段折线。
     * 跳过无效数据点，将连续有效段分别转为折线；
     * 若启用平滑且段长 >= 3，则使用 Monotone Hermite 插值生成曲线。
     */
    public static List<List<Point>> buildPolylines(
            int width, int height, int sampleScale,
            float curveSubdivision,
            double[] values, boolean[] valid,
            double min, double max, boolean smooth
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
                base.add(new Point(
                        x4(k, values.length, width, sampleScale),
                        valueY4(values[k], min, max, height, sampleScale)
                ));
            }
            out.add(smooth && base.size() >= 3
                    ? monotone(base, width, height, sampleScale, curveSubdivision)
                    : clampPoints(base, width, height, sampleScale));
        }
        return out;
    }

    // ========== Monotone Hermite 插值 ==========

    /**
     * Monotone Hermite 三次插值 —— 生成保持单调性的平滑曲线。
     * Fritsch-Carlson 单调性约束：若 α² + β² > 9 则缩放切线。
     */
    public static List<Point> monotone(List<Point> base, int width, int height, int sampleScale, float curveSubdivision) {
        int n = base.size();
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = base.get(i).x;
            y[i] = base.get(i).y;
        }
        double[] d = new double[n - 1];
        for (int i = 0; i < n - 1; i++) {
            double h = Math.max(1.0E-6, x[i + 1] - x[i]);
            d[i] = (y[i + 1] - y[i]) / h;
        }
        double[] m = new double[n];
        m[0] = d[0];
        m[n - 1] = d[n - 2];
        for (int i = 1; i < n - 1; i++) m[i] = 0.5 * (d[i - 1] + d[i]);
        for (int i = 0; i < n - 1; i++) {
            if (Math.abs(d[i]) < 1.0E-9) {
                m[i] = 0.0;
                m[i + 1] = 0.0;
            } else {
                double a = m[i] / d[i];
                double b = m[i + 1] / d[i];
                double s = a * a + b * b;
                if (s > 9.0) {
                    double t = 3.0 / Math.sqrt(s);
                    m[i] = t * a * d[i];
                    m[i + 1] = t * b * d[i];
                }
            }
        }
        double minX = plotMinX(sampleScale);
        double maxX = maxCoordX(width, sampleScale);
        double minY = plotMinY(sampleScale);
        double maxY = maxCoordY(height, sampleScale);
        List<Point> out = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            double x0 = x[i], x1 = x[i + 1];
            double y0 = y[i], y1 = y[i + 1];
            double h = Math.max(1.0E-6, x1 - x0);
            int steps = Math.max(1, (int) Math.ceil(h * Math.max(1.0f, curveSubdivision)));
            int from = i == 0 ? 0 : 1;
            for (int step = from; step <= steps; step++) {
                double t = step / (double) steps;
                double t2 = t * t, t3 = t2 * t;
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

    // ========== 数据平滑 ==========

    /** EMA 指数移动平均平滑，平滑因子 α=0.45 */
    public static double[] smoothSeries(double[] values, boolean[] validMask) {
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

    // ========== 范围计算 ==========

    /** 计算吞吐量模式下所有可见系列的数值范围 */
    public static Range rangeThroughput(ChartRenderer.LineMode mode, double[] p, double[] c, double[] n, boolean[] mask) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < p.length; i++) {
            if (!mask[i]) continue;
            if (mode.showProduction()) { min = Math.min(min, p[i]); max = Math.max(max, p[i]); }
            if (mode.showConsumption()) { min = Math.min(min, c[i]); max = Math.max(max, c[i]); }
            if (mode.showNet()) { min = Math.min(min, n[i]); max = Math.max(max, n[i]); }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) return new Range(0.0, 1.0);
        if (Math.abs(max - min) < 1.0E-6) return new Range(min, min + 1.0);
        return new Range(min, max);
    }

    /** 计算单序列的数值范围 */
    public static Range rangeSingle(double[] values, boolean[] mask) {
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

    // ========== 辅助方法 ==========

    public static List<Point> clampPoints(List<Point> in, int width, int height, int sampleScale) {
        List<Point> out = new ArrayList<>(in.size());
        double minX = plotMinX(sampleScale);
        double maxX = maxCoordX(width, sampleScale);
        double minY = plotMinY(sampleScale);
        double maxY = maxCoordY(height, sampleScale);
        for (Point p : in) {
            out.add(new Point(clampD(p.x, minX, maxX), clampD(p.y, minY, maxY)));
        }
        return out;
    }

    /** Liang-Barsky 线段裁剪 */
    public static ClippedSegment clipSegment(Point a, Point b, RectBounds bounds) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double t0 = 0.0, t1 = 1.0;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {a.x - bounds.minX, bounds.maxX - a.x, a.y - bounds.minY, bounds.maxY - a.y};
        for (int i = 0; i < 4; i++) {
            double pi = p[i], qi = q[i];
            if (Math.abs(pi) < 1.0E-9) {
                if (qi < 0.0) return null;
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
        if (t1 < t0) return null;
        return new ClippedSegment(
                new Point(a.x + dx * t0, a.y + dy * t0),
                new Point(a.x + dx * t1, a.y + dy * t1)
        );
    }

    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double clampD(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** FNV-1a 数据指纹（用于缓存键） */
    public static long seriesFingerprint(List<OverviewViewModel.FlowPoint> series) {
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

    // ========== 预计算图表 ==========

    /**
     * 预计算图表数据（纯 CPU 端）。
     * @param plotWidth     绘图区宽度
     * @param plotHeight    绘图区高度
     * @param sampleScale   采样倍率（Modern UI 用 1）
     * @param curveSubdivision 曲线细分密度
     */
    public static PreparedChart prepareChart(
            int plotWidth, int plotHeight,
            List<OverviewViewModel.FlowPoint> series,
            ChartRenderer.ChartPage page,
            ChartRenderer.LineMode lineMode,
            ChartRenderer.SmoothingMode smoothingMode,
            float curveSubdivision,
            int sampleScale
    ) {
        if (plotWidth <= 0 || plotHeight <= 0 || series == null || series.isEmpty()) {
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
            OverviewViewModel.FlowPoint fp = series.get(i);
            p[i] = fp.production();
            c[i] = fp.consumption();
            net[i] = fp.net();
            stock[i] = fp.stock();
            fm[i] = fp.hasFlow();
            sm[i] = fp.hasStock();
        }

        boolean smooth = smoothingMode == ChartRenderer.SmoothingMode.SMOOTH;
        RectBounds bounds = new RectBounds(
                plotMinX(sampleScale), plotMinY(sampleScale),
                maxCoordX(plotWidth, sampleScale), maxCoordY(plotHeight, sampleScale)
        );

        List<PreparedSeries> preparedSeries = new ArrayList<>();
        List<HoverPoint> hoverPoints = new ArrayList<>();
        Double zeroAxisY = null;

        if (page == ChartRenderer.ChartPage.THROUGHPUT) {
            double[] ps = smooth ? smoothSeries(p, fm) : Arrays.copyOf(p, n);
            double[] cs = smooth ? smoothSeries(c, fm) : Arrays.copyOf(c, n);
            double[] ns = smooth ? smoothSeries(net, fm) : Arrays.copyOf(net, n);

            Range range = rangeThroughput(lineMode, ps, cs, ns, fm);
            if (range.min < 0.0 && range.max > 0.0) {
                zeroAxisY = clampD(
                        valueY4(0.0, range.min, range.max, plotHeight, sampleScale),
                        bounds.minY, bounds.maxY
                );
            }
            if (lineMode.showProduction()) {
                addSeries(preparedSeries, hoverPoints, ChartRenderer.ChartSeriesType.PRODUCTION,
                        series, ps, fm, range, plotWidth, plotHeight, sampleScale, curveSubdivision, smooth,
                        com.yuyinrl.resourceobserver.client.ui.UiThemeTokens.CYAN);
            }
            if (lineMode.showConsumption()) {
                addSeries(preparedSeries, hoverPoints, ChartRenderer.ChartSeriesType.CONSUMPTION,
                        series, cs, fm, range, plotWidth, plotHeight, sampleScale, curveSubdivision, smooth,
                        com.yuyinrl.resourceobserver.client.ui.UiThemeTokens.AMBER);
            }
            if (lineMode.showNet()) {
                addSeries(preparedSeries, hoverPoints, ChartRenderer.ChartSeriesType.NET,
                        series, ns, fm, range, plotWidth, plotHeight, sampleScale, curveSubdivision, smooth,
                        com.yuyinrl.resourceobserver.client.ui.UiThemeTokens.EMERALD);
            }
        } else {
            double[] ss = smooth ? smoothSeries(stock, sm) : Arrays.copyOf(stock, n);
            Range range = rangeSingle(ss, sm);
            addSeries(preparedSeries, hoverPoints, ChartRenderer.ChartSeriesType.STOCK,
                    series, ss, sm, range, plotWidth, plotHeight, sampleScale, curveSubdivision, smooth,
                    com.yuyinrl.resourceobserver.client.ui.UiThemeTokens.BLUE);
        }

        return new PreparedChart(
                bounds, zeroAxisY, preparedSeries,
                List.copyOf(hoverPoints),
                zeroAxisY != null || !preparedSeries.isEmpty()
        );
    }

    private static void addSeries(
            List<PreparedSeries> outSeries, List<HoverPoint> outHover,
            ChartRenderer.ChartSeriesType type,
            List<OverviewViewModel.FlowPoint> source,
            double[] values, boolean[] valid, Range range,
            int plotWidth, int plotHeight, int sampleScale,
            float curveSubdivision, boolean smooth, int color
    ) {
        List<List<Point>> segments = buildPolylines(
                plotWidth, plotHeight, sampleScale, curveSubdivision,
                values, valid, range.min, range.max, smooth
        );
        if (!segments.isEmpty()) {
            outSeries.add(new PreparedSeries(segments, color));
        }
        appendHoverPoints(outHover, type, source, values, valid,
                range.min, range.max, plotWidth, plotHeight, sampleScale);
    }

    private static void appendHoverPoints(
            List<HoverPoint> out, ChartRenderer.ChartSeriesType type,
            List<OverviewViewModel.FlowPoint> source,
            double[] values, boolean[] valid,
            double min, double max,
            int plotWidth, int plotHeight, int sampleScale
    ) {
        if (out == null || source == null || values == null || valid == null) return;
        int size = Math.min(Math.min(source.size(), values.length), valid.length);
        for (int i = 0; i < size; i++) {
            if (!valid[i]) continue;
            OverviewViewModel.FlowPoint fp = source.get(i);
            double localX = x4(i, size, plotWidth, sampleScale) / sampleScale;
            double localY = valueY4(values[i], min, max, plotHeight, sampleScale) / sampleScale;
            out.add(new HoverPoint(type, localX, localY, fp.slotIndex(), values[i]));
        }
    }

    /** 格式化数值为紧凑表示 */
    public static String formatMetric(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000.0) return String.format(java.util.Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
        if (abs >= 1_000_000.0) return String.format(java.util.Locale.ROOT, "%.2fM", value / 1_000_000.0);
        if (abs >= 1_000.0) return String.format(java.util.Locale.ROOT, "%.2fK", value / 1_000.0);
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}

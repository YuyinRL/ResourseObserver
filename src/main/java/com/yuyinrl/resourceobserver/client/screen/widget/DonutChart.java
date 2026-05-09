package com.yuyinrl.resourceobserver.client.screen.widget;

import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

/**
 * 甜甜圈图（V2 / Vanilla 移植）。
 *
 * <p>呈现：
 * <ul>
 *   <li>外环背景圆（headroom 余量区颜色更亮）</li>
 *   <li>负载分段：起点 12 点钟方向，顺时针，段间 1.2° 间隙</li>
 *   <li>中心圆挖空 + 两行中心文本（大值 + 标签）</li>
 *   <li>hover 时其它段 alpha 140；hover 段保持原色 + tooltip</li>
 * </ul>
 *
 * <p>渲染策略：扫描线 + 行程编码（RLE）— 比 per-pixel fill 大幅减少 draw call，
 * 比 Tesselator 三角扇更稳健（无需触碰 RenderType）。</p>
 *
 * <p>用法：
 * <pre>
 * DonutChart donut = new DonutChart();
 * donut.setData(segs, headroomPct, "85%", "Headroom", VanillaTheme.COLOR_TEXT_PRIMARY);
 * donut.setBounds(new Rect(x, y, 100, 100));
 * </pre>
 */
public class DonutChart extends BaseWidget {

    /** 单个负载段。 */
    public record Segment(String label, double percentage, long valuePerTick, int color) {}

    private static final double SEG_GAP_DEG = 1.2;
    private static final int BG_RING_COLOR = 0xFF_0A_10_20;
    private static final int HEADROOM_RING_COLOR = 0xFF_16_22_36;
    private static final int HOLE_COLOR = 0xFF_0D_15_26;
    private static final int DIM_ALPHA = 140;

    private List<Segment> segments = List.of();
    private double headroomPercent = 100.0;
    private String centerLine1 = "";
    private String centerLine2 = "";
    private int centerColor = VanillaTheme.COLOR_TEXT_PRIMARY;
    private float innerRatio = 0.62f;
    private boolean showTooltip = true;

    public DonutChart() {
        super();
    }

    /** 主题：环背景 / hole / headroom 颜色。子类或调用方可重写以适配 V2 灰系。 */
    protected int colorBgRing()       { return BG_RING_COLOR; }
    protected int colorHeadroomRing() { return HEADROOM_RING_COLOR; }
    protected int colorHole()         { return HOLE_COLOR; }

    public DonutChart setInnerRatio(float r) {
        this.innerRatio = Math.max(0f, Math.min(0.95f, r));
        return this;
    }

    public DonutChart setShowTooltip(boolean show) {
        this.showTooltip = show;
        return this;
    }

    /**
     * 设置数据。
     *
     * @param segs           负载段列表（百分比相对总输出，可不足 100）
     * @param headroomPct    余量百分比（0..100+）
     * @param centerLine1    中心大字（如 "85%"）
     * @param centerLine2    中心小字（如 "Headroom"）
     * @param centerColor    中心大字颜色
     */
    public DonutChart setData(List<Segment> segs, double headroomPct,
                              String centerLine1, String centerLine2, int centerColor) {
        this.segments = segs != null ? segs : List.of();
        this.headroomPercent = headroomPct;
        this.centerLine1 = centerLine1 != null ? centerLine1 : "";
        this.centerLine2 = centerLine2 != null ? centerLine2 : "";
        this.centerColor = centerColor;
        return this;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        Rect b = bounds();
        if (b.width() < 10 || b.height() < 10) return;

        int cx = b.x() + b.width() / 2;
        int cy = b.y() + b.height() / 2;
        int outerR = Math.min(b.width(), b.height()) / 2 - 2;
        int innerR = Math.max(2, Math.round(outerR * innerRatio));

        int hovered = -1;
        if (b.contains(mouseX, mouseY)) {
            hovered = hitTest(mouseX - cx, mouseY - cy, outerR, innerR);
        }

        drawDonutScanline(g, cx, cy, outerR, innerR, hovered);
        drawCenterText(g, cx, cy, innerR);

        if (showTooltip && hovered >= 0 && hovered < segments.size()) {
            drawTooltip(g, mouseX, mouseY, segments.get(hovered));
        }
    }

    /** 命中测试：返回被悬停的段索引，-1 表示不在任何段上。 */
    private int hitTest(int dx, int dy, int outerR, int innerR) {
        int dist2 = dx * dx + dy * dy;
        if (dist2 < innerR * innerR || dist2 > outerR * outerR) return -1;
        double angle = Math.toDegrees(Math.atan2(dx, -dy));
        if (angle < 0) angle += 360.0;
        return segmentAt(angle);
    }

    /**
     * 给定屏幕角度（0=12 点钟，顺时针），返回该角度落在哪个段。
     * -1 表示在 headroom / 段间隙 / 全空。
     */
    private int segmentAt(double angleDeg) {
        double scale = usageScale();
        int n = segments.size();
        if (n == 0 || scale <= 0.0) return -1;
        if (angleDeg >= 360.0 * scale) return -1;
        double rawTotal = rawSegTotal();
        if (rawTotal <= 0) return -1;
        double availableDeg = 360.0 * scale - n * SEG_GAP_DEG;
        if (availableDeg <= 0) return -1;
        double cum = 0;
        for (int i = 0; i < n; i++) {
            double segDeg = segments.get(i).percentage() / rawTotal * availableDeg;
            double start = cum + SEG_GAP_DEG / 2.0;
            double end = start + segDeg;
            if (angleDeg >= start && angleDeg < end) return i;
            cum += segDeg + SEG_GAP_DEG;
        }
        return -1;
    }

    /** 扫描线 + 行程编码渲染。 */
    private void drawDonutScanline(GuiGraphics g, int cx, int cy, int outerR, int innerR,
                                   int hovered) {
        double scale = usageScale();
        double rawTotal = rawSegTotal();
        int n = segments.size();
        double availableDeg = 360.0 * scale - (n > 0 ? n * SEG_GAP_DEG : 0);

        int outerR2 = outerR * outerR;
        int innerR2 = innerR * innerR;

        for (int dy = -outerR; dy <= outerR; dy++) {
            int runColor = 0;
            int runStart = Integer.MIN_VALUE;
            for (int dx = -outerR; dx <= outerR; dx++) {
                int dist2 = dx * dx + dy * dy;
                int color;
                if (dist2 < innerR2 || dist2 > outerR2) {
                    color = 0;
                } else {
                    double angle = Math.toDegrees(Math.atan2(dx, -dy));
                    if (angle < 0) angle += 360.0;
                    color = pixelColor(angle, scale, rawTotal, availableDeg, hovered);
                }
                if (color != runColor) {
                    if (runColor != 0) {
                        g.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, runColor);
                    }
                    runColor = color;
                    runStart = dx;
                }
            }
            if (runColor != 0) {
                g.fill(cx + runStart, cy + dy, cx + outerR + 1, cy + dy + 1, runColor);
            }
        }

        // 中心 hole（盖住中心圆，避免扫描线锯齿透出）
        // 已由 dist2 < innerR2 → color=0 留白；外部背景透出。再补一圈淡 hole 圆作为底
        // 本实现 hole 使用透明（color=0），与父容器背景一致
    }

    /** 计算单个像素颜色。 */
    private int pixelColor(double angle, double scale, double rawTotal,
                           double availableDeg, int hovered) {
        int n = segments.size();
        // 在 headroom 区
        if (angle >= 360.0 * scale) {
            return colorHeadroomRing();
        }
        if (n == 0 || rawTotal <= 0 || availableDeg <= 0) {
            return colorBgRing();
        }
        double cum = 0;
        for (int i = 0; i < n; i++) {
            double segDeg = segments.get(i).percentage() / rawTotal * availableDeg;
            double start = cum + SEG_GAP_DEG / 2.0;
            double end = start + segDeg;
            if (angle >= start && angle < end) {
                int base = segments.get(i).color();
                // 兜底：base 颜色未带 alpha 的视为 FF
                if ((base >>> 24) == 0) base |= 0xFF000000;
                if (hovered < 0 || hovered == i) {
                    return base;
                }
                // 其它段降透明度
                return (DIM_ALPHA << 24) | (base & 0x00FF_FFFF);
            }
            cum += segDeg + SEG_GAP_DEG;
        }
        // 段间隙
        return colorBgRing();
    }

    /** 中心两行文本。 */
    private void drawCenterText(GuiGraphics g, int cx, int cy, int innerR) {
        if (centerLine1.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int w1 = font.width(centerLine1);
        // line1 略偏上
        int y1 = cy - VanillaTheme.FONT_HEIGHT - 1;
        g.drawString(font, centerLine1, cx - w1 / 2, y1, centerColor, false);
        if (!centerLine2.isEmpty()) {
            int w2 = font.width(centerLine2);
            int y2 = cy + 2;
            g.drawString(font, centerLine2, cx - w2 / 2, y2, VanillaTheme.COLOR_TEXT_SECONDARY, false);
        }
    }

    /** 段 tooltip：标签 + "X.X% (value/t)"。 */
    private void drawTooltip(GuiGraphics g, int mx, int my, Segment seg) {
        Font font = Minecraft.getInstance().font;
        Component line1 = Component.literal(seg.label());
        double pct = seg.percentage() * usageScale();
        String detail = String.format(java.util.Locale.ROOT, "%.1f%%", pct)
                + (seg.valuePerTick() > 0 ? "  (" + compact(seg.valuePerTick()) + "/t)" : "");
        Component line2 = Component.literal(detail);
        g.renderTooltip(font, List.of(line1, line2), Optional.empty(), mx, my);
    }

    private double usageScale() {
        return Math.min(1.0, Math.max(0.0, (100.0 - headroomPercent) / 100.0));
    }

    private double rawSegTotal() {
        double sum = 0;
        for (Segment seg : segments) sum += seg.percentage();
        return sum;
    }

    /** 紧凑数字格式化（与 ModernUI compact() 等价）。 */
    private static String compact(long v) {
        if (v < 1000) return Long.toString(v);
        if (v < 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fK", v / 1_000.0);
        if (v < 1_000_000_000L) return String.format(java.util.Locale.ROOT, "%.1fM", v / 1_000_000.0);
        return String.format(java.util.Locale.ROOT, "%.1fG", v / 1_000_000_000.0);
    }
}

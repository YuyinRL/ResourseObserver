package com.yuyinrl.resourceobserver.client.ui.render;

import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.UiRect;
import com.yuyinrl.resourceobserver.client.ui.UiThemeTokens;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 电力网络 Debug 面板渲染器 —— 绘制一个半透明小窗口，实时显示 Flux Networks 原始指标。
 * <p>
 * 显示内容：
 * - Input Rate（所有 Plug 接口的合计输入速率 FE/t）
 * - Output Rate（所有 Point 接口的合计输出速率 FE/t）
 * - Buffer Capacity（网络总缓冲容量 FE）
 * - 接口数量统计（Plugs / Points / Storages / Controllers）
 * - 逐接口明细（每个 Plug / Point 的传输速率，可点击选中查看外部容器）
 * - 选中接口的外部容器储能信息（Stored / Capacity / 进度条）
 * <p>
 * 数据随 payload 刷新自动更新（由 tick → requestRefresh → rebuildPowerViewModel 驱动）。
 */
public final class PowerDebugPanelRenderer {
    private PowerDebugPanelRenderer() {
    }

    /** Debug 面板标题栏行高 */
    private static final int TITLE_H = 12;
    /** 每行数据的行高 */
    private static final int LINE_H = 10;
    /** 分隔线高度 */
    private static final int SEPARATOR_H = 6;
    /** 内边距 */
    private static final int PAD = 5;
    /** 外部容器信息区高度 */
    private static final int EXT_SECTION_H = 42;

    /** 半透明深色背景（比普通面板更暗更透明，突出 debug 性质） */
    private static final int DEBUG_BG = 0xB0060E1C;
    /** Debug 面板边框（带有微妙的青色调） */
    private static final int DEBUG_BORDER = 0xFF1A3050;
    /** Debug 标题颜色（青色） */
    private static final int DEBUG_TITLE_COLOR = UiThemeTokens.CYAN;
    /** Debug 标签颜色 */
    private static final int DEBUG_LABEL_COLOR = UiThemeTokens.TEXT_MUTED;
    /** Debug 值颜色 */
    private static final int DEBUG_VALUE_COLOR = UiThemeTokens.TEXT;
    /** Plug 设备颜色（绿色 — 输入/发电） */
    private static final int PLUG_COLOR = UiThemeTokens.EMERALD;
    /** Point 设备颜色（玫瑰色 — 输出/耗电） */
    private static final int POINT_COLOR = UiThemeTokens.ROSE;
    /** 选中设备行的高亮背景 */
    private static final int SELECTED_ROW_BG = 0x4021456A;
    /** 悬停设备行的高亮背景 */
    private static final int HOVERED_ROW_BG = 0x2821456A;

    /**
     * 设备行点击热区 —— 用于在 Screen 中检测鼠标点击。
     * @param rect       行矩形区域
     * @param deviceType 设备类型（plug / point）
     * @param index      设备在对应列表中的索引
     */
    public record DeviceRowHitbox(UiRect rect, String deviceType, int index) {
    }

    /**
     * 渲染结果 —— 包含设备行热区列表。
     * @param deviceRowHitboxes 可点击的设备行热区列表
     */
    public record RenderResult(List<DeviceRowHitbox> deviceRowHitboxes) {
    }

    /**
     * 计算 Debug 面板需要的总高度。
     *
     * @param snapshot             Debug 快照数据
     * @param selectedDeviceType   当前选中的设备类型（null = 未选中）
     * @param selectedDeviceIndex  当前选中的设备索引
     * @return 面板所需像素高度
     */
    public static int measureHeight(
            PowerNetworkViewModel.DebugSnapshot snapshot,
            String selectedDeviceType,
            int selectedDeviceIndex
    ) {
        if (snapshot == null) {
            snapshot = PowerNetworkViewModel.DebugSnapshot.empty();
        }
        int h = PAD * 2;           // 上下内边距
        h += TITLE_H;              // "⚡ DEBUG" 标题
        h += LINE_H * 3;           // Input Rate / Output Rate / Buffer Capacity
        h += SEPARATOR_H;          // 分隔线
        h += LINE_H;               // 接口数量摘要行

        // 逐接口明细
        int deviceLines = snapshot.inputDevices().size() + snapshot.outputDevices().size();
        if (deviceLines > 0) {
            h += SEPARATOR_H;                               // 分隔线
            if (!snapshot.inputDevices().isEmpty()) {
                h += LINE_H;                                // "▸ Plugs (Input):" 标题
                h += LINE_H * snapshot.inputDevices().size(); // 每个 plug 一行
            }
            if (!snapshot.outputDevices().isEmpty()) {
                h += LINE_H;                                // "▸ Points (Output):" 标题
                h += LINE_H * snapshot.outputDevices().size(); // 每个 point 一行
            }
        }

        // 选中设备的外部容器信息区
        PowerNetworkViewModel.DeviceDebugEntry selectedEntry = findSelectedEntry(
                snapshot, selectedDeviceType, selectedDeviceIndex);
        if (selectedEntry != null && selectedEntry.externalEnergyCapacity() > 0) {
            h += SEPARATOR_H;
            h += EXT_SECTION_H;
        }

        return h;
    }

    /** 向后兼容：无选中状态时的 measureHeight */
    public static int measureHeight(PowerNetworkViewModel.DebugSnapshot snapshot) {
        return measureHeight(snapshot, null, -1);
    }

    /**
     * 渲染 Debug 面板。
     *
     * @param gfx                 GuiGraphics 上下文
     * @param font                字体
     * @param area                绘制区域
     * @param snapshot            Debug 快照数据
     * @param selectedDeviceType  当前选中的设备类型（"plug"/"point"，null=未选中）
     * @param selectedDeviceIndex 当前选中的设备在列表中的索引
     * @param mouseX              鼠标 X 坐标（用于悬停高亮）
     * @param mouseY              鼠标 Y 坐标
     * @return 渲染结果（包含设备行热区）
     */
    public static RenderResult render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            PowerNetworkViewModel.DebugSnapshot snapshot,
            String selectedDeviceType,
            int selectedDeviceIndex,
            int mouseX,
            int mouseY
    ) {
        if (snapshot == null) {
            snapshot = PowerNetworkViewModel.DebugSnapshot.empty();
        }

        List<DeviceRowHitbox> hitboxes = new ArrayList<>();

        // 面板背景 + 边框
        RenderUtils.fillPanel(gfx, area, DEBUG_BG, DEBUG_BORDER);

        int x = area.x() + PAD;
        int y = area.y() + PAD;
        int maxW = area.width() - PAD * 2;

        // ========== 标题 ==========
        gfx.drawString(font, "⚡ FLUX DEBUG", x, y, DEBUG_TITLE_COLOR);
        y += TITLE_H;

        // ========== 三个核心指标 ==========
        y = drawLabelValue(gfx, font, x, y, maxW,
                "Input Rate:", formatFEt(snapshot.inputRate()), PLUG_COLOR);
        y = drawLabelValue(gfx, font, x, y, maxW,
                "Output Rate:", formatFEt(snapshot.outputRate()), POINT_COLOR);
        y = drawLabelValue(gfx, font, x, y, maxW,
                "Buffer Cap.:", formatFE(snapshot.totalBufferCapacity()),
                snapshot.totalBufferCapacity() > 0 ? DEBUG_VALUE_COLOR : UiThemeTokens.TEXT_MUTED);

        // ========== 分隔线 ==========
        y += 2;
        gfx.fill(x, y, x + maxW, y + 1, UiThemeTokens.DIVIDER);
        y += SEPARATOR_H - 2;

        // ========== 接口数量摘要 ==========
        String connSummary = String.format(Locale.ROOT, "P:%d  Pt:%d  S:%d  C:%d",
                snapshot.plugCount(), snapshot.pointCount(),
                snapshot.storageCount(), snapshot.controllerCount());
        gfx.drawString(font, RenderUtils.ellipsis(font, connSummary, maxW), x, y, DEBUG_LABEL_COLOR);
        y += LINE_H;

        // ========== 逐接口明细 ==========
        boolean hasDevices = !snapshot.inputDevices().isEmpty() || !snapshot.outputDevices().isEmpty();
        if (hasDevices) {
            y += 2;
            gfx.fill(x, y, x + maxW, y + 1, UiThemeTokens.DIVIDER);
            y += SEPARATOR_H - 2;

            if (!snapshot.inputDevices().isEmpty()) {
                gfx.drawString(font, "▸ Plugs (Input):", x, y, PLUG_COLOR);
                y += LINE_H;
                y = drawDeviceListInteractive(gfx, font, x + 6, y, maxW - 6,
                        snapshot.inputDevices(), PLUG_COLOR, "plug",
                        selectedDeviceType, selectedDeviceIndex,
                        mouseX, mouseY, hitboxes);
            }
            if (!snapshot.outputDevices().isEmpty()) {
                gfx.drawString(font, "▸ Points (Output):", x, y, POINT_COLOR);
                y += LINE_H;
                y = drawDeviceListInteractive(gfx, font, x + 6, y, maxW - 6,
                        snapshot.outputDevices(), POINT_COLOR, "point",
                        selectedDeviceType, selectedDeviceIndex,
                        mouseX, mouseY, hitboxes);
            }
        }

        // ========== 选中设备的外部容器信息 ==========
        PowerNetworkViewModel.DeviceDebugEntry selectedEntry = findSelectedEntry(
                snapshot, selectedDeviceType, selectedDeviceIndex);
        if (selectedEntry != null && selectedEntry.externalEnergyCapacity() > 0) {
            y += 2;
            gfx.fill(x, y, x + maxW, y + 1, UiThemeTokens.DIVIDER);
            y += SEPARATOR_H - 2;

            drawExternalContainerSection(gfx, font, x, y, maxW, selectedEntry);
        }

        return new RenderResult(hitboxes);
    }

    /** 向后兼容：无选中/鼠标状态的 render 入口 */
    public static void render(
            GuiGraphics gfx,
            Font font,
            UiRect area,
            PowerNetworkViewModel.DebugSnapshot snapshot
    ) {
        render(gfx, font, area, snapshot, null, -1, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    // ========== 私有辅助方法 ==========

    /** 绘制一行 "Label: Value"，返回下一行 Y 坐标 */
    private static int drawLabelValue(
            GuiGraphics gfx, Font font, int x, int y, int maxW,
            String label, String value, int valueColor
    ) {
        int labelW = font.width(label);
        gfx.drawString(font, label, x, y, DEBUG_LABEL_COLOR);
        String displayValue = RenderUtils.ellipsis(font, value, maxW - labelW - 4);
        gfx.drawString(font, displayValue, x + labelW + 4, y, valueColor);
        return y + LINE_H;
    }

    /**
     * 绘制可交互的设备明细列表（支持选中高亮和悬停高亮），返回下一行 Y 坐标。
     * 同时收集每行的点击热区。
     */
    private static int drawDeviceListInteractive(
            GuiGraphics gfx, Font font, int x, int y, int maxW,
            List<PowerNetworkViewModel.DeviceDebugEntry> devices, int accentColor,
            String deviceType,
            String selectedDeviceType, int selectedDeviceIndex,
            int mouseX, int mouseY,
            List<DeviceRowHitbox> hitboxes
    ) {
        for (int i = 0; i < devices.size(); i++) {
            PowerNetworkViewModel.DeviceDebugEntry device = devices.get(i);
            UiRect rowRect = new UiRect(x - 2, y - 1, maxW + 4, LINE_H);

            // 选中/悬停高亮
            boolean isSelected = deviceType.equals(selectedDeviceType) && i == selectedDeviceIndex;
            boolean isHovered = rowRect.contains(mouseX, mouseY);
            if (isSelected) {
                gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), SELECTED_ROW_BG);
                // 选中左侧标记条
                gfx.fill(rowRect.x(), rowRect.y(), rowRect.x() + 2, rowRect.bottom(), accentColor);
            } else if (isHovered) {
                gfx.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), HOVERED_ROW_BG);
            }

            // 设备名称 + 传输速率
            String name = device.deviceName();
            String rate = formatFEt(device.transferRate());
            int nameMaxW = maxW - font.width(rate) - 8;
            String displayName = RenderUtils.ellipsis(font, name, Math.max(20, nameMaxW));
            gfx.drawString(font, displayName, x, y, isSelected ? UiThemeTokens.TEXT : DEBUG_LABEL_COLOR);
            gfx.drawString(font, rate, x + maxW - font.width(rate), y, accentColor);

            // 如果有外部容器，显示一个小指示器
            if (device.externalEnergyCapacity() > 0) {
                String extIndicator = " ⬡";
                int indicatorX = x + font.width(displayName);
                if (indicatorX + font.width(extIndicator) < x + nameMaxW) {
                    gfx.drawString(font, extIndicator, indicatorX, y,
                            isSelected ? UiThemeTokens.CYAN : UiThemeTokens.TEXT_MUTED);
                }
            }

            hitboxes.add(new DeviceRowHitbox(rowRect, deviceType, i));
            y += LINE_H;
        }
        return y;
    }

    /**
     * 绘制选中设备的外部容器储能信息区域。
     * 包含：标题、Stored / Capacity 数值、百分比进度条。
     */
    private static void drawExternalContainerSection(
            GuiGraphics gfx, Font font, int x, int y, int maxW,
            PowerNetworkViewModel.DeviceDebugEntry device
    ) {
        long stored = device.externalEnergyStored();
        long capacity = device.externalEnergyCapacity();
        double ratio = capacity > 0 ? (double) stored / capacity : 0.0;

        // 标题
        String title = "⬡ External Container";
        gfx.drawString(font, title, x, y, DEBUG_TITLE_COLOR);
        y += LINE_H + 1;

        // Stored / Capacity
        String storedLabel = "Stored:";
        String storedValue = formatFE(stored) + " / " + formatFE(capacity);
        int labelW = font.width(storedLabel);
        gfx.drawString(font, storedLabel, x, y, DEBUG_LABEL_COLOR);
        int valueColor = ratio > 0.9 ? UiThemeTokens.EMERALD : (ratio < 0.1 ? UiThemeTokens.ROSE : DEBUG_VALUE_COLOR);
        gfx.drawString(font, RenderUtils.ellipsis(font, storedValue, maxW - labelW - 4), x + labelW + 4, y, valueColor);
        y += LINE_H + 1;

        // 百分比文本
        String pctText = String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
        gfx.drawString(font, pctText, x, y, DEBUG_LABEL_COLOR);
        int barX = x + font.width(pctText) + 6;
        int barW = Math.max(20, maxW - font.width(pctText) - 6);
        int barH = 6;
        int barY = y + 1;

        // 进度条背景
        gfx.fill(barX, barY, barX + barW, barY + barH, 0xFF16253C);
        // 进度条填充
        int fillW = (int) Math.round(ratio * barW);
        if (fillW > 0) {
            int barColor = ratio > 0.9 ? UiThemeTokens.EMERALD
                    : (ratio > 0.5 ? UiThemeTokens.CYAN
                    : (ratio > 0.1 ? UiThemeTokens.AMBER : UiThemeTokens.ROSE));
            gfx.fill(barX, barY, barX + fillW, barY + barH, barColor);
        }
        // 进度条边框
        RenderUtils.drawBorder(gfx, new UiRect(barX, barY, barW, barH), UiThemeTokens.DIVIDER);
    }

    /** 根据选中类型和索引查找对应的 DeviceDebugEntry */
    private static PowerNetworkViewModel.DeviceDebugEntry findSelectedEntry(
            PowerNetworkViewModel.DebugSnapshot snapshot,
            String selectedDeviceType,
            int selectedDeviceIndex
    ) {
        if (snapshot == null || selectedDeviceType == null || selectedDeviceIndex < 0) {
            return null;
        }
        List<PowerNetworkViewModel.DeviceDebugEntry> list;
        if ("plug".equals(selectedDeviceType)) {
            list = snapshot.inputDevices();
        } else if ("point".equals(selectedDeviceType)) {
            list = snapshot.outputDevices();
        } else {
            return null;
        }
        if (selectedDeviceIndex >= list.size()) {
            return null;
        }
        return list.get(selectedDeviceIndex);
    }

    /** 格式化 FE/t 值（带单位后缀和千分位压缩） */
    private static String formatFEt(long value) {
        return formatCompact(value) + " FE/t";
    }

    /** 格式化 FE 值（带单位后缀和千分位压缩） */
    private static String formatFE(long value) {
        return formatCompact(value) + " FE";
    }

    /** 紧凑数值格式化：K / M / B 后缀 */
    private static String formatCompact(long value) {
        long abs = Math.abs(value);
        if (abs >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.2fM", value / 1_000_000.0);
        }
        if (abs >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }
}


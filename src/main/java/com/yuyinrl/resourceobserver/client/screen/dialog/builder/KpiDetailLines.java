package com.yuyinrl.resourceobserver.client.screen.dialog.builder;

import com.yuyinrl.resourceobserver.client.screen.dialog.KpiDetailDialog;
import com.yuyinrl.resourceobserver.client.screen.theme.VanillaTheme;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.client.ui.PowerNetworkViewModel;
import com.yuyinrl.resourceobserver.client.ui.format.FormatUtils;
import com.yuyinrl.resourceobserver.service.snapshot.overview.OverviewSnapshot;
import com.yuyinrl.resourceobserver.service.snapshot.power.PowerSnapshot;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * G2.1 共用：将 OverviewSnapshot / PowerSnapshot 数据拍平为 KpiDetailDialog 行列表。
 *
 * <p>所有方法接收最新快照（每帧由 {@code Supplier<List<Line>>} 调用），无内部缓存。</p>
 */
public final class KpiDetailLines {

    private KpiDetailLines() {
    }

    // ---------------- Overview ----------------

    public static Component overviewTitle(OverviewSnapshot.KpiType type) {
        if (type == null) return Component.literal("KPI");
        return switch (type) {
            case PRODUCTION -> Component.translatable("screen.resourceobserver.overview.kpi.production");
            case CONSUMPTION -> Component.translatable("screen.resourceobserver.overview.kpi.consumption");
            case STORAGE -> Component.translatable("screen.resourceobserver.overview.kpi.storage");
            case BALANCE -> Component.translatable("screen.resourceobserver.overview.kpi.balance");
        };
    }

    public static Component overviewTitle(OverviewViewModel.KpiType type) {
        if (type == null) return Component.literal("KPI");
        return switch (type) {
            case PRODUCTION -> Component.translatable("screen.resourceobserver.overview.kpi.production");
            case CONSUMPTION -> Component.translatable("screen.resourceobserver.overview.kpi.consumption");
            case STORAGE -> Component.translatable("screen.resourceobserver.overview.kpi.storage");
            case BALANCE -> Component.translatable("screen.resourceobserver.overview.kpi.balance");
        };
    }

    public static List<KpiDetailDialog.Line> forOverview(
            OverviewViewModel vm, OverviewViewModel.KpiType type) {
        List<KpiDetailDialog.Line> out = new ArrayList<>();
        if (vm == null || type == null) return out;
        OverviewViewModel.KpiDetail detail = vm.kpiDetailFor(type);
        if (detail == null) {
            out.add(new KpiDetailDialog.Line(
                    Component.translatable("screen.resourceobserver.overview.kpi.detail.unavailable").getString(),
                    "—",
                    VanillaTheme.COLOR_TEXT_DISABLED));
            return out;
        }

        if (detail.hintText() != null && !detail.hintText().isBlank()) {
            out.add(new KpiDetailDialog.Line(
                    Component.translatable("screen.resourceobserver.overview.kpi.detail.hint").getString(),
                    detail.hintText(),
                    statusColor(detail.status())));
        }

        appendChannel(out, detail.itemChannel());
        appendChannel(out, detail.fluidChannel());
        return out;
    }

    public static List<KpiDetailDialog.Line> forOverview(
            OverviewSnapshot snapshot, OverviewSnapshot.KpiType type) {
        List<KpiDetailDialog.Line> out = new ArrayList<>();
        if (snapshot == null || type == null) return out;
        OverviewSnapshot.KpiDetailSnapshot detail = findDetail(snapshot, type);
        if (detail == null) {
            out.add(new KpiDetailDialog.Line(
                    Component.translatable("screen.resourceobserver.overview.kpi.detail.unavailable").getString(),
                    "—",
                    VanillaTheme.COLOR_TEXT_DISABLED));
            return out;
        }

        if (detail.hintKey() != null && !detail.hintKey().isBlank()) {
            out.add(new KpiDetailDialog.Line(
                    Component.translatable("screen.resourceobserver.overview.kpi.detail.hint").getString(),
                    Component.translatable(detail.hintKey()).getString(),
                    statusColor(detail.status())));
        }

        appendChannel(out, "screen.resourceobserver.overview.kpi.detail.item_channel", detail.itemChannel());
        appendChannel(out, "screen.resourceobserver.overview.kpi.detail.fluid_channel", detail.fluidChannel());
        return out;
    }

    private static void appendChannel(List<KpiDetailDialog.Line> out, OverviewViewModel.KpiDetailChannel channel) {
        if (channel == null) return;
        out.add(new KpiDetailDialog.Line(
                channel.label() == null ? "" : channel.label(),
                "",
                VanillaTheme.COLOR_TEXT_SECONDARY));
        if (!channel.available()) {
            out.add(new KpiDetailDialog.Line(
                    "  " + Component.translatable("screen.resourceobserver.overview.kpi.detail.unavailable").getString(),
                    "—",
                    VanillaTheme.COLOR_TEXT_DISABLED));
            return;
        }
        out.add(new KpiDetailDialog.Line(
                "  " + safe(channel.recentText()),
                "",
                VanillaTheme.COLOR_TEXT_PRIMARY));
        out.add(new KpiDetailDialog.Line(
                "  " + safe(channel.previousText()),
                "",
                VanillaTheme.COLOR_TEXT_SECONDARY));
        if (channel.trendText() != null && !channel.trendText().isBlank()) {
            int color = channel.trendText().startsWith("-")
                    ? VanillaTheme.COLOR_STATUS_ERROR
                    : VanillaTheme.COLOR_STATUS_OK;
            out.add(new KpiDetailDialog.Line(
                    "  " + channel.trendText(),
                    "",
                    color));
        }
    }

    private static void appendChannel(List<KpiDetailDialog.Line> out, String headerKey,
                                      OverviewSnapshot.ChannelDetail channel) {
        if (channel == null) return;
        out.add(new KpiDetailDialog.Line(
                Component.translatable(headerKey).getString(),
                "",
                VanillaTheme.COLOR_TEXT_SECONDARY));
        if (!channel.available()) {
            out.add(new KpiDetailDialog.Line(
                    "  " + Component.translatable("screen.resourceobserver.overview.kpi.detail.unavailable").getString(),
                    "—",
                    VanillaTheme.COLOR_TEXT_DISABLED));
            return;
        }
        String labelText = channel.labelKey() == null
                ? "" : Component.translatable(channel.labelKey()).getString();
        if (!labelText.isBlank()) {
            out.add(new KpiDetailDialog.Line("  " + labelText, "",
                    VanillaTheme.COLOR_TEXT_SECONDARY));
        }
        out.add(new KpiDetailDialog.Line(
                "  " + Component.translatable("screen.resourceobserver.overview.kpi.detail.recent_label").getString(),
                formatRate(channel.recentRate()),
                VanillaTheme.COLOR_TEXT_PRIMARY));
        out.add(new KpiDetailDialog.Line(
                "  " + Component.translatable("screen.resourceobserver.overview.kpi.detail.previous_label").getString(),
                formatRate(channel.previousRate()),
                VanillaTheme.COLOR_TEXT_SECONDARY));
        if (channel.trendAvailable()) {
            int color = channel.trendPercent() >= 0
                    ? VanillaTheme.COLOR_STATUS_OK
                    : VanillaTheme.COLOR_STATUS_ERROR;
            out.add(new KpiDetailDialog.Line(
                    "  " + Component.translatable("screen.resourceobserver.overview.kpi.detail.trend").getString(),
                    String.format(Locale.ROOT, "%+.1f%%", channel.trendPercent()),
                    color));
        }
    }

    private static OverviewSnapshot.KpiDetailSnapshot findDetail(
            OverviewSnapshot snapshot, OverviewSnapshot.KpiType type) {
        if (snapshot.kpiDetails() == null) return null;
        for (OverviewSnapshot.KpiDetailSnapshot d : snapshot.kpiDetails()) {
            if (d.type() == type) return d;
        }
        return null;
    }

    // ---------------- Power ----------------

    /** Power KPI 索引：0=有效输入 1=有效输出 2=储能 3=外储排除。 */
    public static Component powerTitle(PowerNetworkViewModel vm, int kpiIndex) {
        if (vm != null && vm.kpiCards() != null && kpiIndex >= 0 && kpiIndex < vm.kpiCards().size()) {
            String label = vm.kpiCards().get(kpiIndex).label();
            if (label != null && !label.isBlank()) return Component.literal(label);
        }
        return powerTitle(kpiIndex);
    }

    public static Component powerTitle(int kpiIndex) {
        return switch (kpiIndex) {
            case 0 -> Component.translatable("screen.resourceobserver.power.kpi.effective_input");
            case 1 -> Component.translatable("screen.resourceobserver.power.kpi.effective_output");
            case 2 -> Component.translatable("screen.resourceobserver.power.kpi.stored_energy");
            case 3 -> Component.translatable("screen.resourceobserver.power.kpi.external_excluded");
            default -> Component.literal("Power KPI");
        };
    }

    public static List<KpiDetailDialog.Line> forPower(PowerNetworkViewModel vm, int kpiIndex) {
        List<KpiDetailDialog.Line> out = new ArrayList<>();
        if (vm == null) return out;

        List<PowerNetworkViewModel.PowerKpi> kpis = vm.kpiCards();
        if (kpis != null && kpiIndex >= 0 && kpiIndex < kpis.size()) {
            PowerNetworkViewModel.PowerKpi kpi = kpis.get(kpiIndex);
            out.add(new KpiDetailDialog.Line(
                    safe(kpi.label()),
                    kpi.value() == null ? "—" : kpi.value(),
                    statusColor(kpi.status())));
        }

        out.add(new KpiDetailDialog.Line("", "", 0));

        out.add(new KpiDetailDialog.Line(
                Component.translatable("screen.resourceobserver.power.detail.total_input").getString(),
                FormatUtils.formatCompact(vm.totalInputPerTick()) + " FE/t",
                VanillaTheme.COLOR_STATUS_OK));
        out.add(new KpiDetailDialog.Line(
                Component.translatable("screen.resourceobserver.power.detail.total_output").getString(),
                FormatUtils.formatCompact(vm.totalOutputPerTick()) + " FE/t",
                VanillaTheme.COLOR_STATUS_ERROR));
        out.add(new KpiDetailDialog.Line(
                Component.translatable("screen.resourceobserver.power.detail.total_stored").getString(),
                FormatUtils.formatCompact(vm.totalStored()) + " FE",
                VanillaTheme.COLOR_STATUS_INFO));
        out.add(new KpiDetailDialog.Line(
                Component.translatable("screen.resourceobserver.power.detail.total_capacity").getString(),
                FormatUtils.formatCompact(vm.totalCapacity()) + " FE",
                VanillaTheme.COLOR_TEXT_SECONDARY));

        if (vm.devices() != null) {
            out.add(new KpiDetailDialog.Line(
                    Component.translatable("screen.resourceobserver.power.detail.device_count").getString(),
                    String.valueOf(vm.devices().size()),
                    VanillaTheme.COLOR_TEXT_PRIMARY));
        }
        if (vm.consumers() != null && !vm.consumers().isEmpty()) {
            out.add(new KpiDetailDialog.Line(
                    Component.translatable("screen.resourceobserver.power.detail.consumer_count").getString(),
                    String.valueOf(vm.consumers().size()),
                    VanillaTheme.COLOR_TEXT_PRIMARY));
        }
        return out;
    }

    public static List<KpiDetailDialog.Line> forPower(PowerSnapshot snapshot, int kpiIndex) {
        List<KpiDetailDialog.Line> out = new ArrayList<>();
        if (snapshot == null) return out;

        List<PowerSnapshot.KpiSnapshot> kpis = snapshot.kpiCards();
        if (kpis != null && kpiIndex >= 0 && kpiIndex < kpis.size()) {
            PowerSnapshot.KpiSnapshot kpi = kpis.get(kpiIndex);
            out.add(new KpiDetailDialog.Line(
                    Component.translatable(kpi.labelKey()).getString(),
                    kpi.value() == null ? "—" : kpi.value(),
                    statusColor(kpi.status())));
        }

        out.add(new KpiDetailDialog.Line("", "", 0));

        out.add(new KpiDetailDialog.Line(
                Component.translatable("screen.resourceobserver.power.detail.total_input").getString(),
                FormatUtils.formatCompact(snapshot.totalInputPerTick()) + " FE/t",
                VanillaTheme.COLOR_STATUS_OK));
        out.add(new KpiDetailDialog.Line(
                Component.translatable("screen.resourceobserver.power.detail.total_output").getString(),
                FormatUtils.formatCompact(snapshot.totalOutputPerTick()) + " FE/t",
                VanillaTheme.COLOR_STATUS_ERROR));
        out.add(new KpiDetailDialog.Line(
                Component.translatable("screen.resourceobserver.power.detail.total_stored").getString(),
                FormatUtils.formatCompact(snapshot.totalStored()) + " FE",
                VanillaTheme.COLOR_STATUS_INFO));
        out.add(new KpiDetailDialog.Line(
                Component.translatable("screen.resourceobserver.power.detail.total_capacity").getString(),
                FormatUtils.formatCompact(snapshot.totalCapacity()) + " FE",
                VanillaTheme.COLOR_TEXT_SECONDARY));

        if (snapshot.devices() != null) {
            out.add(new KpiDetailDialog.Line(
                    Component.translatable("screen.resourceobserver.power.detail.device_count").getString(),
                    String.valueOf(snapshot.devices().size()),
                    VanillaTheme.COLOR_TEXT_PRIMARY));
        }
        if (snapshot.consumers() != null && !snapshot.consumers().isEmpty()) {
            out.add(new KpiDetailDialog.Line(
                    Component.translatable("screen.resourceobserver.power.detail.consumer_count").getString(),
                    String.valueOf(snapshot.consumers().size()),
                    VanillaTheme.COLOR_TEXT_PRIMARY));
        }
        return out;
    }

    // ---------------- Helpers ----------------

    private static int statusColor(OverviewViewModel.Status status) {
        if (status == null) return VanillaTheme.COLOR_TEXT_PRIMARY;
        return switch (status) {
            case POSITIVE -> VanillaTheme.COLOR_STATUS_OK;
            case WARNING -> VanillaTheme.COLOR_STATUS_WARN;
            case NEGATIVE -> VanillaTheme.COLOR_STATUS_ERROR;
            case NEUTRAL -> VanillaTheme.COLOR_TEXT_PRIMARY;
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
    private static int statusColor(OverviewSnapshot.KpiStatus status) {
        if (status == null) return VanillaTheme.COLOR_TEXT_PRIMARY;
        return switch (status) {
            case POSITIVE -> VanillaTheme.COLOR_STATUS_OK;
            case WARNING -> VanillaTheme.COLOR_STATUS_WARN;
            case NEGATIVE -> VanillaTheme.COLOR_STATUS_ERROR;
            case NEUTRAL -> VanillaTheme.COLOR_TEXT_PRIMARY;
        };
    }

    private static String formatRate(double rate) {
        if (Double.isNaN(rate) || Double.isInfinite(rate)) return "—";
        if (Math.abs(rate) < 0.05) return "0";
        return FormatUtils.formatCompact((long) Math.round(rate)) + "/min";
    }
}


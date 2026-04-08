package com.yuyinrl.resourceobserver.client.modernui;

import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;

import java.util.List;

/**
 * ModernUI 预览用样例数据。
 */
public final class OverviewPreviewFixture {
    private OverviewPreviewFixture() {
    }

    public static OverviewPreviewState createSample() {
        return new OverviewPreviewState(
                "OPERATIONS DASHBOARD",
                "ModernUI Overview Preview",
                true,
                -8,
                -60,
                11,
                List.of(
                        new OverviewPreviewState.KpiCard(
                                OverviewViewModel.KpiType.PRODUCTION,
                                "screen.resourceobserver.overview.section.kpi_production",
                                "12.4K /min",
                                "+8.2% vs previous window",
                                OverviewViewModel.Status.POSITIVE
                        ),
                        new OverviewPreviewState.KpiCard(
                                OverviewViewModel.KpiType.CONSUMPTION,
                                "screen.resourceobserver.overview.section.kpi_consumption",
                                "11.7K /min",
                                "+3.6% vs previous window",
                                OverviewViewModel.Status.WARNING
                        ),
                        new OverviewPreviewState.KpiCard(
                                OverviewViewModel.KpiType.STORAGE,
                                "screen.resourceobserver.overview.section.kpi_storage",
                                "68.4%",
                                "Disk and external storage synced",
                                OverviewViewModel.Status.NEUTRAL
                        ),
                        new OverviewPreviewState.KpiCard(
                                OverviewViewModel.KpiType.BALANCE,
                                "screen.resourceobserver.overview.section.kpi_balance",
                                "+5.6 pt",
                                "+1.3 vs previous window",
                                OverviewViewModel.Status.POSITIVE
                        )
                ),
                List.of(
                        new OverviewPreviewState.GroupOption("ungrouped", "Ungrouped", true),
                        new OverviewPreviewState.GroupOption("raw", "RAW", false),
                        new OverviewPreviewState.GroupOption("intermediate", "INTERMEDIATE", false),
                        new OverviewPreviewState.GroupOption("finished", "FINISHED", false),
                        new OverviewPreviewState.GroupOption("common_parts", "COMMON PARTS", false)
                ),
                List.of(
                        new OverviewPreviewState.WatchEntry("minecraft:iron_ingot", "minecraft:iron_ingot", 460, 52_100),
                        new OverviewPreviewState.WatchEntry("minecraft:redstone", "minecraft:redstone", -210, 8_900),
                        new OverviewPreviewState.WatchEntry("minecraft:glass", "minecraft:glass", 95, 12_300)
                ),
                List.of(
                        new OverviewPreviewState.TableEntry("minecraft:iron_ingot", "minecraft:iron_ingot", "raw", 930, 470, 52_100, false, true),
                        new OverviewPreviewState.TableEntry("minecraft:redstone", "minecraft:redstone", "raw", 180, 390, 8_900, true, true),
                        new OverviewPreviewState.TableEntry("minecraft:glass", "minecraft:glass", "finished", 240, 145, 12_300, false, true),
                        new OverviewPreviewState.TableEntry("minecraft:copper_ingot", "minecraft:copper_ingot", "raw", 410, 365, 31_800, false, false),
                        new OverviewPreviewState.TableEntry("minecraft:quartz", "minecraft:quartz", "intermediate", 320, 230, 19_200, false, false),
                        new OverviewPreviewState.TableEntry("minecraft:comparator", "minecraft:comparator", "common_parts", 75, 162, 1_800, true, false)
                )
        );
    }
}

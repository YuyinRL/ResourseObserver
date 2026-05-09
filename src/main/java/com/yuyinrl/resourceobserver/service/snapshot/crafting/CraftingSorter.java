package com.yuyinrl.resourceobserver.service.snapshot.crafting;

import java.util.Comparator;
import java.util.List;

/**
 * 任务行 / 可合成行的稳定排序工具。
 *
 * <p>jobs：busy 优先 → networkId → cpuName。
 * <p>craftables：displayName（不区分大小写）→ itemId。
 */
public final class CraftingSorter {

    private CraftingSorter() {
    }

    public static final Comparator<CraftingSnapshot.JobSnapshot> JOB_ORDER = Comparator
            .comparing((CraftingSnapshot.JobSnapshot r) -> r.busy() ? 0 : 1)
            .thenComparing(CraftingSnapshot.JobSnapshot::networkId, Comparator.nullsLast(String::compareTo))
            .thenComparing(CraftingSnapshot.JobSnapshot::cpuName, Comparator.nullsLast(String::compareTo));

    public static final Comparator<CraftingSnapshot.CraftableSnapshot> CRAFTABLE_ORDER = Comparator
            .comparing(CraftingSnapshot.CraftableSnapshot::displayName,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(CraftingSnapshot.CraftableSnapshot::itemId,
                    Comparator.nullsLast(String::compareTo));

    public static void sortJobs(List<CraftingSnapshot.JobSnapshot> jobs) {
        if (jobs != null) jobs.sort(JOB_ORDER);
    }

    public static void sortCraftables(List<CraftingSnapshot.CraftableSnapshot> craftables) {
        if (craftables != null) craftables.sort(CRAFTABLE_ORDER);
    }
}

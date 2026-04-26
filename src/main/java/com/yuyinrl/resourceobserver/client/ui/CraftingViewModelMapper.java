package com.yuyinrl.resourceobserver.client.ui;

import com.yuyinrl.resourceobserver.client.util.ItemNames;
import com.yuyinrl.resourceobserver.client.util.PinyinMatcher;
import com.yuyinrl.resourceobserver.network.ObserverDataPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 合成数据 ViewModel 映射器 —— 将 ObserverDataPayload 中的 craftingBindings 聚合为
 * 可直接渲染的 {@link CraftingViewModel}。
 * <p>
 * 聚合规则：
 * <ul>
 *   <li>storageSummary：跨所有 AE2 网络把 cpu/busy/storage/coProc 求和；reliable 只要有一个 false 就为 false</li>
 *   <li>jobs：展开所有网络的 CPU，busy 优先、然后按 networkId + cpuName 稳定排序</li>
 *   <li>craftables：展开所有网络，可选 pinyin 过滤，按 displayName 稳定排序</li>
 * </ul>
 */
public final class CraftingViewModelMapper {

    private CraftingViewModelMapper() {}

    public static CraftingViewModel fromPayload(ObserverDataPayload payload, String searchQuery) {
        String query = searchQuery == null ? "" : searchQuery;
        if (payload == null) {
            return CraftingViewModel.empty(query);
        }
        List<ObserverDataPayload.CraftingBindingData> craftingBindings = payload.craftingBindings();
        if (craftingBindings == null || craftingBindings.isEmpty()) {
            return CraftingViewModel.empty(query);
        }

        int cpuCount = 0;
        int busyCount = 0;
        long totalBytes = 0L;
        int totalCoProc = 0;
        boolean reliable = true;
        boolean hasData = false;

        List<CraftingViewModel.JobRow> jobs = new ArrayList<>();
        List<CraftingViewModel.CraftableRow> craftablesAll = new ArrayList<>();
        int totalCraftables = 0;

        for (ObserverDataPayload.CraftingBindingData data : craftingBindings) {
            if (data == null) continue;
            hasData = true;
            ObserverDataPayload.CraftingStorageSnapshot storage = data.storage();
            if (storage != null) {
                cpuCount += storage.cpuCount();
                busyCount += storage.busyCpuCount();
                totalBytes += storage.totalStorageBytes();
                totalCoProc += storage.totalCoProcessors();
                if (!storage.reliable()) reliable = false;
            }
            if (data.jobs() != null) {
                for (ObserverDataPayload.CraftingJobSnapshot job : data.jobs()) {
                    if (job == null) continue;
                    long total = Math.max(job.totalAmount(), 0L);
                    long remaining = Math.max(job.remainingAmount(), 0L);
                    double progress = Math.max(0.0, Math.min(1.0, job.progressFraction()));
                    if (progress == 0.0 && total > 0L) {
                        long done = Math.max(total - remaining, 0L);
                        progress = Math.min(1.0, (double) done / (double) total);
                    }
                    jobs.add(new CraftingViewModel.JobRow(
                            data.networkId(),
                            job.cpuName(),
                            job.outputItemId(),
                            ItemNames.localize(job.outputItemId(), job.outputDisplayName()),
                            total,
                            remaining,
                            progress,
                            job.busy(),
                            job.storageBytes(),
                            job.coProcessors(),
                            job.elapsedMillis(),
                            job.treeId() == null ? "" : job.treeId()
                    ));
                }
            }
            if (data.craftables() != null) {
                for (ObserverDataPayload.CraftableSnapshot c : data.craftables()) {
                    if (c == null) continue;
                    totalCraftables++;
                    String localizedName = ItemNames.localize(c.itemId(), c.displayName());
                    if (!query.isEmpty() && !PinyinMatcher.matches(localizedName, query)) {
                        continue;
                    }
                    craftablesAll.add(new CraftingViewModel.CraftableRow(
                            data.networkId(),
                            c.itemId(),
                            localizedName
                    ));
                }
            }
        }

        jobs.sort(Comparator
                .comparing((CraftingViewModel.JobRow r) -> r.busy() ? 0 : 1)
                .thenComparing(CraftingViewModel.JobRow::networkId, Comparator.nullsLast(String::compareTo))
                .thenComparing(CraftingViewModel.JobRow::cpuName, Comparator.nullsLast(String::compareTo)));

        craftablesAll.sort(Comparator
                .comparing(CraftingViewModel.CraftableRow::displayName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(CraftingViewModel.CraftableRow::itemId, Comparator.nullsLast(String::compareTo)));

        CraftingViewModel.StorageSummary summary = new CraftingViewModel.StorageSummary(
                cpuCount, busyCount, totalBytes, totalCoProc, reliable);
        return new CraftingViewModel(hasData, summary, jobs, craftablesAll, totalCraftables, query);
    }
}

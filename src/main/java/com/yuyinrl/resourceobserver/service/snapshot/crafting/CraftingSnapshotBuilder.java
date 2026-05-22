package com.yuyinrl.resourceobserver.service.snapshot.crafting;

import com.yuyinrl.resourceobserver.network.ObserverDataPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Crafting Snapshot 主装配点。
 *
 * <p>从 {@link ObserverDataPayload} 的 {@code craftingBindings} 聚合所有 AE2 网络数据，
 * 输出三方共用的 {@link CraftingSnapshot}。客户端通过注入 {@link CraftingLocalizer}
 * 和 {@link CraftingSearchMatcher} 来叠加 MC i18n / JEC 拼音搜索。
 */
public final class CraftingSnapshotBuilder {

    private CraftingSnapshotBuilder() {
    }

    public static CraftingSnapshot fromPayload(ObserverDataPayload payload, String searchQuery) {
        return fromPayload(payload, searchQuery, CraftingLocalizer.IDENTITY, CraftingSearchMatcher.PLAIN);
    }

    public static CraftingSnapshot fromPayload(ObserverDataPayload payload,
                                               String searchQuery,
                                               CraftingLocalizer localizer,
                                               CraftingSearchMatcher matcher) {
        String query = searchQuery == null ? "" : searchQuery;
        if (payload == null) return CraftingSnapshot.empty(query);

        List<ObserverDataPayload.CraftingBindingData> bindings = payload.craftingBindings();
        if (bindings == null || bindings.isEmpty()) return CraftingSnapshot.empty(query);

        CraftingLocalizer loc = localizer == null ? CraftingLocalizer.IDENTITY : localizer;
        CraftingSearchMatcher mat = matcher == null ? CraftingSearchMatcher.PLAIN : matcher;

        CraftingSummaryAggregator summary = new CraftingSummaryAggregator();
        List<CraftingSnapshot.JobSnapshot> jobs = new ArrayList<>();
        List<CraftingSnapshot.CraftableSnapshot> craftables = new ArrayList<>();
        int totalCraftables = 0;
        boolean hasData = false;

        for (ObserverDataPayload.CraftingBindingData data : bindings) {
            if (data == null) continue;
            hasData = true;

            ObserverDataPayload.CraftingStorageSnapshot storage = data.storage();
            if (storage != null) {
                summary.accumulate(storage.cpuCount(), storage.busyCpuCount(),
                        storage.totalStorageBytes(), storage.totalCoProcessors(), storage.reliable());
            }

            if (data.jobs() != null) {
                for (ObserverDataPayload.CraftingJobSnapshot job : data.jobs()) {
                    if (job == null) continue;
                    var n = CraftingJobNormalizer.normalize(
                            job.totalAmount(), job.remainingAmount(), job.progressFraction());
                    jobs.add(new CraftingSnapshot.JobSnapshot(
                            data.networkId(),
                            job.cpuName(),
                            job.outputItemId(),
                            loc.localizeItem(job.outputItemId(), job.outputDisplayName()),
                            n.total(),
                            n.remaining(),
                            n.progress(),
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
                    String localized = loc.localizeItem(c.itemId(), c.displayName());
                    if (!query.isEmpty() && !mat.matches(localized, query)) continue;
                    craftables.add(new CraftingSnapshot.CraftableSnapshot(
                            data.networkId(), c.itemId(), localized));
                }
            }
        }

        CraftingSorter.sortJobs(jobs);
        CraftingSorter.sortCraftables(craftables);

        return new CraftingSnapshot(
                hasData,
                summary.build(),
                List.copyOf(jobs),
                List.copyOf(craftables),
                totalCraftables,
                query,
                System.currentTimeMillis()
        );
    }
}

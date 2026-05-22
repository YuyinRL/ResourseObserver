package com.yuyinrl.resourceobserver.service.snapshot.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftingSorterTest {

    private static CraftingSnapshot.JobSnapshot job(String net, String cpu, boolean busy) {
        return new CraftingSnapshot.JobSnapshot(net, cpu, "", "", 0L, 0L, 0.0, busy, 0L, 0, 0L, "");
    }

    private static CraftingSnapshot.CraftableSnapshot crf(String name, String id) {
        return new CraftingSnapshot.CraftableSnapshot("net", id, name);
    }

    @Test
    void busyJobsFirst() {
        List<CraftingSnapshot.JobSnapshot> jobs = new ArrayList<>(List.of(
                job("n1", "cpuA", false),
                job("n1", "cpuB", true),
                job("n2", "cpuC", false),
                job("n2", "cpuD", true)
        ));
        CraftingSorter.sortJobs(jobs);
        assertEquals(true, jobs.get(0).busy());
        assertEquals(true, jobs.get(1).busy());
        assertEquals(false, jobs.get(2).busy());
        assertEquals(false, jobs.get(3).busy());
    }

    @Test
    void jobsTieBreakOnNetworkThenCpu() {
        List<CraftingSnapshot.JobSnapshot> jobs = new ArrayList<>(List.of(
                job("netB", "cpu1", true),
                job("netA", "cpu2", true),
                job("netA", "cpu1", true)
        ));
        CraftingSorter.sortJobs(jobs);
        assertEquals("netA", jobs.get(0).networkId());
        assertEquals("cpu1", jobs.get(0).cpuName());
        assertEquals("netA", jobs.get(1).networkId());
        assertEquals("cpu2", jobs.get(1).cpuName());
        assertEquals("netB", jobs.get(2).networkId());
    }

    @Test
    void craftablesCaseInsensitive() {
        List<CraftingSnapshot.CraftableSnapshot> list = new ArrayList<>(List.of(
                crf("banana", "b"),
                crf("Apple", "a"),
                crf("cherry", "c")
        ));
        CraftingSorter.sortCraftables(list);
        assertEquals("Apple", list.get(0).displayName());
        assertEquals("banana", list.get(1).displayName());
        assertEquals("cherry", list.get(2).displayName());
    }

    @Test
    void nullSafe() {
        CraftingSorter.sortJobs(null);
        CraftingSorter.sortCraftables(null);
    }
}

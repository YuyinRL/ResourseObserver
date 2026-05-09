package com.yuyinrl.resourceobserver.service.snapshot.overview;

import com.yuyinrl.resourceobserver.ui.state.TableSortMode;
import com.yuyinrl.resourceobserver.ui.state.TableStatusFilter;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableOpsTest {

    private static OverviewSnapshot.TableRowSnapshot row(
            String id, String name, String groupKey,
            long production, long consumption, long net, long stock) {
        boolean critical = TableOps.isCritical(net, stock);
        return new OverviewSnapshot.TableRowSnapshot(
                id, name, groupKey, production, consumption, net, stock, critical, false, null);
    }

    private static OverviewSnapshot.GroupOptionSnapshot opt(String key, String name) {
        return new OverviewSnapshot.GroupOptionSnapshot(key, name, true);
    }

    // ===== isCritical =====

    @Test
    void isCritical_netPositive_returnsFalse() {
        assertFalse(TableOps.isCritical(10, 0));
    }

    @Test
    void isCritical_netZero_returnsFalse() {
        assertFalse(TableOps.isCritical(0, 0));
    }

    @Test
    void isCritical_netNegativeStockAtThreshold_returnsTrue() {
        assertTrue(TableOps.isCritical(-1, 64));
    }

    @Test
    void isCritical_netNegativeStockAboveThreshold_returnsFalse() {
        assertFalse(TableOps.isCritical(-1, 65));
    }

    // ===== matchesStatus =====

    @Test
    void matchesStatus_all_acceptsEverything() {
        OverviewSnapshot.TableRowSnapshot r = row("a", "A", "g", 0, 0, 0, 0);
        assertTrue(TableOps.matchesStatus(r, TableStatusFilter.ALL));
    }

    @Test
    void matchesStatus_surplus_onlyPositiveNet() {
        assertTrue(TableOps.matchesStatus(row("a", "A", "g", 10, 0, 10, 100), TableStatusFilter.SURPLUS));
        assertFalse(TableOps.matchesStatus(row("a", "A", "g", 0, 10, -10, 100), TableStatusFilter.SURPLUS));
        assertFalse(TableOps.matchesStatus(row("a", "A", "g", 0, 0, 0, 100), TableStatusFilter.SURPLUS));
    }

    @Test
    void matchesStatus_deficit_onlyNegativeNet() {
        assertTrue(TableOps.matchesStatus(row("a", "A", "g", 0, 10, -10, 100), TableStatusFilter.DEFICIT));
        assertFalse(TableOps.matchesStatus(row("a", "A", "g", 10, 0, 10, 100), TableStatusFilter.DEFICIT));
    }

    @Test
    void matchesStatus_critical_onlyCriticalRows() {
        assertTrue(TableOps.matchesStatus(row("a", "A", "g", 0, 10, -10, 32), TableStatusFilter.CRITICAL));
        assertFalse(TableOps.matchesStatus(row("a", "A", "g", 0, 10, -10, 1000), TableStatusFilter.CRITICAL));
    }

    // ===== matchesGroup =====

    @Test
    void matchesGroup_allFilter_acceptsAny() {
        OverviewSnapshot.TableRowSnapshot r = row("a", "A", "g1", 0, 0, 0, 0);
        assertTrue(TableOps.matchesGroup(r, TableOps.GROUP_FILTER_ALL));
        assertTrue(TableOps.matchesGroup(r, null));
        assertTrue(TableOps.matchesGroup(r, ""));
    }

    @Test
    void matchesGroup_specificKey_onlyExactMatch() {
        OverviewSnapshot.TableRowSnapshot r = row("a", "A", "g1", 0, 0, 0, 0);
        assertTrue(TableOps.matchesGroup(r, "g1"));
        assertFalse(TableOps.matchesGroup(r, "g2"));
    }

    // ===== normalizeGroupKey =====

    @Test
    void normalizeGroupKey_blankFallsBack() {
        assertEquals("ungrouped", TableOps.normalizeGroupKey(null, "ungrouped"));
        assertEquals("ungrouped", TableOps.normalizeGroupKey("  ", "ungrouped"));
        assertEquals("g1", TableOps.normalizeGroupKey("g1", "ungrouped"));
    }

    // ===== comparatorFor =====

    @Test
    void comparator_netDescThenName() {
        Comparator<OverviewSnapshot.TableRowSnapshot> c = TableOps.comparatorFor(TableSortMode.NET, true);
        OverviewSnapshot.TableRowSnapshot a = row("a", "AAA", "g", 0, 0, 100, 0);
        OverviewSnapshot.TableRowSnapshot b = row("b", "BBB", "g", 0, 0, 50, 0);
        OverviewSnapshot.TableRowSnapshot c2 = row("c", "AAA", "g", 0, 0, 100, 0);
        assertTrue(c.compare(a, b) < 0); // a higher net -> earlier in desc
        assertEquals(0, c.compare(a, c2)); // same net+name
    }

    @Test
    void comparator_netAbs_aliasesNet() {
        Comparator<OverviewSnapshot.TableRowSnapshot> c = TableOps.comparatorFor(TableSortMode.NET_ABS, false);
        OverviewSnapshot.TableRowSnapshot neg = row("a", "A", "g", 0, 0, -100, 0);
        OverviewSnapshot.TableRowSnapshot pos = row("b", "B", "g", 0, 0, 50, 0);
        assertTrue(c.compare(neg, pos) < 0); // -100 < 50, so NET asc puts neg first
    }

    // ===== applyFiltersAndSort =====

    @Test
    void applyFiltersAndSort_compositeFilters() {
        List<OverviewSnapshot.TableRowSnapshot> rows = List.of(
                row("ironA", "Iron Ingot", "metal", 100, 50, 50, 1000),
                row("ironB", "Iron Block", "metal", 0, 30, -30, 2000),
                row("woodA", "Oak Plank", "wood", 0, 5, -5, 10),  // critical
                row("zero", "Stone", "stone", 0, 0, 0, 100)
        );

        // surplus filter
        List<OverviewSnapshot.TableRowSnapshot> surplus = TableOps.applyFiltersAndSort(
                rows, TableStatusFilter.SURPLUS, TableOps.GROUP_FILTER_ALL,
                TableSortMode.NET, true, null);
        assertEquals(1, surplus.size());
        assertEquals("ironA", surplus.get(0).itemId());

        // deficit + group=metal
        List<OverviewSnapshot.TableRowSnapshot> def = TableOps.applyFiltersAndSort(
                rows, TableStatusFilter.DEFICIT, "metal",
                TableSortMode.NET, true, null);
        assertEquals(1, def.size());
        assertEquals("ironB", def.get(0).itemId());

        // critical filter
        List<OverviewSnapshot.TableRowSnapshot> crit = TableOps.applyFiltersAndSort(
                rows, TableStatusFilter.CRITICAL, TableOps.GROUP_FILTER_ALL,
                TableSortMode.NET, true, null);
        assertEquals(1, crit.size());
        assertEquals("woodA", crit.get(0).itemId());
    }

    @Test
    void applyFiltersAndSort_searchPredicate() {
        List<OverviewSnapshot.TableRowSnapshot> rows = List.of(
                row("a", "Iron Ingot", "g", 0, 0, 0, 0),
                row("b", "Gold Ingot", "g", 0, 0, 0, 0),
                row("c", "Stone", "g", 0, 0, 0, 0)
        );
        Predicate<OverviewSnapshot.TableRowSnapshot> contains =
                r -> r.displayName().toLowerCase().contains("ingot");
        List<OverviewSnapshot.TableRowSnapshot> r = TableOps.applyFiltersAndSort(
                rows, TableStatusFilter.ALL, TableOps.GROUP_FILTER_ALL,
                TableSortMode.NET, false, contains);
        assertEquals(2, r.size());
    }

    @Test
    void applyFiltersAndSort_sortByStockDesc() {
        List<OverviewSnapshot.TableRowSnapshot> rows = List.of(
                row("a", "A", "g", 0, 0, 0, 100),
                row("b", "B", "g", 0, 0, 0, 500),
                row("c", "C", "g", 0, 0, 0, 250)
        );
        List<OverviewSnapshot.TableRowSnapshot> r = TableOps.applyFiltersAndSort(
                rows, TableStatusFilter.ALL, TableOps.GROUP_FILTER_ALL,
                TableSortMode.STOCK, true, null);
        assertEquals(List.of("b", "c", "a"), r.stream().map(OverviewSnapshot.TableRowSnapshot::itemId).toList());
    }

    // ===== groupRows =====

    @Test
    void groupRows_filterAll_emitsOnlyNonEmptyGroupsInOptionsOrder() {
        List<OverviewSnapshot.GroupOptionSnapshot> options = List.of(
                opt("metal", "Metal"),
                opt("wood", "Wood"),
                opt("stone", "Stone"),
                opt(TableOps.GROUP_UNGROUPED, "Ungrouped")
        );
        List<OverviewSnapshot.TableRowSnapshot> rows = List.of(
                row("a", "A", "wood", 0, 0, 0, 0),
                row("b", "B", "metal", 0, 0, 0, 0)
        );
        List<OverviewSnapshot.TableGroupSnapshot> groups = TableOps.groupRows(
                rows, TableOps.GROUP_FILTER_ALL, options);
        assertEquals(2, groups.size());
        assertEquals("metal", groups.get(0).key());
        assertEquals("Metal", groups.get(0).displayName());
        assertEquals("wood", groups.get(1).key());
    }

    @Test
    void groupRows_filterAll_emptyResultEmitsUngroupedPlaceholder() {
        List<OverviewSnapshot.GroupOptionSnapshot> options = List.of(
                opt(TableOps.GROUP_UNGROUPED, "Ungrouped")
        );
        List<OverviewSnapshot.TableGroupSnapshot> groups = TableOps.groupRows(
                List.of(), TableOps.GROUP_FILTER_ALL, options);
        assertEquals(1, groups.size());
        assertEquals(TableOps.GROUP_UNGROUPED, groups.get(0).key());
        assertEquals("Ungrouped", groups.get(0).displayName());
        assertTrue(groups.get(0).rows().isEmpty());
    }

    @Test
    void groupRows_specificFilter_returnsSingleGroupWithMatchingRows() {
        List<OverviewSnapshot.GroupOptionSnapshot> options = List.of(
                opt("metal", "Metal"),
                opt("wood", "Wood")
        );
        List<OverviewSnapshot.TableRowSnapshot> rows = List.of(
                row("a", "A", "metal", 0, 0, 0, 0),
                row("b", "B", "wood", 0, 0, 0, 0)
        );
        List<OverviewSnapshot.TableGroupSnapshot> groups = TableOps.groupRows(rows, "metal", options);
        assertEquals(1, groups.size());
        assertEquals("metal", groups.get(0).key());
        assertEquals(1, groups.get(0).rows().size());
        assertEquals("a", groups.get(0).rows().get(0).itemId());
    }

    @Test
    void groupRows_blankGroupKey_normalizesToUngrouped() {
        List<OverviewSnapshot.GroupOptionSnapshot> options = List.of(
                opt(TableOps.GROUP_UNGROUPED, "Ungrouped")
        );
        List<OverviewSnapshot.TableRowSnapshot> rows = List.of(
                row("a", "A", "", 0, 0, 0, 0),
                row("b", "B", null, 0, 0, 0, 0)
        );
        List<OverviewSnapshot.TableGroupSnapshot> groups = TableOps.groupRows(
                rows, TableOps.GROUP_FILTER_ALL, options);
        assertEquals(1, groups.size());
        assertEquals(TableOps.GROUP_UNGROUPED, groups.get(0).key());
        assertEquals(2, groups.get(0).rows().size());
    }

    // ===== buildWatchlist =====

    @Test
    void buildWatchlist_preservesItemIdOrder() {
        List<OverviewSnapshot.TableRowSnapshot> rows = List.of(
                row("a", "A", "g", 0, 0, 10, 100),
                row("b", "B", "g", 0, 0, 20, 200),
                row("c", "C", "g", 0, 0, 30, 300)
        );
        List<OverviewSnapshot.WatchlistItemSnapshot> wl = TableOps.buildWatchlist(rows, List.of("c", "a"));
        assertEquals(2, wl.size());
        assertEquals("c", wl.get(0).itemId());
        assertEquals(30, wl.get(0).netPerMinute());
        assertEquals("a", wl.get(1).itemId());
    }

    @Test
    void buildWatchlist_silentlyDropsMissingItems() {
        List<OverviewSnapshot.TableRowSnapshot> rows = List.of(
                row("a", "A", "g", 0, 0, 0, 0)
        );
        List<OverviewSnapshot.WatchlistItemSnapshot> wl = TableOps.buildWatchlist(rows, List.of("a", "ghost"));
        assertEquals(1, wl.size());
        assertEquals("a", wl.get(0).itemId());
    }

    @Test
    void buildWatchlist_emptyInputsReturnEmpty() {
        assertTrue(TableOps.buildWatchlist(List.of(), List.of("a")).isEmpty());
        assertTrue(TableOps.buildWatchlist(
                List.of(row("a", "A", "g", 0, 0, 0, 0)), List.of()).isEmpty());
        assertTrue(TableOps.buildWatchlist(
                List.of(row("a", "A", "g", 0, 0, 0, 0)), null).isEmpty());
    }
}

package com.yuyinrl.resourceobserver.client.screen.v2.crafting;

import com.yuyinrl.resourceobserver.client.screen.v2.LatestSnapshotProvider;
import com.yuyinrl.resourceobserver.client.ui.CraftingViewModel;
import com.yuyinrl.resourceobserver.client.ui.CraftingViewModelMapper;

/**
 * 合成样板可用性查询。
 *
 * <p>普通物品行（Overview / Storage）不能假设所有物品都能下单；只有出现在 AE2 craftables
 * 列表中的物品才代表网络存在合成样板。</p>
 */
public final class CraftingAvailability {

    private CraftingAvailability() {}

    public record Match(boolean craftable, String networkId) {
        public static final Match NONE = new Match(false, null);
    }

    public static Match find(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Match.NONE;
        }
        var payload = LatestSnapshotProvider.getPayload();
        if (payload == null) {
            return Match.NONE;
        }
        CraftingViewModel vm = CraftingViewModelMapper.fromPayload(payload, "");
        if (vm == null || vm.craftables() == null || vm.craftables().isEmpty()) {
            return Match.NONE;
        }
        for (CraftingViewModel.CraftableRow row : vm.craftables()) {
            if (row != null && itemId.equals(row.itemId())) {
                return new Match(true, row.networkId());
            }
        }
        return Match.NONE;
    }
}

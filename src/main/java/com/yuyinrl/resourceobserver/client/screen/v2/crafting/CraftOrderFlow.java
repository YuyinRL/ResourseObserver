package com.yuyinrl.resourceobserver.client.screen.v2.crafting;

import com.yuyinrl.resourceobserver.client.screen.ResourceTerminalScreenV2;
import com.yuyinrl.resourceobserver.client.screen.dialog.CraftOrderDialog;
import com.yuyinrl.resourceobserver.client.screen.dialog.CraftPlanReviewDialog;
import com.yuyinrl.resourceobserver.client.screen.dialog.CraftPlanningDialog;
import com.yuyinrl.resourceobserver.client.screen.dialog.DialogHost;
import com.yuyinrl.resourceobserver.client.screen.v2.V2UiActions;
import com.yuyinrl.resourceobserver.network.CraftingPlanResultPayload;
import com.yuyinrl.resourceobserver.network.UiActionType;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * G2.3 + G2.4 合成下单完整流程编排：
 * <pre>
 *  CraftOrderDialog (输入数量)
 *   → 提交 PLACE_CRAFT_ORDER + 弹 CraftPlanningDialog
 *   → 服务端 CraftingPlanResultPayload 回调
 *   → 关闭计算中弹窗 → 弹 CraftPlanReviewDialog
 *   → CONFIRM/CANCEL → 发送 CONFIRM_CRAFT_ORDER / CANCEL_CRAFT_PLAN
 * </pre>
 *
 * <p>与 {@code CraftingSubTabBuilder.showOrderPopup → showPlanningPopup → showReviewPopup} 行为对齐。</p>
 */
public final class CraftOrderFlow {

    private CraftOrderFlow() {}

    /**
     * 启动一次合成下单流程。
     *
     * @param itemId      目标物品 id（必需）
     * @param displayName 物品显示名（用于弹窗标题）
     * @param networkId   首选网络 id；可空（让服务端自动选择默认）
     */
    public static void start(String itemId, String displayName, String networkId) {
        DialogHost host = DialogHost.current();
        if (host == null || itemId == null || itemId.isBlank()) return;
        CraftingAvailability.Match craftable = CraftingAvailability.find(itemId);
        if (!craftable.craftable()) return;
        String targetNetworkId = networkId == null || networkId.isBlank() ? craftable.networkId() : networkId;

        String name = (displayName == null || displayName.isBlank()) ? itemId : displayName;
        Component title = Component.translatable(
                "screen.resourceobserver.crafting.order_title", name);

        host.open(new CraftOrderDialog(title, 1L,
                amount -> onAmountConfirmed(itemId, name, targetNetworkId, amount)));
    }

    private static void onAmountConfirmed(String itemId, String displayName, String networkId, long amount) {
        if (amount <= 0L) return;
        DialogHost host = DialogHost.current();
        if (host == null) return;

        // 注册回调：服务端回执 → 关计算中 → 弹审核
        ResourceTerminalScreenV2.v2PlanCallback = plan -> onPlanResult(plan, displayName, amount);

        // 发送下单请求
        V2UiActions.send(
                UiActionType.PLACE_CRAFT_ORDER,
                itemId,
                List.of(networkId == null ? "" : networkId),
                String.valueOf(amount));

        // 弹"计算中"
        Component planningTitle = Component.translatable(
                "screen.resourceobserver.crafting.plan_calculating", displayName);
        host.open(new CraftPlanningDialog(planningTitle, ""));
    }

    private static void onPlanResult(CraftingPlanResultPayload plan, String displayName, long amount) {
        DialogHost host = DialogHost.current();
        if (host == null || plan == null) return;
        // 关闭"计算中"
        host.closeTop();

        String outName = (plan.finalOutputDisplayName() != null && !plan.finalOutputDisplayName().isBlank())
                ? plan.finalOutputDisplayName()
                : displayName;
        long outAmt = plan.finalOutputAmount() > 0 ? plan.finalOutputAmount() : amount;

        Component reviewTitle = Component.translatable(
                "screen.resourceobserver.crafting.plan_title", outName, String.valueOf(outAmt));

        host.open(new CraftPlanReviewDialog(reviewTitle, plan, action -> onReviewAction(plan, action)));
    }

    private static void onReviewAction(CraftingPlanResultPayload plan, CraftPlanReviewDialog.Action action) {
        if (plan == null || plan.planId() == null || plan.planId().isBlank()) return;
        switch (action) {
            case CONFIRM -> V2UiActions.send(UiActionType.CONFIRM_CRAFT_ORDER, plan.planId(), "");
            case CANCEL, NONE -> V2UiActions.send(UiActionType.CANCEL_CRAFT_PLAN, plan.planId(), "");
        }
    }
}

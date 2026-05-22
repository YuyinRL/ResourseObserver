package com.yuyinrl.resourceobserver.client.screen.v2.overview;

import com.yuyinrl.resourceobserver.client.screen.dialog.DialogHost;
import com.yuyinrl.resourceobserver.client.screen.dialog.GroupNameDialog;
import com.yuyinrl.resourceobserver.client.screen.v2.V2UiActions;
import com.yuyinrl.resourceobserver.client.screen.v2.crafting.CraftingAvailability;
import com.yuyinrl.resourceobserver.client.screen.v2.crafting.CraftOrderFlow;
import com.yuyinrl.resourceobserver.client.screen.widget.ContextMenu;
import com.yuyinrl.resourceobserver.client.ui.OverviewViewModel;
import com.yuyinrl.resourceobserver.network.UiActionType;
import com.yuyinrl.resourceobserver.service.snapshot.overview.TableOps;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 构建 Overview 表格的右键菜单。
 *
 * <p>与 ModernUI {@code OverviewPageBuilder.showRowContextPopup} / {@code showGroupHeaderContextPopup}
 * 在 menu 项与 action 上严格对齐：
 * <ul>
 *   <li>行右键：新建分组（→ GroupNameDialog → CREATE_GROUP）+ 移动到子菜单（→ ASSIGN_ITEM_GROUP）</li>
 *   <li>分组头右键：重命名（→ GroupNameDialog → RENAME_GROUP）+ 删除（→ DELETE_GROUP）</li>
 *   <li>系统分组（systemGroup=true）跳过菜单弹出</li>
 * </ul>
 */
public final class OverviewMenuBuilder {

    private OverviewMenuBuilder() {}

    /** 行右键菜单。 */
    public static void openRowMenu(int x, int y, OverviewViewModel.TableRow row,
                                   List<OverviewViewModel.GroupOption> groups) {
        DialogHost host = DialogHost.current();
        if (host == null || row == null) return;

        List<ContextMenu.MenuItem> items = new ArrayList<>();

        CraftingAvailability.Match craftable = CraftingAvailability.find(row.itemId());
        if (craftable.craftable()) {
            items.add(ContextMenu.MenuItem.of(
                    Component.translatable("screen.resourceobserver.crafting.order"),
                    () -> CraftOrderFlow.start(row.itemId(), row.displayName(), craftable.networkId())));
        }

        items.add(ContextMenu.MenuItem.of(
                Component.translatable("screen.resourceobserver.overview.group.menu.create"),
                () -> openCreateGroupDialog(row.itemId())));

        // 子菜单触发：弹出“移动到”二级菜单
        items.add(ContextMenu.MenuItem.submenu(
                Component.translatable("screen.resourceobserver.overview.group.menu.moveto"),
                () -> openMoveToMenu(x + 8, y + 8, row, groups)));

        if (row.groupKey() != null && !row.groupKey().isBlank()
                && !TableOps.GROUP_UNGROUPED.equals(row.groupKey())) {
            items.add(ContextMenu.MenuItem.of(
                    Component.translatable("screen.resourceobserver.overview.group.menu.clear"),
                    () -> V2UiActions.send(UiActionType.CLEAR_ITEM_GROUP, row.itemId(), "")));
        }

        host.openAt(new ContextMenu(items), x, y);
    }

    /** 分组头右键菜单。systemGroup 直接 return。 */
    public static void openGroupHeaderMenu(int x, int y, String groupKey, String displayName,
                                           List<OverviewViewModel.GroupOption> groups) {
        DialogHost host = DialogHost.current();
        if (host == null || groupKey == null || groupKey.isBlank()) return;

        boolean isSystem = isSystemGroup(groupKey, groups);
        if (isSystem) return;

        List<ContextMenu.MenuItem> items = new ArrayList<>();
        items.add(ContextMenu.MenuItem.of(
                Component.translatable("screen.resourceobserver.overview.group.menu.rename"),
                () -> openRenameGroupDialog(groupKey, displayName)));
        items.add(ContextMenu.MenuItem.of(
                Component.translatable("screen.resourceobserver.overview.group.menu.delete"),
                () -> V2UiActions.send(UiActionType.DELETE_GROUP, groupKey, "")));

        host.openAt(new ContextMenu(items), x, y);
    }

    // ===== 内部 =====

    private static void openMoveToMenu(int x, int y, OverviewViewModel.TableRow row,
                                       List<OverviewViewModel.GroupOption> groups) {
        DialogHost host = DialogHost.current();
        if (host == null) return;

        List<ContextMenu.MenuItem> items = new ArrayList<>();
        if (groups == null || groups.isEmpty()) {
            items.add(ContextMenu.MenuItem.disabled(
                    Component.translatable("screen.resourceobserver.overview.group.menu.moveto")));
        } else {
            for (OverviewViewModel.GroupOption opt : groups) {
                if (opt.systemGroup()) continue;
                String key = opt.key();
                if (key == null || key.isBlank()
                        || TableOps.GROUP_FILTER_ALL.equals(key)
                        || TableOps.GROUP_UNGROUPED.equals(key)) continue;
                if (key.equals(row.groupKey())) continue;
                items.add(ContextMenu.MenuItem.of(
                        Component.literal(opt.displayName()),
                        () -> V2UiActions.send(UiActionType.ASSIGN_ITEM_GROUP, row.itemId(), key)));
            }
            if (items.isEmpty()) {
                items.add(ContextMenu.MenuItem.disabled(
                        Component.translatable("screen.resourceobserver.overview.group.menu.moveto")));
            }
        }
        host.openAt(new ContextMenu(items), x, y);
    }

    private static void openCreateGroupDialog(String itemId) {
        DialogHost host = DialogHost.current();
        if (host == null) return;
        host.open(new GroupNameDialog(
                Component.translatable("screen.resourceobserver.overview.group.input.create_title"),
                Component.translatable("screen.resourceobserver.overview.group.input.name_label"),
                "",
                name -> {
                    if (name == null || name.isBlank()) return;
                    V2UiActions.send(UiActionType.CREATE_GROUP, itemId == null ? "" : itemId, name.trim());
                }));
    }

    private static void openRenameGroupDialog(String groupKey, String currentName) {
        DialogHost host = DialogHost.current();
        if (host == null) return;
        host.open(new GroupNameDialog(
                Component.translatable("screen.resourceobserver.overview.group.input.rename_title"),
                Component.translatable("screen.resourceobserver.overview.group.input.name_label"),
                currentName == null ? "" : currentName,
                name -> {
                    if (name == null || name.isBlank()) return;
                    V2UiActions.send(UiActionType.RENAME_GROUP, groupKey, name.trim());
                }));
    }

    private static boolean isSystemGroup(String key, List<OverviewViewModel.GroupOption> groups) {
        if (key == null) return true;
        if (TableOps.GROUP_FILTER_ALL.equals(key) || TableOps.GROUP_UNGROUPED.equals(key)) return true;
        if (groups == null) return false;
        for (OverviewViewModel.GroupOption opt : groups) {
            if (key.equals(opt.key())) return opt.systemGroup();
        }
        return false;
    }
}

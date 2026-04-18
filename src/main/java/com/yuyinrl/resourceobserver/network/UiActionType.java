package com.yuyinrl.resourceobserver.network;

/**
 * UI 操作类型枚举 —— 定义玩家在资源终端 GUI 中可执行的所有操作。
 * 每种操作通过 ObserverUiActionPayload 发送到服务端处理。
 */
public enum UiActionType {
    TOGGLE_WATCH(0),          // 切换物品关注状态（添加/移除关注列表）
    SET_GROUP_FILTER_KEY(1),  // 设置表格分组筛选键
    SET_SORT_MODE(2),         // 设置表格排序模式
    SET_STATUS_FILTER(3),     // 设置表格状态筛选
    RESET_FILTERS(4),         // 重置所有筛选条件为默认值
    ASSIGN_ITEM_GROUP(5),     // 将物品分配到指定分组
    CREATE_GROUP(6),          // 创建新的自定义分组
    RENAME_GROUP(7),          // 重命名已有分组
    DELETE_GROUP(8),          // 删除自定义分组
    CLEAR_ITEM_GROUP(9),      // 清除物品的分组分配（移回未分组）
    RENAME_NETWORK(10);       // 重命名存储网络节点（自定义显示名称）

    private final int id;

    UiActionType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static UiActionType fromId(int id) {
        return EnumLookup.fromId(values(), UiActionType::id, id, TOGGLE_WATCH);
    }
}

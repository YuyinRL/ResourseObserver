package com.yuyinrl.resourceobserver.network;

public enum UiActionType {
    TOGGLE_WATCH(0),
    SET_GROUP_FILTER_KEY(1),
    SET_SORT_MODE(2),
    SET_STATUS_FILTER(3),
    RESET_FILTERS(4),
    ASSIGN_ITEM_GROUP(5),
    CREATE_GROUP(6),
    RENAME_GROUP(7),
    DELETE_GROUP(8),
    CLEAR_ITEM_GROUP(9);

    private final int id;

    UiActionType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static UiActionType fromId(int id) {
        for (UiActionType value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return TOGGLE_WATCH;
    }
}

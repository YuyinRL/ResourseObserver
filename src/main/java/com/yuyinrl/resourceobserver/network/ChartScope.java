package com.yuyinrl.resourceobserver.network;

public enum ChartScope {
    GLOBAL(0),
    ITEM(1);

    private final int id;

    ChartScope(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static ChartScope fromId(int id) {
        for (ChartScope value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return GLOBAL;
    }
}

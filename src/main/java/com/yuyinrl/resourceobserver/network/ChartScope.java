package com.yuyinrl.resourceobserver.network;

/**
 * 图表作用域枚举 —— 决定图表显示的数据范围。
 * - GLOBAL：全局视角，显示所有物品的汇总数据
 * - ITEM：单物品视角，仅显示指定物品的独立数据系列
 */
public enum ChartScope {
    /** 全局视角 —— 显示所有物品的聚合生产/消耗/库存 */
    GLOBAL(0),
    /** 单物品视角 —— 仅显示指定 scopeItemId 的独立数据 */
    ITEM(1);

    private final int id;

    ChartScope(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static ChartScope fromId(int id) {
        return EnumLookup.fromId(values(), ChartScope::id, id, GLOBAL);
    }
}

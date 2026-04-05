package com.yuyinrl.resourceobserver.client.ui;

/**
 * 终端页面枚举 —— 定义资源终端 GUI 的可导航页面。
 * 用于 ResourceTerminalScreen 的标签页切换。
 */
public enum TerminalPage {
    OVERVIEW("screen.resourceobserver.storage.tab.overview"),
    STORAGE_NETWORK("screen.resourceobserver.storage.tab.storage_network"),
    POWER_NETWORK("screen.resourceobserver.storage.tab.power_network");

    private final String translationKey;

    TerminalPage(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }

    /** 获取下一个页面（循环切换） */
    public TerminalPage next() {
        return switch (this) {
            case OVERVIEW -> STORAGE_NETWORK;
            case STORAGE_NETWORK -> POWER_NETWORK;
            case POWER_NETWORK -> OVERVIEW;
        };
    }
}


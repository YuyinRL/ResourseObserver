package com.yuyinrl.resourceobserver.world.item;

import net.minecraft.world.item.Item;

/**
 * Debug terminal opens terminal GUI with debug tab as default.
 * 调试终端物品 —— 与资源终端功能完全相同，但默认打开调试标签页。
 * 继承 ResourceTerminalItem，将 debugPreferred 标志设为 true。
 */
public class DebugTerminalItem extends ResourceTerminalItem {
    public DebugTerminalItem(Item.Properties properties) {
        super(properties, true);
    }
}

package com.yuyinrl.resourceobserver.world.item;

import net.minecraft.world.item.Item;

/**
 * Debug terminal opens terminal GUI with debug tab as default.
 */
public class DebugTerminalItem extends ResourceTerminalItem {
    public DebugTerminalItem(Item.Properties properties) {
        super(properties, true);
    }
}


package com.yuyinrl.resourceobserver.registry;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.world.item.BindingToolItem;
import com.yuyinrl.resourceobserver.world.item.DebugTerminalItem;
import com.yuyinrl.resourceobserver.world.item.ResourceTerminalItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ResourceObserverMod.MODID);

    public static final DeferredItem<BlockItem> OBSERVER_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("observer_block", ModBlocks.OBSERVER_BLOCK);

    public static final DeferredItem<Item> BINDING_TOOL =
            ITEMS.register("binding_tool", () -> new BindingToolItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> RESOURCE_TERMINAL =
            ITEMS.register("resource_terminal", () -> new ResourceTerminalItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> DEBUG_TERMINAL =
            ITEMS.register("debug_terminal", () -> new DebugTerminalItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}

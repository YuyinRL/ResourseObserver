package com.yuyinrl.resourceobserver.registry;

import com.yuyinrl.resourceobserver.DevMode;
import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.world.item.BindingToolItem;
import com.yuyinrl.resourceobserver.world.item.DebugTerminalItem;
import com.yuyinrl.resourceobserver.world.item.ResourceTerminalItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组物品注册表。
 * 注册所有自定义物品，包括方块的物品形态、绑定工具、资源终端等。
 */
public final class ModItems {
    /** 物品延迟注册器 */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ResourceObserverMod.MODID);

    /** 观察者方块对应的物品形态，用于在背包中携带和放置 */
    public static final DeferredItem<BlockItem> OBSERVER_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("observer_block", ModBlocks.OBSERVER_BLOCK);

    /** 绑定工具 —— 两步式操作将观察者方块绑定到资源网络目标方块，不可堆叠 */
    public static final DeferredItem<Item> BINDING_TOOL =
            ITEMS.register("binding_tool", () -> new BindingToolItem(new Item.Properties().stacksTo(1)));

    /** 资源终端 —— 右键使用打开 GUI 界面查看观察者数据和图表，不可堆叠 */
    public static final DeferredItem<Item> RESOURCE_TERMINAL =
            ITEMS.register("resource_terminal", () -> new ResourceTerminalItem(new Item.Properties().stacksTo(1)));

    /**
     * 调试终端 —— 与资源终端功能相同，但默认打开调试视图，不可堆叠。
     * 仅供开发环境使用，玩家发布版本中不显示在创造标签页。
     */
    public static final DeferredItem<Item> DEBUG_TERMINAL =
            ITEMS.register("debug_terminal", () -> new DebugTerminalItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    /** 将物品注册器挂载到模组事件总线 */
    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    /** 调试终端物品是否应对玩家可见（仅在开发环境下） */
    public static boolean isDebugTerminalVisible() {
        return DevMode.isDev();
    }
}

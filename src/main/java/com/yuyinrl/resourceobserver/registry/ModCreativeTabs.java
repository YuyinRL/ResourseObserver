package com.yuyinrl.resourceobserver.registry;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组创造模式标签页注册表。
 * 将模组的所有物品注册到一个自定义的创造模式物品栏标签页中，方便玩家在创造模式中查找。
 */
public final class ModCreativeTabs {
    /** 创造标签页延迟注册器 */
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ResourceObserverMod.MODID);

    /**
     * 主标签页 —— 包含模组所有物品。
     * 位于红石方块标签页之后，使用观察者方块物品作为标签图标。
     * 展示项目：观察者方块、绑定工具、资源终端、调试终端。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.resourceobserver.main"))
                    .withTabsBefore(CreativeModeTabs.REDSTONE_BLOCKS)
                    .icon(() -> ModItems.OBSERVER_BLOCK_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.OBSERVER_BLOCK_ITEM.get());
                        output.accept(ModItems.BINDING_TOOL.get());
                        output.accept(ModItems.RESOURCE_TERMINAL.get());
                        output.accept(ModItems.DEBUG_TERMINAL.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }

    /** 将创造标签页注册器挂载到模组事件总线 */
    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}

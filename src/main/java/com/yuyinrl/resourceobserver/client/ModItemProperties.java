package com.yuyinrl.resourceobserver.client;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.registry.ModItems;
import com.yuyinrl.resourceobserver.world.item.ResourceTerminalItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端物品模型属性注册 —— 为终端物品注册 bound 属性，
 * 用于根据是否绑定观察者切换物品贴图（正常 / 错误状态）。
 */
public final class ModItemProperties {

    private ModItemProperties() {}

    /** 将客户端初始化监听器挂载到模组事件总线 */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModItemProperties::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ResourceLocation boundProp = ResourceLocation.fromNamespaceAndPath(
                    ResourceObserverMod.MODID, "bound");
            ItemProperties.register(ModItems.RESOURCE_TERMINAL.get(), boundProp,
                    (stack, level, entity, seed) -> {
                        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
                        if (data == null) {
                            // 无 NBT（创造模式 / 刚合成）→ 显示正常贴图
                            return 0.0f;
                        }
                        var nbt = data.copyTag();
                        if (nbt.getBoolean(ResourceTerminalItem.TAG_BOUND)
                                && nbt.getBoolean(ResourceTerminalItem.TAG_HAS_NETWORK)) {
                            // 已绑定且观察者有网络连接 → 正常贴图
                            return 0.0f;
                        }
                        // 存在 NBT 但未绑定或观察者无网络 → 错误贴图
                        return 1.0f;
                    });
        });
    }
}

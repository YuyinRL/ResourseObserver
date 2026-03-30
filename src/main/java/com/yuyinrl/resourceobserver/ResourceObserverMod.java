package com.yuyinrl.resourceobserver;

import com.mojang.logging.LogUtils;
import com.yuyinrl.resourceobserver.registry.ModBlockEntities;
import com.yuyinrl.resourceobserver.registry.ModBlocks;
import com.yuyinrl.resourceobserver.registry.ModCreativeTabs;
import com.yuyinrl.resourceobserver.registry.ModItems;
import com.yuyinrl.resourceobserver.network.ModNetworking;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ResourceObserverMod.MODID)
public class ResourceObserverMod {
    public static final String MODID = "resourceobserver";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ResourceObserverMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModNetworking.register(modEventBus);
    }
}

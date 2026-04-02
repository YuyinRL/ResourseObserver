package com.yuyinrl.resourceobserver;

import com.mojang.logging.LogUtils;
import com.yuyinrl.resourceobserver.client.ChartRenderShaders;
import com.yuyinrl.resourceobserver.registry.ModBlockEntities;
import com.yuyinrl.resourceobserver.registry.ModBlocks;
import com.yuyinrl.resourceobserver.registry.ModCreativeTabs;
import com.yuyinrl.resourceobserver.registry.ModItems;
import com.yuyinrl.resourceobserver.network.ModNetworking;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Resource Observer 模组主类 —— NeoForge 1.21.1 资源网络监控模组。
 * <p>
 * 本模组允许玩家放置"观察者方块"并绑定到 AE2 物品存储网络或 Flux Networks 能量网络，
 * 实时采集网络中的资源生产、消耗与库存数据，通过"资源终端"在游戏内查看统计与图表。
 * <p>
 * 核心游戏对象：
 * 1. 观察者方块（Observer Block）：绑定网络，周期性采样数据
 * 2. 绑定工具（Binding Tool）：两步式右键绑定/解绑流程
 * 3. 资源终端（Resource Terminal）：打开 GUI 查看数据与图表
 */
@Mod(ResourceObserverMod.MODID)
public class ResourceObserverMod {
    /** 模组唯一标识符，用于注册表、资源路径和网络通道 */
    public static final String MODID = "resourceobserver";
    /** 模组全局日志记录器 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组构造函数，在 NeoForge 加载时调用。
     * 按顺序注册所有模组内容：方块、物品、方块实体、创造模式标签页、网络通道。
     * 仅在客户端环境下注册图表渲染着色器。
     */
    public ResourceObserverMod(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);       // 注册方块（观察者方块）
        ModItems.register(modEventBus);        // 注册物品（绑定工具、资源终端等）
        ModBlockEntities.register(modEventBus); // 注册方块实体类型
        ModCreativeTabs.register(modEventBus);  // 注册创造模式物品栏标签页
        ModNetworking.register(modEventBus);    // 注册客户端-服务端网络数据包
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // 仅客户端：注册图表渲染所需的自定义着色器
            ChartRenderShaders.register(modEventBus);
        }
    }
}

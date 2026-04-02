package com.yuyinrl.resourceobserver.registry;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.world.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组方块注册表。
 * 使用 NeoForge 的延迟注册机制（DeferredRegister）注册所有自定义方块。
 */
public final class ModBlocks {
    /** 方块延迟注册器，绑定到模组命名空间 */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ResourceObserverMod.MODID);

    /**
     * 观察者方块 —— 模组的核心方块。
     * 放置在世界中后可通过绑定工具连接到资源网络，进行周期性数据采样。
     * 属性：金属材质色，硬度 3.0，爆炸抗性 6.0
     */
    public static final DeferredBlock<ObserverBlock> OBSERVER_BLOCK = BLOCKS.register(
            "observer_block",
            () -> new ObserverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F))
    );

    private ModBlocks() {
    }

    /** 将方块注册器挂载到模组事件总线 */
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}

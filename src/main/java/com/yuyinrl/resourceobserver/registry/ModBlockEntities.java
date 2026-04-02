package com.yuyinrl.resourceobserver.registry;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组方块实体注册表。
 * 方块实体（BlockEntity）为方块提供额外的数据存储和逻辑能力，
 * 观察者方块实体负责存储绑定信息、采样统计数据及 NBT 持久化。
 */
public final class ModBlockEntities {
    /** 方块实体类型延迟注册器 */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ResourceObserverMod.MODID);

    /**
     * 观察者方块实体类型注册。
     * 将 ObserverBlockEntity 与 OBSERVER_BLOCK 方块绑定，使方块具备数据存储与 tick 逻辑。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ObserverBlockEntity>> OBSERVER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "observer_block_entity",
                    () -> BlockEntityType.Builder.of(ObserverBlockEntity::new, ModBlocks.OBSERVER_BLOCK.get()).build(null)
            );

    private ModBlockEntities() {
    }

    /** 将方块实体注册器挂载到模组事件总线 */
    public static void register(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }
}

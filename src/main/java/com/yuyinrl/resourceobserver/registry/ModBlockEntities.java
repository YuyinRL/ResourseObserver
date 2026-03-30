package com.yuyinrl.resourceobserver.registry;

import com.yuyinrl.resourceobserver.ResourceObserverMod;
import com.yuyinrl.resourceobserver.world.block.entity.ObserverBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ResourceObserverMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ObserverBlockEntity>> OBSERVER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "observer_block_entity",
                    () -> BlockEntityType.Builder.of(ObserverBlockEntity::new, ModBlocks.OBSERVER_BLOCK.get()).build(null)
            );

    private ModBlockEntities() {
    }

    public static void register(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }
}

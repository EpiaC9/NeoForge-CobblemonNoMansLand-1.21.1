package net.epiac9.cobblemonnml.registry;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.field.AquaBubbleBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CobblemonNML.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AquaBubbleBlockEntity>> AQUA_BUBBLE =
            BLOCK_ENTITIES.register("aqua_bubble", () -> BlockEntityType.Builder.of(
                    AquaBubbleBlockEntity::new, ModBlocks.AQUA_BUBBLE.get()).build(null));

    private ModBlockEntities() {}
}

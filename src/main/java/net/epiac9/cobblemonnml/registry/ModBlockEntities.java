package net.epiac9.cobblemonnml.registry;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.field.AquaBubbleBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.field.GrassSeedBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.field.GrassFlowerBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugCarapaceBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.field.PlasmaBallBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockConstructBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyIllusionBlockEntity;
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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlasmaBallBlockEntity>> PLASMA_BALL =
            BLOCK_ENTITIES.register("plasma_ball", () -> BlockEntityType.Builder.of(
                    PlasmaBallBlockEntity::new, ModBlocks.PLASMA_BALL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrassSeedBlockEntity>> GRASS_SEED =
            BLOCK_ENTITIES.register("grass_seed", () -> BlockEntityType.Builder.of(
                    GrassSeedBlockEntity::new, ModBlocks.GRASS_SEED.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrassFlowerBlockEntity>> GRASS_FLOWER =
            BLOCK_ENTITIES.register("grass_flower", () -> BlockEntityType.Builder.of(
                    GrassFlowerBlockEntity::new, ModBlocks.GRASS_FLOWER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ActionBattleBugCarapaceBlockEntity>>
            ACTION_BATTLE_BUG_CARAPACE = BLOCK_ENTITIES.register("action_battle_bug_carapace",
            () -> BlockEntityType.Builder.of(ActionBattleBugCarapaceBlockEntity::new,
                    ModBlocks.ACTION_BATTLE_BUG_CARAPACE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ActionBattleRockConstructBlockEntity>>
            ACTION_BATTLE_ROCK_CONSTRUCT = BLOCK_ENTITIES.register("action_battle_rock_construct",
            () -> BlockEntityType.Builder.of(ActionBattleRockConstructBlockEntity::new,
                    ModBlocks.ACTION_BATTLE_ROCK_CONSTRUCT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ActionBattleFairyIllusionBlockEntity>>
            ACTION_BATTLE_FAIRY_ILLUSION = BLOCK_ENTITIES.register("action_battle_fairy_illusion",
            () -> BlockEntityType.Builder.of(ActionBattleFairyIllusionBlockEntity::new,
                    ModBlocks.ACTION_BATTLE_FAIRY_ILLUSION.get()).build(null));

    private ModBlockEntities() {}
}

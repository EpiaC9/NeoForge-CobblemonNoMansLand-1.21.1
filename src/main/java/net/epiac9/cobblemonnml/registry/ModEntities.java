package net.epiac9.cobblemonnml.registry;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionBattleProjectileEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, CobblemonNML.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ActionBattleProjectileEntity>> ACTION_BATTLE_PROJECTILE =
            ENTITIES.register("action_battle_projectile",
                    () -> EntityType.Builder.<ActionBattleProjectileEntity>of(
                                    ActionBattleProjectileEntity::new,
                                    MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("action_battle_projectile"));

    private ModEntities() {}
}

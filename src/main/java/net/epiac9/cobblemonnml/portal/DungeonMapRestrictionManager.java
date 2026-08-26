package net.epiac9.cobblemonnml.portal;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = CobblemonNML.MOD_ID)
public final class DungeonMapRestrictionManager {

    private static final ResourceLocation NO_MINIMAP =
            ResourceLocation.fromNamespaceAndPath("xaerominimap", "no_minimap");

    private static final ResourceLocation NO_WORLD_MAP =
            ResourceLocation.fromNamespaceAndPath("xaeroworldmap", "no_world_map");

    private static final String APPLIED_TAG =
            "cobblemonnml_dungeon_map_restrictions";

    private DungeonMapRestrictionManager() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) {
                enforce(player);
                continue;
            }

            if (player.getPersistentData().getBoolean(APPLIED_TAG)) {
                clear(player);
            }
        }
    }

    public static void enforce(ServerPlayer player) {
        if (player == null) {
            return;
        }

        boolean appliedAny = false;
        appliedAny |= enforceEffect(player, NO_MINIMAP);
        appliedAny |= enforceEffect(player, NO_WORLD_MAP);

        if (appliedAny) {
            player.getPersistentData().putBoolean(APPLIED_TAG, true);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }

        removeEffect(player, NO_MINIMAP);
        removeEffect(player, NO_WORLD_MAP);
        player.getPersistentData().remove(APPLIED_TAG);
    }

    private static boolean enforceEffect(
            ServerPlayer player,
            ResourceLocation effectId
    ) {
        Holder.Reference<MobEffect> effect =
                BuiltInRegistries.MOB_EFFECT
                        .getHolder(effectId)
                        .orElse(null);

        if (effect == null) {
            return false;
        }

        if (!player.hasEffect(effect)) {
            player.addEffect(
                    new MobEffectInstance(
                            effect,
                            MobEffectInstance.INFINITE_DURATION,
                            0,
                            false,
                            false,
                            false
                    )
            );
        }

        return true;
    }

    private static void removeEffect(
            ServerPlayer player,
            ResourceLocation effectId
    ) {
        BuiltInRegistries.MOB_EFFECT
                .getHolder(effectId)
                .ifPresent(player::removeEffect);
    }
}

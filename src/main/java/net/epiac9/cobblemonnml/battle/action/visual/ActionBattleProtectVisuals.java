package net.epiac9.cobblemonnml.battle.action.visual;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectStance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;

public final class ActionBattleProtectVisuals {
    private static final Vector3f SHIELD_COLOR = new Vector3f(0.48F, 0.82F, 0.62F);

    private ActionBattleProtectVisuals() {}

    public static void tickBattle(ActionBattleSession session, ServerLevel level, Pokemon playerPokemon, Pokemon trainerPokemon) {
        if (session == null || level == null || level.getGameTime() % 2L != 0L) return;
        tickPokemon(session, level, playerPokemon);
        tickPokemon(session, level, trainerPokemon);
    }

    private static void tickPokemon(ActionBattleSession session, ServerLevel level, Pokemon pokemon) {
        if (pokemon == null) return;
        PokemonEntity entity = pokemon.getEntity();
        if (entity == null || entity.isRemoved() || entity.level() != level) return;
        ActionBattleProtectStance stance = ActionBattleProtectController.global().activeStance(session.battleId(), pokemon.getUuid(), level.getGameTime());
        if (stance == null) return;
        emitShield(level, entity, stance.deterioratingShieldLevel());
    }

    private static void emitShield(ServerLevel level, PokemonEntity entity, int levelValue) {
        int deterioration = Math.clamp(levelValue, 1, 9);
        int segments = Math.max(6, 22 - deterioration * 2);
        double radius = Math.max(0.55D, entity.getBbWidth() * 0.82D);
        double centerY = entity.getY() + entity.getBbHeight() * 0.52D;
        double height = Math.max(0.65D, entity.getBbHeight() * 0.58D);
        DustParticleOptions dust = new DustParticleOptions(SHIELD_COLOR, 0.9F);
        for (int i = 0; i < segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            double x = entity.getX() + Math.cos(angle) * radius;
            double z = entity.getZ() + Math.sin(angle) * radius;
            double y = centerY + Math.sin(angle * 2.0D) * height * 0.28D;
            level.sendParticles(dust, x, y, z, 1, 0.015D, 0.02D, 0.015D, 0.0D);
        }
        int cracks = Math.max(0, deterioration - 2);
        if (cracks > 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), centerY, entity.getZ(), cracks, radius * 0.55D, height * 0.38D, radius * 0.55D, 0.02D + deterioration * 0.003D);
    }
}

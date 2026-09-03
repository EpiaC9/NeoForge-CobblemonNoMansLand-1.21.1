package net.epiac9.cobblemonnml.battle.action.typeeffect.ice;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public final class ActionBattleIceVisuals {
    private static final long SHIVER_INTERVAL_TICKS = 2L;
    private static final long PARTICLE_INTERVAL_TICKS = 10L;

    private ActionBattleIceVisuals() {}

    public static void tick(ServerLevel level) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (level == null || sessionId == null || level.getGameTime() % SHIVER_INTERVAL_TICKS != 0L) return;
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof PokemonEntity pokemon) || pokemon.isRemoved()) continue;
            controller.iceView(sessionId, pokemon.getPokemon().getUuid(), level.getGameTime()).ifPresent(ice -> {
                float amplitude = ice.phase() == ActionBattleIceState.Phase.CHILL ? 0.75F : 1.75F;
                float direction = (level.getGameTime() / SHIVER_INTERVAL_TICKS) % 2L == 0L ? 1.0F : -1.0F;
                pokemon.setYHeadRot(pokemon.getYHeadRot() + amplitude * direction);
                if (ice.phase() == ActionBattleIceState.Phase.FROSTBITE
                        && level.getGameTime() % PARTICLE_INTERVAL_TICKS == 0L) {
                    level.sendParticles(ParticleTypes.SNOWFLAKE, pokemon.getX(), pokemon.getY() + pokemon.getBbHeight() * 0.55D,
                            pokemon.getZ(), 2, 0.16D, 0.22D, 0.16D, 0.01D);
                }
            });
        }
    }
}

package net.epiac9.cobblemonnml.battle.action.typeeffect.fire;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public final class ActionBattleFireParticleController {
    private static final long PARTICLE_INTERVAL_TICKS = 20L;

    private ActionBattleFireParticleController() {}

    public static void tick(ServerLevel level) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (level == null || sessionId == null || level.getGameTime() % PARTICLE_INTERVAL_TICKS != 0L) return;
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof PokemonEntity pokemon) || pokemon.isRemoved()) continue;
            controller.fireView(sessionId, pokemon.getPokemon().getUuid(), level.getGameTime()).ifPresent(fire -> {
                if (fire.phase() == ActionBattleFireState.Phase.CINDERS) {
                    level.sendParticles(ParticleTypes.SMOKE, pokemon.getX(), pokemon.getY() + pokemon.getBbHeight() * 0.55D,
                            pokemon.getZ(), 1, 0.12D, 0.18D, 0.12D, 0.005D);
                } else if (fire.phase() == ActionBattleFireState.Phase.BURN) {
                    level.sendParticles(ParticleTypes.SMALL_FLAME, pokemon.getX(), pokemon.getY() + pokemon.getBbHeight() * 0.45D,
                            pokemon.getZ(), 1, 0.14D, 0.20D, 0.14D, 0.005D);
                }
            });
        }
    }
}

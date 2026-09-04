package net.epiac9.cobblemonnml.battle.action.typeeffect.poison;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public final class ActionBattlePoisonParticleController {
    private ActionBattlePoisonParticleController() {}

    public static void tick(ServerLevel level) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (level == null || sessionId == null || level.getGameTime() % 10L != 0L) return;
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof PokemonEntity pokemon) || pokemon.isRemoved()) continue;
            controller.poisonView(sessionId, pokemon.getPokemon().getUuid(), level.getGameTime()).ifPresent(view -> {
                int green = ActionBattlePoisonVisuals.greenParticleCount(view.level());
                int purple = ActionBattlePoisonVisuals.purpleParticleCount(view.level());
                if (green > 0) level.sendParticles(ParticleTypes.COMPOSTER, pokemon.getX(),
                        pokemon.getY() + pokemon.getBbHeight() * 0.55D, pokemon.getZ(),
                        green, 0.18D, 0.22D, 0.18D, 0.01D);
                if (purple > 0) level.sendParticles(ParticleTypes.WITCH, pokemon.getX(),
                        pokemon.getY() + pokemon.getBbHeight() * 0.55D, pokemon.getZ(),
                        purple, 0.18D, 0.22D, 0.18D, 0.01D);
            });
        }
    }
}

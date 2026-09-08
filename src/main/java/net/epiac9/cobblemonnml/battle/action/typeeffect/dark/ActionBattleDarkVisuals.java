package net.epiac9.cobblemonnml.battle.action.typeeffect.dark;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

public final class ActionBattleDarkVisuals {
    private ActionBattleDarkVisuals() {}

    public static void tick(PokemonEntity pokemon, ActionBattleDarkState.View view, long currentTick) {
        if (pokemon == null || view == null || !(pokemon.level() instanceof ServerLevel level)) return;
        int count = ActionBattleDarkVisualRules.particleCount(view.blindnessActive(), view.stackCount());
        if (count <= 0 || currentTick % ActionBattleDarkVisualRules.cadenceTicks(view.stackCount()) != 0L) return;
        double spread = Math.max(0.08D, pokemon.getBbWidth() * 0.18D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pokemon.getX(), pokemon.getEyeY(), pokemon.getZ(),
                count, spread, 0.08D, spread, 0.005D);
    }
}

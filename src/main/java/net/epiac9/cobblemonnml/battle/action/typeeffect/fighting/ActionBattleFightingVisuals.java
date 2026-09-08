package net.epiac9.cobblemonnml.battle.action.typeeffect.fighting;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

public final class ActionBattleFightingVisuals {
    public static final String OUTRAGE_BUILDUP_STATUS_ID = "TYPE_FIGHTING_OUTRAGE_BUILDUP";
    public static final String OUTRAGE_STATUS_ID = "TYPE_FIGHTING_OUTRAGE";
    public static final String EXHAUSTED_STATUS_ID = "TYPE_FIGHTING_EXHAUSTED";

    private ActionBattleFightingVisuals() {}

    public static void emitBuildup(PokemonEntity pokemon, boolean activated) {
        if (!server(pokemon)) return;
        ServerLevel level = (ServerLevel) pokemon.level();
        AABB box = pokemon.getBoundingBox();
        int count = activated ? 16 : 6;
        level.sendParticles(ParticleTypes.CRIT, box.getCenter().x, box.minY + box.getYsize() * 0.55D,
                box.getCenter().z, count, box.getXsize() * 0.35D, box.getYsize() * 0.25D,
                box.getZsize() * 0.35D, activated ? 0.12D : 0.04D);
        if (activated) level.sendParticles(ParticleTypes.SWEEP_ATTACK, box.getCenter().x,
                box.minY + box.getYsize() * 0.55D, box.getCenter().z, 3,
                box.getXsize() * 0.25D, box.getYsize() * 0.18D, box.getZsize() * 0.25D, 0.0D);
    }

    public static void tick(PokemonEntity pokemon, ActionBattleFightingState.View view, long currentTick) {
        if (!server(pokemon) || view == null) return;
        ServerLevel level = (ServerLevel) pokemon.level();
        AABB box = pokemon.getBoundingBox();
        if (view.outrageActive() && currentTick % 4L == 0L) {
            level.sendParticles(ParticleTypes.CRIT, box.getCenter().x, box.minY + box.getYsize() * 0.55D,
                    box.getCenter().z, 3, box.getXsize() * 0.42D, box.getYsize() * 0.38D,
                    box.getZsize() * 0.42D, 0.025D);
        } else if (view.exhaustedActive() && currentTick % 10L == 0L) {
            level.sendParticles(ParticleTypes.SMOKE, box.getCenter().x, box.minY + box.getYsize() * 0.30D,
                    box.getCenter().z, 2, box.getXsize() * 0.22D, box.getYsize() * 0.12D,
                    box.getZsize() * 0.22D, 0.005D);
        }
    }

    private static boolean server(PokemonEntity pokemon) {
        return pokemon != null && !pokemon.isRemoved() && pokemon.level() instanceof ServerLevel;
    }
}

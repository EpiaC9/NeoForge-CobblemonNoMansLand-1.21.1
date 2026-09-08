package net.epiac9.cobblemonnml.battle.action.typeeffect.dragon;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class ActionBattleDragonVisuals {
    public static final String BUILDUP_STATUS_ID = "TYPE_DRAGON_UPROAR_BUILDUP";
    public static final String ACTIVE_STATUS_ID = "TYPE_DRAGON_UPROAR";

    private ActionBattleDragonVisuals() {}

    public static void beginRoar(PokemonEntity pokemon) {
        if (pokemon == null || pokemon.isRemoved() || !(pokemon.level() instanceof ServerLevel level)) return;
        level.playSound(null, pokemon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.NEUTRAL, 1.0F, 1.15F);
        emitRoar(pokemon, 18);
    }

    public static void tickRoar(PokemonEntity pokemon, long currentTick) {
        if (currentTick % 3L == 0L) emitRoar(pokemon, 5);
    }

    private static void emitRoar(PokemonEntity pokemon, int count) {
        if (pokemon == null || pokemon.isRemoved() || !(pokemon.level() instanceof ServerLevel level)) return;
        var box = pokemon.getBoundingBox();
        level.sendParticles(ParticleTypes.DRAGON_BREATH, box.getCenter().x,
                box.minY + box.getYsize() * 0.65D, box.getCenter().z, count,
                Math.max(0.25D, box.getXsize() * 0.55D),
                Math.max(0.20D, box.getYsize() * 0.30D),
                Math.max(0.25D, box.getZsize() * 0.55D), 0.04D);
    }
}

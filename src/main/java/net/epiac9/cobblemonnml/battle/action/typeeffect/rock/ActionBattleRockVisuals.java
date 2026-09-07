package net.epiac9.cobblemonnml.battle.action.typeeffect.rock;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class ActionBattleRockVisuals {
    private static final int PROC_PARTICLE_COUNT = 9;
    private ActionBattleRockVisuals() {}

    public static void emitProcBurst(PokemonEntity pokemon) {
        emit(pokemon, PROC_PARTICLE_COUNT, 0.30D, 0.25D, 0.04D);
    }

    public static void emitEnduranceAura(PokemonEntity pokemon, long currentTick) {
        if (!ActionBattleRockVisualRules.auraDue(currentTick)) return;
        emit(pokemon, 3, 0.42D, 0.48D, 0.01D);
    }

    public static void emitEnduranceConsumed(PokemonEntity pokemon, boolean rockReflection) {
        emit(pokemon, ActionBattleRockVisualRules.consumeBurstCount(rockReflection), 0.48D, 0.42D, 0.09D);
    }

    private static void emit(PokemonEntity pokemon, int count, double horizontalScale,
                             double verticalScale, double speed) {
        if (pokemon == null || pokemon.isRemoved() || !(pokemon.level() instanceof ServerLevel level)) return;
        AABB box = pokemon.getBoundingBox();
        double horizontal = Math.max(0.12D, Math.max(box.getXsize(), box.getZsize()) * horizontalScale);
        double vertical = Math.max(0.16D, box.getYsize() * verticalScale);
        BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        level.sendParticles(stone, box.getCenter().x, box.minY + box.getYsize() * 0.48D, box.getCenter().z,
                count, horizontal, vertical, horizontal, speed);
    }
}

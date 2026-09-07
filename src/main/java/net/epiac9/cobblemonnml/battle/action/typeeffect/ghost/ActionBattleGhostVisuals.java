package net.epiac9.cobblemonnml.battle.action.typeeffect.ghost;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

public final class ActionBattleGhostVisuals {
    private ActionBattleGhostVisuals() {}

    public static void emitApplication(PokemonEntity target, ActionBattleGhostCurseType type) {
        emit(target, ParticleTypes.SOUL, 4, 0.55D, 0.025D);
        ActionBattleGhostVisualRules.Cue cue = ActionBattleGhostVisualRules.cue(type);
        if (cue != null) emit(target, particle(cue.style()), cue.applyCount(), cue.heightFraction(), 0.035D);
    }

    public static void emitStatApplication(PokemonEntity target) {
        emit(target, ParticleTypes.SOUL, 5, 0.55D, 0.03D);
        emit(target, ParticleTypes.WITCH, 5, 0.55D, 0.02D);
    }

    public static void emitEvent(PokemonEntity target, ActionBattleGhostCurseType type) {
        ActionBattleGhostVisualRules.Cue cue = ActionBattleGhostVisualRules.cue(type);
        if (cue != null) emit(target, particle(cue.style()), cue.eventCount(), cue.heightFraction(), 0.06D);
    }

    public static void emitAmbient(PokemonEntity target, ActionBattleGhostCurseType type, long currentTick) {
        if (!ActionBattleGhostVisualRules.ambientDue(currentTick)) return;
        ActionBattleGhostVisualRules.Cue cue = ActionBattleGhostVisualRules.cue(type);
        if (cue != null) emit(target, particle(cue.style()), 2, cue.heightFraction(), 0.005D);
    }

    private static ParticleOptions particle(ActionBattleGhostVisualRules.Style style) {
        return switch (style) {
            case SOUL -> ParticleTypes.SOUL;
            case WITCH -> ParticleTypes.WITCH;
            case SMOKE -> ParticleTypes.SMOKE;
            case ASH -> ParticleTypes.ASH;
            case PORTAL -> ParticleTypes.PORTAL;
            case DAMAGE -> ParticleTypes.DAMAGE_INDICATOR;
        };
    }

    private static void emit(PokemonEntity pokemon, ParticleOptions particle, int count,
                             double heightFraction, double speed) {
        if (pokemon == null || pokemon.isRemoved() || !(pokemon.level() instanceof ServerLevel level)) return;
        AABB box = pokemon.getBoundingBox();
        level.sendParticles(particle, box.getCenter().x,
                box.minY + box.getYsize() * heightFraction, box.getCenter().z, count,
                Math.max(0.12D, box.getXsize() * 0.35D),
                Math.max(0.10D, box.getYsize() * 0.22D),
                Math.max(0.12D, box.getZsize() * 0.35D), speed);
    }
}

package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ActionBattleBugVisuals {
    public static final String SHEDDING_STATUS_ID = "TYPE_BUG_SHEDDING";
    public static final String ATTACK_STATUS_ID = "TYPE_BUG_ATTACK";
    public static final String SPECIAL_ATTACK_STATUS_ID = "TYPE_BUG_SPECIAL_ATTACK";
    public static final String CARAPACE_STATUS_ID = "TYPE_BUG_CARAPACE";
    public static final String EFFECT_GUARD_STATUS_ID = "TYPE_BUG_EFFECT_GUARD";
    public static final String COMBINED_CARAPACE_STATUS_ID = "TYPE_BUG_COMBINED_CARAPACE";
    public static final String SPEED_STATUS_ID = "TYPE_BUG_SPEED";

    private ActionBattleBugVisuals() {}

    public static void sheddingWrap(PokemonEntity pokemon, long tick) {
        if (!(pokemon.level() instanceof ServerLevel level) || tick % 4L != 0L) return;
        AABB box = pokemon.getBoundingBox();
        double radius = Math.max(0.35D, Math.max(box.getXsize(), box.getZsize()) * 0.65D);
        for (int point = 0; point < ActionBattleBugVisualPlan.threadPoints(); point++) {
            double progress = point / (double) (ActionBattleBugVisualPlan.threadPoints() - 1);
            double angle = progress * Math.PI * 4.0D + tick * 0.08D;
            level.sendParticles(ParticleTypes.END_ROD,
                    box.getCenter().x + Math.cos(angle) * radius,
                    box.minY + box.getYsize() * (0.12D + progress * 0.76D),
                    box.getCenter().z + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void explosion(PokemonEntity target) {
        if (!(target.level() instanceof ServerLevel level)) return;
        AABB box = target.getBoundingBox();
        Vec3 center = box.getCenter();
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z,
                2, 0.35D, 0.35D, 0.35D, 0.0D);
        level.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z,
                18, ActionBattleBugVisualPlan.explosionRadius(), 0.55D,
                ActionBattleBugVisualPlan.explosionRadius(), 0.04D);
    }

    public static void dashTrail(ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        int samples = ActionBattleBugVisualPlan.dashSamples(delta.length());
        for (int point = 0; point < samples; point++) {
            Vec3 sample = start.add(delta.scale(samples == 1 ? 0.0D : point / (double) (samples - 1)));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, sample.x, sample.y + 0.35D, sample.z,
                    2, 0.12D, 0.18D, 0.12D, 0.02D);
        }
    }
}

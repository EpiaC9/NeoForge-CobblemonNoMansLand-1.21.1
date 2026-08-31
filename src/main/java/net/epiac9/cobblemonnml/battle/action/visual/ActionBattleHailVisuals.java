package net.epiac9.cobblemonnml.battle.action.visual;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.ThreadLocalRandom;

public final class ActionBattleHailVisuals {
    private ActionBattleHailVisuals() {}

    public static void emitChannel(ServerLevel level, PokemonEntity caster) {
        if (level == null || caster == null || caster.isRemoved() || level.getGameTime() % 5L != 0L) return;
        double y = caster.getY() + caster.getBbHeight() * 0.65D;
        level.sendParticles(ParticleTypes.SNOWFLAKE, caster.getX(), y, caster.getZ(), 2,
                Math.max(0.15D, caster.getBbWidth() * 0.35D), 0.25D, Math.max(0.15D, caster.getBbWidth() * 0.35D), 0.01D);
    }

    public static void emitAreaAmbient(ServerLevel level, ActionBattlePersistentAreaState area) {
        if (level == null || area == null || level.getGameTime() % 4L != 0L) return;
        ActionBattlePosition anchor = area.anchor();
        double cloudY = anchor.y() + area.preset().verticalHeight();
        double radius = area.preset().horizontalRadius();
        level.sendParticles(ParticleTypes.CLOUD, anchor.x(), cloudY, anchor.z(), 3, radius * 0.45D, 0.2D, radius * 0.45D, 0.01D);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 4; i++) {
            double angle = random.nextDouble(Math.PI * 2.0D);
            double distance = Math.sqrt(random.nextDouble()) * radius;
            double x = anchor.x() + Math.cos(angle) * distance;
            double z = anchor.z() + Math.sin(angle) * distance;
            double y = anchor.y() + random.nextDouble(area.preset().verticalHeight());
            level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.05D, 0.15D, 0.05D, 0.01D);
        }
    }

    public static void emitPulse(ServerLevel level, ActionBattlePersistentAreaState area) {
        if (level == null || area == null) return;
        ActionBattlePosition anchor = area.anchor();
        double y = anchor.y() + area.preset().verticalHeight() * 0.5D;
        double radius = area.preset().horizontalRadius();
        level.sendParticles(ParticleTypes.SNOWFLAKE, anchor.x(), y, anchor.z(), 18, radius * 0.65D, area.preset().verticalHeight() * 0.45D, radius * 0.65D, 0.04D);
        level.sendParticles(ParticleTypes.CLOUD, anchor.x(), anchor.y() + area.preset().verticalHeight(), anchor.z(), 5, radius * 0.55D, 0.25D, radius * 0.55D, 0.02D);
    }
}

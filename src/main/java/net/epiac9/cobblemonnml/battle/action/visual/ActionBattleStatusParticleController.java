package net.epiac9.cobblemonnml.battle.action.visual;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.concurrent.ThreadLocalRandom;

public final class ActionBattleStatusParticleController {
    private static final int CINDERS_INTERVAL_TICKS = 4;
    private static final int BURN_INTERVAL_TICKS = 5;
    private static final int CINDERS_EMBERS_PER_EMISSION = 1;
    private static final int BURN_EMBERS_PER_EMISSION = 3;
    private static final int BURN_FLAMES_PER_EMISSION = 1;
    private static final int FREEZE_INTERVAL_TICKS = 5;
    private static final int FROSTBITE_INTERVAL_TICKS = 4;
    private static final int FREEZE_MOTES_PER_EMISSION = 1;
    private static final int FROSTBITE_MOTES_PER_EMISSION = 2;
    private static final int FROSTBITE_WISPS_PER_EMISSION = 1;

    private ActionBattleStatusParticleController() {}

    public static void tickBattle(ActionBattleSession session, ServerLevel level, Pokemon playerPokemon, Pokemon trainerPokemon) {
        if (session == null || level == null) return;
        tickPokemon(session, level, playerPokemon);
        tickPokemon(session, level, trainerPokemon);
    }

    private static void tickPokemon(ActionBattleSession session, ServerLevel level, Pokemon pokemon) {
        if (pokemon == null) return;
        PokemonEntity entity = pokemon.getEntity();
        if (entity == null || entity.isRemoved() || entity.level() != level) return;
        long tick = level.getGameTime();
        ActionBattleEffectController effects = ActionBattleEffectController.global();
        if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.BURN, tick) && tick % BURN_INTERVAL_TICKS == 0L) emitBurnAmbient(level, entity);
        else if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.CINDERS, tick) && tick % CINDERS_INTERVAL_TICKS == 0L) emitCindersAmbient(level, entity);
        if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.FROSTBITE, tick) && tick % FROSTBITE_INTERVAL_TICKS == 0L) emitFrostbiteAmbient(level, entity);
        else if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.FREEZE, tick) && tick % FREEZE_INTERVAL_TICKS == 0L) emitFreezeAmbient(level, entity);
    }

    private static void emitCindersAmbient(ServerLevel level, PokemonEntity entity) { emitAcrossBody(level, entity, CINDERS_EMBERS_PER_EMISSION, ParticleTypes.LAVA, 0.01D); }
    private static void emitBurnAmbient(ServerLevel level, PokemonEntity entity) {
        emitAcrossBody(level, entity, BURN_EMBERS_PER_EMISSION, ParticleTypes.LAVA, 0.01D);
        emitAcrossBody(level, entity, BURN_FLAMES_PER_EMISSION, ParticleTypes.SMALL_FLAME, 0.01D);
    }
    private static void emitFreezeAmbient(ServerLevel level, PokemonEntity entity) { emitAcrossBody(level, entity, FREEZE_MOTES_PER_EMISSION, ParticleTypes.SNOWFLAKE, 0.01D); }
    private static void emitFrostbiteAmbient(ServerLevel level, PokemonEntity entity) {
        emitAcrossBody(level, entity, FROSTBITE_MOTES_PER_EMISSION, ParticleTypes.SNOWFLAKE, 0.015D);
        emitAcrossBody(level, entity, FROSTBITE_WISPS_PER_EMISSION, ParticleTypes.CLOUD, 0.005D);
    }

    public static void emitBurnDotBurst(ServerLevel level, PokemonEntity entity) {
        if (level == null || entity == null || entity.isRemoved()) return;
        AABB box = entity.getBoundingBox();
        double cx = box.getCenter().x, cy = box.getCenter().y, cz = box.getCenter().z;
        level.sendParticles(ParticleTypes.LAVA, cx, cy, cz, 14, Math.max(0.25D, box.getXsize() * 0.55D), Math.max(0.35D, box.getYsize() * 0.45D), Math.max(0.25D, box.getZsize() * 0.55D), 0.12D);
        level.sendParticles(ParticleTypes.FLAME, cx, cy, cz, 10, Math.max(0.20D, box.getXsize() * 0.40D), Math.max(0.30D, box.getYsize() * 0.40D), Math.max(0.20D, box.getZsize() * 0.40D), 0.05D);
    }

    public static void emitFrostbiteDotBurst(ServerLevel level, PokemonEntity entity) {
        if (level == null || entity == null || entity.isRemoved()) return;
        AABB box = entity.getBoundingBox();
        double cx = box.getCenter().x, cy = box.getCenter().y, cz = box.getCenter().z;
        level.sendParticles(ParticleTypes.SNOWFLAKE, cx, cy, cz, 16, Math.max(0.25D, box.getXsize() * 0.55D), Math.max(0.35D, box.getYsize() * 0.45D), Math.max(0.25D, box.getZsize() * 0.55D), 0.10D);
        level.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 8, Math.max(0.20D, box.getXsize() * 0.40D), Math.max(0.30D, box.getYsize() * 0.40D), Math.max(0.20D, box.getZsize() * 0.40D), 0.03D);
    }

    private static void emitAcrossBody(ServerLevel level, PokemonEntity entity, int count, ParticleOptions particle, double speed) {
        AABB box = entity.getBoundingBox();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double x = random.nextDouble(box.minX, Math.max(box.minX + 0.001D, box.maxX));
            double y = random.nextDouble(box.minY, Math.max(box.minY + 0.001D, box.maxY));
            double z = random.nextDouble(box.minZ, Math.max(box.minZ + 0.001D, box.maxZ));
            level.sendParticles(particle, x, y, z, 1, 0.03D, 0.05D, 0.03D, speed);
        }
    }
}

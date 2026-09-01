package net.epiac9.cobblemonnml.battle.action.visual;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentType;
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
    private static final int POISON_INTERVAL_TICKS = 5;
    private static final int TOXIC_1_INTERVAL_TICKS = 5;
    private static final int TOXIC_2_INTERVAL_TICKS = 4;
    private static final int TOXIC_3_INTERVAL_TICKS = 3;
    private static final int PARALYSIS_INTERVAL_TICKS = 4;
    private static final int DROWSINESS_INTERVAL_TICKS = 10;
    private static final int SLEEP_INTERVAL_TICKS = 6;
    private static final int DROWSINESS_GRACE_INTERVAL_TICKS = 12;
    private static final int CONFUSION_INTERVAL_TICKS = 5;
    private static final int EVASION_INTERVAL_TICKS = 6;
    private static final int PERSISTENT_INTERVAL_TICKS = 8;

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
        if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.TOXIC_3, tick) && tick % TOXIC_3_INTERVAL_TICKS == 0L) emitPoisonAmbient(level, entity, 3);
        else if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.TOXIC_2, tick) && tick % TOXIC_2_INTERVAL_TICKS == 0L) emitPoisonAmbient(level, entity, 2);
        else if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.TOXIC_1, tick) && tick % TOXIC_1_INTERVAL_TICKS == 0L) emitPoisonAmbient(level, entity, 1);
        else if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.POISON, tick) && tick % POISON_INTERVAL_TICKS == 0L) emitPoisonAmbient(level, entity, 0);
        if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.PARALYSIS, tick) && tick % PARALYSIS_INTERVAL_TICKS == 0L) emitParalysisAmbient(level, entity);
        if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.SLEEP, tick) && tick % SLEEP_INTERVAL_TICKS == 0L) emitSleepAmbient(level, entity);
        else if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.DROWSINESS, tick) && tick % DROWSINESS_INTERVAL_TICKS == 0L) emitDrowsinessAmbient(level, entity);
        if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.DROWSINESS_GRACE, tick) && tick % DROWSINESS_GRACE_INTERVAL_TICKS == 0L) emitDrowsinessGraceAmbient(level, entity);
        if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.CONFUSION, tick) && tick % CONFUSION_INTERVAL_TICKS == 0L) emitConfusionAmbient(level, entity);
        if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.EVASION, tick) && tick % EVASION_INTERVAL_TICKS == 0L) emitEvasionAmbient(level, entity);
        if (tick % PERSISTENT_INTERVAL_TICKS == 0L) {
            ActionBattlePersistentController persistent = ActionBattlePersistentController.global();
            if (persistent.has(session.battleId(), pokemon.getUuid(), ActionBattlePersistentType.LEECH_SEED, tick)) emitAcrossBody(level, entity, 2, ParticleTypes.HAPPY_VILLAGER, 0.005D);
            if (persistent.has(session.battleId(), pokemon.getUuid(), ActionBattlePersistentType.GHOST_CURSE, tick)) emitAcrossBody(level, entity, 2, ParticleTypes.SOUL, 0.01D);
            if (persistent.has(session.battleId(), pokemon.getUuid(), ActionBattlePersistentType.PERISH_SONG, tick)) emitAcrossBody(level, entity, 1, ParticleTypes.NOTE, 0.005D);
            if (persistent.has(session.battleId(), pokemon.getUuid(), ActionBattlePersistentType.BOUND, tick)) emitAcrossBody(level, entity, 2, ParticleTypes.ASH, 0.005D);
            if (persistent.has(session.battleId(), pokemon.getUuid(), ActionBattlePersistentType.NIGHTMARE, tick)) emitAcrossBody(level, entity, 2, ParticleTypes.PORTAL, 0.01D);
        }
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

    private static void emitPoisonAmbient(ServerLevel level, PokemonEntity entity, int toxicLevel) {
        int greenMotes = switch (toxicLevel) { case 0 -> 2; case 1 -> 2; case 2 -> 1; default -> 0; };
        int purpleMotes = switch (toxicLevel) { case 0 -> 1; case 1 -> 2; case 2 -> 3; default -> 5; };
        if (greenMotes > 0) emitAcrossBody(level, entity, greenMotes, ParticleTypes.SPORE_BLOSSOM_AIR, 0.005D);
        emitAcrossBody(level, entity, purpleMotes, ParticleTypes.WITCH, toxicLevel >= 2 ? 0.015D : 0.008D);
        if (toxicLevel >= 2) emitAcrossBody(level, entity, toxicLevel, ParticleTypes.MYCELIUM, 0.005D);
    }

    public static void emitBurnDotBurst(ServerLevel level, PokemonEntity entity) {
        if (level == null || entity == null || entity.isRemoved()) return;
        AABB box = entity.getBoundingBox();
        double cx = box.getCenter().x, cy = box.getCenter().y, cz = box.getCenter().z;
        level.sendParticles(ParticleTypes.LAVA, cx, cy, cz, 14, Math.max(0.25D, box.getXsize() * 0.55D), Math.max(0.35D, box.getYsize() * 0.45D), Math.max(0.25D, box.getZsize() * 0.55D), 0.12D);
        level.sendParticles(ParticleTypes.FLAME, cx, cy, cz, 10, Math.max(0.20D, box.getXsize() * 0.40D), Math.max(0.30D, box.getYsize() * 0.40D), Math.max(0.20D, box.getZsize() * 0.40D), 0.05D);
    }

    public static void emitPoisonDotBurst(ServerLevel level, PokemonEntity entity, int toxicLevel) {
        if (level == null || entity == null || entity.isRemoved()) return;
        AABB box = entity.getBoundingBox();
        double cx = box.getCenter().x, cy = box.getCenter().y, cz = box.getCenter().z;
        int purple = switch (Math.clamp(toxicLevel, 0, 3)) { case 0 -> 8; case 1 -> 11; case 2 -> 15; default -> 20; };
        int green = switch (Math.clamp(toxicLevel, 0, 3)) { case 0 -> 8; case 1 -> 5; case 2 -> 2; default -> 0; };
        if (green > 0) level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, cx, cy, cz, green, Math.max(0.25D, box.getXsize() * 0.50D), Math.max(0.35D, box.getYsize() * 0.45D), Math.max(0.25D, box.getZsize() * 0.50D), 0.03D);
        level.sendParticles(ParticleTypes.WITCH, cx, cy, cz, purple, Math.max(0.25D, box.getXsize() * 0.55D), Math.max(0.35D, box.getYsize() * 0.50D), Math.max(0.25D, box.getZsize() * 0.55D), toxicLevel >= 2 ? 0.12D : 0.08D);
    }


    private static void emitDrowsinessAmbient(ServerLevel level, PokemonEntity entity) {
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.ENCHANT, box.getCenter().x, box.maxY + 0.20D, box.getCenter().z, 2, 0.18D, 0.08D, 0.18D, 0.01D);
    }

    private static void emitSleepAmbient(ServerLevel level, PokemonEntity entity) {
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.CLOUD, box.getCenter().x, box.maxY + 0.15D, box.getCenter().z, 3, 0.20D, 0.10D, 0.20D, 0.01D);
        level.sendParticles(ParticleTypes.ENCHANT, box.getCenter().x, box.maxY + 0.30D, box.getCenter().z, 2, 0.16D, 0.08D, 0.16D, 0.01D);
    }

    private static void emitDrowsinessGraceAmbient(ServerLevel level, PokemonEntity entity) {
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.END_ROD, box.getCenter().x, box.maxY + 0.15D, box.getCenter().z, 1, 0.12D, 0.08D, 0.12D, 0.005D);
    }

    public static void emitSleepTransitionBurst(ServerLevel level, PokemonEntity entity) {
        if (level == null || entity == null || entity.isRemoved()) return;
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.CLOUD, box.getCenter().x, box.getCenter().y, box.getCenter().z, 10, Math.max(0.20D, box.getXsize() * 0.4D), Math.max(0.25D, box.getYsize() * 0.35D), Math.max(0.20D, box.getZsize() * 0.4D), 0.02D);
        level.sendParticles(ParticleTypes.ENCHANT, box.getCenter().x, box.maxY + 0.15D, box.getCenter().z, 8, 0.25D, 0.15D, 0.25D, 0.02D);
    }

    public static void emitWakeBurst(ServerLevel level, PokemonEntity entity) {
        if (level == null || entity == null || entity.isRemoved()) return;
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.END_ROD, box.getCenter().x, box.getCenter().y, box.getCenter().z, 12, Math.max(0.20D, box.getXsize() * 0.4D), Math.max(0.25D, box.getYsize() * 0.35D), Math.max(0.20D, box.getZsize() * 0.4D), 0.03D);
    }


    private static void emitEvasionAmbient(ServerLevel level, PokemonEntity entity) {
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, box.getCenter().x, box.getCenter().y, box.getCenter().z, 2, Math.max(0.20D, box.getXsize() * 0.45D), Math.max(0.25D, box.getYsize() * 0.35D), Math.max(0.20D, box.getZsize() * 0.45D), 0.01D);
    }

    public static void emitEvasionBurst(ServerLevel level, PokemonEntity entity) {
        if (level == null || entity == null || entity.isRemoved()) return;
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, box.getCenter().x, box.getCenter().y, box.getCenter().z, 14, Math.max(0.25D, box.getXsize() * 0.55D), Math.max(0.30D, box.getYsize() * 0.45D), Math.max(0.25D, box.getZsize() * 0.55D), 0.06D);
        level.sendParticles(ParticleTypes.POOF, box.getCenter().x, box.getCenter().y, box.getCenter().z, 5, 0.18D, 0.18D, 0.18D, 0.02D);
    }

    private static void emitConfusionAmbient(ServerLevel level, PokemonEntity entity) {
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.PORTAL, box.getCenter().x, box.maxY + 0.15D, box.getCenter().z, 3, 0.30D, 0.12D, 0.30D, 0.02D);
    }

    public static void emitConfusionBurst(ServerLevel level, PokemonEntity entity) {
        if (level == null || entity == null || entity.isRemoved()) return;
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.PORTAL, box.getCenter().x, box.getCenter().y, box.getCenter().z, 14, Math.max(0.25D, box.getXsize() * 0.5D), Math.max(0.30D, box.getYsize() * 0.4D), Math.max(0.25D, box.getZsize() * 0.5D), 0.08D);
    }

    private static void emitParalysisAmbient(ServerLevel level, PokemonEntity entity) {
        emitAcrossBody(level, entity, 2, ParticleTypes.ELECTRIC_SPARK, 0.015D);
    }

    public static void emitParalysisTriggerBurst(ServerLevel level, PokemonEntity entity) {
        if (level == null || entity == null || entity.isRemoved()) return;
        AABB box = entity.getBoundingBox();
        double cx = box.getCenter().x, cy = box.getCenter().y, cz = box.getCenter().z;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 18, Math.max(0.25D, box.getXsize() * 0.55D), Math.max(0.35D, box.getYsize() * 0.50D), Math.max(0.25D, box.getZsize() * 0.55D), 0.10D);
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

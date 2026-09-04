package net.epiac9.cobblemonnml.battle.action.visual;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentType;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.concurrent.ThreadLocalRandom;

public final class ActionBattleStatusParticleController {
    private static final int SLEEP_INTERVAL_TICKS = 6;
    private static final int CONFUSION_INTERVAL_TICKS = 5;
    private static final int EVASION_INTERVAL_TICKS = 6;
    private static final int PERSISTENT_INTERVAL_TICKS = 8;
    private static final int DROWSY_INTERVAL_TICKS = 10;

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
        if (effects.hasStatus(session.dungeonSessionId(), pokemon.getUuid(), ActionBattleStatus.SLEEP, tick) && tick % SLEEP_INTERVAL_TICKS == 0L) emitSleepAmbient(level, entity);
        if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.CONFUSION, tick) && tick % CONFUSION_INTERVAL_TICKS == 0L) emitConfusionAmbient(level, entity);
        if (effects.hasStatus(session.battleId(), pokemon.getUuid(), ActionBattleStatus.EVASION, tick) && tick % EVASION_INTERVAL_TICKS == 0L) emitEvasionAmbient(level, entity);
        if (tick % PERSISTENT_INTERVAL_TICKS == 0L) {
            ActionBattlePersistentController persistent = ActionBattlePersistentController.global();
            if (persistent.has(session.battleId(), pokemon.getUuid(), ActionBattlePersistentType.PERISH_SONG, tick)) emitAcrossBody(level, entity, 1, ParticleTypes.NOTE, 0.005D);
            if (persistent.has(session.battleId(), pokemon.getUuid(), ActionBattlePersistentType.BOUND, tick)) emitAcrossBody(level, entity, 2, ParticleTypes.ASH, 0.005D);
            if (persistent.has(session.battleId(), pokemon.getUuid(), ActionBattlePersistentType.NIGHTMARE, tick)) emitAcrossBody(level, entity, 2, ParticleTypes.PORTAL, 0.01D);
        }
        if (tick % DROWSY_INTERVAL_TICKS == 0L && ActionBattleTypeEffectController.global()
                .drowsyView(session.dungeonSessionId(), pokemon.getUuid(), tick).isPresent()) {
            AABB box = entity.getBoundingBox();
            level.sendParticles(ParticleTypes.ENCHANT, box.getCenter().x, box.maxY + 0.20D, box.getCenter().z,
                    2, 0.18D, 0.08D, 0.18D, 0.005D);
        }
    }

    private static void emitSleepAmbient(ServerLevel level, PokemonEntity entity) {
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.CLOUD, box.getCenter().x, box.maxY + 0.15D, box.getCenter().z, 3, 0.20D, 0.10D, 0.20D, 0.01D);
        level.sendParticles(ParticleTypes.ENCHANT, box.getCenter().x, box.maxY + 0.30D, box.getCenter().z, 2, 0.16D, 0.08D, 0.16D, 0.01D);
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

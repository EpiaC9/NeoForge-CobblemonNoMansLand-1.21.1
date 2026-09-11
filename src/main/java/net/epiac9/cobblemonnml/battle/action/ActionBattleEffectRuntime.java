package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlController;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatLinkCleanup;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleFieldSideMoveFamily;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentEvent;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentTick;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentType;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleProtectVisuals;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleStatusParticleController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectRuntime;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleDrowsyController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.psychic.ActionBattlePsychicChannelRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockVisuals;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugRuntime;
import net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleRampageController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattlePropulsionController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelVisuals;
import net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleWeightedKnockbackController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassFlowerRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.poison.ActionBattlePoisonSludgeRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyIllusionRuntime;
import net.epiac9.cobblemonnml.battle.action.interrupt.ActionBattleInterruptController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ActionBattleEffectRuntime {
    private static final ActionBattleStatLinkCleanup STAT_LINK_CLEANUP = new ActionBattleStatLinkCleanup(
            ActionBattleEffectController.global());

    private ActionBattleEffectRuntime() {}

    static void tickBattle(ActionBattleSession session, ServerLevel level, ActionBattlePokemonRefs refs) {
        if (session == null || level == null) return;
        ActionBattleFieldSideMoveFamily.tickBattle(session, level);
        ActionBattleGhostRuntime.global().tickBattle(session, level);
        ActionBattlePropulsionController.tickBattle(session, level);
        ActionBattleAerialMoveController.tickBattle(session, level, level.getGameTime());
        ActionBattleWeightedKnockbackController.tick(level);
        ActionBattleGrassFlowerRuntime.tick(level, session.battleId(), level.getGameTime());
        ActionBattlePoisonSludgeRuntime.tick(level, session.battleId(), level.getGameTime());
        ActionBattlePsychicChannelRuntime.tickBattle(session, level);

        Set<UUID> activeProtectPokemon = new HashSet<>();
        for (UUID playerUUID : session.playerUUIDs()) {
            if (session.playerActivePokemonUUID(playerUUID) != null) activeProtectPokemon.add(session.playerActivePokemonUUID(playerUUID));
        }
        if (session.trainerActivePokemonUUID() != null) activeProtectPokemon.add(session.trainerActivePokemonUUID());
        ActionBattleProtectController.global().tickBattle(session.battleId(), activeProtectPokemon);

        long currentTick = level.getGameTime();
        ActionBattleControlController.global().tickBattle(session.battleId(), currentTick);
        refreshImprisonMoveSets(session, refs, currentTick);
        net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleInfatuationController
                .tickBattle(session, level, currentTick);
        if (refs != null) {
            for (Pokemon pokemon : refs.allPlayerPokemon()) {
                ActionBattleDarkRuntime.tickState(session, pokemon.getUuid(), currentTick);
                trackRuntimeState(session, level, pokemon, currentTick);
            }
            if (refs.trainerPokemon() != null) ActionBattleDarkRuntime.tickState(session, refs.trainerPokemon().getUuid(), currentTick);
            trackRuntimeState(session, level, refs.trainerPokemon(), currentTick);
        }
        syncHazeBattleZone(session, level, currentTick);
        observeDamageFeedback(session, refs);
        ActionBattleStatusDotRuntime.tickBattle(session, refs, currentTick);
        ActionBattleEffectController.global().tickBattle(session.battleId(), currentTick);
        ActionBattleRockController.global().tickBattle(session.battleId(), currentTick);
        ActionBattleSteelRuntime.tickBattle(session.battleId(), currentTick);
        List<ActionBattlePersistentTick> persistentTicks = ActionBattlePersistentController.global().tickBattle(session.battleId(), currentTick);
        applyPersistentTicks(session, level, persistentTicks, currentTick);
        if (refs != null) {
            for (Pokemon pokemon : refs.allPlayerPokemon()) {
                ActionBattleStatusParticleController.tickBattle(session, level, pokemon, refs.trainerPokemon());
                ActionBattleProtectVisuals.tickBattle(session, level, pokemon, refs.trainerPokemon());
            }
        }
        observeDamageFeedback(session, refs);
    }

    private static void refreshImprisonMoveSets(ActionBattleSession session, ActionBattlePokemonRefs refs,
                                                 long currentTick) {
        if (session == null || refs == null) return;
        for (Pokemon pokemon : refs.allPlayerPokemon()) refreshImprisonMoveSet(session, pokemon, currentTick);
        refreshImprisonMoveSet(session, refs.trainerPokemon(), currentTick);
    }

    private static void refreshImprisonMoveSet(ActionBattleSession session, Pokemon pokemon, long currentTick) {
        if (pokemon == null) return;
        Set<String> moveIds = new HashSet<>();
        for (var move : pokemon.getMoveSet()) if (move != null) moveIds.add(move.getName());
        if (!moveIds.isEmpty()) ActionBattleControlController.global().refreshImprison(
                session.battleId(), pokemon.getUuid(), moveIds, currentTick);
    }

    static void seedDamageFeedback(ActionBattleSession session, Pokemon pokemon) {
        if (session == null || pokemon == null) return;
        ActionBattleDamageFeedbackController.global().seedPokemon(session.battleId(), pokemon.getUuid(), pokemon.getCurrentHealth());
    }

    static void clearBattle(UUID battleId) {
        if (battleId == null) return;
        ActionBattleFieldSideMoveFamily.clearBattle(battleId);
        ActionBattleProtectController.global().clearBattle(battleId);
        STAT_LINK_CLEANUP.clearBattle(battleId);
        ActionBattlePersistentController.global().clearBattle(battleId);
        ActionBattleControlController.global().clearBattle(battleId);
        ActionBattleDamageFeedbackController.global().clearBattle(battleId);
        ActionBattleEvasionController.clearBattle(battleId);
        ActionBattleRockController.global().clearBattle(battleId);
        ActionBattleGhostRuntime.global().clearBattle(battleId);
        ActionBattleBugRuntime.clearBattle(battleId);
        ActionBattleFlyingRuntime.clearBattle(battleId);
        ActionBattlePropulsionController.clearBattle(battleId);
        ActionBattleAerialMoveController.clearBattle(battleId);
        ActionBattleTargetTracker.global().clearBattle(battleId);
        ActionBattleSteelRuntime.clearBattle(battleId);
        ActionBattleWeightedKnockbackController.clearBattle(battleId);
        ActionBattleInterruptController.clearBattle(battleId);
        ActionBattleSwapTransitionGuard.clearBattle(battleId);
        ActionBattleGrassFlowerRuntime.clearBattle(battleId);
        ActionBattlePoisonSludgeRuntime.clearBattle(battleId);
        ActionBattleFairyIllusionRuntime.clearBattle(null, battleId);
        ActionBattlePsychicChannelRuntime.clearBattle(battleId);
        net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleInfatuationController.clearBattle(battleId);
    }

    static void clearAll() {
        STAT_LINK_CLEANUP.clearAll();
        ActionBattleRockController.global().clearAll();
        ActionBattleGhostRuntime.global().clearAll();
        ActionBattleBugRuntime.clearAll();
        ActionBattleRampageController.global().clearAll();
        ActionBattleDragonRuntime.clearAll();
        ActionBattleDarkRuntime.clearAll();
        ActionBattleFlyingRuntime.clearAll();
        ActionBattlePropulsionController.clearAll();
        ActionBattleAerialMoveController.clearAll();
        ActionBattleTargetTracker.global().clearAll();
        ActionBattleSteelRuntime.clearAll();
        ActionBattleWeightedKnockbackController.clearAll();
        ActionBattleInterruptController.clearAll();
        ActionBattleSwapTransitionGuard.clearAll();
        ActionBattleGrassFlowerRuntime.clearAll();
        ActionBattlePoisonSludgeRuntime.clearAll();
        ActionBattleFairyIllusionRuntime.clearAll();
        ActionBattlePsychicChannelRuntime.clearAll();
        net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleInfatuationController.clearAll();
    }

    static void onPokemonUnavailable(ActionBattleSession session, UUID pokemonId,
                                     boolean fainted, long currentTick) {
        onPokemonUnavailable(session, pokemonId, fainted, currentTick, true);
    }

    static void onPokemonUnavailable(ActionBattleSession session, UUID pokemonId,
                                     boolean fainted, long currentTick, boolean applyDragonCleanup) {
        if (session == null || pokemonId == null || currentTick < 0L) return;
        ActionBattleDrowsyController.onPokemonRecalled(session, pokemonId, currentTick);
        ActionBattleTypeEffectRuntime.onPokemonRecalled(session.dungeonSessionId(), pokemonId, currentTick);
        ActionBattleRockController.global().onPokemonUnavailable(session.battleId(), pokemonId);
        ActionBattleGhostRuntime.global().onPokemonUnavailable(session.battleId(), pokemonId);
        ActionBattleBugRuntime.clearPokemon(session, pokemonId, fainted);
        session.clearLastAcceptedMoveHereDirective(pokemonId);
        ActionBattleRampageController.global().clearPokemon(session.battleId(), pokemonId);
        if (applyDragonCleanup) ActionBattleDragonRuntime.onPokemonUnavailable(session, pokemonId, fainted, currentTick);
        ActionBattleDarkRuntime.onPokemonUnavailable(session, pokemonId, fainted, currentTick);
        ActionBattleFlyingRuntime.clearPokemon(session, pokemonId);
        ActionBattlePropulsionController.clearPokemon(session, pokemonId);
        ActionBattleAerialMoveController.clearPokemon(session, pokemonId);
        ActionBattleTargetTracker.global().clearPokemon(session.battleId(), pokemonId);
        ActionBattleSteelRuntime.clearPokemon(session.battleId(), pokemonId);
        ActionBattleWeightedKnockbackController.clearPokemon(pokemonId);
        ActionBattlePsychicChannelRuntime.clearPokemon(pokemonId);
        ActionBattleInterruptController.clearPokemon(session.battleId(), pokemonId);
        net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleInfatuationController
                .clearPokemon(session.battleId(), pokemonId);
        STAT_LINK_CLEANUP.onPokemonUnavailable(session.battleId(), pokemonId, currentTick);
        ActionBattlePersistentController.global().onPokemonUnavailable(session.battleId(), pokemonId, fainted, currentTick);
        ActionBattleControlController.global().onPokemonUnavailable(session.battleId(), pokemonId, fainted, currentTick);
        ActionBattleProtectController.global().onPokemonRecalled(session.battleId(), pokemonId);
    }

    private static void trackRuntimeState(ActionBattleSession session, ServerLevel level, Pokemon pokemon, long currentTick) {
        if (session == null || level == null || pokemon == null) return;
        PokemonEntity entity = pokemon.getEntity();
        if (entity == null || entity.isRemoved() || entity.level() != level) return;
        ActionBattleEvasionController.record(session, entity, currentTick);
        ActionBattleSleepController.tickPokemon(session, entity, currentTick);
        ActionBattleBugRuntime.tickPokemon(session, entity, currentTick);
        ActionBattleDragonRuntime.tickPokemon(session, level, entity, currentTick);
        ActionBattleDarkRuntime.tickPokemon(session, entity, currentTick);
        ActionBattleFlyingRuntime.tickPokemon(session, entity, currentTick);
        ActionBattleSteelVisuals.tick(session, entity, currentTick);
        syncNightmareWithSleep(session, entity, currentTick);
        if (ActionBattleRockController.global().enduranceView(
                session.battleId(), pokemon.getUuid(), currentTick).isPresent()) {
            ActionBattleRockVisuals.emitEnduranceAura(entity, currentTick);
        }
    }

    private static void syncNightmareWithSleep(ActionBattleSession session, PokemonEntity entity, long currentTick) {
        if (session == null || entity == null) return;
        UUID pokemonUUID = entity.getPokemon().getUuid();
        ActionBattlePersistentController persistent = ActionBattlePersistentController.global();
        if (persistent.has(session.battleId(), pokemonUUID, ActionBattlePersistentType.NIGHTMARE, currentTick)
                && !ActionBattleSleepController.isSleeping(session, pokemonUUID, currentTick)) {
            persistent.onSleepEnded(session.battleId(), pokemonUUID);
        }
    }

    private static void applyPersistentTicks(ActionBattleSession session, ServerLevel level, List<ActionBattlePersistentTick> ticks, long currentTick) {
        if (session == null || level == null || ticks == null || ticks.isEmpty()) return;
        for (ActionBattlePersistentTick tick : ticks) applyPersistentTick(session, level, tick, currentTick);
    }

    private static void applyPersistentTick(ActionBattleSession session, ServerLevel level, ActionBattlePersistentTick tick, long currentTick) {
        if (tick == null || tick.event() == null) return;
        ActionBattlePersistentEvent event = tick.event();
        if (event.kind() == ActionBattlePersistentEvent.Kind.ENDED) return;
        Pokemon target = findBattlePokemon(session, level, tick.targetPokemonUUID());
        if (target == null || target.isFainted()) return;
        if (event.kind() == ActionBattlePersistentEvent.Kind.FAINT) {
            int before = target.getCurrentHealth();
            target.setCurrentHealth(0);
            ActionBattleDamageFeedbackController.global().recordDamage(session.battleId(), target.getUuid(), before, 0, ActionBattleDamageFeedbackCategory.DOT);
            DebugLog.log("[CobblemonNML] Action battle Perish countdown reached zero. Battle=" + session.battleId() + ", pokemon=" + target.getUuid());
            return;
        }
        int maxHealth = Math.max(1, target.getMaxHealth());
        int before = target.getCurrentHealth();
        int damage = Math.max(1, (int) Math.floor(maxHealth * event.maxHealthFraction()));
        PokemonEntity deployed = target.getEntity();
        if (deployed != null && !deployed.isRemoved() && deployed.level() == level) {
            int actualDamage = ActionBattleGhostRuntime.global().applyDot(
                    deployed, damage, event.type().name().toLowerCase(java.util.Locale.ROOT), currentTick);
            DebugLog.log("[CobblemonNML] Action battle persistent tick. Battle=" + session.battleId()
                    + ", effect=" + event.type() + ", pokemon=" + target.getUuid()
                    + ", damage=" + actualDamage + ", hp=" + target.getCurrentHealth() + "/" + maxHealth);
            return;
        }
        int after = Math.max(0, before - damage);
        target.setCurrentHealth(after);
        int actualDamage = Math.max(0, before - after);
        ActionBattleDamageFeedbackController.global().recordDamage(session.battleId(), target.getUuid(), before, after, ActionBattleDamageFeedbackCategory.DOT);
        DebugLog.log("[CobblemonNML] Action battle persistent tick. Battle=" + session.battleId() + ", effect=" + event.type()
                + ", pokemon=" + target.getUuid() + ", damage=" + actualDamage + ", hp=" + after + "/" + maxHealth);
    }

    private static Pokemon findBattlePokemon(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        if (session == null || level == null || pokemonUUID == null) return null;
        Pokemon playerPokemon = findPlayerPokemon(session, level, pokemonUUID);
        return playerPokemon != null ? playerPokemon : findTrainerPokemon(session, level, pokemonUUID);
    }

    private static Pokemon findPlayerPokemon(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        for (UUID playerUUID : session.playerUUIDs()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUUID);
            if (player == null) continue;
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            for (int slot = 0; slot < party.size(); slot++) {
                Pokemon pokemon = party.get(slot);
                if (pokemon != null && pokemonUUID.equals(pokemon.getUuid())) return pokemon;
            }
        }
        return null;
    }

    private static Pokemon findTrainerPokemon(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        if (!(rawTrainer instanceof LivingEntity trainerEntity)) return null;
        TrainerNPC trainer = ActionBattleTrainerResolver.resolve(session.runtimeTrainerId(), trainerEntity);
        if (trainer == null) return null;
        for (Pokemon pokemon : trainer.getTeam()) {
            if (pokemon != null && pokemonUUID.equals(pokemon.getUuid())) return pokemon;
        }
        return null;
    }

    private static void syncHazeBattleZone(ActionBattleSession session, ServerLevel level, long currentTick) {
        long remaining = session.hazeRemainingTicks(currentTick);
        boolean active = session.isHazeActive(currentTick) && remaining > 0L;
        for (UUID playerUUID : session.playerUUIDs()) {
            syncPokemonHaze(session, level, session.playerActivePokemonUUID(playerUUID), session.playerActiveEntityUUID(playerUUID), active, currentTick);
        }
        syncPokemonHaze(session, level, session.trainerActivePokemonUUID(), session.trainerActiveEntityUUID(), active, currentTick);
    }

    private static void syncPokemonHaze(ActionBattleSession session, ServerLevel level, UUID pokemonUUID, UUID entityUUID, boolean hazeActive, long currentTick) {
        if (pokemonUUID == null) return;
        Entity raw = entityUUID != null ? level.getEntity(entityUUID) : null;
        boolean inside = isInsideHazeZone(session, raw, hazeActive);
        ActionBattleEffectController.global().setHazeProtected(session.battleId(), pokemonUUID, inside, currentTick);
        if (inside && DungeonSession.isActive()) {
            ActionBattleDragonRuntime.suppressStatsByHaze(session, pokemonUUID, currentTick);
            ActionBattleGhostRuntime.global().clearStatCursesForHaze(session.battleId(), pokemonUUID, currentTick);
        }
    }

    private static boolean isInsideHazeZone(ActionBattleSession session, Entity entity, boolean hazeActive) {
        return hazeActive && entity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()
                && session.containsArena(pokemonEntity.getX(), pokemonEntity.getZ());
    }

    private static void observeDamageFeedback(ActionBattleSession session, ActionBattlePokemonRefs refs) {
        if (session == null || refs == null) return;
        ActionBattleDamageFeedbackController feedback = ActionBattleDamageFeedbackController.global();
        for (Pokemon pokemon : refs.allPlayerPokemon()) {
            if (pokemon != null) feedback.observePokemon(session.battleId(), pokemon.getUuid(), pokemon.getCurrentHealth());
        }
        if (refs.trainerPokemon() != null) feedback.observePokemon(session.battleId(), refs.trainerPokemon().getUuid(), refs.trainerPokemon().getCurrentHealth());
    }

}

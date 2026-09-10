package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatRules;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleParalysisController;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleParalysisRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime;
import net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleRampageController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugRuntime;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ActionBattleMovementController {
    static final double ACTION_MOVEMENT_SPEED = 0.6D;
    private static final Map<UUID, Boolean> PLAYER_ZONE_STATES = new HashMap<>();

    private ActionBattleMovementController() {}

    static void tickPlayerBattleZone(ActionBattleSession session, ServerPlayer player, ServerLevel level) {
        if (session == null || player == null || level == null) return;
        boolean inside = session.containsArena(player.getX(), player.getZ());
        Boolean previous = PLAYER_ZONE_STATES.put(player.getUUID(), inside);
        if (previous == null || previous.booleanValue() != inside) {
            if (inside) {
                suppressActiveBattlePokemonBrains(session, level);
                DebugLog.log("[CobblemonNML] Player entered battle zone; autonomous battle Pokemon brain movement suppressed. Battle=" + session.battleId());
            } else {
                for (UUID playerUUID : session.playerUUIDs()) {
                    clearBattlePokemonPathCooldown(session.playerActiveEntityUUID(playerUUID), level);
                }
                clearBattlePokemonPathCooldown(session.trainerActiveEntityUUID(), level);
                DebugLog.log("[CobblemonNML] Player left battle zone; normal Cobblemon Pokemon brain movement restored. Battle=" + session.battleId());
            }
        }
    }

    static boolean shouldSuppressAutonomousMovement(PokemonEntity pokemonEntity) {
        if (pokemonEntity == null || pokemonEntity.isRemoved() || !(pokemonEntity.level() instanceof ServerLevel level)) return false;
        if (ActionBattleDragonRuntime.isRoarStunned(pokemonEntity.getPokemon().getUuid())) return true;
        ActionBattleSession session = ActionBattleRegistry.findByPokemonEntity(pokemonEntity.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        return session.arena() != null ? !session.arena().isEmpty() : legacyPlayerInside(session, level);
    }

    static void suppressAutonomousMovementNow(ActionBattleSession session, PokemonEntity pokemonEntity) {
        if (session == null || pokemonEntity == null || pokemonEntity.isRemoved()) return;
        pokemonEntity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        pokemonEntity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        if (ActionBattleDragonRuntime.isRoarStunned(pokemonEntity.getPokemon().getUuid())) {
            pokemonEntity.getNavigation().stop();
            return;
        }
        if (hasExplicitMovementIntent(session, pokemonEntity.getUUID())) return;
        pokemonEntity.getNavigation().stop();
    }

    static void stopActivePlayerNavigation(ActionBattleSession session, ServerLevel level) {
        stopNavigation(session != null ? session.playerActiveEntityUUID() : null, level);
    }

    static void stopActivePlayerNavigation(ActionBattleSession session, UUID playerUUID, ServerLevel level) {
        stopNavigation(session != null ? session.playerActiveEntityUUID(playerUUID) : null, level);
    }

    static void stopActiveTrainerNavigation(ActionBattleSession session, ServerLevel level) {
        stopNavigation(session != null ? session.trainerActiveEntityUUID() : null, level);
    }

    static void pursuePlayerPendingMove(ActionBattleSession session, PokemonEntity pokemonEntity, PokemonEntity targetEntity) {
        pursuePlayerPendingMove(session, session != null ? session.playerUUID() : null, pokemonEntity, targetEntity);
    }

    static void pursuePlayerPendingMove(ActionBattleSession session, UUID playerUUID,
                                         PokemonEntity pokemonEntity, PokemonEntity targetEntity) {
        long currentTick = pokemonEntity.level().getGameTime();
        if (ActionBattleMovementActionRules.isMovementBlocked(
                session, pokemonEntity.getPokemon().getUuid(), currentTick)) {
            pokemonEntity.getNavigation().stop();
            session.clearPlayerMoveCommand(playerUUID);
            return;
        }
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        Pokemon ownerPokemon = refs != null ? refs.playerPokemon(playerUUID) : null;
        var move = ownerPokemon != null && session.playerMoveSlot(playerUUID) >= 0
                ? ownerPokemon.getMoveSet().get(session.playerMoveSlot(playerUUID)) : null;
        boolean enemyTargeted = move != null && !FightOrFlightAdapter.isSelfOrAllyTargetCategory(
                FightOrFlightAdapter.moveTargetCategory(move));
        if (enemyTargeted && !ActionBattleDarkRuntime.canPerceive(
                session, pokemonEntity, targetEntity, currentTick)) {
            pokemonEntity.getNavigation().stop();
            pokemonEntity.setTarget(null);
            session.clearPlayerMoveCommand(playerUUID);
            return;
        }
        int momentum = ActionBattleFlyingRuntime.momentum(session, pokemonEntity.getPokemon().getUuid());
        if (move != null && FightOrFlightAdapter.commitMode(
                pokemonEntity, targetEntity, move, momentum)
                != net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattlePropulsionRules.CommitMode.REPOSITION) {
            pokemonEntity.getNavigation().stop();
            return;
        }
        ActionBattleVisualTrackingRules.faceTarget(pokemonEntity, targetEntity);
        Vec3 tracked = rememberedTarget(session, pokemonEntity, targetEntity)
                .orElseGet(() -> ActionBattleEvasionController.trackedPosition(targetEntity, currentTick));
        Path path = reachablePath(pokemonEntity, tracked);
        if (path == null) path = reachableRepositionPath(pokemonEntity, tracked,
                move != null && FightOrFlightAdapter.isRangedMove(move));
        if (path == null) {
            pokemonEntity.getNavigation().stop();
            return;
        }
        pokemonEntity.getNavigation().moveTo(path,
                movementSpeed(session, pokemonEntity.getPokemon().getUuid(), currentTick));
    }

    private static java.util.Optional<Vec3> rememberedTarget(ActionBattleSession session,
                                                              PokemonEntity attacker,
                                                              PokemonEntity target) {
        if (session == null || attacker == null || target == null) return java.util.Optional.empty();
        ActionBattleTargetTracker tracker = ActionBattleTargetTracker.global();
        UUID ownerId = attacker.getPokemon().getUuid();
        UUID targetId = target.getPokemon().getUuid();
        tracker.clearRememberedIfReached(session.battleId(), ownerId, targetId,
                new ActionBattleTargetingRules.Point(attacker.getX(), attacker.getY(), attacker.getZ()),
                Math.max(0.75D, attacker.getBbWidth() * 0.75D));
        return tracker.lastVisible(session.battleId(), ownerId, targetId)
                .map(point -> new Vec3(point.x(), point.y(), point.z()));
    }

    private static Path reachablePath(PokemonEntity pokemon, Vec3 target) {
        if (pokemon == null || target == null) return null;
        Path path = pokemon.getNavigation().createPath(BlockPos.containing(target), 0);
        return path != null && path.canReach() ? path : null;
    }

    private static Path reachableRepositionPath(PokemonEntity pokemon, Vec3 target, boolean ranged) {
        if (pokemon == null || target == null) return null;
        for (ActionBattleTrainerTactics.Point candidate : ActionBattleTrainerTactics.repositionCandidates(
                pokemon.getX(), pokemon.getZ(), target.x, target.z, ranged)) {
            Path path = reachablePath(pokemon, new Vec3(candidate.x(), target.y, candidate.z()));
            if (path != null) return path;
        }
        return null;
    }


    static double movementSpeed(ActionBattleSession session, UUID pokemonUUID, long currentTick) {
        if (session == null || pokemonUUID == null || currentTick < 0L) return ACTION_MOVEMENT_SPEED;
        int stage = ActionBattleStatResolver.effectiveStage(session.battleId(), pokemonUUID, ActionBattleStat.SPEED, currentTick);
        double grassMultiplier = ActionBattleTypeEffectController.global().grassMovementMultiplier(
                session.dungeonSessionId(), pokemonUUID, currentTick);
        double groundMultiplier = ActionBattleTypeEffectController.global().groundMovementMultiplier(
                session.dungeonSessionId(), pokemonUUID, currentTick);
        double exhaustedMultiplier = ActionBattleRampageController.global().movementMultiplier(
                session.battleId(), pokemonUUID, currentTick);
        double bugMultiplier = ActionBattleBugRuntime.locomotionMultiplier(session, pokemonUUID, currentTick);
        double paralysisMultiplier = ActionBattleParalysisRules.movementMultiplier(
                ActionBattleParalysisController.active(session, pokemonUUID, currentTick));
        return ActionBattleMovementActionRules.composeMovementSpeed(ACTION_MOVEMENT_SPEED,
                ActionBattleStatRules.standardMultiplier(stage), grassMultiplier,
                groundMultiplier * exhaustedMultiplier * bugMultiplier, paralysisMultiplier);
    }

    static void removeBattle(ActionBattleSession session) {
        if (session == null) return;
        for (UUID playerUUID : session.playerUUIDs()) PLAYER_ZONE_STATES.remove(playerUUID);
    }

    static void clearAll() {
        PLAYER_ZONE_STATES.clear();
    }

    private static void suppressActiveBattlePokemonBrains(ActionBattleSession session, ServerLevel level) {
        suppressActiveBattlePokemonBrain(session, level, session.playerActiveEntityUUID());
        for (UUID playerUUID : session.playerUUIDs()) {
            if (!playerUUID.equals(session.playerUUID())) suppressActiveBattlePokemonBrain(session, level, session.playerActiveEntityUUID(playerUUID));
        }
        suppressActiveBattlePokemonBrain(session, level, session.trainerActiveEntityUUID());
    }

    private static void suppressActiveBattlePokemonBrain(ActionBattleSession session, ServerLevel level, UUID entityUUID) {
        Entity rawEntity = entityUUID != null && level != null ? level.getEntity(entityUUID) : null;
        if (rawEntity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) suppressAutonomousMovementNow(session, pokemonEntity);
    }

    private static void clearBattlePokemonPathCooldown(UUID entityUUID, ServerLevel level) {
        Entity rawEntity = entityUUID != null && level != null ? level.getEntity(entityUUID) : null;
        if (rawEntity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) pokemonEntity.getBrain().eraseMemory(CobblemonMemories.PATH_COOLDOWN);
    }

    private static boolean hasExplicitMovementIntent(ActionBattleSession session, UUID entityUUID) {
        if (entityUUID == null || session == null) return false;
        for (UUID playerUUID : session.playerUUIDs()) {
            if (entityUUID.equals(session.playerActiveEntityUUID(playerUUID))) {
                return session.hasPokemonMovementIntent(session.playerActivePokemonUUID(playerUUID));
            }
        }
        if (entityUUID.equals(session.trainerActiveEntityUUID())) {
            return session.hasPokemonMovementIntent(session.trainerActivePokemonUUID());
        }
        return false;
    }

    private static void stopNavigation(UUID entityUUID, ServerLevel level) {
        Entity raw = entityUUID != null && level != null ? level.getEntity(entityUUID) : null;
        if (raw instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) pokemonEntity.getNavigation().stop();
    }

    private static PokemonEntity pokemonEntity(ServerLevel level, UUID entityUUID) {
        Entity raw = entityUUID != null ? level.getEntity(entityUUID) : null;
        return raw instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved() ? pokemonEntity : null;
    }

    private static boolean legacyPlayerInside(ActionBattleSession session, ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerUUID());
        return player != null && player.level() == level && session.containsArena(player.getX(), player.getZ());
    }
}

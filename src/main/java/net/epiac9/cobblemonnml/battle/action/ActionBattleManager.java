package net.epiac9.cobblemonnml.battle.action;

import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.compat.ActionBattleMoveEffectResolver;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleConfusionRules;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleBalefulBunkerHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleHailHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveTimingRules;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentType;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundVisualSync;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleRampageController;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleParalysisController;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleParalysisRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleAerialMoveRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattlePropulsionRules;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.events.trainer.DungeonTrainerBattleResultHandler;
import net.epiac9.cobblemonnml.events.trainer.DungeonTrainerTracker;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ActionBattleManager {

    private ActionBattleManager() {}

    public static ActionBattleSession startBattle(ServerPlayer player, LivingEntity trainer, String runtimeTrainerId, ResourceLocation trainerPreset) {
        if (player == null || trainer == null || runtimeTrainerId == null || runtimeTrainerId.isBlank()) return null;
        UUID dungeonSessionId = DungeonSession.getSessionId();
        if (!DungeonSession.isActive() || dungeonSessionId == null) {
            DebugLog.log("[CobblemonNML] Cannot start action battle without an active dungeon session.");
            return null;
        }
        if (ActionBattleRegistry.byPlayer(player.getUUID()) != null) {
            DebugLog.log("[CobblemonNML] Player already has an active action battle: " + player.getUUID());
            return null;
        }
        if (ActionBattleRegistry.byTrainer(trainer.getUUID()) != null) {
            DebugLog.log("[CobblemonNML] Trainer already has an active action battle: " + trainer.getUUID());
            return null;
        }
        TrainerNPC runtimeTrainer = ActionBattleTrainerResolver.resolve(runtimeTrainerId, trainer);
        if (runtimeTrainer == null) return null;
        ActionBattlePokemonSelection.Selection playerLead = ActionBattlePokemonSelection.firstUsable(player);
        ActionBattlePokemonSelection.Selection trainerLead = ActionBattlePokemonSelection.firstUsable(runtimeTrainer);
        if (playerLead == null) {
            DebugLog.log("[CobblemonNML] Cannot start action battle: player has no usable Pokemon.");
            return null;
        }
        if (trainerLead == null) {
            DebugLog.log("[CobblemonNML] Cannot start action battle: trainer has no usable Pokemon.");
            return null;
        }
        ActionBattleRoomBounds roomBounds = DungeonTrainerTracker.getRoomBounds(trainer.getUUID());
        String roomId = DungeonTrainerTracker.getRoomId(trainer.getUUID());
        if (roomBounds == null) {
            DebugLog.log("[CobblemonNML] Cannot start action battle: trainer has no recorded room bounds.");
            return null;
        }
        UUID battleId = UUID.randomUUID();
        ActionBattleSession session = new ActionBattleSession(
                battleId, dungeonSessionId, player.getUUID(), trainer.getUUID(), runtimeTrainerId,
                trainerPreset != null ? trainerPreset.toString() : null,
                roomId, roomBounds, roomBounds.contains(player.getX(), player.getZ())
        );
        ActionBattleRegistry.register(session, new ActionBattlePokemonRefs(player.getUUID(), playerLead.pokemon(), trainerLead.pokemon()));
        ActionBattlePokemonRuntime.seedDamageFeedback(session, playerLead.pokemon());
        ActionBattlePokemonRuntime.seedDamageFeedback(session, trainerLead.pokemon());
        DebugLog.log("[CobblemonNML] Action battle session started. Battle=" + session.battleId() + ", player=" + session.playerUUID() + ", trainer=" + session.trainerUUID() + ", runtimeTrainer=" + session.runtimeTrainerId() + ", preset=" + session.trainerPreset());
        try {
            if (session.arena().isInside(player.getUUID())) {
                session.setPlayerSendOutPending(player.getUUID(), true);
                session.setTrainerSendOutPending(true);
                ActionBattlePokemonRuntime.sendOut(session, true, player, trainer, playerLead);
                ActionBattlePokemonRuntime.sendOut(session, false, trainer, player, trainerLead);
                syncHud(player, session);
            }
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] Failed to begin action battle Pokemon send-out: " + exception.getMessage(), exception);
            invalidateBattle(player.getUUID());
            return null;
        }
        return session;
    }

    public static ActionBattleSession getByPlayer(UUID playerUUID) {
        return playerUUID != null ? ActionBattleRegistry.byPlayer(playerUUID) : null;
    }

    public static ActionBattleSession getByTrainer(UUID trainerUUID) {
        return trainerUUID != null ? ActionBattleRegistry.byTrainer(trainerUUID) : null;
    }

    public static boolean hasBattleForPlayer(UUID playerUUID) {
        return getByPlayer(playerUUID) != null;
    }

    public static boolean endBattle(ServerPlayer player, ActionBattleResult result) {
        if (player == null || result == null) return false;
        ActionBattleSession session = ActionBattleRegistry.byPlayer(player.getUUID());
        if (session == null || !session.end(result)) return false;
        hideHudForAllPlayers(session, player.getServer());
        cleanupBattlePokemon(session);
        removeSession(session);
        routeResult(player, session, result);
        DebugLog.log("[CobblemonNML] Action battle session ended. Battle=" + session.battleId() + ", result=" + result);
        return true;
    }

    public static void invalidateBattle(UUID playerUUID) {
        if (playerUUID == null) return;
        ActionBattleSession session = ActionBattleRegistry.byPlayer(playerUUID);
        if (session == null || !session.end(ActionBattleResult.INVALID)) return;
        ServerPlayer player = ActionBattlePokemonRuntime.findServerPlayer(session);
        hideHudForAllPlayers(session, player != null ? player.getServer() : null);
        cleanupBattlePokemon(session);
        removeSession(session);
        DebugLog.log("[CobblemonNML] Action battle session invalidated. Battle=" + session.battleId());
    }

    public static void clearAll() {
        for (ActionBattleSession session : ActionBattleRegistry.sessionsSnapshot()) {
            session.end(ActionBattleResult.INVALID);
            cleanupBattlePokemon(session);
        }
        ActionBattleRegistry.clear();
        ActionBattleMovementController.clearAll();
        ActionBattleEffectRuntime.clearAll();
    }

    public static int size() {
        return ActionBattleRegistry.size();
    }

    public static void clearEffectStateForDungeonSession(UUID dungeonSessionId) {
        if (dungeonSessionId == null) return;
        for (ActionBattleSession session : ActionBattleRegistry.sessionsSnapshot()) {
            if (dungeonSessionId.equals(session.dungeonSessionId())) {
                ActionBattleEffectRuntime.clearBattle(session.battleId());
            }
        }
    }

    public static boolean requestPlayerMoveHere(ServerPlayer player, double x, double y, double z) {
        if (player == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        ActionBattleSession session = ActionBattleRegistry.byPlayer(player.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        if (!DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return false;
        if (!player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        UUID ownerUUID = player.getUUID();
        UUID activePokemonId = session.playerActivePokemonUUID(ownerUUID);
        UUID activeEntityId = session.playerActiveEntityUUID(ownerUUID);
        if (activePokemonId == null || activeEntityId == null) return false;
        long currentTick = level.getGameTime();
        if (net.epiac9.cobblemonnml.battle.action.interrupt.ActionBattleInterruptController
                .isActive(session.battleId(), activePokemonId, currentTick)) return false;
        if (net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleInfatuationController
                .blocksCommands(session.battleId(), activePokemonId, currentTick)) return false;
        if (net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime
                .blocksTrainerCommands(session, activePokemonId, currentTick)) return false;
        if (ActionBattleMovementActionRules.isMovementBlocked(session, activePokemonId, currentTick)) {
            DebugLog.log("[CobblemonNML] Move Here rejected. Battle=" + session.battleId() + ", reason=immobilized");
            return false;
        }
        if (!ActionBattleSleepController.canIssueCommand(session, activePokemonId, currentTick,
                ActionBattleSleepController.CommandKind.MOVE_HERE)) {
            DebugLog.log("[CobblemonNML] Move Here rejected. Battle=" + session.battleId() + ", reason=sleep");
            return false;
        }
        ActionBattleCommandController.onMovementCommandIssued(session, activePokemonId);
        if (session.isPokemonMovementCommandOnCooldown(activePokemonId, currentTick)) {
            DebugLog.log("[CobblemonNML] Move Here rejected. Battle=" + session.battleId() + ", reason=move_here_cooldown");
            return false;
        }
        Entity rawEntity = level.getEntity(activeEntityId);
        if (!(rawEntity instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) return false;
        ActionBattleConfusionController.CommandPlan confusionPlan = ActionBattleConfusionController.roll(session, pokemonEntity, ActionBattleConfusionRules.CommandKind.MOVE_HERE, currentTick);
        if (confusionPlan.corrupted()) {
            Vec3 randomTarget = ActionBattleConfusionController.randomMoveTarget(pokemonEntity);
            x = randomTarget.x; y = randomTarget.y; z = randomTarget.z;
        }
        pokemonEntity.getNavigation().stop();
        session.clearPlayerMoveCommand(ownerUUID);
        BlockPos targetPos = BlockPos.containing(x, y, z);
        if (targetPos.getY() < level.getMinBuildHeight() || targetPos.getY() >= level.getMaxBuildHeight()) return false;
        if (!level.getChunkSource().hasChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4) || !level.getWorldBorder().isWithinBounds(targetPos)) return false;
        if (!session.containsArena(x, z)) return false;
        Vec3 destination = new Vec3(x, y, z);
        ActionBattleAerialMoveRules.Mode movementMode = ActionBattleAerialMoveRules.classify(
                ActionBattleFlyingRules.isFlyingPokemon(pokemonEntity.getPokemon()),
                ActionBattleAerialMoveController.destinationSupported(level, pokemonEntity, destination));
        boolean started;
        if (movementMode == ActionBattleAerialMoveRules.Mode.AERIAL) {
            started = ActionBattleAerialMoveController.tryStart(
                    session, ownerUUID, pokemonEntity, destination);
        } else if (movementMode == ActionBattleAerialMoveRules.Mode.GROUND) {
            Path path = pokemonEntity.getNavigation().createPath(x, y, z, 0);
            if (path == null || !path.canReach()) {
                DebugLog.log("[CobblemonNML] Move Here rejected as unreachable. Battle="
                        + session.battleId() + ", target=" + destination);
                return false;
            }
            started = pokemonEntity.getNavigation().moveTo(path,
                    ActionBattleMovementController.movementSpeed(session, activePokemonId, currentTick));
            if (started) ActionBattleAerialMoveController.clearPokemon(session, activePokemonId);
        } else {
            DebugLog.log("[CobblemonNML] Move Here rejected. Battle=" + session.battleId()
                    + ", reason=air_target_requires_flying_type, target=" + destination);
            return false;
        }
        if (ActionBattlePersistentController.global().has(
                session.battleId(), activePokemonId, ActionBattlePersistentType.BOUND, currentTick)) {
            DebugLog.log("[CobblemonNML] Move Here rejected. Battle=" + session.battleId() + ", reason=bound");
            return false;
        }
        if (!started) return false;
        session.startPokemonMovementCommandCooldown(activePokemonId, currentTick, ActionBattleTiming.MOVE_HERE_COOLDOWN_TICKS);
        long revision = session.replacePlayerMoveTarget(ownerUUID, x, y, z);
        DebugLog.log("[CobblemonNML] Move Here accepted. Battle=" + session.battleId() + ", revision=" + revision + ", target=" + new Vec3(x, y, z) + ", cooldownTicks=" + ActionBattleTiming.MOVE_HERE_COOLDOWN_TICKS);
        return true;
    }

    public static boolean requestPlayerMove(ServerPlayer player, int moveSlot) {
        if (player == null || moveSlot < 0 || moveSlot > 3) return false;
        ActionBattleSession session = ActionBattleRegistry.byPlayer(player.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        if (!DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return rejectMove(session, moveSlot, "inactive_dungeon_session");
        if (!(player.level() instanceof ServerLevel level) || !player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return rejectMove(session, moveSlot, "wrong_dimension");
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        UUID ownerUUID = player.getUUID();
        Pokemon playerPokemon = refs != null ? refs.playerPokemon(ownerUUID) : null;
        if (playerPokemon == null) return rejectMove(session, moveSlot, "missing_player_pokemon_ref");
        Move move = playerPokemon.getMoveSet().get(moveSlot);
        if (move == null) return rejectMove(session, moveSlot, "missing_move");
        if (!FightOrFlightAdapter.supports(move)) return rejectMove(session, moveSlot, "unsupported_move");
        if (!FightOrFlightAdapter.hasPp(move)) return rejectMove(session, moveSlot, "no_pp");
        long currentTick = level.getGameTime();
        if (net.epiac9.cobblemonnml.battle.action.interrupt.ActionBattleInterruptController
                .isActive(session.battleId(), playerPokemon.getUuid(), currentTick)) {
            return rejectMove(session, moveSlot, "interrupt");
        }
        if (net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleInfatuationController
                .blocksCommands(session.battleId(), playerPokemon.getUuid(), currentTick)) {
            return rejectMove(session, moveSlot, "infatuation");
        }
        if (net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime
                .blocksTrainerCommands(session, playerPokemon.getUuid(), currentTick)) {
            return rejectMove(session, moveSlot, "uproar_control");
        }
        if (!ActionBattleMovementActionRules.canUseAction(
                ActionBattleMovementActionRules.isMovementBlocked(session, playerPokemon.getUuid(), currentTick),
                ActionBattleMovementActionRules.requiresMovement(move))) {
            return rejectMove(session, moveSlot, "immobilized");
        }
        if (!ActionBattleSleepController.canIssueCommand(session, playerPokemon.getUuid(), currentTick,
                ActionBattleSleepController.CommandKind.MOVE)) return rejectMove(session, moveSlot, "sleep");
        if (!ActionBattleControlController.global().canUseMove(session.battleId(), playerPokemon.getUuid(), move, currentTick)) return rejectMove(session, moveSlot, "control_effect");
        if (!ActionBattleRampageController.global().canUseAbility(session.battleId(), playerPokemon.getUuid(), move.getName(), currentTick)) return rejectMove(session, moveSlot, "rampage_lock");
        ActionBattleCommandController.onCommandIssued(session, playerPokemon.getUuid());
        if (session.isPokemonAbilitySlotOnCooldown(playerPokemon.getUuid(), moveSlot, currentTick)) return rejectMove(session, moveSlot, "cooldown");
        UUID playerEntityId = session.playerActiveEntityUUID(ownerUUID);
        if (playerEntityId == null) return rejectMove(session, moveSlot, "missing_player_entity_id");
        Entity rawPlayerPokemon = level.getEntity(playerEntityId);
        if (!(rawPlayerPokemon instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) return rejectMove(session, moveSlot, "missing_player_entity");
        if (handleConfusedPlayerMove(session, level, refs, move, moveSlot, pokemonEntity, currentTick)) {
            return true;
        }
        if (ActionBattleBalefulBunkerHandler.isBalefulBunker(move)) {
            clearPlayerMoveAttempt(session, ownerUUID, pokemonEntity);
            ActionBattleBalefulBunkerHandler.StartResult result = ActionBattleBalefulBunkerHandler.tryStart(session, pokemonEntity, move);
            if (result == ActionBattleBalefulBunkerHandler.StartResult.STARTED) {
                ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), playerPokemon.getUuid(), move);
                dispatchOwnedMechanics(pokemonEntity, pokemonEntity, move);
            }
            DebugLog.log("[CobblemonNML] Baleful Bunker ACTION start result. Battle=" + session.battleId() + ", result=" + result);
            return result == ActionBattleBalefulBunkerHandler.StartResult.STARTED;
        }
        UUID targetEntityId = session.trainerActiveEntityUUID();
        if (targetEntityId == null) return rejectMove(session, moveSlot, "missing_target_entity_id");
        Entity rawTargetPokemon = level.getEntity(targetEntityId);
        if (!(rawTargetPokemon instanceof PokemonEntity targetEntity) || targetEntity.isRemoved()) return rejectMove(session, moveSlot, "missing_target_entity");
        if (!FightOrFlightAdapter.isSelfOrAllyTargetCategory(FightOrFlightAdapter.moveTargetCategory(move))
                && !net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime
                .canPerceive(session, pokemonEntity, targetEntity, currentTick)) {
            pokemonEntity.setTarget(null);
            return rejectMove(session, moveSlot, "target_outside_awareness");
        }
        clearPlayerMoveAttempt(session, ownerUUID, pokemonEntity);
        long revision = session.replacePlayerMoveCommand(ownerUUID, moveSlot, targetEntityId);
        ActionBattleMovementController.pursuePlayerPendingMove(session, ownerUUID, pokemonEntity, targetEntity);
        DebugLog.log("[CobblemonNML] Move " + (moveSlot + 1) + " queued. Battle=" + session.battleId() + ", revision=" + revision + ", target=" + targetEntityId);
        return true;
    }

    private static boolean handleConfusedPlayerMove(ActionBattleSession session, ServerLevel level, ActionBattlePokemonRefs refs, Move move,
                                                  int moveSlot, PokemonEntity pokemonEntity, long currentTick) {
        if (move == null || pokemonEntity == null) return false;
        ActionBattleConfusionRules.CommandKind confusionKind = ActionBattleConfusionRules.commandKindFor(move);
        ActionBattleConfusionController.CommandPlan confusionPlan = ActionBattleConfusionController.roll(session, pokemonEntity, confusionKind, currentTick);
        if (!confusionPlan.corrupted()) return false;

        clearPlayerMoveAttempt(session, session.playerOwnerForPokemon(pokemonEntity.getPokemon().getUuid()), pokemonEntity);
        if (confusionKind == ActionBattleConfusionRules.CommandKind.PROTECT) {
            if (!FightOrFlightAdapter.consumeOnePp(pokemonEntity, move)) return rejectMove(session, moveSlot, "no_pp");
            ActionBattleGhostRuntime.global().applyAbilityCooldown(session,
                    pokemonEntity, moveSlot, currentTick);
            ActionBattleProtectController.global().recordFailedProtectAttempt(session.battleId(),
                    pokemonEntity.getPokemon().getUuid(), pokemonEntity.getRandom().nextBoolean() ? 2 : 1);
            DebugLog.log("[CobblemonNML] Confusion caused move to fail without effect. Battle="
                    + session.battleId() + ", move=" + move.getName());
            return true;
        }
        if (confusionKind == ActionBattleConfusionRules.CommandKind.SUPPORT) {
            if (!FightOrFlightAdapter.consumeOnePp(pokemonEntity, move)) return rejectMove(session, moveSlot, "no_pp");
            ActionBattleGhostRuntime.global().applyAbilityCooldown(session, pokemonEntity, moveSlot, currentTick);
            PokemonEntity enemy = refs.trainerPokemon() != null ? refs.trainerPokemon().getEntity() : null;
            if (ActionBattleMoveEffectResolver.applyCorruptedSupport(
                    pokemonEntity, enemy, move, confusionPlan.supportCorruption())) {
                ActionBattleControlController.global().recordSuccessfulMove(
                        session.battleId(), pokemonEntity.getPokemon().getUuid(), move);
                dispatchOwnedMechanics(pokemonEntity, enemy, move);
            }
            return true;
        }
        if (confusionKind == ActionBattleConfusionRules.CommandKind.RANGED) {
            if (!FightOrFlightAdapter.consumeOnePp(pokemonEntity, move)) return rejectMove(session, moveSlot, "no_pp");
            ActionBattleGhostRuntime.global().applyAbilityCooldown(session,
                    pokemonEntity, moveSlot, currentTick);
            ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), pokemonEntity.getPokemon().getUuid(), move);
            PokemonEntity confusedTarget = refs.trainerPokemon() != null ? refs.trainerPokemon().getEntity() : null;
            Vec3 redirectedDirection = ActionBattleConfusionController.corruptedShotDirection(
                    pokemonEntity, confusedTarget, confusionPlan.rangedCorruption());
            ActionBattleCommittedMove committedMove = captureCommittedMove(session, pokemonEntity, move);
            FightOrFlightAdapter.executeConfusedRanged(pokemonEntity, move, redirectedDirection,
                    1.0D, committedMove);
            net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime.onSuccessfulSelfMoveCommit(pokemonEntity, move);
            DebugLog.log("[CobblemonNML] Confusion fired ranged move in random direction. Battle=" + session.battleId() + ", move=" + move.getName());
            return true;
        }
        if (confusionKind == ActionBattleConfusionRules.CommandKind.MELEE) {
            if (!FightOrFlightAdapter.consumeOnePp(pokemonEntity, move)) return rejectMove(session, moveSlot, "no_pp");
            ActionBattleGhostRuntime.global().applyAbilityCooldown(session,
                    pokemonEntity, moveSlot, currentTick);
            ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), pokemonEntity.getPokemon().getUuid(), move);
            ActionBattleCommittedMove committedMove = captureCommittedMove(session, pokemonEntity, move);
            ActionBattleConfusionController.startMeleeDash(session, level, pokemonEntity, move, currentTick,
                    1.0D, committedMove);
            dispatchOwnedMechanics(pokemonEntity, null, move);
            net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime.onSuccessfulSelfMoveCommit(pokemonEntity, move);
            DebugLog.log("[CobblemonNML] Confusion started uncontrolled melee dash. Battle=" + session.battleId() + ", move=" + move.getName());
            return true;
        }
        if (confusionKind == ActionBattleConfusionRules.CommandKind.CHANNEL) {
            if (ActionBattleHailHandler.isHail(move)) {
                var result = ActionBattleHailHandler.tryStart(session, level, pokemonEntity, null, move, confusionPlan.channelBonusTicks(), confusionPlan.channelSelfCancel());
                if (result == ActionBattleHailHandler.StartResult.STARTED) dispatchOwnedMechanics(pokemonEntity, null, move);
                DebugLog.log("[CobblemonNML] Confused Hail ACTION start result. Battle=" + session.battleId() + ", result=" + result);
                return result == ActionBattleHailHandler.StartResult.STARTED;
            }
            var result = ActionBattleToxicSpikesHandler.tryStart(session, level, pokemonEntity, null, move, confusionPlan.channelBonusTicks(), confusionPlan.channelSelfCancel());
            if (result == ActionBattleToxicSpikesHandler.StartResult.STARTED) {
                ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), pokemonEntity.getPokemon().getUuid(), move);
                dispatchOwnedMechanics(pokemonEntity, null, move);
            }
            DebugLog.log("[CobblemonNML] Confused Toxic Spikes ACTION start result. Battle=" + session.battleId() + ", result=" + result);
            return result == ActionBattleToxicSpikesHandler.StartResult.STARTED;
        }
        return false;
    }

    private static void clearPlayerMoveAttempt(ActionBattleSession session, PokemonEntity pokemonEntity) {
        clearPlayerMoveAttempt(session, session != null ? session.playerUUID() : null, pokemonEntity);
    }

    private static void clearPlayerMoveAttempt(ActionBattleSession session, UUID ownerUUID, PokemonEntity pokemonEntity) {
        if (pokemonEntity != null) pokemonEntity.getNavigation().stop();
        ActionBattleAerialMoveController.holdForAbility(session, pokemonEntity);
        if (session != null) session.clearPlayerMoveTarget(ownerUUID);
    }

    private static boolean rejectMove(ActionBattleSession session, int moveSlot, String reason) {
        DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId() + ", slot=" + (moveSlot + 1) + ", reason=" + reason);
        return false;
    }

    public static boolean requestPlayerSwap(ServerPlayer player) {
        return requestPlayerSwap(player, false);
    }

    public static boolean forcePlayerSwitch(ServerPlayer player) {
        return requestPlayerSwap(player, true);
    }

    public static boolean forceTrainerSwitch(ActionBattleSession session, ServerLevel level) {
        if (session == null || level == null || session.state() != ActionBattleState.ACTIVE
                || session.isTrainerSendOutPending()) return false;
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        Pokemon current = refs != null ? refs.trainerPokemon() : null;
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        ServerPlayer opponent = level.getServer().getPlayerList().getPlayer(session.playerUUID());
        if (current == null || !(rawTrainer instanceof LivingEntity trainer) || opponent == null) return false;
        TrainerNPC runtimeTrainer = ActionBattleTrainerResolver.resolve(session.runtimeTrainerId(), trainer);
        ActionBattlePokemonSelection.Selection next = ActionBattlePokemonSelection.nextUsable(
                runtimeTrainer, session.trainerActivePartyIndex());
        if (next == null) return false;
        long currentTick = level.getGameTime();
        session.setTrainerSendOutPending(true);
        ActionBattleSwapTransitionGuard.begin(session.battleId(), ActionBattleCommandController.Side.TRAINER,
                current.getUuid(), next.pokemon().getUuid());
        ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER,
                ActionBattleCommandController.InterruptReason.SWAP);
        ActionBattleEffectRuntime.onPokemonUnavailable(session, current.getUuid(), false, currentTick);
        ActionBattlePokemonRuntime.recall(current);
        session.clearTrainerActivePokemon();
        refs.setTrainerPokemon(next.pokemon());
        ActionBattlePokemonRuntime.seedDamageFeedback(session, next.pokemon());
        ActionBattlePokemonRuntime.sendOut(session, false, trainer, opponent, next);
        session.startTrainerSwapCooldown(currentTick,
                net.epiac9.cobblemonnml.battle.action.effect.utility.ActionBattleForcedSwitchRules.SWAP_COOLDOWN_TICKS);
        return true;
    }

    private static boolean requestPlayerSwap(ServerPlayer player, boolean forced) {
        if (player == null) return false;
        ActionBattleSession session = ActionBattleRegistry.byPlayer(player.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        if (!DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return rejectSwap(session, "inactive_dungeon_session");
        if (!(player.level() instanceof ServerLevel level) || !player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return rejectSwap(session, "wrong_dimension");
        long currentTick = level.getGameTime();
        UUID ownerUUID = player.getUUID();
        UUID activePokemonUUID = session.playerActivePokemonUUID(ownerUUID);
        if (!forced && activePokemonUUID != null && net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleInfatuationController
                .blocksCommands(session.battleId(), activePokemonUUID, currentTick)) return rejectSwap(session, "infatuation");
        if (!forced && activePokemonUUID != null
                && net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime
                .blocksSwap(activePokemonUUID)) return rejectSwap(session, "roar_stun");
        if (!forced && activePokemonUUID != null && ActionBattleMovementActionRules.isVoluntaryRecallBlocked(
                session, activePokemonUUID, currentTick)) return rejectSwap(session, "grounded");
        if (!forced && activePokemonUUID != null && ActionBattleControlController.global().blocksSwap(session.battleId(), activePokemonUUID, currentTick)) return rejectSwap(session, "trapped");
        if (!forced && activePokemonUUID != null && ActionBattleGhostRuntime.global().blocksVoluntarySwap(
                session.battleId(), activePokemonUUID, currentTick)) return rejectSwap(session, "binding");
        if (!forced && activePokemonUUID != null) ActionBattleCommandController.onCommandIssued(session, activePokemonUUID);
        if (!forced && session.isPlayerSwapOnCooldown(currentTick)) return rejectSwap(session, "cooldown");
        if (session.isPlayerSendOutPending(ownerUUID)) return rejectSwap(session, "sendout_pending");
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        Pokemon currentPlayerPokemon = refs != null ? refs.playerPokemon(ownerUUID) : null;
        if (currentPlayerPokemon == null) return rejectSwap(session, "missing_player_pokemon_ref");
        int previousSlot = session.playerActivePartyIndex(ownerUUID);
        ActionBattlePokemonSelection.Selection next = ActionBattlePokemonSelection.nextUsable(player, previousSlot);
        if (next == null) return rejectSwap(session, "no_available_replacement");
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        if (!(rawTrainer instanceof LivingEntity trainerEntity) || trainerEntity.isRemoved()) return rejectSwap(session, "missing_trainer_entity");
        Entity rawPlayerPokemon = session.playerActiveEntityUUID(ownerUUID) != null ? level.getEntity(session.playerActiveEntityUUID(ownerUUID)) : null;
        if (rawPlayerPokemon instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) {
            pokemonEntity.getNavigation().stop();
        }
        ActionBattleCommandController.cancelPendingOrders(session, activePokemonUUID,
                ActionBattleCommandController.InterruptReason.SWAP);
        session.clearPlayerMoveState(ownerUUID);
        session.startPlayerSwapCooldown(currentTick,
                net.epiac9.cobblemonnml.battle.action.effect.utility.ActionBattleForcedSwitchRules.SWAP_COOLDOWN_TICKS);
        session.setPlayerSendOutPending(ownerUUID, true);
        Pokemon previous = currentPlayerPokemon;
        ActionBattleSwapTransitionGuard.begin(session.battleId(), ActionBattleCommandController.Side.PLAYER,
                previous.getUuid(), next.pokemon().getUuid());
        ActionBattleEffectRuntime.onPokemonUnavailable(session, previous.getUuid(), false, currentTick);
        ActionBattlePokemonRuntime.recall(previous);
        session.clearPlayerActivePokemon(ownerUUID);
        refs.setPlayerPokemon(ownerUUID, next.pokemon());
        ActionBattlePokemonRuntime.seedDamageFeedback(session, next.pokemon());
        ActionBattlePokemonRuntime.sendOut(session, true, ownerUUID, player, trainerEntity, next);
        DebugLog.log("[CobblemonNML] Player Swap Out accepted. Battle=" + session.battleId() + ", fromSlot=" + previousSlot + ", toSlot=" + next.slot() + ", cooldownTicks=" + ActionBattleTiming.SWAP_COOLDOWN_TICKS);
        return true;
    }

    private static boolean rejectSwap(ActionBattleSession session, String reason) {
        DebugLog.log("[CobblemonNML] Player Swap Out rejected. Battle=" + session.battleId() + ", reason=" + reason);
        return false;
    }

    public static void tickPlayerMovement(ServerPlayer player) {
        if (player == null) return;
        ActionBattleSession session = ActionBattleRegistry.byPlayer(player.getUUID());
        if (session == null) session = autoJoinArena(player);
        if (session == null || session.state() != ActionBattleState.ACTIVE) return;
        if (!(player.level() instanceof ServerLevel)) {
            UUID activePokemonUUID = session.playerActivePokemonUUID(player.getUUID());
            if (activePokemonUUID != null) ActionBattleCommandController.cancelPendingOrders(session, activePokemonUUID,
                    ActionBattleCommandController.InterruptReason.TARGET_INVALID);
            return;
        }
        ServerLevel level = player.getServer().getLevel(DungeonDimension.DUNGEON_DIMENSION);
        if (level == null) return;
        ActionBattlePokemonRefs runtimeRefs = ActionBattleRegistry.pokemonRefs(session.battleId());
        if (session.claimBackgroundTick(level.getGameTime())) {
            ActionBattleEffectRuntime.tickBattle(session, level, runtimeRefs);
            ActionBattleConfusionController.tickBattle(session, level);
        }
        if (!updateArenaPresence(player, session, level, runtimeRefs)) return;
        if (handleFaintState(player, session, level)) return;
        ActionBattleMovementController.tickPlayerBattleZone(session, player, level);
        if (level.getGameTime() % ActionBattleTiming.HUD_SYNC_INTERVAL_TICKS == 0L) syncHud(player, session);
        if (session.claimPhysicalTick(level.getGameTime())) {
            ActionBattleTrainerAiController.tick(session, level, ActionBattleRegistry.pokemonRefs(session.battleId()));
            ActionBattlePokemonRefs autonomousRefs = ActionBattleRegistry.pokemonRefs(session.battleId());
            if (autonomousRefs != null) {
                java.util.Set<UUID> activeParticipants = session.arena() != null
                        ? session.arena().insideParticipants() : java.util.Set.of(session.playerUUID());
                for (UUID participantUUID : activeParticipants) {
                    Pokemon participantPokemon = autonomousRefs.playerPokemon(participantUUID);
                    if (participantPokemon != null) net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonAutonomousController
                            .tick(session, level, participantPokemon, autonomousRefs.trainerPokemon());
                }
            }
        }
        if (session.state() != ActionBattleState.ACTIVE) return;
        UUID ownerUUID = player.getUUID();
        UUID activeEntityId = session.playerActiveEntityUUID(ownerUUID);
        Entity rawEntity = activeEntityId != null ? level.getEntity(activeEntityId) : null;
        if (!(rawEntity instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) {
            session.clearPlayerMoveState(ownerUUID);
            return;
        }
        long sleepTick = level.getGameTime();
        if (session.playerActivePokemonUUID(ownerUUID) != null
                && net.epiac9.cobblemonnml.battle.action.interrupt.ActionBattleInterruptController.isActive(
                session.battleId(), session.playerActivePokemonUUID(ownerUUID), sleepTick)) {
            pokemonEntity.getNavigation().stop();
            session.clearPlayerMoveState(ownerUUID);
            return;
        }
        if (session.playerActivePokemonUUID(ownerUUID) != null && !ActionBattleSleepController.canIssueCommand(
                session, session.playerActivePokemonUUID(ownerUUID), sleepTick, ActionBattleSleepController.CommandKind.PENDING_CONTINUATION)) {
            pokemonEntity.getNavigation().stop();
            ActionBattleCommandController.cancelPendingOrders(session, session.playerActivePokemonUUID(ownerUUID), ActionBattleCommandController.InterruptReason.SLEEP);
            session.clearPlayerMoveState(ownerUUID);
            return;
        }
        if (session.hasPlayerMoveCommand(ownerUUID)) {
            Entity rawTarget = level.getEntity(session.playerMoveTargetEntityUUID(ownerUUID));
            if (!(rawTarget instanceof PokemonEntity targetEntity) || targetEntity.isRemoved()) {
                pokemonEntity.getNavigation().stop();
                session.clearPlayerMoveState(ownerUUID);
                DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId() + ", slot=" + (session.playerMoveSlot(ownerUUID) + 1) + ", reason=missing_target_entity");
                return;
            }
            ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
            Pokemon playerPokemon = refs != null ? refs.playerPokemon(ownerUUID) : null;
            Move move = playerPokemon != null ? playerPokemon.getMoveSet().get(session.playerMoveSlot(ownerUUID)) : null;
            if (move == null || !FightOrFlightAdapter.supports(move) || !FightOrFlightAdapter.hasPp(move)) {
                pokemonEntity.getNavigation().stop();
                session.clearPlayerMoveState(ownerUUID);
                String reason = move == null ? "missing_move" : !FightOrFlightAdapter.supports(move) ? "unsupported_move" : "no_pp";
                DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId() + ", slot=" + (session.playerMoveSlot(ownerUUID) + 1) + ", reason=" + reason);
                return;
            }
            if (!ActionBattleMovementActionRules.canUseAction(
                    ActionBattleMovementActionRules.isMovementBlocked(session, playerPokemon.getUuid(), level.getGameTime()),
                    ActionBattleMovementActionRules.requiresMovement(move))) {
                pokemonEntity.getNavigation().stop();
                session.clearPlayerMoveState(ownerUUID);
                DebugLog.log("[CobblemonNML] Queued movement move cancelled. Battle=" + session.battleId() + ", reason=immobilized");
                return;
            }
            long currentTick = level.getGameTime();
            UUID pokemonUUID = playerPokemon.getUuid();
            boolean enemyTargeted = !FightOrFlightAdapter.isSelfOrAllyTargetCategory(
                    FightOrFlightAdapter.moveTargetCategory(move));
            if (enemyTargeted && !net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime
                    .canPerceive(session, pokemonEntity, targetEntity, currentTick)) {
                clearPlayerMoveAttempt(session, ownerUUID, pokemonEntity);
                pokemonEntity.setTarget(null);
                DebugLog.log("[CobblemonNML] Action target lost outside awareness. Battle=" + session.battleId());
                return;
            }
            if (!ActionBattleControlController.global().canUseMove(session.battleId(), pokemonUUID, move, currentTick)) {
                pokemonEntity.getNavigation().stop();
                session.clearPlayerMoveState(ownerUUID);
                DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId() + ", slot=" + (session.playerMoveSlot(ownerUUID) + 1) + ", reason=control_effect");
                return;
            }
            if (!ActionBattleRampageController.global().canUseAbility(session.battleId(), playerPokemon.getUuid(), move.getName(), currentTick)) {
                clearPlayerMoveAttempt(session, ownerUUID, pokemonEntity);
                DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId()
                        + ", slot=" + (session.playerMoveSlot(ownerUUID) + 1) + ", reason=rampage_lock");
                return;
            }
            if (session.isPokemonAbilitySlotOnCooldown(pokemonUUID, session.playerMoveSlot(ownerUUID), currentTick)) {
                clearPlayerMoveAttempt(session, ownerUUID, pokemonEntity);
                DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId() + ", slot=" + (session.playerMoveSlot(ownerUUID) + 1) + ", reason=cooldown");
                return;
            }
            if (ActionBattleHailHandler.isHail(move)) {
                if (!FightOrFlightAdapter.canCommit(pokemonEntity, targetEntity, move)) {
                    session.resetPlayerMoveCommitReady(ownerUUID);
                    ActionBattleMovementController.pursuePlayerPendingMove(session, ownerUUID, pokemonEntity, targetEntity);
                    return;
                }
                if (!playerMoveStartupReady(session, ownerUUID, pokemonEntity, move, currentTick)) {
                    pokemonEntity.getNavigation().stop();
                    return;
                }
                if (!ActionBattleParalysisController.executionReady(pokemonEntity,
                        ActionBattleParalysisController.active(session, pokemonUUID, currentTick), currentTick)) return;
                pokemonEntity.getNavigation().stop();
                ActionBattleHailHandler.StartResult hailResult = ActionBattleHailHandler.tryStart(session, level, pokemonEntity, targetEntity, move);
                if (hailResult == ActionBattleHailHandler.StartResult.STARTED) dispatchOwnedMechanics(pokemonEntity, targetEntity, move);
                session.clearPlayerMoveCommand(ownerUUID);
                DebugLog.log("[CobblemonNML] Hail ACTION start result. Battle=" + session.battleId() + ", result=" + hailResult);
                return;
            }
            if (ActionBattleToxicSpikesHandler.isToxicSpikes(move)) {
                if (!FightOrFlightAdapter.canCommit(pokemonEntity, targetEntity, move)) {
                    session.resetPlayerMoveCommitReady(ownerUUID);
                    ActionBattleMovementController.pursuePlayerPendingMove(session, ownerUUID, pokemonEntity, targetEntity);
                    return;
                }
                if (!playerMoveStartupReady(session, ownerUUID, pokemonEntity, move, currentTick)) {
                    pokemonEntity.getNavigation().stop();
                    return;
                }
                if (!ActionBattleParalysisController.executionReady(pokemonEntity,
                        ActionBattleParalysisController.active(session, pokemonUUID, currentTick), currentTick)) return;
                pokemonEntity.getNavigation().stop();
                ActionBattleToxicSpikesHandler.StartResult result = ActionBattleToxicSpikesHandler.tryStart(session, level, pokemonEntity, targetEntity, move);
                session.clearPlayerMoveCommand(ownerUUID);
                if (result == ActionBattleToxicSpikesHandler.StartResult.STARTED) {
                    ActionBattleProtectController.global().onSuccessfulNonProtectMove(session.battleId(), pokemonUUID);
                    ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), pokemonUUID, move);
                    dispatchOwnedMechanics(pokemonEntity, targetEntity, move);
                }
                DebugLog.log("[CobblemonNML] Toxic Spikes ACTION start result. Battle=" + session.battleId() + ", result=" + result);
                return;
            }
            int momentum = ActionBattleFlyingRuntime.momentum(session, pokemonUUID);
            ActionBattlePropulsionRules.CommitMode commitMode = FightOrFlightAdapter.commitMode(
                    pokemonEntity, targetEntity, move, momentum);
            if (commitMode != ActionBattlePropulsionRules.CommitMode.REPOSITION) {
                if (!playerMoveStartupReady(session, ownerUUID, pokemonEntity, move, currentTick)) {
                    pokemonEntity.getNavigation().stop();
                    return;
                }
                if (!ActionBattleParalysisController.executionReady(pokemonEntity,
                        ActionBattleParalysisController.active(session, pokemonUUID, currentTick), currentTick)) return;
                pokemonEntity.getNavigation().stop();
                if (!FightOrFlightAdapter.consumeOnePp(pokemonEntity, move)) {
                    session.clearPlayerMoveCommand(ownerUUID);
                    DebugLog.log("[CobblemonNML] Action move cancelled because PP could not be consumed. Battle=" + session.battleId() + ", move=" + move.getName());
                    return;
                }
                if (ActionBattleParalysisRules.failsAction(
                        ActionBattleParalysisController.active(session, pokemonUUID, currentTick),
                        pokemonEntity.getRandom().nextDouble())) {
                    ActionBattleGhostRuntime.global().applyAbilityCooldown(
                            session, pokemonEntity, session.playerMoveSlot(ownerUUID), currentTick);
                    session.clearPlayerMoveCommand(ownerUUID);
                    DebugLog.log("[CobblemonNML] Paralyzed ACTION move committed but failed. Battle="
                            + session.battleId() + ", move=" + move.getName());
                    return;
                }
                ActionBattleCommittedMove committedMove = captureCommittedMove(session, pokemonEntity, move);
                BlockPos plasmaTarget = ActionBattleTypeMechanicIdentity.hasMechanicBenefit(pokemonEntity, "electric")
                        && !ActionBattleTypeMechanicIdentity.hasMechanicBenefit(pokemonEntity, "psychic")
                        && FightOrFlightAdapter.isRangedMove(move) && FightOrFlightAdapter.movePower(move) > 0
                        && ActionBattleElectricController.activeCount(session.dungeonSessionId()) > 3
                        ? ActionBattleElectricController.nearestPlasmaBallTo(
                        session.dungeonSessionId(), targetEntity.blockPosition()) : null;
                boolean executed = plasmaTarget != null
                        ? FightOrFlightAdapter.executeRangedAtPoint(pokemonEntity, targetEntity, move,
                        Vec3.atCenterOf(plasmaTarget), 1.0D, committedMove)
                        : FightOrFlightAdapter.execute(pokemonEntity, targetEntity, move, 1.0D, committedMove);
                if (executed) {
                    if (plasmaTarget != null) {
                        DebugLog.log("[CobblemonNML] Electric player aimed at Plasma Ball. Battle="
                                + session.battleId() + ", ball=" + plasmaTarget + ", enemy="
                                + targetEntity.blockPosition());
                    }
                    net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime.onSuccessfulSelfMoveCommit(pokemonEntity, move);
                    long cooldownTicks = ActionBattleGhostRuntime.global().applyAbilityCooldown(
                            session, pokemonEntity, session.playerMoveSlot(ownerUUID), currentTick).sharedTicks();
                    ActionBattleProtectController.global().onSuccessfulNonProtectMove(session.battleId(), pokemonUUID);
                    ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), pokemonUUID, move);
                    session.clearPlayerMoveCommand(ownerUUID);
                    DebugLog.log("[CobblemonNML] Action move committed through Fight or Flight. Battle=" + session.battleId() + ", move=" + move.getName() + ", cooldownTicks=" + cooldownTicks);
                } else {
                    FightOrFlightAdapter.refundOnePp(move);
                    if (commitMode == ActionBattlePropulsionRules.CommitMode.PROPULSION) {
                        ActionBattleMovementController.pursuePlayerPendingMove(
                                session, ownerUUID, pokemonEntity, targetEntity);
                    } else {
                        session.clearPlayerMoveCommand(ownerUUID);
                    }
                }
                return;
            }
            session.resetPlayerMoveCommitReady(ownerUUID);
            ActionBattleMovementController.pursuePlayerPendingMove(session, ownerUUID, pokemonEntity, targetEntity);
            return;
        }
        if (!session.hasPlayerMoveTarget(ownerUUID)) return;
        if (ActionBattleAerialMoveController.isActive(session,
                session.playerActivePokemonUUID(ownerUUID))) return;
        Vec3 target = new Vec3(session.playerMoveTargetX(ownerUUID), session.playerMoveTargetY(ownerUUID), session.playerMoveTargetZ(ownerUUID));
        double tolerance = Math.max(0.75D, pokemonEntity.getBbWidth() * 0.75D);
        if (pokemonEntity.position().distanceToSqr(target) <= tolerance * tolerance) {
            pokemonEntity.getNavigation().stop();
            session.clearPlayerMoveTarget(ownerUUID);
            return;
        }
        if (pokemonEntity.getNavigation().isDone()) {
            session.clearPlayerMoveTarget(ownerUUID);
            DebugLog.log("[CobblemonNML] Move Here cancelled after navigation stopped before reaching target. Battle=" + session.battleId());
        }
    }


    private static boolean playerMoveStartupReady(ActionBattleSession session, UUID ownerUUID,
                                                  PokemonEntity pokemonEntity, Move move, long currentTick) {
        if (session == null || ownerUUID == null || pokemonEntity == null || move == null) return false;
        int speedStage = ActionBattleStatResolver.effectiveStage(session.battleId(),
                pokemonEntity.getPokemon().getUuid(), ActionBattleStat.SPEED, currentTick);
        int priority = FightOrFlightAdapter.movePriority(move);
        long readySince = session.markPlayerMoveCommitReady(ownerUUID, currentTick);
        return ActionBattleMoveTimingRules.ready(readySince, currentTick, priority, speedStage);
    }

    private static boolean handleFaintState(ServerPlayer player, ActionBattleSession session, ServerLevel level) {
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        if (refs == null) return false;
        UUID ownerUUID = player.getUUID();
        Pokemon playerPokemon = refs.playerPokemon(ownerUUID);
        Pokemon trainerPokemon = refs.trainerPokemon();
        boolean playerFainted = playerPokemon != null && playerPokemon.isFainted() && !session.isPlayerSendOutPending(ownerUUID);
        boolean trainerFainted = trainerPokemon != null && trainerPokemon.isFainted() && !session.isTrainerSendOutPending();
        if (!playerFainted && !trainerFainted) return false;

        Entity rawTrainer = level.getEntity(session.trainerUUID());
        if (!(rawTrainer instanceof LivingEntity trainerEntity) || trainerEntity.isRemoved()) {
            DebugLog.log("[CobblemonNML] Action battle faint transition failed: trainer entity is missing. Battle=" + session.battleId());
            invalidateBattle(player.getUUID());
            return true;
        }
        TrainerNPC runtimeTrainer = ActionBattleTrainerResolver.resolve(session.runtimeTrainerId(), trainerEntity);
        if (runtimeTrainer == null) {
            invalidateBattle(player.getUUID());
            return true;
        }

        ActionBattlePokemonSelection.Selection playerReplacement = playerFainted ? ActionBattlePokemonSelection.nextUsable(player, session.playerActivePartyIndex(ownerUUID)) : null;
        ActionBattlePokemonSelection.Selection trainerReplacement = trainerFainted ? ActionBattlePokemonSelection.nextUsable(runtimeTrainer, session.trainerActivePartyIndex()) : null;

        if (playerFainted && playerReplacement == null) {
            DebugLog.log("[CobblemonNML] Player has no usable Pokemon remaining. Battle=" + session.battleId());
            endBattle(player, ActionBattleResult.PLAYER_LOSS);
            return true;
        }
        if (trainerFainted && trainerReplacement == null) {
            DebugLog.log("[CobblemonNML] Trainer has no usable Pokemon remaining. Battle=" + session.battleId());
            endBattle(player, ActionBattleResult.PLAYER_WIN);
            return true;
        }

        if (playerPokemon != null) ActionBattleCommandController.cancelPendingOrders(session, playerPokemon.getUuid(),
                ActionBattleCommandController.InterruptReason.FAINT);
        ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.FAINT);
        ActionBattleMovementController.stopActivePlayerNavigation(session, ownerUUID, level);
        ActionBattleMovementController.stopActiveTrainerNavigation(session, level);
        if (playerFainted) {
            clearGroundState(session, level, playerPokemon.getUuid(), session.playerActiveEntityUUID(ownerUUID));
            ActionBattleEffectRuntime.onPokemonUnavailable(session, playerPokemon.getUuid(), true, level.getGameTime());
            int previousSlot = session.playerActivePartyIndex(ownerUUID);
            session.setPlayerSendOutPending(ownerUUID, true);
            ActionBattlePokemonRuntime.recall(playerPokemon);
            session.clearPlayerActivePokemon(ownerUUID);
            refs.setPlayerPokemon(ownerUUID, playerReplacement.pokemon());
            ActionBattlePokemonRuntime.seedDamageFeedback(session, playerReplacement.pokemon());
            ActionBattlePokemonRuntime.sendOut(session, true, ownerUUID, player, trainerEntity, playerReplacement);
            DebugLog.log("[CobblemonNML] Player faint replacement started. Battle=" + session.battleId() + ", fromSlot=" + previousSlot + ", toSlot=" + playerReplacement.slot());
        }
        if (trainerFainted) {
            clearGroundState(session, level, trainerPokemon.getUuid(), session.trainerActiveEntityUUID());
            ActionBattleEffectRuntime.onPokemonUnavailable(session, trainerPokemon.getUuid(), true, level.getGameTime());
            int previousSlot = session.trainerActivePartyIndex();
            session.setTrainerSendOutPending(true);
            ActionBattlePokemonRuntime.recall(trainerPokemon);
            session.clearTrainerActivePokemon();
            refs.setTrainerPokemon(trainerReplacement.pokemon());
            ActionBattlePokemonRuntime.seedDamageFeedback(session, trainerReplacement.pokemon());
            ActionBattlePokemonRuntime.sendOut(session, false, trainerEntity, player, trainerReplacement);
            DebugLog.log("[CobblemonNML] Trainer faint replacement started. Battle=" + session.battleId() + ", fromSlot=" + previousSlot + ", toSlot=" + trainerReplacement.slot());
        }
        return true;
    }

    private static ActionBattleCommittedMove captureCommittedMove(ActionBattleSession session,
                                                                   PokemonEntity attacker, Move move) {
        int momentum = ActionBattleFlyingRuntime.momentum(session, attacker.getPokemon().getUuid());
        return ActionBattleCommittedMove.capture(attacker, move, momentum, attacker.getRandom()::nextDouble);
    }

    private static void clearGroundState(ActionBattleSession session, ServerLevel level,
                                         UUID pokemonId, UUID entityId) {
        if (!ActionBattleTypeEffectController.global().clearGroundPokemon(session.dungeonSessionId(), pokemonId)) return;
        Entity raw = level != null && entityId != null ? level.getEntity(entityId) : null;
        if (raw instanceof PokemonEntity pokemon) {
            ActionBattleGroundVisualSync.update(pokemon, session.dungeonSessionId(), 0);
        }
    }

    private static void syncHud(ServerPlayer player, ActionBattleSession session) {
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        Pokemon playerPokemon = refs != null ? refs.playerPokemon(player.getUUID()) : null;
        if (playerPokemon == null || refs.trainerPokemon() == null) return;
        ActionBattleHudSync.send(player, session, playerPokemon, refs.trainerPokemon());
    }

    private static ActionBattleSession autoJoinArena(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel) || !player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return null;
        ActionBattleSession session = ActionBattleRegistry.arenaContaining(player.getX(), player.getZ());
        if (session == null || session.arena() == null || session.arena().isParticipant(player.getUUID())) return null;
        ActionBattlePokemonSelection.Selection lead = ActionBattlePokemonSelection.firstUsable(player);
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        Entity rawTrainer = player.serverLevel().getEntity(session.trainerUUID());
        if (lead == null || refs == null || !(rawTrainer instanceof LivingEntity trainer) || trainer.isRemoved()) return null;
        if (!ActionBattleRegistry.registerParticipant(session, player.getUUID())) return null;
        refs.setPlayerPokemon(player.getUUID(), lead.pokemon());
        ActionBattlePokemonRuntime.seedDamageFeedback(session, lead.pokemon());
        ActionBattleArenaTransition transition = session.arena().enter(player.getUUID());
        session.setPlayerSendOutPending(player.getUUID(), true);
        ActionBattlePokemonRuntime.sendOut(session, true, player.getUUID(), player, trainer, lead);
        if (transition.has(ActionBattleArenaTransition.Action.SHOW_HUD)) syncHud(player, session);
        DebugLog.log("[CobblemonNML] Player automatically joined room battle. Battle=" + session.battleId()
                + ", player=" + player.getUUID());
        return session;
    }

    private static boolean updateArenaPresence(ServerPlayer player, ActionBattleSession session, ServerLevel level,
                                               ActionBattlePokemonRefs refs) {
        ActionBattleArena arena = session.arena();
        if (arena == null) return true;
        UUID playerUUID = player.getUUID();
        boolean insideBounds = player.level() == level && arena.contains(player.getX(), player.getZ());
        if (insideBounds && !arena.isInside(playerUUID)) {
            ActionBattleArenaTransition transition = arena.enter(playerUUID);
            resumePlayerParticipant(player, session, level, refs);
            if (transition.has(ActionBattleArenaTransition.Action.SEND_OUT_TRAINER)) resumeTrainer(session, level, refs, player);
            syncHud(player, session);
        } else if (!insideBounds && arena.isInside(playerUUID)) {
            ActionBattleArenaTransition transition = arena.exit(playerUUID);
            recallPlayerParticipant(player, session, level, refs);
            ActionBattleHudSync.hide(player);
            if (transition.has(ActionBattleArenaTransition.Action.RECALL_TRAINER)) recallTrainer(session, level, refs);
        }
        return arena.isInside(playerUUID);
    }

    private static void recallPlayerParticipant(ServerPlayer player, ActionBattleSession session, ServerLevel level,
                                                ActionBattlePokemonRefs refs) {
        Pokemon pokemon = refs != null ? refs.playerPokemon(player.getUUID()) : null;
        if (pokemon == null) return;
        session.clearPlayerMoveState(player.getUUID());
        UUID entityUUID = session.playerActiveEntityUUID(player.getUUID());
        clearGroundState(session, level, pokemon.getUuid(), entityUUID);
        ActionBattleEffectRuntime.onPokemonUnavailable(session, pokemon.getUuid(), pokemon.isFainted(), level.getGameTime());
        ActionBattlePokemonRuntime.recall(pokemon);
        session.clearPlayerActivePokemon(player.getUUID());
    }

    private static void resumePlayerParticipant(ServerPlayer player, ActionBattleSession session, ServerLevel level,
                                                ActionBattlePokemonRefs refs) {
        Pokemon retained = refs != null ? refs.playerPokemon(player.getUUID()) : null;
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        if (retained == null || retained.isFainted() || !(rawTrainer instanceof LivingEntity trainer)) return;
        int slot = partySlot(player, retained);
        if (slot < 0) return;
        session.setPlayerSendOutPending(player.getUUID(), true);
        ActionBattlePokemonRuntime.sendOut(session, true, player.getUUID(), player, trainer,
                new ActionBattlePokemonSelection.Selection(slot, retained));
    }

    private static void recallTrainer(ActionBattleSession session, ServerLevel level, ActionBattlePokemonRefs refs) {
        Pokemon trainerPokemon = refs != null ? refs.trainerPokemon() : null;
        if (trainerPokemon == null) return;
        clearGroundState(session, level, trainerPokemon.getUuid(), session.trainerActiveEntityUUID());
        ActionBattleEffectRuntime.onPokemonUnavailable(session, trainerPokemon.getUuid(), trainerPokemon.isFainted(), level.getGameTime());
        ActionBattlePokemonRuntime.recall(trainerPokemon);
        session.clearTrainerActivePokemon();
        session.clearTrainerMoveState();
    }

    private static void resumeTrainer(ActionBattleSession session, ServerLevel level, ActionBattlePokemonRefs refs,
                                      ServerPlayer opponent) {
        Pokemon trainerPokemon = refs != null ? refs.trainerPokemon() : null;
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        TrainerNPC trainer = rawTrainer instanceof LivingEntity living
                ? ActionBattleTrainerResolver.resolve(session.runtimeTrainerId(), living) : null;
        if (trainerPokemon == null || trainerPokemon.isFainted() || !(rawTrainer instanceof LivingEntity living) || trainer == null) return;
        int slot = trainerSlot(trainer, trainerPokemon);
        if (slot < 0) return;
        session.setTrainerSendOutPending(true);
        ActionBattlePokemonRuntime.sendOut(session, false, living, opponent,
                new ActionBattlePokemonSelection.Selection(slot, trainerPokemon));
    }

    private static int partySlot(ServerPlayer player, Pokemon wanted) {
        var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int slot = 0; slot < party.size(); slot++) if (party.get(slot) == wanted) return slot;
        return -1;
    }

    private static int trainerSlot(TrainerNPC trainer, Pokemon wanted) {
        Pokemon[] team = trainer.getTeam();
        for (int slot = 0; slot < team.length; slot++) if (team[slot] == wanted) return slot;
        return -1;
    }

    public static boolean shouldSuppressAutonomousMovement(PokemonEntity pokemonEntity) {
        if (pokemonEntity == null || pokemonEntity.isRemoved()) return false;
        return ActionBattleMovementController.shouldSuppressAutonomousMovement(pokemonEntity);
    }

    public static ActionBattleSession findSessionForBattlePokemonEntity(UUID entityUUID) {
        return ActionBattleRegistry.findByPokemonEntity(entityUUID);
    }

    public static ActionBattleSession findSessionForPokemon(UUID pokemonUUID) {
        if (pokemonUUID == null) return null;
        for (ActionBattleSession session : ActionBattleRegistry.sessionsSnapshot()) {
            if (pokemonUUID.equals(session.playerActivePokemonUUID()) || pokemonUUID.equals(session.trainerActivePokemonUUID())) {
                return session;
            }
            for (UUID playerUUID : session.playerUUIDs()) {
                if (pokemonUUID.equals(session.playerActivePokemonUUID(playerUUID))) return session;
            }
        }
        return null;
    }

    public static ActionBattleSession findSessionByBattleId(UUID battleId) {
        if (battleId == null) return null;
        for (ActionBattleSession session : ActionBattleRegistry.sessionsSnapshot()) {
            if (battleId.equals(session.battleId())) return session;
        }
        return null;
    }

    public static Pokemon findActivePokemon(UUID pokemonUUID) {
        ActionBattleSession session = findSessionForPokemon(pokemonUUID);
        if (session == null) return null;
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        if (refs == null) return null;
        for (Pokemon pokemon : refs.allPlayerPokemon()) {
            if (pokemon != null && pokemonUUID.equals(pokemon.getUuid())) return pokemon;
        }
        if (refs.trainerPokemon() != null && pokemonUUID.equals(refs.trainerPokemon().getUuid())) return refs.trainerPokemon();
        return null;
    }

    public static UUID battleIdForPokemonEntity(UUID entityUUID) {
        ActionBattleSession session = findSessionForBattlePokemonEntity(entityUUID);
        return session != null ? session.battleId() : null;
    }

    private static void cleanupBattlePokemon(ActionBattleSession session) {
        ServerPlayer cleanupPlayer = ActionBattlePokemonRuntime.findServerPlayer(session);
        ServerLevel cleanupLevel = cleanupPlayer != null && cleanupPlayer.getServer() != null
                ? cleanupPlayer.getServer().getLevel(DungeonDimension.DUNGEON_DIMENSION) : null;
        long battleEndTick = cleanupLevel != null ? cleanupLevel.getGameTime() : 0L;
        ActionBattleTypeEffectRuntime.onBattleEnded(session.dungeonSessionId(), battleEndTick);
        ActionBattlePokemonRefs activeRefs = ActionBattleRegistry.pokemonRefs(session.battleId());
        if (activeRefs != null) {
            for (UUID playerUUID : session.playerUUIDs()) {
                Pokemon pokemon = activeRefs.playerPokemon(playerUUID);
                if (pokemon == null) continue;
                clearGroundState(session, cleanupLevel, pokemon.getUuid(), session.playerActiveEntityUUID(playerUUID));
                ActionBattleEffectRuntime.onPokemonUnavailable(session, pokemon.getUuid(),
                        pokemon.isFainted(), battleEndTick, pokemon.isFainted());
            }
        }
        if (activeRefs != null && activeRefs.trainerPokemon() != null) {
            clearGroundState(session, cleanupLevel, activeRefs.trainerPokemon().getUuid(), session.trainerActiveEntityUUID());
            ActionBattleEffectRuntime.onPokemonUnavailable(session, activeRefs.trainerPokemon().getUuid(),
                    activeRefs.trainerPokemon().isFainted(), battleEndTick, activeRefs.trainerPokemon().isFainted());
        }
        ActionBattleEffectRuntime.clearBattle(session.battleId());
        ActionBattlePokemonRefs refs = ActionBattleRegistry.removePokemonRefs(session.battleId());
        if (refs != null) {
            for (Pokemon pokemon : refs.allPlayerPokemon()) ActionBattlePokemonRuntime.recall(pokemon);
            ActionBattlePokemonRuntime.recall(refs.trainerPokemon());
        }
        for (UUID playerUUID : session.playerUUIDs()) {
            UUID pokemonUUID = session.playerActivePokemonUUID(playerUUID);
            if (pokemonUUID != null) ActionBattleCommandController.cancelPendingOrders(session, pokemonUUID,
                    ActionBattleCommandController.InterruptReason.BATTLE_END);
            else session.clearPlayerMoveState(playerUUID);
        }
        ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.BATTLE_END);
        for (UUID playerUUID : session.playerUUIDs()) session.setPlayerSendOutPending(playerUUID, false);
        session.setTrainerSendOutPending(false);
        for (UUID playerUUID : session.playerUUIDs()) session.clearPlayerActivePokemon(playerUUID);
        session.clearTrainerActivePokemon();
        if (session.arena() != null) session.arena().deactivate();
    }

    private static void removeSession(ActionBattleSession session) {
        ActionBattleRegistry.remove(session);
        ActionBattleMovementController.removeBattle(session);
        ActionBattleConfusionController.clearBattle(session.battleId());
    }

    private static void routeResult(ServerPlayer player, ActionBattleSession session, ActionBattleResult result) {
        switch (result) {
            case PLAYER_WIN -> DungeonTrainerBattleResultHandler.handleVictory(player, session.trainerUUID());
            case SURRENDER -> DungeonTrainerBattleResultHandler.handleSurrender(player, session.trainerUUID());
            case PLAYER_LOSS -> DungeonTrainerBattleResultHandler.handleLoss(player, session.trainerUUID());
            case INVALID -> {
            }
        }
    }

    private static void dispatchOwnedMechanics(PokemonEntity pokemon, LivingEntity target, Move move) {
        net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicRuntime.onActionStarted(
                net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext.started(
                        pokemon, target, move));
    }

    private static void hideHudForAllPlayers(ActionBattleSession session, net.minecraft.server.MinecraftServer server) {
        if (session == null || server == null) return;
        for (UUID playerUUID : session.playerUUIDs()) {
            ServerPlayer participant = server.getPlayerList().getPlayer(playerUUID);
            if (participant != null) ActionBattleHudSync.hide(participant);
        }
    }

}

package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleBalefulBunkerHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleHailHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.events.trainer.DungeonTrainerBattleResultHandler;
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
    private static final double BATTLE_ZONE_RADIUS = 20.0D;

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
        ActionBattleSession session = new ActionBattleSession(
                UUID.randomUUID(), dungeonSessionId, player.getUUID(), trainer.getUUID(), runtimeTrainerId,
                trainerPreset != null ? trainerPreset.toString() : null,
                new ActionBattleZone(player.getX(), player.getZ(), BATTLE_ZONE_RADIUS)
        );
        ActionBattleRegistry.register(session, new ActionBattlePokemonRefs(playerLead.pokemon(), trainerLead.pokemon()));
        ActionBattlePokemonRuntime.seedDamageFeedback(session, playerLead.pokemon());
        ActionBattlePokemonRuntime.seedDamageFeedback(session, trainerLead.pokemon());
        DebugLog.log("[CobblemonNML] Action battle session started. Battle=" + session.battleId() + ", player=" + session.playerUUID() + ", trainer=" + session.trainerUUID() + ", runtimeTrainer=" + session.runtimeTrainerId() + ", preset=" + session.trainerPreset());
        try {
            ActionBattlePokemonRuntime.sendOut(session, true, player, trainer, playerLead);
            ActionBattlePokemonRuntime.sendOut(session, false, trainer, player, trainerLead);
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
        ActionBattleHudSync.hide(player);
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
        if (player != null) ActionBattleHudSync.hide(player);
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
    }

    public static int size() {
        return ActionBattleRegistry.size();
    }

    public static boolean requestPlayerMoveHere(ServerPlayer player, double x, double y, double z) {
        if (player == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        ActionBattleSession session = ActionBattleRegistry.byPlayer(player.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        if (!DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return false;
        if (!player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        UUID activePokemonId = session.playerActivePokemonUUID();
        UUID activeEntityId = session.playerActiveEntityUUID();
        if (activePokemonId == null || activeEntityId == null) return false;
        long currentTick = level.getGameTime();
        ActionBattleCommandController.onCommandIssued(session, activePokemonId);
        if (session.isPokemonMovementCommandOnCooldown(activePokemonId, currentTick)) {
            DebugLog.log("[CobblemonNML] Move Here rejected. Battle=" + session.battleId() + ", reason=move_here_cooldown");
            return false;
        }
        Entity rawEntity = level.getEntity(activeEntityId);
        if (!(rawEntity instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) return false;
        pokemonEntity.getNavigation().stop();
        session.clearPlayerMoveCommand();
        BlockPos targetPos = BlockPos.containing(x, y, z);
        if (targetPos.getY() < level.getMinBuildHeight() || targetPos.getY() >= level.getMaxBuildHeight()) return false;
        if (!level.getChunkSource().hasChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4) || !level.getWorldBorder().isWithinBounds(targetPos)) return false;
        Path path = pokemonEntity.getNavigation().createPath(x, y, z, 0);
        if (path == null || !path.canReach()) {
            session.clearPlayerMoveTarget();
            DebugLog.log("[CobblemonNML] Move Here rejected as unreachable. Battle=" + session.battleId() + ", target=" + new Vec3(x, y, z));
            return false;
        }
        if (!pokemonEntity.getNavigation().moveTo(path, ActionBattleMovementController.movementSpeed(session, activePokemonId, currentTick))) {
            session.clearPlayerMoveTarget();
            return false;
        }
        session.startPokemonMovementCommandCooldown(activePokemonId, currentTick, ActionBattleTiming.MOVE_HERE_COOLDOWN_TICKS);
        long revision = session.replacePlayerMoveTarget(x, y, z);
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
        if (refs == null || refs.playerPokemon() == null) return rejectMove(session, moveSlot, "missing_player_pokemon_ref");
        Move move = refs.playerPokemon().getMoveSet().get(moveSlot);
        if (move == null) return rejectMove(session, moveSlot, "missing_move");
        if (!FightOrFlightAdapter.supports(move)) return rejectMove(session, moveSlot, "unsupported_move");
        if (!FightOrFlightAdapter.hasPp(move)) return rejectMove(session, moveSlot, "no_pp");
        long currentTick = level.getGameTime();
        ActionBattleCommandController.onCommandIssued(session, refs.playerPokemon().getUuid());
        if (session.isPokemonMoveOnCooldown(refs.playerPokemon().getUuid(), currentTick)) return rejectMove(session, moveSlot, "cooldown");
        UUID playerEntityId = session.playerActiveEntityUUID();
        if (playerEntityId == null) return rejectMove(session, moveSlot, "missing_player_entity_id");
        Entity rawPlayerPokemon = level.getEntity(playerEntityId);
        if (!(rawPlayerPokemon instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) return rejectMove(session, moveSlot, "missing_player_entity");
        if (ActionBattleBalefulBunkerHandler.isBalefulBunker(move)) {
            pokemonEntity.getNavigation().stop();
            session.clearPlayerMoveTarget();
            ActionBattleBalefulBunkerHandler.StartResult result = ActionBattleBalefulBunkerHandler.tryStart(session, pokemonEntity, move);
            if (result == ActionBattleBalefulBunkerHandler.StartResult.STARTED) ActionBattleParalysisController.onAbilitySucceeded(session.battleId(), refs.playerPokemon().getUuid(), currentTick);
            DebugLog.log("[CobblemonNML] Baleful Bunker ACTION start result. Battle=" + session.battleId() + ", result=" + result);
            return result == ActionBattleBalefulBunkerHandler.StartResult.STARTED;
        }
        UUID targetEntityId = session.trainerActiveEntityUUID();
        if (targetEntityId == null) return rejectMove(session, moveSlot, "missing_target_entity_id");
        Entity rawTargetPokemon = level.getEntity(targetEntityId);
        if (!(rawTargetPokemon instanceof PokemonEntity targetEntity) || targetEntity.isRemoved()) return rejectMove(session, moveSlot, "missing_target_entity");
        pokemonEntity.getNavigation().stop();
        session.clearPlayerMoveTarget();
        long revision = session.replacePlayerMoveCommand(moveSlot, targetEntityId);
        ActionBattleMovementController.pursuePlayerPendingMove(session, pokemonEntity, targetEntity);
        DebugLog.log("[CobblemonNML] Move " + (moveSlot + 1) + " queued. Battle=" + session.battleId() + ", revision=" + revision + ", target=" + targetEntityId);
        return true;
    }

    private static boolean rejectMove(ActionBattleSession session, int moveSlot, String reason) {
        DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId() + ", slot=" + (moveSlot + 1) + ", reason=" + reason);
        return false;
    }

    public static boolean requestPlayerSwap(ServerPlayer player) {
        if (player == null) return false;
        ActionBattleSession session = ActionBattleRegistry.byPlayer(player.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        if (!DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return rejectSwap(session, "inactive_dungeon_session");
        if (!(player.level() instanceof ServerLevel level) || !player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return rejectSwap(session, "wrong_dimension");
        long currentTick = level.getGameTime();
        if (session.playerActivePokemonUUID() != null) ActionBattleCommandController.onCommandIssued(session, session.playerActivePokemonUUID());
        if (session.isPlayerSwapOnCooldown(currentTick)) return rejectSwap(session, "cooldown");
        if (session.isPlayerSendOutPending()) return rejectSwap(session, "sendout_pending");
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        if (refs == null || refs.playerPokemon() == null) return rejectSwap(session, "missing_player_pokemon_ref");
        int previousSlot = session.playerActivePartyIndex();
        ActionBattlePokemonSelection.Selection next = ActionBattlePokemonSelection.nextUsable(player, previousSlot);
        if (next == null) return rejectSwap(session, "no_available_replacement");
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        if (!(rawTrainer instanceof LivingEntity trainerEntity) || trainerEntity.isRemoved()) return rejectSwap(session, "missing_trainer_entity");
        Entity rawPlayerPokemon = session.playerActiveEntityUUID() != null ? level.getEntity(session.playerActiveEntityUUID()) : null;
        if (rawPlayerPokemon instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) {
            pokemonEntity.getNavigation().stop();
        }
        ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.PLAYER, ActionBattleCommandController.InterruptReason.SWAP);
        session.startPlayerSwapCooldown(currentTick, ActionBattleTiming.SWAP_COOLDOWN_TICKS);
        session.setPlayerSendOutPending(true);
        Pokemon previous = refs.playerPokemon();
        ActionBattleEffectController.global().onPokemonRecalled(session.battleId(), previous.getUuid(), currentTick);
        ActionBattleParalysisController.onPokemonRecalled(session.battleId(), previous.getUuid());
        ActionBattleProtectController.global().onPokemonRecalled(session.battleId(), previous.getUuid());
        ActionBattlePokemonRuntime.recall(previous);
        session.clearPlayerActivePokemon();
        refs.setPlayerPokemon(next.pokemon());
        ActionBattlePokemonRuntime.seedDamageFeedback(session, next.pokemon());
        ActionBattlePokemonRuntime.sendOut(session, true, player, trainerEntity, next);
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
        if (session == null || session.state() != ActionBattleState.ACTIVE) return;
        if (!(player.level() instanceof ServerLevel level)) {
            ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.PLAYER, ActionBattleCommandController.InterruptReason.TARGET_INVALID);
            return;
        }
        ActionBattlePokemonRefs runtimeRefs = ActionBattleRegistry.pokemonRefs(session.battleId());
        ActionBattleEffectRuntime.tickBattle(session, level, runtimeRefs);
        if (handleFaintState(player, session, level)) return;
        ActionBattleMovementController.tickPlayerBattleZone(session, player, level);
        ActionBattleParalysisController.tickBattle(session, level);
        if (level.getGameTime() % ActionBattleTiming.HUD_SYNC_INTERVAL_TICKS == 0L) syncHud(player, session);
        ActionBattleTrainerAiController.tick(session, level, ActionBattleRegistry.pokemonRefs(session.battleId()));
        if (session.state() != ActionBattleState.ACTIVE) return;
        UUID activeEntityId = session.playerActiveEntityUUID();
        Entity rawEntity = activeEntityId != null ? level.getEntity(activeEntityId) : null;
        if (!(rawEntity instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) {
            session.clearPlayerMoveTarget();
            session.clearPlayerMoveCommand();
            return;
        }
        if (session.hasPlayerMoveCommand()) {
            Entity rawTarget = level.getEntity(session.playerMoveTargetEntityUUID());
            if (!(rawTarget instanceof PokemonEntity targetEntity) || targetEntity.isRemoved()) {
                pokemonEntity.getNavigation().stop();
                session.clearPlayerMoveCommand();
                DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId() + ", slot=" + (session.playerMoveSlot() + 1) + ", reason=missing_target_entity");
                return;
            }
            ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
            Move move = refs != null && refs.playerPokemon() != null ? refs.playerPokemon().getMoveSet().get(session.playerMoveSlot()) : null;
            if (move == null || !FightOrFlightAdapter.supports(move) || !FightOrFlightAdapter.hasPp(move)) {
                pokemonEntity.getNavigation().stop();
                session.clearPlayerMoveCommand();
                String reason = move == null ? "missing_move" : !FightOrFlightAdapter.supports(move) ? "unsupported_move" : "no_pp";
                DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId() + ", slot=" + (session.playerMoveSlot() + 1) + ", reason=" + reason);
                return;
            }
            long currentTick = level.getGameTime();
            UUID pokemonUUID = refs.playerPokemon().getUuid();
            if (session.isPokemonMoveOnCooldown(pokemonUUID, currentTick)) {
                pokemonEntity.getNavigation().stop();
                session.clearPlayerMoveCommand();
                DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId() + ", slot=" + (session.playerMoveSlot() + 1) + ", reason=cooldown");
                return;
            }
            if (ActionBattleHailHandler.isHail(move)) {
                if (!FightOrFlightAdapter.canCommit(pokemonEntity, targetEntity, move)) {
                    ActionBattleMovementController.pursuePlayerPendingMove(session, pokemonEntity, targetEntity);
                    return;
                }
                pokemonEntity.getNavigation().stop();
                ActionBattleHailHandler.StartResult hailResult = ActionBattleHailHandler.tryStart(session, level, pokemonEntity, targetEntity, move);
                session.clearPlayerMoveCommand();
                if (hailResult == ActionBattleHailHandler.StartResult.STARTED) ActionBattleProtectController.global().onSuccessfulNonProtectMove(session.battleId(), pokemonUUID);
                DebugLog.log("[CobblemonNML] Hail ACTION start result. Battle=" + session.battleId() + ", result=" + hailResult);
                return;
            }
            if (ActionBattleToxicSpikesHandler.isToxicSpikes(move)) {
                if (!FightOrFlightAdapter.canCommit(pokemonEntity, targetEntity, move)) {
                    ActionBattleMovementController.pursuePlayerPendingMove(session, pokemonEntity, targetEntity);
                    return;
                }
                pokemonEntity.getNavigation().stop();
                ActionBattleToxicSpikesHandler.StartResult result = ActionBattleToxicSpikesHandler.tryStart(session, level, pokemonEntity, targetEntity, move);
                session.clearPlayerMoveCommand();
                if (result == ActionBattleToxicSpikesHandler.StartResult.STARTED) ActionBattleProtectController.global().onSuccessfulNonProtectMove(session.battleId(), pokemonUUID);
                DebugLog.log("[CobblemonNML] Toxic Spikes ACTION start result. Battle=" + session.battleId() + ", result=" + result);
                return;
            }
            if (FightOrFlightAdapter.canCommit(pokemonEntity, targetEntity, move)) {
                pokemonEntity.getNavigation().stop();
                if (!FightOrFlightAdapter.consumeOnePp(move)) {
                    session.clearPlayerMoveCommand();
                    DebugLog.log("[CobblemonNML] Action move cancelled because PP could not be consumed. Battle=" + session.battleId() + ", move=" + move.getName());
                    return;
                }
                if (FightOrFlightAdapter.execute(pokemonEntity, targetEntity, move)) {
                    long cooldownTicks = FightOrFlightAdapter.cooldownTicks(move);
                    session.startPokemonMoveCooldown(pokemonUUID, currentTick, cooldownTicks);
                    ActionBattleProtectController.global().onSuccessfulNonProtectMove(session.battleId(), pokemonUUID);
                    ActionBattleParalysisController.onAbilitySucceeded(session.battleId(), pokemonUUID, currentTick);
                    session.clearPlayerMoveCommand();
                    DebugLog.log("[CobblemonNML] Action move committed through Fight or Flight. Battle=" + session.battleId() + ", move=" + move.getName() + ", cooldownTicks=" + cooldownTicks);
                } else {
                    FightOrFlightAdapter.refundOnePp(move);
                    session.clearPlayerMoveCommand();
                }
                return;
            }
            ActionBattleMovementController.pursuePlayerPendingMove(session, pokemonEntity, targetEntity);
            return;
        }
        if (!session.hasPlayerMoveTarget()) return;
        Vec3 target = new Vec3(session.playerMoveTargetX(), session.playerMoveTargetY(), session.playerMoveTargetZ());
        double tolerance = Math.max(0.75D, pokemonEntity.getBbWidth() * 0.75D);
        if (pokemonEntity.position().distanceToSqr(target) <= tolerance * tolerance) {
            pokemonEntity.getNavigation().stop();
            session.clearPlayerMoveTarget();
            return;
        }
        if (pokemonEntity.getNavigation().isDone()) {
            session.clearPlayerMoveTarget();
            DebugLog.log("[CobblemonNML] Move Here cancelled after navigation stopped before reaching target. Battle=" + session.battleId());
        }
    }

    private static boolean handleFaintState(ServerPlayer player, ActionBattleSession session, ServerLevel level) {
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        if (refs == null) return false;
        Pokemon playerPokemon = refs.playerPokemon();
        Pokemon trainerPokemon = refs.trainerPokemon();
        boolean playerFainted = playerPokemon != null && playerPokemon.isFainted() && !session.isPlayerSendOutPending();
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

        ActionBattlePokemonSelection.Selection playerReplacement = playerFainted ? ActionBattlePokemonSelection.nextUsable(player, session.playerActivePartyIndex()) : null;
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

        ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.PLAYER, ActionBattleCommandController.InterruptReason.FAINT);
        ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.FAINT);
        ActionBattleMovementController.stopActivePlayerNavigation(session, level);
        ActionBattleMovementController.stopActiveTrainerNavigation(session, level);
        if (playerFainted) {
            int previousSlot = session.playerActivePartyIndex();
            session.setPlayerSendOutPending(true);
            ActionBattlePokemonRuntime.recall(playerPokemon);
            session.clearPlayerActivePokemon();
            refs.setPlayerPokemon(playerReplacement.pokemon());
            ActionBattlePokemonRuntime.seedDamageFeedback(session, playerReplacement.pokemon());
            ActionBattlePokemonRuntime.sendOut(session, true, player, trainerEntity, playerReplacement);
            DebugLog.log("[CobblemonNML] Player faint replacement started. Battle=" + session.battleId() + ", fromSlot=" + previousSlot + ", toSlot=" + playerReplacement.slot());
        }
        if (trainerFainted) {
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

    private static void syncHud(ServerPlayer player, ActionBattleSession session) {
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        if (refs == null || refs.playerPokemon() == null || refs.trainerPokemon() == null) return;
        ActionBattleHudSync.send(player, session, refs.playerPokemon(), refs.trainerPokemon());
    }

    public static boolean shouldSuppressAutonomousMovement(PokemonEntity pokemonEntity) {
        if (pokemonEntity == null || pokemonEntity.isRemoved() || !(pokemonEntity.level() instanceof ServerLevel level)) return false;
        return ActionBattleMovementController.shouldSuppressAutonomousMovement(pokemonEntity);
    }

    public static ActionBattleSession findSessionForBattlePokemonEntity(UUID entityUUID) {
        return ActionBattleRegistry.findByPokemonEntity(entityUUID);
    }

    public static UUID battleIdForPokemonEntity(UUID entityUUID) {
        ActionBattleSession session = findSessionForBattlePokemonEntity(entityUUID);
        return session != null ? session.battleId() : null;
    }

    private static void cleanupBattlePokemon(ActionBattleSession session) {
        ActionBattleEffectRuntime.clearBattle(session.battleId());
        ActionBattlePokemonRefs refs = ActionBattleRegistry.removePokemonRefs(session.battleId());
        if (refs != null) {
            ActionBattlePokemonRuntime.recall(refs.playerPokemon());
            ActionBattlePokemonRuntime.recall(refs.trainerPokemon());
        }
        ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.PLAYER, ActionBattleCommandController.InterruptReason.BATTLE_END);
        ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.BATTLE_END);
        session.setPlayerSendOutPending(false);
        session.setTrainerSendOutPending(false);
        session.clearPlayerActivePokemon();
        session.clearTrainerActivePokemon();
    }

    private static void removeSession(ActionBattleSession session) {
        ActionBattleRegistry.remove(session);
        ActionBattleMovementController.removeBattle(session.battleId());
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

}

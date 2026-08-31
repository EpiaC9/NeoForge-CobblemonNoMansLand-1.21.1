package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry;
import com.gitlab.srcmc.tbcs.api.TBCS;
import kotlin.Unit;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleDotEvent;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleDotDamage;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleStatusParticleController;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;

public final class ActionBattleManager {
    private static final double ACTION_MOVEMENT_SPEED = 0.6D;
    private static final double BATTLE_ZONE_RADIUS = 20.0D;
    private static final long SWAP_COOLDOWN_TICKS = 320L;
    private static final long MOVE_HERE_COOLDOWN_TICKS = 20L;
    private static final long HUD_SYNC_INTERVAL_TICKS = 2L;
    private static final Map<UUID, ActionBattleSession> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, ActionBattleSession> BY_TRAINER = new HashMap<>();
    private static final Map<UUID, BattlePokemonRefs> POKEMON_BY_BATTLE = new HashMap<>();
    private static final Map<UUID, Boolean> PLAYER_ZONE_STATES = new HashMap<>();

    private ActionBattleManager() {}

    public static ActionBattleSession startBattle(ServerPlayer player, LivingEntity trainer, String runtimeTrainerId, ResourceLocation trainerPreset) {
        if (player == null || trainer == null || runtimeTrainerId == null || runtimeTrainerId.isBlank()) return null;
        UUID dungeonSessionId = DungeonSession.getSessionId();
        if (!DungeonSession.isActive() || dungeonSessionId == null) {
            DebugLog.log("[CobblemonNML] Cannot start action battle without an active dungeon session.");
            return null;
        }
        if (BY_PLAYER.containsKey(player.getUUID())) {
            DebugLog.log("[CobblemonNML] Player already has an active action battle: " + player.getUUID());
            return null;
        }
        if (BY_TRAINER.containsKey(trainer.getUUID())) {
            DebugLog.log("[CobblemonNML] Trainer already has an active action battle: " + trainer.getUUID());
            return null;
        }
        TrainerNPC runtimeTrainer = resolveRuntimeTrainer(runtimeTrainerId, trainer);
        if (runtimeTrainer == null) return null;
        SelectedPokemon playerLead = findFirstUsablePlayerPokemon(player);
        SelectedPokemon trainerLead = findFirstUsableTrainerPokemon(runtimeTrainer);
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
        BY_PLAYER.put(player.getUUID(), session);
        BY_TRAINER.put(trainer.getUUID(), session);
        POKEMON_BY_BATTLE.put(session.battleId(), new BattlePokemonRefs(playerLead.pokemon(), trainerLead.pokemon()));
        seedDamageFeedback(session, playerLead.pokemon());
        seedDamageFeedback(session, trainerLead.pokemon());
        DebugLog.log("[CobblemonNML] Action battle session started. Battle=" + session.battleId() + ", player=" + session.playerUUID() + ", trainer=" + session.trainerUUID() + ", runtimeTrainer=" + session.runtimeTrainerId() + ", preset=" + session.trainerPreset());
        try {
            sendOutPokemon(session, true, player, trainer, playerLead);
            sendOutPokemon(session, false, trainer, player, trainerLead);
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] Failed to begin action battle Pokemon send-out: " + exception.getMessage());
            exception.printStackTrace();
            invalidateBattle(player.getUUID());
            return null;
        }
        return session;
    }

    public static ActionBattleSession getByPlayer(UUID playerUUID) {
        return playerUUID != null ? BY_PLAYER.get(playerUUID) : null;
    }

    public static ActionBattleSession getByTrainer(UUID trainerUUID) {
        return trainerUUID != null ? BY_TRAINER.get(trainerUUID) : null;
    }

    public static boolean hasBattleForPlayer(UUID playerUUID) {
        return getByPlayer(playerUUID) != null;
    }

    public static boolean endBattle(ServerPlayer player, ActionBattleResult result) {
        if (player == null || result == null) return false;
        ActionBattleSession session = BY_PLAYER.get(player.getUUID());
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
        ActionBattleSession session = BY_PLAYER.get(playerUUID);
        if (session == null || !session.end(ActionBattleResult.INVALID)) return;
        ServerPlayer player = findServerPlayer(session);
        if (player != null) ActionBattleHudSync.hide(player);
        cleanupBattlePokemon(session);
        removeSession(session);
        DebugLog.log("[CobblemonNML] Action battle session invalidated. Battle=" + session.battleId());
    }

    public static void clearAll() {
        for (ActionBattleSession session : BY_PLAYER.values().toArray(ActionBattleSession[]::new)) {
            session.end(ActionBattleResult.INVALID);
            cleanupBattlePokemon(session);
        }
        BY_PLAYER.clear();
        BY_TRAINER.clear();
        POKEMON_BY_BATTLE.clear();
        PLAYER_ZONE_STATES.clear();
    }

    public static int size() {
        return BY_PLAYER.size();
    }

    public static boolean requestPlayerMoveHere(ServerPlayer player, double x, double y, double z) {
        if (player == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        ActionBattleSession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        if (!DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return false;
        if (!player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        UUID activePokemonId = session.playerActivePokemonUUID();
        UUID activeEntityId = session.playerActiveEntityUUID();
        if (activePokemonId == null || activeEntityId == null) return false;
        long currentTick = level.getGameTime();
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
        if (!pokemonEntity.getNavigation().moveTo(path, ACTION_MOVEMENT_SPEED)) {
            session.clearPlayerMoveTarget();
            return false;
        }
        session.startPokemonMovementCommandCooldown(activePokemonId, currentTick, MOVE_HERE_COOLDOWN_TICKS);
        long revision = session.replacePlayerMoveTarget(x, y, z);
        DebugLog.log("[CobblemonNML] Move Here accepted. Battle=" + session.battleId() + ", revision=" + revision + ", target=" + new Vec3(x, y, z) + ", cooldownTicks=" + MOVE_HERE_COOLDOWN_TICKS);
        return true;
    }

    public static boolean requestPlayerMove(ServerPlayer player, int moveSlot) {
        if (player == null || moveSlot < 0 || moveSlot > 3) return false;
        ActionBattleSession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        if (!DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return rejectMove(session, moveSlot, "inactive_dungeon_session");
        if (!(player.level() instanceof ServerLevel level) || !player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return rejectMove(session, moveSlot, "wrong_dimension");
        BattlePokemonRefs refs = POKEMON_BY_BATTLE.get(session.battleId());
        if (refs == null || refs.playerPokemon() == null) return rejectMove(session, moveSlot, "missing_player_pokemon_ref");
        Move move = refs.playerPokemon().getMoveSet().get(moveSlot);
        if (move == null) return rejectMove(session, moveSlot, "missing_move");
        if (!FightOrFlightAdapter.supports(move)) return rejectMove(session, moveSlot, "unsupported_move");
        if (!FightOrFlightAdapter.hasPp(move)) return rejectMove(session, moveSlot, "no_pp");
        long currentTick = level.getGameTime();
        if (session.isPokemonMoveOnCooldown(refs.playerPokemon().getUuid(), currentTick)) return rejectMove(session, moveSlot, "cooldown");
        UUID playerEntityId = session.playerActiveEntityUUID();
        UUID targetEntityId = session.trainerActiveEntityUUID();
        if (playerEntityId == null) return rejectMove(session, moveSlot, "missing_player_entity_id");
        if (targetEntityId == null) return rejectMove(session, moveSlot, "missing_target_entity_id");
        Entity rawPlayerPokemon = level.getEntity(playerEntityId);
        Entity rawTargetPokemon = level.getEntity(targetEntityId);
        if (!(rawPlayerPokemon instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) return rejectMove(session, moveSlot, "missing_player_entity");
        if (!(rawTargetPokemon instanceof PokemonEntity targetEntity) || targetEntity.isRemoved()) return rejectMove(session, moveSlot, "missing_target_entity");
        pokemonEntity.getNavigation().stop();
        session.clearPlayerMoveTarget();
        long revision = session.replacePlayerMoveCommand(moveSlot, targetEntityId);
        pursuePendingMove(session, pokemonEntity, targetEntity);
        DebugLog.log("[CobblemonNML] Move " + (moveSlot + 1) + " queued. Battle=" + session.battleId() + ", revision=" + revision + ", target=" + targetEntityId);
        return true;
    }

    private static boolean rejectMove(ActionBattleSession session, int moveSlot, String reason) {
        DebugLog.log("[CobblemonNML] Action move rejected. Battle=" + session.battleId() + ", slot=" + (moveSlot + 1) + ", reason=" + reason);
        return false;
    }

    public static boolean requestPlayerSwap(ServerPlayer player) {
        if (player == null) return false;
        ActionBattleSession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        if (!DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return rejectSwap(session, "inactive_dungeon_session");
        if (!(player.level() instanceof ServerLevel level) || !player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return rejectSwap(session, "wrong_dimension");
        long currentTick = level.getGameTime();
        if (session.isPlayerSwapOnCooldown(currentTick)) return rejectSwap(session, "cooldown");
        if (session.isPlayerSendOutPending()) return rejectSwap(session, "sendout_pending");
        BattlePokemonRefs refs = POKEMON_BY_BATTLE.get(session.battleId());
        if (refs == null || refs.playerPokemon() == null) return rejectSwap(session, "missing_player_pokemon_ref");
        int previousSlot = session.playerActivePartyIndex();
        SelectedPokemon next = findNextUsablePlayerPokemon(player, previousSlot);
        if (next == null) return rejectSwap(session, "no_available_replacement");
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        if (!(rawTrainer instanceof LivingEntity trainerEntity) || trainerEntity.isRemoved()) return rejectSwap(session, "missing_trainer_entity");
        Entity rawPlayerPokemon = session.playerActiveEntityUUID() != null ? level.getEntity(session.playerActiveEntityUUID()) : null;
        if (rawPlayerPokemon instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) {
            pokemonEntity.getNavigation().stop();
        }
        session.cancelPlayerOrders();
        session.startPlayerSwapCooldown(currentTick, SWAP_COOLDOWN_TICKS);
        session.setPlayerSendOutPending(true);
        Pokemon previous = refs.playerPokemon();
        recallPokemon(previous);
        session.clearPlayerActivePokemon();
        refs.setPlayerPokemon(next.pokemon());
        seedDamageFeedback(session, next.pokemon());
        sendOutPokemon(session, true, player, trainerEntity, next);
        DebugLog.log("[CobblemonNML] Player Swap Out accepted. Battle=" + session.battleId() + ", fromSlot=" + previousSlot + ", toSlot=" + next.slot() + ", cooldownTicks=" + SWAP_COOLDOWN_TICKS);
        return true;
    }

    private static boolean rejectSwap(ActionBattleSession session, String reason) {
        DebugLog.log("[CobblemonNML] Player Swap Out rejected. Battle=" + session.battleId() + ", reason=" + reason);
        return false;
    }

    public static void tickPlayerMovement(ServerPlayer player) {
        if (player == null) return;
        ActionBattleSession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return;
        if (!(player.level() instanceof ServerLevel level)) {
            session.cancelPlayerOrders();
            return;
        }
        observeDamageFeedback(session);
        List<ActionBattleDotEvent> effectTicks = ActionBattleEffectController.global().tickBattle(session.battleId(), level.getGameTime());
        applyEffectTicks(session, level, effectTicks);
        BattlePokemonRefs visualRefs = POKEMON_BY_BATTLE.get(session.battleId());
        if (visualRefs != null) ActionBattleStatusParticleController.tickBattle(session, level, visualRefs.playerPokemon(), visualRefs.trainerPokemon());
        observeDamageFeedback(session);
        if (handleFaintState(player, session, level)) return;
        tickPlayerBattleZone(session, player, level);
        if (level.getGameTime() % HUD_SYNC_INTERVAL_TICKS == 0L) syncHud(player, session);
        tickTrainerAi(session, level);
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
            BattlePokemonRefs refs = POKEMON_BY_BATTLE.get(session.battleId());
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
                    session.clearPlayerMoveCommand();
                    DebugLog.log("[CobblemonNML] Action move committed through Fight or Flight. Battle=" + session.battleId() + ", move=" + move.getName() + ", cooldownTicks=" + cooldownTicks);
                } else {
                    FightOrFlightAdapter.refundOnePp(move);
                    session.clearPlayerMoveCommand();
                }
                return;
            }
            pursuePendingMove(session, pokemonEntity, targetEntity);
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
        BattlePokemonRefs refs = POKEMON_BY_BATTLE.get(session.battleId());
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
        TrainerNPC runtimeTrainer = resolveRuntimeTrainer(session.runtimeTrainerId(), trainerEntity);
        if (runtimeTrainer == null) {
            invalidateBattle(player.getUUID());
            return true;
        }

        SelectedPokemon playerReplacement = playerFainted ? findNextUsablePlayerPokemon(player, session.playerActivePartyIndex()) : null;
        SelectedPokemon trainerReplacement = trainerFainted ? findNextUsableTrainerPokemon(runtimeTrainer, session.trainerActivePartyIndex()) : null;

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

        session.cancelPlayerOrders();
        session.cancelTrainerOrders();
        stopActivePlayerNavigation(session, level);
        stopActiveTrainerNavigation(session, level);
        if (playerFainted) {
            int previousSlot = session.playerActivePartyIndex();
            session.setPlayerSendOutPending(true);
            PokemonEntity oldPlayerEntity = playerPokemon != null ? playerPokemon.getEntity() : null;
            recallPokemon(playerPokemon);
            session.clearPlayerActivePokemon();
            refs.setPlayerPokemon(playerReplacement.pokemon());
            seedDamageFeedback(session, playerReplacement.pokemon());
            sendOutPokemon(session, true, player, trainerEntity, playerReplacement);
            DebugLog.log("[CobblemonNML] Player faint replacement started. Battle=" + session.battleId() + ", fromSlot=" + previousSlot + ", toSlot=" + playerReplacement.slot());
        }
        if (trainerFainted) {
            int previousSlot = session.trainerActivePartyIndex();
            session.setTrainerSendOutPending(true);
            recallPokemon(trainerPokemon);
            session.clearTrainerActivePokemon();
            refs.setTrainerPokemon(trainerReplacement.pokemon());
            seedDamageFeedback(session, trainerReplacement.pokemon());
            sendOutPokemon(session, false, trainerEntity, player, trainerReplacement);
            DebugLog.log("[CobblemonNML] Trainer faint replacement started. Battle=" + session.battleId() + ", fromSlot=" + previousSlot + ", toSlot=" + trainerReplacement.slot());
        }
        return true;
    }

    private static void stopActivePlayerNavigation(ActionBattleSession session, ServerLevel level) {
        UUID entityId = session.playerActiveEntityUUID();
        Entity raw = entityId != null ? level.getEntity(entityId) : null;
        if (raw instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) pokemonEntity.getNavigation().stop();
    }

    private static void stopActiveTrainerNavigation(ActionBattleSession session, ServerLevel level) {
        UUID entityId = session.trainerActiveEntityUUID();
        Entity raw = entityId != null ? level.getEntity(entityId) : null;
        if (raw instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) pokemonEntity.getNavigation().stop();
    }

    private static void tickTrainerAi(ActionBattleSession session, ServerLevel level) {
        if (session == null || level == null || session.state() != ActionBattleState.ACTIVE) return;
        if (session.isTrainerSendOutPending() || session.isPlayerSendOutPending()) return;
        BattlePokemonRefs refs = POKEMON_BY_BATTLE.get(session.battleId());
        if (refs == null || refs.trainerPokemon() == null || refs.playerPokemon() == null) return;
        Pokemon trainerPokemon = refs.trainerPokemon();
        if (trainerPokemon.isFainted() || refs.playerPokemon().isFainted()) return;
        UUID trainerEntityId = session.trainerActiveEntityUUID();
        UUID playerEntityId = session.playerActiveEntityUUID();
        if (trainerEntityId == null || playerEntityId == null) return;
        Entity rawTrainerPokemon = level.getEntity(trainerEntityId);
        Entity rawPlayerPokemon = level.getEntity(playerEntityId);
        if (!(rawTrainerPokemon instanceof PokemonEntity trainerPokemonEntity) || trainerPokemonEntity.isRemoved()) return;
        if (!(rawPlayerPokemon instanceof PokemonEntity playerPokemonEntity) || playerPokemonEntity.isRemoved()) {
            trainerPokemonEntity.getNavigation().stop();
            session.cancelTrainerOrders();
            return;
        }

        long currentTick = level.getGameTime();
        if (session.trainerRepositionAttempt() >= ActionBattleTrainerTactics.maxRepositionAttempts()) {
            handleExhaustedTrainerReposition(session, level, refs, trainerPokemon, trainerPokemonEntity, currentTick);
            return;
        }
        if (!session.hasTrainerMoveCommand()) {
            int moveSlot = selectTrainerMoveSlot(trainerPokemon, refs.playerPokemon(), trainerPokemonEntity, playerPokemonEntity);
            if (moveSlot < 0) return;
            long revision = session.replaceTrainerMoveCommand(moveSlot, playerEntityId);
            DebugLog.log("[CobblemonNML] Trainer AI move " + (moveSlot + 1) + " queued. Battle=" + session.battleId() + ", revision=" + revision + ", target=" + playerEntityId);
        }

        if (!playerEntityId.equals(session.trainerMoveTargetEntityUUID())) {
            trainerPokemonEntity.getNavigation().stop();
            session.cancelTrainerOrders();
            return;
        }
        Move move = trainerPokemon.getMoveSet().get(session.trainerMoveSlot());
        if (move == null || !FightOrFlightAdapter.supports(move) || !FightOrFlightAdapter.hasPp(move)) {
            trainerPokemonEntity.getNavigation().stop();
            session.cancelTrainerOrders();
            return;
        }
        boolean onCooldown = session.isPokemonMoveOnCooldown(trainerPokemon.getUuid(), currentTick);
        if (!onCooldown && FightOrFlightAdapter.canCommit(trainerPokemonEntity, playerPokemonEntity, move)) {
            trainerPokemonEntity.getNavigation().stop();
            if (!FightOrFlightAdapter.consumeOnePp(move)) {
                session.cancelTrainerOrders();
                return;
            }
            if (FightOrFlightAdapter.execute(trainerPokemonEntity, playerPokemonEntity, move)) {
                long cooldownTicks = FightOrFlightAdapter.cooldownTicks(move);
                session.startPokemonMoveCooldown(trainerPokemon.getUuid(), currentTick, cooldownTicks);
                session.clearTrainerMoveCommand();
                session.resetTrainerRepositionState();
                DebugLog.log("[CobblemonNML] Trainer AI move committed through Fight or Flight. Battle=" + session.battleId() + ", move=" + move.getName() + ", cooldownTicks=" + cooldownTicks);
            } else {
                FightOrFlightAdapter.refundOnePp(move);
                session.cancelTrainerOrders();
            }
            return;
        }
        repositionTrainerPendingMove(session, trainerPokemon, trainerPokemonEntity, playerPokemonEntity, move, onCooldown, currentTick);
    }

    private static void handleExhaustedTrainerReposition(ActionBattleSession session, ServerLevel level, BattlePokemonRefs refs,
                                                          Pokemon trainerPokemon, PokemonEntity trainerPokemonEntity, long currentTick) {
        trainerPokemonEntity.getNavigation().stop();
        session.clearTrainerMoveCommand();
        if (session.isTrainerSwapOnCooldown(currentTick)) {
            session.resetTrainerRepositionState();
            DebugLog.log("[CobblemonNML] Trainer voluntary swap is on cooldown; continuing reposition attempts. Battle=" + session.battleId());
            return;
        }
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        ServerPlayer player = findServerPlayer(session);
        if (!(rawTrainer instanceof LivingEntity trainerEntity) || trainerEntity.isRemoved() || player == null || player.level() != level) {
            session.resetTrainerRepositionState();
            return;
        }
        TrainerNPC runtimeTrainer = resolveRuntimeTrainer(session.runtimeTrainerId(), trainerEntity);
        if (runtimeTrainer == null) {
            session.resetTrainerRepositionState();
            return;
        }
        int currentScore = trainerSwapScore(trainerPokemon, refs.playerPokemon());
        SelectedPokemon replacement = findBetterTrainerSwapCandidate(runtimeTrainer, session.trainerActivePartyIndex(), currentScore, refs.playerPokemon());
        if (replacement == null) {
            session.resetTrainerRepositionState();
            DebugLog.log("[CobblemonNML] Trainer AI found no meaningfully better voluntary swap; continuing reposition attempts. Battle=" + session.battleId());
            return;
        }
        int previousSlot = session.trainerActivePartyIndex();
        session.setTrainerSendOutPending(true);
        session.cancelTrainerOrders();
        recallPokemon(trainerPokemon);
        session.clearTrainerActivePokemon();
        refs.setTrainerPokemon(replacement.pokemon());
        seedDamageFeedback(session, replacement.pokemon());
        sendOutPokemon(session, false, trainerEntity, player, replacement);
        session.startTrainerSwapCooldown(currentTick, SWAP_COOLDOWN_TICKS);
        DebugLog.log("[CobblemonNML] Trainer voluntary swap started. Battle=" + session.battleId() + ", fromSlot=" + previousSlot
                + ", toSlot=" + replacement.slot() + ", cooldownTicks=" + SWAP_COOLDOWN_TICKS);
    }

    private static SelectedPokemon findBetterTrainerSwapCandidate(TrainerNPC trainer, int currentIndex, int currentScore, Pokemon targetPokemon) {
        if (trainer == null) return null;
        Pokemon[] team = trainer.getTeam();
        if (team == null || team.length == 0) return null;
        int tier = trainerAiTier();
        for (int offset = 1; offset < team.length; offset++) {
            int slot = Math.floorMod(currentIndex + offset, team.length);
            Pokemon candidate = team[slot];
            if (candidate == null || candidate.isFainted()) continue;
            int engagement = trainerEngagementScore(candidate);
            double hpRatio = pokemonHpRatio(candidate);
            double typeMultiplier = bestTypeMultiplier(candidate, targetPokemon);
            int candidateScore = ActionBattleTrainerAiTier.swapScore(tier, engagement, hpRatio, typeMultiplier);
            if (ActionBattleTrainerTactics.isMeaningfullyBetter(currentScore, candidateScore)) {
                if (tier >= 2) {
                    DebugLog.log("[CobblemonNML] Trainer AI swap evaluation. Tier=" + tier + ", currentScore=" + currentScore
                            + ", candidateScore=" + candidateScore + ", candidateSlot=" + slot + ", engagement=" + engagement
                            + ", hpRatio=" + hpRatio + ", bestTypeMultiplier=" + typeMultiplier + ", selected=true");
                }
                return new SelectedPokemon(slot, candidate);
            }
        }
        return null;
    }

    private static int trainerEngagementScore(Pokemon pokemon) {
        if (pokemon == null) return 0;
        boolean hasRanged = false;
        boolean hasMelee = false;
        for (int slot = 0; slot < 4; slot++) {
            Move move = pokemon.getMoveSet().get(slot);
            if (move == null || !FightOrFlightAdapter.supports(move) || !FightOrFlightAdapter.hasPp(move)) continue;
            if (FightOrFlightAdapter.isRangedMove(move)) hasRanged = true;
            else hasMelee = true;
        }
        return ActionBattleTrainerTactics.engagementScore(hasRanged, hasMelee);
    }

    private static int trainerAiTier() {
        var tier = DungeonSession.getTier();
        return tier != null ? tier.ordinal() + 1 : 1;
    }

    private static double pokemonHpRatio(Pokemon pokemon) {
        if (pokemon == null || pokemon.getMaxHealth() <= 0) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, pokemon.getCurrentHealth() / (double) pokemon.getMaxHealth()));
    }

    private static double moveTypeMultiplier(Move move, Pokemon targetPokemon) {
        if (move == null || targetPokemon == null) return 1.0D;
        String attack = move.getType().getName();
        String primary = targetPokemon.getPrimaryType().getName();
        String secondary = targetPokemon.getSecondaryType() != null ? targetPokemon.getSecondaryType().getName() : null;
        return ActionBattleTrainerAiTier.typeMultiplier(attack, primary, secondary);
    }

    private static double bestTypeMultiplier(Pokemon pokemon, Pokemon targetPokemon) {
        if (pokemon == null || targetPokemon == null) return 1.0D;
        double best = 0.0D;
        for (int slot = 0; slot < 4; slot++) {
            Move move = pokemon.getMoveSet().get(slot);
            if (move == null || !FightOrFlightAdapter.supports(move) || !FightOrFlightAdapter.hasPp(move)) continue;
            best = Math.max(best, moveTypeMultiplier(move, targetPokemon));
        }
        return best > 0.0D ? best : 1.0D;
    }

    private static int trainerSwapScore(Pokemon pokemon, Pokemon targetPokemon) {
        return ActionBattleTrainerAiTier.swapScore(trainerAiTier(), trainerEngagementScore(pokemon), pokemonHpRatio(pokemon), bestTypeMultiplier(pokemon, targetPokemon));
    }

    private static int selectTrainerMoveSlot(Pokemon trainerPokemon, Pokemon targetPokemon, PokemonEntity trainerEntity, PokemonEntity targetEntity) {
        if (trainerPokemon == null) return -1;
        List<Integer> usableSlots = new ArrayList<>(4);
        for (int slot = 0; slot < 4; slot++) {
            Move move = trainerPokemon.getMoveSet().get(slot);
            if (move != null && FightOrFlightAdapter.supports(move) && FightOrFlightAdapter.hasPp(move)) usableSlots.add(slot);
        }
        if (usableSlots.isEmpty()) return -1;
        int tier = trainerAiTier();
        if (tier <= 1) return usableSlots.get(ThreadLocalRandom.current().nextInt(usableSlots.size()));
        int bestScore = Integer.MIN_VALUE;
        List<Integer> bestSlots = new ArrayList<>(4);
        double hpRatio = pokemonHpRatio(trainerPokemon);
        for (int slot : usableSlots) {
            Move move = trainerPokemon.getMoveSet().get(slot);
            boolean canCommitNow = FightOrFlightAdapter.canCommit(trainerEntity, targetEntity, move);
            double multiplier = tier >= 3 ? moveTypeMultiplier(move, targetPokemon) : 1.0D;
            int score = ActionBattleTrainerAiTier.moveScore(tier, canCommitNow, multiplier,
                    FightOrFlightAdapter.movePower(move), FightOrFlightAdapter.movePriority(move), hpRatio);
            if (score > bestScore) {
                bestScore = score;
                bestSlots.clear();
                bestSlots.add(slot);
            } else if (score == bestScore) {
                bestSlots.add(slot);
            }
        }
        int selectedSlot = bestSlots.get(ThreadLocalRandom.current().nextInt(bestSlots.size()));
        Move selectedMove = trainerPokemon.getMoveSet().get(selectedSlot);
        boolean selectedCanCommitNow = FightOrFlightAdapter.canCommit(trainerEntity, targetEntity, selectedMove);
        double selectedMultiplier = tier >= 3 ? moveTypeMultiplier(selectedMove, targetPokemon) : 1.0D;
        int selectedPower = FightOrFlightAdapter.movePower(selectedMove);
        int selectedPriority = FightOrFlightAdapter.movePriority(selectedMove);
        int selectedScore = ActionBattleTrainerAiTier.moveScore(tier, selectedCanCommitNow, selectedMultiplier,
                selectedPower, selectedPriority, hpRatio);
        DebugLog.log("[CobblemonNML] Trainer AI decision. Tier=" + tier + ", move=" + selectedMove.getName()
                + ", slot=" + (selectedSlot + 1) + ", score=" + selectedScore + ", canCommitNow=" + selectedCanCommitNow
                + ", typeMultiplier=" + selectedMultiplier + ", power=" + selectedPower + ", priority=" + selectedPriority
                + ", hpRatio=" + hpRatio);
        return selectedSlot;
    }

    private static void repositionTrainerPendingMove(ActionBattleSession session, Pokemon trainerPokemon, PokemonEntity trainerPokemonEntity, PokemonEntity playerPokemonEntity, Move move, boolean onCooldown, long currentTick) {
        int attempt = session.trainerRepositionAttempt();
        if (attempt >= ActionBattleTrainerTactics.maxRepositionAttempts()) {
            trainerPokemonEntity.getNavigation().stop();
            return;
        }
        if (session.hasTrainerRepositionTarget()) {
            double dx = trainerPokemonEntity.getX() - session.trainerRepositionTargetX();
            double dy = trainerPokemonEntity.getY() - session.trainerRepositionTargetY();
            double dz = trainerPokemonEntity.getZ() - session.trainerRepositionTargetZ();
            boolean reached = dx * dx + dy * dy + dz * dz <= 2.25D;
            if (reached) {
                trainerPokemonEntity.getNavigation().stop();
                if (onCooldown) return;
                failTrainerRepositionAttempt(session, trainerPokemonEntity, "position reached without a valid attack angle");
                return;
            }
            if (trainerPokemonEntity.getNavigation().isDone()) {
                failTrainerRepositionAttempt(session, trainerPokemonEntity, "navigation stopped before reaching tactical position");
            }
            return;
        }

        if (session.isPokemonMovementCommandOnCooldown(trainerPokemon.getUuid(), currentTick)) return;

        ActionBattleTrainerTactics.Point[] candidates = ActionBattleTrainerTactics.repositionCandidates(
                trainerPokemonEntity.getX(), trainerPokemonEntity.getZ(),
                playerPokemonEntity.getX(), playerPokemonEntity.getZ(), FightOrFlightAdapter.isRangedMove(move));
        ActionBattleTrainerTactics.Point candidate = candidates[attempt];
        BlockPos targetPos = BlockPos.containing(candidate.x(), playerPokemonEntity.getY(), candidate.z());
        Path path = trainerPokemonEntity.getNavigation().createPath(targetPos, 0);
        if (path == null || !path.canReach()) {
            failTrainerRepositionAttempt(session, trainerPokemonEntity, "candidate was unreachable");
            return;
        }
        if (!trainerPokemonEntity.getNavigation().moveTo(path, ACTION_MOVEMENT_SPEED)) {
            failTrainerRepositionAttempt(session, trainerPokemonEntity, "navigation refused tactical position");
            return;
        }
        session.setTrainerRepositionTarget(candidate.x(), playerPokemonEntity.getY(), candidate.z());
        session.startPokemonMovementCommandCooldown(trainerPokemon.getUuid(), currentTick, MOVE_HERE_COOLDOWN_TICKS);
        DebugLog.log("[CobblemonNML] Trainer AI reposition attempt " + (attempt + 1) + "/" + ActionBattleTrainerTactics.maxRepositionAttempts()
                + " started. Battle=" + session.battleId() + ", target=(" + candidate.x() + ", " + playerPokemonEntity.getY() + ", " + candidate.z() + "), moveHereCooldownTicks=" + MOVE_HERE_COOLDOWN_TICKS);
    }

    private static void failTrainerRepositionAttempt(ActionBattleSession session, PokemonEntity trainerPokemonEntity, String reason) {
        trainerPokemonEntity.getNavigation().stop();
        int attempts = session.advanceTrainerRepositionAttempt();
        DebugLog.log("[CobblemonNML] Trainer AI reposition attempt " + attempts + "/" + ActionBattleTrainerTactics.maxRepositionAttempts()
                + " failed. Battle=" + session.battleId() + ", reason=" + reason);
        if (attempts >= ActionBattleTrainerTactics.maxRepositionAttempts()) {
            session.clearTrainerMoveCommand();
            DebugLog.log("[CobblemonNML] Trainer AI exhausted reposition attempts. Battle=" + session.battleId());
        }
    }

    private static void pursuePendingMove(ActionBattleSession session, PokemonEntity pokemonEntity, PokemonEntity targetEntity) {
        BattlePokemonRefs refs = POKEMON_BY_BATTLE.get(session.battleId());
        Move move = refs != null && refs.playerPokemon() != null && session.playerMoveSlot() >= 0
                ? refs.playerPokemon().getMoveSet().get(session.playerMoveSlot()) : null;
        if (move != null && FightOrFlightAdapter.canCommit(pokemonEntity, targetEntity, move)) {
            pokemonEntity.getNavigation().stop();
            return;
        }
        Path path = pokemonEntity.getNavigation().createPath(targetEntity, 0);
        if (path == null || !path.canReach()) {
            pokemonEntity.getNavigation().stop();
            session.clearPlayerMoveCommand();
            DebugLog.log("[CobblemonNML] Pending move cancelled because opponent is unreachable. Battle=" + session.battleId());
            return;
        }
        pokemonEntity.getNavigation().moveTo(path, ACTION_MOVEMENT_SPEED);
    }

    private static void syncHud(ServerPlayer player, ActionBattleSession session) {
        BattlePokemonRefs refs = POKEMON_BY_BATTLE.get(session.battleId());
        if (refs == null || refs.playerPokemon() == null || refs.trainerPokemon() == null) return;
        ActionBattleHudSync.send(player, session, refs.playerPokemon(), refs.trainerPokemon());
    }

    private static ServerPlayer findServerPlayer(ActionBattleSession session) {
        if (session == null) return null;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayer(session.playerUUID()) : null;
    }

    private static TrainerNPC resolveRuntimeTrainer(String runtimeTrainerId, LivingEntity trainerEntity) {
        try {
            TrainerRegistry registry = TBCS.getInstance().getTrainerRegistry();
            TrainerNPC runtimeTrainer = registry.getById(runtimeTrainerId, TrainerNPC.class);
            if (runtimeTrainer == null) {
                DebugLog.log("[CobblemonNML] Runtime action trainer does not exist: " + runtimeTrainerId);
                return null;
            }
            runtimeTrainer.setEntity(trainerEntity);
            return runtimeTrainer;
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] Could not resolve runtime trainer for action battle: " + runtimeTrainerId);
            exception.printStackTrace();
            return null;
        }
    }

    private static SelectedPokemon findFirstUsablePlayerPokemon(ServerPlayer player) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon != null && !pokemon.isFainted()) return new SelectedPokemon(slot, pokemon);
        }
        return null;
    }

    private static SelectedPokemon findNextUsablePlayerPokemon(ServerPlayer player, int currentIndex) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        boolean[] usable = new boolean[party.size()];
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            usable[slot] = pokemon != null && !pokemon.isFainted();
        }
        int nextIndex = ActionPartyCycle.nextUsableIndex(currentIndex, usable);
        if (nextIndex < 0) return null;
        Pokemon pokemon = party.get(nextIndex);
        return pokemon != null ? new SelectedPokemon(nextIndex, pokemon) : null;
    }

    private static SelectedPokemon findFirstUsableTrainerPokemon(TrainerNPC trainer) {
        Pokemon[] team = trainer.getTeam();
        for (int slot = 0; slot < team.length; slot++) {
            Pokemon pokemon = team[slot];
            if (pokemon != null && !pokemon.isFainted()) return new SelectedPokemon(slot, pokemon);
        }
        return null;
    }

    private static SelectedPokemon findNextUsableTrainerPokemon(TrainerNPC trainer, int currentIndex) {
        Pokemon[] team = trainer.getTeam();
        boolean[] usable = new boolean[team.length];
        for (int slot = 0; slot < team.length; slot++) {
            Pokemon pokemon = team[slot];
            usable[slot] = pokemon != null && !pokemon.isFainted();
        }
        int nextIndex = ActionPartyCycle.nextUsableIndex(currentIndex, usable);
        if (nextIndex < 0) return null;
        Pokemon pokemon = team[nextIndex];
        return pokemon != null ? new SelectedPokemon(nextIndex, pokemon) : null;
    }

    private static void sendOutPokemon(ActionBattleSession session, boolean playerSide, LivingEntity source, LivingEntity opponent, SelectedPokemon selected) {
        Pokemon pokemon = selected.pokemon();
        ServerLevel level = (ServerLevel) source.level();
        PokemonEntity existing = pokemon.getEntity();
        if (existing != null && !existing.isRemoved() && existing.level() == level) {
            bindActivePokemon(session, playerSide, selected.slot(), pokemon, existing);
            return;
        }
        if (existing != null) recallPokemon(pokemon);
        Vec3 sendOutPosition = calculateSendOutPosition(source, opponent);
        CompletableFuture<PokemonEntity> future = ActionBattlePokemonControlGuard.callInternal(() -> pokemon.sendOutWithAnimation(
                source, level, sendOutPosition, null, true, null, entity -> Unit.INSTANCE
        ));
        future.whenComplete((entity, throwable) -> {
            if (throwable != null || entity == null) {
                DebugLog.log("[CobblemonNML] Action battle Pokemon send-out failed for battle " + session.battleId());
                if (throwable != null) throwable.printStackTrace();
                if (isCurrentSession(session)) invalidateBattle(session.playerUUID());
                return;
            }
            if (!isCurrentSession(session) || session.state() != ActionBattleState.ACTIVE) {
                recallPokemon(pokemon);
                return;
            }
            if (!bindActivePokemon(session, playerSide, selected.slot(), pokemon, entity)) {
                recallPokemon(pokemon);
            }
        });
    }

    private static boolean bindActivePokemon(ActionBattleSession session, boolean playerSide, int partyIndex, Pokemon pokemon, PokemonEntity entity) {
        boolean bound = playerSide
                ? session.bindPlayerActivePokemon(partyIndex, pokemon.getUuid(), entity.getUUID())
                : session.bindTrainerActivePokemon(partyIndex, pokemon.getUuid(), entity.getUUID());
        if (bound) {
            if (playerSide) session.setPlayerSendOutPending(false);
            else session.setTrainerSendOutPending(false);
            if (entity.level() instanceof ServerLevel level) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerUUID());
                if (player != null && player.level() == level && session.battleZone().contains(player.getX(), player.getZ())) {
                    suppressAutonomousMovementNow(session, entity);
                }
            }
            DebugLog.log("[CobblemonNML] Action battle " + (playerSide ? "player" : "trainer") + " Pokemon active. Battle=" + session.battleId() + ", slot=" + partyIndex + ", pokemon=" + pokemon.getUuid() + ", entity=" + entity.getUUID());
            if (session.playerActiveEntityUUID() != null && session.trainerActiveEntityUUID() != null) {
                DebugLog.log("[CobblemonNML] Action battle combatants ready. Battle=" + session.battleId());
            }
        }
        return bound;
    }

    public static boolean shouldSuppressAutonomousMovement(PokemonEntity pokemonEntity) {
        if (pokemonEntity == null || pokemonEntity.isRemoved() || !(pokemonEntity.level() instanceof ServerLevel level)) return false;
        ActionBattleSession session = findSessionForBattlePokemonEntity(pokemonEntity.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerUUID());
        if (player == null || player.level() != level) return false;
        return session.battleZone().contains(player.getX(), player.getZ());
    }

    public static ActionBattleSession findSessionForBattlePokemonEntity(UUID entityUUID) {
        if (entityUUID == null) return null;
        for (ActionBattleSession session : BY_PLAYER.values()) {
            if (entityUUID.equals(session.playerActiveEntityUUID()) || entityUUID.equals(session.trainerActiveEntityUUID())) return session;
        }
        return null;
    }

    public static UUID battleIdForPokemonEntity(UUID entityUUID) {
        ActionBattleSession session = findSessionForBattlePokemonEntity(entityUUID);
        return session != null ? session.battleId() : null;
    }

    private static void applyEffectTicks(ActionBattleSession session, ServerLevel level, List<ActionBattleDotEvent> events) {
        if (session == null || level == null || events == null || events.isEmpty()) return;
        for (ActionBattleDotEvent event : events) {
            Pokemon pokemon = findBattlePokemon(session, level, event.pokemonUUID());
            if (pokemon == null || pokemon.isFainted()) continue;
            int maxHealth = Math.max(1, pokemon.getMaxHealth());
            int beforeHealth = pokemon.getCurrentHealth();
            int damage = ActionBattleDotDamage.calculate(maxHealth, pokemon.getCurrentHealth(), event.maxHealthFraction());
            int newHealth = Math.max(event.canKo() ? 0 : 1, beforeHealth - damage);
            pokemon.setCurrentHealth(newHealth);
            ActionBattleDamageFeedbackController.global().recordDamage(session.battleId(), pokemon.getUuid(), beforeHealth, newHealth, ActionBattleDamageFeedbackCategory.DOT);
            if (event.status() == ActionBattleStatus.BURN) {
                PokemonEntity entity = pokemon.getEntity();
                if (entity != null && !entity.isRemoved() && entity.level() == level) ActionBattleStatusParticleController.emitBurnDotBurst(level, entity);
            }
            DebugLog.log("[CobblemonNML] Action battle DOT tick. Battle=" + session.battleId() + ", status=" + event.status()
                    + ", pokemon=" + event.pokemonUUID() + ", damage=" + damage + ", hp=" + newHealth + "/" + maxHealth);
        }
    }

    private static Pokemon findBattlePokemon(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        if (session == null || level == null || pokemonUUID == null) return null;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerUUID());
        if (player != null) {
            PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
            for (int slot = 0; slot < party.size(); slot++) {
                Pokemon pokemon = party.get(slot);
                if (pokemon != null && pokemonUUID.equals(pokemon.getUuid())) return pokemon;
            }
        }
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        if (rawTrainer instanceof LivingEntity trainerEntity) {
            TrainerNPC trainer = resolveRuntimeTrainer(session.runtimeTrainerId(), trainerEntity);
            if (trainer != null) {
                for (Pokemon pokemon : trainer.getTeam()) {
                    if (pokemon != null && pokemonUUID.equals(pokemon.getUuid())) return pokemon;
                }
            }
        }
        return null;
    }


    private static void seedDamageFeedback(ActionBattleSession session, Pokemon pokemon) {
        if (session == null || pokemon == null) return;
        ActionBattleDamageFeedbackController.global().seedPokemon(session.battleId(), pokemon.getUuid(), pokemon.getCurrentHealth());
    }

    private static void observeDamageFeedback(ActionBattleSession session) {
        if (session == null) return;
        BattlePokemonRefs refs = POKEMON_BY_BATTLE.get(session.battleId());
        if (refs == null) return;
        ActionBattleDamageFeedbackController feedback = ActionBattleDamageFeedbackController.global();
        if (refs.playerPokemon() != null) feedback.observePokemon(session.battleId(), refs.playerPokemon().getUuid(), refs.playerPokemon().getCurrentHealth());
        if (refs.trainerPokemon() != null) feedback.observePokemon(session.battleId(), refs.trainerPokemon().getUuid(), refs.trainerPokemon().getCurrentHealth());
    }

    private static void tickPlayerBattleZone(ActionBattleSession session, ServerPlayer player, ServerLevel level) {
        if (session == null || player == null) return;
        boolean inside = session.battleZone().contains(player.getX(), player.getZ());
        Boolean previous = PLAYER_ZONE_STATES.put(session.battleId(), inside);
        if (previous == null || previous.booleanValue() != inside) {
            if (inside) {
                suppressActiveBattlePokemonBrains(session, level);
                DebugLog.log("[CobblemonNML] Player entered battle zone; autonomous battle Pokemon brain movement suppressed. Battle=" + session.battleId());
            } else {
                clearBattlePokemonPathCooldown(session.playerActiveEntityUUID(), level);
                clearBattlePokemonPathCooldown(session.trainerActiveEntityUUID(), level);
                DebugLog.log("[CobblemonNML] Player left battle zone; normal Cobblemon Pokemon brain movement restored. Battle=" + session.battleId());
            }
        }
    }

    private static void suppressActiveBattlePokemonBrains(ActionBattleSession session, ServerLevel level) {
        UUID playerEntityId = session.playerActiveEntityUUID();
        Entity playerEntity = playerEntityId != null ? level.getEntity(playerEntityId) : null;
        if (playerEntity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) suppressAutonomousMovementNow(session, pokemonEntity);
        UUID trainerEntityId = session.trainerActiveEntityUUID();
        Entity trainerEntity = trainerEntityId != null ? level.getEntity(trainerEntityId) : null;
        if (trainerEntity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) suppressAutonomousMovementNow(session, pokemonEntity);
    }

    private static void clearBattlePokemonPathCooldown(UUID entityUUID, ServerLevel level) {
        Entity rawEntity = entityUUID != null ? level.getEntity(entityUUID) : null;
        if (rawEntity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) {
            pokemonEntity.getBrain().eraseMemory(CobblemonMemories.PATH_COOLDOWN);
        }
    }

    private static void suppressAutonomousMovementNow(ActionBattleSession session, PokemonEntity pokemonEntity) {
        pokemonEntity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        pokemonEntity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        UUID entityUUID = pokemonEntity.getUUID();
        boolean explicitMovement = entityUUID.equals(session.playerActiveEntityUUID())
                ? session.hasPlayerMoveCommand() || session.hasPlayerMoveTarget()
                : entityUUID.equals(session.trainerActiveEntityUUID()) && session.hasTrainerMoveCommand();
        if (!explicitMovement) pokemonEntity.getNavigation().stop();
    }

    private static Vec3 calculateSendOutPosition(LivingEntity source, LivingEntity opponent) {
        Vec3 delta = opponent.position().subtract(source.position());
        Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
        if (horizontal.lengthSqr() < 0.0001D) {
            Vec3 look = source.getLookAngle();
            horizontal = new Vec3(look.x, 0.0D, look.z);
        }
        if (horizontal.lengthSqr() < 0.0001D) horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        return source.position().add(horizontal.normalize().scale(1.75D)).add(0.0D, 0.15D, 0.0D);
    }

    private static boolean isCurrentSession(ActionBattleSession session) {
        return session != null && BY_PLAYER.get(session.playerUUID()) == session && BY_TRAINER.get(session.trainerUUID()) == session;
    }

    private static void cleanupBattlePokemon(ActionBattleSession session) {
        ActionBattleEffectController.global().clearBattle(session.battleId());
        ActionBattleDamageFeedbackController.global().clearBattle(session.battleId());
        BattlePokemonRefs refs = POKEMON_BY_BATTLE.remove(session.battleId());
        if (refs != null) {
            PokemonEntity playerEntity = refs.playerPokemon() != null ? refs.playerPokemon().getEntity() : null;
            recallPokemon(refs.playerPokemon());
            recallPokemon(refs.trainerPokemon());
        }
        session.cancelPlayerOrders();
        session.cancelTrainerOrders();
        session.setPlayerSendOutPending(false);
        session.setTrainerSendOutPending(false);
        session.clearPlayerActivePokemon();
        session.clearTrainerActivePokemon();
    }

    private static void recallPokemon(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            ActionBattlePokemonControlGuard.runInternal(pokemon::recall);
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] Failed to recall action battle Pokemon " + pokemon.getUuid());
            exception.printStackTrace();
        }
    }

    private static void removeSession(ActionBattleSession session) {
        BY_PLAYER.remove(session.playerUUID(), session);
        BY_TRAINER.remove(session.trainerUUID(), session);
        POKEMON_BY_BATTLE.remove(session.battleId());
        PLAYER_ZONE_STATES.remove(session.battleId());
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

    private record SelectedPokemon(int slot, Pokemon pokemon) {}

    private static final class BattlePokemonRefs {
        private Pokemon playerPokemon;
        private Pokemon trainerPokemon;

        private BattlePokemonRefs(Pokemon playerPokemon, Pokemon trainerPokemon) {
            this.playerPokemon = playerPokemon;
            this.trainerPokemon = trainerPokemon;
        }

        private Pokemon playerPokemon() { return playerPokemon; }
        private Pokemon trainerPokemon() { return trainerPokemon; }
        private void setPlayerPokemon(Pokemon pokemon) { playerPokemon = pokemon; }
        private void setTrainerPokemon(Pokemon pokemon) { trainerPokemon = pokemon; }
    }
}

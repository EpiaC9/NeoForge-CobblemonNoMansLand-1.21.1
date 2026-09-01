package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.CobblemonMemories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatRules;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ActionBattleMovementController {
    static final double ACTION_MOVEMENT_SPEED = 0.6D;
    private static final Map<UUID, Boolean> PLAYER_ZONE_STATES = new HashMap<>();

    private ActionBattleMovementController() {}

    static void tickPlayerBattleZone(ActionBattleSession session, ServerPlayer player, ServerLevel level) {
        if (session == null || player == null || level == null) return;
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

    static boolean shouldSuppressAutonomousMovement(PokemonEntity pokemonEntity) {
        if (pokemonEntity == null || pokemonEntity.isRemoved() || !(pokemonEntity.level() instanceof ServerLevel level)) return false;
        ActionBattleSession session = ActionBattleRegistry.findByPokemonEntity(pokemonEntity.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE) return false;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerUUID());
        if (player == null || player.level() != level) return false;
        return session.battleZone().contains(player.getX(), player.getZ());
    }

    static void suppressAutonomousMovementNow(ActionBattleSession session, PokemonEntity pokemonEntity) {
        if (session == null || pokemonEntity == null || pokemonEntity.isRemoved()) return;
        pokemonEntity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        pokemonEntity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        UUID entityUUID = pokemonEntity.getUUID();
        boolean explicitMovement = entityUUID.equals(session.playerActiveEntityUUID())
                ? session.hasPlayerMoveCommand() || session.hasPlayerMoveTarget()
                : entityUUID.equals(session.trainerActiveEntityUUID()) && session.hasTrainerMoveCommand();
        if (!explicitMovement) pokemonEntity.getNavigation().stop();
    }

    static void stopActivePlayerNavigation(ActionBattleSession session, ServerLevel level) {
        stopNavigation(session != null ? session.playerActiveEntityUUID() : null, level);
    }

    static void stopActiveTrainerNavigation(ActionBattleSession session, ServerLevel level) {
        stopNavigation(session != null ? session.trainerActiveEntityUUID() : null, level);
    }

    static void pursuePlayerPendingMove(ActionBattleSession session, PokemonEntity pokemonEntity, PokemonEntity targetEntity) {
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        var move = refs != null && refs.playerPokemon() != null && session.playerMoveSlot() >= 0
                ? refs.playerPokemon().getMoveSet().get(session.playerMoveSlot()) : null;
        if (move != null && net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter.canCommit(pokemonEntity, targetEntity, move)) {
            pokemonEntity.getNavigation().stop();
            return;
        }
        var tracked = ActionBattleEvasionController.trackedPosition(targetEntity, pokemonEntity.level().getGameTime());
        Path path = pokemonEntity.getNavigation().createPath(BlockPos.containing(tracked), 0);
        if (path == null || !path.canReach()) {
            pokemonEntity.getNavigation().stop();
            session.clearPlayerMoveCommand();
            DebugLog.log("[CobblemonNML] Pending move cancelled because opponent is unreachable. Battle=" + session.battleId());
            return;
        }
        pokemonEntity.getNavigation().moveTo(path, movementSpeed(session, pokemonEntity.getPokemon().getUuid(), pokemonEntity.level().getGameTime()));
    }


    static double movementSpeed(ActionBattleSession session, UUID pokemonUUID, long currentTick) {
        if (session == null || pokemonUUID == null || currentTick < 0L) return ACTION_MOVEMENT_SPEED;
        int stage = ActionBattleEffectController.global().effectiveStage(session.battleId(), pokemonUUID, ActionBattleStat.SPEED, currentTick);
        return ACTION_MOVEMENT_SPEED * ActionBattleStatRules.standardMultiplier(stage);
    }

    static void removeBattle(UUID battleId) {
        if (battleId != null) PLAYER_ZONE_STATES.remove(battleId);
    }

    static void clearAll() {
        PLAYER_ZONE_STATES.clear();
    }

    private static void suppressActiveBattlePokemonBrains(ActionBattleSession session, ServerLevel level) {
        Entity playerEntity = session.playerActiveEntityUUID() != null ? level.getEntity(session.playerActiveEntityUUID()) : null;
        if (playerEntity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) suppressAutonomousMovementNow(session, pokemonEntity);
        Entity trainerEntity = session.trainerActiveEntityUUID() != null ? level.getEntity(session.trainerActiveEntityUUID()) : null;
        if (trainerEntity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) suppressAutonomousMovementNow(session, pokemonEntity);
    }

    private static void clearBattlePokemonPathCooldown(UUID entityUUID, ServerLevel level) {
        Entity rawEntity = entityUUID != null && level != null ? level.getEntity(entityUUID) : null;
        if (rawEntity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) pokemonEntity.getBrain().eraseMemory(CobblemonMemories.PATH_COOLDOWN);
    }

    private static void stopNavigation(UUID entityUUID, ServerLevel level) {
        Entity raw = entityUUID != null && level != null ? level.getEntity(entityUUID) : null;
        if (raw instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved()) pokemonEntity.getNavigation().stop();
    }
}

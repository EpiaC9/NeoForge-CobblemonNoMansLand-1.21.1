package net.epiac9.cobblemonnml.battle.action.typeeffect;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleDrowsyController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSleepController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundVisualSync;
import net.epiac9.cobblemonnml.battle.action.projectile.wave.ActionBattleWaveServerRuntime;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.typeeffect.psychic.ActionBattlePsycUpController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ActionBattleTypeEffectRuntime {
    private ActionBattleTypeEffectRuntime() {}

    public static void tick(MinecraftServer server) {
        UUID sessionId = DungeonSession.getSessionId();
        if (server == null || !DungeonSession.isActive() || sessionId == null) return;
        ServerLevel level = server.getLevel(DungeonDimension.DUNGEON_DIMENSION);
        if (level == null) return;
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        controller.guardSession(sessionId);
        ActionBattleSleepController.tickSession(level, sessionId, level.getGameTime());
        var groundEvents = controller.tickSession(sessionId, level.getGameTime());
        for (var event : groundEvents) {
            PokemonEntity pokemon = activePokemonEntity(level, event.pokemonId());
            if (pokemon != null) {
                int depth = controller.groundView(sessionId, event.pokemonId(), level.getGameTime())
                        .map(ActionBattleGroundState.View::depthPercent).orElse(0);
                ActionBattleGroundVisualSync.update(pokemon, sessionId, depth);
            }
            if (event.branch() != ActionBattleGroundState.Branch.DIG
                    || event.result() != ActionBattleGroundState.TickResult.NATURAL_EXPEL) continue;
            if (pokemon != null) ActionBattleGroundController.launchExpelWave(sessionId, pokemon);
        }
        ActionBattleWaterController.tickSession(sessionId);
        ActionBattleWaveServerRuntime.tick(level, sessionId);
        ActionBattleDrowsyController.tickSession(level, sessionId);
    }

    public static void clearPlayer(ServerPlayer player) {
        UUID sessionId = DungeonSession.getSessionId();
        if (player == null || sessionId == null) return;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        ServerLevel dungeonLevel = player.getServer() != null
                ? player.getServer().getLevel(DungeonDimension.DUNGEON_DIMENSION) : null;
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon != null) {
                PokemonEntity entity = dungeonLevel != null ? activePokemonEntity(dungeonLevel, pokemon.getUuid()) : null;
                ActionBattleTypeEffectController.global().clearPokemon(sessionId, pokemon.getUuid());
                ActionBattleDragonRuntime.clearPokemon(sessionId, pokemon.getUuid());
                ActionBattleDarkRuntime.clearPokemon(sessionId, pokemon.getUuid());
                if (entity != null) ActionBattleGroundVisualSync.update(entity, sessionId, 0);
                ActionBattleSession battle = ActionBattleManager.findSessionForPokemon(pokemon.getUuid());
                if (battle != null) {
                    ActionBattleEffectController.global().onPokemonRecalled(
                            battle.battleId(), pokemon.getUuid(), player.level().getGameTime());
                    ActionBattlePsycUpController.global().onPokemonUnavailable(battle.battleId(), pokemon.getUuid());
                    ActionBattleRockController.global().onPokemonUnavailable(battle.battleId(), pokemon.getUuid());
                    ActionBattleGhostRuntime.global().onPokemonUnavailable(battle.battleId(), pokemon.getUuid());
                }
            }
        }
    }

    public static void onBattleEnded(UUID sessionId, long currentTick) {
        if (sessionId == null || currentTick < 0L) return;
        ActionBattleTypeEffectController.global().tickSession(sessionId, currentTick);
        ActionBattleDragonRuntime.onBattleEnded(sessionId, currentTick);
    }

    public static void clearSession(UUID sessionId) {
        ActionBattleManager.clearEffectStateForDungeonSession(sessionId);
        ActionBattleTypeEffectController.global().clearSession(sessionId);
        ActionBattleDragonRuntime.clearSession(sessionId);
        ActionBattleDarkRuntime.clearSession(sessionId);
    }

    public static void clearSession(ServerLevel level, UUID sessionId) {
        ActionBattleWaterController.clearSession(level, sessionId);
        ActionBattleGrassController.clearSession(level, sessionId);
        ActionBattleWaveServerRuntime.clearSession(sessionId);
        clearSession(sessionId);
        ActionBattleGroundVisualSync.clearSession(sessionId);
    }

    public static void onPokemonRecalled(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectController.global().onPokemonUnavailable(sessionId, pokemonUUID, currentTick);
    }

    public static void onPokemonAvailable(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectController.global().onPokemonAvailable(sessionId, pokemonUUID);
    }

    public static void clearAll() {
        ActionBattleTypeEffectController.global().clearAll();
        ActionBattleEffectController.global().clearAll();
        ActionBattlePsycUpController.global().clearAll();
        ActionBattleRockController.global().clearAll();
        ActionBattleGrassController.clearAll();
        ActionBattleWaveServerRuntime.clearAll();
        ActionBattleGroundVisualSync.clearAll();
        ActionBattleDragonRuntime.clearAll();
        ActionBattleDarkRuntime.clearAll();
    }

    private static PokemonEntity activePokemonEntity(ServerLevel level, UUID pokemonId) {
        ActionBattleSession battle = ActionBattleManager.findSessionForPokemon(pokemonId);
        if (battle == null) return null;
        UUID entityId = battle.isPlayerPokemon(pokemonId) ? battle.playerEntityForPokemon(pokemonId)
                : pokemonId.equals(battle.trainerActivePokemonUUID()) ? battle.trainerActiveEntityUUID() : null;
        Entity raw = entityId != null ? level.getEntity(entityId) : null;
        return raw instanceof PokemonEntity pokemon && !pokemon.isRemoved() ? pokemon : null;
    }
}

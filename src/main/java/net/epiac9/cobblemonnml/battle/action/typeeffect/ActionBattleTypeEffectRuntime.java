package net.epiac9.cobblemonnml.battle.action.typeeffect;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireParticleController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceVisuals;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.poison.ActionBattlePoisonParticleController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleParalysisController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
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
        controller.tickSession(sessionId, level.getGameTime());
        ActionBattleFairyController.tickSession(level, sessionId);
        ActionBattleFireParticleController.tick(level);
        ActionBattleIceVisuals.tick(level);
        ActionBattlePoisonParticleController.tick(level);
    }

    public static void clearPlayer(ServerPlayer player) {
        UUID sessionId = DungeonSession.getSessionId();
        if (player == null || sessionId == null) return;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon != null) {
                ActionBattleTypeEffectController.global().clearPokemon(sessionId, pokemon.getUuid());
                ActionBattleEffectController.global().clearStatuses(sessionId, pokemon.getUuid(), player.level().getGameTime());
            }
        }
    }

    public static void clearSession(UUID sessionId) {
        ActionBattleTypeEffectController.global().clearSession(sessionId);
        ActionBattleEffectController.global().clearBattle(sessionId);
    }

    public static void onPokemonRecalled(UUID sessionId, UUID pokemonUUID) {
        ActionBattleParalysisController.global().clearPokemon(sessionId, pokemonUUID);
    }

    public static void clearAll() {
        ActionBattleTypeEffectController.global().clearAll();
    }
}

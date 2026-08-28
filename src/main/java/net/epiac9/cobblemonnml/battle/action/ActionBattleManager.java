package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerRegistry;
import com.gitlab.srcmc.tbcs.api.TBCS;
import kotlin.Unit;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.events.trainer.DungeonTrainerBattleResultHandler;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ActionBattleManager {
    private static final Map<UUID, ActionBattleSession> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, ActionBattleSession> BY_TRAINER = new HashMap<>();
    private static final Map<UUID, BattlePokemonRefs> POKEMON_BY_BATTLE = new HashMap<>();

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
                trainerPreset != null ? trainerPreset.toString() : null
        );
        BY_PLAYER.put(player.getUUID(), session);
        BY_TRAINER.put(trainer.getUUID(), session);
        POKEMON_BY_BATTLE.put(session.battleId(), new BattlePokemonRefs(playerLead.pokemon(), trainerLead.pokemon()));
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
    }

    public static int size() {
        return BY_PLAYER.size();
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

    private static SelectedPokemon findFirstUsableTrainerPokemon(TrainerNPC trainer) {
        Pokemon[] team = trainer.getTeam();
        for (int slot = 0; slot < team.length; slot++) {
            Pokemon pokemon = team[slot];
            if (pokemon != null && !pokemon.isFainted()) return new SelectedPokemon(slot, pokemon);
        }
        return null;
    }

    private static void sendOutPokemon(ActionBattleSession session, boolean playerSide, LivingEntity source, LivingEntity opponent, SelectedPokemon selected) {
        Pokemon pokemon = selected.pokemon();
        ServerLevel level = (ServerLevel) source.level();
        PokemonEntity existing = pokemon.getEntity();
        if (existing != null && !existing.isRemoved() && existing.level() == level) {
            bindActivePokemon(session, playerSide, selected.slot(), pokemon, existing);
            return;
        }
        if (existing != null) pokemon.recall();
        Vec3 sendOutPosition = calculateSendOutPosition(source, opponent);
        CompletableFuture<PokemonEntity> future = pokemon.sendOutWithAnimation(
                source, level, sendOutPosition, null, true, null, entity -> Unit.INSTANCE
        );
        future.whenComplete((entity, throwable) -> {
            if (throwable != null || entity == null) {
                DebugLog.log("[CobblemonNML] Action battle Pokemon send-out failed for battle " + session.battleId());
                if (throwable != null) throwable.printStackTrace();
                if (isCurrentSession(session)) invalidateBattle(session.playerUUID());
                return;
            }
            if (!isCurrentSession(session) || session.state() != ActionBattleState.ACTIVE) {
                pokemon.recall();
                return;
            }
            if (!bindActivePokemon(session, playerSide, selected.slot(), pokemon, entity)) {
                pokemon.recall();
            }
        });
    }

    private static boolean bindActivePokemon(ActionBattleSession session, boolean playerSide, int partyIndex, Pokemon pokemon, PokemonEntity entity) {
        boolean bound = playerSide
                ? session.bindPlayerActivePokemon(partyIndex, pokemon.getUuid(), entity.getUUID())
                : session.bindTrainerActivePokemon(partyIndex, pokemon.getUuid(), entity.getUUID());
        if (bound) {
            DebugLog.log("[CobblemonNML] Action battle " + (playerSide ? "player" : "trainer") + " Pokemon active. Battle=" + session.battleId() + ", slot=" + partyIndex + ", pokemon=" + pokemon.getUuid() + ", entity=" + entity.getUUID());
            if (session.playerActiveEntityUUID() != null && session.trainerActiveEntityUUID() != null) {
                DebugLog.log("[CobblemonNML] Action battle combatants ready. Battle=" + session.battleId());
            }
        }
        return bound;
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
        BattlePokemonRefs refs = POKEMON_BY_BATTLE.remove(session.battleId());
        if (refs != null) {
            recallPokemon(refs.playerPokemon());
            recallPokemon(refs.trainerPokemon());
        }
        session.clearPlayerActivePokemon();
        session.clearTrainerActivePokemon();
    }

    private static void recallPokemon(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            pokemon.recall();
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] Failed to recall action battle Pokemon " + pokemon.getUuid());
            exception.printStackTrace();
        }
    }

    private static void removeSession(ActionBattleSession session) {
        BY_PLAYER.remove(session.playerUUID(), session);
        BY_TRAINER.remove(session.trainerUUID(), session);
        POKEMON_BY_BATTLE.remove(session.battleId());
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
    private record BattlePokemonRefs(Pokemon playerPokemon, Pokemon trainerPokemon) {}
}

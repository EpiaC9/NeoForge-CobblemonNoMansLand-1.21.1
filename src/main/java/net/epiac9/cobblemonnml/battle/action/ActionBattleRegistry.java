package net.epiac9.cobblemonnml.battle.action;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ActionBattleRegistry {
    private static final Map<UUID, ActionBattleSession> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, ActionBattleSession> BY_TRAINER = new HashMap<>();
    private static final Map<UUID, ActionBattlePokemonRefs> POKEMON_BY_BATTLE = new HashMap<>();

    private ActionBattleRegistry() {}

    static ActionBattleSession byPlayer(UUID playerUUID) {
        return playerUUID != null ? BY_PLAYER.get(playerUUID) : null;
    }

    static ActionBattleSession byTrainer(UUID trainerUUID) {
        return trainerUUID != null ? BY_TRAINER.get(trainerUUID) : null;
    }

    static boolean register(ActionBattleSession session, ActionBattlePokemonRefs refs) {
        if (session == null || refs == null || BY_PLAYER.containsKey(session.playerUUID()) || BY_TRAINER.containsKey(session.trainerUUID())) return false;
        BY_PLAYER.put(session.playerUUID(), session);
        BY_TRAINER.put(session.trainerUUID(), session);
        POKEMON_BY_BATTLE.put(session.battleId(), refs);
        return true;
    }

    static ActionBattlePokemonRefs pokemonRefs(UUID battleId) {
        return battleId != null ? POKEMON_BY_BATTLE.get(battleId) : null;
    }

    static ActionBattlePokemonRefs removePokemonRefs(UUID battleId) {
        return battleId != null ? POKEMON_BY_BATTLE.remove(battleId) : null;
    }

    static boolean isCurrent(ActionBattleSession session) {
        return session != null && BY_PLAYER.get(session.playerUUID()) == session && BY_TRAINER.get(session.trainerUUID()) == session;
    }

    static ActionBattleSession findByPokemonEntity(UUID entityUUID) {
        if (entityUUID == null) return null;
        for (ActionBattleSession session : BY_PLAYER.values()) {
            if (entityUUID.equals(session.playerActiveEntityUUID()) || entityUUID.equals(session.trainerActiveEntityUUID())) return session;
        }
        return null;
    }

    static ActionBattleSession[] sessionsSnapshot() {
        return BY_PLAYER.values().toArray(ActionBattleSession[]::new);
    }

    static void remove(ActionBattleSession session) {
        if (session == null) return;
        BY_PLAYER.remove(session.playerUUID(), session);
        BY_TRAINER.remove(session.trainerUUID(), session);
        POKEMON_BY_BATTLE.remove(session.battleId());
    }

    static void clear() {
        BY_PLAYER.clear();
        BY_TRAINER.clear();
        POKEMON_BY_BATTLE.clear();
    }

    static int size() {
        return BY_PLAYER.size();
    }
}

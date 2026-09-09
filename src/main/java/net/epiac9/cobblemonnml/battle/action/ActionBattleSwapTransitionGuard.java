package net.epiac9.cobblemonnml.battle.action;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleSwapTransitionGuard {
    private static final Map<Key, Transition> ACTIVE = new HashMap<>();
    private ActionBattleSwapTransitionGuard() {}

    public static void begin(UUID battleId, ActionBattleCommandController.Side side,
                             UUID outgoingPokemonId, UUID incomingPokemonId) {
        if (battleId == null || side == null || incomingPokemonId == null) return;
        ACTIVE.put(new Key(battleId, incomingPokemonId), new Transition(outgoingPokemonId, incomingPokemonId));
    }
    public static void bindComplete(UUID battleId, UUID incomingPokemonId) {
        if (battleId != null && incomingPokemonId != null) ACTIVE.remove(new Key(battleId, incomingPokemonId));
    }
    public static boolean rejectsHit(UUID battleId, UUID pokemonId) {
        if (battleId == null || pokemonId == null) return false;
        for (Map.Entry<Key, Transition> entry : ACTIVE.entrySet()) {
            if (entry.getKey().battleId().equals(battleId) && entry.getValue().contains(pokemonId)) return true;
        }
        return false;
    }
    public static boolean rejectsHit(UUID pokemonId) {
        if (pokemonId == null) return false;
        return ACTIVE.values().stream().anyMatch(transition -> transition.contains(pokemonId));
    }
    public static void clearBattle(UUID battleId) { if (battleId != null) ACTIVE.keySet().removeIf(key -> key.battleId().equals(battleId)); }
    public static void clearAll() { ACTIVE.clear(); }
    private record Key(UUID battleId, UUID incomingPokemonId) {}
    private record Transition(UUID outgoing, UUID incoming) {
        boolean contains(UUID id) { return id.equals(outgoing) || id.equals(incoming); }
    }
}

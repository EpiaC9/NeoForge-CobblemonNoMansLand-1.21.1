package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundVisualRules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionBattleGroundVisualClientState {
    private static final Map<UUID, Entry> DEPTHS = new ConcurrentHashMap<>();

    private ActionBattleGroundVisualClientState() {}

    public static void apply(String operation, String sessionId, String pokemonId, int depthPercent) {
        if (operation == null) return;
        switch (operation) {
            case "UPDATE" -> applyUpdate(sessionId, pokemonId, depthPercent);
            case "CLEAR_SESSION" -> parse(sessionId).ifPresent(ActionBattleGroundVisualClientState::clearSession);
            case "CLEAR_ALL" -> clearAll();
            default -> { }
        }
    }

    public static int depthPercent(UUID pokemonId) {
        Entry entry = pokemonId != null ? DEPTHS.get(pokemonId) : null;
        return entry != null ? entry.depthPercent() : 0;
    }

    public static void clearSession(UUID sessionId) {
        if (sessionId != null) DEPTHS.entrySet().removeIf(entry -> sessionId.equals(entry.getValue().sessionId()));
    }

    public static void clearAll() { DEPTHS.clear(); }

    private static void applyUpdate(String sessionValue, String pokemonValue, int depthPercent) {
        var sessionId = parse(sessionValue);
        var pokemonId = parse(pokemonValue);
        if (sessionId.isEmpty() || pokemonId.isEmpty()
                || !ActionBattleGroundVisualRules.isSupportedDepth(depthPercent)) return;
        if (depthPercent == 0) DEPTHS.remove(pokemonId.get());
        else DEPTHS.put(pokemonId.get(), new Entry(sessionId.get(), depthPercent));
    }

    private static java.util.Optional<UUID> parse(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return java.util.Optional.empty();
        }
    }

    private record Entry(UUID sessionId, int depthPercent) {}
}

package net.epiac9.cobblemonnml.battle.action;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleTargetTracker {
    private static final ActionBattleTargetTracker GLOBAL = new ActionBattleTargetTracker();
    private final Map<Key, Entry> entries = new HashMap<>();

    public static ActionBattleTargetTracker global() {
        return GLOBAL;
    }

    public void observe(UUID battleId, UUID ownerPokemonId, UUID targetPokemonId,
                        ActionBattleTargetingRules.Point targetPosition,
                        ActionBattleTargetingRules.VisibilityResult visibility) {
        if (battleId == null || ownerPokemonId == null || targetPokemonId == null || visibility == null) return;
        Key key = new Key(battleId, ownerPokemonId);
        Entry entry = entries.get(key);
        if (entry == null || !targetPokemonId.equals(entry.targetPokemonId)) {
            entry = new Entry(targetPokemonId);
            entries.put(key, entry);
        }
        entry.visibility = visibility;
        if (visibility.visible() && targetPosition != null) entry.lastVisible = targetPosition;
    }

    public Optional<ActionBattleTargetingRules.Point> lastVisible(
            UUID battleId, UUID ownerPokemonId, UUID targetPokemonId) {
        Entry entry = entry(battleId, ownerPokemonId, targetPokemonId);
        return entry != null ? Optional.ofNullable(entry.lastVisible) : Optional.empty();
    }

    public Optional<ActionBattleTargetingRules.VisibilityResult> currentVisibility(
            UUID battleId, UUID ownerPokemonId, UUID targetPokemonId) {
        Entry entry = entry(battleId, ownerPokemonId, targetPokemonId);
        return entry != null ? Optional.ofNullable(entry.visibility) : Optional.empty();
    }

    public boolean clearRememberedIfReached(UUID battleId, UUID ownerPokemonId, UUID targetPokemonId,
                                            ActionBattleTargetingRules.Point ownerPosition, double tolerance) {
        Entry entry = entry(battleId, ownerPokemonId, targetPokemonId);
        if (entry == null || entry.lastVisible == null || ownerPosition == null || tolerance < 0.0D) return false;
        double dx = entry.lastVisible.x() - ownerPosition.x();
        double dy = entry.lastVisible.y() - ownerPosition.y();
        double dz = entry.lastVisible.z() - ownerPosition.z();
        if (dx * dx + dy * dy + dz * dz > tolerance * tolerance) return false;
        entry.lastVisible = null;
        return true;
    }

    public void clearPokemon(UUID battleId, UUID pokemonId) {
        if (battleId == null || pokemonId == null) return;
        entries.entrySet().removeIf(entry -> battleId.equals(entry.getKey().battleId)
                && (pokemonId.equals(entry.getKey().ownerPokemonId)
                || pokemonId.equals(entry.getValue().targetPokemonId)));
    }

    public void clearBattle(UUID battleId) {
        if (battleId != null) entries.keySet().removeIf(key -> battleId.equals(key.battleId));
    }

    public void clearAll() {
        entries.clear();
    }

    private Entry entry(UUID battleId, UUID ownerPokemonId, UUID targetPokemonId) {
        if (battleId == null || ownerPokemonId == null || targetPokemonId == null) return null;
        Entry entry = entries.get(new Key(battleId, ownerPokemonId));
        return entry != null && targetPokemonId.equals(entry.targetPokemonId) ? entry : null;
    }

    private record Key(UUID battleId, UUID ownerPokemonId) {}

    private static final class Entry {
        private final UUID targetPokemonId;
        private ActionBattleTargetingRules.Point lastVisible;
        private ActionBattleTargetingRules.VisibilityResult visibility;

        private Entry(UUID targetPokemonId) {
            this.targetPokemonId = targetPokemonId;
        }
    }
}

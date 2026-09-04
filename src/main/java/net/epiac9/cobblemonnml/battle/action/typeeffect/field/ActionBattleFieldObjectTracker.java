package net.epiac9.cobblemonnml.battle.action.typeeffect.field;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleFieldObjectTracker {
    private final int perOwnerLimit;
    private final Map<UUID, List<ActionBattleFieldObject>> bySession = new HashMap<>();

    public ActionBattleFieldObjectTracker(int perOwnerLimit) {
        if (perOwnerLimit <= 0) throw new IllegalArgumentException("Field-object owner limit must be positive.");
        this.perOwnerLimit = perOwnerLimit;
    }

    public Optional<ActionBattleFieldObject> register(ActionBattleFieldObject object) {
        if (object == null) return Optional.empty();
        List<ActionBattleFieldObject> objects = bySession.computeIfAbsent(object.sessionId(), ignored -> new ArrayList<>());
        objects.removeIf(existing -> existing.position().equals(object.position()));
        List<ActionBattleFieldObject> owned = objects.stream()
                .filter(existing -> existing.ownerPokemonUUID().equals(object.ownerPokemonUUID()))
                .sorted(order()).toList();
        ActionBattleFieldObject evicted = owned.size() >= perOwnerLimit ? owned.getFirst() : null;
        if (evicted != null) objects.remove(evicted);
        objects.add(object);
        return Optional.ofNullable(evicted);
    }

    public boolean unregister(UUID sessionId, ActionBattleFieldObject.Position position) {
        if (sessionId == null || position == null) return false;
        List<ActionBattleFieldObject> objects = bySession.get(sessionId);
        if (objects == null) return false;
        boolean removed = objects.removeIf(object -> position.equals(object.position()));
        if (objects.isEmpty()) bySession.remove(sessionId);
        return removed;
    }

    public List<ActionBattleFieldObject> objectsForOwner(UUID sessionId, UUID ownerPokemonUUID) {
        if (sessionId == null || ownerPokemonUUID == null) return List.of();
        return bySession.getOrDefault(sessionId, List.of()).stream()
                .filter(object -> ownerPokemonUUID.equals(object.ownerPokemonUUID()))
                .sorted(order()).toList();
    }

    public List<ActionBattleFieldObject> clearSession(UUID sessionId) {
        if (sessionId == null) return List.of();
        List<ActionBattleFieldObject> removed = bySession.remove(sessionId);
        return removed == null ? List.of() : removed.stream().sorted(order()).toList();
    }

    public int trackedCount(UUID sessionId) {
        return sessionId == null ? 0 : bySession.getOrDefault(sessionId, List.of()).size();
    }

    private static Comparator<ActionBattleFieldObject> order() {
        return Comparator.comparingLong(ActionBattleFieldObject::creationSequence)
                .thenComparingInt(object -> object.position().x())
                .thenComparingInt(object -> object.position().y())
                .thenComparingInt(object -> object.position().z());
    }
}

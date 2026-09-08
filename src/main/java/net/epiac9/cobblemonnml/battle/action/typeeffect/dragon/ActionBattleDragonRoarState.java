package net.epiac9.cobblemonnml.battle.action.typeeffect.dragon;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks every Pokemon caught by one roar until that roar's fixed end tick. */
public final class ActionBattleDragonRoarState {
    private final long startTick;
    private final long endTick;
    private final Map<UUID, UUID> capturedEntityIds = new HashMap<>();

    public ActionBattleDragonRoarState(long startTick, long endTick) {
        this.startTick = startTick;
        this.endTick = endTick;
    }

    public boolean capture(UUID pokemonId, long currentTick) {
        return capture(pokemonId, pokemonId, currentTick);
    }

    public boolean capture(UUID pokemonId, UUID entityId, long currentTick) {
        if (pokemonId == null || entityId == null || currentTick < startTick || currentTick >= endTick) return false;
        return capturedEntityIds.putIfAbsent(pokemonId, entityId) == null;
    }

    public boolean isStunned(UUID pokemonId, long currentTick) {
        return pokemonId != null && currentTick >= startTick && currentTick < endTick
                && capturedEntityIds.containsKey(pokemonId);
    }

    public Set<UUID> pokemonIds() {
        return Collections.unmodifiableSet(capturedEntityIds.keySet());
    }

    public Set<UUID> entityIds() {
        return Set.copyOf(capturedEntityIds.values());
    }

    public static boolean insideRadiusSquared(double distanceSquared) {
        return distanceSquared <= ActionBattleDragonRules.ROAR_RADIUS * ActionBattleDragonRules.ROAR_RADIUS;
    }
}

package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ActionBattleTypeEffectController {
    private static final ActionBattleTypeEffectController GLOBAL = new ActionBattleTypeEffectController();
    private final Map<UUID, ActionBattleTypeEffectState> states = new HashMap<>();
    private UUID activeSessionId;

    public static ActionBattleTypeEffectController global() { return GLOBAL; }

    public void guardSession(UUID sessionId) {
        if (sessionId == null) { clearAll(); return; }
        if (sessionId.equals(activeSessionId)) return;
        states.clear();
        activeSessionId = sessionId;
    }

    public boolean applyImmobilized(UUID sessionId, UUID pokemonUUID, long currentTick) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return false;
        return state(pokemonUUID).applyImmobilized(currentTick);
    }

    public boolean breakWaterTrapOnDamage(UUID sessionId, UUID pokemonUUID, int actualDamage) {
        ActionBattleTypeEffectState state = existing(sessionId, pokemonUUID);
        if (state == null || !state.breakWaterTrapOnDamage(actualDamage)) return false;
        removeIfEmpty(state);
        return true;
    }

    public Optional<ActionBattleWaterState.ImmobilizedView> immobilizedView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = existing(sessionId, pokemonUUID);
        return state == null ? Optional.empty() : state.immobilizedView(currentTick);
    }


    public ActionBattleGroundState.ApplyResult applyGround(UUID sessionId, UUID pokemonUUID, long currentTick,
                                                            boolean groundTypedTarget) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return ActionBattleGroundState.ApplyResult.IGNORED;
        return state(pokemonUUID).applyGround(currentTick, groundTypedTarget);
    }

    public Optional<ActionBattleGroundState.View> groundView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = currentTick >= 0L ? existing(sessionId, pokemonUUID) : null;
        return state == null ? Optional.empty() : state.groundView(currentTick);
    }

    public double groundMovementMultiplier(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = currentTick >= 0L ? existing(sessionId, pokemonUUID) : null;
        return state == null ? 1.0D : state.groundMovementMultiplier(currentTick);
    }

    public boolean groundBlocksMovement(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = currentTick >= 0L ? existing(sessionId, pokemonUUID) : null;
        return state != null && state.groundBlocksMovement(currentTick);
    }

    public boolean groundBlocksRecall(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = currentTick >= 0L ? existing(sessionId, pokemonUUID) : null;
        return state != null && state.groundBlocksRecall(currentTick);
    }

    public boolean expelGround(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = existing(sessionId, pokemonUUID);
        if (state == null || !state.expelGround()) return false;
        removeIfEmpty(state);
        return true;
    }

    public boolean clearGroundPokemon(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = existing(sessionId, pokemonUUID);
        if (state == null || !state.clearGround()) return false;
        removeIfEmpty(state);
        return true;
    }

    public List<GroundTickEvent> tickSession(UUID sessionId, long currentTick) {
        if (!validSession(sessionId) || currentTick < 0L) return List.of();
        List<GroundTickEvent> events = new ArrayList<>();
        for (Map.Entry<UUID, ActionBattleTypeEffectState> entry : states.entrySet()) {
            ActionBattleGroundState.Branch branch = entry.getValue().groundBranch();
            ActionBattleGroundState.TickResult result = entry.getValue().tickGround(currentTick);
            if (branch != null && result != ActionBattleGroundState.TickResult.NONE) events.add(new GroundTickEvent(entry.getKey(), branch, result));
            entry.getValue().tick(currentTick);
        }
        states.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return List.copyOf(events);
    }

    public Set<UUID> trackedPokemonIds(UUID sessionId) { return validSession(sessionId) ? Set.copyOf(states.keySet()) : Set.of(); }
    public void onPokemonUnavailable(UUID sessionId, UUID pokemonUUID, long currentTick) { removeIfEmpty(existing(sessionId, pokemonUUID)); }
    public void onPokemonAvailable(UUID sessionId, UUID pokemonUUID) {}

    public void clearPokemon(UUID sessionId, UUID pokemonUUID) {
        if (validSession(sessionId) && pokemonUUID != null) states.remove(pokemonUUID);
    }

    public void clearSession(UUID sessionId) {
        if (!validSession(sessionId)) return;
        states.clear();
        activeSessionId = null;
    }

    public void clearAll() { states.clear(); activeSessionId = null; }
    public int trackedPokemonCount(UUID sessionId) { return validSession(sessionId) ? states.size() : 0; }

    private ActionBattleTypeEffectState state(UUID pokemonUUID) { return states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new); }
    private ActionBattleTypeEffectState existing(UUID sessionId, UUID pokemonUUID) { return validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null; }
    private void removeIfEmpty(ActionBattleTypeEffectState state) { if (state != null && state.isEmpty()) states.remove(state.pokemonUUID()); }
    private boolean validSession(UUID sessionId) { return sessionId != null && sessionId.equals(activeSessionId); }

    public record GroundTickEvent(UUID pokemonId, ActionBattleGroundState.Branch branch, ActionBattleGroundState.TickResult result) {}
}

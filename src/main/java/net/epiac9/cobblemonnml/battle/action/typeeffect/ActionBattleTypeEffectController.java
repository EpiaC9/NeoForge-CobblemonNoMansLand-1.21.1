package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceRules;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleTypeEffectController {
    private static final ActionBattleTypeEffectController GLOBAL = new ActionBattleTypeEffectController();

    private final Map<UUID, ActionBattleTypeEffectState> states = new HashMap<>();
    private UUID activeSessionId;

    public static ActionBattleTypeEffectController global() { return GLOBAL; }

    public void guardSession(UUID sessionId) {
        if (sessionId == null) {
            clearAll();
            return;
        }
        if (sessionId.equals(activeSessionId)) return;
        states.clear();
        activeSessionId = sessionId;
    }

    public boolean applyFirePressure(UUID sessionId, UUID pokemonUUID, double amount, long currentTick,
                                     boolean fireTyped, boolean hazeActive) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return false;
        ActionBattleTypeEffectState state = states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new);
        boolean applied = state.applyFirePressure(amount, currentTick, fireTyped, hazeActive);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return applied;
    }

    public boolean applyIceApplication(UUID sessionId, UUID pokemonUUID, long currentTick,
                                       boolean iceTyped, boolean hazeActive) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return false;
        ActionBattleTypeEffectState state = states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new);
        boolean applied = state.applyIceApplication(currentTick, iceTyped, hazeActive);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return applied;
    }

    public void tickSession(UUID sessionId, long currentTick) {
        if (!validSession(sessionId) || currentTick < 0L) return;
        for (ActionBattleTypeEffectState state : states.values()) state.tick(currentTick);
        states.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public Optional<ActionBattleTypeEffectState.FireView> fireView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state == null) return Optional.empty();
        Optional<ActionBattleTypeEffectState.FireView> view = state.fireView(currentTick);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return view;
    }

    public Optional<ActionBattleTypeEffectState.IceView> iceView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state == null) return Optional.empty();
        Optional<ActionBattleTypeEffectState.IceView> view = state.iceView(currentTick);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return view;
    }

    public int fireAttackStages(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.fireAttackStages(currentTick) : 0;
    }

    public int iceDefenseStages(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.iceDefenseStages(currentTick) : 0;
    }

    public int iceHitsRequired(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.iceHitsRequired(currentTick) : ActionBattleIceRules.BASE_HITS_REQUIRED;
    }

    public void suppressFireBonusByHaze(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state != null) state.suppressFireBonusByHaze();
    }

    public void suppressIceDefenseByHaze(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state != null) state.suppressIceDefenseByHaze();
    }

    public double modifyDamage(UUID sessionId, UUID targetPokemonUUID, boolean fireMove, double damage, long currentTick) {
        return modifyDamage(sessionId, targetPokemonUUID, fireMove, false, damage, currentTick);
    }

    public double modifyDamage(UUID sessionId, UUID targetPokemonUUID, boolean fireMove, boolean iceMove,
                               double damage, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && targetPokemonUUID != null ? states.get(targetPokemonUUID) : null;
        boolean burned = state != null && state.isBurning(currentTick);
        boolean frostbitten = state != null && state.isFrostbitten(currentTick);
        double fireModified = ActionBattleFireRules.modifyIncomingDamage(damage, fireMove, burned);
        return ActionBattleIceRules.modifyIncomingDamage(fireModified, iceMove, frostbitten);
    }

    public void clearPokemon(UUID sessionId, UUID pokemonUUID) {
        if (validSession(sessionId) && pokemonUUID != null) states.remove(pokemonUUID);
    }

    public void clearSession(UUID sessionId) {
        if (!validSession(sessionId)) return;
        states.clear();
        activeSessionId = null;
    }

    public void clearAll() {
        states.clear();
        activeSessionId = null;
    }

    public int trackedPokemonCount(UUID sessionId) { return validSession(sessionId) ? states.size() : 0; }

    private boolean validSession(UUID sessionId) {
        return sessionId != null && sessionId.equals(activeSessionId);
    }
}

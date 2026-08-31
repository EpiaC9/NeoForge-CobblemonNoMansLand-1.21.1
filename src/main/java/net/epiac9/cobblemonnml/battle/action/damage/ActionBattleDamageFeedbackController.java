package net.epiac9.cobblemonnml.battle.action.damage;
import java.util.*;
public final class ActionBattleDamageFeedbackController {
    private static final ActionBattleDamageFeedbackController GLOBAL = new ActionBattleDamageFeedbackController();
    private final Map<UUID, BattleState> battles = new HashMap<>();
    private long nextEventId = 1L;
    public static ActionBattleDamageFeedbackController global() { return GLOBAL; }
    public void seedPokemon(UUID battleId, UUID pokemonUUID, int currentHp) {
        if (battleId == null || pokemonUUID == null) return;
        state(battleId).hpByPokemon.put(pokemonUUID, Math.max(0, currentHp));
    }
    public void recordDamage(UUID battleId, UUID pokemonUUID, int beforeHp, int afterHp, ActionBattleDamageFeedbackCategory category) {
        if (battleId == null || pokemonUUID == null) return;
        BattleState state = state(battleId);
        int safeAfter = Math.max(0, afterHp);
        int damage = Math.max(0, Math.max(0, beforeHp) - safeAfter);
        state.hpByPokemon.put(pokemonUUID, safeAfter);
        if (damage <= 0) return;
        queue(state, pokemonUUID, damage, category);
    }
    public void observePokemon(UUID battleId, UUID pokemonUUID, int currentHp) {
        if (battleId == null || pokemonUUID == null) return;
        BattleState state = state(battleId);
        int safeCurrent = Math.max(0, currentHp);
        Integer previous = state.hpByPokemon.put(pokemonUUID, safeCurrent);
        if (previous == null || safeCurrent >= previous) return;
        queue(state, pokemonUUID, previous - safeCurrent, ActionBattleDamageFeedbackCategory.NORMAL);
    }
    public List<ActionBattleDamageFeedbackEvent> drain(UUID battleId, UUID pokemonUUID) {
        BattleState state = battles.get(battleId);
        if (state == null || pokemonUUID == null) return List.of();
        List<ActionBattleDamageFeedbackEvent> events = state.queuedByPokemon.remove(pokemonUUID);
        return events == null || events.isEmpty() ? List.of() : List.copyOf(events);
    }
    public void clearBattle(UUID battleId) { if (battleId != null) battles.remove(battleId); }
    private BattleState state(UUID battleId) { return battles.computeIfAbsent(battleId, ignored -> new BattleState()); }
    private void queue(BattleState state, UUID pokemonUUID, int damage, ActionBattleDamageFeedbackCategory category) {
        if (damage <= 0) return;
        state.queuedByPokemon.computeIfAbsent(pokemonUUID, ignored -> new ArrayList<>())
                .add(new ActionBattleDamageFeedbackEvent(nextEventId++, pokemonUUID, damage, category));
    }
    private static final class BattleState {
        private final Map<UUID, Integer> hpByPokemon = new HashMap<>();
        private final Map<UUID, List<ActionBattleDamageFeedbackEvent>> queuedByPokemon = new HashMap<>();
    }
}

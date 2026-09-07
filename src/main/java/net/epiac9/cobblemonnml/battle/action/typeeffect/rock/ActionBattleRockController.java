package net.epiac9.cobblemonnml.battle.action.typeeffect.rock;

import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatApplicationService;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleRockController {
    private static final ActionBattleRockController GLOBAL = new ActionBattleRockController(
            ActionBattleEffectController.global(), ActionBattleStatApplicationService.global());
    private final ActionBattleEffectController effects;
    private final ActionBattleStatApplicationService stats;
    private final Map<Key, ActionBattleRockState> states = new HashMap<>();

    public ActionBattleRockController(ActionBattleEffectController effects, ActionBattleStatApplicationService stats) {
        this.effects = effects;
        this.stats = stats;
    }

    public static ActionBattleRockController global() { return GLOBAL; }

    public ActionBattleRockState.ApplyResult applyStockpile(UUID battleId, UUID pokemonId,
                                                             ActionBattleRockSelection selection,
                                                             boolean rockTyped, long currentTick) {
        if (battleId == null || pokemonId == null) return ActionBattleRockState.ApplyResult.INVALID;
        return states.computeIfAbsent(new Key(battleId, pokemonId), ignored -> new ActionBattleRockState())
                .apply(selection, rockTyped, currentTick);
    }

    public ProcResult onDamage(UUID battleId, UUID pokemonId, int positiveDamage, int remainingHp,
                               ActionBattleDamageOrigin origin, boolean consumedEnduranceThisHit, long currentTick) {
        ActionBattleRockState state = state(battleId, pokemonId, currentTick);
        ActionBattleRockState.Stockpile stockpile = state == null ? null : state.stockpile(currentTick);
        if (stockpile == null || origin != ActionBattleDamageOrigin.DIRECT_MOVE || positiveDamage <= 0 || remainingHp <= 0) {
            return ProcResult.NONE;
        }
        ActionBattleStat resistance = stockpile.selection().resistanceStat();
        ActionBattleStat damage = stockpile.selection().damageStat();
        int resistanceStage = effects.effectiveStage(battleId, pokemonId, resistance, currentTick);
        int damageStage = effects.effectiveStage(battleId, pokemonId, damage, currentTick);
        boolean resistanceCapped = resistanceStage >= 6;
        boolean damageCapped = damageStage >= 6;
        if (resistanceCapped && damageCapped) {
            boolean granted = !consumedEnduranceThisHit && state.grantEndurance(currentTick, stockpile.rockTyped());
            return new ProcResult(0, 0, granted);
        }
        int budget = stockpile.rockTyped() ? 2 : 1;
        int resistanceRequest = resistanceCapped ? 0 : Math.min(budget, 6 - resistanceStage);
        int resistanceApplied = apply(battleId, pokemonId, resistance, resistanceRequest, currentTick);
        budget -= Math.max(0, resistanceApplied);
        int damageRequest = resistanceCapped ? Math.min(budget, 6 - damageStage)
                : stockpile.rockTyped() ? Math.min(budget, 6 - damageStage) : 0;
        int damageApplied = apply(battleId, pokemonId, damage, damageRequest, currentTick);
        return new ProcResult(resistanceApplied, damageApplied, false);
    }

    public EnduranceResult resolveLethal(UUID battleId, UUID pokemonId, int beforeHp, int incomingDamage,
                                         boolean protectParticipated, long currentTick) {
        ActionBattleRockState state = state(battleId, pokemonId, currentTick);
        if (state == null || beforeHp <= 0 || incomingDamage < beforeHp || protectParticipated) return EnduranceResult.NONE;
        boolean reflect = state.enduranceRockTyped();
        if (!state.consumeEndurance(currentTick)) return EnduranceResult.NONE;
        return new EnduranceResult(true, 1, reflect ? ActionBattleRockRules.reflectionDamage(incomingDamage) : 0);
    }

    public Optional<ActionBattleRockState> view(UUID battleId, UUID pokemonId, long currentTick) {
        return Optional.ofNullable(state(battleId, pokemonId, currentTick));
    }

    public Optional<ActionBattleRockState.StockpileView> stockpileView(UUID battleId, UUID pokemonId, long currentTick) {
        ActionBattleRockState value = state(battleId, pokemonId, currentTick);
        return value == null ? Optional.empty() : value.stockpileView(currentTick);
    }

    public Optional<ActionBattleRockState.EnduranceView> enduranceView(UUID battleId, UUID pokemonId, long currentTick) {
        ActionBattleRockState value = state(battleId, pokemonId, currentTick);
        return value == null ? Optional.empty() : value.enduranceView(currentTick);
    }

    public void tickBattle(UUID battleId, long currentTick) {
        states.forEach((key, value) -> { if (key.battleId.equals(battleId)) value.tick(currentTick); });
        states.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
    public void onPokemonUnavailable(UUID battleId, UUID pokemonId) { states.remove(new Key(battleId, pokemonId)); }
    public void clearBattle(UUID battleId) { states.keySet().removeIf(key -> key.battleId.equals(battleId)); }
    public void clearAll() { states.clear(); }

    private int apply(UUID battle, UUID pokemon, ActionBattleStat stat, int amount, long tick) {
        if (amount <= 0) return 0;
        return stats.applyBatch(battle, pokemon, Map.of(stat, amount), tick,
                ActionBattleStatSource.ROCK_STOCKPILE, true).receiverStages().getOrDefault(stat, 0);
    }
    private ActionBattleRockState state(UUID battleId, UUID pokemonId, long tick) {
        if (battleId == null || pokemonId == null || tick < 0L) return null;
        ActionBattleRockState value = states.get(new Key(battleId, pokemonId));
        if (value != null) value.tick(tick);
        return value;
    }
    private record Key(UUID battleId, UUID pokemonId) {}
    public record ProcResult(int resistanceStages, int damageStages, boolean enduranceGranted) {
        public static final ProcResult NONE = new ProcResult(0, 0, false);
        public boolean occurred() { return resistanceStages != 0 || damageStages != 0 || enduranceGranted; }
    }
    public record EnduranceResult(boolean consumed, int holderHp, int reflectedDamage) {
        public static final EnduranceResult NONE = new EnduranceResult(false, 0, 0);
    }
}

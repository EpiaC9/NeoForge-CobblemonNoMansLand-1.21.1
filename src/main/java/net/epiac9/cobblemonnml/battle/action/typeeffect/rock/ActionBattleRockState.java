package net.epiac9.cobblemonnml.battle.action.typeeffect.rock;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.Optional;

public final class ActionBattleRockState {
    private Stockpile stockpile;
    private long enduranceEndTick = -1L;
    private boolean enduranceRockTyped;
    private int completedCycles;
    private long historyResetTick = -1L;

    public ApplyResult apply(ActionBattleRockSelection selection, boolean rockTyped, long currentTick) {
        if (selection == null || currentTick < 0L) return ApplyResult.INVALID;
        tick(currentTick);
        if (stockpile != null) return ApplyResult.IGNORED_ACTIVE;
        long duration = ActionBattleRockRules.stockpileDuration(completedCycles);
        stockpile = new Stockpile(selection, rockTyped, currentTick,
                ActionBattleTiming.safeAdd(currentTick, duration));
        historyResetTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleRockRules.HISTORY_RESET_TICKS);
        return ApplyResult.APPLIED;
    }

    public boolean tick(long currentTick) {
        if (currentTick < 0L) return false;
        boolean changed = false;
        if (stockpile != null && currentTick >= stockpile.endTick()) {
            stockpile = null;
            completedCycles = Math.min(4, completedCycles + 1);
            changed = true;
        }
        if (historyResetTick >= 0L && currentTick >= historyResetTick) {
            completedCycles = 0;
            historyResetTick = -1L;
            changed = true;
        }
        if (enduranceEndTick >= 0L && currentTick >= enduranceEndTick) {
            enduranceEndTick = -1L;
            enduranceRockTyped = false;
            changed = true;
        }
        return changed;
    }

    public boolean grantEndurance(long currentTick, boolean rockTyped) {
        tick(currentTick);
        if (currentTick < 0L || enduranceEndTick >= 0L) return false;
        enduranceEndTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleRockRules.ENDURANCE_TICKS);
        enduranceRockTyped = rockTyped;
        return true;
    }

    public boolean consumeEndurance(long currentTick) {
        tick(currentTick);
        if (enduranceEndTick < 0L) return false;
        enduranceEndTick = -1L;
        enduranceRockTyped = false;
        return true;
    }

    public Stockpile stockpile(long currentTick) { tick(currentTick); return stockpile; }
    public Optional<StockpileView> stockpileView(long currentTick) {
        tick(currentTick);
        return stockpile == null ? Optional.empty() : Optional.of(new StockpileView(
                Math.max(0L, stockpile.endTick() - currentTick),
                Math.max(1L, stockpile.endTick() - stockpile.startTick())));
    }
    public Optional<EnduranceView> enduranceView(long currentTick) {
        tick(currentTick);
        return enduranceEndTick < 0L ? Optional.empty() : Optional.of(new EnduranceView(
                Math.max(0L, enduranceEndTick - currentTick), ActionBattleRockRules.ENDURANCE_TICKS));
    }
    public boolean hasEndurance(long currentTick) { tick(currentTick); return enduranceEndTick >= 0L; }
    public long enduranceEndTick() { return enduranceEndTick; }
    public boolean enduranceRockTyped() { return enduranceRockTyped; }
    public int completedCycles() { return completedCycles; }
    public long historyResetTick() { return historyResetTick; }
    public boolean isEmpty() { return stockpile == null && enduranceEndTick < 0L && completedCycles == 0 && historyResetTick < 0L; }

    public record Stockpile(ActionBattleRockSelection selection, boolean rockTyped, long startTick, long endTick) {}
    public record StockpileView(long remainingTicks, long totalDurationTicks) {}
    public record EnduranceView(long remainingTicks, long totalDurationTicks) {}
    public enum ApplyResult { APPLIED, IGNORED_ACTIVE, INVALID }
}

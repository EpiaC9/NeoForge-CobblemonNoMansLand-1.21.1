package net.epiac9.cobblemonnml.battle.action.typeeffect.ground;

import java.util.Optional;

public final class ActionBattleGroundState {
    private int depthPercent;

    public ActionBattleGroundState(boolean ignoredGroundTyped) {}

    public ApplyResult apply(long currentTick) {
        requireTick(currentTick);
        if (depthPercent >= 100) return ApplyResult.ALREADY_FULL;
        boolean fresh = depthPercent == 0;
        depthPercent = Math.min(100, depthPercent + ActionBattleGroundRules.SINK_PER_SECOND);
        return depthPercent == 100 ? ApplyResult.FULLY_SUNK : fresh ? ApplyResult.APPLIED : ApplyResult.ADVANCED;
    }

    public TickResult tick(long currentTick) { requireTick(currentTick); return TickResult.NONE; }
    public boolean expel() { return clear(); }
    public void clearSilently() { depthPercent = 0; }
    public Optional<View> view(long currentTick) {
        requireTick(currentTick);
        return depthPercent == 0 ? Optional.empty() : Optional.of(new View(Branch.SINK, depthPercent, 0L, 0L));
    }
    public boolean groundTyped() { return false; }
    public Branch branch() { return Branch.SINK; }
    public boolean isEmpty() { return depthPercent == 0; }
    private boolean clear() { if (depthPercent == 0) return false; clearSilently(); return true; }
    private static void requireTick(long tick) { if (tick < 0L) throw new IllegalArgumentException("Ground tick cannot be negative."); }

    public enum Branch { SINK, DIG }
    public enum ApplyResult { APPLIED, ADVANCED, FULLY_SUNK, ALREADY_FULL, IGNORED }
    public enum TickResult { NONE, STAGE_RECOVERED, SILENT_CLEARED, NATURAL_EXPEL }
    public record View(Branch branch, int depthPercent, long stageRemainingTicks, long overallRemainingTicks) {}
}

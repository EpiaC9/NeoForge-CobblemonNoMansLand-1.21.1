package net.epiac9.cobblemonnml.battle.action.typeeffect.ground;

import java.util.Optional;

public final class ActionBattleGroundState {
    private final Branch branch;
    private int depthPercent;
    private long stageEndTick = -1L;
    private long overallEndTick = -1L;

    public ActionBattleGroundState(boolean groundTyped) {
        branch = groundTyped ? Branch.DIG : Branch.SINK;
    }

    public ApplyResult apply(long currentTick) {
        requireTick(currentTick);
        if (depthPercent == 0) {
            depthPercent = ActionBattleGroundRules.firstDepth(groundTyped());
            overallEndTick = currentTick + ActionBattleGroundRules.OVERALL_TICKS;
            stageEndTick = currentTick + stageDurationTicks();
            return ApplyResult.APPLIED;
        }
        if (depthPercent >= 90) return ApplyResult.ALREADY_FULL;
        depthPercent = Math.min(90, depthPercent + ActionBattleGroundRules.depthIncrement(groundTyped()));
        stageEndTick = currentTick + stageDurationTicks();
        return ApplyResult.ADVANCED;
    }

    public TickResult tick(long currentTick) {
        requireTick(currentTick);
        if (isEmpty()) return TickResult.NONE;
        if (currentTick >= overallEndTick) {
            clearSilently();
            return TickResult.SILENT_CLEARED;
        }
        if (currentTick < stageEndTick) return TickResult.NONE;
        if (groundTyped()) {
            if (depthPercent == 45) {
                clearSilently();
                return TickResult.NATURAL_EXPEL;
            }
            depthPercent = 45;
        } else {
            depthPercent = Math.max(0, depthPercent - 30);
            if (depthPercent == 0) {
                clearSilently();
                return TickResult.STAGE_RECOVERED;
            }
        }
        stageEndTick = currentTick + stageDurationTicks();
        return TickResult.STAGE_RECOVERED;
    }

    public boolean expel() {
        if (depthPercent != 90) return false;
        clearSilently();
        return true;
    }

    public void clearSilently() {
        depthPercent = 0;
        stageEndTick = -1L;
        overallEndTick = -1L;
    }

    public Optional<View> view(long currentTick) {
        requireTick(currentTick);
        if (isEmpty()) return Optional.empty();
        return Optional.of(new View(branch, depthPercent,
                Math.max(0L, stageEndTick - currentTick), Math.max(0L, overallEndTick - currentTick)));
    }

    public boolean groundTyped() { return branch == Branch.DIG; }
    public Branch branch() { return branch; }
    public boolean isEmpty() { return depthPercent == 0; }

    private long stageDurationTicks() {
        return ActionBattleGroundRules.stageDurationTicks(groundTyped());
    }

    private static void requireTick(long currentTick) {
        if (currentTick < 0L) throw new IllegalArgumentException("Ground state tick cannot be negative.");
    }

    public enum Branch { SINK, DIG }
    public enum ApplyResult { APPLIED, ADVANCED, ALREADY_FULL, IGNORED }
    public enum TickResult { NONE, STAGE_RECOVERED, SILENT_CLEARED, NATURAL_EXPEL }
    public record View(Branch branch, int depthPercent, long stageRemainingTicks, long overallRemainingTicks) {}
}

package net.epiac9.cobblemonnml.battle.action.control;

public final class ActionBattleControlState {
    public static final long TIMED_DURATION_TICKS = 120L;
    public static final long REPLACEMENT_DURATION_TICKS = 60L;
    public static final long GRACE_DURATION_TICKS = 60L;

    public enum ApplyResult { APPLIED_FULL, APPLIED_CONDITIONAL, REPLACED_HALF, REPLACED_CONDITIONAL, REJECTED_GRACE, REJECTED_SAME_EFFECT, REJECTED_INVALID }
    public enum EndReason { EXPIRED, CLEANSED, CONDITION_ENDED, SOURCE_ENDED, RECALLED, FAINTED }
    public enum EndResult { NONE, EXPIRED, CONDITION_ENDED, SOURCE_ENDED, RECALLED, FAINTED, CLEANSED }

    private ActionBattleControlEffect active;
    private long endTick;
    private long activeDurationTicks;
    private long graceEndTick;

    public ApplyResult apply(ActionBattleControlEffect effect, long currentTick) {
        if (effect == null || currentTick < 0L) return ApplyResult.REJECTED_INVALID;
        pruneGrace(currentTick);
        expireIfNeeded(currentTick);
        if (hasGrace(currentTick)) return ApplyResult.REJECTED_GRACE;
        if (active != null && active.type() == effect.type()) return ApplyResult.REJECTED_SAME_EFFECT;
        boolean replacing = active != null;
        active = effect;
        if (!effect.type().timed()) {
            endTick = Long.MAX_VALUE;
            activeDurationTicks = Long.MAX_VALUE;
            return replacing ? ApplyResult.REPLACED_CONDITIONAL : ApplyResult.APPLIED_CONDITIONAL;
        }
        activeDurationTicks = replacing ? REPLACEMENT_DURATION_TICKS : TIMED_DURATION_TICKS;
        endTick = safeAdd(currentTick, activeDurationTicks);
        return replacing ? ApplyResult.REPLACED_HALF : ApplyResult.APPLIED_FULL;
    }

    public EndResult tick(long currentTick) {
        if (currentTick < 0L) return EndResult.NONE;
        pruneGrace(currentTick);
        return expireIfNeeded(currentTick);
    }

    public boolean end(EndReason reason, long currentTick) {
        if (reason == null || currentTick < 0L || active == null) return false;
        active = null;
        endTick = 0L;
        activeDurationTicks = 0L;
        startGrace(currentTick);
        return true;
    }

    public EndResult endIfSource(java.util.UUID sourcePokemonUUID, long currentTick) {
        if (sourcePokemonUUID == null || currentTick < 0L || active == null || !sourcePokemonUUID.equals(active.sourcePokemonUUID())) return EndResult.NONE;
        ActionBattleControlType ended = active.type();
        active = null;
        endTick = 0L;
        activeDurationTicks = 0L;
        startGrace(currentTick);
        return ended == ActionBattleControlType.IMPRISON || ended == ActionBattleControlType.TRAPPED || ended == ActionBattleControlType.TORMENT ? EndResult.SOURCE_ENDED : EndResult.NONE;
    }

    public ActionBattleControlEffect activeEffect(long currentTick) {
        tick(currentTick);
        return active;
    }

    public ActionBattleControlType activeType(long currentTick) {
        ActionBattleControlEffect effect = activeEffect(currentTick);
        return effect != null ? effect.type() : null;
    }

    public long remainingTicks(long currentTick) {
        ActionBattleControlEffect effect = activeEffect(currentTick);
        if (effect == null) return 0L;
        return effect.type().timed() ? Math.max(0L, endTick - currentTick) : Long.MAX_VALUE;
    }

    public long activeDurationTicks(long currentTick) {
        return activeEffect(currentTick) != null ? activeDurationTicks : 0L;
    }

    public boolean hasGrace(long currentTick) {
        if (currentTick < 0L) return false;
        pruneGrace(currentTick);
        return graceEndTick > currentTick;
    }

    public long graceRemainingTicks(long currentTick) {
        return hasGrace(currentTick) ? Math.max(0L, graceEndTick - currentTick) : 0L;
    }

    public boolean isEmpty(long currentTick) { return activeEffect(currentTick) == null && !hasGrace(currentTick); }
    public void clearAll() { active = null; endTick = 0L; activeDurationTicks = 0L; graceEndTick = 0L; }

    private EndResult expireIfNeeded(long currentTick) {
        if (active == null || !active.type().timed() || endTick > currentTick) return EndResult.NONE;
        active = null;
        endTick = 0L;
        activeDurationTicks = 0L;
        startGrace(currentTick);
        return EndResult.EXPIRED;
    }

    private void startGrace(long currentTick) {
        if (graceEndTick <= currentTick) graceEndTick = safeAdd(currentTick, GRACE_DURATION_TICKS);
    }

    private void pruneGrace(long currentTick) {
        if (graceEndTick <= currentTick) graceEndTick = 0L;
    }

    private static long safeAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}

package net.epiac9.cobblemonnml.battle.action.control;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleReapplicationTracker;

import java.util.EnumMap;
import java.util.Map;

public final class ActionBattleControlState {
    public static final long TIMED_DURATION_TICKS = 120L;

    public enum ApplyResult { APPLIED_TIMED, APPLIED_CONDITIONAL, REPLACED_TIMED, REPLACED_CONDITIONAL, REJECTED_SAME_EFFECT, REJECTED_INVALID }
    public enum EndReason { EXPIRED, CLEANSED, CONDITION_ENDED, SOURCE_ENDED, RECALLED, FAINTED }
    public enum EndResult { NONE, EXPIRED, CONDITION_ENDED, SOURCE_ENDED, RECALLED, FAINTED, CLEANSED }

    private final Map<ActionBattleControlType, ActionBattleReapplicationTracker> reapplications = new EnumMap<>(ActionBattleControlType.class);
    private ActionBattleControlEffect active;
    private long endTick;
    private long activeDurationTicks;

    public ApplyResult apply(ActionBattleControlEffect effect, long currentTick) {
        if (effect == null || currentTick < 0L) return ApplyResult.REJECTED_INVALID;
        expireIfNeeded(currentTick);
        if (active != null && active.type() == effect.type()) return ApplyResult.REJECTED_SAME_EFFECT;
        boolean replacing = active != null;
        if (replacing) clearActive(currentTick);
        active = effect;
        if (!effect.type().timed()) {
            endTick = Long.MAX_VALUE;
            activeDurationTicks = Long.MAX_VALUE;
            return replacing ? ApplyResult.REPLACED_CONDITIONAL : ApplyResult.APPLIED_CONDITIONAL;
        }
        ActionBattleReapplicationTracker history = reapplications.computeIfAbsent(effect.type(), ignored -> new ActionBattleReapplicationTracker());
        activeDurationTicks = history.durationForApplication(TIMED_DURATION_TICKS, currentTick);
        endTick = ActionBattleTiming.safeAdd(currentTick, activeDurationTicks);
        return replacing ? ApplyResult.REPLACED_TIMED : ApplyResult.APPLIED_TIMED;
    }

    public EndResult tick(long currentTick) {
        if (currentTick < 0L) return EndResult.NONE;
        pruneReapplications(currentTick);
        return expireIfNeeded(currentTick);
    }

    public boolean end(EndReason reason, long currentTick) {
        if (reason == null || currentTick < 0L || active == null) return false;
        clearActive(currentTick);
        return true;
    }

    public EndResult endIfSource(java.util.UUID sourcePokemonUUID, long currentTick) {
        if (sourcePokemonUUID == null || currentTick < 0L || active == null || !sourcePokemonUUID.equals(active.sourcePokemonUUID())) return EndResult.NONE;
        ActionBattleControlType ended = active.type();
        clearActive(currentTick);
        return ended == ActionBattleControlType.IMPRISON || ended == ActionBattleControlType.TRAPPED || ended == ActionBattleControlType.TORMENT ? EndResult.SOURCE_ENDED : EndResult.NONE;
    }

    public ActionBattleControlEffect activeEffect(long currentTick) { tick(currentTick); return active; }

    public ActionBattleControlType activeType(long currentTick) {
        ActionBattleControlEffect effect = activeEffect(currentTick);
        return effect != null ? effect.type() : null;
    }

    public long remainingTicks(long currentTick) {
        ActionBattleControlEffect effect = activeEffect(currentTick);
        if (effect == null) return 0L;
        return effect.type().timed() ? Math.max(0L, endTick - currentTick) : Long.MAX_VALUE;
    }

    public long activeDurationTicks(long currentTick) { return activeEffect(currentTick) != null ? activeDurationTicks : 0L; }
    public boolean isEmpty(long currentTick) { tick(currentTick); return active == null && reapplications.isEmpty(); }

    public void clearAll() {
        active = null;
        endTick = 0L;
        activeDurationTicks = 0L;
        reapplications.clear();
    }

    private EndResult expireIfNeeded(long currentTick) {
        if (active == null || !active.type().timed() || endTick > currentTick) return EndResult.NONE;
        long naturalEndTick = endTick;
        ActionBattleControlType endedType = active.type();
        active = null;
        endTick = 0L;
        activeDurationTicks = 0L;
        reapplications.get(endedType).onEffectEnded(naturalEndTick);
        return EndResult.EXPIRED;
    }

    private void clearActive(long currentTick) {
        if (active != null && active.type().timed()) {
            reapplications.get(active.type()).onEffectEnded(currentTick);
        }
        active = null;
        endTick = 0L;
        activeDurationTicks = 0L;
    }

    private void pruneReapplications(long currentTick) {
        reapplications.entrySet().removeIf(entry -> !entry.getValue().isTracking(currentTick));
    }
}

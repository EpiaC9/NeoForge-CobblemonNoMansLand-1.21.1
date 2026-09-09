package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

public final class ActionBattleBugState {
    private final EnumMap<ActionBattleBugTrainingStat, Long> lockEnds = new EnumMap<>(ActionBattleBugTrainingStat.class);
    private final List<ScheduledDot> dots = new ArrayList<>();
    private int sheddingRemaining;
    private long sheddingEndsAt;
    private boolean sheddingBugTyped;
    private long physicalCarapaceEndsAt;
    private long effectZoneEndsAt;
    private long slowdownEndsAt;
    private double slowdownMultiplier = 1.0D;

    public TriggerResult trigger(EnumSet<ActionBattleBugTrainingStat> highest, boolean bugTyped,
                                 int maxHp, long tick) {
        if (highest == null || highest.isEmpty() || maxHp <= 0 || tick < 0L) return TriggerResult.NONE;
        EnumSet<ActionBattleBugTrainingStat> activated = EnumSet.noneOf(ActionBattleBugTrainingStat.class);
        EnumSet<ActionBattleBugTrainingStat> skipped = EnumSet.noneOf(ActionBattleBugTrainingStat.class);
        for (ActionBattleBugTrainingStat branch : highest) {
            if (locked(branch, tick)) skipped.add(branch);
            else {
                activated.add(branch);
                lockEnds.put(branch, tick + ActionBattleBugRules.LOCKOUT_TICKS);
            }
        }
        if (activated.contains(ActionBattleBugTrainingStat.HP)) {
            sheddingRemaining = ActionBattleBugRules.sheddingHp(maxHp, bugTyped);
            sheddingEndsAt = tick + ActionBattleBugRules.LOCKOUT_TICKS;
            sheddingBugTyped = bugTyped;
        }
        if (activated.contains(ActionBattleBugTrainingStat.DEFENSE)) {
            physicalCarapaceEndsAt = tick + ActionBattleBugRules.CARAPACE_TICKS;
        }
        if (activated.contains(ActionBattleBugTrainingStat.SPECIAL_DEFENSE)) {
            effectZoneEndsAt = tick + ActionBattleBugRules.LOCKOUT_TICKS;
        }
        if (activated.contains(ActionBattleBugTrainingStat.SPEED)) {
            slowdownEndsAt = tick + ActionBattleBugRules.LOCKOUT_TICKS;
            slowdownMultiplier = ActionBattleBugRules.slowdownMultiplier(bugTyped);
        }
        return new TriggerResult(activated, skipped, activated.contains(ActionBattleBugTrainingStat.HP)
                ? sheddingRemaining : 0);
    }

    public AbsorbResult absorb(int incomingDamage, long tick) {
        int safeDamage = Math.max(0, incomingDamage);
        if (safeDamage == 0 || sheddingRemaining <= 0 || tick < 0L || tick >= sheddingEndsAt) {
            sheddingRemaining = 0;
            return new AbsorbResult(0, safeDamage, 0);
        }
        int absorbed = Math.min(safeDamage, sheddingRemaining);
        int delayed = ActionBattleBugRules.delayedDotTotal(absorbed, sheddingBugTyped);
        List<Integer> amounts = ActionBattleBugRules.delayedDotTicks(delayed);
        for (int index = 0; index < amounts.size(); index++) {
            int amount = amounts.get(index);
            if (amount > 0) dots.add(new ScheduledDot(tick + (index + 1L) * ActionBattleBugRules.DOT_INTERVAL_TICKS, amount));
        }
        sheddingRemaining = 0;
        return new AbsorbResult(absorbed, safeDamage - absorbed, delayed);
    }

    public List<DotTick> tick(long tick) {
        if (tick < 0L) return List.of();
        if (tick >= sheddingEndsAt) sheddingRemaining = 0;
        List<DotTick> due = new ArrayList<>();
        Iterator<ScheduledDot> iterator = dots.iterator();
        while (iterator.hasNext()) {
            ScheduledDot scheduled = iterator.next();
            if (scheduled.tick <= tick) {
                due.add(new DotTick(scheduled.tick, scheduled.damage));
                iterator.remove();
            }
        }
        due.sort(java.util.Comparator.comparingLong(DotTick::scheduledTick));
        return List.copyOf(due);
    }

    public boolean locked(ActionBattleBugTrainingStat branch, long tick) {
        Long end = branch != null ? lockEnds.get(branch) : null;
        return end != null && tick >= 0L && tick < end;
    }

    public boolean sheddingActive(long tick) {
        return sheddingRemaining > 0 && tick >= 0L && tick < sheddingEndsAt;
    }

    public boolean physicalCarapaceActive(long tick) { return tick >= 0L && tick < physicalCarapaceEndsAt; }
    public boolean effectZoneActive(long tick) { return tick >= 0L && tick < effectZoneEndsAt; }
    public long physicalCarapaceRemaining(long tick) { return Math.max(0L, physicalCarapaceEndsAt - tick); }
    public long effectZoneRemaining(long tick) { return Math.max(0L, effectZoneEndsAt - tick); }
    public ActionBattleBugCarapaceState.Snapshot carapaceSnapshot(long tick) {
        long physicalRemaining = physicalCarapaceRemaining(tick);
        long zoneRemaining = effectZoneRemaining(tick);
        return new ActionBattleBugCarapaceState.Snapshot(physicalCarapaceActive(tick),
                effectZoneActive(tick), physicalRemaining, zoneRemaining);
    }
    public long slowdownRemaining(long tick) { return Math.max(0L, slowdownEndsAt - tick); }
    public void breakCarapace() { physicalCarapaceEndsAt = 0L; effectZoneEndsAt = 0L; }
    public double locomotionMultiplier(long tick) { return tick >= 0L && tick < slowdownEndsAt ? slowdownMultiplier : 1.0D; }
    public long lockRemaining(ActionBattleBugTrainingStat branch, long tick) {
        Long end = branch != null ? lockEnds.get(branch) : null;
        return end == null ? 0L : Math.max(0L, end - tick);
    }
    public boolean isEmpty(long tick) {
        tick(tick);
        return lockEnds.values().stream().noneMatch(end -> tick < end) && dots.isEmpty()
                && !physicalCarapaceActive(tick) && !effectZoneActive(tick) && locomotionMultiplier(tick) == 1.0D;
    }

    public record TriggerResult(EnumSet<ActionBattleBugTrainingStat> activated,
                                EnumSet<ActionBattleBugTrainingStat> skippedLocked,
                                int sheddingHp) {
        public static final TriggerResult NONE = new TriggerResult(EnumSet.noneOf(ActionBattleBugTrainingStat.class),
                EnumSet.noneOf(ActionBattleBugTrainingStat.class), 0);
        public TriggerResult {
            activated = activated.clone();
            skippedLocked = skippedLocked.clone();
        }
    }
    public record AbsorbResult(int absorbed, int remainingDamage, int delayedDotTotal) {}
    public record DotTick(long scheduledTick, int damage) {}
    private record ScheduledDot(long tick, int damage) {}
}

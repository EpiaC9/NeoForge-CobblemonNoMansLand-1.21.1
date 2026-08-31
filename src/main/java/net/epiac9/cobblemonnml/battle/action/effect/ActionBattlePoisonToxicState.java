package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattlePoisonToxicState {
    public static final long BASE_DURATION_TICKS = 360L;
    public static final long DOT_INTERVAL_TICKS = 20L;

    public enum Stage {
        POISON,
        TOXIC_1,
        TOXIC_2,
        TOXIC_3
    }

    public record DotTick(float currentHpFraction, boolean canKo) {}

    private Stage stage;
    private long expiresAtTick;
    private long nextDotTick;
    private int reapplicationCount;

    public ActionBattleStatusApplication apply(int strength, long currentTick) {
        return apply(strength, currentTick, 1.0F);
    }

    public ActionBattleStatusApplication apply(int strength, long currentTick, float durationMultiplier) {
        if (strength != 1 && strength != 2) throw new IllegalArgumentException("strength must be 1 or 2");
        if (currentTick < 0L) throw new IllegalArgumentException("currentTick cannot be negative");
        if (!(durationMultiplier > 0.0F)) throw new IllegalArgumentException("durationMultiplier must be positive");
        if (isExpired(currentTick)) clear();
        if (stage == null) {
            stage = strength == 1 ? Stage.POISON : Stage.TOXIC_1;
            expiresAtTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleTiming.scaledTicks(BASE_DURATION_TICKS, durationMultiplier));
            nextDotTick = ActionBattleTiming.safeAdd(currentTick, DOT_INTERVAL_TICKS);
            reapplicationCount = 0;
            return stage == Stage.POISON
                    ? ActionBattleStatusApplication.POISON_APPLIED
                    : ActionBattleStatusApplication.TOXIC_1_APPLIED;
        }

        expiresAtTick = ActionBattleTiming.safeAdd(expiresAtTick, ActionBattleTiming.scaledTicks(extensionTicksForNextReapplication(), durationMultiplier));
        reapplicationCount++;
        nextDotTick = ActionBattleTiming.safeAdd(currentTick, DOT_INTERVAL_TICKS);

        int currentIndex = stage.ordinal();
        int nextIndex = Math.min(Stage.TOXIC_3.ordinal(), currentIndex + strength);
        Stage previous = stage;
        stage = Stage.values()[nextIndex];

        if (stage == Stage.TOXIC_3 && previous == Stage.TOXIC_3) {
            return ActionBattleStatusApplication.TOXIC_3_REAPPLIED;
        }
        return switch (stage) {
            case POISON -> ActionBattleStatusApplication.POISON_APPLIED;
            case TOXIC_1 -> ActionBattleStatusApplication.TOXIC_1_APPLIED;
            case TOXIC_2 -> ActionBattleStatusApplication.TOXIC_2_APPLIED;
            case TOXIC_3 -> ActionBattleStatusApplication.TOXIC_3_APPLIED;
        };
    }

    public DotTick pollDot(long currentTick) {
        if (currentTick < 0L) return null;
        if (isExpired(currentTick)) return null;
        if (stage == null || nextDotTick <= 0L || currentTick < nextDotTick) return null;
        while (nextDotTick <= currentTick) nextDotTick = ActionBattleTiming.safeAdd(nextDotTick, DOT_INTERVAL_TICKS);
        return switch (stage) {
            case POISON -> new DotTick(0.03F, false);
            case TOXIC_1 -> new DotTick(0.03F, false);
            case TOXIC_2 -> new DotTick(0.06F, false);
            case TOXIC_3 -> new DotTick(0.09F, true);
        };
    }

    public void collapseToPoisonOnRecall(long currentTick) {
        if (currentTick < 0L) throw new IllegalArgumentException("currentTick cannot be negative");
        if (isExpired(currentTick) || stage == null) return;
        stage = Stage.POISON;
        nextDotTick = ActionBattleTiming.safeAdd(currentTick, DOT_INTERVAL_TICKS);
    }

    public boolean isExpired(long currentTick) {
        if (stage == null) return true;
        if (currentTick < 0L) return false;
        if (currentTick < expiresAtTick) return false;
        clear();
        return true;
    }

    public void clear() {
        stage = null;
        expiresAtTick = 0L;
        nextDotTick = 0L;
        reapplicationCount = 0;
    }

    public Stage stage() { return stage; }
    public long expiresAtTick() { return expiresAtTick; }
    public long nextDotTick() { return nextDotTick; }
    public int reapplicationCount() { return reapplicationCount; }
    public long remainingTicks(long currentTick) {
        if (isExpired(currentTick)) return 0L;
        return Math.max(0L, expiresAtTick - currentTick);
    }

    private long extensionTicksForNextReapplication() {
        return switch (reapplicationCount) {
            case 0 -> 120L;
            case 1 -> 80L;
            case 2 -> 40L;
            default -> 20L;
        };
    }


}

package net.epiac9.cobblemonnml.battle.action.typeeffect.dark;

public final class ActionBattleDarkState {
    private double currentAwareness = ActionBattleDarkRules.NORMAL_AWARENESS;
    private double instanceBaseAwareness = ActionBattleDarkRules.NORMAL_AWARENESS;
    private boolean blindnessActive;
    private long blindnessEndsAt;
    private int stackCount;
    private int awarenessReductionTotal;
    private long nextAwarenessRecoveryAt = -1L;
    private int obscurityStage;
    private int obscurityFloor;
    private long nextObscurityRecoveryAt = -1L;

    public HitResult applyHit(int awarenessReduction, int obscurityIncrease, long currentTick) {
        if (currentTick < 0L || awarenessReduction <= 0) return HitResult.IGNORED;
        tick(currentTick);
        boolean fresh = !blindnessActive;
        if (fresh) {
            blindnessActive = true;
            blindnessEndsAt = ActionBattleDarkRules.add(currentTick,
                    ActionBattleDarkRules.BLINDNESS_DURATION_TICKS);
            instanceBaseAwareness = currentAwareness;
            stackCount = 0;
            awarenessReductionTotal = 0;
            obscurityFloor = 0;
        } else {
            long remaining = Math.max(0L, blindnessEndsAt - currentTick);
            long extended = Math.min(ActionBattleDarkRules.BLINDNESS_DURATION_TICKS,
                    remaining + remaining / 2L);
            blindnessEndsAt = ActionBattleDarkRules.add(currentTick, extended);
        }
        stackCount++;
        awarenessReductionTotal += awarenessReduction;
        currentAwareness = Math.max(ActionBattleDarkRules.MINIMUM_AWARENESS,
                currentAwareness - awarenessReduction);
        currentAwareness = Math.min(currentAwareness, awarenessCap());
        int added = addObscurity(obscurityIncrease, currentTick);
        obscurityFloor = Math.min(obscurityStage, obscurityFloor + added);
        return new HitResult(fresh, added);
    }

    public void tick(long currentTick) {
        if (currentTick < 0L) return;
        if (blindnessActive && currentTick >= blindnessEndsAt) {
            recoverObscurityUntil(Math.max(0L, blindnessEndsAt - 1L));
            long expiredAt = blindnessEndsAt;
            blindnessActive = false;
            blindnessEndsAt = 0L;
            stackCount = 0;
            awarenessReductionTotal = 0;
            obscurityFloor = 0;
            nextAwarenessRecoveryAt = ActionBattleDarkRules.add(expiredAt,
                    ActionBattleDarkRules.AWARENESS_RECOVERY_INTERVAL_TICKS);
        }
        recoverAwarenessUntil(currentTick);
        recoverObscurityUntil(currentTick);
    }

    public void onSwapOrRecall(long currentTick) {
        if (currentTick < 0L) return;
        tick(currentTick);
        blindnessActive = false;
        blindnessEndsAt = 0L;
        stackCount = 0;
        awarenessReductionTotal = 0;
        obscurityFloor = 0;
        nextAwarenessRecoveryAt = currentAwareness < ActionBattleDarkRules.NORMAL_AWARENESS
                ? ActionBattleDarkRules.add(currentTick, ActionBattleDarkRules.AWARENESS_RECOVERY_INTERVAL_TICKS)
                : -1L;
    }

    public void onFaint() {
        currentAwareness = ActionBattleDarkRules.NORMAL_AWARENESS;
        instanceBaseAwareness = ActionBattleDarkRules.NORMAL_AWARENESS;
        blindnessActive = false;
        blindnessEndsAt = 0L;
        stackCount = 0;
        awarenessReductionTotal = 0;
        nextAwarenessRecoveryAt = -1L;
        obscurityStage = 0;
        obscurityFloor = 0;
        nextObscurityRecoveryAt = -1L;
    }

    public View view(long currentTick) {
        long remaining = blindnessActive ? Math.max(0L, blindnessEndsAt - currentTick) : 0L;
        return new View(blindnessActive, remaining, stackCount, currentAwareness, awarenessCap(),
                obscurityStage, obscurityFloor);
    }

    public boolean isEmpty() {
        return !blindnessActive && currentAwareness >= ActionBattleDarkRules.NORMAL_AWARENESS
                && obscurityStage == 0;
    }

    private double awarenessCap() {
        if (!blindnessActive) return ActionBattleDarkRules.NORMAL_AWARENESS;
        return Math.max(ActionBattleDarkRules.MINIMUM_AWARENESS,
                instanceBaseAwareness - awarenessReductionTotal);
    }

    private void recoverAwarenessUntil(long currentTick) {
        if (nextAwarenessRecoveryAt < 0L) return;
        while (nextAwarenessRecoveryAt <= currentTick) {
            currentAwareness = Math.min(awarenessCap(), currentAwareness + 1.0D);
            nextAwarenessRecoveryAt = ActionBattleDarkRules.add(nextAwarenessRecoveryAt,
                    ActionBattleDarkRules.AWARENESS_RECOVERY_INTERVAL_TICKS);
        }
        if (!blindnessActive && currentAwareness >= ActionBattleDarkRules.NORMAL_AWARENESS) {
            currentAwareness = ActionBattleDarkRules.NORMAL_AWARENESS;
            nextAwarenessRecoveryAt = -1L;
        }
    }

    private int addObscurity(int requestedIncrease, long currentTick) {
        if (requestedIncrease <= 0 || obscurityStage >= ActionBattleDarkRules.MAX_OBSCURITY_STAGE) return 0;
        obscurityStage++;
        if (nextObscurityRecoveryAt < 0L) {
            nextObscurityRecoveryAt = ActionBattleDarkRules.add(currentTick,
                    ActionBattleDarkRules.OBSCURITY_RECOVERY_INTERVAL_TICKS);
        }
        return 1;
    }

    private void recoverObscurityUntil(long currentTick) {
        if (nextObscurityRecoveryAt < 0L) return;
        while (nextObscurityRecoveryAt <= currentTick) {
            if (obscurityStage > obscurityFloor) obscurityStage--;
            nextObscurityRecoveryAt = ActionBattleDarkRules.add(nextObscurityRecoveryAt,
                    ActionBattleDarkRules.OBSCURITY_RECOVERY_INTERVAL_TICKS);
        }
        if (obscurityStage == 0) nextObscurityRecoveryAt = -1L;
    }

    public record HitResult(boolean freshInstance, int obscurityStagesAdded) {
        public static final HitResult IGNORED = new HitResult(false, 0);
    }

    public record View(boolean blindnessActive, long blindnessRemainingTicks, int stackCount,
                       double currentAwareness, double awarenessCap, int obscurityStage,
                       int obscurityFloor) {
        public int stage(ActionBattleObscurityElement element) {
            return element != null ? obscurityStage : 0;
        }
    }
}

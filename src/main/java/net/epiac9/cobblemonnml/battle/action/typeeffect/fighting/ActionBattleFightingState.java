package net.epiac9.cobblemonnml.battle.action.typeeffect.fighting;

public final class ActionBattleFightingState {
    private String trackedMoveId = "";
    private int buildupCount;
    private long buildupExpiresAtTick;
    private String lockedMoveId = "";
    private int lockedMoveSlot = -1;
    private long outrageEndsAtTick;
    private long exhaustedEndsAtTick;
    private boolean suppressNextActivationCooldown;

    public HitResult onSuccessfulHit(String moveId, boolean fightingMove, int moveSlot, long currentTick) {
        if (currentTick < 0L) return HitResult.IGNORED;
        advance(currentTick);
        if (exhaustedActive(currentTick)) return HitResult.BLOCKED_EXHAUSTED;
        if (outrageActive(currentTick) || !fightingMove) return HitResult.IGNORED;
        String normalized = ActionBattleFightingRules.normalizeMoveId(moveId);
        if (normalized.isEmpty()) return HitResult.IGNORED;
        if (!normalized.equals(trackedMoveId)) {
            clearBuildup();
            trackedMoveId = normalized;
        }
        buildupCount++;
        buildupExpiresAtTick = ActionBattleFightingRules.safeAdd(
                currentTick, ActionBattleFightingRules.BUILDUP_TIMEOUT_TICKS);
        if (buildupCount < ActionBattleFightingRules.BUILDUP_HITS) return HitResult.BUILT;
        lockedMoveId = trackedMoveId;
        lockedMoveSlot = moveSlot;
        outrageEndsAtTick = ActionBattleFightingRules.safeAdd(
                currentTick, ActionBattleFightingRules.OUTRAGE_DURATION_TICKS);
        clearBuildup();
        return HitResult.ACTIVATED;
    }

    public void onMoveUsed(String moveId, long currentTick) {
        if (currentTick < 0L) return;
        advance(currentTick);
        if (outrageActive(currentTick) || buildupCount <= 0) return;
        if (!ActionBattleFightingRules.normalizeMoveId(moveId).equals(trackedMoveId)) clearBuildup();
    }

    public void onMoveMissed(String moveId, long currentTick) {
        if (currentTick >= 0L) advance(currentTick);
    }

    public void onNonClearingEvent(long currentTick) {
        if (currentTick >= 0L) advance(currentTick);
    }

    public boolean clearOutrageEarly(long currentTick) {
        if (currentTick < 0L) return false;
        advance(currentTick);
        if (!outrageActive(currentTick)) return false;
        clearOutrage();
        return true;
    }

    public void armActivationCooldownSuppression(long currentTick) {
        if (currentTick < 0L) return;
        advance(currentTick);
        if (outrageActive(currentTick)) suppressNextActivationCooldown = true;
    }

    public boolean consumeActivationCooldownSuppression(String moveId, int moveSlot, long currentTick) {
        if (currentTick < 0L) return false;
        advance(currentTick);
        if (!suppressNextActivationCooldown || !outrageActive(currentTick)
                || !lockedMoveId.equals(ActionBattleFightingRules.normalizeMoveId(moveId))
                || lockedMoveSlot != moveSlot) return false;
        suppressNextActivationCooldown = false;
        return true;
    }

    public boolean canUseAbility(String moveId, long currentTick) {
        if (currentTick < 0L) return false;
        advance(currentTick);
        return !outrageActive(currentTick)
                || lockedMoveId.equals(ActionBattleFightingRules.normalizeMoveId(moveId));
    }

    public long sharedCooldownTicks(String moveId, boolean fightingHolder, long currentTick) {
        if (currentTick >= 0L) advance(currentTick);
        if (currentTick >= 0L && outrageActive(currentTick)
                && lockedMoveId.equals(ActionBattleFightingRules.normalizeMoveId(moveId))) {
            return fightingHolder
                    ? ActionBattleFightingRules.FIGHTING_OUTRAGE_SHARED_COOLDOWN_TICKS
                    : ActionBattleFightingRules.NORMAL_OUTRAGE_SHARED_COOLDOWN_TICKS;
        }
        return ActionBattleFightingRules.NORMAL_OUTRAGE_SHARED_COOLDOWN_TICKS;
    }

    public double outgoingDamageMultiplier(String moveId, boolean fightingHolder, long currentTick) {
        if (currentTick < 0L) return 1.0D;
        advance(currentTick);
        if (exhaustedActive(currentTick)) return ActionBattleFightingRules.EXHAUSTED_MULTIPLIER;
        if (!outrageActive(currentTick)
                || !lockedMoveId.equals(ActionBattleFightingRules.normalizeMoveId(moveId))) return 1.0D;
        return fightingHolder
                ? ActionBattleFightingRules.FIGHTING_OUTRAGE_DAMAGE_MULTIPLIER
                : ActionBattleFightingRules.NORMAL_OUTRAGE_DAMAGE_MULTIPLIER;
    }

    public double normalLocomotionMultiplier(long currentTick) {
        if (currentTick < 0L) return 1.0D;
        advance(currentTick);
        return exhaustedActive(currentTick) ? ActionBattleFightingRules.EXHAUSTED_MULTIPLIER : 1.0D;
    }

    public View view(long currentTick) {
        if (currentTick >= 0L) advance(currentTick);
        boolean outrage = currentTick >= 0L && outrageActive(currentTick);
        boolean exhausted = currentTick >= 0L && exhaustedActive(currentTick);
        return new View(trackedMoveId, buildupCount, buildupExpiresAtTick,
                outrage, lockedMoveId, lockedMoveSlot, outrageEndsAtTick,
                outrage ? Math.max(0L, outrageEndsAtTick - currentTick) : 0L,
                exhausted, exhaustedEndsAtTick,
                exhausted ? Math.max(0L, exhaustedEndsAtTick - currentTick) : 0L);
    }

    public boolean isEmpty(long currentTick) {
        View view = view(currentTick);
        return view.buildupCount() == 0 && !view.outrageActive() && !view.exhaustedActive();
    }

    private void advance(long currentTick) {
        if (buildupCount > 0 && currentTick >= buildupExpiresAtTick) clearBuildup();
        if (outrageEndsAtTick > 0L && currentTick >= outrageEndsAtTick) {
            long naturalEnd = outrageEndsAtTick;
            clearOutrage();
            exhaustedEndsAtTick = ActionBattleFightingRules.safeAdd(
                    naturalEnd, ActionBattleFightingRules.EXHAUSTED_DURATION_TICKS);
        }
        if (exhaustedEndsAtTick > 0L && currentTick >= exhaustedEndsAtTick) exhaustedEndsAtTick = 0L;
    }

    private boolean outrageActive(long currentTick) {
        return outrageEndsAtTick > currentTick;
    }

    private boolean exhaustedActive(long currentTick) {
        return exhaustedEndsAtTick > currentTick;
    }

    private void clearBuildup() {
        trackedMoveId = "";
        buildupCount = 0;
        buildupExpiresAtTick = 0L;
    }

    private void clearOutrage() {
        lockedMoveId = "";
        lockedMoveSlot = -1;
        outrageEndsAtTick = 0L;
        suppressNextActivationCooldown = false;
    }

    public enum HitResult { IGNORED, BLOCKED_EXHAUSTED, BUILT, ACTIVATED }

    public record View(String trackedMoveId, int buildupCount, long buildupExpiresAtTick,
                       boolean outrageActive, String lockedMoveId, int lockedMoveSlot,
                       long outrageEndsAtTick, long outrageRemainingTicks,
                       boolean exhaustedActive, long exhaustedEndsAtTick,
                       long exhaustedRemainingTicks) {}
}

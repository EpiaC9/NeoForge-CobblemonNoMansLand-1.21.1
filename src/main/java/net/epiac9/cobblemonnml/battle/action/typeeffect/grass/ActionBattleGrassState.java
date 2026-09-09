package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

import java.util.Optional;

public final class ActionBattleGrassState {
    private ActionBattleGrassEmpowerState empower;
    private ActionBattleGrassMovementBurstState movement;
    private ActionBattleLeechSeedState leechSeed;

    public record GrassMoveCommit(double capturedDamageMultiplier, boolean consumed) {}
    public record EmpowerView(double multiplier) {}
    public record MovementView(long remainingTicks, long totalDurationTicks, double multiplier) {}
    public record LeechSeedView(long remainingTicks, long endTick) {}

    public void applyEmpower(double multiplier) {
        if (empower == null) empower = new ActionBattleGrassEmpowerState();
        empower.replace(multiplier);
    }

    public GrassMoveCommit commitMove(boolean grassMove) {
        if (!grassMove || empower == null || !empower.active()) return new GrassMoveCommit(1.0D, false);
        empower.consume();
        empower = null;
        return new GrassMoveCommit(1.0D, true);
    }

    public void applyMovementBurst(long currentTick) {
        if (movement == null) movement = new ActionBattleGrassMovementBurstState();
        movement.apply(currentTick);
    }

    public double movementMultiplier(long currentTick) {
        prune(currentTick);
        return movement != null ? ActionBattleGrassRules.MOVEMENT_MULTIPLIER : 1.0D;
    }

    public boolean applyLeechSeed(long currentTick) {
        prune(currentTick);
        if (leechSeed != null) return false;
        leechSeed = new ActionBattleLeechSeedState(currentTick);
        return true;
    }

    public Optional<EmpowerView> empowerView() {
        return empower != null && empower.active() ? Optional.of(new EmpowerView(empower.multiplier())) : Optional.empty();
    }

    public Optional<MovementView> movementView(long currentTick) {
        prune(currentTick);
        return movement == null ? Optional.empty() : Optional.of(new MovementView(
                movement.remainingTicks(currentTick), movement.totalDurationTicks(), ActionBattleGrassRules.MOVEMENT_MULTIPLIER));
    }

    public Optional<LeechSeedView> leechSeedView(long currentTick) {
        prune(currentTick);
        return leechSeed == null ? Optional.empty() : Optional.of(new LeechSeedView(
                leechSeed.remainingTicks(currentTick), leechSeed.endTick()));
    }

    public void tick(long currentTick) { prune(currentTick); }
    public boolean isEmpty(long currentTick) { prune(currentTick); return empower == null && movement == null && leechSeed == null; }

    private void prune(long currentTick) {
        if (movement != null && !movement.active(currentTick)) movement = null;
        if (leechSeed != null && !leechSeed.active(currentTick)) leechSeed = null;
    }
}

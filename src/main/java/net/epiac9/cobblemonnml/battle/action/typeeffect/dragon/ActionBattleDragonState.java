package net.epiac9.cobblemonnml.battle.action.typeeffect.dragon;

public final class ActionBattleDragonState {
    private Phase phase = Phase.IDLE;
    private long maintenanceUntil;
    private long activationAt;
    private long phaseUntil;
    private long nextStatDecayAt;
    private int statLevel;
    private boolean dragonHolder;
    private boolean statSuppressedByHaze;

    public CommitResult onAbilityCommitted(boolean qualifyingOwnedAction, long currentTick) {
        if (currentTick < 0L) return CommitResult.IGNORED;
        expireFailedBuildup(currentTick);
        if (phase == Phase.IDLE && qualifyingOwnedAction) {
            phase = Phase.BUILDUP;
            maintenanceUntil = ActionBattleDragonRules.add(currentTick,
                    ActionBattleDragonRules.MAINTENANCE_DURATION_TICKS);
            activationAt = ActionBattleDragonRules.add(currentTick,
                    ActionBattleDragonRules.ACTIVATION_DURATION_TICKS);
            return CommitResult.BUILDUP_STARTED;
        }
        if (phase != Phase.BUILDUP) return CommitResult.IGNORED;
        maintenanceUntil = Math.min(ActionBattleDragonRules.add(currentTick,
                        ActionBattleDragonRules.MAINTENANCE_DURATION_TICKS),
                ActionBattleDragonRules.add(maintenanceUntil,
                        ActionBattleDragonRules.ABILITY_SUSTAIN_TICKS));
        return CommitResult.SUSTAINED;
    }

    public boolean onDamageTaken(long currentTick) {
        if (currentTick < 0L) return false;
        expireFailedBuildup(currentTick);
        if (phase != Phase.BUILDUP) return false;
        maintenanceUntil = Math.min(ActionBattleDragonRules.add(currentTick,
                        ActionBattleDragonRules.MAINTENANCE_DURATION_TICKS),
                ActionBattleDragonRules.add(maintenanceUntil,
                        ActionBattleDragonRules.DAMAGE_SUSTAIN_TICKS));
        return true;
    }

    public TickResult tick(long currentTick) {
        if (currentTick < 0L) return TickResult.NONE;
        if (phase == Phase.BUILDUP) {
            if (currentTick >= activationAt && currentTick < maintenanceUntil) {
                phase = Phase.ROARING;
                phaseUntil = ActionBattleDragonRules.add(currentTick,
                        ActionBattleDragonRules.ROAR_DURATION_TICKS);
                maintenanceUntil = 0L;
                activationAt = 0L;
                return TickResult.ROAR_STARTED;
            }
            if (currentTick >= maintenanceUntil) {
                clear();
                return TickResult.BUILDUP_FAILED;
            }
        } else if (phase == Phase.ROARING && currentTick >= phaseUntil) {
            phase = Phase.ACTIVE;
            phaseUntil = ActionBattleDragonRules.add(currentTick,
                    ActionBattleDragonRules.ACTIVE_DURATION_TICKS);
            statLevel = dragonHolder ? ActionBattleDragonRules.DRAGON_STAT_LEVEL
                    : ActionBattleDragonRules.NORMAL_STAT_LEVEL;
            nextStatDecayAt = ActionBattleDragonRules.add(currentTick, statDecayTicks());
            return TickResult.ACTIVE_STARTED;
        } else if (phase == Phase.ACTIVE) {
            while (statLevel > 0 && currentTick >= nextStatDecayAt) {
                statLevel--;
                nextStatDecayAt = ActionBattleDragonRules.add(nextStatDecayAt, statDecayTicks());
            }
            if (currentTick >= phaseUntil) {
                phase = Phase.SLEEP_PENDING;
                phaseUntil = 0L;
                nextStatDecayAt = 0L;
                statLevel = 0;
                return TickResult.ACTIVE_EXPIRED_APPLY_SLEEP;
            }
        }
        return TickResult.NONE;
    }

    public ExitResult exit(ExitReason reason, long currentTick) {
        if (reason == null || currentTick < 0L) return ExitResult.NONE;
        tick(currentTick);
        boolean sleep = (phase == Phase.ACTIVE || phase == Phase.SLEEP_PENDING)
                && (reason == ExitReason.SWAP || reason == ExitReason.RECALL);
        boolean existed = phase != Phase.IDLE;
        clear();
        if (sleep) return ExitResult.APPLY_SLEEP;
        return existed ? ExitResult.CLEARED : ExitResult.NONE;
    }

    public void suppressStatsByHaze() {
        if (phase == Phase.ACTIVE) {
            statLevel = 0;
            statSuppressedByHaze = true;
        }
    }

    public View view(long currentTick) {
        if (currentTick >= 0L) {
            expireFailedBuildup(currentTick);
        }
        long maintenance = phase == Phase.BUILDUP ? Math.max(0L, maintenanceUntil - currentTick) : 0L;
        long activation = phase == Phase.BUILDUP ? Math.max(0L, activationAt - currentTick) : 0L;
        long remaining = phase == Phase.ROARING || phase == Phase.ACTIVE
                ? Math.max(0L, phaseUntil - currentTick) : 0L;
        return new View(phase, maintenance, activation, remaining, statLevel,
                dragonHolder, statSuppressedByHaze);
    }

    public void setDragonHolder(boolean dragonHolder) { this.dragonHolder = dragonHolder; }
    public void finishSleepConsequence() { if (phase == Phase.SLEEP_PENDING) clear(); }
    public boolean isEmpty(long currentTick) { return view(currentTick).phase() == Phase.IDLE; }

    private long statDecayTicks() {
        return dragonHolder ? ActionBattleDragonRules.DRAGON_STAT_DECAY_TICKS
                : ActionBattleDragonRules.NORMAL_STAT_DECAY_TICKS;
    }

    private void expireFailedBuildup(long currentTick) {
        if (phase == Phase.BUILDUP && currentTick >= maintenanceUntil && currentTick < activationAt) clear();
    }

    public void clear() {
        phase = Phase.IDLE;
        maintenanceUntil = 0L;
        activationAt = 0L;
        phaseUntil = 0L;
        nextStatDecayAt = 0L;
        statLevel = 0;
        dragonHolder = false;
        statSuppressedByHaze = false;
    }

    public enum Phase { IDLE, BUILDUP, ROARING, ACTIVE, SLEEP_PENDING }
    public enum CommitResult { IGNORED, BUILDUP_STARTED, SUSTAINED }
    public enum TickResult { NONE, BUILDUP_FAILED, ROAR_STARTED, ACTIVE_STARTED, ACTIVE_EXPIRED_APPLY_SLEEP }
    public enum ExitReason { SWAP, RECALL, FAINT, HARD_CLEAR }
    public enum ExitResult { NONE, CLEARED, APPLY_SLEEP }

    public record View(Phase phase, long maintenanceRemainingTicks, long activationRemainingTicks,
                       long phaseRemainingTicks, int statLevel, boolean dragonHolder,
                       boolean statSuppressedByHaze) {}
}

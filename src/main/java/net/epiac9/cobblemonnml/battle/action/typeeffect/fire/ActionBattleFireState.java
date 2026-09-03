package net.epiac9.cobblemonnml.battle.action.typeeffect.fire;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

public final class ActionBattleFireState {
    private double pressure;
    private Phase phase;
    private long decayDelayEndTick = -1L;
    private long nextDecayTick = -1L;
    private long burnEndTick = -1L;
    private int ownedAttackStages;
    private boolean fireBonusSuppressedByHaze;

    public boolean applyPressure(double amount, long currentTick, boolean fireTyped, boolean hazeActive) {
        if (!(amount > 0.0D) || !Double.isFinite(amount) || currentTick < 0L || phase == Phase.BURN) return false;
        Phase previousPhase = phase;
        pressure = ActionBattleFireRules.clampPressure(pressure + amount);
        phase = phaseForPressure(pressure);
        decayDelayEndTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleFireRules.PRESSURE_DECAY_DELAY_TICKS);
        nextDecayTick = decayDelayEndTick;
        if (phase == Phase.BURN) {
            burnEndTick = ActionBattleTiming.safeAdd(currentTick, ActionBattleFireRules.BURN_DURATION_TICKS);
            decayDelayEndTick = -1L;
            nextDecayTick = -1L;
        }
        if (phase != previousPhase) establishAttackContribution(fireTyped, hazeActive);
        return true;
    }

    public boolean tick(long currentTick) {
        if (currentTick < 0L || phase == null) return false;
        if (phase == Phase.BURN) {
            if (currentTick < burnEndTick) return false;
            clear();
            return true;
        }
        boolean changed = false;
        while (nextDecayTick >= 0L && currentTick >= nextDecayTick && pressure > 0.0D) {
            pressure = ActionBattleFireRules.clampPressure(pressure - ActionBattleFireRules.PRESSURE_DECAY_AMOUNT);
            nextDecayTick = ActionBattleTiming.safeAdd(nextDecayTick, ActionBattleFireRules.PRESSURE_DECAY_STEP_TICKS);
            changed = true;
        }
        if (!changed) return false;
        Phase nextPhase = phaseForPressure(pressure);
        if (nextPhase != phase) {
            phase = nextPhase;
            if (phase != Phase.CINDERS && phase != Phase.BURN) ownedAttackStages = 0;
        }
        if (phase == null) clear();
        return true;
    }

    public void suppressFireBonusByHaze() {
        if (ownedAttackStages == 0) return;
        ownedAttackStages = 0;
        fireBonusSuppressedByHaze = true;
    }

    public double pressure() { return pressure; }
    public Phase phase() { return phase; }
    public long decayDelayEndTick() { return decayDelayEndTick; }
    public long nextDecayTick() { return nextDecayTick; }
    public long burnEndTick() { return burnEndTick; }
    public int ownedAttackStages() { return ownedAttackStages; }
    public boolean fireBonusSuppressedByHaze() { return fireBonusSuppressedByHaze; }
    public boolean isBurning() { return phase == Phase.BURN; }
    public boolean isEmpty() { return phase == null; }

    public long burnRemainingTicks(long currentTick) {
        return isBurning() && currentTick >= 0L ? Math.max(0L, burnEndTick - currentTick) : 0L;
    }

    private void establishAttackContribution(boolean fireTyped, boolean hazeActive) {
        if (!fireTyped || (phase != Phase.CINDERS && phase != Phase.BURN)) {
            ownedAttackStages = 0;
            return;
        }
        fireBonusSuppressedByHaze = hazeActive;
        ownedAttackStages = hazeActive ? 0 : phase == Phase.BURN
                ? ActionBattleFireRules.BURN_FIRE_ATTACK_STAGE
                : ActionBattleFireRules.CINDERS_FIRE_ATTACK_STAGE;
    }

    private void clear() {
        pressure = 0.0D;
        phase = null;
        decayDelayEndTick = -1L;
        nextDecayTick = -1L;
        burnEndTick = -1L;
        ownedAttackStages = 0;
        fireBonusSuppressedByHaze = false;
    }

    private static Phase phaseForPressure(double pressure) {
        if (pressure >= ActionBattleFireRules.BURN_THRESHOLD) return Phase.BURN;
        if (pressure >= ActionBattleFireRules.CINDERS_THRESHOLD) return Phase.CINDERS;
        return pressure > 0.0D ? Phase.BUILDUP : null;
    }

    public enum Phase { BUILDUP, CINDERS, BURN }
}

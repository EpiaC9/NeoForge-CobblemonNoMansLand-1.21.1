package net.epiac9.cobblemonnml.battle.action.typeeffect.fire;

public final class ActionBattleFirePressureState {
    public static final int MAX_PRESSURE = 100;
    public static final long DECAY_INTERVAL_TICKS = 20L;

    private int amount;
    private boolean inferno;
    private long lastDecayTick;
    private boolean initialized;

    public static boolean acceptsDamageGain(boolean fireMechanicIdentity, int actualDamage) {
        return fireMechanicIdentity && actualDamage > 0;
    }

    public Change addPressure(int suppliedAmount, long currentTick) {
        tick(currentTick);
        if (inferno || suppliedAmount <= 0) return new Change(false, false, amount);
        amount = Math.min(MAX_PRESSURE, amount + suppliedAmount);
        boolean entered = amount == MAX_PRESSURE;
        if (entered) inferno = true;
        return new Change(entered, false, amount);
    }

    public boolean consumeInfernoAmmo(int suppliedCost) {
        if (!inferno || suppliedCost <= 0) return false;
        amount = Math.max(0, amount - suppliedCost);
        if (amount == 0) inferno = false;
        return true;
    }

    public View view(long currentTick) { tick(currentTick); return new View(amount, inferno); }

    public void tick(long currentTick) {
        if (currentTick < 0L) throw new IllegalArgumentException("Fire Pressure tick cannot be negative.");
        if (!initialized) { initialized = true; lastDecayTick = currentTick; return; }
        if (inferno || amount == 0 || currentTick <= lastDecayTick) return;
        long intervals = (currentTick - lastDecayTick) / DECAY_INTERVAL_TICKS;
        if (intervals <= 0L) return;
        amount = Math.max(0, amount - (int) Math.min(Integer.MAX_VALUE, intervals));
        lastDecayTick += intervals * DECAY_INTERVAL_TICKS;
    }

    public boolean empty() { return amount == 0 && !inferno; }
    public record Change(boolean enteredInferno, boolean exitedInferno, int amount) {}
    public record View(int amount, boolean inferno) {}
}

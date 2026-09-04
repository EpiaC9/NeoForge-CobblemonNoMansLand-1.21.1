package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

public final class ActionBattleElectricState {
    private int charge;
    private long lastTick;

    public ActionBattleElectricState(int charge, long lastTick) {
        if (charge < 1 || charge >= ActionBattleElectricRules.MAX_CHARGE) {
            throw new IllegalArgumentException("Charge must be between 1 and 99.");
        }
        if (lastTick < 0L) throw new IllegalArgumentException("Tick cannot be negative.");
        this.charge = charge;
        this.lastTick = lastTick;
    }

    public int charge() { return charge; }
    public long lastTick() { return lastTick; }

    public int add(int amount, long currentTick) {
        validateTick(currentTick);
        if (currentTick < lastTick) throw new IllegalArgumentException("Tick cannot go backwards.");
        if (amount <= 0) throw new IllegalArgumentException("Charge amount must be positive.");
        long total = (long) charge + amount;
        charge = (int) Math.min(ActionBattleElectricRules.MAX_CHARGE, total);
        lastTick = currentTick;
        return charge;
    }

    public int depleteTo(long currentTick, int depletionPerTick) {
        validateTick(currentTick);
        if (depletionPerTick <= 0) throw new IllegalArgumentException("Depletion must be positive.");
        if (currentTick < lastTick) throw new IllegalArgumentException("Tick cannot go backwards.");
        long elapsed = currentTick - lastTick;
        long loss = elapsed > Long.MAX_VALUE / depletionPerTick
                ? Long.MAX_VALUE : elapsed * (long) depletionPerTick;
        charge = loss >= charge ? 0 : charge - (int) loss;
        lastTick = currentTick;
        return charge;
    }

    public boolean isEmpty() { return charge <= 0; }

    private static void validateTick(long tick) {
        if (tick < 0L) throw new IllegalArgumentException("Tick cannot be negative.");
    }
}
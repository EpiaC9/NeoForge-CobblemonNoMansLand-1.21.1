package net.epiac9.cobblemonnml.battle.action;

public record ActionBattlePosition(double x, double y, double z) {
    public ActionBattlePosition {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Position must be finite.");
    }
}

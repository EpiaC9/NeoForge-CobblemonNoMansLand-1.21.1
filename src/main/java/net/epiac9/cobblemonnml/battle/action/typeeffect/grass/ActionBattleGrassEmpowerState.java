package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

public final class ActionBattleGrassEmpowerState {
    private double multiplier;

    public void replace(double value) {
        if (value != ActionBattleGrassRules.ALLY_EMPOWER && value != ActionBattleGrassRules.GRASS_ALLY_EMPOWER) {
            throw new IllegalArgumentException("Grass Empower must use an approved multiplier.");
        }
        multiplier = value;
    }

    public double consume() {
        double value = multiplier;
        multiplier = 0.0D;
        return value;
    }

    public boolean active() { return multiplier > 0.0D; }
    public double multiplier() { return multiplier; }
}

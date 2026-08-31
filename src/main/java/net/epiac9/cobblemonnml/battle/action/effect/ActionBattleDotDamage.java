package net.epiac9.cobblemonnml.battle.action.effect;

public final class ActionBattleDotDamage {
    private ActionBattleDotDamage() {}

    public static int calculate(int maxHealth, int currentHealth, double maxHealthFraction) {
        int safeMaxHealth = Math.max(1, maxHealth);
        int safeCurrentHealth = Math.max(0, Math.min(currentHealth, safeMaxHealth));
        double currentHealthRatio = safeCurrentHealth / (double) safeMaxHealth;
        return Math.max(1, (int) Math.floor(safeMaxHealth * maxHealthFraction * currentHealthRatio));
    }
}

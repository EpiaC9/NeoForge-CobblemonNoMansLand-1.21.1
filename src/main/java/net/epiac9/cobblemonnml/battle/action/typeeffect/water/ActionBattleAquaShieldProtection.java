package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleDeterioratingShieldState;

public final class ActionBattleAquaShieldProtection {
    public record Result(int finalDamage, int finalDeterioratingShieldLevel,
                         boolean protectConsumed, boolean aquaShieldConsumed) {}

    private ActionBattleAquaShieldProtection() {}

    public static Result resolve(int damage, int deterioratingShieldLevel,
                                 boolean protectActive, boolean aquaShieldActive) {
        int safeDamage = Math.max(0, damage);
        int safeLevel = Math.clamp(deterioratingShieldLevel, 0, 9);
        if (!protectActive && !aquaShieldActive) return new Result(safeDamage, safeLevel, false, false);
        int finalDamage = Math.max(0, Math.round(safeDamage
                * ActionBattleDeterioratingShieldState.damageTakenMultiplierForLevel(safeLevel)));
        int finalLevel = aquaShieldActive && !protectActive ? Math.max(0, safeLevel - 1) : safeLevel;
        return new Result(finalDamage, finalLevel, protectActive, aquaShieldActive);
    }
}

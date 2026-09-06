package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

import java.util.Locale;

public final class ActionBattleGrassDeliveryRules {
    public static final int SEED_LOB_COUNT = 3;
    private ActionBattleGrassDeliveryRules() {}
    public static boolean qualifies(String moveType) { return moveType != null && "grass".equals(moveType.toLowerCase(Locale.ROOT)); }
    public static <T> T anchor(T caster, T affectedTarget) {
        if (caster == null) throw new IllegalArgumentException("Grass delivery requires a caster.");
        return affectedTarget != null ? affectedTarget : caster;
    }
}

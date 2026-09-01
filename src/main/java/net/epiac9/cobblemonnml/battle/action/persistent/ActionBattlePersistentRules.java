package net.epiac9.cobblemonnml.battle.action.persistent;

import java.util.Locale;

public final class ActionBattlePersistentRules {
    private ActionBattlePersistentRules() {}
    public static long boundDurationTicks(int roll) {
        int clamped = Math.max(0, Math.min(2, roll));
        return 160L + clamped * 20L;
    }
    public static boolean leechSeedImmune(String primaryType, String secondaryType) {
        return isGrass(primaryType) || isGrass(secondaryType);
    }
    public static boolean canApplyNightmare(boolean sleeping) { return sleeping; }
    private static boolean isGrass(String type) { return type != null && "grass".equals(type.toLowerCase(Locale.ROOT)); }
}

package net.epiac9.cobblemonnml.battle.action.persistent;

public final class ActionBattlePersistentRules {
    private ActionBattlePersistentRules() {}
    public static long boundDurationTicks(int roll) {
        int clamped = Math.max(0, Math.min(2, roll));
        return 160L + clamped * 20L;
    }
    public static boolean canApplyNightmare(boolean sleeping) { return sleeping; }
}

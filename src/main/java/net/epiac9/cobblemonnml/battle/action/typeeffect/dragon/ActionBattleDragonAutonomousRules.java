package net.epiac9.cobblemonnml.battle.action.typeeffect.dragon;

public final class ActionBattleDragonAutonomousRules {
    private ActionBattleDragonAutonomousRules() {}

    public static int chooseSlot(boolean[] usable, boolean[] damaging) {
        if (usable == null || damaging == null) return -1;
        int count = Math.min(usable.length, damaging.length);
        int support = -1;
        for (int slot = 0; slot < count; slot++) {
            if (!usable[slot]) continue;
            if (damaging[slot]) return slot;
            if (support < 0) support = slot;
        }
        return support;
    }
}

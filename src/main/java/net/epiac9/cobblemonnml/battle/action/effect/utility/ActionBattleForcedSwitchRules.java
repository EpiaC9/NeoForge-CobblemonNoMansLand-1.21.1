package net.epiac9.cobblemonnml.battle.action.effect.utility;

public final class ActionBattleForcedSwitchRules {
    public static final long SWAP_COOLDOWN_TICKS = 360L;

    private ActionBattleForcedSwitchRules() {}

    public static int nextUsable(int currentIndex, boolean[] usable) {
        if (usable == null || usable.length == 0) return -1;
        for (int offset = 1; offset <= usable.length; offset++) {
            int slot = Math.floorMod(currentIndex + offset, usable.length);
            if (slot != currentIndex && usable[slot]) return slot;
        }
        return -1;
    }
}

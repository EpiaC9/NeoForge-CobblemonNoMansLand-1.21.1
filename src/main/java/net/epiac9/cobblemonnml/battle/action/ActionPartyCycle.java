package net.epiac9.cobblemonnml.battle.action;

public final class ActionPartyCycle {
    private ActionPartyCycle() {}

    public static int nextUsableIndex(int currentIndex, boolean[] usable) {
        if (usable == null || usable.length == 0) return -1;
        int start = currentIndex >= 0 && currentIndex < usable.length ? currentIndex : -1;
        for (int offset = 1; offset <= usable.length; offset++) {
            int index = Math.floorMod(start + offset, usable.length);
            if (index == currentIndex) continue;
            if (usable[index]) return index;
        }
        return -1;
    }
}

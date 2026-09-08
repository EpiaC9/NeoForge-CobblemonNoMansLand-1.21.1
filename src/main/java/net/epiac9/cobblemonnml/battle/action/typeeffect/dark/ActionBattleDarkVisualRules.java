package net.epiac9.cobblemonnml.battle.action.typeeffect.dark;

public final class ActionBattleDarkVisualRules {
    public static final int MAX_PARTICLES = 8;

    private ActionBattleDarkVisualRules() {}

    public static int particleCount(boolean blindnessActive, int stacks) {
        if (!blindnessActive || stacks <= 0) return 0;
        return Math.min(MAX_PARTICLES, 1 + Math.max(0, stacks - 1) / 2);
    }

    public static int cadenceTicks(int stacks) {
        return Math.max(1, 6 - Math.max(1, stacks) / 2);
    }
}

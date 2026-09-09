package net.epiac9.cobblemonnml.battle.action.typeeffect.fighting;

import java.util.Locale;

public final class ActionBattleFightingRules {
    public static final int BUILDUP_HITS = 3;
    public static final long BUILDUP_TIMEOUT_TICKS = 360L;
    public static final long OUTRAGE_DURATION_TICKS = 180L;
    public static final long EXHAUSTED_DURATION_TICKS = 180L;
    public static final long NORMAL_OUTRAGE_SHARED_COOLDOWN_TICKS = 60L;
    public static final long FIGHTING_OUTRAGE_SHARED_COOLDOWN_TICKS = 30L;
    public static final double EXHAUSTED_MULTIPLIER = 0.80D;

    private ActionBattleFightingRules() {}

    public static OutrageStatPlan outrageStatPlan(boolean damaging, boolean special, boolean fightingTyped) {
        if (!damaging) return OutrageStatPlan.NONE;
        return new OutrageStatPlan(special ? OutrageStat.SPECIAL_ATTACK : OutrageStat.ATTACK,
                fightingTyped ? 2 : 1);
    }

    public static String normalizeMoveId(String moveId) {
        if (moveId == null) return "";
        String normalized = moveId.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(':');
        if (separator >= 0) normalized = normalized.substring(separator + 1);
        return normalized.replace("_", "").replace("-", "").replace(" ", "");
    }

    public static long safeAdd(long currentTick, long durationTicks) {
        if (currentTick < 0L || durationTicks <= 0L) return 0L;
        return currentTick > Long.MAX_VALUE - durationTicks ? Long.MAX_VALUE : currentTick + durationTicks;
    }

    public enum OutrageStat { NONE, ATTACK, SPECIAL_ATTACK }
    public record OutrageStatPlan(OutrageStat stat, int stages) {
        public static final OutrageStatPlan NONE = new OutrageStatPlan(OutrageStat.NONE, 0);
    }
}

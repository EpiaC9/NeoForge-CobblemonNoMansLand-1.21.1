package net.epiac9.cobblemonnml.battle.action.typeeffect.ghost;

import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ActionBattleGhostRules {
    public static final long TIMED_CURSE_TICKS = 60L;
    public static final long PENDING_CURSE_TICKS = 360L;
    public static final long DECAY_INTERVAL_TICKS = 10L;

    private static final List<ActionBattleStat> CURSE_STATS = List.of(
            ActionBattleStat.ATTACK,
            ActionBattleStat.DEFENSE,
            ActionBattleStat.SPECIAL_ATTACK,
            ActionBattleStat.SPECIAL_DEFENSE,
            ActionBattleStat.SPEED,
            ActionBattleStat.ACCURACY
    );

    private ActionBattleGhostRules() {}

    public static int safeSacrifice(int maximumHealth, int currentHealth, boolean ghostCaster) {
        if (maximumHealth <= 0 || currentHealth <= 1) return 0;
        int cost = roundedPercent(maximumHealth, ghostCaster ? 2 : 3);
        return currentHealth > cost ? cost : 0;
    }

    public static boolean isCurseImmune(String primaryType, String secondaryType) {
        return immuneType(primaryType) || immuneType(secondaryType);
    }

    public static ActionBattleGhostCurseType selectSpecialCurse(int randomIndex) {
        ActionBattleGhostCurseType[] values = ActionBattleGhostCurseType.values();
        return values[Math.floorMod(randomIndex, values.length)];
    }

    public static Optional<ActionBattleStat> selectEligibleStat(Map<ActionBattleStat, Integer> stages,
                                                                 int randomIndex) {
        List<ActionBattleStat> eligible = new ArrayList<>();
        for (ActionBattleStat stat : CURSE_STATS) {
            int stage = stages.getOrDefault(stat, 0);
            if (stage > -ActionBattleStatRules.maxStage(stat)) eligible.add(stat);
        }
        if (eligible.isEmpty()) return Optional.empty();
        return Optional.of(eligible.get(Math.floorMod(randomIndex, eligible.size())));
    }

    public static int decayDamage(int maximumHealth) {
        return roundedPercent(maximumHealth, 3);
    }

    public static int hungerDamage(int maximumHealth, int ppConsumed) {
        if (ppConsumed <= 0) return 0;
        return (int) Math.ceil(maximumHealth * 0.02D * ppConsumed);
    }

    public static int hauntingDamage(int actualDamage) {
        return roundedPercent(actualDamage, 25);
    }

    private static boolean immuneType(String type) {
        if (type == null) return false;
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "ghost", "normal", "dark" -> true;
            default -> false;
        };
    }

    private static int roundedPercent(int value, int percent) {
        if (value <= 0 || percent <= 0) return 0;
        return (int) Math.ceil(value * (percent / 100.0D));
    }
}

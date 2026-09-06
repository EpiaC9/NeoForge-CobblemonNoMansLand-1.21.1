package net.epiac9.cobblemonnml.battle.action.compat;

import java.util.Locale;

public final class ActionBattleMoveTargetRules {
    private ActionBattleMoveTargetRules() {}

    public static boolean usesCasterInSingles(String category) {
        String normalized = category != null ? category.toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "") : "";
        return normalized.equals("user") || normalized.equals("self") || normalized.contains("ally")
                || normalized.equals("userorally") || normalized.equals("allallies");
    }
}

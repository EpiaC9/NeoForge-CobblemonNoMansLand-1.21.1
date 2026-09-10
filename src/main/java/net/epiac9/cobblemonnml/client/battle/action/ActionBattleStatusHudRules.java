package net.epiac9.cobblemonnml.client.battle.action;

import java.util.Set;

public final class ActionBattleStatusHudRules {
    private static final int ICON_GAP = 2;
    private static final Set<String> PRESENCE_ONLY = Set.of(
            "TYPE_GHOST_FRAILTY", "TYPE_GHOST_WEAKNESS", "TYPE_GHOST_SILENCE",
            "TYPE_GHOST_BURDEN", "TYPE_GHOST_TORMENT");

    private ActionBattleStatusHudRules() {}

    public static boolean shouldDisplay(String statusId, long remainingTicks) {
        return remainingTicks > 0L || ActionBattleTypeMechanicVisualRegistry.isMechanic(statusId)
                || "TYPE_ICE_FREEZE".equals(statusId)
                || PRESENCE_ONLY.contains(statusId);
    }

    public static boolean hasCountdown(String statusId) {
        return statusId != null && !"TYPE_GRASS_EMPOWER".equals(statusId)
                && !PRESENCE_ONLY.contains(statusId);
    }

    public static int statusX(int panelX, int panelWidth, int index, boolean alignRight) {
        int iconSize = ActionBattleEffectIconRules.standardSize();
        int offset = Math.max(0, index) * (iconSize + ICON_GAP);
        return alignRight ? panelX + panelWidth - iconSize - offset : panelX + offset;
    }

    public static String ghostTextureName(String statusId) {
        if (statusId == null || !statusId.startsWith("TYPE_GHOST_")) return null;
        String suffix = statusId.substring("TYPE_GHOST_".length()).toLowerCase(java.util.Locale.ROOT);
        return switch (suffix) {
            case "frailty", "weakness", "silence", "decay", "withering", "burden",
                    "torment", "binding", "hunger", "misfortune", "haunting" -> suffix;
            default -> null;
        };
    }
}

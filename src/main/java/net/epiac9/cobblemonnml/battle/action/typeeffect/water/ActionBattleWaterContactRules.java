package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

import java.util.Locale;

public final class ActionBattleWaterContactRules {
    public enum ActivationResult { ALLY_SHIELD, ENEMY_WATER_HEALED, ENEMY_IMMOBILIZED }

    private ActionBattleWaterContactRules() {}

    public static ActivationResult resolveContact(boolean allied, boolean waterTyped) {
        if (allied) return ActivationResult.ALLY_SHIELD;
        return waterTyped ? ActivationResult.ENEMY_WATER_HEALED : ActivationResult.ENEMY_IMMOBILIZED;
    }

    public static int clampedHeal(int currentHealth, int maxHealth) {
        int maximum = Math.max(1, maxHealth);
        return Math.min(maximum, Math.max(0, currentHealth) + ActionBattleWaterRules.healAmount(maximum));
    }

    public static boolean isQualifyingInteraction(String moveType, boolean damaging, int movePower,
                                                   String targetCategory) {
        if (!"water".equals(normalize(moveType))) return false;
        return damaging || (movePower == 0 && enemyTargetCategory(targetCategory));
    }

    private static boolean enemyTargetCategory(String targetCategory) {
        String normalized = normalize(targetCategory).replace("_", "").replace("-", "");
        return normalized.contains("foe") || normalized.contains("enemy") || normalized.contains("opponent")
                || normalized.equals("normal") || normalized.equals("adjacentpokemon");
    }

    private static String normalize(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT) : "";
    }
}

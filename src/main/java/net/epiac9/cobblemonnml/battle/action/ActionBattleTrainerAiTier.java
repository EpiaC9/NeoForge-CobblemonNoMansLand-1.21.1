package net.epiac9.cobblemonnml.battle.action;

import java.util.Locale;

public final class ActionBattleTrainerAiTier {
    private ActionBattleTrainerAiTier() {}

    public static Profile profile(int tier) {
        return switch (Math.max(1, Math.min(4, tier))) {
            case 1 -> new Profile(1, false, false, false, 0, 0, 0, 0);
            case 2 -> new Profile(2, false, false, false, 45, 0, 0, 10);
            case 3 -> new Profile(3, true, true, false, 55, 120, 1, 20);
            default -> new Profile(4, true, true, true, 65, 220, 2, 35);
        };
    }


    public static int moveScore(int tier, boolean canCommitNow, double typeMultiplier, int power, int priority, double hpRatio) {
        Profile profile = profile(tier);
        if (profile.tier() == 1) return 0;
        int score = canCommitNow ? profile.geometryWeight() : 0;
        if (profile.usesTypeEffectiveness()) {
            if (typeMultiplier <= 0.0D) score -= profile.typeWeight() * 4;
            else score += (int) Math.round((typeMultiplier - 1.0D) * profile.typeWeight());
        }
        if (profile.usesMovePower()) score += Math.max(0, power) * profile.powerWeight();
        if (profile.usesPriority()) {
            int urgency = hpRatio <= 0.35D ? 20 : 10;
            score += priority * urgency;
        }
        return score;
    }

    public static int swapScore(int tier, int engagementScore, double hpRatio, double bestTypeMultiplier) {
        Profile profile = profile(tier);
        int score = engagementScore * 100;
        if (profile.tier() >= 2) score += (int) Math.round(Math.max(0.0D, Math.min(1.0D, hpRatio)) * profile.hpWeight());
        if (profile.usesTypeEffectiveness()) score += (int) Math.round((bestTypeMultiplier - 1.0D) * profile.typeWeight());
        return score;
    }

    public static double typeMultiplier(String attackingType, String primaryDefender, String secondaryDefender) {
        double first = singleTypeMultiplier(normalize(attackingType), normalize(primaryDefender));
        double second = secondaryDefender == null || secondaryDefender.isBlank() ? 1.0D
                : singleTypeMultiplier(normalize(attackingType), normalize(secondaryDefender));
        return first * second;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }

    private static double singleTypeMultiplier(String attack, String defend) {
        if (attack.isEmpty() || defend.isEmpty()) return 1.0D;
        if (isImmune(attack, defend)) return 0.0D;
        if (isSuperEffective(attack, defend)) return 2.0D;
        if (isResisted(attack, defend)) return 0.5D;
        return 1.0D;
    }

    private static boolean isImmune(String a, String d) {
        return (a.equals("normal") && d.equals("ghost"))
                || (a.equals("fighting") && d.equals("ghost"))
                || (a.equals("poison") && d.equals("steel"))
                || (a.equals("ground") && d.equals("flying"))
                || (a.equals("ghost") && d.equals("normal"))
                || (a.equals("electric") && d.equals("ground"))
                || (a.equals("psychic") && d.equals("dark"))
                || (a.equals("dragon") && d.equals("fairy"));
    }

    private static boolean isSuperEffective(String a, String d) {
        return switch (a) {
            case "normal" -> false;
            case "fire" -> any(d, "grass", "ice", "bug", "steel");
            case "water" -> any(d, "fire", "ground", "rock");
            case "electric" -> any(d, "water", "flying");
            case "grass" -> any(d, "water", "ground", "rock");
            case "ice" -> any(d, "grass", "ground", "flying", "dragon");
            case "fighting" -> any(d, "normal", "ice", "rock", "dark", "steel");
            case "poison" -> any(d, "grass", "fairy");
            case "ground" -> any(d, "fire", "electric", "poison", "rock", "steel");
            case "flying" -> any(d, "grass", "fighting", "bug");
            case "psychic" -> any(d, "fighting", "poison");
            case "bug" -> any(d, "grass", "psychic", "dark");
            case "rock" -> any(d, "fire", "ice", "flying", "bug");
            case "ghost" -> any(d, "psychic", "ghost");
            case "dragon" -> d.equals("dragon");
            case "dark" -> any(d, "psychic", "ghost");
            case "steel" -> any(d, "ice", "rock", "fairy");
            case "fairy" -> any(d, "fighting", "dragon", "dark");
            default -> false;
        };
    }

    private static boolean isResisted(String a, String d) {
        return switch (a) {
            case "normal" -> any(d, "rock", "steel");
            case "fire" -> any(d, "fire", "water", "rock", "dragon");
            case "water" -> any(d, "water", "grass", "dragon");
            case "electric" -> any(d, "electric", "grass", "dragon");
            case "grass" -> any(d, "fire", "grass", "poison", "flying", "bug", "dragon", "steel");
            case "ice" -> any(d, "fire", "water", "ice", "steel");
            case "fighting" -> any(d, "poison", "flying", "psychic", "bug", "fairy");
            case "poison" -> any(d, "poison", "ground", "rock", "ghost");
            case "ground" -> any(d, "grass", "bug");
            case "flying" -> any(d, "electric", "rock", "steel");
            case "psychic" -> any(d, "psychic", "steel");
            case "bug" -> any(d, "fire", "fighting", "poison", "flying", "ghost", "steel", "fairy");
            case "rock" -> any(d, "fighting", "ground", "steel");
            case "ghost" -> d.equals("dark");
            case "dragon" -> d.equals("steel");
            case "dark" -> any(d, "fighting", "dark", "fairy");
            case "steel" -> any(d, "fire", "water", "electric", "steel");
            case "fairy" -> any(d, "fire", "poison", "steel");
            default -> false;
        };
    }

    private static boolean any(String value, String... options) {
        for (String option : options) if (value.equals(option)) return true;
        return false;
    }

    public record Profile(int tier, boolean usesTypeEffectiveness, boolean usesMovePower, boolean usesPriority,
                          int geometryWeight, int typeWeight, int powerWeight, int hpWeight) {}
}

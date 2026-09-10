package net.epiac9.cobblemonnml.battle.action.effect.control;

public final class ActionBattleInfatuationRules {
    public static final long DURATION_TICKS = 60L;
    public static final double WALK_SPEED = 0.30D;
    public static final double MALE_TO_FEMALE_DISTANCE = 3.0D;
    public static final double FEMALE_TO_MALE_DISTANCE = 5.0D;

    private ActionBattleInfatuationRules() {}

    public static boolean eligible(String source, String target) {
        String sourceGender = normalize(source);
        String targetGender = normalize(target);
        return !sourceGender.isEmpty() && !targetGender.isEmpty()
                && !"genderless".equals(sourceGender) && !"genderless".equals(targetGender)
                && !sourceGender.equals(targetGender);
    }

    public static double approachDistance(String source, String target) {
        if (!eligible(source, target)) return 0.0D;
        return "male".equals(normalize(source)) ? MALE_TO_FEMALE_DISTANCE : FEMALE_TO_MALE_DISTANCE;
    }

    private static String normalize(String value) {
        return value != null ? value.trim().toLowerCase(java.util.Locale.ROOT) : "";
    }
}

package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

public record ActionBattleBugCarapaceVisualPlan(String speciesId, String formId, String aspectsCsv,
                                                 String animationName, int rotation45, long stoneSeed) {
    private static final double SHELL_SCALE = 1.08D;
    private static final double ZONE_RADIUS = ActionBattleBugRules.CONSTRUCT_RADIUS;
    public ActionBattleBugCarapaceVisualPlan {
        speciesId = speciesId == null || speciesId.isBlank() ? "cobblemon:missingno" : speciesId;
        formId = formId == null || formId.isBlank() ? "normal" : formId;
        aspectsCsv = aspectsCsv == null ? "" : aspectsCsv;
        animationName = animationName == null ? "" : animationName;
        rotation45 = Math.floorMod(rotation45, 8);
    }

    public static ActionBattleBugCarapaceVisualPlan from(String speciesId, String formId,
                                                          String aspectsCsv, float yaw, long seed) {
        return from(speciesId, formId, aspectsCsv, "", yaw, seed);
    }

    public static ActionBattleBugCarapaceVisualPlan from(String speciesId, String formId,
                                                          String aspectsCsv, String animationName,
                                                          float yaw, long seed) {
        return new ActionBattleBugCarapaceVisualPlan(speciesId, formId, aspectsCsv, animationName,
                Math.round(yaw / 45.0F), seed);
    }

    public double shellScale() { return SHELL_SCALE; }
    public double zoneRadius() { return ZONE_RADIUS; }
    public double zoneX(int point, int segments) { return ZONE_RADIUS * Math.cos(angle(point, segments)); }
    public double zoneZ(int point, int segments) { return ZONE_RADIUS * Math.sin(angle(point, segments)); }

    private static double angle(int point, int segments) {
        int safeSegments = Math.max(3, segments);
        return Math.floorMod(point, safeSegments) * Math.PI * 2.0D / safeSegments;
    }
}

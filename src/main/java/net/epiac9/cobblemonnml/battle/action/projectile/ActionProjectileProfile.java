package net.epiac9.cobblemonnml.battle.action.projectile;

import java.util.List;

public final class ActionProjectileProfile {
    private static final double DEFAULT_SPEED = 0.70D;
    private static final int DEFAULT_MAX_LIFETIME_TICKS = 80;
    private static final double RANGED_COMMIT_DISTANCE = 12.0D;

    private ActionProjectileProfile() {}

    public static double rangedCommitDistance() {
        return RANGED_COMMIT_DISTANCE;
    }

    public static double speedBlocksPerTick(String moveName) {
        if (moveName == null) return DEFAULT_SPEED;
        return switch (moveName.toLowerCase()) {
            case "shadowball" -> 0.35D;
            case "icebeam" -> 0.55D;
            default -> DEFAULT_SPEED;
        };
    }

    public static int maxLifetimeTicks(String moveName) {
        return DEFAULT_MAX_LIFETIME_TICKS;
    }

    public static List<String> nativeCobblemonEffects(String moveName) {
        if (moveName == null) return List.of();
        return switch (moveName.toLowerCase()) {
            case "shadowball" -> List.of("cobblemon:shadowball_actorblob", "cobblemon:shadowball_actorflaring");
            default -> List.of();
        };
    }
}

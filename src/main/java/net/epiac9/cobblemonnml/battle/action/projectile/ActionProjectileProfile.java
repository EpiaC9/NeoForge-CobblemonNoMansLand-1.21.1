package net.epiac9.cobblemonnml.battle.action.projectile;

import java.util.List;
import java.util.Locale;

public final class ActionProjectileProfile {
    public static final double BEAM_BOLT_SPEED = 0.75D;
    public static final double NORMAL_PROJECTILE_SPEED = 0.35D;
    public static final double WAVE_AREA_SPEED = 0.08D;
    public static final double HOMING_GUIDED_SPEED = 0.15D;
    public static final double ARCING_LOBBED_SPEED = 0.45D;
    public static final double GROUNDED_SURFACE_SPEED = 0.10D;
    public static final double INSTANT_SPEED = 1.0D;
    public static final double TARGET_LOCKED_SPEED = 1.0D;
    public static final double DASH_RANGE_BONUS = 3.0D;
    private static final int DEFAULT_MAX_LIFETIME_TICKS = 80;
    private static final double RANGED_COMMIT_DISTANCE = 12.0D;

    private ActionProjectileProfile() {}

    public static double rangedCommitDistance() {
        return RANGED_COMMIT_DISTANCE;
    }

    public static ActionMoveDeliveryType deliveryType(String moveName) {
        String id = normalize(moveName);
        return switch (id) {
            case "icebeam", "inferno", "scald", "steameruption", "flamethrower", "iceburn" -> ActionMoveDeliveryType.BEAM_BOLT;
            case "lavaplume", "searingshot", "matchagotcha", "heatwave" -> ActionMoveDeliveryType.WAVE_AREA;
            case "willowisp", "infernalparade" -> ActionMoveDeliveryType.HOMING_GUIDED;
            case "pyroball" -> ActionMoveDeliveryType.ARCING_LOBBED;
            case "scorchingsands", "sandsearstorm" -> ActionMoveDeliveryType.GROUNDED_SURFACE;
            case "firepunch", "firefang", "blazekick" -> ActionMoveDeliveryType.PHYSICAL_CONTACT;
            case "sizzlyslide", "blazingtorque", "flamewheel", "flareblitz" -> ActionMoveDeliveryType.DASH_RUSH;
            default -> ActionMoveDeliveryType.NORMAL_PROJECTILE;
        };
    }

    public static double speedBlocksPerTick(String moveName) {
        return switch (deliveryType(moveName)) {
            case BEAM_BOLT -> BEAM_BOLT_SPEED;
            case WAVE_AREA -> WAVE_AREA_SPEED;
            case HOMING_GUIDED -> HOMING_GUIDED_SPEED;
            case ARCING_LOBBED -> ARCING_LOBBED_SPEED;
            case GROUNDED_SURFACE -> GROUNDED_SURFACE_SPEED;
            case INSTANT -> INSTANT_SPEED;
            case TARGET_LOCKED -> TARGET_LOCKED_SPEED;
            default -> NORMAL_PROJECTILE_SPEED;
        };
    }

    public static double dashRangeBonus(String moveName) {
        return deliveryType(moveName) == ActionMoveDeliveryType.DASH_RUSH ? DASH_RANGE_BONUS : 0.0D;
    }

    public static boolean isDashRush(String moveName) {
        return deliveryType(moveName) == ActionMoveDeliveryType.DASH_RUSH;
    }

    public static boolean isHoming(String moveName) {
        return deliveryType(moveName) == ActionMoveDeliveryType.HOMING_GUIDED;
    }

    public static boolean isLobbed(String moveName) {
        return deliveryType(moveName) == ActionMoveDeliveryType.ARCING_LOBBED;
    }

    public static boolean isGrounded(String moveName) {
        return deliveryType(moveName) == ActionMoveDeliveryType.GROUNDED_SURFACE;
    }

    public static int visualProjectileCount(String moveName) {
        return "triattack".equals(normalize(moveName)) ? 3 : 1;
    }

    public static int maxLifetimeTicks(String moveName) {
        return DEFAULT_MAX_LIFETIME_TICKS;
    }

    public static List<String> nativeCobblemonEffects(String moveName) {
        String id = normalize(moveName);
        return switch (id) {
            case "shadowball" -> List.of("cobblemon:shadowball_actorblob", "cobblemon:shadowball_actorflaring");
            case "icebeam", "iceburn" -> List.of("cobblemon:icebeam_actorpilot");
            case "willowisp" -> List.of("cobblemon:willowisp_actor");
            case "lavaplume", "searingshot", "heatwave" -> List.of("cobblemon:lavaplume_actor");
            case "ember" -> List.of("cobblemon:ember_actor");
            case "fireblast", "sacredfire", "blueflare" -> List.of("cobblemon:fireblast_actor1", "cobblemon:fireblast_actor2");
            case "flamethrower", "inferno" -> List.of("cobblemon:flamethrower_actor");
            case "infernalparade" -> List.of("cobblemon:willowisp_actor", "cobblemon:shadowball_actorflaring");
            case "scald", "steameruption" -> List.of("cobblemon:bubblebeam_actor");
            case "scorchingsands", "sandsearstorm" -> List.of("cobblemon:bulldoze_targetfloor");
            case "matchagotcha" -> List.of("cobblemon:magicalleaf_actor", "cobblemon:bubblebeam_actor");
            case "pyroball" -> List.of("cobblemon:fireblast_actor1");
            case "triattack" -> List.of("cobblemon:fireblast_actor1", "cobblemon:icebeam_actorpilot", "cobblemon:thunderbolt_actor");
            default -> List.of();
        };
    }

    private static String normalize(String moveName) {
        return moveName == null ? "" : moveName.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
    }
}

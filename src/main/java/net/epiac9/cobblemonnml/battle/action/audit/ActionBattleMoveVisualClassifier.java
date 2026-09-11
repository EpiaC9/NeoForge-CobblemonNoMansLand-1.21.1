package net.epiac9.cobblemonnml.battle.action.audit;

import com.google.gson.JsonElement;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveFlag;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveMetadataRules;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionMoveDeliveryType;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Audit-only visual classification. This does not change ACTION move delivery or gameplay behavior.
 */
public final class ActionBattleMoveVisualClassifier {
    private ActionBattleMoveVisualClassifier() {}

    public static Classification classify(String type,
                                          String category,
                                          String target,
                                          Set<String> flags,
                                          Map<String, JsonElement> effectMetadata,
                                          ActionMoveDeliveryType delivery,
                                          boolean explicitDeliveryProfile) {
        Set<String> safeFlags = flags == null ? Set.of() : flags;
        Map<String, JsonElement> effects = effectMetadata == null ? Map.of() : effectMetadata;

        if (explicitDeliveryProfile) {
            return new Classification(fromExplicitDelivery(delivery), "explicit_delivery", Confidence.HIGH);
        }

        if (hasAny(effects, "weather", "terrain", "pseudoWeather")) {
            return new Classification(ActionBattleMoveVisualFamily.WEATHER_ARENA, "canonical_field", Confidence.HIGH);
        }
        if (hasAny(effects, "sideCondition", "slotCondition")) {
            return new Classification(ActionBattleMoveVisualFamily.CONSTRUCT_WORLD, "canonical_field", Confidence.HIGH);
        }
        if (ActionBattleMoveMetadataRules.hasCanonicalFlag(safeFlags, ActionBattleMoveFlag.SOUND)) {
            return new Classification(ActionBattleMoveVisualFamily.SOUND, "canonical_flag:sound", Confidence.HIGH);
        }
        if (ActionBattleMoveMetadataRules.hasCanonicalFlag(safeFlags, ActionBattleMoveFlag.POWDER)) {
            return new Classification(ActionBattleMoveVisualFamily.POWDER_CLOUD, "canonical_flag:powder", Confidence.HIGH);
        }
        if (ActionBattleMoveMetadataRules.hasCanonicalFlag(safeFlags, ActionBattleMoveFlag.SLICING)) {
            return new Classification(ActionBattleMoveVisualFamily.SLASH_ARC, "canonical_flag:slicing", Confidence.HIGH);
        }
        if (ActionBattleMoveMetadataRules.hasCanonicalFlag(safeFlags, ActionBattleMoveFlag.PUNCH)) {
            return new Classification(ActionBattleMoveVisualFamily.PUNCH_CONTACT, "canonical_flag:punch", Confidence.HIGH);
        }
        if (ActionBattleMoveMetadataRules.hasCanonicalFlag(safeFlags, ActionBattleMoveFlag.BITE)) {
            return new Classification(ActionBattleMoveVisualFamily.BITE_CONTACT, "canonical_flag:bite", Confidence.HIGH);
        }
        if (ActionBattleMoveMetadataRules.hasCanonicalFlag(safeFlags, ActionBattleMoveFlag.WIND)) {
            return new Classification(ActionBattleMoveVisualFamily.WIND_GUST, "canonical_flag:wind", Confidence.HIGH);
        }
        if (ActionBattleMoveMetadataRules.hasCanonicalFlag(safeFlags, ActionBattleMoveFlag.BALL_BOMB)) {
            return new Classification(ActionBattleMoveVisualFamily.ORB_BALL, "canonical_flag:bullet", Confidence.MEDIUM);
        }
        if (ActionBattleMoveMetadataRules.hasCanonicalFlag(safeFlags, ActionBattleMoveFlag.CONTACT)) {
            return new Classification(ActionBattleMoveVisualFamily.CONTACT_MELEE, "canonical_flag:contact", Confidence.MEDIUM);
        }

        String normalizedCategory = normalize(category);
        String normalizedTarget = normalize(target);
        if (normalizedCategory.equals("status")) {
            if (isSelfOrAllyTarget(normalizedTarget)) {
                return new Classification(ActionBattleMoveVisualFamily.SELF_AURA, "target_category", Confidence.MEDIUM);
            }
            if (normalize(type).equals("psychic")) {
                return new Classification(ActionBattleMoveVisualFamily.DIRECT_PSYCHIC, "type+status_target", Confidence.LOW);
            }
            return new Classification(ActionBattleMoveVisualFamily.TARGET_AURA_STATUS, "status_fallback", Confidence.LOW);
        }

        return new Classification(ActionBattleMoveVisualFamily.ORB_BALL, "generic_damaging_fallback", Confidence.LOW);
    }

    private static ActionBattleMoveVisualFamily fromExplicitDelivery(ActionMoveDeliveryType delivery) {
        return switch (delivery) {
            case BEAM_BOLT -> ActionBattleMoveVisualFamily.BEAM_RAY;
            case ARCING_LOBBED -> ActionBattleMoveVisualFamily.LOB_BOMB;
            case WAVE_AREA -> ActionBattleMoveVisualFamily.WAVE_PULSE;
            case GROUND_HUGGING_WAVE, PERSISTENT_AREA -> ActionBattleMoveVisualFamily.GROUND_SURFACE;
            case DASH_RUSH -> ActionBattleMoveVisualFamily.RUSH_DASH;
            case PHYSICAL_CONTACT -> ActionBattleMoveVisualFamily.CONTACT_MELEE;
            case HOMING_GUIDED, NORMAL_PROJECTILE -> ActionBattleMoveVisualFamily.ORB_BALL;
            case CHANNELING -> ActionBattleMoveVisualFamily.SELF_AURA;
            case TARGET_LOCKED -> ActionBattleMoveVisualFamily.TARGET_AURA_STATUS;
            case INSTANT -> ActionBattleMoveVisualFamily.DIRECT_PSYCHIC;
        };
    }

    private static boolean hasAny(Map<String, JsonElement> object, String... names) {
        for (String name : names) {
            JsonElement value = object.get(name);
            if (value != null && !value.isJsonNull()) return true;
        }
        return false;
    }

    private static boolean isSelfOrAllyTarget(String target) {
        return target.equals("self") || target.contains("ally") || target.equals("allies") || target.equals("allyteam") || target.equals("allyside");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    public record Classification(ActionBattleMoveVisualFamily family, String source, Confidence confidence) {}
}

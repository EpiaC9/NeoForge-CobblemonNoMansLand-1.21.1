package net.epiac9.cobblemonnml.battle.action.audit;

import com.google.gson.JsonElement;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveFlag;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveMetadataRules;

import java.util.Map;
import java.util.Set;

/**
 * Primary work-queue grouping for the move audit. This is intentionally separate from
 * support classification and visual family: every move gets exactly one handling group
 * so later implementation passes can be worked through in stable batches.
 */
public enum ActionBattleMoveHandlingGroup {
    EXPLICIT_EXISTING,

    BESPOKE_MULTI_HIT,
    BESPOKE_CHARGE_RECHARGE,
    BESPOKE_SWITCH_PIVOT,
    BESPOKE_FIELD_SIDE,
    BESPOKE_PROTECT_SPECIAL,
    BESPOKE_CRIT_OHKO,
    BESPOKE_OTHER,

    GENERIC_CONTACT,
    GENERIC_SOUND_WIND,
    GENERIC_STATUS_SELF_ALLY,
    GENERIC_STATUS_TARGET,
    GENERIC_PROJECTILE,
    GENERIC_OTHER;

    public static ActionBattleMoveHandlingGroup classify(String support,
                                                          String category,
                                                          String target,
                                                          String visualFamily,
                                                          Set<String> flags,
                                                          Map<String, JsonElement> canonicalEffects) {
        if ("EXPLICIT".equals(support) || "PARTIAL".equals(support)) {
            return EXPLICIT_EXISTING;
        }

        if ("BESPOKE_REQUIRED".equals(support)) {
            if (hasEffect(canonicalEffects, "multihit", "multiaccuracy")) {
                return BESPOKE_MULTI_HIT;
            }
            if (hasFlag(flags, ActionBattleMoveFlag.CHARGE, ActionBattleMoveFlag.RECHARGE, ActionBattleMoveFlag.CANNOT_USE_TWICE)
                    || hasVolatile(canonicalEffects, "mustrecharge")) {
                return BESPOKE_CHARGE_RECHARGE;
            }
            if (hasEffect(canonicalEffects, "forceSwitch", "selfSwitch")
                    || hasFlag(flags, ActionBattleMoveFlag.FUTURE_MOVE, ActionBattleMoveFlag.PLEDGE_COMBO)) {
                return BESPOKE_SWITCH_PIVOT;
            }
            if (hasEffect(canonicalEffects, "weather", "terrain", "pseudoWeather", "sideCondition", "slotCondition")) {
                return BESPOKE_FIELD_SIDE;
            }
            if (hasEffect(canonicalEffects, "stallingMove", "breaksProtect")) {
                return BESPOKE_PROTECT_SPECIAL;
            }
            if (hasEffect(canonicalEffects, "ohko", "willCrit")) {
                return BESPOKE_CRIT_OHKO;
            }
            return BESPOKE_OTHER;
        }

        if (isContactFamily(visualFamily)) return GENERIC_CONTACT;
        if ("SOUND".equals(visualFamily) || "WIND_GUST".equals(visualFamily) || "WAVE_PULSE".equals(visualFamily)) {
            return GENERIC_SOUND_WIND;
        }
        if (isStatus(category)) {
            if (isSelfOrAllyTarget(target)) return GENERIC_STATUS_SELF_ALLY;
            return GENERIC_STATUS_TARGET;
        }
        if ("ORB_BALL".equals(visualFamily) || "BEAM_RAY".equals(visualFamily)
                || "LOB_BOMB".equals(visualFamily) || "GROUND_SURFACE".equals(visualFamily)
                || "DIRECT_PSYCHIC".equals(visualFamily) || "POWDER_CLOUD".equals(visualFamily)) {
            return GENERIC_PROJECTILE;
        }
        return GENERIC_OTHER;
    }

    private static boolean isContactFamily(String visualFamily) {
        return "CONTACT_MELEE".equals(visualFamily)
                || "PUNCH_CONTACT".equals(visualFamily)
                || "BITE_CONTACT".equals(visualFamily)
                || "SLASH_ARC".equals(visualFamily)
                || "RUSH_DASH".equals(visualFamily);
    }

    private static boolean isStatus(String category) {
        return category != null && category.equalsIgnoreCase("status");
    }

    private static boolean isSelfOrAllyTarget(String target) {
        if (target == null) return false;
        String normalized = target.toLowerCase();
        return normalized.contains("self") || normalized.contains("ally") || normalized.contains("allies") || normalized.contains("team");
    }

    private static boolean hasFlag(Set<String> flags, ActionBattleMoveFlag... candidates) {
        if (flags == null || flags.isEmpty() || candidates == null) return false;
        for (ActionBattleMoveFlag candidate : candidates) {
            if (ActionBattleMoveMetadataRules.hasCanonicalFlag(flags, candidate)) return true;
        }
        return false;
    }

    private static boolean hasEffect(Map<String, JsonElement> effects, String... names) {
        if (effects == null || effects.isEmpty()) return false;
        for (String name : names) if (effects.containsKey(name)) return true;
        return false;
    }

    private static boolean hasVolatile(Map<String, JsonElement> effects, String value) {
        if (effects == null) return false;
        JsonElement volatileStatus = effects.get("volatileStatus");
        return volatileStatus != null && volatileStatus.isJsonPrimitive()
                && value.equalsIgnoreCase(volatileStatus.getAsString());
    }
}

package net.epiac9.cobblemonnml.battle.action.move;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Typed ACTION-facing view of Cobblemon/Showdown move flags.
 *
 * Raw canonical flag strings are still retained in the descriptor for audit and
 * forward compatibility, but runtime systems should prefer this enum instead of
 * introducing ad-hoc string checks.
 */
public enum ActionBattleMoveFlag {
    METRONOME("metronome"),
    PROTECT("protect", "protectable", "blockedbyprotect", "protectblocked"),
    MIRROR("mirror"),
    CONTACT("contact"),
    REFLECTABLE("reflectable"),
    SNATCH("snatch"),
    BYPASS_SUBSTITUTE("bypasssub", "bypasssubstitute"),
    NO_ASSIST("noassist"),
    ALLY_ANIMATION("allyanim", "allyanimation"),
    FAIL_COPYCAT("failcopycat"),
    FAIL_INSTRUCT("failinstruct"),
    NO_SLEEP_TALK("nosleeptalk"),
    NON_SKY("nonsky"),
    HEAL("heal"),
    SOUND("sound", "soundbased"),
    DISTANCE("distance"),
    BALL_BOMB("bullet", "ballbomb", "ball", "bomb", "bombmove"),
    SLICING("slicing", "slice", "slicingmove"),
    FAIL_MIMIC("failmimic"),
    PUNCH("punch", "punching"),
    FAIL_ENCORE("failencore"),
    WIND("wind", "windmove"),
    FAIL_ME_FIRST("failmefirst"),
    CHARGE("charge"),
    DANCE("dance"),
    DEFROST("defrost"),
    BITE("bite", "biting"),
    RECHARGE("recharge"),
    GRAVITY("gravity"),
    NO_PARENTAL_BOND("noparentalbond"),
    POWDER("powder", "powderbased"),
    PULSE("pulse"),
    MUST_PRESSURE("mustpressure"),
    PLEDGE_COMBO("pledgecombo"),
    CANNOT_USE_TWICE("cantusetwice", "cannotusetwice"),
    FUTURE_MOVE("futuremove");

    private static final Map<String, ActionBattleMoveFlag> BY_ALIAS = buildLookup();

    private final Set<String> aliases;

    ActionBattleMoveFlag(String... aliases) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String alias : aliases) {
            String token = normalize(alias);
            if (!token.isEmpty()) normalized.add(token);
        }
        this.aliases = Set.copyOf(normalized);
    }

    public boolean matches(String rawFlag) {
        return aliases.contains(normalize(rawFlag));
    }

    public Set<String> aliases() {
        return aliases;
    }

    public static ActionBattleMoveFlag fromCanonical(String rawFlag) {
        return BY_ALIAS.get(normalize(rawFlag));
    }

    public static Set<ActionBattleMoveFlag> resolveAll(Collection<?> rawFlags) {
        if (rawFlags == null || rawFlags.isEmpty()) return Set.of();
        LinkedHashSet<ActionBattleMoveFlag> result = new LinkedHashSet<>();
        for (Object raw : rawFlags) {
            ActionBattleMoveFlag flag = fromCanonical(raw != null ? raw.toString() : null);
            if (flag != null) result.add(flag);
        }
        return Set.copyOf(result);
    }

    private static Map<String, ActionBattleMoveFlag> buildLookup() {
        LinkedHashMap<String, ActionBattleMoveFlag> result = new LinkedHashMap<>();
        for (ActionBattleMoveFlag flag : values()) {
            for (String alias : flag.aliases) {
                result.putIfAbsent(alias, flag);
            }
        }
        return Map.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "");
    }
}

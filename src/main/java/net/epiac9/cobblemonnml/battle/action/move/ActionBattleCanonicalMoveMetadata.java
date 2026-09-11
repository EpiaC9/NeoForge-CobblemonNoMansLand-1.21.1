package net.epiac9.cobblemonnml.battle.action.move;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, runtime-facing view of the canonical Showdown move metadata that
 * Cobblemon's reduced MoveTemplate does not retain.
 */
public final class ActionBattleCanonicalMoveMetadata {
    private static final ActionBattleCanonicalMoveMetadata EMPTY = new ActionBattleCanonicalMoveMetadata(
            "", Set.of(), "", "", Map.of(), Map.of(), List.of(), null, null, null, null,
            false, false, "", false, false, false, false, "", "", "", "", "", false, Map.of()
    );

    private final String id;
    private final Set<String> flags;
    private final Set<ActionBattleMoveFlag> typedFlags;
    private final String status;
    private final String volatileStatus;
    private final Map<String, Integer> boosts;
    private final Map<String, Integer> selfBoosts;
    private final List<SecondaryEffect> secondaries;
    private final Fraction recoil;
    private final Fraction drain;
    private final Fraction heal;
    private final MultiHit multiHit;
    private final boolean multiAccuracy;
    private final boolean forceSwitch;
    private final String selfSwitchMode;
    private final boolean ohko;
    private final boolean guaranteedCrit;
    private final boolean breaksProtect;
    private final boolean stallingMove;
    private final String weather;
    private final String terrain;
    private final String sideCondition;
    private final String slotCondition;
    private final String pseudoWeather;
    private final boolean mindBlownRecoil;
    private final Map<String, JsonElement> structuredMetadata;

    private ActionBattleCanonicalMoveMetadata(
            String id,
            Set<String> flags,
            String status,
            String volatileStatus,
            Map<String, Integer> boosts,
            Map<String, Integer> selfBoosts,
            List<SecondaryEffect> secondaries,
            Fraction recoil,
            Fraction drain,
            Fraction heal,
            MultiHit multiHit,
            boolean multiAccuracy,
            boolean forceSwitch,
            String selfSwitchMode,
            boolean ohko,
            boolean guaranteedCrit,
            boolean breaksProtect,
            boolean stallingMove,
            String weather,
            String terrain,
            String sideCondition,
            String slotCondition,
            String pseudoWeather,
            boolean mindBlownRecoil,
            Map<String, JsonElement> structuredMetadata
    ) {
        this.id = id != null ? id : "";
        this.flags = flags != null ? Set.copyOf(flags) : Set.of();
        this.typedFlags = ActionBattleMoveMetadataRules.typedFlags(this.flags);
        this.status = status != null ? status : "";
        this.volatileStatus = volatileStatus != null ? volatileStatus : "";
        this.boosts = boosts != null ? Map.copyOf(boosts) : Map.of();
        this.selfBoosts = selfBoosts != null ? Map.copyOf(selfBoosts) : Map.of();
        this.secondaries = secondaries != null ? List.copyOf(secondaries) : List.of();
        this.recoil = recoil;
        this.drain = drain;
        this.heal = heal;
        this.multiHit = multiHit;
        this.multiAccuracy = multiAccuracy;
        this.forceSwitch = forceSwitch;
        this.selfSwitchMode = selfSwitchMode != null ? selfSwitchMode : "";
        this.ohko = ohko;
        this.guaranteedCrit = guaranteedCrit;
        this.breaksProtect = breaksProtect;
        this.stallingMove = stallingMove;
        this.weather = weather != null ? weather : "";
        this.terrain = terrain != null ? terrain : "";
        this.sideCondition = sideCondition != null ? sideCondition : "";
        this.slotCondition = slotCondition != null ? slotCondition : "";
        this.pseudoWeather = pseudoWeather != null ? pseudoWeather : "";
        this.mindBlownRecoil = mindBlownRecoil;
        this.structuredMetadata = immutableJsonMap(structuredMetadata);
    }

    public static ActionBattleCanonicalMoveMetadata empty() {
        return EMPTY;
    }

    public static ActionBattleCanonicalMoveMetadata fromShowdown(JsonObject source) {
        if (source == null) return EMPTY;

        String id = ActionBattleMoveMetadataRules.canonicalMoveId(string(source, "id"));
        Set<String> flags = parseFlags(source.get("flags"));
        String status = string(source, "status");
        String volatileStatus = string(source, "volatileStatus");
        Map<String, Integer> boosts = parseBoosts(source.get("boosts"));
        Map<String, Integer> selfBoosts = parseSelfBoosts(source);
        List<SecondaryEffect> secondaries = parseSecondaries(source);
        Fraction recoil = parseFraction(source.get("recoil"));
        Fraction drain = parseFraction(source.get("drain"));
        Fraction heal = parseFraction(source.get("heal"));
        MultiHit multiHit = parseMultiHit(source.get("multihit"));

        return new ActionBattleCanonicalMoveMetadata(
                id,
                flags,
                status,
                volatileStatus,
                boosts,
                selfBoosts,
                secondaries,
                recoil,
                drain,
                heal,
                multiHit,
                bool(source, "multiaccuracy"),
                bool(source, "forceSwitch"),
                switchMode(source.get("selfSwitch")),
                bool(source, "ohko"),
                bool(source, "willCrit"),
                bool(source, "breaksProtect"),
                bool(source, "stallingMove"),
                string(source, "weather"),
                string(source, "terrain"),
                string(source, "sideCondition"),
                string(source, "slotCondition"),
                string(source, "pseudoWeather"),
                bool(source, "mindBlownRecoil"),
                structuredMetadata(source)
        );
    }

    public String id() { return id; }
    public Set<String> flags() { return flags; }
    public Set<ActionBattleMoveFlag> typedFlags() { return typedFlags; }
    public String status() { return status; }
    public String volatileStatus() { return volatileStatus; }
    public Map<String, Integer> boosts() { return boosts; }
    public Map<String, Integer> selfBoosts() { return selfBoosts; }
    public List<SecondaryEffect> secondaries() { return secondaries; }
    public Fraction recoil() { return recoil; }
    public Fraction drain() { return drain; }
    public Fraction heal() { return heal; }
    public MultiHit multiHit() { return multiHit; }
    public boolean multiAccuracy() { return multiAccuracy; }
    public boolean forceSwitch() { return forceSwitch; }
    public boolean selfSwitch() { return !selfSwitchMode.isBlank(); }
    public String selfSwitchMode() { return selfSwitchMode; }
    public boolean ohko() { return ohko; }
    public boolean guaranteedCrit() { return guaranteedCrit; }
    public boolean breaksProtect() { return breaksProtect; }
    public boolean stallingMove() { return stallingMove; }
    public String weather() { return weather; }
    public String terrain() { return terrain; }
    public String sideCondition() { return sideCondition; }
    public String slotCondition() { return slotCondition; }
    public String pseudoWeather() { return pseudoWeather; }
    public boolean mindBlownRecoil() { return mindBlownRecoil; }
    public Map<String, JsonElement> structuredMetadata() { return structuredMetadata; }

    public boolean hasFlag(String flag) {
        return flags.contains(ActionBattleMoveMetadataRules.normalizeToken(flag));
    }

    public boolean hasFlag(ActionBattleMoveFlag flag) {
        return typedFlags.contains(flag);
    }

    public boolean chargeMove() { return hasFlag(ActionBattleMoveFlag.CHARGE); }
    public boolean rechargeMove() { return hasFlag(ActionBattleMoveFlag.RECHARGE) || "mustrecharge".equals(volatileStatus); }
    public boolean multiHitMove() { return multiHit != null; }
    public boolean fieldOrSideEffect() {
        return !weather.isBlank() || !terrain.isBlank() || !sideCondition.isBlank()
                || !slotCondition.isBlank() || !pseudoWeather.isBlank();
    }

    private static Set<String> parseFlags(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
            if (!meaningful(entry.getValue())) continue;
            String normalized = ActionBattleMoveMetadataRules.normalizeToken(entry.getKey());
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return Set.copyOf(result);
    }

    private static Map<String, Integer> parseBoosts(JsonElement raw) {
        if (raw == null || !raw.isJsonObject()) return Map.of();
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                result.put(ActionBattleMoveMetadataRules.normalizeToken(entry.getKey()), value.getAsInt());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, Integer> parseSelfBoosts(JsonObject source) {
        JsonElement selfBoost = source.get("selfBoost");
        if (selfBoost != null && selfBoost.isJsonObject()) {
            Map<String, Integer> parsed = parseBoosts(selfBoost.getAsJsonObject().get("boosts"));
            if (!parsed.isEmpty()) return parsed;
        }
        JsonElement self = source.get("self");
        if (self != null && self.isJsonObject()) {
            return parseBoosts(self.getAsJsonObject().get("boosts"));
        }
        return Map.of();
    }

    private static List<SecondaryEffect> parseSecondaries(JsonObject source) {
        List<SecondaryEffect> result = new ArrayList<>();
        JsonElement rawSecondaries = source.get("secondaries");
        if (rawSecondaries != null && rawSecondaries.isJsonArray()) {
            for (JsonElement entry : rawSecondaries.getAsJsonArray()) {
                SecondaryEffect parsed = parseSecondary(entry);
                if (parsed != null) result.add(parsed);
            }
        }
        if (result.isEmpty()) {
            SecondaryEffect single = parseSecondary(source.get("secondary"));
            if (single != null) result.add(single);
        }
        return List.copyOf(result);
    }

    private static SecondaryEffect parseSecondary(JsonElement raw) {
        if (raw == null || !raw.isJsonObject() || raw.getAsJsonObject().isEmpty()) return null;
        JsonObject object = raw.getAsJsonObject();
        int chance = number(object, "chance", 100);
        String status = string(object, "status");
        String volatileStatus = string(object, "volatileStatus");
        Map<String, Integer> boosts = parseBoosts(object.get("boosts"));
        Map<String, Integer> selfBoosts = Map.of();
        JsonElement self = object.get("self");
        if (self != null && self.isJsonObject()) selfBoosts = parseBoosts(self.getAsJsonObject().get("boosts"));
        return new SecondaryEffect(chance, status, volatileStatus, boosts, selfBoosts);
    }

    private static Fraction parseFraction(JsonElement raw) {
        if (raw == null || !raw.isJsonArray()) return null;
        JsonArray array = raw.getAsJsonArray();
        if (array.size() < 2 || !array.get(0).isJsonPrimitive() || !array.get(1).isJsonPrimitive()) return null;
        try {
            return new Fraction(array.get(0).getAsInt(), array.get(1).getAsInt());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static MultiHit parseMultiHit(JsonElement raw) {
        if (raw == null || raw.isJsonNull()) return null;
        try {
            if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
                int hits = Math.max(1, raw.getAsInt());
                return new MultiHit(hits, hits);
            }
            if (raw.isJsonArray()) {
                JsonArray array = raw.getAsJsonArray();
                if (array.size() >= 2) {
                    int min = Math.max(1, array.get(0).getAsInt());
                    int max = Math.max(min, array.get(1).getAsInt());
                    return new MultiHit(min, max);
                }
            }
        } catch (RuntimeException ignored) {}
        return null;
    }

    private static Map<String, JsonElement> structuredMetadata(JsonObject source) {
        LinkedHashMap<String, JsonElement> result = new LinkedHashMap<>();
        for (String property : List.of(
                "status", "boosts", "self", "selfBoost", "recoil", "drain", "heal",
                "secondary", "secondaries", "volatileStatus", "weather", "terrain",
                "sideCondition", "slotCondition", "pseudoWeather", "multihit", "multiaccuracy",
                "willCrit", "ohko", "breaksProtect", "forceSwitch", "selfSwitch", "stallingMove",
                "mindBlownRecoil"
        )) {
            JsonElement value = source.get(property);
            if (meaningful(value)) result.put(property, value.deepCopy());
        }
        return result;
    }

    private static Map<String, JsonElement> immutableJsonMap(Map<String, JsonElement> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<String, JsonElement> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, value != null ? value.deepCopy() : null));
        return Map.copyOf(copy);
    }


    private static String switchMode(JsonElement raw) {
        if (!meaningful(raw) || raw == null || !raw.isJsonPrimitive()) return "";
        try {
            if (raw.getAsJsonPrimitive().isBoolean()) return raw.getAsBoolean() ? "switch" : "";
            return ActionBattleMoveMetadataRules.normalizeToken(raw.getAsString());
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String string(JsonObject source, String property) {
        if (source == null) return "";
        JsonElement value = source.get(property);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return "";
        try {
            return value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static boolean bool(JsonObject source, String property) {
        if (source == null) return false;
        JsonElement value = source.get(property);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return false;
        try {
            return value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static int number(JsonObject source, String property, int fallback) {
        if (source == null) return fallback;
        JsonElement value = source.get(property);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return fallback;
        try {
            return value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean meaningful(JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        if (value.isJsonArray()) return !value.getAsJsonArray().isEmpty();
        if (value.isJsonObject()) return !value.getAsJsonObject().isEmpty();
        if (value.isJsonPrimitive()) {
            if (value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean();
            if (value.getAsJsonPrimitive().isNumber()) return value.getAsDouble() != 0.0D;
            return !value.getAsString().isBlank();
        }
        return true;
    }

    public record Fraction(int numerator, int denominator) {
        public Fraction {
            if (denominator == 0) denominator = 1;
        }

        public double value() {
            return (double) numerator / (double) denominator;
        }
    }

    public record MultiHit(int minHits, int maxHits) {
        public MultiHit {
            minHits = Math.max(1, minHits);
            maxHits = Math.max(minHits, maxHits);
        }

        public boolean fixed() { return minHits == maxHits; }
    }

    public record SecondaryEffect(
            int chancePercent,
            String status,
            String volatileStatus,
            Map<String, Integer> boosts,
            Map<String, Integer> selfBoosts
    ) {
        public SecondaryEffect {
            chancePercent = Math.max(0, Math.min(100, chancePercent));
            status = status != null ? status : "";
            volatileStatus = volatileStatus != null ? volatileStatus : "";
            boosts = boosts != null ? Map.copyOf(boosts) : Map.of();
            selfBoosts = selfBoosts != null ? Map.copyOf(selfBoosts) : Map.of();
        }
    }
}

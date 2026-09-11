package net.epiac9.cobblemonnml.battle.action.audit;

import com.cobblemon.mod.common.api.moves.Move;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.epiac9.cobblemonnml.battle.action.compat.ActionBattleMoveEffectDataManager;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveDescriptor;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveMetadataResolver;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleCanonicalMoveRegistry;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveMetadataRules;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveReflection;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionMoveDeliveryType;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ActionBattleMoveAuditExporter {
    private static final String COBBLEMON_MOVES_CLASS = "com.cobblemon.mod.common.api.moves.Moves";
    private static final String COBBLEMON_VERSION = "1.7.3";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ActionBattleMoveAuditExporter() {}

    public static AuditResult export(Path gameDirectory) throws IOException {
        if (gameDirectory == null) throw new IllegalArgumentException("gameDirectory");
        Catalog catalog = discoverCatalog();
        Map<String, JsonObject> showdownMoves = showdownMoveData();
        ActionBattleAddonVisualAudit.Catalog addonVisuals = ActionBattleAddonVisualAudit.scan(gameDirectory);
        List<MoveAuditEntry> entries = new ArrayList<>();
        for (Object template : catalog.templates()) {
            String id = templateId(template);
            MoveAuditEntry entry = auditTemplate(template, showdownMoves.get(id), addonVisuals.forMove(id));
            if (entry != null && !entry.id().isBlank()) entries.add(entry);
        }
        entries.sort(Comparator.comparing(MoveAuditEntry::id));
        Path outputDirectory = gameDirectory.resolve("cobblemonnml-audit");
        Files.createDirectories(outputDirectory);
        Path output = outputDirectory.resolve("action_moves_cobblemon_1_7_3.json");
        Files.writeString(output, GSON.toJson(toJson(entries, catalog, addonVisuals)), StandardCharsets.UTF_8);
        return new AuditResult(output, entries.size(), catalog.discoverySource());
    }

    private static MoveAuditEntry auditTemplate(Object template, JsonObject showdown,
                                                    ActionBattleAddonVisualAudit.MoveVisuals addonVisuals) {
        Move move = createMove(template);
        String rawId = move != null ? move.getName() : stringValue(ActionBattleMoveReflection.property(template, "name"));
        String id = ActionBattleMoveMetadataRules.canonicalMoveId(rawId);
        if (id.isBlank()) return null;

        ActionBattleMoveDescriptor descriptor = move != null ? ActionBattleMoveMetadataResolver.resolve(null, move) : null;
        validateDescriptorCanonicalConsistency(id, descriptor, showdown);
        String type = canonicalType(template, move, descriptor);
        String category = showdownString(showdown, "category");
        if (category.isBlank()) category = canonicalCategory(template, move);
        if (category.isBlank() && descriptor != null) category = descriptor.damageCategory().name().toLowerCase(Locale.ROOT);
        int power = intProperty(template, move, "power", descriptor != null ? descriptor.power() : 0);
        double accuracy = doubleProperty(template, move, "accuracy", descriptor != null ? descriptor.accuracy() : Double.NaN);
        int priority = intProperty(template, move, "priority", descriptor != null ? descriptor.priority() : 0);
        int pp = intProperty(template, move, "pp", descriptor != null ? descriptor.maxPp() : 0);
        String target = stringProperty(template, move, "target");
        if (target.isBlank() && descriptor != null) target = descriptor.targetCategory();
        double critRatio = doubleProperty(template, move, "critRatio", descriptor != null ? descriptor.critRatio() : Double.NaN);
        Set<String> flags = showdownFlags(showdown);
        if (flags.isEmpty()) flags = canonicalFlags(template, move);
        if (flags.isEmpty() && descriptor != null) flags = descriptor.flags();
        ActionMoveDeliveryType delivery = ActionProjectileProfile.deliveryType(id);
        List<String> nativeEffects = ActionProjectileProfile.nativeCobblemonEffects(id);
        boolean explicitDelivery = ActionProjectileProfile.hasExplicitDeliveryProfile(id);
        boolean nmlEffectMetadata = !ActionBattleMoveEffectDataManager.getAll(id).isEmpty();
        boolean executable = move != null && FightOrFlightAdapter.supports(move);
        String explicitHandler = ActionBattleExplicitMoveHandlerAudit.handlerFor(id);
        Map<String, JsonElement> effectMetadata = canonicalEffectMetadata(showdown, template, move);
        String support = supportClassification(executable, explicitDelivery, nmlEffectMetadata,
                !nativeEffects.isEmpty(), !explicitHandler.isBlank(), flags, effectMetadata);
        ActionBattleMoveVisualClassifier.Classification visual = ActionBattleMoveVisualClassifier.classify(
                type, category, target, flags, effectMetadata, delivery, explicitDelivery
        );
        ActionBattleMoveHandlingGroup handlingGroup = ActionBattleMoveHandlingGroup.classify(
                support, category, target, visual.family().name(), flags, effectMetadata
        );
        List<String> visualAssetSources = visualAssetSources(nativeEffects, addonVisuals, nmlEffectMetadata, explicitDelivery);
        String preferredVisualAssetSource = visualAssetSources.getFirst();

        return new MoveAuditEntry(
                id, translationKey(template, move), type, category, power, finiteOrNull(accuracy), priority, pp, target,
                finiteOrNull(critRatio), flags.stream().sorted().toList(), effectMetadata,
                executable, nmlEffectMetadata, !explicitHandler.isBlank(), explicitHandler, explicitDelivery, delivery.name(), nativeEffects,
                addonVisuals, visualAssetSources, preferredVisualAssetSource, support, handlingGroup.name(),
                visual.family().name(), visual.source(), visual.confidence().name()
        );
    }

    private static JsonObject toJson(List<MoveAuditEntry> entries, Catalog catalog, ActionBattleAddonVisualAudit.Catalog addonVisuals) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 7);
        root.addProperty("cobblemonVersion", COBBLEMON_VERSION);
        root.addProperty("catalogDiscovery", catalog.discoverySource());
        root.addProperty("metadataEnrichment", "ShowdownService.service.getRegistryData(move)");
        root.addProperty("moveCount", entries.size());
        JsonObject externalVisuals = new JsonObject();
        externalVisuals.addProperty("extraMoveAnimationsPresent", addonVisuals.present());
        externalVisuals.addProperty("extraMoveAnimationsJar", addonVisuals.jarFile());
        externalVisuals.addProperty("extraMoveAnimationsMoveCount", addonVisuals.moves().size());
        root.add("externalVisualSources", externalVisuals);
        root.add("summary", auditSummary(entries));
        root.add("handlingGroups", handlingGroups(entries));
        if (!catalog.templates().isEmpty()) root.add("runtimeSchema", runtimeSchema(catalog.templates().getFirst()));
        JsonArray moves = new JsonArray();
        for (MoveAuditEntry entry : entries) moves.add(GSON.toJsonTree(entry));
        root.add("moves", moves);
        return root;
    }

    private static JsonObject runtimeSchema(Object template) {
        JsonObject result = new JsonObject();
        result.addProperty("templateClass", template.getClass().getName());
        result.add("templateZeroArgMethods", GSON.toJsonTree(ActionBattleMoveReflection.publicZeroArgMembers(template)));
        result.add("templateFields", GSON.toJsonTree(ActionBattleMoveReflection.declaredFieldNames(template)));
        Move move = createMove(template);
        if (move != null) {
            result.addProperty("moveClass", move.getClass().getName());
            result.add("moveZeroArgMethods", GSON.toJsonTree(ActionBattleMoveReflection.publicZeroArgMembers(move)));
            result.add("moveFields", GSON.toJsonTree(ActionBattleMoveReflection.declaredFieldNames(move)));
        }
        return result;
    }


    private static void validateDescriptorCanonicalConsistency(String id, ActionBattleMoveDescriptor descriptor, JsonObject showdown) {
        if (descriptor == null) {
            throw new IllegalStateException("ACTION descriptor could not be resolved for canonical move: " + id);
        }

        Set<String> expectedFlags = showdownFlags(showdown);
        if (!expectedFlags.equals(descriptor.flags())) {
            throw new IllegalStateException("ACTION descriptor canonical flags mismatch for " + id
                    + ": expected=" + expectedFlags + ", actual=" + descriptor.flags());
        }

        Map<String, JsonElement> expectedStructured = canonicalEffectMetadata(showdown, null, null);
        Map<String, JsonElement> actualStructured = descriptor.canonicalMetadata().structuredMetadata();
        if (!expectedStructured.equals(actualStructured)) {
            throw new IllegalStateException("ACTION descriptor canonical metadata mismatch for " + id
                    + ": expected=" + expectedStructured + ", actual=" + actualStructured);
        }
    }

    private static String canonicalType(Object template, Move move, ActionBattleMoveDescriptor descriptor) {
        Object value = firstNonNull(ActionBattleMoveReflection.property(template, "type"),
                ActionBattleMoveReflection.property(move, "type"));
        Object name = ActionBattleMoveReflection.property(value, "name");
        String raw = name != null ? stringValue(name) : stringValue(value);
        if (!raw.isBlank()) return normalizeType(raw);
        return descriptor != null ? descriptor.exposedType() : "normal";
    }

    private static String canonicalCategory(Object template, Move move) {
        Object value = firstNonNull(ActionBattleMoveReflection.property(template, "damageCategory", "category"),
                ActionBattleMoveReflection.property(move, "damageCategory", "category"));
        return normalizeTokenValue(value);
    }

    private static Set<String> canonicalFlags(Object template, Move move) {
        Collection<?> values = ActionBattleMoveReflection.collectionProperty(template,
                "flags", "moveFlags", "properties", "moveProperties");
        if (values.isEmpty()) values = ActionBattleMoveReflection.collectionProperty(move,
                "flags", "moveFlags", "properties", "moveProperties");
        return ActionBattleMoveMetadataRules.normalizeFlags(values);
    }

    private static Map<String, JsonElement> canonicalEffectMetadata(JsonObject showdown, Object template, Move move) {
        LinkedHashMap<String, JsonElement> result = new LinkedHashMap<>();
        if (showdown != null) {
            for (String property : List.of("status", "boosts", "self", "selfBoost", "recoil", "drain", "heal",
                    "secondary", "secondaries", "volatileStatus", "weather", "terrain", "sideCondition",
                    "slotCondition", "pseudoWeather", "multihit", "multiaccuracy", "willCrit", "ohko",
                    "breaksProtect", "forceSwitch", "selfSwitch", "stallingMove", "mindBlownRecoil")) {
                JsonElement value = showdown.get(property);
                if (meaningful(value)) result.put(property, value.deepCopy());
            }
        }
        if (result.isEmpty()) {
            Object chances = firstNonNull(ActionBattleMoveReflection.property(template, "effectChances"),
                    ActionBattleMoveReflection.property(move, "effectChances"));
            JsonElement json = GSON.toJsonTree(chances);
            if (meaningful(json)) result.put("effectChances", json);
        }
        return Map.copyOf(result);
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

    private static Map<String, JsonObject> showdownMoveData() {
        return ActionBattleCanonicalMoveRegistry.rawSnapshot();
    }

    private static String showdownString(JsonObject source, String property) {
        if (source == null || !source.has(property) || source.get(property).isJsonNull()) return "";
        JsonElement value = source.get(property);
        return value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static Set<String> showdownFlags(JsonObject showdown) {
        if (showdown == null) return Set.of();
        JsonElement raw = showdown.get("flags");
        if (raw == null || !raw.isJsonObject()) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
            if (meaningful(entry.getValue())) result.add(ActionBattleMoveMetadataRules.normalizeToken(entry.getKey()));
        }
        return Set.copyOf(result);
    }

    private static String templateId(Object template) {
        Move move = createMove(template);
        String raw = move != null ? move.getName() : stringValue(ActionBattleMoveReflection.property(template, "name"));
        return ActionBattleMoveMetadataRules.canonicalMoveId(raw);
    }

    private static int intProperty(Object template, Object move, String property, int fallback) {
        Object value = firstNonNull(ActionBattleMoveReflection.property(template, property),
                ActionBattleMoveReflection.property(move, property));
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double doubleProperty(Object template, Object move, String property, double fallback) {
        Object value = firstNonNull(ActionBattleMoveReflection.property(template, property),
                ActionBattleMoveReflection.property(move, property));
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static String stringProperty(Object template, Object move, String property) {
        Object value = firstNonNull(ActionBattleMoveReflection.property(template, property),
                ActionBattleMoveReflection.property(move, property));
        return stringValue(value);
    }

    private static Catalog discoverCatalog() {
        try {
            Class<?> movesClass = Class.forName(COBBLEMON_MOVES_CLASS);
            Object instance = singletonInstance(movesClass);
            List<String> preferredMethods = List.of("getAll", "getAllMoves", "getMoves", "getTemplates");
            for (String name : preferredMethods) {
                Object value = invokeEither(movesClass, instance, name);
                List<Object> templates = templateValues(value);
                if (!templates.isEmpty()) return new Catalog(templates, "Moves." + name + "()");
            }
            for (Method method : movesClass.getMethods()) {
                if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) continue;
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (!name.contains("move") && !name.contains("all") && !name.contains("template")) continue;
                try {
                    Object receiver = Modifier.isStatic(method.getModifiers()) ? null : instance;
                    if (receiver == null && !Modifier.isStatic(method.getModifiers())) continue;
                    List<Object> templates = templateValues(method.invoke(receiver));
                    if (!templates.isEmpty()) return new Catalog(templates, "Moves." + method.getName() + "()");
                } catch (ReflectiveOperationException ignored) {}
            }
            for (Field field : movesClass.getDeclaredFields()) {
                try {
                    if (!Modifier.isStatic(field.getModifiers()) && instance == null) continue;
                    field.setAccessible(true);
                    Object value = field.get(Modifier.isStatic(field.getModifiers()) ? null : instance);
                    List<Object> templates = templateValues(value);
                    if (!templates.isEmpty()) return new Catalog(templates, "Moves." + field.getName());
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
            throw new IllegalStateException("Cobblemon Moves catalog was found, but no move-template collection could be discovered.");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cobblemon Moves catalog class is unavailable: " + COBBLEMON_MOVES_CLASS, exception);
        }
    }

    private static List<Object> templateValues(Object value) {
        if (value == null) return List.of();
        Collection<?> collection;
        if (value instanceof Map<?, ?> map) collection = map.values();
        else if (value instanceof Collection<?> found) collection = found;
        else if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            for (Object item : iterable) copy.add(item);
            collection = copy;
        } else if (value.getClass().isArray()) {
            List<Object> copy = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) copy.add(Array.get(value, i));
            collection = copy;
        } else return List.of();

        LinkedHashMap<String, Object> byId = new LinkedHashMap<>();
        for (Object candidate : collection) {
            if (candidate == null) continue;
            Move move = createMove(candidate);
            Object nameValue = move != null ? move.getName() : invoke(candidate, "getName");
            String id = ActionBattleMoveMetadataRules.canonicalMoveId(stringValue(nameValue));
            if (!id.isBlank()) byId.putIfAbsent(id, candidate);
        }
        return List.copyOf(byId.values());
    }

    private static Object singletonInstance(Class<?> type) {
        try {
            Field field = type.getField("INSTANCE");
            return field.get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeEither(Class<?> type, Object instance, String name) {
        try {
            Method method = type.getMethod(name);
            Object receiver = Modifier.isStatic(method.getModifiers()) ? null : instance;
            if (receiver == null && !Modifier.isStatic(method.getModifiers())) return null;
            return method.invoke(receiver);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Move createMove(Object template) {
        if (template instanceof Move move) return move;
        Object created = invoke(template, "create");
        return created instanceof Move move ? move : null;
    }

    private static String translationKey(Object template, Move move) {
        String key = translationKeyFrom(template);
        return !key.isBlank() ? key : translationKeyFrom(move);
    }

    private static String translationKeyFrom(Object source) {
        if (source == null) return "";
        Object display = firstNonNull(invoke(source, "getDisplayName"), invoke(source, "getDisplayNameComponent"));
        String key = componentTranslationKey(display);
        if (!key.isBlank()) return key;
        Object direct = firstNonNull(invoke(source, "getTranslationKey"), invoke(source, "getLangKey"));
        return direct != null ? stringValue(direct) : "";
    }

    private static String componentTranslationKey(Object component) {
        Object contents = invoke(component, "getContents");
        Object key = firstNonNull(invoke(contents, "getKey"), invoke(contents, "key"));
        return key != null ? stringValue(key) : "";
    }

    private static List<String> visualAssetSources(List<String> nativeEffects,
                                                   ActionBattleAddonVisualAudit.MoveVisuals addonVisuals,
                                                   boolean nmlEffectMetadata,
                                                   boolean explicitDelivery) {
        List<String> result = new ArrayList<>();
        if (nativeEffects != null && !nativeEffects.isEmpty()) result.add("COBBLEMON_NATIVE");
        if (addonVisuals != null && addonVisuals.hasVisuals()) result.add("EXTRA_MOVE_ANIMATIONS");
        if (nmlEffectMetadata || explicitDelivery) result.add("NML_EXPLICIT");
        if (result.isEmpty()) result.add("NML_FALLBACK");
        return List.copyOf(result);
    }

    private static JsonObject auditSummary(List<MoveAuditEntry> entries) {
        JsonObject summary = new JsonObject();
        summary.add("categories", countBy(entries.stream().map(MoveAuditEntry::category).toList()));
        summary.addProperty("extraMoveAnimationsCoveredMoves", entries.stream().filter(entry -> entry.extraMoveAnimationsResources() != null && entry.extraMoveAnimationsResources().hasVisuals()).count());
        summary.add("support", countBy(entries.stream().map(MoveAuditEntry::support).toList()));
        summary.add("handlingGroups", countBy(entries.stream().map(MoveAuditEntry::handlingGroup).toList()));
        summary.add("visualFamilies", countBy(entries.stream().map(MoveAuditEntry::visualFamily).toList()));
        summary.add("visualConfidence", countBy(entries.stream().map(MoveAuditEntry::visualConfidence).toList()));
        summary.add("visualSources", countBy(entries.stream().map(MoveAuditEntry::visualClassificationSource).toList()));
        summary.addProperty("movesWithCanonicalFlags", entries.stream().filter(entry -> !entry.flags().isEmpty()).count());
        summary.addProperty("movesWithCanonicalEffectMetadata", entries.stream().filter(entry -> !entry.canonicalEffectMetadata().isEmpty()).count());
        summary.addProperty("movesWithExplicitHandlers", entries.stream().filter(MoveAuditEntry::explicitHandlerPresent).count());
        summary.add("explicitHandlers", countBy(entries.stream().filter(MoveAuditEntry::explicitHandlerPresent).map(MoveAuditEntry::explicitHandler).toList()));
        summary.addProperty("movesWithExplicitDelivery", entries.stream().filter(MoveAuditEntry::explicitDeliveryProfile).count());
        summary.addProperty("movesWithNativeCobblemonEffects", entries.stream().filter(entry -> !entry.nativeCobblemonEffects().isEmpty()).count());
        return summary;
    }


    private static JsonObject handlingGroups(List<MoveAuditEntry> entries) {
        LinkedHashMap<String, JsonArray> groups = new LinkedHashMap<>();
        for (ActionBattleMoveHandlingGroup group : ActionBattleMoveHandlingGroup.values()) {
            groups.put(group.name(), new JsonArray());
        }
        for (MoveAuditEntry entry : entries) {
            groups.computeIfAbsent(entry.handlingGroup(), ignored -> new JsonArray()).add(entry.id());
        }
        JsonObject result = new JsonObject();
        groups.forEach((name, ids) -> {
            if (!ids.isEmpty()) result.add(name, ids);
        });
        return result;
    }

    private static JsonObject countBy(List<String> values) {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        values.stream().sorted().forEach(value -> counts.merge(value, 1, Integer::sum));
        JsonObject result = new JsonObject();
        counts.forEach(result::addProperty);
        return result;
    }

    private static String supportClassification(boolean executable, boolean explicitDelivery,
                                                boolean effectMetadata, boolean nativeVisual,
                                                boolean explicitHandler, Set<String> flags,
                                                Map<String, JsonElement> canonicalEffects) {
        boolean explicitSignal = explicitDelivery || effectMetadata || nativeVisual || explicitHandler;
        if (explicitHandler) return executable ? "EXPLICIT" : "PARTIAL";
        if (executable && explicitSignal) return "EXPLICIT";
        if (executable && requiresBespokeHandling(flags, canonicalEffects)) return "BESPOKE_REQUIRED";
        if (executable) return "GENERIC";
        if (explicitSignal) return "PARTIAL";
        return "UNCLASSIFIED";
    }

    private static boolean requiresBespokeHandling(Set<String> flags, Map<String, JsonElement> canonicalEffects) {
        if (flags != null) {
            for (String flag : List.of("charge", "recharge", "cantusetwice", "futuremove", "pledgecombo")) {
                if (flags.contains(flag)) return true;
            }
        }
        if (canonicalEffects == null || canonicalEffects.isEmpty()) return false;
        for (String field : List.of(
                "multihit", "multiaccuracy", "ohko", "breaksProtect", "forceSwitch", "selfSwitch",
                "sideCondition", "slotCondition", "pseudoWeather", "weather", "terrain",
                "stallingMove", "mindBlownRecoil", "willCrit"
        )) {
            if (canonicalEffects.containsKey(field)) return true;
        }
        return false;
    }

    private static Object firstNonNull(Object first, Object second) { return first != null ? first : second; }

    private static Object invoke(Object target, String methodName) {
        if (target == null || methodName == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String invokeNestedName(Object target, String getter) {
        Object nested = invoke(target, getter);
        Object name = invoke(nested, "getName");
        return name != null ? stringValue(name) : stringValue(nested);
    }

    private static String stringValue(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String normalizeType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "normal" : normalized;
    }
    private static String normalizeTokenValue(Object value) {
        return value == null ? "" : stringValue(value).trim().toLowerCase(Locale.ROOT);
    }
    private static int intValue(Object value, int fallback) { return value instanceof Number number ? number.intValue() : fallback; }
    private static double doubleValue(Object value) { return value instanceof Number number ? number.doubleValue() : Double.NaN; }
    private static Double finiteOrNull(double value) { return Double.isFinite(value) ? value : null; }

    public record AuditResult(Path output, int moveCount, String discoverySource) {}
    private record Catalog(List<Object> templates, String discoverySource) {}
    private record MoveAuditEntry(
            String id,
            String displayNameKey,
            String type,
            String category,
            int power,
            Double accuracy,
            int priority,
            int pp,
            String target,
            Double critRatio,
            List<String> flags,
            Map<String, JsonElement> canonicalEffectMetadata,
            boolean actionExecutable,
            boolean nmlMoveEffectMetadata,
            boolean explicitHandlerPresent,
            String explicitHandler,
            boolean explicitDeliveryProfile,
            String delivery,
            List<String> nativeCobblemonEffects,
            ActionBattleAddonVisualAudit.MoveVisuals extraMoveAnimationsResources,
            List<String> visualAssetSources,
            String preferredVisualAssetSource,
            String support,
            String handlingGroup,
            String visualFamily,
            String visualClassificationSource,
            String visualConfidence
    ) {}
}

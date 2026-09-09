package net.epiac9.cobblemonnml.battle.action.typeeffect.normal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ActionBattleNormalAdaptationRules {
    private static final String NORMAL = "normal";
    private static final int REQUIRED_MOVE_COUNT = 2;

    private ActionBattleNormalAdaptationRules() {}

    public static Set<String> adaptiveTypes(
            Collection<String> actualTypes,
            Collection<String> effectiveMoveTypes
    ) {
        if (!containsType(actualTypes, NORMAL)) {
            return Set.of();
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : effectiveMoveTypes) {
            String normalized = normalize(type);
            if (!normalized.isEmpty() && !NORMAL.equals(normalized)) {
                counts.merge(normalized, 1, Integer::sum);
            }
        }

        Set<String> result = new LinkedHashSet<>();
        counts.forEach((type, count) -> {
            if (count >= REQUIRED_MOVE_COUNT) {
                result.add(type);
            }
        });
        return Set.copyOf(result);
    }

    private static boolean containsType(Collection<String> types, String expected) {
        return types.stream().map(ActionBattleNormalAdaptationRules::normalize).anyMatch(expected::equals);
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}

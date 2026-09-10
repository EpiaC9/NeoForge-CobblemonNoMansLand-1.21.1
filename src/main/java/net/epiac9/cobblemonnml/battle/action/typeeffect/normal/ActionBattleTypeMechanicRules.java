package net.epiac9.cobblemonnml.battle.action.typeeffect.normal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ActionBattleTypeMechanicRules {
    private ActionBattleTypeMechanicRules() {}

    public static List<String> mechanicIdentities(Collection<String> actualTypes,
                                                   Collection<String> adaptiveTypes) {
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        if (actualTypes != null) {
            for (String type : actualTypes) addNormalized(identities, type);
        }
        if (adaptiveTypes != null) {
            List<String> sortedAdaptive = new ArrayList<>();
            for (String type : adaptiveTypes) {
                String normalized = normalize(type);
                if (!normalized.isEmpty()) sortedAdaptive.add(normalized);
            }
            sortedAdaptive.sort(String::compareTo);
            identities.addAll(sortedAdaptive);
        }
        return List.copyOf(identities);
    }

    public static boolean hasActualType(Collection<String> actualTypes, String type) {
        String expected = normalize(type);
        return actualTypes != null && !expected.isEmpty()
                && actualTypes.stream().map(ActionBattleTypeMechanicRules::normalize).anyMatch(expected::equals);
    }

    public static boolean hasMechanicBenefit(
            Collection<String> actualTypes,
            Collection<String> adaptiveTypes,
            String type
    ) {
        return hasActualType(actualTypes, type) || hasActualType(adaptiveTypes, type);
    }

    public static boolean hasSameMechanicImmunity(
            Collection<String> actualTypes,
            Collection<String> adaptiveTypes,
            String type
    ) {
        return hasMechanicBenefit(actualTypes, adaptiveTypes, type);
    }

    public static boolean hasCrossTypeImmunity(Collection<String> actualTypes, String type) {
        return hasActualType(actualTypes, type);
    }

    private static void addNormalized(Collection<String> identities, String type) {
        String normalized = normalize(type);
        if (!normalized.isEmpty()) identities.add(normalized);
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}

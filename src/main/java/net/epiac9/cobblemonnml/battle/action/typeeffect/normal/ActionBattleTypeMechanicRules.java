package net.epiac9.cobblemonnml.battle.action.typeeffect.normal;

import java.util.Collection;
import java.util.Locale;

public final class ActionBattleTypeMechanicRules {
    private ActionBattleTypeMechanicRules() {}

    public static boolean hasActualType(Collection<String> actualTypes, String type) {
        String expected = normalize(type);
        return actualTypes.stream().map(ActionBattleTypeMechanicRules::normalize).anyMatch(expected::equals);
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

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }
}

package net.epiac9.cobblemonnml.battle.action.typeeffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ActionBattleTypeMechanicHudProjection {
    private ActionBattleTypeMechanicHudProjection() {}

    public static List<String> identities(boolean active, Collection<String> actualTypes,
                                          Collection<String> adaptiveTypes) {
        if (!active) return List.of();
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (actualTypes != null) {
            for (String value : actualTypes) {
                String normalized = normalize(value);
                if (!normalized.isEmpty() && !"normal".equals(normalized)) ordered.add(normalized);
            }
        }
        List<String> adaptive = new ArrayList<>();
        if (adaptiveTypes != null) {
            for (String type : adaptiveTypes) {
                String normalized = normalize(type);
                if (!normalized.isEmpty()) adaptive.add(normalized);
            }
        }
        adaptive.sort(String::compareTo);
        ordered.addAll(adaptive);
        return List.copyOf(ordered);
    }


    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

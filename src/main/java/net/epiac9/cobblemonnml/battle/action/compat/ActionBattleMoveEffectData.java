package net.epiac9.cobblemonnml.battle.action.compat;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** NML-owned extension metadata for ACTION/type-mechanic routing only. */
public record ActionBattleMoveEffectData(Set<String> typeEffects) {
    public ActionBattleMoveEffectData {
        if (typeEffects == null || typeEffects.isEmpty()) {
            typeEffects = Set.of();
        } else {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String value : typeEffects) {
                String token = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                if (!token.isEmpty()) normalized.add(token);
            }
            typeEffects = Set.copyOf(normalized);
        }
    }

    public boolean routesTypeEffect(String identity) {
        if (identity == null) return false;
        return typeEffects.contains(identity.trim().toLowerCase(Locale.ROOT));
    }
}

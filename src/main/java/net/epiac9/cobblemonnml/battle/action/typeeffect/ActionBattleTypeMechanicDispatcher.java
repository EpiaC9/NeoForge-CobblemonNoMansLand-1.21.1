package net.epiac9.cobblemonnml.battle.action.typeeffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class ActionBattleTypeMechanicDispatcher {
    private ActionBattleTypeMechanicDispatcher() {}

    public static DispatchResult plan(Collection<String> identities, boolean mechanicSecondary) {
        List<String> ordered = orderedUnique(identities);
        if (mechanicSecondary) return new DispatchResult(0, 0, 0, List.of());
        return new DispatchResult(1, 1, 1, ordered);
    }

    public static int dispatch(Collection<String> identities, boolean mechanicSecondary,
                               Consumer<String> handler) {
        if (handler == null) return 0;
        DispatchResult result = plan(identities, mechanicSecondary);
        result.mechanicIdentities().forEach(handler);
        return result.mechanicIdentities().size();
    }

    private static List<String> orderedUnique(Collection<String> identities) {
        if (identities == null || identities.isEmpty()) return List.of();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String identity : identities) {
            String normalized = identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) unique.add(normalized);
        }
        return List.copyOf(new ArrayList<>(unique));
    }

    public record DispatchResult(int ppCommits, int cooldownCommits, int baseExecutions,
                                 List<String> mechanicIdentities) {
        public DispatchResult {
            ppCommits = Math.max(0, ppCommits);
            cooldownCommits = Math.max(0, cooldownCommits);
            baseExecutions = Math.max(0, baseExecutions);
            mechanicIdentities = mechanicIdentities != null ? List.copyOf(mechanicIdentities) : List.of();
        }
    }
}

package net.epiac9.cobblemonnml.battle.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ActionBattlePersistentEffectProjection {
    private final Map<String, Boolean> persistsOffField = new HashMap<>();

    public void register(String effectId, boolean persists) {
        if (effectId != null && !effectId.isBlank()) persistsOffField.put(effectId, persists);
    }

    public List<String> project(List<String> activeEffectIds) {
        if (activeEffectIds == null || activeEffectIds.isEmpty()) return List.of();
        LinkedHashSet<String> projected = new LinkedHashSet<>();
        for (String effectId : activeEffectIds) {
            if (effectId != null && Boolean.TRUE.equals(persistsOffField.get(effectId))) projected.add(effectId);
        }
        return List.copyOf(new ArrayList<>(projected));
    }
}

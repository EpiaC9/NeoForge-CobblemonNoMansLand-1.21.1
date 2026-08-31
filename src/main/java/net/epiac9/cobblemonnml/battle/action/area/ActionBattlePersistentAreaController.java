package net.epiac9.cobblemonnml.battle.action.area;

import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class ActionBattlePersistentAreaController {
    private static final ActionBattlePersistentAreaController GLOBAL = new ActionBattlePersistentAreaController();
    private final Map<UUID, ActiveArea> activeAreas = new HashMap<>();

    public static ActionBattlePersistentAreaController global() { return GLOBAL; }

    public UUID create(UUID battleId, UUID ownerPokemonUUID, String effectId, ActionBattlePosition anchor, ActionBattlePersistentAreaPreset preset, Consumer<ActionBattlePersistentAreaState> pulse) {
        if (battleId == null || ownerPokemonUUID == null || effectId == null || effectId.isBlank() || anchor == null || preset == null || pulse == null) return null;
        UUID areaId = UUID.randomUUID();
        ActionBattlePersistentAreaState state = new ActionBattlePersistentAreaState(areaId, battleId, ownerPokemonUUID, effectId, anchor, preset);
        activeAreas.put(areaId, new ActiveArea(state, pulse));
        if (preset.pulseImmediately()) pulse.accept(state);
        return areaId;
    }

    public void tick(UUID battleId) {
        if (battleId == null) return;
        List<UUID> ids = new ArrayList<>();
        for (Map.Entry<UUID, ActiveArea> entry : activeAreas.entrySet()) if (battleId.equals(entry.getValue().state().battleId())) ids.add(entry.getKey());
        for (UUID id : ids) {
            ActiveArea active = activeAreas.get(id);
            if (active == null) continue;
            ActionBattlePersistentAreaState state = active.state();
            state.advance();
            if (state.shouldExpire()) {
                activeAreas.remove(id);
                continue;
            }
            if (state.shouldPulse()) {
                active.pulse().accept(state);
                state.advancePulse();
            }
        }
    }

    public void clearBattle(UUID battleId) {
        if (battleId != null) activeAreas.entrySet().removeIf(entry -> battleId.equals(entry.getValue().state().battleId()));
    }

    public List<ActionBattlePersistentAreaState> statesForBattle(UUID battleId) {
        if (battleId == null) return List.of();
        List<ActionBattlePersistentAreaState> result = new ArrayList<>();
        for (ActiveArea active : activeAreas.values()) if (battleId.equals(active.state().battleId())) result.add(active.state());
        return List.copyOf(result);
    }

    private record ActiveArea(ActionBattlePersistentAreaState state, Consumer<ActionBattlePersistentAreaState> pulse) {}
}

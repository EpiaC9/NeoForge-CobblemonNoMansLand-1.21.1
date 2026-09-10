package net.epiac9.cobblemonnml.battle.action.health;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleDotController {
    private final Map<UUID, Map<UUID, ActiveDot>> byBattle = new HashMap<>();

    public UUID start(UUID battleId, UUID sourcePokemonUUID, UUID targetPokemonUUID,
                      ActionBattleDotSpec spec, long currentTick) {
        if (battleId == null || targetPokemonUUID == null || spec == null || currentTick < 0L) {
            throw new IllegalArgumentException("Cannot start DoT with invalid battle, target, spec, or tick.");
        }
        UUID handleId = UUID.randomUUID();
        byBattle.computeIfAbsent(battleId, ignored -> new HashMap<>()).put(handleId,
                new ActiveDot(handleId, sourcePokemonUUID, targetPokemonUUID, spec, currentTick));
        return handleId;
    }

    public List<ActionBattleDotTick> tickBattle(UUID battleId, long currentTick) {
        if (battleId == null || currentTick < 0L) return List.of();
        Map<UUID, ActiveDot> dots = byBattle.get(battleId);
        if (dots == null || dots.isEmpty()) return List.of();

        List<ActionBattleDotTick> due = new ArrayList<>();
        Iterator<Map.Entry<UUID, ActiveDot>> iterator = dots.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveDot active = iterator.next().getValue();
            active.collectDue(currentTick, due);
            if (active.expired(currentTick)) iterator.remove();
        }
        if (dots.isEmpty()) byBattle.remove(battleId);
        due.sort(java.util.Comparator.comparingLong(ActionBattleDotTick::scheduledTick)
                .thenComparing(ActionBattleDotTick::effectId)
                .thenComparing(tick -> tick.handleId().toString()));
        return List.copyOf(due);
    }

    public boolean stop(UUID battleId, UUID handleId) {
        if (battleId == null || handleId == null) return false;
        Map<UUID, ActiveDot> dots = byBattle.get(battleId);
        if (dots == null) return false;
        boolean removed = dots.remove(handleId) != null;
        if (dots.isEmpty()) byBattle.remove(battleId);
        return removed;
    }

    public int clearTarget(UUID battleId, UUID targetPokemonUUID) {
        if (battleId == null || targetPokemonUUID == null) return 0;
        Map<UUID, ActiveDot> dots = byBattle.get(battleId);
        if (dots == null) return 0;
        int before = dots.size();
        dots.entrySet().removeIf(entry -> targetPokemonUUID.equals(entry.getValue().targetPokemonUUID));
        int removed = before - dots.size();
        if (dots.isEmpty()) byBattle.remove(battleId);
        return removed;
    }

    public void clearBattle(UUID battleId) {
        if (battleId != null) byBattle.remove(battleId);
    }

    public void clearAll() {
        byBattle.clear();
    }

    public int activeCount(UUID battleId) {
        Map<UUID, ActiveDot> dots = battleId != null ? byBattle.get(battleId) : null;
        return dots != null ? dots.size() : 0;
    }

    private static final class ActiveDot {
        private final UUID handleId;
        private final UUID sourcePokemonUUID;
        private final UUID targetPokemonUUID;
        private final ActionBattleDotSpec spec;
        private final long startTick;
        private final long endTick;
        private long nextTick;
        private long tickIndex;

        private ActiveDot(UUID handleId, UUID sourcePokemonUUID, UUID targetPokemonUUID,
                          ActionBattleDotSpec spec, long startTick) {
            this.handleId = handleId;
            this.sourcePokemonUUID = sourcePokemonUUID;
            this.targetPokemonUUID = targetPokemonUUID;
            this.spec = spec;
            this.startTick = startTick;
            this.endTick = spec.ownerManagedDuration() ? Long.MAX_VALUE
                    : ActionBattleTiming.safeAdd(startTick, spec.durationTicks());
            this.nextTick = ActionBattleTiming.safeAdd(startTick, spec.tickIntervalTicks());
        }

        private void collectDue(long currentTick, List<ActionBattleDotTick> due) {
            while (nextTick <= currentTick && nextTick < endTick) {
                tickIndex++;
                due.add(new ActionBattleDotTick(handleId, spec.effectId(), sourcePokemonUUID,
                        targetPokemonUUID, startTick, nextTick, tickIndex, spec));
                long advanced = ActionBattleTiming.safeAdd(nextTick, spec.tickIntervalTicks());
                if (advanced <= nextTick) {
                    nextTick = Long.MAX_VALUE;
                    break;
                }
                nextTick = advanced;
            }
        }

        private boolean expired(long currentTick) {
            return !spec.ownerManagedDuration() && currentTick >= endTick;
        }
    }
}

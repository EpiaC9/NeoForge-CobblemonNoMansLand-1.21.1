package net.epiac9.cobblemonnml.battle.action;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ActionBattleTargetHistory {
    private final int retentionTicks;
    private final Map<UUID, ArrayDeque<Sample>> samples = new HashMap<>();

    ActionBattleTargetHistory(int retentionTicks) {
        this.retentionTicks = Math.max(1, retentionTicks);
    }

    void record(UUID entityUUID, long tick, double x, double y, double z) {
        if (entityUUID == null || tick < 0L) return;
        ArrayDeque<Sample> history = samples.computeIfAbsent(entityUUID, ignored -> new ArrayDeque<>());
        if (!history.isEmpty() && history.peekLast().tick() == tick) history.removeLast();
        history.addLast(new Sample(tick, x, y, z));
        long cutoff = tick - retentionTicks;
        while (history.size() > 1 && history.peekFirst().tick() < cutoff) history.removeFirst();
    }

    Position positionAtOrBefore(UUID entityUUID, long targetTick) {
        ArrayDeque<Sample> history = entityUUID != null ? samples.get(entityUUID) : null;
        if (history == null || history.isEmpty()) return null;
        Sample best = null;
        for (Sample sample : history) {
            if (sample.tick() <= targetTick) best = sample;
            else break;
        }
        if (best == null) best = history.peekFirst();
        return new Position(best.x(), best.y(), best.z());
    }

    void clear(UUID entityUUID) { if (entityUUID != null) samples.remove(entityUUID); }
    void clearAll() { samples.clear(); }

    record Position(double x, double y, double z) {}
    private record Sample(long tick, double x, double y, double z) {}
}

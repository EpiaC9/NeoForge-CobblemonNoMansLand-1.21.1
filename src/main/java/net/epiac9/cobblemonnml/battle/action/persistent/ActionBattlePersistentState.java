package net.epiac9.cobblemonnml.battle.action.persistent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattlePersistentState {
    public static final long DOT_INTERVAL_TICKS = 40L;
    public static final long PERISH_DURATION_TICKS = 480L;
    private static final float BOUND_FRACTION = 0.02F;
    private static final float NIGHTMARE_FRACTION = 0.03F;
    private final Map<ActionBattlePersistentType, Instance> effects = new EnumMap<>(ActionBattlePersistentType.class);

    public boolean applyNightmare(UUID sourcePokemonUUID, long currentTick) { return applyUntimed(ActionBattlePersistentType.NIGHTMARE, sourcePokemonUUID, currentTick); }

    public boolean applyPerishSong(UUID sourcePokemonUUID, long currentTick) {
        if (!valid(sourcePokemonUUID, currentTick) || effects.containsKey(ActionBattlePersistentType.PERISH_SONG)) return false;
        effects.put(ActionBattlePersistentType.PERISH_SONG, new Instance(sourcePokemonUUID, currentTick + PERISH_DURATION_TICKS, 0L, PERISH_DURATION_TICKS));
        return true;
    }

    public boolean applyBound(UUID sourcePokemonUUID, long currentTick, long durationTicks) {
        if (!valid(sourcePokemonUUID, currentTick) || durationTicks <= 0L || effects.containsKey(ActionBattlePersistentType.BOUND)) return false;
        effects.put(ActionBattlePersistentType.BOUND, new Instance(sourcePokemonUUID, currentTick + durationTicks, currentTick + DOT_INTERVAL_TICKS, durationTicks));
        return true;
    }

    public boolean has(ActionBattlePersistentType type, long currentTick) {
        Instance instance = type != null ? effects.get(type) : null;
        if (instance == null) return false;
        if (instance.endTick > 0L && currentTick >= instance.endTick && type != ActionBattlePersistentType.PERISH_SONG) return false;
        return true;
    }

    public UUID sourcePokemonUUID(ActionBattlePersistentType type) {
        Instance instance = type != null ? effects.get(type) : null;
        return instance != null ? instance.sourcePokemonUUID : null;
    }

    public long remainingTicks(ActionBattlePersistentType type, long currentTick) {
        Instance instance = type != null ? effects.get(type) : null;
        if (instance == null) return 0L;
        if (instance.endTick <= 0L) return Long.MAX_VALUE;
        return Math.max(0L, instance.endTick - currentTick);
    }

    public long durationTicks(ActionBattlePersistentType type) {
        Instance instance = type != null ? effects.get(type) : null;
        return instance != null && instance.durationTicks > 0L ? instance.durationTicks : Long.MAX_VALUE;
    }

    public List<ActionBattlePersistentEvent> tick(long currentTick) {
        if (currentTick < 0L || effects.isEmpty()) return List.of();
        List<ActionBattlePersistentEvent> events = new ArrayList<>();
        tickDot(ActionBattlePersistentType.NIGHTMARE, NIGHTMARE_FRACTION, currentTick, events);
        tickBound(currentTick, events);
        Instance perish = effects.get(ActionBattlePersistentType.PERISH_SONG);
        if (perish != null && currentTick >= perish.endTick) {
            effects.remove(ActionBattlePersistentType.PERISH_SONG);
            events.add(ActionBattlePersistentEvent.faint(ActionBattlePersistentType.PERISH_SONG, perish.sourcePokemonUUID));
        }
        return List.copyOf(events);
    }

    public void onPokemonRecalled(long currentTick) {
        effects.remove(ActionBattlePersistentType.PERISH_SONG);
        effects.remove(ActionBattlePersistentType.NIGHTMARE);
    }

    public void onSleepEnded() { effects.remove(ActionBattlePersistentType.NIGHTMARE); }

    public boolean onSourceUnavailable(UUID sourcePokemonUUID) {
        if (sourcePokemonUUID == null) return false;
        Instance bound = effects.get(ActionBattlePersistentType.BOUND);
        if (bound == null || !sourcePokemonUUID.equals(bound.sourcePokemonUUID)) return false;
        effects.remove(ActionBattlePersistentType.BOUND);
        return true;
    }

    public boolean clear(ActionBattlePersistentType type) { return type != null && effects.remove(type) != null; }
    public boolean isEmpty() { return effects.isEmpty(); }

    private boolean applyUntimed(ActionBattlePersistentType type, UUID sourcePokemonUUID, long currentTick) {
        if (!valid(sourcePokemonUUID, currentTick) || effects.containsKey(type)) return false;
        effects.put(type, new Instance(sourcePokemonUUID, 0L, currentTick + DOT_INTERVAL_TICKS, 0L));
        return true;
    }

    private void tickDot(ActionBattlePersistentType type, float fraction, long currentTick, List<ActionBattlePersistentEvent> events) {
        Instance instance = effects.get(type);
        if (instance == null || currentTick < instance.nextDotTick) return;
        while (currentTick >= instance.nextDotTick) {
            events.add(ActionBattlePersistentEvent.damage(type, instance.sourcePokemonUUID, fraction));
            instance.nextDotTick += DOT_INTERVAL_TICKS;
        }
    }

    private void tickBound(long currentTick, List<ActionBattlePersistentEvent> events) {
        Instance bound = effects.get(ActionBattlePersistentType.BOUND);
        if (bound == null) return;
        long damageUntil = Math.min(currentTick, Math.max(0L, bound.endTick - 1L));
        while (damageUntil >= bound.nextDotTick) {
            events.add(ActionBattlePersistentEvent.damage(ActionBattlePersistentType.BOUND, bound.sourcePokemonUUID, BOUND_FRACTION));
            bound.nextDotTick += DOT_INTERVAL_TICKS;
        }
        if (currentTick >= bound.endTick) {
            effects.remove(ActionBattlePersistentType.BOUND);
            events.add(ActionBattlePersistentEvent.ended(ActionBattlePersistentType.BOUND, bound.sourcePokemonUUID));
        }
    }

    private static boolean valid(UUID sourcePokemonUUID, long currentTick) { return sourcePokemonUUID != null && currentTick >= 0L; }

    private static final class Instance {
        private final UUID sourcePokemonUUID;
        private final long endTick;
        private long nextDotTick;
        private final long durationTicks;
        private Instance(UUID sourcePokemonUUID, long endTick, long nextDotTick, long durationTicks) {
            this.sourcePokemonUUID = sourcePokemonUUID;
            this.endTick = endTick;
            this.nextDotTick = nextDotTick;
            this.durationTicks = durationTicks;
        }
    }
}

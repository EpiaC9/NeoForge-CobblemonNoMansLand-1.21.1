package net.epiac9.cobblemonnml.battle.action.typeeffect.ghost;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleGhostCurseState {
    private final EnumMap<ActionBattleGhostCurseType, Instance> curses =
            new EnumMap<>(ActionBattleGhostCurseType.class);

    public void apply(UUID casterPokemonUUID, ActionBattleGhostCurseType type, long currentTick) {
        long duration = type.timed()
                ? ActionBattleGhostRules.TIMED_CURSE_TICKS
                : ActionBattleGhostRules.PENDING_CURSE_TICKS;
        long nextTick = type == ActionBattleGhostCurseType.DECAY
                ? currentTick + ActionBattleGhostRules.DECAY_INTERVAL_TICKS
                : Long.MAX_VALUE;
        curses.put(type, new Instance(casterPokemonUUID, currentTick + duration, nextTick));
    }

    public Optional<View> view(ActionBattleGhostCurseType type, long currentTick) {
        Instance instance = curses.get(type);
        if (!active(instance, currentTick)) return Optional.empty();
        return Optional.of(toView(type, instance, currentTick));
    }

    public List<View> views(long currentTick) {
        List<View> views = new ArrayList<>();
        for (var entry : curses.entrySet()) {
            if (active(entry.getValue(), currentTick)) {
                views.add(toView(entry.getKey(), entry.getValue(), currentTick));
            }
        }
        views.sort(Comparator.comparing(View::type));
        return List.copyOf(views);
    }

    public boolean consume(ActionBattleGhostCurseType type, long currentTick) {
        Instance instance = curses.get(type);
        if (!active(instance, currentTick)) {
            curses.remove(type);
            return false;
        }
        curses.remove(type);
        return true;
    }

    public List<TickEvent> tick(long currentTick) {
        List<TickEvent> events = new ArrayList<>();
        for (var entry : List.copyOf(curses.entrySet())) {
            ActionBattleGhostCurseType type = entry.getKey();
            Instance instance = entry.getValue();
            if (type == ActionBattleGhostCurseType.DECAY) {
                long scheduledTick = instance.nextTick();
                while (scheduledTick <= currentTick && scheduledTick <= instance.expiresAtTick()) {
                    events.add(new TickEvent(type, instance.casterPokemonUUID(), scheduledTick));
                    scheduledTick += ActionBattleGhostRules.DECAY_INTERVAL_TICKS;
                }
                curses.put(type, new Instance(instance.casterPokemonUUID(), instance.expiresAtTick(), scheduledTick));
            }
            if (currentTick >= instance.expiresAtTick()) curses.remove(type);
        }
        events.sort(Comparator.comparingLong(TickEvent::scheduledTick));
        return List.copyOf(events);
    }

    public void removeOwnedBy(UUID casterPokemonUUID) {
        curses.entrySet().removeIf(entry -> entry.getValue().casterPokemonUUID().equals(casterPokemonUUID));
    }

    public boolean isEmpty() {
        return curses.isEmpty();
    }

    private static boolean active(Instance instance, long currentTick) {
        return instance != null && currentTick < instance.expiresAtTick();
    }

    private static View toView(ActionBattleGhostCurseType type, Instance instance, long currentTick) {
        boolean showCountdown = type.timed();
        long totalTicks = showCountdown ? ActionBattleGhostRules.TIMED_CURSE_TICKS : 0L;
        long remainingTicks = showCountdown ? Math.max(0L, instance.expiresAtTick() - currentTick) : 0L;
        return new View(type, instance.casterPokemonUUID(), instance.expiresAtTick(),
                remainingTicks, totalTicks, showCountdown);
    }

    private record Instance(UUID casterPokemonUUID, long expiresAtTick, long nextTick) {}

    public record View(ActionBattleGhostCurseType type, UUID casterPokemonUUID, long expiresAtTick,
                       long remainingTicks, long totalTicks, boolean showCountdown) {}

    public record TickEvent(ActionBattleGhostCurseType type, UUID casterPokemonUUID, long scheduledTick) {}
}

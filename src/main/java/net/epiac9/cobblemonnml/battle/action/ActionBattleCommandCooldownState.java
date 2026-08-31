package net.epiac9.cobblemonnml.battle.action;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ActionBattleCommandCooldownState {
    enum Side { PLAYER, TRAINER }

    private final Map<UUID, Long> moveEndTicks = new HashMap<>();
    private final Map<UUID, Long> moveDurationTicks = new HashMap<>();
    private final Map<UUID, Long> movementEndTicks = new HashMap<>();
    private final Map<UUID, Long> movementDurationTicks = new HashMap<>();
    private final Cooldown playerSwap = new Cooldown();
    private final Cooldown trainerSwap = new Cooldown();

    boolean startMove(UUID pokemonUUID, long currentTick, long durationTicks) {
        return start(moveEndTicks, moveDurationTicks, pokemonUUID, currentTick, durationTicks);
    }

    boolean moveOnCooldown(UUID pokemonUUID, long currentTick) { return onCooldown(moveEndTicks, pokemonUUID, currentTick); }
    long moveEndTick(UUID pokemonUUID) { return endTick(moveEndTicks, pokemonUUID); }
    long moveDurationTicks(UUID pokemonUUID) { return duration(moveDurationTicks, pokemonUUID); }

    boolean startMovement(UUID pokemonUUID, long currentTick, long durationTicks) {
        return start(movementEndTicks, movementDurationTicks, pokemonUUID, currentTick, durationTicks);
    }

    boolean movementOnCooldown(UUID pokemonUUID, long currentTick) { return onCooldown(movementEndTicks, pokemonUUID, currentTick); }
    long movementEndTick(UUID pokemonUUID) { return endTick(movementEndTicks, pokemonUUID); }
    long movementDurationTicks(UUID pokemonUUID) { return duration(movementDurationTicks, pokemonUUID); }

    boolean setAll(UUID pokemonUUID, Side side, long currentTick, long durationTicks) {
        if (!valid(pokemonUUID, currentTick, durationTicks) || side == null) return false;
        start(moveEndTicks, moveDurationTicks, pokemonUUID, currentTick, durationTicks);
        start(movementEndTicks, movementDurationTicks, pokemonUUID, currentTick, durationTicks);
        swap(side).start(currentTick, durationTicks);
        return true;
    }

    boolean addPenalty(UUID pokemonUUID, Side side, long currentTick, long penaltyTicks) {
        if (!valid(pokemonUUID, currentTick, penaltyTicks) || side == null) return false;
        extend(moveEndTicks, moveDurationTicks, pokemonUUID, currentTick, penaltyTicks);
        extend(movementEndTicks, movementDurationTicks, pokemonUUID, currentTick, penaltyTicks);
        swap(side).extend(currentTick, penaltyTicks);
        return true;
    }

    boolean startSwap(Side side, long currentTick, long durationTicks) {
        return side != null && currentTick >= 0L && durationTicks > 0L && swap(side).start(currentTick, durationTicks);
    }

    boolean swapOnCooldown(Side side, long currentTick) {
        return side != null && currentTick >= 0L && swap(side).onCooldown(currentTick);
    }

    long swapEndTick(Side side) { return side != null ? swap(side).endTick : 0L; }
    long swapDurationTicks(Side side) { return side != null ? swap(side).durationTicks : 0L; }

    private Cooldown swap(Side side) { return side == Side.PLAYER ? playerSwap : trainerSwap; }

    private static boolean start(Map<UUID, Long> ends, Map<UUID, Long> durations, UUID pokemonUUID, long currentTick, long durationTicks) {
        if (!valid(pokemonUUID, currentTick, durationTicks)) return false;
        ends.put(pokemonUUID, ActionBattleTiming.safeAdd(currentTick, durationTicks));
        durations.put(pokemonUUID, durationTicks);
        return true;
    }

    private static boolean onCooldown(Map<UUID, Long> ends, UUID pokemonUUID, long currentTick) {
        return pokemonUUID != null && currentTick >= 0L && currentTick < ends.getOrDefault(pokemonUUID, 0L);
    }

    private static long endTick(Map<UUID, Long> ends, UUID pokemonUUID) { return pokemonUUID != null ? ends.getOrDefault(pokemonUUID, 0L) : 0L; }
    private static long duration(Map<UUID, Long> durations, UUID pokemonUUID) { return pokemonUUID != null ? durations.getOrDefault(pokemonUUID, 0L) : 0L; }

    private static void extend(Map<UUID, Long> ends, Map<UUID, Long> durations, UUID pokemonUUID, long currentTick, long penaltyTicks) {
        long currentEnd = ends.getOrDefault(pokemonUUID, 0L);
        if (currentEnd > currentTick) {
            ends.put(pokemonUUID, ActionBattleTiming.safeAdd(currentEnd, penaltyTicks));
            durations.put(pokemonUUID, ActionBattleTiming.safeAdd(durations.getOrDefault(pokemonUUID, Math.max(0L, currentEnd - currentTick)), penaltyTicks));
        } else {
            ends.put(pokemonUUID, ActionBattleTiming.safeAdd(currentTick, penaltyTicks));
            durations.put(pokemonUUID, penaltyTicks);
        }
    }

    private static boolean valid(UUID pokemonUUID, long currentTick, long durationTicks) {
        return pokemonUUID != null && currentTick >= 0L && durationTicks > 0L;
    }

    private static final class Cooldown {
        private long endTick;
        private long durationTicks;

        private boolean start(long currentTick, long durationTicks) {
            if (currentTick < 0L || durationTicks <= 0L) return false;
            endTick = ActionBattleTiming.safeAdd(currentTick, durationTicks);
            this.durationTicks = durationTicks;
            return true;
        }

        private boolean onCooldown(long currentTick) { return currentTick >= 0L && currentTick < endTick; }

        private void extend(long currentTick, long penaltyTicks) {
            if (endTick > currentTick) {
                endTick = ActionBattleTiming.safeAdd(endTick, penaltyTicks);
                durationTicks = ActionBattleTiming.safeAdd(durationTicks, penaltyTicks);
            } else {
                endTick = ActionBattleTiming.safeAdd(currentTick, penaltyTicks);
                durationTicks = penaltyTicks;
            }
        }
    }
}

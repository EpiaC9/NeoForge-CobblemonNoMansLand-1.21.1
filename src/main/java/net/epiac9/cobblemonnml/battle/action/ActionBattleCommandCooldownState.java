package net.epiac9.cobblemonnml.battle.action;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ActionBattleCommandCooldownState {
    enum Side { PLAYER, TRAINER }

    private final Map<UUID, Long> moveEndTicks = new HashMap<>();
    private final Map<UUID, Long> moveDurationTicks = new HashMap<>();
    private final Map<MoveSlotKey, Long> personalMoveEndTicks = new HashMap<>();
    private final Map<MoveSlotKey, Long> personalMoveDurationTicks = new HashMap<>();
    private final Map<UUID, Long> movementEndTicks = new HashMap<>();
    private final Map<UUID, Long> movementDurationTicks = new HashMap<>();
    private final Cooldown playerSwap = new Cooldown();
    private final Cooldown trainerSwap = new Cooldown();

    boolean startMove(UUID pokemonUUID, long currentTick, long durationTicks) {
        return startSharedAbility(pokemonUUID, currentTick, durationTicks);
    }

    boolean moveOnCooldown(UUID pokemonUUID, long currentTick) { return sharedAbilityOnCooldown(pokemonUUID, currentTick); }
    long moveEndTick(UUID pokemonUUID) { return endTick(moveEndTicks, pokemonUUID); }
    long moveDurationTicks(UUID pokemonUUID) { return duration(moveDurationTicks, pokemonUUID); }

    boolean startSharedAbility(UUID pokemonUUID, long currentTick, long durationTicks) {
        return start(moveEndTicks, moveDurationTicks, pokemonUUID, currentTick, durationTicks);
    }

    boolean sharedAbilityOnCooldown(UUID pokemonUUID, long currentTick) {
        return onCooldown(moveEndTicks, pokemonUUID, currentTick);
    }

    boolean startPersonalMove(UUID pokemonUUID, int moveSlot, long currentTick, long durationTicks) {
        if (pokemonUUID == null || moveSlot < 0 || currentTick < 0L || durationTicks <= 0L) return false;
        MoveSlotKey key = new MoveSlotKey(pokemonUUID, moveSlot);
        storeCooldown(personalMoveEndTicks, personalMoveDurationTicks, key,
                ActionBattleTiming.safeAdd(currentTick, durationTicks), durationTicks);
        return true;
    }

    boolean personalMoveOnCooldown(UUID pokemonUUID, int moveSlot, long currentTick) {
        return pokemonUUID != null && moveSlot >= 0 && currentTick >= 0L
                && currentTick < personalMoveEndTicks.getOrDefault(new MoveSlotKey(pokemonUUID, moveSlot), 0L);
    }

    EffectiveCooldown effectiveMoveCooldown(UUID pokemonUUID, int moveSlot, long currentTick) {
        if (pokemonUUID == null || moveSlot < 0 || currentTick < 0L) return EffectiveCooldown.NONE;
        long sharedRemaining = Math.max(0L, moveEndTick(pokemonUUID) - currentTick);
        long sharedDuration = moveDurationTicks(pokemonUUID);
        MoveSlotKey key = new MoveSlotKey(pokemonUUID, moveSlot);
        long personalRemaining = Math.max(0L, personalMoveEndTicks.getOrDefault(key, 0L) - currentTick);
        long personalDuration = personalMoveDurationTicks.getOrDefault(key, 0L);
        if (personalRemaining > sharedRemaining) return new EffectiveCooldown(personalRemaining, personalDuration);
        if (sharedRemaining > personalRemaining) return new EffectiveCooldown(sharedRemaining, sharedDuration);
        return sharedRemaining > 0L
                ? new EffectiveCooldown(sharedRemaining, Math.max(sharedDuration, personalDuration))
                : EffectiveCooldown.NONE;
    }

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

    boolean addMovementPenalty(UUID pokemonUUID, long currentTick, long penaltyTicks) {
        if (!valid(pokemonUUID, currentTick, penaltyTicks)) return false;
        extend(movementEndTicks, movementDurationTicks, pokemonUUID, currentTick, penaltyTicks);
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
        storeCooldown(ends, durations, pokemonUUID, ActionBattleTiming.safeAdd(currentTick, durationTicks), durationTicks);
        return true;
    }

    private static boolean onCooldown(Map<UUID, Long> ends, UUID pokemonUUID, long currentTick) {
        return pokemonUUID != null && currentTick >= 0L && currentTick < ends.getOrDefault(pokemonUUID, 0L);
    }

    private static long endTick(Map<UUID, Long> ends, UUID pokemonUUID) { return pokemonUUID != null ? ends.getOrDefault(pokemonUUID, 0L) : 0L; }
    private static long duration(Map<UUID, Long> durations, UUID pokemonUUID) { return pokemonUUID != null ? durations.getOrDefault(pokemonUUID, 0L) : 0L; }

    private static <K> void storeCooldown(Map<K, Long> ends, Map<K, Long> durations, K key, long endTick, long durationTicks) {
        ends.put(key, endTick);
        durations.put(key, durationTicks);
    }

    private static void extend(Map<UUID, Long> ends, Map<UUID, Long> durations, UUID pokemonUUID, long currentTick, long penaltyTicks) {
        long currentEnd = ends.getOrDefault(pokemonUUID, 0L);
        long currentDuration = durations.getOrDefault(pokemonUUID, Math.max(0L, currentEnd - currentTick));
        if (currentEnd > currentTick) {
            storeCooldown(ends, durations, pokemonUUID, ActionBattleTiming.safeAdd(currentEnd, penaltyTicks), ActionBattleTiming.safeAdd(currentDuration, penaltyTicks));
        } else {
            storeCooldown(ends, durations, pokemonUUID, ActionBattleTiming.safeAdd(currentTick, penaltyTicks), penaltyTicks);
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

    record EffectiveCooldown(long remainingTicks, long durationTicks) {
        static final EffectiveCooldown NONE = new EffectiveCooldown(0L, 0L);
    }

    private record MoveSlotKey(UUID pokemonUUID, int moveSlot) {}
}

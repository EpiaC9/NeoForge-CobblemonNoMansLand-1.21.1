package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ActionBattleWaterState {
    public enum ApplyShieldResult { STARTED, REPLACED }
    public enum ShieldEndReason { PROTECTED_HIT, EXPIRED, REPLACED }
    public record AquaShieldView(long instanceId, long remainingTicks, long totalDurationTicks, boolean healEligible) {}
    public record ImmobilizedView(long remainingTicks, long totalDurationTicks) {}
    public record ShieldEndEvent(long instanceId, ShieldEndReason reason, boolean healEligible,
                                 boolean reduceDeterioratingShield) {}

    private final List<ShieldEndEvent> shieldEndEvents = new ArrayList<>();
    private ActionBattleAquaShieldState aquaShield;
    private ActionBattleImmobilizedState immobilized;
    private long nextShieldInstanceId = 1L;

    public ApplyShieldResult applyShield(long currentTick, boolean waterTyped, boolean protectActive) {
        requireTick(currentTick);
        tick(currentTick);
        ApplyShieldResult result = aquaShield == null ? ApplyShieldResult.STARTED : ApplyShieldResult.REPLACED;
        if (aquaShield != null) endShield(ShieldEndReason.REPLACED, !protectActive);
        aquaShield = new ActionBattleAquaShieldState(nextShieldInstanceId++, currentTick, waterTyped);
        return result;
    }

    public boolean breakShield(long currentTick, boolean protectActive) {
        requireTick(currentTick);
        tick(currentTick);
        if (aquaShield == null) return false;
        endShield(ShieldEndReason.PROTECTED_HIT, !protectActive);
        return true;
    }

    public boolean applyImmobilized(long currentTick) {
        requireTick(currentTick);
        immobilized = new ActionBattleImmobilizedState(currentTick);
        return true;
    }

    public void tick(long currentTick) {
        requireTick(currentTick);
        if (aquaShield != null && !aquaShield.active(currentTick)) {
            endShield(ShieldEndReason.EXPIRED, false);
        }
        if (immobilized != null && !immobilized.active(currentTick)) immobilized = null;
    }

    public Optional<AquaShieldView> aquaShieldView(long currentTick) {
        tick(currentTick);
        return aquaShield == null ? Optional.empty() : Optional.of(new AquaShieldView(
                aquaShield.instanceId(), aquaShield.remainingTicks(currentTick),
                ActionBattleWaterRules.AQUA_SHIELD_DURATION_TICKS, aquaShield.healEligible()));
    }

    public Optional<ImmobilizedView> immobilizedView(long currentTick) {
        tick(currentTick);
        return immobilized == null ? Optional.empty() : Optional.of(new ImmobilizedView(
                immobilized.remainingTicks(currentTick), ActionBattleWaterRules.IMMOBILIZED_DURATION_TICKS));
    }

    public List<ShieldEndEvent> drainShieldEndEvents() {
        if (shieldEndEvents.isEmpty()) return List.of();
        List<ShieldEndEvent> drained = List.copyOf(shieldEndEvents);
        shieldEndEvents.clear();
        return drained;
    }

    public void clearSilently() {
        aquaShield = null;
        immobilized = null;
        shieldEndEvents.clear();
    }

    public boolean isEmpty() {
        return aquaShield == null && immobilized == null && shieldEndEvents.isEmpty();
    }

    private void endShield(ShieldEndReason reason, boolean reduceDeterioratingShield) {
        shieldEndEvents.add(new ShieldEndEvent(aquaShield.instanceId(), reason, aquaShield.healEligible(),
                reduceDeterioratingShield));
        aquaShield = null;
    }

    private static void requireTick(long currentTick) {
        if (currentTick < 0L) throw new IllegalArgumentException("Water state tick cannot be negative.");
    }
}

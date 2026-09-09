package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

public final class ActionBattleBugCarapaceState {
    private final long physicalExpiresAt;
    private final long effectZoneExpiresAt;
    private boolean destroyed;

    public ActionBattleBugCarapaceState(boolean blocksProjectiles, boolean effectZone, long createdAt) {
        physicalExpiresAt = blocksProjectiles
                ? createdAt + ActionBattleBugRules.CARAPACE_TICKS : createdAt;
        effectZoneExpiresAt = effectZone
                ? createdAt + ActionBattleBugRules.LOCKOUT_TICKS : createdAt;
    }

    public ProjectileOutcome onProjectileCollision(long tick) {
        Snapshot current = snapshot(tick);
        if (!current.active()) return ProjectileOutcome.MISS;
        destroyed = true;
        return current.physicalActive() ? ProjectileOutcome.DESTROY_BOTH
                : ProjectileOutcome.DESTROY_CONSTRUCT_AND_CONTINUE;
    }

    public Snapshot snapshot(long tick) {
        long physicalRemaining = destroyed ? 0L : remaining(physicalExpiresAt, tick);
        long zoneRemaining = destroyed ? 0L : remaining(effectZoneExpiresAt, tick);
        return new Snapshot(physicalRemaining > 0L, zoneRemaining > 0L,
                physicalRemaining, zoneRemaining);
    }

    public boolean physicalActive(long tick) { return snapshot(tick).physicalActive(); }
    public boolean effectZoneActive(long tick) { return snapshot(tick).effectZoneActive(); }
    public boolean active(long tick) { return snapshot(tick).active(); }
    public long physicalRemaining(long tick) { return snapshot(tick).physicalRemainingTicks(); }
    public long effectZoneRemaining(long tick) { return snapshot(tick).effectZoneRemainingTicks(); }

    private static long remaining(long expiresAt, long tick) {
        return tick < 0L ? 0L : Math.max(0L, expiresAt - tick);
    }

    public record Snapshot(boolean physicalActive, boolean effectZoneActive,
                           long physicalRemainingTicks, long effectZoneRemainingTicks) {
        public boolean combined() { return physicalActive && effectZoneActive; }
        public boolean active() { return physicalActive || effectZoneActive; }
    }

    public enum ProjectileOutcome { MISS, DESTROY_CONSTRUCT_AND_CONTINUE, DESTROY_BOTH }
}

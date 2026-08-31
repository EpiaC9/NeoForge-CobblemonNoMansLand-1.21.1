package net.epiac9.cobblemonnml.client.battle.action;
import java.util.*;
public final class ActionBattleDamageHudState {
    public static final long NUMBER_LIFETIME_TICKS = 25L;
    public static final long TRAIL_DELAY_TICKS = 6L;
    public static final long TRAIL_CATCHUP_TICKS = 12L;
    private final SideState enemy = new SideState();
    private final SideState ally = new SideState();
    public void applySideSnapshot(boolean allySide, String pokemonUuid, int currentHp, int maxHp, List<DamageInput> events, long clientTick) {
        (allySide ? ally : enemy).apply(pokemonUuid, currentHp, maxHp, events, clientTick);
    }
    public RenderSnapshot enemy(long clientTick) { return enemy.snapshot(clientTick); }
    public RenderSnapshot ally(long clientTick) { return ally.snapshot(clientTick); }
    public void clear() { enemy.clear(); ally.clear(); }
    public record DamageInput(long eventId, int damage, String category) {
        public DamageInput { damage = Math.max(0, damage); category = category != null ? category : "NORMAL"; }
    }
    public record FloatingDamage(long eventId, int damage, String category, long ageTicks, int stackIndex) {}
    public record RenderSnapshot(String pokemonUuid, int currentHp, int maxHp, double trailingHp, List<FloatingDamage> floatingDamage) {}
    private static final class MutableDamage {
        private final long eventId;
        private final int damage;
        private final String category;
        private final long spawnTick;
        private final int stackIndex;
        private MutableDamage(DamageInput input, long spawnTick, int stackIndex) {
            this.eventId = input.eventId(); this.damage = input.damage(); this.category = input.category(); this.spawnTick = spawnTick; this.stackIndex = stackIndex;
        }
    }
    private static final class SideState {
        private String pokemonUuid = "";
        private int currentHp;
        private int maxHp = 1;
        private double trailStartHp;
        private long trailStartTick;
        private final List<MutableDamage> floating = new ArrayList<>();
        private final Set<Long> seenEventIds = new HashSet<>();
        private void apply(String uuid, int hp, int max, List<DamageInput> events, long tick) {
            String safeUuid = uuid != null ? uuid : "";
            int safeMax = Math.max(1, max);
            int safeHp = Math.max(0, Math.min(safeMax, hp));
            if (!safeUuid.equals(pokemonUuid)) {
                pokemonUuid = safeUuid; currentHp = safeHp; maxHp = safeMax; trailStartHp = safeHp; trailStartTick = tick; floating.clear(); seenEventIds.clear();
                addEvents(events, tick);
                return;
            }
            double currentTrail = trailingAt(tick);
            int previousHp = currentHp;
            maxHp = safeMax;
            currentHp = safeHp;
            if (safeHp < previousHp && events != null && !events.isEmpty()) {
                trailStartHp = Math.max(currentTrail, previousHp);
                trailStartTick = tick;
            } else if (safeHp > previousHp) {
                trailStartHp = safeHp;
                trailStartTick = tick;
            }
            addEvents(events, tick);
            prune(tick);
        }
        private void addEvents(List<DamageInput> events, long tick) {
            if (events == null) return;
            for (DamageInput input : events) {
                if (input == null || input.damage() <= 0 || !seenEventIds.add(input.eventId())) continue;
                int stack = floating.size() % 4;
                floating.add(new MutableDamage(input, tick, stack));
            }
        }
        private void prune(long tick) { floating.removeIf(entry -> tick - entry.spawnTick >= NUMBER_LIFETIME_TICKS); }
        private double trailingAt(long tick) {
            double start = Math.max(currentHp, Math.min(maxHp, trailStartHp));
            long age = Math.max(0L, tick - trailStartTick);
            if (start <= currentHp || age <= TRAIL_DELAY_TICKS) return start;
            double progress = Math.min(1.0D, (double) (age - TRAIL_DELAY_TICKS) / TRAIL_CATCHUP_TICKS);
            return start + (currentHp - start) * progress;
        }
        private RenderSnapshot snapshot(long tick) {
            prune(tick);
            List<FloatingDamage> entries = new ArrayList<>(floating.size());
            for (MutableDamage entry : floating) entries.add(new FloatingDamage(entry.eventId, entry.damage, entry.category, Math.max(0L, tick - entry.spawnTick), entry.stackIndex));
            return new RenderSnapshot(pokemonUuid, currentHp, maxHp, trailingAt(tick), List.copyOf(entries));
        }
        private void clear() { pokemonUuid = ""; currentHp = 0; maxHp = 1; trailStartHp = 0; trailStartTick = 0; floating.clear(); seenEventIds.clear(); }
    }
}

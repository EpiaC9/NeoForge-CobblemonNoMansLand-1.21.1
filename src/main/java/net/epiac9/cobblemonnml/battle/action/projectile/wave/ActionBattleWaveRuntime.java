package net.epiac9.cobblemonnml.battle.action.projectile.wave;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ActionBattleWaveRuntime {
    public record Point(double x, double y, double z) {
        double distanceSquared(Point other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }
    public record PokemonSample(UUID pokemonId, UUID sessionId, Point position, boolean activeActionPokemon) {}

    public static final class Instance {
        private final UUID sessionId;
        private final Point origin;
        private final long startTick;
        private final ActionBattleWaveParameters parameters;
        private final Set<UUID> hitPokemon = new HashSet<>();

        public Instance(UUID sessionId, Point origin, long startTick, ActionBattleWaveParameters parameters) {
            if (sessionId == null || origin == null || startTick < 0L || parameters == null) {
                throw new IllegalArgumentException("Wave instance requires session, origin, tick, and parameters.");
            }
            this.sessionId = sessionId;
            this.origin = origin;
            this.startTick = startTick;
            this.parameters = parameters;
        }

        public double radius(long currentTick) {
            return Math.min(parameters.maxRadius(), Math.max(0L, currentTick - startTick) * parameters.speed());
        }

        public boolean complete(long currentTick) { return radius(currentTick) >= parameters.maxRadius(); }

        public List<UUID> collectNewHits(long currentTick, List<PokemonSample> samples) {
            if (samples == null) return List.of();
            double radius = radius(currentTick);
            double radiusSquared = radius * radius;
            List<UUID> hits = new ArrayList<>();
            for (PokemonSample sample : samples) {
                if (sample == null || sample.pokemonId() == null || !sample.activeActionPokemon()
                        || !sessionId.equals(sample.sessionId()) || sample.position() == null
                        || hitPokemon.contains(sample.pokemonId())
                        || origin.distanceSquared(sample.position()) > radiusSquared) continue;
                hitPokemon.add(sample.pokemonId());
                hits.add(sample.pokemonId());
            }
            return List.copyOf(hits);
        }

        public int hitCount() { return hitPokemon.size(); }
        public UUID sessionId() { return sessionId; }
        public Point origin() { return origin; }
    }

    private ActionBattleWaveRuntime() {}
}

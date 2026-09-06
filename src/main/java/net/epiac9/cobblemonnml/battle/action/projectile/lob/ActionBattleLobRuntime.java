package net.epiac9.cobblemonnml.battle.action.projectile.lob;

import java.util.function.Predicate;

public final class ActionBattleLobRuntime {
    public enum Resolution { CONTINUE, VALID, INVALID, ALREADY_RESOLVED }
    public enum Face { DOWN, UP, NORTH, SOUTH, WEST, EAST }
    public record Position(int x, int y, int z) {
        Position relative(Face face) {
            return switch (face) {
                case DOWN -> new Position(x, y - 1, z);
                case UP -> new Position(x, y + 1, z);
                case NORTH -> new Position(x, y, z - 1);
                case SOUTH -> new Position(x, y, z + 1);
                case WEST -> new Position(x - 1, y, z);
                case EAST -> new Position(x + 1, y, z);
            };
        }
    }
    public record Outcome(Resolution resolution, Position position) {}

    private boolean resolved;

    public Resolution onEntityCollision() { return Resolution.CONTINUE; }
    public boolean resolved() { return resolved; }

    public Outcome onBlockCollision(Position hit, Face face, Predicate<Position> validator) {
        if (hit == null || face == null) return resolve(null, validator);
        return resolve(hit.relative(face), validator);
    }

    public Outcome onArrival(Position destination, Predicate<Position> validator) {
        return resolve(destination, validator);
    }

    private Outcome resolve(Position position, Predicate<Position> validator) {
        if (resolved) return new Outcome(Resolution.ALREADY_RESOLVED, null);
        resolved = true;
        if (position == null || validator == null || !validator.test(position)) {
            return new Outcome(Resolution.INVALID, null);
        }
        return new Outcome(Resolution.VALID, position);
    }
}

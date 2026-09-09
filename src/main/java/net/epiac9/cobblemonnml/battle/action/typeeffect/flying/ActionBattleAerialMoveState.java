package net.epiac9.cobblemonnml.battle.action.typeeffect.flying;

public final class ActionBattleAerialMoveState {
    private final double arrivalTolerance;
    private Point destination;
    private Point position;
    private Phase phase = Phase.IDLE;
    private boolean arrived;

    public ActionBattleAerialMoveState(double arrivalTolerance) {
        if (!Double.isFinite(arrivalTolerance) || arrivalTolerance < 0.0D) {
            throw new IllegalArgumentException("Arrival tolerance must be finite and non-negative");
        }
        this.arrivalTolerance = arrivalTolerance;
    }

    public void replace(Point destination) {
        if (!finite(destination)) throw new IllegalArgumentException("Destination must be finite");
        this.destination = destination;
        this.phase = Phase.TRAVELLING;
        this.arrived = false;
    }

    public void holdAt(Point current) {
        if (!finite(current)) throw new IllegalArgumentException("Hold position must be finite");
        this.destination = current;
        this.position = current;
        this.phase = Phase.HOVERING;
        this.arrived = false;
    }

    public Point advance(Point current, double maximumStep, SafeProbe safeProbe) {
        if (phase != Phase.TRAVELLING) return position != null ? position : current;
        if (!finite(current) || !Double.isFinite(maximumStep) || maximumStep <= 0.0D || safeProbe == null) {
            return stopAt(current);
        }
        double dx = destination.x() - current.x();
        double dy = destination.y() - current.y();
        double dz = destination.z() - current.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= arrivalTolerance) {
            position = destination;
            phase = Phase.HOVERING;
            arrived = true;
            return position;
        }
        double scale = Math.min(distance, maximumStep) / distance;
        Point candidate = new Point(current.x() + dx * scale,
                current.y() + dy * scale, current.z() + dz * scale);
        if (!safeProbe.safe(new Segment(current, candidate))) return stopAt(current);
        position = candidate;
        if (distance <= maximumStep || distance(candidate, destination) <= arrivalTolerance) {
            position = destination;
            phase = Phase.HOVERING;
            arrived = true;
        }
        return position;
    }

    public Point destination() { return destination; }
    public Point position() { return position; }
    public boolean arrived() { return arrived; }
    public boolean hovering() { return phase == Phase.HOVERING; }

    private Point stopAt(Point current) {
        if (finite(current)) position = current;
        phase = Phase.HOVERING;
        arrived = false;
        return position;
    }

    private static double distance(Point first, Point second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean finite(Point point) {
        return point != null && Double.isFinite(point.x())
                && Double.isFinite(point.y()) && Double.isFinite(point.z());
    }

    public record Point(double x, double y, double z) {}
    public record Segment(Point from, Point to) {}
    public enum Phase { IDLE, TRAVELLING, HOVERING }

    @FunctionalInterface
    public interface SafeProbe {
        boolean safe(Segment segment);
    }
}

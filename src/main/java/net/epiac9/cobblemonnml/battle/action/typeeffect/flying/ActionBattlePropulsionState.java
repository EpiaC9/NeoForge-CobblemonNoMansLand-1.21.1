package net.epiac9.cobblemonnml.battle.action.typeeffect.flying;

import java.util.function.Predicate;

public final class ActionBattlePropulsionState {
    private static final double EPSILON = 1.0E-9D;

    private final ActionBattlePropulsionRules.Plan plan;
    private final double speed;
    private int pointIndex = 1;
    private boolean hitResolved;
    private boolean finished;

    public ActionBattlePropulsionState(ActionBattlePropulsionRules.Plan plan, double speed) {
        if (plan == null || !plan.valid()) throw new IllegalArgumentException("valid plan required");
        if (!Double.isFinite(speed) || speed <= 0.0D) throw new IllegalArgumentException("positive speed required");
        this.plan = plan;
        this.speed = speed;
    }

    public Step advance(ActionBattlePropulsionRules.Point current,
                        Predicate<Segment> safe,
                        Predicate<Segment> intendedTargetContact) {
        if (finished) return new Step(current, hitResolved ? Event.COMPLETE : Event.MISS, true);
        if (current == null || safe == null || intendedTargetContact == null) {
            finished = true;
            return new Step(current, Event.BLOCKED, true);
        }
        double remaining = speed;
        ActionBattlePropulsionRules.Point position = current;
        while (remaining > EPSILON && pointIndex < plan.points().size()) {
            ActionBattlePropulsionRules.Point waypoint = plan.points().get(pointIndex);
            ActionBattlePropulsionRules.Point delta = waypoint.subtract(position);
            double distance = delta.length();
            if (distance <= EPSILON) {
                pointIndex++;
                continue;
            }
            double travel = Math.min(remaining, distance);
            ActionBattlePropulsionRules.Point candidate = position.add(delta.scale(travel / distance));
            Segment segment = new Segment(position, candidate);
            if (!safe.test(segment)) {
                finished = true;
                return new Step(position, Event.BLOCKED, true);
            }
            position = candidate;
            remaining -= travel;
            if (travel + EPSILON >= distance) pointIndex++;
            if (!hitResolved && intendedTargetContact.test(segment)) {
                hitResolved = true;
                if (plan.mode() == ActionBattlePropulsionRules.Mode.STRAIGHT) finished = true;
                return new Step(position, Event.CONTACT, finished);
            }
        }
        if (pointIndex >= plan.points().size()) {
            finished = true;
            return new Step(position, hitResolved ? Event.COMPLETE : Event.MISS, true);
        }
        return new Step(position, Event.MOVING, false);
    }

    public boolean hitResolved() {
        return hitResolved;
    }

    public boolean finished() {
        return finished;
    }

    public record Segment(ActionBattlePropulsionRules.Point from,
                          ActionBattlePropulsionRules.Point to) {}

    public record Step(ActionBattlePropulsionRules.Point position, Event event, boolean terminal) {}

    public enum Event {
        MOVING,
        CONTACT,
        COMPLETE,
        MISS,
        BLOCKED
    }
}

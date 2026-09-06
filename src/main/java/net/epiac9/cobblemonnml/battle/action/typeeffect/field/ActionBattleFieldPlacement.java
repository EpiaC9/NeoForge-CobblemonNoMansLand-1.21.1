package net.epiac9.cobblemonnml.battle.action.typeeffect.field;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class ActionBattleFieldPlacement {
    public record Offset(int x, int z) {}
    public record Position(int x, int y, int z) {}

    private static final List<Integer> VERTICAL_CORRECTIONS = List.of(0, 1, -1, -2);
    private static final List<Offset> HORIZONTAL_OFFSETS = createHorizontalOffsets();

    private ActionBattleFieldPlacement() {}

    public static List<Offset> horizontalOffsets() { return HORIZONTAL_OFFSETS; }
    public static List<Integer> verticalCorrections() { return VERTICAL_CORRECTIONS; }

    public static boolean isHorizontalOffsetEligible(int xOffset, int zOffset) {
        int squaredDistance = xOffset * xOffset + zOffset * zOffset;
        return squaredDistance > 4 && squaredDistance >= 25 && squaredDistance <= 49;
    }

    public static Position candidate(Position anchor, int xOffset, int yOffset, int zOffset) {
        if (anchor == null) throw new IllegalArgumentException("Placement anchor cannot be null.");
        return new Position(anchor.x() + xOffset, anchor.y() + yOffset, anchor.z() + zOffset);
    }

    public static List<Position> validCandidates(Position anchor, Set<Position> reserved,
                                                  Predicate<Position> validAndReachable) {
        if (anchor == null || reserved == null || validAndReachable == null) {
            throw new IllegalArgumentException("Placement search requires an anchor, reservations, and validator.");
        }
        List<Position> candidates = new ArrayList<>();
        for (Offset offset : HORIZONTAL_OFFSETS) {
            for (int correction : VERTICAL_CORRECTIONS) {
                Position candidate = candidate(anchor, offset.x(), correction, offset.z());
                if (!reserved.contains(candidate) && validAndReachable.test(candidate)) {
                    candidates.add(candidate);
                    break;
                }
            }
        }
        return List.copyOf(candidates);
    }

    private static List<Offset> createHorizontalOffsets() {
        List<Offset> offsets = new ArrayList<>();
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                if (isHorizontalOffsetEligible(x, z)) offsets.add(new Offset(x, z));
            }
        }
        return List.copyOf(offsets);
    }
}

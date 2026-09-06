package net.epiac9.cobblemonnml.battle.action.typeeffect.water.field;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldPlacement;

public final class WaterFieldPlacement {
    public record Offset(int x, int z) {}
    public record Position(int x, int y, int z) {}

    private WaterFieldPlacement() {}

    public static List<Offset> horizontalOffsets() {
        return ActionBattleFieldPlacement.horizontalOffsets().stream()
                .map(offset -> new Offset(offset.x(), offset.z())).toList();
    }
    public static List<Integer> verticalCorrections() { return ActionBattleFieldPlacement.verticalCorrections(); }

    public static boolean isHorizontalOffsetEligible(int xOffset, int zOffset) {
        return ActionBattleFieldPlacement.isHorizontalOffsetEligible(xOffset, zOffset);
    }

    public static Position candidate(Position anchor, int xOffset, int yOffset, int zOffset) {
        if (anchor == null) throw new IllegalArgumentException("Placement anchor cannot be null.");
        return new Position(anchor.x() + xOffset, anchor.y() + yOffset, anchor.z() + zOffset);
    }

    public static Position chooseAnchor(Position caster, Position target, boolean chooseCaster) {
        if (caster == null || target == null) throw new IllegalArgumentException("Placement anchors cannot be null.");
        return chooseCaster ? caster : target;
    }

    public static List<Position> validCandidates(Position anchor, Predicate<Position> validator) {
        if (anchor == null || validator == null) throw new IllegalArgumentException("Placement search requires an anchor and validator.");
        var sharedAnchor = new ActionBattleFieldPlacement.Position(anchor.x(), anchor.y(), anchor.z());
        return ActionBattleFieldPlacement.validCandidates(sharedAnchor, Set.of(), shared ->
                        validator.test(new Position(shared.x(), shared.y(), shared.z()))).stream()
                .map(shared -> new Position(shared.x(), shared.y(), shared.z())).toList();
    }

    public static <T> T choose(List<T> candidates, int index) {
        if (candidates == null || candidates.isEmpty() || index < 0 || index >= candidates.size()) {
            throw new IllegalArgumentException("Candidate index is outside the available placement list.");
        }
        return candidates.get(index);
    }

}

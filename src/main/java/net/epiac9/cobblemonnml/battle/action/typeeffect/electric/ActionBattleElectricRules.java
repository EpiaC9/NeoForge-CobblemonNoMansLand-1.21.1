package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ActionBattleElectricRules {
    public static final int CHAIN_HALF_WIDTH = 2;
    public static final int DAMAGE_HALF_WIDTH = 1;
    public static final int AI_PREFERENCE_THRESHOLD = 3;
    public static final int HOVER_BLOCKS = 1;

    private ActionBattleElectricRules() {}

    public static boolean insideChainArea(GridPosition center, GridPosition candidate) {
        return insideHorizontalSquare(center, candidate, CHAIN_HALF_WIDTH);
    }

    public static boolean insideDamageArea(GridPosition center, GridPosition candidate) {
        return insideHorizontalSquare(center, candidate, DAMAGE_HALF_WIDTH);
    }

    public static boolean prefersPlasmaTarget(int activeBalls, boolean validInteraction,
                                               boolean playerIssuedTarget) {
        return !playerIssuedTarget && validInteraction && activeBalls > AI_PREFERENCE_THRESHOLD;
    }

    public static boolean canChain(boolean electricIdentity, boolean damagingProjectile,
                                   boolean mechanicSecondary) {
        return electricIdentity && damagingProjectile && !mechanicSecondary;
    }

    public static List<GridPosition> roamingCandidates(GridPosition center,
                                                       Predicate<GridPosition> safe) {
        if (center == null || safe == null) return List.of();
        List<GridPosition> candidates = new ArrayList<>(8);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                GridPosition candidate = new GridPosition(center.x() + x, center.y(), center.z() + z);
                if (safe.test(candidate)) candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
    }

    private static boolean insideHorizontalSquare(GridPosition center, GridPosition candidate,
                                                  int halfWidth) {
        return center != null && candidate != null
                && Math.abs(center.x() - candidate.x()) <= halfWidth
                && Math.abs(center.z() - candidate.z()) <= halfWidth;
    }

    public record GridPosition(int x, int y, int z) {}
}

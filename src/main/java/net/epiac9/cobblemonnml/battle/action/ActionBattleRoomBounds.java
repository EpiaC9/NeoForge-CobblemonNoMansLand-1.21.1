package net.epiac9.cobblemonnml.battle.action;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record ActionBattleRoomBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public ActionBattleRoomBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Invalid room bounds");
    }

    public boolean contains(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && x >= minX && x < maxX + 1.0D
                && y >= minY && y < maxY + 1.0D
                && z >= minZ && z < maxZ + 1.0D;
    }

    public boolean contains(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z)
                && x >= minX && x < maxX + 1.0D
                && z >= minZ && z < maxZ + 1.0D;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public static Optional<ActionBattleRoomBounds> smallestContaining(List<ActionBattleRoomBounds> bounds,
                                                                       int x, int y, int z) {
        if (bounds == null) return Optional.empty();
        return bounds.stream().filter(candidate -> candidate != null && candidate.contains(x + 0.5D, y + 0.5D, z + 0.5D))
                .min(Comparator.comparingLong(ActionBattleRoomBounds::volume));
    }
}

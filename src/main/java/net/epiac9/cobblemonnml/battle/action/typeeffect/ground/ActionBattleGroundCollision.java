package net.epiac9.cobblemonnml.battle.action.typeeffect.ground;

public final class ActionBattleGroundCollision {
    private ActionBattleGroundCollision() {}

    public static Box combatBox(Box original, int depthPercent) {
        if (original == null) throw new IllegalArgumentException("original box is required");
        double scale = ActionBattleGroundRules.collisionScale(depthPercent);
        double centerX = (original.minX() + original.maxX()) * 0.5D;
        double centerZ = (original.minZ() + original.maxZ()) * 0.5D;
        double halfWidth = original.width() * scale * 0.5D;
        double halfDepth = original.depth() * scale * 0.5D;
        return new Box(centerX - halfWidth, original.minY(), centerZ - halfDepth,
                centerX + halfWidth, original.minY() + original.height() * scale, centerZ + halfDepth);
    }

    public static Box buriedAwareBox(Box original, int depthPercent) {
        Box exposed = combatBox(original, depthPercent);
        return new Box(exposed.minX(), exposed.minY() - original.height() * depthPercent / 100.0D,
                exposed.minZ(), exposed.maxX(), exposed.maxY(), exposed.maxZ());
    }

    public static boolean segmentIntersects(Box box, Point start, Point end) {
        if (box == null || start == null || end == null) return false;
        if (box.contains(start) || box.contains(end)) return true;
        double[] range = {0.0D, 1.0D};
        return clipsAxis(start.x(), end.x() - start.x(), box.minX(), box.maxX(), range)
                && clipsAxis(start.y(), end.y() - start.y(), box.minY(), box.maxY(), range)
                && clipsAxis(start.z(), end.z() - start.z(), box.minZ(), box.maxZ(), range);
    }

    public static boolean contactIntersects(Box first, Box second) {
        return first != null && second != null
                && first.maxX() > second.minX() && first.minX() < second.maxX()
                && first.maxY() > second.minY() && first.minY() < second.maxY()
                && first.maxZ() > second.minZ() && first.minZ() < second.maxZ();
    }

    private static boolean clipsAxis(double start, double delta, double min, double max, double[] range) {
        if (Math.abs(delta) < 0.0000001D) return start >= min && start <= max;
        double first = (min - start) / delta;
        double second = (max - start) / delta;
        if (first > second) { double swap = first; first = second; second = swap; }
        range[0] = Math.max(range[0], first);
        range[1] = Math.min(range[1], second);
        return range[0] <= range[1];
    }

    public record Point(double x, double y, double z) {}

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box {
            if (maxX < minX || maxY < minY || maxZ < minZ) throw new IllegalArgumentException("invalid box");
        }
        public double width() { return maxX - minX; }
        public double height() { return maxY - minY; }
        public double depth() { return maxZ - minZ; }
        public boolean contains(Point point) {
            return point.x() >= minX && point.x() <= maxX
                    && point.y() >= minY && point.y() <= maxY
                    && point.z() >= minZ && point.z() <= maxZ;
        }
    }
}

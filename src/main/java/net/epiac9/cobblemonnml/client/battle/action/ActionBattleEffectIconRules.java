package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleEffectIconRules {
    private static final int STANDARD_SIZE = 18;

    private ActionBattleEffectIconRules() {}

    public static int standardSize() { return STANDARD_SIZE; }

    public static int timerThickness(int size) { return size >= STANDARD_SIZE ? 2 : 1; }

    public static int perimeterLength(int size) {
        if (size < 2) return 0;
        return size < 6 ? size * 4 - 4 : size * 4 - 12;
    }

    public static int visiblePerimeterPixels(int size, float progress) {
        return Math.clamp(Math.round(perimeterLength(size) * Math.clamp(progress, 0.0F, 1.0F)),
                0, perimeterLength(size));
    }

    public static Point perimeterPoint(int size, int rawIndex) {
        if (size < 2) return new Point(0, 0);
        int index = Math.floorMod(rawIndex, perimeterLength(size));
        if (size >= 6) {
            int segment = size - 4;
            if (index < segment) return new Point(index + 2, 0);
            index -= segment;
            if (index == 0) return new Point(size - 2, 1);
            index--;
            if (index < segment) return new Point(size - 1, index + 2);
            index -= segment;
            if (index == 0) return new Point(size - 2, size - 2);
            index--;
            if (index < segment) return new Point(size - 3 - index, size - 1);
            index -= segment;
            if (index == 0) return new Point(1, size - 2);
            index--;
            if (index < segment) return new Point(0, size - 3 - index);
            return new Point(1, 1);
        }
        if (index < size) return new Point(index, 0);
        index -= size;
        if (index < size - 1) return new Point(size - 1, index + 1);
        index -= size - 1;
        if (index < size - 1) return new Point(size - 2 - index, size - 1);
        index -= size - 1;
        return new Point(0, size - 2 - index);
    }

    public static boolean frameContains(int size, int x, int y) {
        if (size <= 0 || x < 0 || y < 0 || x >= size || y >= size) return false;
        if (size < 6) return true;
        int farX = size - 1 - x;
        int farY = size - 1 - y;
        return x + y >= 2 && farX + y >= 2 && x + farY >= 2 && farX + farY >= 2;
    }

    public static ActionBattleHudLayout.Rect innerBounds(int size) {
        int inset = size >= 16 ? 3 : 2;
        return new ActionBattleHudLayout.Rect(inset, inset,
                Math.max(0, size - inset * 2), Math.max(0, size - inset * 2));
    }

    public record Point(int x, int y) {}
}

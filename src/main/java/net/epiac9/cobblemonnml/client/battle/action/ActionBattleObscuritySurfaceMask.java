package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleObscuritySurfaceMask {
    public enum Shape {
        PANEL,
        PANEL_FLIPPED,
        MOVE,
        PARTY,
        PARTY_ACTIVE
    }

    public record Span(int left, int rightExclusive) {}

    private static final int[] PANEL_LEFT = {
            12, 11, 10, 9, 5, 4, 3, 2, 1, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 1
    };
    private static final int[] PANEL_RIGHT = {
            139, 140, 140, 140, 139, 139, 139, 139, 138, 138,
            138, 138, 137, 137, 137, 137, 136, 136, 136, 136,
            138, 139, 139, 139, 138, 138, 138, 137, 38, 38,
            38, 38, 38, 38, 38, 37, 36, 35, 34, 33
    };
    private static final int[] MOVE_LEFT = {
            1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
    };
    private static final int[] MOVE_RIGHT = {
            87, 88, 89, 90, 90, 90, 90, 90, 90, 90, 90, 90,
            90, 91, 92, 92, 92, 92, 92, 92, 92, 92, 91, 90
    };
    private static final int[] PARTY_LEFT = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    private static final int[] PARTY_RIGHT = {
            43, 44, 45, 46, 51, 51, 51, 51, 51, 51,
            51, 51, 51, 51, 51, 51, 51, 51, 51, 52,
            53, 54, 55, 56, 56, 56, 55, 54, 53, 52
    };
    private static final int[] PARTY_ACTIVE_LEFT = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 1, 2, 3
    };
    private static final int[] PARTY_ACTIVE_RIGHT = {
            49, 50, 51, 52, 57, 57, 57, 57, 57, 57,
            57, 57, 57, 57, 57, 57, 57, 57, 57, 58,
            59, 60, 61, 62, 62, 62, 61, 60, 59, 58
    };

    private ActionBattleObscuritySurfaceMask() {}

    public static Span span(Shape shape, int width, int height, int row) {
        if (shape == null || width <= 0 || height <= 0 || row < 0 || row >= height) {
            return new Span(0, 0);
        }
        int[] lefts = lefts(shape);
        int[] rights = rights(shape);
        int sourceWidth = sourceWidth(shape);
        int sourceHeight = lefts.length;
        int sourceRow = Math.min(sourceHeight - 1, row * sourceHeight / height);
        int left = lefts[sourceRow];
        int right = rights[sourceRow];
        if (shape == Shape.PANEL_FLIPPED) {
            int mirroredLeft = sourceWidth - right;
            right = sourceWidth - left;
            left = mirroredLeft;
        }
        return new Span(scaleCeil(left, width, sourceWidth),
                scaleCeil(right, width, sourceWidth));
    }

    private static int[] lefts(Shape shape) {
        return switch (shape) {
            case PANEL, PANEL_FLIPPED -> PANEL_LEFT;
            case MOVE -> MOVE_LEFT;
            case PARTY -> PARTY_LEFT;
            case PARTY_ACTIVE -> PARTY_ACTIVE_LEFT;
        };
    }

    private static int[] rights(Shape shape) {
        return switch (shape) {
            case PANEL, PANEL_FLIPPED -> PANEL_RIGHT;
            case MOVE -> MOVE_RIGHT;
            case PARTY -> PARTY_RIGHT;
            case PARTY_ACTIVE -> PARTY_ACTIVE_RIGHT;
        };
    }

    private static int sourceWidth(Shape shape) {
        return switch (shape) {
            case PANEL, PANEL_FLIPPED -> 140;
            case MOVE -> 92;
            case PARTY, PARTY_ACTIVE -> 62;
        };
    }

    private static int scaleCeil(int value, int destination, int source) {
        return (value * destination + source - 1) / source;
    }
}

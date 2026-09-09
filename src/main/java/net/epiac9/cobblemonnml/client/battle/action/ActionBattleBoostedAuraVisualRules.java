package net.epiac9.cobblemonnml.client.battle.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ActionBattleBoostedAuraVisualRules {
    public static final int ICON_SIZE = 18;
    public static final float ICON_CENTER = ICON_SIZE / 2.0F;
    private static final long PULSE_DURATION_MILLIS = 1_000L;
    private static final List<Span> OUTER_SPANS = discSpans(13, 64, 180);
    private static final List<Span> INNER_SPANS = discSpans(11, 176, 255);

    private ActionBattleBoostedAuraVisualRules() {}

    public static List<Span> outerSpans() {
        return OUTER_SPANS;
    }

    public static List<Span> innerSpans() {
        return INNER_SPANS;
    }

    public static AnimationFrame animationFrame(long animationMillis) {
        double phase = Math.floorMod(animationMillis, PULSE_DURATION_MILLIS)
                / (double) PULSE_DURATION_MILLIS;
        double pulse = 0.5D - 0.5D * Math.cos(phase * Math.PI * 2.0D);
        return new AnimationFrame(
                interpolate(220, 255, pulse),
                interpolate(110, 200, pulse),
                (float) (1.0D + 0.12D * pulse)
        );
    }

    public static int scaledAlpha(int spanAlpha, int layerAlpha) {
        return Math.clamp(Math.round(Math.clamp(spanAlpha, 0, 255)
                * Math.clamp(layerAlpha, 0, 255) / 255.0F), 0, 255);
    }

    public static int color(String type, int alpha, int obscurityStage) {
        int argb = Math.clamp(alpha, 0, 255) << 24 | rgb(type);
        return ActionBattleObscurityHudRules.presentationColor(argb, obscurityStage);
    }

    public static int rgb(String type) {
        return switch (normalize(type)) {
            case "fire" -> 0xE66A39; case "water" -> 0x4F86E8; case "grass" -> 0x55A94F;
            case "electric" -> 0xE4C13A; case "ice" -> 0x58BFC8; case "fighting" -> 0xB4473D;
            case "poison" -> 0xA257A9; case "ground" -> 0xC99C55; case "flying" -> 0x8098DF;
            case "psychic" -> 0xE45C93; case "bug" -> 0x9CAD3A; case "rock" -> 0xB09A58;
            case "ghost" -> 0x6E5A9D; case "dragon" -> 0x6652C9; case "dark" -> 0x62554F;
            case "steel" -> 0x8795A5; case "fairy" -> 0xD889C3; default -> 0x8D8D8D;
        };
    }

    private static List<Span> discSpans(int radius, int edgeAlpha, int centerAlpha) {
        List<Span> spans = new ArrayList<>(radius * 2 + 1);
        int center = ICON_SIZE / 2;
        for (int offsetY = -radius; offsetY <= radius; offsetY++) {
            int halfWidth = (int) Math.floor(Math.sqrt(radius * radius - offsetY * offsetY));
            double centerWeight = 1.0D - Math.abs(offsetY) / (double) radius;
            spans.add(new Span(center + offsetY, center - halfWidth, center + halfWidth + 1,
                    interpolate(edgeAlpha, centerAlpha, centerWeight)));
        }
        return List.copyOf(spans);
    }

    private static int interpolate(int low, int high, double amount) {
        return Math.clamp((int) Math.round(low + (high - low) * amount), 0, 255);
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    public record AnimationFrame(int innerAlpha, int outerAlpha, float outerScale) {
        public AnimationFrame {
            innerAlpha = Math.clamp(innerAlpha, 0, 255);
            outerAlpha = Math.clamp(outerAlpha, 0, 255);
            outerScale = Math.max(1.0F, outerScale);
        }
    }

    public record Span(int y, int startX, int endX, int alpha) {
        public Span {
            if (endX <= startX) throw new IllegalArgumentException("Aura span must have positive width.");
            alpha = Math.clamp(alpha, 0, 255);
        }

        public int width() {
            return endX - startX;
        }
    }
}

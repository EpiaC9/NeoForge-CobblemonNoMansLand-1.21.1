package net.epiac9.cobblemonnml.client.battle.action;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class ActionBattleObscurityHudRenderer {
    private ActionBattleObscurityHudRenderer() {}

    public static void renderSurface(GuiGraphics graphics, ActionBattleHudLayout.Rect rect,
                                     int rawStage, int seed) {
        if (graphics == null || rect == null) return;
        int stage = ActionBattleObscurityHudRules.clampStage(rawStage);
        if (rect.width() <= 0 || rect.height() <= 0) return;
        boolean smoke = ActionBattleObscurityHudRules.drawsSmoke(stage);
        if (!smoke) return;
        graphics.enableScissor(rect.x(), rect.y(), right(rect), bottom(rect));
        renderSmokeCover(graphics, rect, seed);
        graphics.disableScissor();
    }

    public static void renderMaskedSurface(GuiGraphics graphics, ActionBattleHudLayout.Rect rect,
                                           int rawStage, int seed,
                                           ActionBattleObscuritySurfaceMask.Shape shape) {
        if (graphics == null || rect == null || shape == null
                || rect.width() <= 0 || rect.height() <= 0) return;
        int stage = ActionBattleObscurityHudRules.clampStage(rawStage);
        boolean shade = ActionBattleObscurityHudRules.grayscale(stage);
        boolean smoke = ActionBattleObscurityHudRules.drawsSmoke(stage);
        if (!shade && !smoke) return;
        graphics.enableScissor(rect.x(), rect.y(), right(rect), bottom(rect));
        if (shade) renderSilhouetteShade(graphics, rect, shape);
        if (smoke) renderSmokeCover(graphics, rect, seed);
        graphics.disableScissor();
    }

    private static void renderSilhouetteShade(GuiGraphics graphics,
                                               ActionBattleHudLayout.Rect rect,
                                               ActionBattleObscuritySurfaceMask.Shape shape) {
        for (int row = 0; row < rect.height(); row++) {
            ActionBattleObscuritySurfaceMask.Span span =
                    ActionBattleObscuritySurfaceMask.span(shape, rect.width(), rect.height(), row);
            if (span.rightExclusive() <= span.left()) continue;
            graphics.fill(rect.x() + span.left(), rect.y() + row,
                    rect.x() + span.rightExclusive(), rect.y() + row + 1, 0x684F4F4F);
        }
    }

    private static void renderSmokeCover(GuiGraphics graphics, ActionBattleHudLayout.Rect rect, int seed) {
        long tick = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        int baseRadius = Math.max(2, Math.min(9, Math.max(rect.height() / 3, 2)));
        int clouds = Math.max(5, (int) Math.ceil((double) rect.width() / Math.max(2, baseRadius)));
        for (int cloud = 0; cloud < clouds; cloud++) {
            long value = tick / 5L + seed * 37L + cloud * 19L;
            int distributedX = ((cloud * 2 + 1) * rect.width()) / (clouds * 2);
            int jitter = Math.floorMod((int) value, Math.max(1, baseRadius)) - baseRadius / 2;
            int centerX = rect.x() + Math.clamp(distributedX + jitter, 0, Math.max(0, rect.width() - 1));
            int centerY = rect.y() + Math.floorMod((int) (value * 7L), Math.max(1, rect.height()));
            int radius = Math.max(2, baseRadius - Math.floorMod((int) value, 3));
            fillCloud(graphics, centerX, centerY, radius,
                    cloud % 3 == 0 ? 0xF0080808 : 0xE0181818);
        }
    }

    private static void fillCloud(GuiGraphics graphics, int centerX, int centerY,
                                  int radius, int color) {
        int radiusSquared = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = (int) Math.floor(Math.sqrt(Math.max(0, radiusSquared - dy * dy)));
            graphics.fill(centerX - halfWidth, centerY + dy,
                    centerX + halfWidth + 1, centerY + dy + 1, color);
        }
    }

    private static int right(ActionBattleHudLayout.Rect rect) { return rect.x() + rect.width(); }
    private static int bottom(ActionBattleHudLayout.Rect rect) { return rect.y() + rect.height(); }
}

package net.epiac9.cobblemonnml.client.battle.action;

import com.mojang.blaze3d.systems.RenderSystem;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.minecraft.client.gui.GuiGraphics;

public final class ActionBattleEffectIconRenderer {
    private static final int FRAME = 0xF03B3D40;
    private static final int FRAME_HIGHLIGHT = 0xFF8A8D91;
    private static final int FRAME_SHADOW = 0xFF202226;
    private static final int BORDER = 0xFF565A5F;

    private ActionBattleEffectIconRenderer() {}

    public static void render(GuiGraphics graphics, int x, int y, int size,
                              ActionBattleHudPayload.StatusState state,
                              ActionBattleStatusVisualRegistry.StatusVisual visual,
                              boolean grayscale) {
        if (graphics == null || state == null || visual == null || size < 6) return;
        renderFrame(graphics, x, y, size);
        ActionBattleHudLayout.Rect inner = ActionBattleEffectIconRules.innerBounds(size);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (grayscale) RenderSystem.setShaderColor(0.58F, 0.58F, 0.58F, 1.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(x + inner.x(), y + inner.y(), 0.0F);
        graphics.pose().scale(inner.width() / 16.0F, inner.height() / 16.0F, 1.0F);
        graphics.blit(visual.icon(), 0, 0, 0.0F, 0.0F, 16, 16, 16, 16);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        float progress;
        if ("RAMPAGE".equals(state.statusId())) {
            progress = Math.clamp((int) state.remainingTicks(), 0, 3) / 3.0F;
        } else if (ActionBattleStatusHudRules.hasCountdown(state.statusId())) {
            progress = new ActionBattleStatusHudEntry(state, visual).progress();
        } else {
            progress = 1.0F;
        }
        renderTimer(graphics, x, y, size, progress, visual.ringArgb());
        renderPoisonBoundaries(graphics, x, y, size, state.statusId());
        RenderSystem.disableBlend();
    }

    private static void renderFrame(GuiGraphics graphics, int x, int y, int size) {
        for (int localY = 0; localY < size; localY++) {
            for (int localX = 0; localX < size; localX++) {
                if (!ActionBattleEffectIconRules.frameContains(size, localX, localY)) continue;
                int color = FRAME;
                if (localY <= 1 || localX <= 1) color = FRAME_HIGHLIGHT;
                if (localY >= size - 2 || localX >= size - 2) color = FRAME_SHADOW;
                graphics.fill(x + localX, y + localY, x + localX + 1, y + localY + 1, color);
            }
        }
    }

    private static void renderPerimeter(GuiGraphics graphics, int x, int y, int size,
                                        int visible, int color) {
        int length = ActionBattleEffectIconRules.perimeterLength(size);
        for (int index = 0; index < Math.min(length, visible); index++) {
            ActionBattleEffectIconRules.Point point = ActionBattleEffectIconRules.perimeterPoint(size, index);
            graphics.fill(x + point.x(), y + point.y(), x + point.x() + 1, y + point.y() + 1, color);
        }
    }

    private static void renderTimer(GuiGraphics graphics, int x, int y, int size,
                                    float progress, int color) {
        int thickness = ActionBattleEffectIconRules.timerThickness(size);
        for (int layer = 0; layer < thickness; layer++) {
            int layerSize = size - layer * 2;
            if (layerSize < 2) break;
            int layerX = x + layer;
            int layerY = y + layer;
            renderPerimeter(graphics, layerX, layerY, layerSize,
                    ActionBattleEffectIconRules.perimeterLength(layerSize), BORDER);
            renderPerimeter(graphics, layerX, layerY, layerSize,
                    ActionBattleEffectIconRules.visiblePerimeterPixels(layerSize, progress), color);
        }
    }

    private static void renderPoisonBoundaries(GuiGraphics graphics, int x, int y, int size,
                                               String statusId) {
        if (statusId == null || !statusId.startsWith("TYPE_POISON")) return;
        int thickness = ActionBattleEffectIconRules.timerThickness(size);
        for (int layer = 0; layer < thickness; layer++) {
            int layerSize = size - layer * 2;
            int length = ActionBattleEffectIconRules.perimeterLength(layerSize);
            for (int index : new int[]{Math.round(length / 3.0F), Math.round(length * 2.0F / 3.0F)}) {
                ActionBattleEffectIconRules.Point point =
                        ActionBattleEffectIconRules.perimeterPoint(layerSize, index);
                graphics.fill(x + layer + point.x(), y + layer + point.y(),
                        x + layer + point.x() + 1, y + layer + point.y() + 1, 0xFFD8D8D8);
            }
        }
    }
}

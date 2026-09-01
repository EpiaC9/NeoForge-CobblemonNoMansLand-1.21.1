package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class ActionBattleStatStageHudRenderer {
    private static final String[] LABELS = {"ATK", "DEF", "SpA", "SpD", "SPE", "ACC"};
    private static final int SLOT_WIDTH = 20;
    private static final int SLOT_HEIGHT = 18;
    private static final int SLOT_GAP = 3;
    private static final int PANEL_GAP = 2;
    private static final int BACKGROUND = 0xD0181818;
    private static final int BORDER = 0xD0909090;
    private static final int LABEL = 0xFFBEBEBE;
    private static final int NEUTRAL = 0xFFE0E0E0;
    private static final int POSITIVE = 0xFF59D66F;
    private static final int NEGATIVE = 0xFFE35A5A;

    private ActionBattleStatStageHudRenderer() {}

    public static void render(GuiGraphics graphics, Font font, ActionBattleHudLayout.Rect panel, ActionBattleHudPayload.StatStageState stages, boolean ally) {
        ActionBattleHudPayload.StatStageState safe = stages != null ? stages : ActionBattleHudPayload.StatStageState.neutral();
        int totalWidth = LABELS.length * SLOT_WIDTH + (LABELS.length - 1) * SLOT_GAP;
        int startX = ally ? panel.x() + panel.width() - totalWidth : panel.x();
        int y = panel.y() + panel.height() + PANEL_GAP;
        for (int i = 0; i < LABELS.length; i++) {
            int x = startX + i * (SLOT_WIDTH + SLOT_GAP);
            renderOval(graphics, x, y, SLOT_WIDTH, SLOT_HEIGHT);
            drawScaledCentered(graphics, font, LABELS[i], x + SLOT_WIDTH / 2, y + 2, 0.42F, LABEL);
            renderStage(graphics, font, x, y, safe.stage(i));
        }
    }

    public static int rowBottom(ActionBattleHudLayout.Rect panel) { return panel.y() + panel.height() + PANEL_GAP + SLOT_HEIGHT; }

    private static void renderOval(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 4, y, x + width - 4, y + height, BORDER);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, BORDER);
        graphics.fill(x + 1, y + 5, x + width - 1, y + height - 5, BORDER);
        graphics.fill(x + 4, y + 1, x + width - 4, y + height - 1, BACKGROUND);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, BACKGROUND);
        graphics.fill(x + 2, y + 6, x + width - 2, y + height - 6, BACKGROUND);
    }

    private static void renderStage(GuiGraphics graphics, Font font, int x, int y, int stage) {
        ActionBattleStatStageDisplay display = ActionBattleStatStageDisplay.fromStage(stage);
        int centerX = x + SLOT_WIDTH / 2;
        if (display == ActionBattleStatStageDisplay.NEUTRAL) {
            drawScaledCentered(graphics, font, display.glyph(), centerX, y + 9, 0.54F, NEUTRAL);
            return;
        }
        int color = display.direction() > 0 ? POSITIVE : NEGATIVE;
        if (display.arrowCount() == 1) {
            drawArrow(graphics, centerX, y + 12, display.direction(), color);
            return;
        }
        if (display.direction() > 0) {
            drawArrow(graphics, centerX, y + 13, 1, color);
            drawArrow(graphics, centerX, y + 9, 1, color);
        } else {
            drawArrow(graphics, centerX, y + 10, -1, color);
            drawArrow(graphics, centerX, y + 14, -1, color);
        }
    }

    private static void drawArrow(GuiGraphics graphics, int centerX, int centerY, int direction, int color) {
        if (direction > 0) {
            graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 3, color);
            graphics.fill(centerX - 1, centerY - 2, centerX + 2, centerY - 1, color);
            graphics.fill(centerX - 2, centerY - 1, centerX + 3, centerY, color);
        } else {
            graphics.fill(centerX, centerY - 2, centerX + 1, centerY + 4, color);
            graphics.fill(centerX - 2, centerY + 1, centerX + 3, centerY + 2, color);
            graphics.fill(centerX - 1, centerY + 2, centerX + 2, centerY + 3, color);
        }
    }

    private static void drawScaledCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }
}

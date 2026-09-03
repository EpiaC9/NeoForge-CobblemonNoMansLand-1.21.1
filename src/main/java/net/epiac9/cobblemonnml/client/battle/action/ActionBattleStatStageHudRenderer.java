package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class ActionBattleStatStageHudRenderer {
    private static final int STAT_COUNT = 6;
    private static final int HP_BAR_WIDTH = 97;
    private static final int SLOT_WIDTH = 15;
    private static final int SLOT_HEIGHT = 11;
    private static final int SLOT_GAP = 1;
    private static final int ROW_OFFSET_Y = 28;
    private static final int BACKGROUND = 0xD0181818;
    private static final int BORDER = 0xD0909090;
    private static final int NEUTRAL = 0xFFE0E0E0;
    private static final int POSITIVE = 0xFF59D66F;
    private static final int NEGATIVE = 0xFFE35A5A;

    private ActionBattleStatStageHudRenderer() {}

    public static void render(GuiGraphics graphics, Font font, ActionBattleHudLayout.Rect panel, ActionBattleHudPayload.StatStageState stages, boolean rightAligned) {
        ActionBattleHudPayload.StatStageState safe = stages != null ? stages : ActionBattleHudPayload.StatStageState.neutral();
        int totalWidth = STAT_COUNT * SLOT_WIDTH + (STAT_COUNT - 1) * SLOT_GAP;
        int hpLeft = rightAligned ? panel.x() + 5 : panel.x() + 38;
        int startX = hpLeft + (HP_BAR_WIDTH - totalWidth) / 2;
        int y = panel.y() + ROW_OFFSET_Y;
        for (int i = 0; i < STAT_COUNT; i++) {
            int x = startX + i * (SLOT_WIDTH + SLOT_GAP);
            renderOval(graphics, x, y, SLOT_WIDTH, SLOT_HEIGHT);
            renderStage(graphics, font, x, y, safe.stage(i));
        }
    }

    public static int rowBottom(ActionBattleHudLayout.Rect panel) { return panel.y() + ROW_OFFSET_Y + SLOT_HEIGHT; }

    private static void renderOval(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 3, y, x + width - 3, y + height, BORDER);
        graphics.fill(x + 1, y + 2, x + width - 1, y + height - 2, BORDER);
        graphics.fill(x + 3, y + 1, x + width - 3, y + height - 1, BACKGROUND);
        graphics.fill(x + 2, y + 3, x + width - 2, y + height - 3, BACKGROUND);
    }

    private static void renderStage(GuiGraphics graphics, Font font, int x, int y, int stage) {
        String text = stage == 0 ? "--" : stage > 0 ? "+" + stage : Integer.toString(stage);
        int color = stage == 0 ? NEUTRAL : stage > 0 ? POSITIVE : NEGATIVE;
        drawScaledCentered(graphics, font, text, x + SLOT_WIDTH / 2, y + 2, 0.72F, color);
    }

    private static void drawScaledCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }
}

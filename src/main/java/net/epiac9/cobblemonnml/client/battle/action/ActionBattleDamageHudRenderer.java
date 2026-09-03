package net.epiac9.cobblemonnml.client.battle.action;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
public final class ActionBattleDamageHudRenderer {
    private static final int LOST_HP = 0xFFD9534F;
    private static final int NORMAL_DAMAGE_RGB = 0xFF5A5A;
    private static final int DOT_DAMAGE_RGB = 0xFFA040;
    private static final int HP_BAR_WIDTH = 97;
    private ActionBattleDamageHudRenderer() {}
    public static void renderTrailingHp(GuiGraphics graphics, ActionBattleHudLayout.Rect rect, boolean flipped, int currentHp, int maxHp, double trailingHp) {
        int safeMax = Math.max(1, maxHp);
        double currentRatio = Math.clamp((double) Math.max(0, currentHp) / safeMax, 0.0D, 1.0D);
        double trailingRatio = Math.clamp(trailingHp / safeMax, 0.0D, 1.0D);
        if (trailingRatio <= currentRatio) return;
        int currentWidth = (int) Math.round(HP_BAR_WIDTH * currentRatio);
        int trailingWidth = (int) Math.round(HP_BAR_WIDTH * trailingRatio);
        int infoX = flipped ? rect.x() + 7 : rect.x() + 40;
        int baseX = infoX - 2;
        if (!flipped) {
            graphics.fill(baseX + currentWidth, rect.y() + 22, baseX + trailingWidth, rect.y() + 26, LOST_HP);
        } else {
            int trailingLeft = baseX + (HP_BAR_WIDTH - trailingWidth);
            int currentLeft = baseX + (HP_BAR_WIDTH - currentWidth);
            graphics.fill(trailingLeft, rect.y() + 22, currentLeft, rect.y() + 26, LOST_HP);
        }
    }
    public static void renderFloating(GuiGraphics graphics, Font font, ActionBattleHudLayout.Rect rect, boolean ally, ActionBattleDamageHudState.RenderSnapshot snapshot) {
        if (snapshot == null || snapshot.floatingDamage().isEmpty()) return;
        for (ActionBattleDamageHudState.FloatingDamage event : snapshot.floatingDamage()) {
            double progress = Math.clamp((double) event.ageTicks() / ActionBattleDamageHudState.NUMBER_LIFETIME_TICKS, 0.0D, 1.0D);
            int outward = (int) Math.round(10.0D * progress);
            int upward = (int) Math.round(8.0D * progress);
            int y = rect.y() + 18 - upward + event.stackIndex() * 7;
            int alpha = progress <= 0.55D ? 255 : (int) Math.round(255.0D * (1.0D - (progress - 0.55D) / 0.45D));
            alpha = Math.max(0, Math.min(255, alpha));
            int rgb = "DOT".equalsIgnoreCase(event.category()) ? DOT_DAMAGE_RGB : NORMAL_DAMAGE_RGB;
            int color = (alpha << 24) | rgb;
            String text = "-" + event.damage();
            int anchorX = ally ? rect.x() - 4 - outward : rect.x() + rect.width() + 4 + outward;
            drawScaled(graphics, font, text, anchorX, y, 0.70F, color, ally);
        }
    }
    private static void drawScaled(GuiGraphics graphics, Font font, String text, int anchorX, int y, float scale, int color, boolean rightAligned) {
        graphics.pose().pushPose();
        graphics.pose().translate(anchorX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, rightAligned ? -font.width(text) : 0, 0, color, true);
        graphics.pose().popPose();
    }
}

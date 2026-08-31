package net.epiac9.cobblemonnml.client.battle.action;

import com.mojang.blaze3d.systems.RenderSystem;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public final class ActionBattleStatusHudRenderer {
    private static final int ICON_SIZE = 16;
    private static final int SLOT_SIZE = 24;
    private static final int PANEL_GAP = 2;
    private static final int RING_SEGMENTS = 32;
    private static final int RING_RADIUS = 10;

    private ActionBattleStatusHudRenderer() {}

    public static void renderEnemy(GuiGraphics graphics, ActionBattleHudLayout.Rect panel, List<ActionBattleHudPayload.StatusState> statuses) {
        render(graphics, panel, statuses, false);
    }

    public static void renderAlly(GuiGraphics graphics, ActionBattleHudLayout.Rect panel, List<ActionBattleHudPayload.StatusState> statuses) {
        render(graphics, panel, statuses, true);
    }

    static int statusX(ActionBattleHudLayout.Rect panel, int index, boolean ally) {
        return ally ? panel.x() + panel.width() - 6 - ICON_SIZE - index * SLOT_SIZE : panel.x() + 6 + index * SLOT_SIZE;
    }

    static int statusY(ActionBattleHudLayout.Rect panel) {
        return panel.y() + panel.height() + PANEL_GAP;
    }

    private static void render(GuiGraphics graphics, ActionBattleHudLayout.Rect panel, List<ActionBattleHudPayload.StatusState> statuses, boolean ally) {
        if (statuses == null || statuses.isEmpty()) return;
        List<ActionBattleStatusHudEntry> entries = new ArrayList<>();
        for (ActionBattleHudPayload.StatusState state : statuses) {
            if (state == null || state.remainingTicks() <= 0L) continue;
            ActionBattleStatusVisualRegistry.StatusVisual visual = ActionBattleStatusVisualRegistry.visualFor(state.statusId());
            if (visual != null) entries.add(new ActionBattleStatusHudEntry(state, visual));
        }
        entries.sort(java.util.Comparator.comparingInt(entry -> priorityFor(entry.state().statusId())));
        for (int i = 0; i < entries.size(); i++) {
            ActionBattleStatusHudEntry entry = entries.get(i);
            int x = statusX(panel, i, ally);
            int y = statusY(panel);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(entry.visual().icon(), x, y, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            renderTimerRing(graphics, x + ICON_SIZE / 2, y + ICON_SIZE / 2, entry.progress(), entry.visual().ringArgb());
            RenderSystem.disableBlend();
        }
    }

    private static int priorityFor(String statusId) {
        return statusId != null && statusId.startsWith("DETERIORATING_SHIELD_") ? 0 : 100;
    }

    private static void renderTimerRing(GuiGraphics graphics, int centerX, int centerY, float progress, int color) {
        int visible = Math.clamp(Math.round(RING_SEGMENTS * progress), 0, RING_SEGMENTS);
        for (int segment = 0; segment < visible; segment++) {
            double angle = -Math.PI / 2.0D + (Math.PI * 2.0D * segment / RING_SEGMENTS);
            int px = centerX + (int) Math.round(Math.cos(angle) * RING_RADIUS);
            int py = centerY + (int) Math.round(Math.sin(angle) * RING_RADIUS);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, color);
        }
    }
}

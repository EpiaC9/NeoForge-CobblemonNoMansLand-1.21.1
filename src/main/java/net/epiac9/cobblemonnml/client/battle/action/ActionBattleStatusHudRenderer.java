package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public final class ActionBattleStatusHudRenderer {
    private static final int ICON_SIZE = ActionBattleEffectIconRules.standardSize();
    private static final int PANEL_GAP = 2;

    private ActionBattleStatusHudRenderer() {}

    public static void renderEnemy(GuiGraphics graphics, ActionBattleHudLayout.Rect panel, List<ActionBattleHudPayload.StatusState> statuses) {
        render(graphics, panel, statuses, false, false);
    }

    public static void renderAlly(GuiGraphics graphics, ActionBattleHudLayout.Rect panel, List<ActionBattleHudPayload.StatusState> statuses) {
        render(graphics, panel, statuses, true, false);
    }

    public static void renderEnemy(GuiGraphics graphics, ActionBattleHudLayout.Rect panel,
                                   List<ActionBattleHudPayload.StatusState> statuses, boolean grayscale) {
        render(graphics, panel, statuses, false, grayscale);
    }

    public static void renderAlly(GuiGraphics graphics, ActionBattleHudLayout.Rect panel,
                                  List<ActionBattleHudPayload.StatusState> statuses, boolean grayscale) {
        render(graphics, panel, statuses, true, grayscale);
    }

    static int statusX(ActionBattleHudLayout.Rect panel, int index, boolean ally) {
        return ActionBattleStatusHudRules.statusX(
                panel.x(), panel.width(), index, ally);
    }

    static int statusY(ActionBattleHudLayout.Rect panel) {
        return ActionBattleStatStageHudRenderer.rowBottom(panel) + PANEL_GAP;
    }

    private static void render(GuiGraphics graphics, ActionBattleHudLayout.Rect panel,
                               List<ActionBattleHudPayload.StatusState> statuses,
                               boolean ally, boolean grayscale) {
        if (statuses == null || statuses.isEmpty()) return;
        List<ActionBattleStatusHudEntry> entries = new ArrayList<>();
        for (ActionBattleHudPayload.StatusState state : statuses) {
            if (state == null || !ActionBattleStatusHudRules.shouldDisplay(
                    state.statusId(), state.remainingTicks())) continue;
            ActionBattleStatusVisualRegistry.StatusVisual visual = ActionBattleStatusVisualRegistry.visualFor(state.statusId());
            if (visual != null) entries.add(new ActionBattleStatusHudEntry(state, visual));
        }
        entries.sort(java.util.Comparator.comparingInt(entry -> priorityFor(entry.state().statusId())));
        for (int i = 0; i < entries.size(); i++) {
            ActionBattleStatusHudEntry entry = entries.get(i);
            int x = statusX(panel, i, ally);
            int y = statusY(panel);
            ActionBattleEffectIconRenderer.render(graphics, x, y, ICON_SIZE,
                    entry.state(), entry.visual(), grayscale);
        }
    }

    private static int priorityFor(String statusId) {
        if (statusId == null) return Integer.MAX_VALUE;
        if (statusId.startsWith("DETERIORATING_SHIELD_")) return 0;
        return statusId.startsWith("TYPE_") ? 200 : 100;
    }

    static int visibleBuildupSegments(ActionBattleHudPayload.StatusState state) {
        if (state == null || !"RAMPAGE".equals(state.statusId())) return 0;
        return Math.clamp((int) state.remainingTicks(), 0, 2);
    }

}

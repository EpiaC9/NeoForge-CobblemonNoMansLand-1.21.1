package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleObscurityGeometry {
    private ActionBattleObscurityGeometry() {}

    public static ActionBattleHudLayout.Rect panel(ActionBattleHudLayout.Rect panel) {
        return panel;
    }

    public static ActionBattleHudLayout.Rect hpBar(ActionBattleHudLayout.Rect panel, boolean flipped) {
        return new ActionBattleHudLayout.Rect(panel.x() + (flipped ? 5 : 38), panel.y() + 20, 101, 8);
    }

    public static ActionBattleHudLayout.Rect icon(ActionBattleHudLayout.Rect panel, boolean flipped) {
        return new ActionBattleHudLayout.Rect(panel.x() + (flipped ? 106 : 4), panel.y() + 4, 30, 30);
    }

    public static ActionBattleHudLayout.Rect name(ActionBattleHudLayout.Rect panel, boolean flipped) {
        return new ActionBattleHudLayout.Rect(panel.x() + (flipped ? 7 : 40), panel.y() + 4, 96, 12);
    }

    public static ActionBattleHudLayout.Rect statuses(ActionBattleHudLayout.Rect panel) {
        return new ActionBattleHudLayout.Rect(panel.x(), panel.y() + panel.height() + 1,
                panel.width(), ActionBattleEffectIconRules.standardSize());
    }

    public static ActionBattleHudLayout.Rect stats(ActionBattleHudLayout.Rect panel, boolean flipped) {
        return new ActionBattleHudLayout.Rect(panel.x() + (flipped ? 5 : 38), panel.y() + 28, 97, 11);
    }

    public static ActionBattleHudLayout.Rect command(ActionBattleHudLayout layout, int slot) {
        return layout.commandButton(slot);
    }

    public static ActionBattleHudLayout.Rect move(ActionBattleHudLayout layout, int slot) {
        return layout.moveButton(slot);
    }
}

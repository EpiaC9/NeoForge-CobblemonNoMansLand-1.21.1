package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;

public final class ActionBattleHudClientState {
    private static ActionBattleHudPayload latest = ActionBattleHudPayload.hidden();
    private ActionBattleHudClientState() {}
    public static void apply(ActionBattleHudPayload payload) { latest = payload != null ? payload : ActionBattleHudPayload.hidden(); }
    public static ActionBattleHudPayload get() { return latest; }
    public static boolean isVisible() { return latest.visible(); }
    public static void clear() { latest = ActionBattleHudPayload.hidden(); }
}

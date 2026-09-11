package net.epiac9.cobblemonnml.battle.action.audit;

/**
 * Audit-only registry for standalone NML move handlers that bypass shared ACTION move families.
 * Shared family runtimes are intentionally not counted as explicit handlers.
 */
public final class ActionBattleExplicitMoveHandlerAudit {
    private ActionBattleExplicitMoveHandlerAudit() {}

    public static String handlerFor(String moveId) {
        return "";
    }

    public static boolean hasHandler(String moveId) {
        return false;
    }
}

package net.epiac9.cobblemonnml.battle.action.audit;

import java.util.Map;

/**
 * Static audit-only registry for NML move handlers that bypass the generic ACTION move path.
 * Keep this list limited to handlers owned by CobblemonNML; external visual providers belong
 * in ActionBattleAddonVisualAudit instead.
 */
public final class ActionBattleExplicitMoveHandlerAudit {
    private static final Map<String, String> HANDLERS = Map.of(
            "banefulbunker", "ActionBattleBalefulBunkerHandler",
            "earthquake", "ActionBattleEarthquakeHandler",
            "hail", "ActionBattleHailHandler",
            "toxicspikes", "ActionBattleToxicSpikesHandler"
    );

    private ActionBattleExplicitMoveHandlerAudit() {}

    public static String handlerFor(String moveId) {
        if (moveId == null) return "";
        return HANDLERS.getOrDefault(moveId, "");
    }

    public static boolean hasHandler(String moveId) {
        return !handlerFor(moveId).isBlank();
    }
}

package net.epiac9.cobblemonnml.battle.action;

import java.util.Set;
import java.util.UUID;

public record ActionBattleArenaTransition(UUID playerUUID, Set<Action> actions) {
    public ActionBattleArenaTransition {
        actions = actions == null || actions.isEmpty() ? Set.of() : Set.copyOf(actions);
    }

    public boolean has(Action action) { return actions.contains(action); }

    public enum Action {
        JOIN_PARTICIPANT,
        SEND_OUT_PLAYER,
        RECALL_PLAYER,
        SEND_OUT_TRAINER,
        RECALL_TRAINER,
        SHOW_HUD,
        HIDE_HUD
    }
}

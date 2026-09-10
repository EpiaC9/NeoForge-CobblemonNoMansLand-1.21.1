package net.epiac9.cobblemonnml.battle.action.effect;

import java.util.Map;

public record ActionBattleStatApplicationResult(
        Map<ActionBattleStat, Integer> receiverStages
) {
    public ActionBattleStatApplicationResult {
        receiverStages = receiverStages == null ? Map.of() : Map.copyOf(receiverStages);
    }
}

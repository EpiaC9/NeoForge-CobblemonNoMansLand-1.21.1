package net.epiac9.cobblemonnml.battle.action.effect;

import java.util.Map;
import java.util.UUID;

public record ActionBattleStatApplicationResult(
        Map<ActionBattleStat, Integer> receiverStages,
        UUID psycUpCasterId,
        Map<ActionBattleStat, Integer> psycUpCasterStages
) {
    public ActionBattleStatApplicationResult {
        receiverStages = receiverStages == null ? Map.of() : Map.copyOf(receiverStages);
        psycUpCasterStages = psycUpCasterStages == null ? Map.of() : Map.copyOf(psycUpCasterStages);
    }
}

package net.epiac9.cobblemonnml.battle.action.effect;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleStatApplicationService {
    private static final EnumSet<ActionBattleStat> SUPPORTED_STATS = EnumSet.of(
            ActionBattleStat.ATTACK, ActionBattleStat.DEFENSE, ActionBattleStat.SPECIAL_ATTACK,
            ActionBattleStat.SPECIAL_DEFENSE, ActionBattleStat.SPEED, ActionBattleStat.ACCURACY);
    private static final ActionBattleStatApplicationService GLOBAL = new ActionBattleStatApplicationService(
            ActionBattleEffectController.global());

    private final ActionBattleEffectController effects;

    public ActionBattleStatApplicationService(ActionBattleEffectController effects) {
        if (effects == null) throw new IllegalArgumentException("Stat service cannot be null.");
        this.effects = effects;
    }

    public static ActionBattleStatApplicationService global() { return GLOBAL; }

    public ActionBattleStatApplicationResult applyBatch(UUID battleId, UUID receiverId,
                                                         Map<ActionBattleStat, Integer> requestedStages,
                                                         long currentTick, ActionBattleStatSource source,
                                                         boolean reactive) {
        if (battleId == null || receiverId == null || requestedStages == null || currentTick < 0L || source == null) {
            return new ActionBattleStatApplicationResult(Map.of());
        }
        EnumMap<ActionBattleStat, Integer> receiver = new EnumMap<>(ActionBattleStat.class);
        for (ActionBattleStat stat : ActionBattleStat.values()) {
            if (!SUPPORTED_STATS.contains(stat)) continue;
            int requested = requestedStages.getOrDefault(stat, 0);
            if (requested == 0) continue;
            putNonZero(receiver, stat, effects.applyBoundedStatContribution(
                    battleId, receiverId, stat, requested, currentTick, source));
        }
        return new ActionBattleStatApplicationResult(receiver);
    }

    private static void putNonZero(Map<ActionBattleStat, Integer> values, ActionBattleStat stat, int stages) {
        if (stages != 0) values.put(stat, stages);
    }
}

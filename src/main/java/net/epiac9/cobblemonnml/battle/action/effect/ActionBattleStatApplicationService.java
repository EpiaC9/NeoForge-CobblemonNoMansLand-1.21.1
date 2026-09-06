package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.psychic.ActionBattlePsycUpController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.psychic.ActionBattlePsycUpState;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleStatApplicationService {
    private static final EnumSet<ActionBattleStat> SUPPORTED_STATS = EnumSet.of(
            ActionBattleStat.ATTACK, ActionBattleStat.DEFENSE, ActionBattleStat.SPECIAL_ATTACK,
            ActionBattleStat.SPECIAL_DEFENSE, ActionBattleStat.SPEED, ActionBattleStat.ACCURACY);
    private static final ActionBattleStatApplicationService GLOBAL = new ActionBattleStatApplicationService(
            ActionBattleEffectController.global(), ActionBattlePsycUpController.global());

    private final ActionBattleEffectController effects;
    private final ActionBattlePsycUpController psycUp;

    public ActionBattleStatApplicationService(ActionBattleEffectController effects,
                                              ActionBattlePsycUpController psycUp) {
        if (effects == null || psycUp == null) throw new IllegalArgumentException("Stat services cannot be null.");
        this.effects = effects;
        this.psycUp = psycUp;
    }

    public static ActionBattleStatApplicationService global() { return GLOBAL; }

    public ActionBattleStatApplicationResult applyBatch(UUID battleId, UUID receiverId,
                                                         Map<ActionBattleStat, Integer> requestedStages,
                                                         long currentTick, ActionBattleStatSource source,
                                                         boolean reactive) {
        if (battleId == null || receiverId == null || requestedStages == null || currentTick < 0L || source == null) {
            return new ActionBattleStatApplicationResult(Map.of(), null, Map.of());
        }
        EnumMap<ActionBattleStat, Integer> receiver = new EnumMap<>(ActionBattleStat.class);
        EnumMap<ActionBattleStat, Integer> caster = new EnumMap<>(ActionBattleStat.class);
        ActionBattlePsycUpState link = reactive && source != ActionBattleStatSource.PSYC_UP_DERIVED
                ? psycUp.activeLink(battleId, receiverId, currentTick).orElse(null) : null;
        for (ActionBattleStat stat : ActionBattleStat.values()) {
            if (!SUPPORTED_STATS.contains(stat)) continue;
            int requested = requestedStages.getOrDefault(stat, 0);
            if (requested == 0) continue;
            if (requested < 0 || link == null) {
                putNonZero(receiver, stat, effects.applyBoundedStatContribution(
                        battleId, receiverId, stat, requested, currentTick, source));
                continue;
            }
            if (link.casterPsychicTyped()) {
                int moved = effects.applyBoundedStatContribution(battleId, link.casterPokemonId(), stat,
                        requested, currentTick, ActionBattleStatSource.PSYC_UP_DERIVED);
                int kept = effects.applyBoundedStatContribution(battleId, receiverId, stat,
                        requested - moved, currentTick, source);
                putNonZero(caster, stat, moved);
                putNonZero(receiver, stat, kept);
            } else {
                int kept = effects.applyBoundedStatContribution(
                        battleId, receiverId, stat, requested, currentTick, source);
                int mirrored = effects.applyBoundedStatContribution(battleId, link.casterPokemonId(), stat,
                        kept, currentTick, ActionBattleStatSource.PSYC_UP_DERIVED);
                putNonZero(receiver, stat, kept);
                putNonZero(caster, stat, mirrored);
            }
        }
        return new ActionBattleStatApplicationResult(receiver,
                link != null && !caster.isEmpty() ? link.casterPokemonId() : null, caster);
    }

    private static void putNonZero(Map<ActionBattleStat, Integer> values, ActionBattleStat stat, int stages) {
        if (stages != 0) values.put(stat, stages);
    }
}

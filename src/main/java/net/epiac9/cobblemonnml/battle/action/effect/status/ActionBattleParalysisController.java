package net.epiac9.cobblemonnml.battle.action.effect.status;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatusApplication;

public final class ActionBattleParalysisController {
    private static final String EXECUTION_READY_TICK = "CobblemonNmlParalysisReadyTick";
    private ActionBattleParalysisController() {}

    public static ActionBattleStatusApplication apply(ActionBattleSession session, PokemonEntity target,
                                                       long currentTick, long durationTicks) {
        if (session == null || target == null || target.isRemoved() || currentTick < 0L
                || !ActionBattleEffectApplicationGuard.allowsNewApplication(session, target, currentTick)) {
            return ActionBattleStatusApplication.REJECTED_INVALID;
        }
        return ActionBattleEffectController.global().applyStatus(session.battleId(),
                target.getPokemon().getUuid(), ActionBattleStatus.PARALYSIS, currentTick, durationTicks);
    }

    public static boolean active(ActionBattleSession session, java.util.UUID pokemonId, long currentTick) {
        return session != null && pokemonId != null && ActionBattleEffectController.global().hasStatus(
                session.battleId(), pokemonId, ActionBattleStatus.PARALYSIS, currentTick);
    }

    public static boolean executionReady(PokemonEntity pokemon, boolean active, long currentTick) {
        if (pokemon == null || currentTick < 0L) return false;
        var data = pokemon.getPersistentData();
        if (!active) {
            data.remove(EXECUTION_READY_TICK);
            return true;
        }
        if (!data.contains(EXECUTION_READY_TICK)) {
            data.putLong(EXECUTION_READY_TICK, currentTick + ActionBattleParalysisRules.EXECUTION_DELAY_TICKS);
            return false;
        }
        if (currentTick < data.getLong(EXECUTION_READY_TICK)) return false;
        data.remove(EXECUTION_READY_TICK);
        return true;
    }
}

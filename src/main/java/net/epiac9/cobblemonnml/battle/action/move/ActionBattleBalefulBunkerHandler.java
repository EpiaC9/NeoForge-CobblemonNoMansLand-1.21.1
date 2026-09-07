package net.epiac9.cobblemonnml.battle.action.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectStance;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;

public final class ActionBattleBalefulBunkerHandler {
    private static final String MOVE_ID = "banefulbunker";
    public static final long STANCE_TICKS = 40L;
    public static final long GLOBAL_COOLDOWN_TICKS = 60L;

    private ActionBattleBalefulBunkerHandler() {}

    public static boolean isBalefulBunker(Move move) {
        return move != null && MOVE_ID.equals(move.getName());
    }

    public static StartResult tryStart(ActionBattleSession session, PokemonEntity caster, Move move) {
        if (session == null || caster == null || move == null || !isBalefulBunker(move) || caster.isRemoved()) return StartResult.INVALID;
        long currentTick = caster.level().getGameTime();
        if (session.isPokemonSharedAbilityOnCooldown(caster.getPokemon().getUuid(), currentTick)) return StartResult.COOLDOWN;
        if (!FightOrFlightAdapter.consumeOnePp(caster, move)) return StartResult.NO_PP;
        caster.getNavigation().stop();
        ActionBattleProtectStance stance = ActionBattleProtectController.global().startBalefulBunker(
                session.battleId(), caster.getPokemon().getUuid(), currentTick
        );
        if (stance == null) {
            FightOrFlightAdapter.refundOnePp(move);
            return StartResult.INVALID;
        }
        ActionBattleGhostRuntime.global().applyAbilityCooldown(session, caster,
                ActionBattleGhostRuntime.global().findMoveSlot(caster, move), currentTick);
        return StartResult.STARTED;
    }

    public enum StartResult { STARTED, INVALID, NO_PP, COOLDOWN }
}

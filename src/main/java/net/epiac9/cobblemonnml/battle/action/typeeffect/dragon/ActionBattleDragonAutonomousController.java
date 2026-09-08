package net.epiac9.cobblemonnml.battle.action.typeeffect.dragon;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleCommandController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fighting.ActionBattleFightingRuntime;
import net.minecraft.server.level.ServerLevel;

public final class ActionBattleDragonAutonomousController {
    private ActionBattleDragonAutonomousController() {}

    public static void tick(ActionBattleSession session, ServerLevel level, Pokemon pokemon, Pokemon target) {
        if (session == null || level == null || pokemon == null
                || target == null || session.hasPlayerMoveCommand()
                || session.hasPlayerMovementIntent()) return;
        long currentTick = level.getGameTime();
        if (!ActionBattleDragonRuntime.active(session, pokemon.getUuid(), currentTick)) return;
        int slot = selectMoveSlot(session, pokemon, currentTick);
        if (slot < 0 || session.trainerActiveEntityUUID() == null) return;
        ActionBattleCommandController.onCommandIssued(session, pokemon.getUuid());
        session.replacePlayerMoveCommand(slot, session.trainerActiveEntityUUID());
    }

    static int selectMoveSlot(ActionBattleSession session, Pokemon pokemon, long currentTick) {
        boolean[] usable = new boolean[4];
        boolean[] damaging = new boolean[4];
        for (int slot = 0; slot < 4; slot++) {
            Move move = pokemon.getMoveSet().get(slot);
            if (move == null || !FightOrFlightAdapter.supports(move) || !FightOrFlightAdapter.hasPp(move)
                    || session.isPokemonAbilitySlotOnCooldown(pokemon.getUuid(), slot, currentTick)
                    || !ActionBattleControlController.global().canUseMove(
                            session.battleId(), pokemon.getUuid(), move, currentTick)
                    || !ActionBattleFightingRuntime.canUseAbility(session, pokemon, move, currentTick)) continue;
            usable[slot] = true;
            damaging[slot] = FightOrFlightAdapter.movePower(move) > 0;
        }
        return ActionBattleDragonAutonomousRules.chooseSlot(usable, damaging);
    }
}

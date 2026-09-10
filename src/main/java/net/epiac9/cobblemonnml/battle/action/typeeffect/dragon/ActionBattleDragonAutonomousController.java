package net.epiac9.cobblemonnml.battle.action.typeeffect.dragon;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleCommandController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleTargetingRules;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlController;
import net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleRampageController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime;
import net.minecraft.server.level.ServerLevel;

public final class ActionBattleDragonAutonomousController {
    private ActionBattleDragonAutonomousController() {}

    public static void tick(ActionBattleSession session, ServerLevel level, Pokemon pokemon, Pokemon target) {
        java.util.UUID playerUUID = session != null ? session.playerOwnerForPokemon(pokemon != null ? pokemon.getUuid() : null) : null;
        if (session == null || level == null || pokemon == null
                || target == null || playerUUID == null || session.hasPlayerMoveCommand(playerUUID)
                || session.hasPlayerMovementIntent(playerUUID)) return;
        long currentTick = level.getGameTime();
        if (!ActionBattleDragonRuntime.active(session, pokemon.getUuid(), currentTick)) return;
        PokemonEntity observer = pokemon.getEntity();
        PokemonEntity targetEntity = target.getEntity();
        boolean enemyVisible = observer != null && targetEntity != null
                && ActionBattleDarkRuntime.canPerceive(session, observer, targetEntity, currentTick);
        int slot = selectMoveSlot(session, pokemon, currentTick, enemyVisible);
        if (slot < 0 || session.trainerActiveEntityUUID() == null) return;
        ActionBattleCommandController.onCommandIssued(session, pokemon.getUuid());
        session.replacePlayerMoveCommand(playerUUID, slot, session.trainerActiveEntityUUID());
    }

    static int selectMoveSlot(ActionBattleSession session, Pokemon pokemon, long currentTick,
                              boolean enemyVisible) {
        boolean[] usable = new boolean[4];
        boolean[] damaging = new boolean[4];
        PokemonEntity pokemonEntity = pokemon.getEntity();
        for (int slot = 0; slot < 4; slot++) {
            Move move = pokemon.getMoveSet().get(slot);
            if (move == null || !FightOrFlightAdapter.supportsForUser(pokemonEntity, move) || !FightOrFlightAdapter.hasPp(move)
                    || session.isPokemonAbilitySlotOnCooldown(pokemon.getUuid(), slot, currentTick)
                    || !ActionBattleControlController.global().canUseMove(
                            session.battleId(), pokemon.getUuid(), move, currentTick)
                    || !ActionBattleRampageController.global().canUseAbility(session.battleId(), pokemon.getUuid(), move.getName(), currentTick)) continue;
            boolean enemyTargeted = !FightOrFlightAdapter.isSelfOrAllyTargetCategory(
                    FightOrFlightAdapter.moveTargetCategory(move));
            if (!ActionBattleTargetingRules.maySelectMove(enemyVisible, enemyTargeted)) continue;
            usable[slot] = true;
            damaging[slot] = FightOrFlightAdapter.movePower(move) > 0;
        }
        return ActionBattleDragonAutonomousRules.chooseSlot(usable, damaging);
    }
}

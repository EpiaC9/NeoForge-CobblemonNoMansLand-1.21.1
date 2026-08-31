package net.epiac9.cobblemonnml.battle.action.compat;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.data.movedata.MoveData;
import me.rufia.fightorflight.data.movedata.movedatas.StatusEffectMoveData;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatusApplication;
import net.epiac9.cobblemonnml.util.DebugLog;

import java.util.List;
import java.util.Objects;

public final class ActionBattleMoveEffectResolver {
    private ActionBattleMoveEffectResolver() {}

    public static boolean hasStatusMetadata(Move move) {
        if (move == null) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries == null) return false;
        for (MoveData entry : entries) if (entry instanceof StatusEffectMoveData) return true;
        return false;
    }


    public static boolean hasSupportedBurnOnHitMetadata(Move move) {
        if (move == null) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries != null) {
            for (MoveData entry : entries) {
                if (!(entry instanceof StatusEffectMoveData status)) continue;
                if (!status.isOnHit() || !Objects.equals(status.getTarget(), "target")) continue;
                if (Objects.equals(status.getName(), "burn") || Objects.equals(status.getName(), "triattack")) return true;
            }
        }
        ActionBattleMoveEffectData fallback = ActionBattleMoveEffectDataManager.get(move.getName());
        return fallback != null && fallback.isSupportedBurnOnHit();
    }

    public static void applyDeclaredBurnOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        if (!hitSucceeded || attacker == null || target == null || move == null || attacker.level().isClientSide) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return;
        if (!rollBurnEffect(attacker, move)) return;
        long currentTick = attacker.level().getGameTime();
        ActionBattleStatusApplication result = ActionBattleEffectController.global().applyBurnCapableHit(
                session.battleId(), target.getPokemon().getUuid(), currentTick
        );
        if (result != null) {
            DebugLog.log("[CobblemonNML] Action battle Burn effect resolved. Battle=" + session.battleId()
                    + ", move=" + move.getName() + ", target=" + target.getPokemon().getUuid() + ", result=" + result);
        }
    }

    private static boolean rollBurnEffect(PokemonEntity attacker, Move move) {
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        boolean foundFoFBurnMetadata = false;
        if (entries != null) {
            for (MoveData entry : entries) {
                if (!(entry instanceof StatusEffectMoveData status)) continue;
                if (!status.isOnHit() || !Objects.equals(status.getTarget(), "target")) continue;
                String effectName = status.getName();
                if (!Objects.equals(effectName, "burn") && !Objects.equals(effectName, "triattack")) continue;
                foundFoFBurnMetadata = true;
                if (status.canActivateSheerForce() && Objects.equals(attacker.getPokemon().getAbility().getName(), "sheerforce")) continue;
                float chance = status.getChance();
                if (Objects.equals(attacker.getPokemon().getAbility().getName(), "serenegrace")) chance *= 2.0F;
                if (!(chance > attacker.getRandom().nextFloat())) continue;
                if (Objects.equals(effectName, "triattack")) return attacker.getRandom().nextFloat() < (1.0F / 3.0F);
                return true;
            }
        }
        if (foundFoFBurnMetadata) return false;
        ActionBattleMoveEffectData fallback = ActionBattleMoveEffectDataManager.get(move.getName());
        return fallback != null && fallback.isSupportedBurnOnHit() && fallback.chance() > attacker.getRandom().nextFloat();
    }
}

package net.epiac9.cobblemonnml.battle.action.compat;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.data.movedata.MoveData;
import me.rufia.fightorflight.data.movedata.movedatas.StatusEffectMoveData;
import net.epiac9.cobblemonnml.battle.action.ActionBattleConfusionController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleFlinchController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
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

    public static boolean hasSupportedFlinchOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.FLINCH); }
    public static boolean hasSupportedConfusionOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.CONFUSION); }
    public static boolean hasExplicitWakeOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.WAKE); }

    public static boolean isOwnedActionStatus(StatusEffectMoveData status) {
        if (!isOnHitTarget(status)) return false;
        String name = status.getName();
        return StatusFamily.FLINCH.matchesMetadata(name) || StatusFamily.CONFUSION.matchesMetadata(name)
                || StatusFamily.WAKE.matchesMetadata(name);
    }

    public static void applyDeclaredFlinchOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.FLINCH);
    }

    public static void applyDeclaredConfusionOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.CONFUSION);
    }

    private static boolean hasSupportedOnHitMetadata(Move move, StatusFamily family) {
        if (move == null) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries != null) {
            for (MoveData entry : entries) {
                if (entry instanceof StatusEffectMoveData status && isOnHitTarget(status) && family.matchesMetadata(status.getName())) return true;
            }
        }
        for (ActionBattleMoveEffectData fallback : ActionBattleMoveEffectDataManager.getAll(move.getName())) {
            if (family.matchesFallback(fallback)) return true;
        }
        return false;
    }

    private static void applyDeclared(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded, StatusFamily family) {
        if (!hitSucceeded || attacker == null || target == null || move == null || attacker.level().isClientSide) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return;
        if (!rollEffect(attacker, move, family)) return;
        long currentTick = attacker.level().getGameTime();
        if (family == StatusFamily.FLINCH) {
            boolean applied = ActionBattleFlinchController.apply(session, target.getPokemon().getUuid(), currentTick, FightOrFlightAdapter.makesContact(move));
            if (applied) DebugLog.log("[CobblemonNML] Action battle Flinch resolved. Battle=" + session.battleId()
                    + ", move=" + move.getName() + ", target=" + target.getPokemon().getUuid());
            return;
        }
        ActionBattleStatusApplication result = ActionBattleConfusionController.apply(session, target, currentTick);
        if (result != null) DebugLog.log("[CobblemonNML] Action battle Confusion effect resolved. Battle=" + session.battleId()
                + ", move=" + move.getName() + ", target=" + target.getPokemon().getUuid() + ", result=" + result);
    }

    private static boolean rollEffect(PokemonEntity attacker, Move move, StatusFamily family) {
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        boolean foundOwnedMetadata = false;
        if (entries != null) {
            for (MoveData entry : entries) {
                if (!(entry instanceof StatusEffectMoveData status) || !isOnHitTarget(status) || !family.matchesMetadata(status.getName())) continue;
                foundOwnedMetadata = true;
                if (status.canActivateSheerForce() && hasAbility(attacker, "sheerforce")) continue;
                float chance = status.getChance();
                if (hasAbility(attacker, "serenegrace")) chance *= 2.0F;
                if (chance > attacker.getRandom().nextFloat()) return true;
            }
        }
        if (foundOwnedMetadata) return false;
        for (ActionBattleMoveEffectData fallback : ActionBattleMoveEffectDataManager.getAll(move.getName())) {
            if (!family.matchesFallback(fallback)) continue;
            if (fallback.secondary() && hasAbility(attacker, "sheerforce")) continue;
            float chance = fallback.chance();
            if (fallback.secondary() && hasAbility(attacker, "serenegrace")) chance *= 2.0F;
            if (chance > attacker.getRandom().nextFloat()) return true;
        }
        return false;
    }

    private static boolean isOnHitTarget(StatusEffectMoveData status) {
        return status != null && status.isOnHit() && Objects.equals(status.getTarget(), "target");
    }

    private static boolean hasAbility(PokemonEntity attacker, String abilityName) {
        return attacker != null && Objects.equals(attacker.getPokemon().getAbility().getName(), abilityName);
    }

    private enum StatusFamily {
        CONFUSION,
        FLINCH,
        WAKE;

        boolean matchesMetadata(String name) {
            if (this == FLINCH) return Objects.equals(name, "flinch");
            if (this == WAKE) return Objects.equals(name, "wake") || Objects.equals(name, "wakeup") || Objects.equals(name, "wake_up");
            return Objects.equals(name, "confusion") || Objects.equals(name, "confuse") || Objects.equals(name, "confused");
        }

        boolean matchesFallback(ActionBattleMoveEffectData fallback) {
            if (this == FLINCH) return fallback.isSupportedFlinchOnHit();
            if (this == WAKE) return fallback.isExplicitWakeOnHit();
            return fallback.isSupportedConfusionOnHit();
        }
    }
}

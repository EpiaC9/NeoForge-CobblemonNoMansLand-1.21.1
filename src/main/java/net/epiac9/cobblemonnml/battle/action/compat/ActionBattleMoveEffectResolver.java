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
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.util.DebugLog;

import java.util.List;
import java.util.Locale;
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
    public static boolean hasSupportedParalysisOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.PARALYSIS); }

    public static boolean isOwnedParalysisName(String name) {
        String normalized = name != null ? name.trim().toLowerCase(Locale.ROOT) : "";
        return Objects.equals(normalized, "paralysis") || Objects.equals(normalized, "paralyze")
                || Objects.equals(normalized, "paralyzed");
    }

    public static boolean isOwnedActionStatus(StatusEffectMoveData status) {
        if (!isOnHitTarget(status)) return false;
        String name = status.getName();
        return StatusFamily.FLINCH.matchesMetadata(name) || StatusFamily.CONFUSION.matchesMetadata(name)
            || StatusFamily.WAKE.matchesMetadata(name) || StatusFamily.PARALYSIS.matchesMetadata(name);
    }

    public static void applyDeclaredFlinchOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.FLINCH);
    }

    public static void applyDeclaredConfusionOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.CONFUSION);
    }

    public static void applyDeclaredParalysisOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        if (!hitSucceeded || attacker == null || target == null || move == null || attacker.level().isClientSide) return;
        if (!allowsDirectParalysisMetadata(move.getType() != null ? move.getType().getName() : null)) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))
                || !DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())
                || !rollEffect(attacker, move, StatusFamily.PARALYSIS)) return;
        ActionBattleElectricController.applyExternalParalysis(ActionBattleTypeEffectController.global(),
                session.dungeonSessionId(), target.getPokemon().getUuid(), attacker.level().getGameTime(),
                hasType(target, "electric"), ActionBattleEffectController.global().hasHaze(
                        session.battleId(), target.getPokemon().getUuid(), attacker.level().getGameTime()));
    }

    public static boolean allowsDirectParalysisMetadata(String moveTypeName) {
        return !"electric".equals(moveTypeName != null ? moveTypeName.trim().toLowerCase(Locale.ROOT) : "");
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
        WAKE,
        PARALYSIS;

        boolean matchesMetadata(String name) {
            if (this == FLINCH) return Objects.equals(name, "flinch");
            if (this == WAKE) return Objects.equals(name, "wake") || Objects.equals(name, "wakeup") || Objects.equals(name, "wake_up");
            if (this == PARALYSIS) return isOwnedParalysisName(name);
            return Objects.equals(name, "confusion") || Objects.equals(name, "confuse") || Objects.equals(name, "confused");
        }

        boolean matchesFallback(ActionBattleMoveEffectData fallback) {
            if (this == FLINCH) return fallback.isSupportedFlinchOnHit();
            if (this == WAKE) return fallback.isExplicitWakeOnHit();
            if (this == PARALYSIS) return fallback.isSupportedParalysisOnHit();
            return fallback.isSupportedConfusionOnHit();
        }
    }

    private static boolean hasType(PokemonEntity entity, String typeName) {
        if (entity == null || entity.getPokemon() == null) return false;
        String expected = typeName != null ? typeName.toLowerCase(Locale.ROOT) : "";
        var pokemon = entity.getPokemon();
        if (pokemon.getPrimaryType() != null
                && expected.equals(pokemon.getPrimaryType().getName().toLowerCase(Locale.ROOT))) return true;
        return pokemon.getSecondaryType() != null
                && expected.equals(pokemon.getSecondaryType().getName().toLowerCase(Locale.ROOT));
    }
}

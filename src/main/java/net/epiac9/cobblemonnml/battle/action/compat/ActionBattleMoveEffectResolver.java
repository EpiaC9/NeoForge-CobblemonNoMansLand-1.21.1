package net.epiac9.cobblemonnml.battle.action.compat;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.data.movedata.MoveData;
import me.rufia.fightorflight.data.movedata.movedatas.StatusEffectMoveData;
import me.rufia.fightorflight.data.movedata.movedatas.StatChangeMoveData;
import net.epiac9.cobblemonnml.battle.action.ActionBattleConfusionController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleFlinchController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatusApplication;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.epiac9.cobblemonnml.mixin.ActionBattleStatChangeMoveDataAccessor;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatApplicationService;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleConfusionRules;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ActionBattleMoveEffectResolver {
    public static final int DEFAULT_DIRECT_STATUS_DURATION_TICKS = 180;
    private ActionBattleMoveEffectResolver() {}

    public static boolean hasStatusMetadata(Move move) {
        if (move == null) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries == null) return false;
        for (MoveData entry : entries) if (entry instanceof StatusEffectMoveData) return true;
        return false;
    }

    public static boolean isSelfBuffingMove(Move move) {
        if (move == null) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries == null) return false;
        for (MoveData entry : entries) {
            if (!(entry instanceof StatChangeMoveData statData)) continue;
            int stages = ((ActionBattleStatChangeMoveDataAccessor) statData).cobblemonNml$getStage();
            if (stages > 0 && !Objects.equals(statData.getTarget(), "target")) return true;
        }
        return false;
    }

    public static boolean hasSupportedFlinchOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.FLINCH); }
    public static boolean hasSupportedConfusionOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.CONFUSION); }
    public static boolean hasExplicitWakeOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.WAKE); }
    public static boolean hasSupportedParalysisOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.PARALYSIS); }

    public static boolean hasSupportedActionStatusMetadata(Move move) {
        return hasSupportedFlinchOnHitMetadata(move) || hasSupportedConfusionOnHitMetadata(move)
                || hasSupportedParalysisOnHitMetadata(move) || hasSupportedOnHitMetadata(move, StatusFamily.BURN)
                || hasSupportedOnHitMetadata(move, StatusFamily.FREEZE) || hasSupportedOnHitMetadata(move, StatusFamily.POISON)
                || hasSupportedOnHitMetadata(move, StatusFamily.TOXIC);
    }

    public static boolean isOwnedParalysisName(String name) {
        String normalized = name != null ? name.trim().toLowerCase(Locale.ROOT) : "";
        return Objects.equals(normalized, "paralysis") || Objects.equals(normalized, "paralyze")
                || Objects.equals(normalized, "paralyzed");
    }

    public static boolean isOwnedActionStatus(StatusEffectMoveData status) {
        if (!isOnHitTarget(status)) return false;
        String name = status.getName();
        return StatusFamily.FLINCH.matchesMetadata(name) || StatusFamily.CONFUSION.matchesMetadata(name)
            || StatusFamily.WAKE.matchesMetadata(name) || StatusFamily.PARALYSIS.matchesMetadata(name)
            || StatusFamily.BURN.matchesMetadata(name) || StatusFamily.FREEZE.matchesMetadata(name)
            || StatusFamily.POISON.matchesMetadata(name) || StatusFamily.TOXIC.matchesMetadata(name);
    }

    public static void applyDeclaredStatChanges(PokemonEntity attacker, LivingEntity suppliedTarget,
                                                Move move, StatTrigger trigger, boolean hitSucceeded) {
        if (attacker == null || move == null || trigger == null || attacker.level().isClientSide) return;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries == null) return;
        for (MoveData entry : entries) {
            if (!(entry instanceof StatChangeMoveData statData) || !matchesTrigger(statData, trigger)
                    || trigger == StatTrigger.ON_HIT && !hitSucceeded) continue;
            if (!ActionBattleStatMoveMetadata.passesChance(statData.getChance(),
                    hasAbility(attacker, "serenegrace"), hasAbility(attacker, "sheerforce"),
                    statData.canActivateSheerForce(), attacker.getRandom().nextFloat())) continue;
            LivingEntity receiverEntity = Objects.equals(statData.getTarget(), "target")
                    ? suppliedTarget : attacker;
            if (!(receiverEntity instanceof PokemonEntity receiver)) continue;
            ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(receiver.getUUID());
            if (session == null || !session.battleId().equals(
                    ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) continue;
            if (!ActionBattleEffectApplicationGuard.allowsNewApplication(
                    session, receiver, attacker.level().getGameTime())) continue;
            int stages = ((ActionBattleStatChangeMoveDataAccessor) statData).cobblemonNml$getStage();
            ActionBattleStatApplicationService.global().applyBatch(session.battleId(),
                    receiver.getPokemon().getUuid(), ActionBattleStatMoveMetadata.translate(statData.getName(), stages),
                    attacker.level().getGameTime(), ActionBattleStatSource.NORMAL_MOVE, true);
        }
    }

    public static boolean applyCorruptedSupport(PokemonEntity attacker, PokemonEntity enemy, Move move,
                                                 ActionBattleConfusionRules.SupportCorruption mode) {
        if (attacker == null || move == null || mode == null
                || mode == ActionBattleConfusionRules.SupportCorruption.FAIL || attacker.level().isClientSide) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries == null) return false;
        boolean applied = false;
        for (MoveData entry : entries) {
            if (!(entry instanceof StatChangeMoveData statData)) continue;
            int declared = ((ActionBattleStatChangeMoveDataAccessor) statData).cobblemonNml$getStage();
            if (declared == 0) continue;
            PokemonEntity receiver = mode == ActionBattleConfusionRules.SupportCorruption.INVERSE_SELF ? attacker : enemy;
            if (receiver == null) continue;
            int corrupted = mode == ActionBattleConfusionRules.SupportCorruption.INVERSE_SELF
                    ? -declared : declared < 0 ? -declared : declared;
            ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(receiver.getUUID());
            if (session == null || !ActionBattleEffectApplicationGuard.allowsNewApplication(
                    session, receiver, attacker.level().getGameTime())) continue;
            applied |= ActionBattleStatApplicationService.global().applyBatch(session.battleId(),
                    receiver.getPokemon().getUuid(), ActionBattleStatMoveMetadata.translate(statData.getName(), corrupted),
                    attacker.level().getGameTime(), ActionBattleStatSource.NORMAL_MOVE, true).receiverStages().values().stream()
                    .anyMatch(value -> value != 0);
        }
        return applied;
    }

    public static void applyDeclaredFlinchOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.FLINCH);
    }

    public static void applyDeclaredConfusionOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.CONFUSION);
    }

    public static void applyDeclaredParalysisOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclaredDirectStatus(attacker, target, move, hitSucceeded, StatusFamily.PARALYSIS, ActionBattleStatus.PARALYSIS);
    }

    public static void applyDeclaredMajorStatusesOnHit(PokemonEntity attacker, PokemonEntity target, Move move,
                                                        boolean hitSucceeded) {
        applyDeclaredDirectStatus(attacker, target, move, hitSucceeded, StatusFamily.BURN, ActionBattleStatus.BURN);
        applyDeclaredDirectStatus(attacker, target, move, hitSucceeded, StatusFamily.FREEZE, ActionBattleStatus.FREEZE);
        applyDeclaredDirectStatus(attacker, target, move, hitSucceeded, StatusFamily.POISON, ActionBattleStatus.POISON);
        applyDeclaredDirectStatus(attacker, target, move, hitSucceeded, StatusFamily.TOXIC, ActionBattleStatus.TOXIC);
    }

    private static void applyDeclaredDirectStatus(PokemonEntity attacker, PokemonEntity target, Move move,
                                                   boolean hitSucceeded, StatusFamily family,
                                                   ActionBattleStatus status) {
        if (!hitSucceeded || attacker == null || target == null || move == null || attacker.level().isClientSide) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))
                || !DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())
                || !rollEffect(attacker, move, family)) return;
        if (!ActionBattleEffectApplicationGuard.allowsNewApplication(
                session, target, attacker.level().getGameTime())) return;
        ActionBattleEffectController.global().applyStatus(session.battleId(), target.getPokemon().getUuid(), status,
                attacker.level().getGameTime(), DEFAULT_DIRECT_STATUS_DURATION_TICKS);
    }

    public static boolean allowsDirectParalysisMetadata(String moveTypeName) {
        return true;
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
        if (!ActionBattleEffectApplicationGuard.allowsNewApplication(
                session, target, attacker.level().getGameTime())) return;
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

    private static boolean matchesTrigger(MoveData data, StatTrigger trigger) {
        return switch (trigger) {
            case BEFORE_USE -> data.isBeforeUse();
            case ON_USE -> data.isOnUse();
            case ON_HIT -> data.isOnHit();
        };
    }

    public enum StatTrigger { BEFORE_USE, ON_USE, ON_HIT }

    private enum StatusFamily {
        CONFUSION,
        FLINCH,
        WAKE,
        PARALYSIS,
        BURN,
        FREEZE,
        POISON,
        TOXIC;

        boolean matchesMetadata(String name) {
            if (this == FLINCH) return Objects.equals(name, "flinch");
            if (this == WAKE) return Objects.equals(name, "wake") || Objects.equals(name, "wakeup") || Objects.equals(name, "wake_up");
            if (this == PARALYSIS) return isOwnedParalysisName(name);
            if (this == BURN) return Objects.equals(name, "burn") || Objects.equals(name, "burned");
            if (this == FREEZE) return Objects.equals(name, "freeze") || Objects.equals(name, "frozen");
            if (this == POISON) return Objects.equals(name, "poison") || Objects.equals(name, "poisoned");
            if (this == TOXIC) return Objects.equals(name, "toxic") || Objects.equals(name, "badly_poisoned")
                    || Objects.equals(name, "badpoison");
            return Objects.equals(name, "confusion") || Objects.equals(name, "confuse") || Objects.equals(name, "confused");
        }

        boolean matchesFallback(ActionBattleMoveEffectData fallback) {
            if (this == FLINCH) return fallback.isSupportedFlinchOnHit();
            if (this == WAKE) return fallback.isExplicitWakeOnHit();
            if (this == PARALYSIS) return fallback.isSupportedParalysisOnHit();
            if (this == BURN || this == FREEZE || this == POISON || this == TOXIC) return false;
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

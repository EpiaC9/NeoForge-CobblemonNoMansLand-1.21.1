package net.epiac9.cobblemonnml.battle.action.compat;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleConfusionController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleFlinchController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleConfusionRules;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatApplicationService;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatusApplication;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleCanonicalMoveMetadata;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveDescriptor;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveMetadataResolver;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies ordinary ACTION move effects from Cobblemon's canonical Showdown metadata.
 * NML move-effect JSON is deliberately not consulted here; that data layer is reserved
 * for NML-specific ACTION/type-mechanic routing.
 */
public final class ActionBattleMoveEffectResolver {
    public static final int DEFAULT_DIRECT_STATUS_DURATION_TICKS = 180;

    private ActionBattleMoveEffectResolver() {}

    public static boolean hasStatusMetadata(Move move) {
        ActionBattleMoveDescriptor descriptor = descriptor(null, move);
        return descriptor != null && !statusRolls(descriptor).isEmpty();
    }

    public static boolean isSelfBuffingMove(Move move) {
        ActionBattleMoveDescriptor descriptor = descriptor(null, move);
        if (descriptor == null) return false;
        if (descriptor.selfOrAllyTargeted() && hasPositive(descriptor.boosts())) return true;
        return hasPositive(descriptor.selfBoosts()) || descriptor.secondaries().stream()
                .anyMatch(secondary -> hasPositive(secondary.selfBoosts()));
    }

    public static boolean hasSupportedFlinchOnHitMetadata(Move move) {
        return hasFamily(move, StatusFamily.FLINCH);
    }

    public static boolean hasSupportedConfusionOnHitMetadata(Move move) {
        return hasFamily(move, StatusFamily.CONFUSION);
    }

    public static boolean hasExplicitWakeOnHitMetadata(Move move) {
        return false;
    }

    public static boolean hasSupportedParalysisOnHitMetadata(Move move) {
        return hasFamily(move, StatusFamily.PARALYSIS);
    }

    public static boolean hasSupportedActionStatusMetadata(Move move) {
        ActionBattleMoveDescriptor descriptor = descriptor(null, move);
        return descriptor != null && !statusRolls(descriptor).isEmpty();
    }

    public static boolean isOwnedParalysisName(String name) {
        return StatusFamily.PARALYSIS.matches(name);
    }

    public static void applyDeclaredStatChanges(PokemonEntity attacker, LivingEntity suppliedTarget,
                                                Move move, StatTrigger trigger, boolean hitSucceeded) {
        if (attacker == null || move == null || trigger == null || attacker.level().isClientSide) return;
        ActionBattleMoveDescriptor descriptor = descriptor(attacker, move);
        if (descriptor == null) return;

        if (trigger == StatTrigger.ON_USE && descriptor.selfOrAllyTargeted()) {
            PokemonEntity receiver = suppliedTarget instanceof PokemonEntity pokemon ? pokemon : attacker;
            applyStatBatch(attacker, receiver, descriptor.boosts());
            applyStatBatch(attacker, attacker, descriptor.selfBoosts());
            return;
        }

        if (trigger != StatTrigger.ON_HIT || !hitSucceeded) return;
        if (descriptor.targeted() && suppliedTarget instanceof PokemonEntity target) {
            applyStatBatch(attacker, target, descriptor.boosts());
        }
        if (!descriptor.selfOrAllyTargeted()) applyStatBatch(attacker, attacker, descriptor.selfBoosts());

        for (ActionBattleCanonicalMoveMetadata.SecondaryEffect secondary : descriptor.secondaries()) {
            if (!passesSecondaryChance(attacker, secondary.chancePercent())) continue;
            if (suppliedTarget instanceof PokemonEntity target) applyStatBatch(attacker, target, secondary.boosts());
            applyStatBatch(attacker, attacker, secondary.selfBoosts());
        }
    }

    public static boolean applyCorruptedSupport(PokemonEntity attacker, PokemonEntity enemy, Move move,
                                                 ActionBattleConfusionRules.SupportCorruption mode) {
        if (attacker == null || move == null || mode == null
                || mode == ActionBattleConfusionRules.SupportCorruption.FAIL || attacker.level().isClientSide) return false;
        ActionBattleMoveDescriptor descriptor = descriptor(attacker, move);
        if (descriptor == null) return false;
        Map<String, Integer> declared = !descriptor.boosts().isEmpty() ? descriptor.boosts() : descriptor.selfBoosts();
        if (declared.isEmpty()) return false;
        PokemonEntity receiver = mode == ActionBattleConfusionRules.SupportCorruption.INVERSE_SELF ? attacker : enemy;
        if (receiver == null) return false;
        Map<String, Integer> corrupted = declared.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> mode == ActionBattleConfusionRules.SupportCorruption.INVERSE_SELF
                        ? -entry.getValue() : entry.getValue() < 0 ? -entry.getValue() : entry.getValue()
        ));
        return applyStatBatch(attacker, receiver, corrupted);
    }

    public static void applyDeclaredFlinchOnHit(PokemonEntity attacker, PokemonEntity target, Move move,
                                                 boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.FLINCH);
    }

    public static void applyDeclaredConfusionOnHit(PokemonEntity attacker, PokemonEntity target, Move move,
                                                    boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.CONFUSION);
    }

    public static void applyDeclaredParalysisOnHit(PokemonEntity attacker, PokemonEntity target, Move move,
                                                    boolean hitSucceeded) {
        applyDeclaredDirectStatus(attacker, target, move, hitSucceeded, StatusFamily.PARALYSIS,
                ActionBattleStatus.PARALYSIS);
    }

    public static void applyDeclaredMajorStatusesOnHit(PokemonEntity attacker, PokemonEntity target, Move move,
                                                        boolean hitSucceeded) {
        applyDeclaredDirectStatus(attacker, target, move, hitSucceeded, StatusFamily.BURN, ActionBattleStatus.BURN);
        applyDeclaredDirectStatus(attacker, target, move, hitSucceeded, StatusFamily.FREEZE, ActionBattleStatus.FREEZE);
        applyDeclaredDirectStatus(attacker, target, move, hitSucceeded, StatusFamily.POISON, ActionBattleStatus.POISON);
        applyDeclaredDirectStatus(attacker, target, move, hitSucceeded, StatusFamily.TOXIC, ActionBattleStatus.TOXIC);
    }

    public static boolean allowsDirectParalysisMetadata(String moveTypeName) {
        return true;
    }

    private static void applyDeclaredDirectStatus(PokemonEntity attacker, PokemonEntity target, Move move,
                                                   boolean hitSucceeded, StatusFamily family,
                                                   ActionBattleStatus status) {
        if (!hitSucceeded || attacker == null || target == null || move == null || attacker.level().isClientSide) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))
                || !DungeonSession.isActive() || !session.dungeonSessionId().equals(DungeonSession.getSessionId())
                || !rollEffect(attacker, move, family)) return;
        if (!ActionBattleEffectApplicationGuard.allowsNewApplication(session, target, attacker.level().getGameTime())) return;
        ActionBattleEffectController.global().applyStatus(session.battleId(), attacker.getPokemon().getUuid(),
                target.getPokemon().getUuid(), status, attacker.level().getGameTime(),
                DEFAULT_DIRECT_STATUS_DURATION_TICKS);
    }

    private static boolean hasFamily(Move move, StatusFamily family) {
        ActionBattleMoveDescriptor descriptor = descriptor(null, move);
        return descriptor != null && statusRolls(descriptor).stream().anyMatch(roll -> roll.family() == family);
    }

    private static void applyDeclared(PokemonEntity attacker, PokemonEntity target, Move move,
                                      boolean hitSucceeded, StatusFamily family) {
        if (!hitSucceeded || attacker == null || target == null || move == null || attacker.level().isClientSide) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return;
        if (!ActionBattleEffectApplicationGuard.allowsNewApplication(session, target, attacker.level().getGameTime())) return;
        if (!rollEffect(attacker, move, family)) return;
        long currentTick = attacker.level().getGameTime();
        if (family == StatusFamily.FLINCH) {
            boolean applied = ActionBattleFlinchController.apply(session, target.getPokemon().getUuid(), currentTick,
                    FightOrFlightAdapter.makesContact(move));
            if (applied) DebugLog.log("[CobblemonNML] Action battle Flinch resolved. Battle=" + session.battleId()
                    + ", move=" + move.getName() + ", target=" + target.getPokemon().getUuid());
            return;
        }
        ActionBattleStatusApplication result = ActionBattleConfusionController.apply(session, target, currentTick);
        if (result != null) DebugLog.log("[CobblemonNML] Action battle Confusion effect resolved. Battle=" + session.battleId()
                + ", move=" + move.getName() + ", target=" + target.getPokemon().getUuid() + ", result=" + result);
    }

    private static boolean rollEffect(PokemonEntity attacker, Move move, StatusFamily family) {
        ActionBattleMoveDescriptor descriptor = descriptor(attacker, move);
        if (descriptor == null) return false;
        for (StatusRoll roll : statusRolls(descriptor)) {
            if (roll.family() != family) continue;
            if (roll.secondary() && hasAbility(attacker, "sheerforce")) continue;
            int chance = roll.chancePercent();
            if (roll.secondary() && hasAbility(attacker, "serenegrace")) chance = Math.min(100, chance * 2);
            if (chance >= 100 || attacker.getRandom().nextFloat() * 100.0F < chance) return true;
        }
        return false;
    }

    private static List<StatusRoll> statusRolls(ActionBattleMoveDescriptor descriptor) {
        if (descriptor == null) return List.of();
        List<StatusRoll> result = new ArrayList<>();
        addStatusRoll(result, descriptor.status(), 100, false);
        addStatusRoll(result, descriptor.volatileStatus(), 100, false);
        for (ActionBattleCanonicalMoveMetadata.SecondaryEffect secondary : descriptor.secondaries()) {
            addStatusRoll(result, secondary.status(), secondary.chancePercent(), true);
            addStatusRoll(result, secondary.volatileStatus(), secondary.chancePercent(), true);
        }
        return List.copyOf(result);
    }

    private static void addStatusRoll(List<StatusRoll> result, String token, int chance, boolean secondary) {
        StatusFamily family = StatusFamily.from(token);
        if (family != null) result.add(new StatusRoll(family, Math.max(0, Math.min(100, chance)), secondary));
    }

    private static boolean applyStatBatch(PokemonEntity attacker, PokemonEntity receiver, Map<String, Integer> boosts) {
        if (attacker == null || receiver == null || boosts == null || boosts.isEmpty()) return false;
        var translated = ActionBattleStatMoveMetadata.translateCanonical(boosts);
        if (translated.isEmpty()) return false;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(receiver.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return false;
        if (!ActionBattleEffectApplicationGuard.allowsNewApplication(session, receiver, attacker.level().getGameTime())) return false;
        return ActionBattleStatApplicationService.global().applyBatch(session.battleId(), receiver.getPokemon().getUuid(),
                translated, attacker.level().getGameTime(), ActionBattleStatSource.NORMAL_MOVE, true)
                .receiverStages().values().stream().anyMatch(value -> value != 0);
    }

    private static boolean passesSecondaryChance(PokemonEntity attacker, int chancePercent) {
        if (attacker == null || chancePercent <= 0 || hasAbility(attacker, "sheerforce")) return false;
        int chance = hasAbility(attacker, "serenegrace") ? Math.min(100, chancePercent * 2) : chancePercent;
        return chance >= 100 || attacker.getRandom().nextFloat() * 100.0F < chance;
    }

    private static boolean hasPositive(Map<String, Integer> boosts) {
        return boosts != null && boosts.values().stream().filter(Objects::nonNull).anyMatch(value -> value > 0);
    }

    private static ActionBattleMoveDescriptor descriptor(PokemonEntity attacker, Move move) {
        return move == null ? null : ActionBattleMoveMetadataResolver.resolve(attacker, move);
    }

    private static boolean hasAbility(PokemonEntity attacker, String abilityName) {
        return attacker != null && Objects.equals(attacker.getPokemon().getAbility().getName(), abilityName);
    }

    public enum StatTrigger { BEFORE_USE, ON_USE, ON_HIT }

    private record StatusRoll(StatusFamily family, int chancePercent, boolean secondary) {}

    private enum StatusFamily {
        CONFUSION,
        FLINCH,
        PARALYSIS,
        BURN,
        FREEZE,
        POISON,
        TOXIC;

        static StatusFamily from(String token) {
            for (StatusFamily value : values()) if (value.matches(token)) return value;
            return null;
        }

        boolean matches(String token) {
            String name = token == null ? "" : token.trim().toLowerCase(java.util.Locale.ROOT);
            return switch (this) {
                case FLINCH -> name.equals("flinch");
                case CONFUSION -> name.equals("confusion") || name.equals("confuse") || name.equals("confused");
                case PARALYSIS -> name.equals("par") || name.equals("paralysis") || name.equals("paralyze") || name.equals("paralyzed");
                case BURN -> name.equals("brn") || name.equals("burn") || name.equals("burned");
                case FREEZE -> name.equals("frz") || name.equals("freeze") || name.equals("frozen");
                case POISON -> name.equals("psn") || name.equals("poison") || name.equals("poisoned");
                case TOXIC -> name.equals("tox") || name.equals("toxic") || name.equals("badly_poisoned") || name.equals("badpoison");
            };
        }
    }
}

package net.epiac9.cobblemonnml.battle.action.compat;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.data.movedata.MoveData;
import me.rufia.fightorflight.data.movedata.movedatas.StatusEffectMoveData;
import net.epiac9.cobblemonnml.battle.action.ActionBattleFlinchController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleParalysisController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatusApplication;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.util.DebugLog;

import java.util.List;
import java.util.Objects;

public final class ActionBattleMoveEffectResolver {
    private static final float TRI_ATTACK_EFFECT_CHANCE = 0.0667F;
    private static final int STANDARD_STATUS_DURATION_TICKS = 120;
    private static final int POISON_STATUS_DURATION_TICKS = 360;

    private ActionBattleMoveEffectResolver() {}

    public static boolean hasStatusMetadata(Move move) {
        if (move == null) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries == null) return false;
        for (MoveData entry : entries) if (entry instanceof StatusEffectMoveData) return true;
        return false;
    }

    public static boolean hasSupportedBurnOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.BURN); }
    public static boolean hasSupportedFreezeOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.FREEZE); }
    public static boolean hasSupportedPoisonOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.POISON); }
    public static boolean hasSupportedFlinchOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.FLINCH); }
    public static boolean hasSupportedParalysisOnHitMetadata(Move move) { return hasSupportedOnHitMetadata(move, StatusFamily.PARALYSIS); }

    public static boolean isOwnedActionStatus(StatusEffectMoveData status) {
        if (!isOnHitTarget(status)) return false;
        String name = status.getName();
        return StatusFamily.BURN.matchesMetadata(name) || StatusFamily.FREEZE.matchesMetadata(name)
                || StatusFamily.POISON.matchesMetadata(name) || StatusFamily.FLINCH.matchesMetadata(name)
                || StatusFamily.PARALYSIS.matchesMetadata(name) || Objects.equals(name, "triattack");
    }

    public static void applyDeclaredBurnOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.BURN);
    }

    public static void applyDeclaredFreezeOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.FREEZE);
    }

    public static void applyDeclaredPoisonOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.POISON);
    }

    public static void applyDeclaredFlinchOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.FLINCH);
    }

    public static void applyDeclaredParalysisOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, StatusFamily.PARALYSIS);
    }

    private static boolean hasSupportedOnHitMetadata(Move move, StatusFamily family) {
        if (move == null) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries != null) {
            for (MoveData entry : entries) {
                if (!(entry instanceof StatusEffectMoveData status) || !isOnHitTarget(status)) continue;
                if (family.matchesMetadata(status.getName())) return true;
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
        int strength = rollEffect(attacker, move, family);
        if (strength <= 0) return;
        long currentTick = attacker.level().getGameTime();
        if (family == StatusFamily.FLINCH) {
            boolean applied = ActionBattleFlinchController.apply(
                    session, target.getPokemon().getUuid(), currentTick, FightOrFlightAdapter.makesContact(move));
            if (applied) DebugLog.log("[CobblemonNML] Action battle Flinch resolved. Battle=" + session.battleId()
                    + ", move=" + move.getName() + ", target=" + target.getPokemon().getUuid());
            return;
        }
        int baseDuration = family.baseDurationTicks();
        ActionBattleProtectController.EffectInterception interception = ActionBattleProtectController.global()
                .interceptTimedEffect(session.battleId(), target.getPokemon().getUuid(), currentTick, family.effectId(), baseDuration);
        if (!interception.allowed()) return;
        float durationMultiplier = interception.durationTicks() / (float) baseDuration;
        ActionBattleStatusApplication result = switch (family) {
            case BURN -> ActionBattleEffectController.global().applyBurnCapableHit(
                    session.battleId(), target.getPokemon().getUuid(), currentTick, durationMultiplier);
            case FREEZE -> ActionBattleEffectController.global().applyFreezeCapableHit(
                    session.battleId(), target.getPokemon().getUuid(), currentTick, durationMultiplier);
            case POISON -> ActionBattleEffectController.global().applyPoison(
                    session.battleId(), target.getPokemon().getUuid(), strength, currentTick, durationMultiplier);
            case PARALYSIS -> {
                ActionBattleParalysisController.ApplicationResult paralysis = ActionBattleParalysisController.apply(session, target, currentTick, durationMultiplier, FightOrFlightAdapter.makesContact(move));
                yield paralysis != null ? paralysis.application() : null;
            }
            case FLINCH -> null;
        };
        if (result == null) return;
        String detail = family == StatusFamily.POISON ? ", strength=" + strength : "";
        DebugLog.log("[CobblemonNML] Action battle " + family.logName() + " effect resolved. Battle=" + session.battleId()
                + ", move=" + move.getName() + ", target=" + target.getPokemon().getUuid() + detail + ", result=" + result);
    }

    private static int rollEffect(PokemonEntity attacker, Move move, StatusFamily family) {
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        boolean foundOwnedMetadata = false;
        if (entries != null) {
            for (MoveData entry : entries) {
                if (!(entry instanceof StatusEffectMoveData status) || !isOnHitTarget(status) || !family.matchesMetadata(status.getName())) continue;
                foundOwnedMetadata = true;
                if (status.canActivateSheerForce() && hasAbility(attacker, "sheerforce")) continue;
                float chance = family.metadataChance(status);
                if (hasAbility(attacker, "serenegrace")) chance *= 2.0F;
                if (chance > attacker.getRandom().nextFloat()) return family.metadataStrength(status.getName());
            }
        }
        if (foundOwnedMetadata) return 0;
        for (ActionBattleMoveEffectData fallback : ActionBattleMoveEffectDataManager.getAll(move.getName())) {
            if (!family.matchesFallback(fallback)) continue;
            if (fallback.secondary() && hasAbility(attacker, "sheerforce")) continue;
            float chance = family.fallbackChance(fallback);
            if (fallback.secondary() && hasAbility(attacker, "serenegrace")) chance *= 2.0F;
            if (chance > attacker.getRandom().nextFloat()) return family.fallbackStrength(fallback);
        }
        return 0;
    }

    private static boolean isOnHitTarget(StatusEffectMoveData status) {
        return status != null && status.isOnHit() && Objects.equals(status.getTarget(), "target");
    }

    private static boolean hasAbility(PokemonEntity attacker, String abilityName) {
        return attacker != null && Objects.equals(attacker.getPokemon().getAbility().getName(), abilityName);
    }

    private enum StatusFamily {
        BURN("burn", "Burn", STANDARD_STATUS_DURATION_TICKS),
        FREEZE("freeze", "Freeze", STANDARD_STATUS_DURATION_TICKS),
        POISON("poison", "Poison/Toxic", POISON_STATUS_DURATION_TICKS),
        PARALYSIS("paralysis", "Paralysis", STANDARD_STATUS_DURATION_TICKS),
        FLINCH("flinch", "Flinch", 0);

        private final String effectId;
        private final String logName;
        private final int baseDurationTicks;

        StatusFamily(String effectId, String logName, int baseDurationTicks) {
            this.effectId = effectId;
            this.logName = logName;
            this.baseDurationTicks = baseDurationTicks;
        }

        boolean matchesMetadata(String name) {
            return switch (this) {
                case BURN -> isBurnName(name) || Objects.equals(name, "triattack");
                case FREEZE -> isFreezeName(name) || Objects.equals(name, "triattack");
                case POISON -> isPoisonName(name) || isToxicName(name);
                case PARALYSIS -> isParalysisName(name) || Objects.equals(name, "triattack");
                case FLINCH -> isFlinchName(name);
            };
        }

        boolean matchesFallback(ActionBattleMoveEffectData fallback) {
            return switch (this) {
                case BURN -> fallback.isSupportedBurnOnHit();
                case FREEZE -> fallback.isSupportedFreezeOnHit();
                case POISON -> fallback.isSupportedPoisonOnHit();
                case PARALYSIS -> fallback.isSupportedParalysisOnHit();
                case FLINCH -> fallback.isSupportedFlinchOnHit();
            };
        }

        float metadataChance(StatusEffectMoveData status) {
            return Objects.equals(status.getName(), "triattack") ? TRI_ATTACK_EFFECT_CHANCE : status.getChance();
        }

        float fallbackChance(ActionBattleMoveEffectData fallback) {
            return fallback.isTriAttack() ? TRI_ATTACK_EFFECT_CHANCE : fallback.chance();
        }

        int metadataStrength(String effectName) {
            return this == POISON && isToxicName(effectName) ? 2 : 1;
        }

        int fallbackStrength(ActionBattleMoveEffectData fallback) {
            return this == POISON ? fallback.poisonProgressionStrength() : 1;
        }

        String effectId() { return effectId; }
        String logName() { return logName; }
        int baseDurationTicks() { return baseDurationTicks; }
    }

    private static boolean isBurnName(String name) {
        return Objects.equals(name, "burn") || Objects.equals(name, "brn");
    }

    private static boolean isFreezeName(String name) {
        return Objects.equals(name, "freeze") || Objects.equals(name, "frozen") || Objects.equals(name, "frz");
    }

    private static boolean isPoisonName(String name) {
        return Objects.equals(name, "poison") || Objects.equals(name, "psn");
    }

    private static boolean isFlinchName(String name) {
        return Objects.equals(name, "flinch");
    }

    private static boolean isParalysisName(String name) {
        return Objects.equals(name, "paralysis") || Objects.equals(name, "paralyze")
                || Objects.equals(name, "paralyzed") || Objects.equals(name, "par");
    }

    private static boolean isToxicName(String name) {
        return Objects.equals(name, "toxic") || Objects.equals(name, "badly_poison")
                || Objects.equals(name, "badlypoisoned") || Objects.equals(name, "tox");
    }
}

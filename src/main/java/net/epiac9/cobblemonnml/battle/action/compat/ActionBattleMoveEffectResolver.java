package net.epiac9.cobblemonnml.battle.action.compat;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.data.movedata.MoveData;
import me.rufia.fightorflight.data.movedata.movedatas.StatusEffectMoveData;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatusApplication;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.util.DebugLog;

import java.util.List;
import java.util.Objects;

public final class ActionBattleMoveEffectResolver {
    private static final float TRI_ATTACK_EFFECT_CHANCE = 0.0667F;

    private ActionBattleMoveEffectResolver() {}

    public static boolean hasStatusMetadata(Move move) {
        if (move == null) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries == null) return false;
        for (MoveData entry : entries) if (entry instanceof StatusEffectMoveData) return true;
        return false;
    }

    public static boolean hasSupportedBurnOnHitMetadata(Move move) {
        return hasSupportedOnHitMetadata(move, true);
    }

    public static boolean hasSupportedFreezeOnHitMetadata(Move move) {
        return hasSupportedOnHitMetadata(move, false);
    }

    public static boolean hasSupportedPoisonOnHitMetadata(Move move) {
        if (move == null) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries != null) {
            for (MoveData entry : entries) {
                if (!(entry instanceof StatusEffectMoveData status) || !status.isOnHit() || !Objects.equals(status.getTarget(), "target")) continue;
                if (isPoisonName(status.getName()) || isToxicName(status.getName())) return true;
            }
        }
        ActionBattleMoveEffectData fallback = ActionBattleMoveEffectDataManager.get(move.getName());
        return fallback != null && fallback.isSupportedPoisonOnHit();
    }

    public static boolean isOwnedActionStatus(StatusEffectMoveData status) {
        if (status == null || !status.isOnHit() || !Objects.equals(status.getTarget(), "target")) return false;
        String name = status.getName();
        return isBurnName(name) || isFreezeName(name) || isPoisonName(name) || isToxicName(name) || Objects.equals(name, "triattack");
    }

    public static void applyDeclaredBurnOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, true);
    }

    public static void applyDeclaredFreezeOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        applyDeclared(attacker, target, move, hitSucceeded, false);
    }

    public static void applyDeclaredPoisonOnHit(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded) {
        if (!hitSucceeded || attacker == null || target == null || move == null || attacker.level().isClientSide) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return;
        int strength = rollPoisonEffect(attacker, move);
        if (strength == 0) return;
        long currentTick = attacker.level().getGameTime();
        ActionBattleProtectController.EffectInterception interception = ActionBattleProtectController.global()
                .interceptTimedEffect(session.battleId(), target.getPokemon().getUuid(), currentTick, "poison", 360);
        if (!interception.allowed()) return;
        float durationMultiplier = interception.durationTicks() / 360.0F;
        ActionBattleStatusApplication result = ActionBattleEffectController.global().applyPoison(
                session.battleId(), target.getPokemon().getUuid(), strength, currentTick, durationMultiplier);
        if (result != null) {
            DebugLog.log("[CobblemonNML] Action battle Poison/Toxic effect resolved. Battle=" + session.battleId()
                    + ", move=" + move.getName() + ", target=" + target.getPokemon().getUuid() + ", strength=" + strength + ", result=" + result);
        }
    }

    private static boolean hasSupportedOnHitMetadata(Move move, boolean burn) {
        if (move == null) return false;
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        if (entries != null) {
            for (MoveData entry : entries) {
                if (!(entry instanceof StatusEffectMoveData status) || !status.isOnHit() || !Objects.equals(status.getTarget(), "target")) continue;
                String name = status.getName();
                if (burn ? isBurnName(name) || Objects.equals(name, "triattack") : isFreezeName(name) || Objects.equals(name, "triattack")) return true;
            }
        }
        ActionBattleMoveEffectData fallback = ActionBattleMoveEffectDataManager.get(move.getName());
        return fallback != null && (burn ? fallback.isSupportedBurnOnHit() : fallback.isSupportedFreezeOnHit());
    }

    private static void applyDeclared(PokemonEntity attacker, PokemonEntity target, Move move, boolean hitSucceeded, boolean burn) {
        if (!hitSucceeded || attacker == null || target == null || move == null || attacker.level().isClientSide) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return;
        if (!rollEffect(attacker, move, burn)) return;
        long currentTick = attacker.level().getGameTime();
        String effectId = burn ? "burn" : "freeze";
        ActionBattleProtectController.EffectInterception interception = ActionBattleProtectController.global()
                .interceptTimedEffect(session.battleId(), target.getPokemon().getUuid(), currentTick, effectId, 120);
        if (!interception.allowed()) return;
        float durationMultiplier = interception.durationTicks() / 120.0F;
        ActionBattleStatusApplication result = burn
                ? ActionBattleEffectController.global().applyBurnCapableHit(session.battleId(), target.getPokemon().getUuid(), currentTick, durationMultiplier)
                : ActionBattleEffectController.global().applyFreezeCapableHit(session.battleId(), target.getPokemon().getUuid(), currentTick, durationMultiplier);
        if (result != null) {
            DebugLog.log("[CobblemonNML] Action battle " + (burn ? "Burn" : "Freeze") + " effect resolved. Battle=" + session.battleId()
                    + ", move=" + move.getName() + ", target=" + target.getPokemon().getUuid() + ", result=" + result);
        }
    }

    private static boolean rollEffect(PokemonEntity attacker, Move move, boolean burn) {
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        boolean foundOwnedMetadata = false;
        if (entries != null) {
            for (MoveData entry : entries) {
                if (!(entry instanceof StatusEffectMoveData status) || !status.isOnHit() || !Objects.equals(status.getTarget(), "target")) continue;
                String effectName = status.getName();
                boolean matching = burn ? isBurnName(effectName) || Objects.equals(effectName, "triattack") : isFreezeName(effectName) || Objects.equals(effectName, "triattack");
                if (!matching) continue;
                foundOwnedMetadata = true;
                if (status.canActivateSheerForce() && Objects.equals(attacker.getPokemon().getAbility().getName(), "sheerforce")) continue;
                float chance = status.getChance();
                if (Objects.equals(effectName, "triattack")) chance = TRI_ATTACK_EFFECT_CHANCE;
                if (Objects.equals(attacker.getPokemon().getAbility().getName(), "serenegrace")) chance *= 2.0F;
                if (!(chance > attacker.getRandom().nextFloat())) continue;
                return true;
            }
        }
        if (foundOwnedMetadata) return false;
        ActionBattleMoveEffectData fallback = ActionBattleMoveEffectDataManager.get(move.getName());
        if (fallback == null || !(burn ? fallback.isSupportedBurnOnHit() : fallback.isSupportedFreezeOnHit())) return false;
        if (fallback.secondary() && Objects.equals(attacker.getPokemon().getAbility().getName(), "sheerforce")) return false;
        float chance = fallback.chance();
        if (fallback.isTriAttack()) chance = TRI_ATTACK_EFFECT_CHANCE;
        if (fallback.secondary() && Objects.equals(attacker.getPokemon().getAbility().getName(), "serenegrace")) chance *= 2.0F;
        return chance > attacker.getRandom().nextFloat();
    }


    private static int rollPoisonEffect(PokemonEntity attacker, Move move) {
        List<MoveData> entries = MoveData.moveData.get(move.getName());
        boolean foundOwnedMetadata = false;
        if (entries != null) {
            for (MoveData entry : entries) {
                if (!(entry instanceof StatusEffectMoveData status) || !status.isOnHit() || !Objects.equals(status.getTarget(), "target")) continue;
                String effectName = status.getName();
                int strength = isToxicName(effectName) ? 2 : isPoisonName(effectName) ? 1 : 0;
                if (strength == 0) continue;
                foundOwnedMetadata = true;
                if (status.canActivateSheerForce() && Objects.equals(attacker.getPokemon().getAbility().getName(), "sheerforce")) continue;
                float chance = status.getChance();
                if (Objects.equals(attacker.getPokemon().getAbility().getName(), "serenegrace")) chance *= 2.0F;
                if (chance > attacker.getRandom().nextFloat()) return strength;
            }
        }
        if (foundOwnedMetadata) return 0;
        ActionBattleMoveEffectData fallback = ActionBattleMoveEffectDataManager.get(move.getName());
        if (fallback == null || !fallback.isSupportedPoisonOnHit()) return 0;
        if (fallback.secondary() && Objects.equals(attacker.getPokemon().getAbility().getName(), "sheerforce")) return 0;
        float chance = fallback.chance();
        if (fallback.secondary() && Objects.equals(attacker.getPokemon().getAbility().getName(), "serenegrace")) chance *= 2.0F;
        return chance > attacker.getRandom().nextFloat() ? fallback.poisonProgressionStrength() : 0;
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

    private static boolean isToxicName(String name) {
        return Objects.equals(name, "toxic") || Objects.equals(name, "badly_poison") || Objects.equals(name, "badlypoisoned") || Objects.equals(name, "tox");
    }
}

package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleSleepState;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleSleepWakeRules;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleStatusParticleController;
import net.epiac9.cobblemonnml.util.DebugLog;

import java.util.UUID;

public final class ActionBattleSleepController {
    private ActionBattleSleepController() {}

    public static void tickPokemon(ActionBattleSession session, PokemonEntity target, long currentTick) {
        if (session == null || target == null || target.isRemoved() || currentTick < 0L) return;
        UUID pokemonUUID = target.getPokemon().getUuid();
        if (ActionBattleEffectController.global().tickSleepState(session.battleId(), pokemonUUID, currentTick) == ActionBattleSleepState.NaturalWakeResult.WOKE_NATURALLY) {
            ActionBattlePersistentController.global().onSleepEnded(session.battleId(), pokemonUUID);
            if (target.level() instanceof net.minecraft.server.level.ServerLevel level) ActionBattleStatusParticleController.emitWakeBurst(level, target);
            DebugLog.log("[CobblemonNML] Action battle Pokemon woke naturally. Battle=" + session.battleId() + ", pokemon=" + pokemonUUID);
        }
    }

    public static boolean isSleeping(ActionBattleSession session, UUID pokemonUUID, long currentTick) {
        return session != null && pokemonUUID != null && ActionBattleEffectController.global().hasStatus(session.battleId(), pokemonUUID, ActionBattleStatus.SLEEP, currentTick);
    }

    public static WakePlan planDamagingWake(ActionBattleSession session, PokemonEntity target, long currentTick, boolean ranged, boolean explicitWake) {
        if (session == null || target == null || currentTick < 0L || !isSleeping(session, target.getPokemon().getUuid(), currentTick)) return WakePlan.NONE;
        boolean wakes = explicitWake || ActionBattleSleepWakeRules.rollWake(ranged, target.getRandom().nextFloat());
        return wakes ? new WakePlan(true, ActionBattleSleepWakeRules.damageMultiplier(true, explicitWake), explicitWake) : WakePlan.NONE;
    }

    public static boolean applyWakeDamageAndWake(ActionBattleSession session, PokemonEntity target, long currentTick, int beforeHp, WakePlan plan) {
        if (session == null || target == null || plan == null || !plan.wakesTarget()) return false;
        int afterHp = target.getPokemon().getCurrentHealth();
        int actualDamage = Math.max(0, beforeHp - afterHp);
        if (actualDamage <= 0) return false;
        int boostedDamage = Math.max(actualDamage, Math.round(actualDamage * plan.damageMultiplier()));
        int bonusDamage = Math.max(0, boostedDamage - actualDamage);
        if (bonusDamage > 0) target.getPokemon().setCurrentHealth(Math.max(0, afterHp - bonusDamage));
        boolean woke = ActionBattleEffectController.global().wakeSleep(session.battleId(), target.getPokemon().getUuid(), currentTick);
        if (woke) {
            ActionBattlePersistentController.global().onSleepEnded(session.battleId(), target.getPokemon().getUuid());
            if (target.level() instanceof net.minecraft.server.level.ServerLevel level) ActionBattleStatusParticleController.emitWakeBurst(level, target);
            DebugLog.log("[CobblemonNML] Action battle Pokemon woke from ability damage. Battle=" + session.battleId() + ", pokemon=" + target.getPokemon().getUuid()
                    + ", explicitWake=" + plan.explicitWake() + ", multiplier=" + plan.damageMultiplier());
        }
        return woke;
    }

    public record WakePlan(boolean wakesTarget, float damageMultiplier, boolean explicitWake) {
        public static final WakePlan NONE = new WakePlan(false, 1.0F, false);
    }
}

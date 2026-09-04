package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleSleepState;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleSleepWakeRules;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleStatusParticleController;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.util.RandomSource;

import java.util.UUID;

public final class ActionBattleSleepController {
    private ActionBattleSleepController() {}

    public static void tickPokemon(ActionBattleSession session, PokemonEntity target, long currentTick) {
        if (session == null || target == null || target.isRemoved() || currentTick < 0L) return;
        UUID pokemonUUID = target.getPokemon().getUuid();
        if (ActionBattleEffectController.global().tickSleepState(session.dungeonSessionId(), pokemonUUID, currentTick) == ActionBattleSleepState.NaturalWakeResult.WOKE_NATURALLY) {
            ActionBattlePersistentController.global().onSleepEnded(session.battleId(), pokemonUUID);
            if (target.level() instanceof net.minecraft.server.level.ServerLevel level) ActionBattleStatusParticleController.emitWakeBurst(level, target);
            DebugLog.log("[CobblemonNML] Action battle Pokemon woke naturally. Battle=" + session.battleId() + ", pokemon=" + pokemonUUID);
        }
    }

    public static boolean isSleeping(ActionBattleSession session, UUID pokemonUUID, long currentTick) {
        return session != null && pokemonUUID != null && ActionBattleEffectController.global().hasStatus(session.dungeonSessionId(), pokemonUUID, ActionBattleStatus.SLEEP, currentTick);
    }

    public static int rollSleepDurationTicks(RandomSource random) {
        if (random == null) throw new IllegalArgumentException("Sleep duration random source cannot be null.");
        return ActionBattleSleepWakeRules.sleepDurationTicksFromRoll(random.nextInt(7));
    }

    public static WakePlan planDamagingWake(boolean sleeping, boolean fairyMove, boolean explicitWake) {
        return sleeping
                ? new WakePlan(true, ActionBattleSleepWakeRules.damageMultiplier(true, fairyMove, explicitWake), explicitWake)
                : WakePlan.NONE;
    }

    public static WakePlan planDamagingWake(ActionBattleSession session, PokemonEntity target, long currentTick, boolean ranged, boolean explicitWake) {
        boolean sleeping = session != null && target != null && currentTick >= 0L
                && isSleeping(session, target.getPokemon().getUuid(), currentTick);
        return planDamagingWake(sleeping, false, explicitWake);
    }

    public static boolean applyWakeDamageAndWake(ActionBattleSession session, PokemonEntity target, long currentTick, int beforeHp, WakePlan plan) {
        if (session == null || target == null || !shouldWakeAfterDamage(plan, beforeHp,
                target.getPokemon().getCurrentHealth())) return false;
        boolean woke = ActionBattleEffectController.global().wakeSleep(session.dungeonSessionId(), target.getPokemon().getUuid(), currentTick);
        if (woke) {
            ActionBattlePersistentController.global().onSleepEnded(session.battleId(), target.getPokemon().getUuid());
            if (target.level() instanceof net.minecraft.server.level.ServerLevel level) ActionBattleStatusParticleController.emitWakeBurst(level, target);
            DebugLog.log("[CobblemonNML] Action battle Pokemon woke from ability damage. Battle=" + session.battleId() + ", pokemon=" + target.getPokemon().getUuid()
                    + ", explicitWake=" + plan.explicitWake() + ", multiplier=" + plan.damageMultiplier());
        }
        return woke;
    }

    public static boolean shouldWakeAfterDamage(WakePlan plan, int beforeHp, int afterHp) {
        return plan != null && plan.wakesTarget() && beforeHp > afterHp;
    }

    public record WakePlan(boolean wakesTarget, float damageMultiplier, boolean explicitWake) {
        public static final WakePlan NONE = new WakePlan(false, 1.0F, false);
    }
}

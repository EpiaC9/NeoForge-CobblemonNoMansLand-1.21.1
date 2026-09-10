package net.epiac9.cobblemonnml.battle.action.effect.status;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSleepController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** Generic Drowsy lifecycle. Moves decide when to call {@link #apply}; no type applies it automatically. */
public final class ActionBattleDrowsyController {
    private static UproarCompletionHook uproarCompletionHook = (pokemonUUID, durationTicks) -> {};

    private ActionBattleDrowsyController() {}

    public static boolean apply(ActionBattleSession session, PokemonEntity target, long currentTick,
                                double penetrationRoll) {
        if (session == null || target == null || target.isRemoved() || currentTick < 0L
                || !ActionBattleEffectApplicationGuard.allowsNewApplication(session, target, currentTick)) return false;
        UUID pokemonId = target.getPokemon().getUuid();
        double chance = ActionBattleProtectController.global().effectPenetrationChance(
                session.battleId(), pokemonId, currentTick);
        if (!passesPenetration(chance, penetrationRoll)) return false;
        ActionBattleDrowsyTracker.CompletionRoute route = ActionBattleTypeMechanicIdentity.hasActualType(
                target.getPokemon(), "dragon") ? ActionBattleDrowsyTracker.CompletionRoute.DRAGON_UPROAR
                : ActionBattleDrowsyTracker.CompletionRoute.SLEEP;
        return ActionBattleEffectController.global().applyDrowsy(
                session.dungeonSessionId(), pokemonId, currentTick, route);
    }

    public static void tickSession(ServerLevel level, UUID dungeonSessionId) {
        if (level == null || dungeonSessionId == null) return;
        long currentTick = level.getGameTime();
        ActionBattleEffectController effects = ActionBattleEffectController.global();
        for (UUID pokemonId : effects.trackedPokemonIds(dungeonSessionId)) {
            var view = effects.drowsyView(dungeonSessionId, pokemonId, currentTick);
            if (view.isEmpty() || view.orElseThrow().remainingTicks() > 0L) continue;
            int duration = ActionBattleSleepController.rollSleepDurationTicks(level.getRandom());
            ActionBattleDrowsyTracker.CompletionRoute route = effects.pendingDrowsyCompletionRoute(
                    dungeonSessionId, pokemonId);
            if (!effects.completeDrowsy(dungeonSessionId, pokemonId, currentTick, duration, route)) continue;
            if (route == ActionBattleDrowsyTracker.CompletionRoute.SLEEP) {
                ActionBattleSleepController.applySleep(level, dungeonSessionId, pokemonId, currentTick, duration);
            } else {
                uproarCompletionHook.onDragonUproar(pokemonId, duration);
            }
        }
    }

    public static void onPokemonRecalled(ActionBattleSession session, UUID pokemonId, long currentTick) {
        if (session != null && pokemonId != null) ActionBattleEffectController.global().onPokemonRecalled(
                session.dungeonSessionId(), pokemonId, currentTick);
    }

    public static boolean passesPenetration(double chance, double roll) {
        return Double.isFinite(chance) && Double.isFinite(roll) && chance > 0.0D
                && roll >= 0.0D && roll < Math.min(1.0D, chance);
    }

    public static void setUproarCompletionHook(UproarCompletionHook hook) {
        uproarCompletionHook = hook != null ? hook : (pokemonUUID, durationTicks) -> {};
    }

    @FunctionalInterface
    public interface UproarCompletionHook {
        void onDragonUproar(UUID pokemonUUID, int durationTicks);
    }
}

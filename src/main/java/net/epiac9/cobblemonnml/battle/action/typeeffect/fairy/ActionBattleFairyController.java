package net.epiac9.cobblemonnml.battle.action.typeeffect.fairy;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceController;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSleepController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.minecraft.server.level.ServerLevel;

import java.util.Locale;
import java.util.UUID;

public final class ActionBattleFairyController {
    private static UproarCompletionHook uproarCompletionHook = (pokemonUUID, durationTicks) -> {};
    private ActionBattleFairyController() {}

    public static boolean onSuccessfulEnemyTargetingMove(PokemonEntity attacker, PokemonEntity target, Move move) {
        if (attacker == null || target == null || move == null || !isQualifyingAutomaticDrowsyMove(move)) return false;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null || !battleId.equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))) return false;
        return applyDrowsy(target, battleId, target.level().getGameTime(), target.getRandom().nextDouble());
    }

    public static boolean applyDrowsy(PokemonEntity target, UUID battleId, long currentTick, double penetrationRoll) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId == null || target == null || currentTick < 0L) return false;
        Pokemon pokemon = target.getPokemon();
        if (!canReceiveDrowsy(hasType(pokemon, "steel"))) return false;
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        controller.guardSession(sessionId);
        if (controller.hasActiveDrowsy(sessionId, pokemon.getUuid(), currentTick)) return false;
        double chance = ActionBattleProtectController.global()
                .effectPenetrationChance(battleId, pokemon.getUuid(), currentTick);
        if (!passesPenetration(chance, penetrationRoll)) return false;
        ActionBattleDrowsyTracker.CompletionRoute route = completionRoute(
                hasType(pokemon, "dragon"), hasType(pokemon, "fairy"));
        return controller.applyDrowsy(sessionId, pokemon.getUuid(), currentTick, route);
    }

    public static void tickSession(ServerLevel level, UUID sessionId) {
        if (level == null || sessionId == null) return;
        long currentTick = level.getGameTime();
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        for (UUID pokemonUUID : controller.trackedPokemonIds(sessionId)) {
            var view = controller.drowsyView(sessionId, pokemonUUID, currentTick);
            if (view.isEmpty() || view.orElseThrow().remainingTicks() > 0L) continue;
            int duration = ActionBattleSleepController.rollSleepDurationTicks(level.getRandom());
            ActionBattleDrowsyTracker.CompletionRoute route = controller.pendingDrowsyCompletionRoute(sessionId, pokemonUUID);
            if (!controller.completeDrowsy(sessionId, pokemonUUID, currentTick, duration, route)) continue;
            if (route == ActionBattleDrowsyTracker.CompletionRoute.SLEEP) {
                ActionBattleEffectController.global().beginSleep(sessionId, pokemonUUID, currentTick, duration);
            } else if (route == ActionBattleDrowsyTracker.CompletionRoute.DRAGON_UPROAR) {
                uproarCompletionHook.onDragonUproar(pokemonUUID, duration);
            }
        }
        controller.tickSession(sessionId, currentTick);
    }

    public static void onPokemonRecalled(UUID pokemonUUID, long currentTick) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId != null && pokemonUUID != null) {
            ActionBattleTypeEffectController.global().cancelDrowsyOnRecall(sessionId, pokemonUUID, currentTick);
        }
    }

    public static void setUproarCompletionHook(UproarCompletionHook hook) {
        uproarCompletionHook = hook != null ? hook : (pokemonUUID, durationTicks) -> {};
    }

    public static boolean isQualifyingAutomaticDrowsyMove(Move move) {
        return move != null && FightOrFlightAdapter.movePower(move) == 0
                && move.getType() != null && "fairy".equals(normalize(move.getType().getName()))
                && isEnemyTargetCategory(FightOrFlightAdapter.moveTargetCategory(move));
    }

    public static boolean canReceiveDrowsy(boolean steelTyped) { return !steelTyped; }

    public static ActionBattleDrowsyTracker.CompletionRoute completionRoute(boolean dragonTyped, boolean fairyTyped) {
        if (dragonTyped) return ActionBattleDrowsyTracker.CompletionRoute.DRAGON_UPROAR;
        if (fairyTyped) return ActionBattleDrowsyTracker.CompletionRoute.FAIRY_SPDEF;
        return ActionBattleDrowsyTracker.CompletionRoute.SLEEP;
    }

    public static boolean passesPenetration(double chance, double roll) {
        return ActionBattleIceController.passesPenetration(chance, roll);
    }

    public static boolean isEnemyTargetCategory(String category) {
        String normalized = normalize(category).replace("_", "").replace("-", "");
        return normalized.equals("normal") || normalized.equals("adjacentpokemon")
                || normalized.equals("any") || normalized.equals("alladjacentfoes")
                || normalized.equals("randomnormal");
    }

    public static boolean hasType(Pokemon pokemon, String expected) {
        if (pokemon == null || expected == null) return false;
        String normalized = normalize(expected);
        if (pokemon.getPrimaryType() != null && normalized.equals(normalize(pokemon.getPrimaryType().getName()))) return true;
        return pokemon.getSecondaryType() != null && normalized.equals(normalize(pokemon.getSecondaryType().getName()));
    }

    private static String normalize(String value) {
        return value != null ? value.toLowerCase(Locale.ROOT).replace(" ", "") : "";
    }

    @FunctionalInterface
    public interface UproarCompletionHook {
        void onDragonUproar(UUID pokemonUUID, int durationTicks);
    }
}

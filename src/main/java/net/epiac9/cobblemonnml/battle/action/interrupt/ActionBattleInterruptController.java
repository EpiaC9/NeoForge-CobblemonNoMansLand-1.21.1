package net.epiac9.cobblemonnml.battle.action.interrupt;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleAerialMoveController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleCommandController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattlePropulsionController;
import net.epiac9.cobblemonnml.util.DebugLog;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleInterruptController {
    private static final Map<Key, ActionBattleInterruptState> STATES = new HashMap<>();

    private ActionBattleInterruptController() {}

    public static boolean apply(ActionBattleSession session, PokemonEntity target, long durationTicks, long currentTick) {
        if (session == null || target == null || target.isRemoved() || durationTicks <= 0L) return false;
        UUID pokemonId = target.getPokemon().getUuid();
        ActionBattleCommandController.Side side = ActionBattleCommandController.sideOf(session, pokemonId);
        if (side == null) return false;
        boolean cancelled = ActionBattleCommandController.cancelPendingOrders(session, pokemonId,
                ActionBattleCommandController.InterruptReason.EXPLICIT_INTERRUPT);
        session.clearLastAcceptedMoveHereDirective(pokemonId);
        ActionBattleCommandController.onExplicitInterrupt(pokemonId);
        target.getNavigation().stop();
        ActionBattleAerialMoveController.clearPokemon(session, pokemonId);
        ActionBattlePropulsionController.clearPokemon(session, pokemonId);
        STATES.computeIfAbsent(new Key(session.battleId(), pokemonId), ignored -> new ActionBattleInterruptState())
                .apply(currentTick, durationTicks);
        DebugLog.log("[CobblemonNML] Interrupt applied. Battle=" + session.battleId() + ", target="
                + pokemonId + ", duration=" + durationTicks + ", cancelledCommand=" + cancelled);
        return true;
    }

    public static boolean isActive(UUID battleId, UUID pokemonId, long currentTick) {
        Key key = new Key(battleId, pokemonId);
        ActionBattleInterruptState state = battleId != null && pokemonId != null ? STATES.get(key) : null;
        if (state == null) return false;
        boolean active = state.isActive(currentTick);
        if (!active) STATES.remove(key);
        return active;
    }

    public static void clearPokemon(UUID battleId, UUID pokemonId) { if (battleId != null && pokemonId != null) STATES.remove(new Key(battleId, pokemonId)); }
    public static void clearBattle(UUID battleId) { if (battleId != null) STATES.keySet().removeIf(key -> key.battleId().equals(battleId)); }
    public static void clearAll() { STATES.clear(); }

    private record Key(UUID battleId, UUID pokemonId) {}
}

package net.epiac9.cobblemonnml.battle.action.typeeffect.fighting;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleFightingGuardRuntime {
    public static final long GUARD_TICKS = 10L;
    private static final Map<Key, Long> END_TICKS = new HashMap<>();
    private ActionBattleFightingGuardRuntime() {}

    public static void onOwnedActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.battleId() == null || context.pokemon() == null
                || context.moveCategory() != ActionBattleTypeMechanicActionContext.MoveCategory.MELEE) return;
        long tick = context.pokemon().level().getGameTime();
        END_TICKS.put(new Key(context.battleId(), context.pokemon().getPokemon().getUuid()),
                ActionBattleTiming.safeAdd(tick, GUARD_TICKS));
    }

    public static boolean blocks(UUID battleId, UUID pokemonId, boolean meleeOrProjectile, long tick) {
        Long end = battleId != null && pokemonId != null ? END_TICKS.get(new Key(battleId, pokemonId)) : null;
        if (end == null || tick >= end) {
            if (end != null) END_TICKS.remove(new Key(battleId, pokemonId));
            return false;
        }
        return meleeOrProjectile;
    }

    public static void clearBattle(UUID battleId) { END_TICKS.keySet().removeIf(key -> key.battleId().equals(battleId)); }
    public static void clearAll() { END_TICKS.clear(); }
    private record Key(UUID battleId, UUID pokemonId) {}
}

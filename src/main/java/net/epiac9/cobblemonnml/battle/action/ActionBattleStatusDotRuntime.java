package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.health.ActionBattleDotController;
import net.epiac9.cobblemonnml.battle.action.health.ActionBattleDotSpec;
import net.epiac9.cobblemonnml.battle.action.health.ActionBattleDotTick;
import net.epiac9.cobblemonnml.battle.action.health.ActionBattleStatusDotRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleStatusDotRuntime {
    private static final ActionBattleDotController DOTS = new ActionBattleDotController();
    private static final Map<Key, ActiveStatusDot> ACTIVE = new HashMap<>();

    private ActionBattleStatusDotRuntime() {}

    public static void onStatusApplied(UUID battleId, UUID sourcePokemonUUID, UUID targetPokemonUUID,
                                       ActionBattleStatus status, long currentTick, long durationTicks) {
        ActionBattleDotSpec spec = ActionBattleStatusDotRules.spec(status, durationTicks);
        if (battleId == null || targetPokemonUUID == null || spec == null || currentTick < 0L) return;
        Key key = new Key(battleId, targetPokemonUUID, status);
        ActiveStatusDot previous = ACTIVE.remove(key);
        if (previous != null) DOTS.stop(battleId, previous.handleId());
        UUID handleId = DOTS.start(battleId, sourcePokemonUUID, targetPokemonUUID, spec, currentTick);
        ACTIVE.put(key, new ActiveStatusDot(handleId, sourcePokemonUUID,
                ActionBattleTiming.safeAdd(currentTick, durationTicks)));
    }

    public static void onPoisonConvertedToToxic(UUID battleId, UUID targetPokemonUUID,
                                                 long currentTick, long remainingTicks) {
        if (battleId == null || targetPokemonUUID == null || currentTick < 0L || remainingTicks <= 0L) return;
        Key poisonKey = new Key(battleId, targetPokemonUUID, ActionBattleStatus.POISON);
        ActiveStatusDot poison = ACTIVE.remove(poisonKey);
        if (poison != null) DOTS.stop(battleId, poison.handleId());

        Key toxicKey = new Key(battleId, targetPokemonUUID, ActionBattleStatus.TOXIC);
        if (ACTIVE.containsKey(toxicKey)) return;
        onStatusApplied(battleId, poison != null ? poison.sourcePokemonUUID() : null, targetPokemonUUID,
                ActionBattleStatus.TOXIC, currentTick, remainingTicks);
    }

    static void tickBattle(ActionBattleSession session, ActionBattlePokemonRefs refs, long currentTick) {
        if (session == null || refs == null || currentTick < 0L) return;
        for (ActionBattleDotTick tick : DOTS.tickBattle(session.battleId(), currentTick)) {
            Pokemon target = findPokemon(refs, tick.targetPokemonUUID());
            if (target == null || target.getCurrentHealth() <= 0) continue;
            PokemonEntity entity = target.getEntity();
            if (entity == null || entity.isRemoved()) continue;
            int requested = tick.requestedDamage(target.getCurrentHealth(), target.getMaxHealth());
            if (requested > 0) {
                ActionBattleGhostRuntime.global().applyDot(entity, requested, tick.effectId(), currentTick);
            }
        }
        ACTIVE.entrySet().removeIf(entry -> entry.getKey().battleId().equals(session.battleId())
                && currentTick >= entry.getValue().endTick());
    }

    public static void clearTarget(UUID battleId, UUID targetPokemonUUID) {
        if (battleId == null || targetPokemonUUID == null) return;
        Iterator<Map.Entry<Key, ActiveStatusDot>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, ActiveStatusDot> entry = iterator.next();
            if (!entry.getKey().battleId().equals(battleId)
                    || !entry.getKey().targetPokemonUUID().equals(targetPokemonUUID)) continue;
            DOTS.stop(battleId, entry.getValue().handleId());
            iterator.remove();
        }
    }

    public static void clearBattle(UUID battleId) {
        if (battleId == null) return;
        DOTS.clearBattle(battleId);
        ACTIVE.keySet().removeIf(key -> key.battleId().equals(battleId));
    }

    public static void clearAll() {
        DOTS.clearAll();
        ACTIVE.clear();
    }

    private static Pokemon findPokemon(ActionBattlePokemonRefs refs, UUID pokemonUUID) {
        if (pokemonUUID == null) return null;
        for (Pokemon pokemon : refs.allPlayerPokemon()) {
            if (pokemon != null && pokemonUUID.equals(pokemon.getUuid())) return pokemon;
        }
        Pokemon trainer = refs.trainerPokemon();
        return trainer != null && pokemonUUID.equals(trainer.getUuid()) ? trainer : null;
    }

    private record Key(UUID battleId, UUID targetPokemonUUID, ActionBattleStatus status) {}
    private record ActiveStatusDot(UUID handleId, UUID sourcePokemonUUID, long endTick) {}
}

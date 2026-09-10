package net.epiac9.cobblemonnml.battle.action.effect.control;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleCommandController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleInfatuationController {
    private static final Map<Key, RuntimeState> STATES = new HashMap<>();

    private ActionBattleInfatuationController() {}

    public static boolean apply(ActionBattleSession session, PokemonEntity source,
                                PokemonEntity target, long currentTick) {
        if (session == null || source == null || target == null || source.isRemoved() || target.isRemoved()
                || !ActionBattleEffectApplicationGuard.allowsNewApplication(session, target, currentTick)) return false;
        double distance = ActionBattleInfatuationRules.approachDistance(
                source.getPokemon().getGender().name(), target.getPokemon().getGender().name());
        if (distance <= 0.0D) return false;
        UUID targetId = target.getPokemon().getUuid();
        ActionBattleCommandController.cancelPendingOrders(session, targetId,
                ActionBattleCommandController.InterruptReason.CONTROL_EFFECT);
        target.getNavigation().stop();
        STATES.put(new Key(session.battleId(), targetId), new RuntimeState(
                new ActionBattleInfatuationState(source.getPokemon().getUuid(), currentTick, distance),
                target.position()));
        return true;
    }

    public static boolean blocksCommands(UUID battleId, UUID pokemonId, long currentTick) {
        RuntimeState runtime = battleId != null && pokemonId != null ? STATES.get(new Key(battleId, pokemonId)) : null;
        if (runtime == null) return false;
        if (runtime.state.active(currentTick)) return true;
        STATES.remove(new Key(battleId, pokemonId));
        return false;
    }

    public static long remainingTicks(UUID battleId, UUID pokemonId, long currentTick) {
        RuntimeState runtime = battleId != null && pokemonId != null ? STATES.get(new Key(battleId, pokemonId)) : null;
        return runtime != null ? runtime.state.remainingTicks(currentTick) : 0L;
    }

    public static void tickBattle(ActionBattleSession session, ServerLevel level, long currentTick) {
        if (session == null || level == null) return;
        STATES.entrySet().removeIf(entry -> {
            if (!entry.getKey().battleId.equals(session.battleId())) return false;
            RuntimeState runtime = entry.getValue();
            if (!runtime.state.active(currentTick)) return true;
            var targetPokemon = ActionBattleManager.findActivePokemon(entry.getKey().pokemonId);
            var sourcePokemon = ActionBattleManager.findActivePokemon(runtime.state.sourcePokemonId());
            PokemonEntity target = targetPokemon != null ? targetPokemon.getEntity() : null;
            PokemonEntity source = sourcePokemon != null ? sourcePokemon.getEntity() : null;
            if (target == null || source == null || target.isRemoved() || source.isRemoved()) return true;
            Vec3 delta = source.position().subtract(target.position());
            double remainingTravel = runtime.state.maximumApproachDistance()
                    - Math.sqrt(target.position().distanceToSqr(runtime.startPosition));
            if (remainingTravel <= 0.05D || delta.horizontalDistanceSqr() <= 1.0D) {
                target.getNavigation().stop();
                return false;
            }
            Vec3 destination = target.position().add(delta.normalize().scale(Math.min(remainingTravel, delta.length())));
            Path path = target.getNavigation().createPath(destination.x, destination.y, destination.z, 0);
            if (path != null && path.canReach()) target.getNavigation().moveTo(path, ActionBattleInfatuationRules.WALK_SPEED);
            return false;
        });
    }

    public static void clearPokemon(UUID battleId, UUID pokemonId) {
        if (battleId == null || pokemonId == null) return;
        STATES.remove(new Key(battleId, pokemonId));
        STATES.entrySet().removeIf(entry -> pokemonId.equals(entry.getValue().state.sourcePokemonId()));
    }

    public static void clearBattle(UUID battleId) { if (battleId != null) STATES.keySet().removeIf(key -> key.battleId.equals(battleId)); }
    public static void clearAll() { STATES.clear(); }

    private record Key(UUID battleId, UUID pokemonId) {}
    private record RuntimeState(ActionBattleInfatuationState state, Vec3 startPosition) {}
}

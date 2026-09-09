package net.epiac9.cobblemonnml.battle.action.typeeffect.dragon;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleCommandController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSleepController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActionBattleDragonRuntime {
    private static final Set<ActionBattleStat> UPROAR_STATS = EnumSet.of(
            ActionBattleStat.ATTACK, ActionBattleStat.SPECIAL_ATTACK,
            ActionBattleStat.DEFENSE, ActionBattleStat.SPECIAL_DEFENSE);
    private static final Map<Key, ActionBattleDragonRoarState> ROARS = new HashMap<>();
    private static final Map<Key, Integer> APPLIED_STAT_LEVELS = new HashMap<>();

    private ActionBattleDragonRuntime() {}

    public static void onMoveCommitted(UUID battleId, UUID pokemonId, Move move) {
        ActionBattleSession session = ActionBattleManager.findSessionByBattleId(battleId);
        Pokemon pokemon = ActionBattleManager.findActivePokemon(pokemonId);
        if (session == null || pokemon == null || move == null) return;
        long currentTick = pokemon.getEntity() != null ? pokemon.getEntity().level().getGameTime() : -1L;
        if (currentTick < 0L) return;
        boolean dragonMove = move.getType() != null && "dragon".equalsIgnoreCase(move.getType().getName());
        boolean dragonHolder = ActionBattleTypeMechanicIdentity.hasMechanicBenefit(pokemon, "dragon");
        ActionBattleDragonController.global().onAbilityCommitted(
                session.dungeonSessionId(), pokemonId, dragonMove, dragonHolder, currentTick);
    }

    public static void onDamageTaken(UUID battleId, UUID pokemonId, long currentTick) {
        ActionBattleSession session = ActionBattleManager.findSessionByBattleId(battleId);
        if (session != null) ActionBattleDragonController.global().onDamageTaken(
                session.dungeonSessionId(), pokemonId, currentTick);
    }

    public static void onDamageTaken(UUID battleId, UUID pokemonId) {
        Pokemon pokemon = ActionBattleManager.findActivePokemon(pokemonId);
        if (pokemon != null && pokemon.getEntity() != null) {
            onDamageTaken(battleId, pokemonId, pokemon.getEntity().level().getGameTime());
        }
    }

    public static void tickPokemon(ActionBattleSession session, ServerLevel level,
                                   PokemonEntity entity, long currentTick) {
        if (session == null || level == null || entity == null || entity.isRemoved()) return;
        UUID pokemonId = entity.getPokemon().getUuid();
        ActionBattleDragonController controller = ActionBattleDragonController.global();
        ActionBattleDragonState.View before = controller.view(
                session.dungeonSessionId(), pokemonId, currentTick).orElse(null);
        ActionBattleDragonState.TickResult result = controller.tickPokemon(
                session.dungeonSessionId(), pokemonId, currentTick);
        ActionBattleDragonState.View after = controller.view(
                session.dungeonSessionId(), pokemonId, currentTick).orElse(null);
        if (after != null && after.phase() == ActionBattleDragonState.Phase.SLEEP_PENDING) {
            clearUproarRuntime(session, pokemonId, currentTick);
            applySleep(level, session, pokemonId, currentTick);
            controller.finishSleepConsequence(session.dungeonSessionId(), pokemonId, currentTick);
            return;
        }
        if (result == ActionBattleDragonState.TickResult.ROAR_STARTED) beginRoar(session, entity, currentTick);
        if (after != null && after.phase() == ActionBattleDragonState.Phase.ROARING) {
            captureRoarPokemon(session, level, entity, currentTick);
            enforceCapturedStuns(session, level, entity, currentTick);
            stop(entity);
            ActionBattleDragonVisuals.tickRoar(entity, currentTick);
        }
        if (result == ActionBattleDragonState.TickResult.ACTIVE_STARTED) syncStats(session, pokemonId, after, currentTick);
        if (after != null && after.phase() == ActionBattleDragonState.Phase.ACTIVE
                && (before == null || before.statLevel() != after.statLevel()
                || !APPLIED_STAT_LEVELS.containsKey(new Key(session.dungeonSessionId(), pokemonId)))) {
            syncStats(session, pokemonId, after, currentTick);
        }
        if (before != null && before.phase() == ActionBattleDragonState.Phase.ROARING
                && (after == null || after.phase() != ActionBattleDragonState.Phase.ROARING)) clearRoar(session, pokemonId);
        if (result == ActionBattleDragonState.TickResult.ACTIVE_EXPIRED_APPLY_SLEEP) {
            clearUproarRuntime(session, pokemonId, currentTick);
            applySleep(level, session, pokemonId, currentTick);
            controller.finishSleepConsequence(session.dungeonSessionId(), pokemonId, currentTick);
        }
    }

    public static boolean blocksTrainerCommands(ActionBattleSession session, UUID pokemonId, long currentTick) {
        if (session == null || pokemonId == null) return false;
        ActionBattleDragonState.View view = ActionBattleDragonController.global()
                .view(session.dungeonSessionId(), pokemonId, currentTick).orElse(null);
        return isRoarStunned(pokemonId) || view != null && view.phase() == ActionBattleDragonState.Phase.ACTIVE;
    }

    public static boolean blocksSwap(UUID pokemonId) { return isRoarStunned(pokemonId); }

    public static boolean active(ActionBattleSession session, UUID pokemonId, long currentTick) {
        ActionBattleDragonState.View view = session != null ? ActionBattleDragonController.global()
                .view(session.dungeonSessionId(), pokemonId, currentTick).orElse(null) : null;
        return view != null && view.phase() == ActionBattleDragonState.Phase.ACTIVE;
    }

    public static CooldownPlan cooldownPlan(ActionBattleSession session, PokemonEntity caster,
                                             int moveSlot, long currentTick) {
        if (session == null || caster == null || !active(session, caster.getPokemon().getUuid(), currentTick)) {
            return CooldownPlan.NORMAL;
        }
        boolean dragonHolder = ActionBattleTypeMechanicIdentity.hasMechanicBenefit(caster, "dragon");
        return new CooldownPlan(true, 0L,
                ActionBattleDragonRules.personalCooldownTicks(dragonHolder), moveSlot);
    }

    public static void suppressStatsByHaze(ActionBattleSession session, UUID pokemonId, long currentTick) {
        if (session == null || pokemonId == null) return;
        ActionBattleDragonController.global().suppressStatsByHaze(session.dungeonSessionId(), pokemonId);
        clearStats(session, pokemonId, currentTick);
    }

    public static void onPokemonUnavailable(ActionBattleSession session, UUID pokemonId,
                                            boolean fainted, long currentTick) {
        if (session == null || pokemonId == null) return;
        ActionBattleDragonState.ExitReason reason = fainted
                ? ActionBattleDragonState.ExitReason.FAINT : ActionBattleDragonState.ExitReason.SWAP;
        ActionBattleDragonState.ExitResult result = ActionBattleDragonController.global().exit(
                session.dungeonSessionId(), pokemonId, reason, currentTick);
        clearUproarRuntime(session, pokemonId, currentTick);
        Pokemon pokemon = ActionBattleManager.findActivePokemon(pokemonId);
        if (result == ActionBattleDragonState.ExitResult.APPLY_SLEEP && pokemon != null
                && pokemon.getEntity() != null && pokemon.getEntity().level() instanceof ServerLevel level) {
            applySleep(level, session, pokemonId, currentTick);
        }
    }

    public static void onBattleEnded(UUID sessionId, long currentTick) {
        ActionBattleDragonController.global().onBattleEnded(sessionId, currentTick);
        APPLIED_STAT_LEVELS.keySet().removeIf(key -> key.sessionId.equals(sessionId));
    }

    public static void clearSession(UUID sessionId) {
        ActionBattleDragonController.global().clearSession(sessionId);
        ROARS.keySet().removeIf(key -> key.sessionId.equals(sessionId));
        APPLIED_STAT_LEVELS.keySet().removeIf(key -> key.sessionId.equals(sessionId));
    }

    public static void clearPokemon(UUID sessionId, UUID pokemonId) {
        ActionBattleDragonController.global().clearPokemon(sessionId, pokemonId);
        ROARS.remove(new Key(sessionId, pokemonId));
        APPLIED_STAT_LEVELS.remove(new Key(sessionId, pokemonId));
    }

    public static void clearAll() {
        ActionBattleDragonController.global().clearAll();
        ROARS.clear();
        APPLIED_STAT_LEVELS.clear();
    }

    private static void beginRoar(ActionBattleSession session, PokemonEntity owner, long currentTick) {
        UUID pokemonId = owner.getPokemon().getUuid();
        ROARS.put(new Key(session.dungeonSessionId(), pokemonId), new ActionBattleDragonRoarState(
                currentTick, currentTick + ActionBattleDragonRules.ROAR_DURATION_TICKS));
        session.clearPokemonAllCommandCooldowns(pokemonId);
        ActionBattleCommandController.cancelPendingOrders(session, pokemonId,
                ActionBattleCommandController.InterruptReason.CONTROL_EFFECT);
        stop(owner);
        ActionBattleDragonVisuals.beginRoar(owner);
    }

    private static void captureRoarPokemon(ActionBattleSession session, ServerLevel level,
                                           PokemonEntity owner, long currentTick) {
        Key key = new Key(session.dungeonSessionId(), owner.getPokemon().getUuid());
        ActionBattleDragonRoarState roar = ROARS.computeIfAbsent(key, ignored ->
                new ActionBattleDragonRoarState(currentTick,
                        currentTick + ActionBattleDragonRules.ROAR_DURATION_TICKS));
        AABB area = owner.getBoundingBox().inflate(ActionBattleDragonRules.ROAR_RADIUS);
        for (PokemonEntity pokemon : level.getEntitiesOfClass(PokemonEntity.class, area,
                candidate -> !candidate.isRemoved() && candidate.distanceToSqr(owner)
                        <= ActionBattleDragonRules.ROAR_RADIUS * ActionBattleDragonRules.ROAR_RADIUS)) {
            boolean newlyCaptured = roar.capture(pokemon.getPokemon().getUuid(), pokemon.getUUID(), currentTick);
            stop(pokemon);
            ActionBattleSession affected = ActionBattleManager.findSessionForPokemon(pokemon.getPokemon().getUuid());
            if (newlyCaptured && affected != null) ActionBattleCommandController.cancelPendingOrders(affected,
                    pokemon.getPokemon().getUuid(), ActionBattleCommandController.InterruptReason.CONTROL_EFFECT);
        }
    }

    private static void enforceCapturedStuns(ActionBattleSession session, ServerLevel level,
                                             PokemonEntity owner, long currentTick) {
        ActionBattleDragonRoarState roar = ROARS.get(
                new Key(session.dungeonSessionId(), owner.getPokemon().getUuid()));
        if (roar == null) return;
        for (UUID entityId : roar.entityIds()) {
            if (level.getEntity(entityId) instanceof PokemonEntity pokemon && !pokemon.isRemoved()) stop(pokemon);
        }
    }

    public static boolean isRoarStunned(UUID pokemonId) {
        if (pokemonId == null) return false;
        for (ActionBattleDragonRoarState roar : ROARS.values()) {
            if (roar.pokemonIds().contains(pokemonId)) return true;
        }
        return false;
    }

    private static void clearRoar(ActionBattleSession session, UUID ownerId) {
        ROARS.remove(new Key(session.dungeonSessionId(), ownerId));
    }

    private static void syncStats(ActionBattleSession session, UUID pokemonId,
                                  ActionBattleDragonState.View view, long currentTick) {
        clearStats(session, pokemonId, currentTick);
        if (view == null || view.statLevel() <= 0 || view.statSuppressedByHaze()) return;
        if (ActionBattleEffectController.global().hasHaze(session.battleId(), pokemonId, currentTick)) {
            ActionBattleDragonController.global().suppressStatsByHaze(session.dungeonSessionId(), pokemonId);
            return;
        }
        for (ActionBattleStat stat : UPROAR_STATS) ActionBattleEffectController.global()
                .applyBoundedStatContribution(session.battleId(), pokemonId, stat, view.statLevel(), currentTick,
                        Math.max(1L, view.phaseRemainingTicks()), ActionBattleStatSource.DRAGON_UPROAR);
        APPLIED_STAT_LEVELS.put(new Key(session.dungeonSessionId(), pokemonId), view.statLevel());
    }

    private static void clearStats(ActionBattleSession session, UUID pokemonId, long currentTick) {
        ActionBattleEffectController.global().clearStatContributionsFromSource(
                session.battleId(), pokemonId, ActionBattleStatSource.DRAGON_UPROAR, currentTick);
        APPLIED_STAT_LEVELS.remove(new Key(session.dungeonSessionId(), pokemonId));
    }

    private static void clearUproarRuntime(ActionBattleSession session, UUID pokemonId, long currentTick) {
        clearRoar(session, pokemonId);
        clearStats(session, pokemonId, currentTick);
        session.clearPokemonSharedAbilityCooldown(pokemonId);
        session.clearPokemonPersonalMoveCooldowns(pokemonId);
    }

    private static void applySleep(ServerLevel level, ActionBattleSession session, UUID pokemonId, long currentTick) {
        ActionBattleSleepController.applySleep(level, session.dungeonSessionId(), pokemonId, currentTick,
                ActionBattleSleepController.rollSleepDurationTicks(level.random));
    }

    private static void stop(PokemonEntity pokemon) {
        pokemon.getNavigation().stop();
        pokemon.setDeltaMovement(Vec3.ZERO);
    }

    public record CooldownPlan(boolean active, long sharedTicks, long personalTicks, int moveSlot) {
        public static final CooldownPlan NORMAL = new CooldownPlan(false, 0L, 0L, -1);
    }

    private record Key(UUID sessionId, UUID pokemonId) {}
}

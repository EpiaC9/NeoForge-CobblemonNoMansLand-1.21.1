package net.epiac9.cobblemonnml.battle.action.typeeffect.psychic;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleLineOfSight;
import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelCancelReason;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelController;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelState;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleChannelVisuals;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattlePsychicChannelRuntime {
    public static final int DEFAULT_CHANNEL_TICKS = 40;
    private static final ActionBattleChannelController CHANNELS = new ActionBattleChannelController();
    private static final Map<UUID, Context> CONTEXTS = new HashMap<>();
    private static TimingHook timingHook = (move, suggestedTicks) -> suggestedTicks;

    private ActionBattlePsychicChannelRuntime() {}

    public static boolean start(ActionBattleSession session, PokemonEntity caster, PokemonEntity target,
                                Move move, ActionBattlePsychicDeliveryRules.Path path,
                                int suggestedTicks, Runnable completion) {
        if (session == null || caster == null || target == null || move == null || completion == null
                || path == null || path == ActionBattlePsychicDeliveryRules.Path.STANDARD) return false;
        int duration = Math.max(1, timingHook.channelTicks(move, Math.max(1, suggestedTicks)));
        UUID casterId = caster.getPokemon().getUuid();
        if (!(caster.level() instanceof ServerLevel level)) return false;
        boolean requiresTargetLos = path == ActionBattlePsychicDeliveryRules.Path.TARGETED_DIRECT_CHANNEL;
        Context context = new Context(session, level, caster.getUUID(), target.getUUID(),
                requiresTargetLos, completion);
        boolean started = CHANNELS.start(session.battleId(), casterId, target.getPokemon().getUuid(),
                move.getName(), ActionBattlePsychicDeliveryRules.presetFor(path, duration),
                position(target), caster.getPokemon().getCurrentHealth(),
                state -> complete(casterId, state),
                (state, reason) -> cancel(casterId, state, reason));
        if (started) CONTEXTS.put(casterId, context);
        return started;
    }

    public static void tickBattle(ActionBattleSession session, ServerLevel level) {
        if (session == null || level == null) return;
        CHANNELS.tick(session.battleId(), state -> track(level, state));
        for (ActionBattleChannelState state : CHANNELS.statesForBattle(session.battleId())) {
            Context context = CONTEXTS.get(state.casterPokemonUUID());
            PokemonEntity caster = context != null ? entity(level, context.casterEntityId()) : null;
            if (caster == null) {
                CHANNELS.queueCancel(state.casterPokemonUUID(), ActionBattleChannelCancelReason.CASTER_INVALID);
                continue;
            }
            CHANNELS.observeHealth(state.casterPokemonUUID(), caster.getPokemon().getCurrentHealth());
            if (state.preset().immobilizeCaster()) {
                caster.getNavigation().stop();
                caster.setDeltaMovement(Vec3.ZERO);
            }
            ActionBattleChannelVisuals.emitAura(level, caster, "psychic", state.progress());
        }
    }

    public static void onCommand(UUID pokemonId) { CHANNELS.onCommand(pokemonId); }
    public static void onMovementCommand(UUID pokemonId) {
        ActionBattleChannelState state = CHANNELS.state(pokemonId).orElse(null);
        if (state != null && state.preset().immobilizeCaster()) CHANNELS.onCommand(pokemonId);
    }
    public static void onControlEffect(UUID pokemonId) {
        CHANNELS.cancel(pokemonId, ActionBattleChannelCancelReason.CONTROL_EFFECT);
    }
    public static boolean isChanneling(UUID pokemonId) { return CHANNELS.isChanneling(pokemonId); }
    public static boolean blocksMovement(UUID pokemonId) {
        return CHANNELS.state(pokemonId).map(state -> state.preset().immobilizeCaster()).orElse(false);
    }
    public static void clearPokemon(UUID pokemonId) {
        CHANNELS.cancel(pokemonId, ActionBattleChannelCancelReason.CASTER_INVALID);
        CONTEXTS.remove(pokemonId);
    }
    public static void clearBattle(UUID battleId) {
        CHANNELS.clearBattle(battleId);
        CONTEXTS.entrySet().removeIf(entry -> entry.getValue().session().battleId().equals(battleId));
    }
    public static void clearAll() {
        for (UUID battleId : CONTEXTS.values().stream().map(value -> value.session().battleId()).distinct().toList()) {
            CHANNELS.clearBattle(battleId);
        }
        CONTEXTS.clear();
        timingHook = (move, suggestedTicks) -> suggestedTicks;
    }

    public static void setTimingHook(TimingHook hook) {
        timingHook = hook != null ? hook : (move, suggestedTicks) -> suggestedTicks;
    }

    private static ActionBattleChannelController.TargetUpdate track(ServerLevel level,
                                                                     ActionBattleChannelState state) {
        Context context = CONTEXTS.get(state.casterPokemonUUID());
        PokemonEntity caster = context != null ? entity(level, context.casterEntityId()) : null;
        PokemonEntity target = context != null ? entity(level, context.targetEntityId()) : null;
        boolean valid = context != null && caster != null && target != null && caster.isAlive() && target.isAlive()
                && context.session().battleId().equals(state.battleId())
                && (!context.requiresTargetLos()
                || ActionBattleLineOfSight.evaluate(context.session(), caster, target).visible());
        return new ActionBattleChannelController.TargetUpdate(valid, valid ? position(target) : null);
    }

    private static void complete(UUID casterId, ActionBattleChannelState state) {
        Context context = CONTEXTS.remove(casterId);
        if (context == null) return;
        ServerLevel level = context.level();
        PokemonEntity caster = level != null ? entity(level, context.casterEntityId()) : null;
        PokemonEntity target = level != null ? entity(level, context.targetEntityId()) : null;
        if (caster != null && target != null && caster.isAlive() && target.isAlive()
                && (!context.requiresTargetLos()
                || ActionBattleLineOfSight.evaluate(context.session(), caster, target).visible())) {
            context.completion().run();
        }
    }

    private static void cancel(UUID casterId, ActionBattleChannelState state,
                               ActionBattleChannelCancelReason reason) {
        Context context = CONTEXTS.remove(casterId);
        if (context == null) return;
        ServerLevel level = context.level();
        PokemonEntity caster = level != null ? entity(level, context.casterEntityId()) : null;
        if (caster != null) ActionBattleChannelVisuals.emitCancellationBurst(level, caster, "psychic");
    }

    private static PokemonEntity entity(ServerLevel level, UUID id) {
        Entity entity = level != null && id != null ? level.getEntity(id) : null;
        return entity instanceof PokemonEntity pokemon && !pokemon.isRemoved() ? pokemon : null;
    }
    private static ActionBattlePosition position(PokemonEntity pokemon) {
        return new ActionBattlePosition(pokemon.getX(), pokemon.getY(), pokemon.getZ());
    }

    @FunctionalInterface public interface TimingHook { int channelTicks(Move move, int suggestedTicks); }
    private record Context(ActionBattleSession session, ServerLevel level, UUID casterEntityId,
                           UUID targetEntityId, boolean requiresTargetLos, Runnable completion) {}
}

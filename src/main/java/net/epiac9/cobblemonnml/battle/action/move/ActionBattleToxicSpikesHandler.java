package net.epiac9.cobblemonnml.battle.action.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import net.epiac9.cobblemonnml.battle.action.ActionBattleParalysisController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaController;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaPreset;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaState;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelCancelReason;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelController;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelPreset;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelState;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleChannelVisuals;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleToxicSpikesHandler {
    public static final String MOVE_ID = "toxicspikes";
    public static final int CHANNEL_TICKS = 40;
    public static final int FAILURE_COOLDOWN_TICKS = 20;
    public static final int DURATION_TICKS = 180;
    public static final int PULSE_INTERVAL_TICKS = 20;
    public static final double RADIUS = 7.0D;
    public static final double HEIGHT = 2.0D;

    private static final ActionBattleChannelPreset CHANNEL = new ActionBattleChannelPreset(CHANNEL_TICKS, true, true, true, true);
    private static final ActionBattlePersistentAreaPreset AREA = new ActionBattlePersistentAreaPreset(RADIUS, HEIGHT, DURATION_TICKS, PULSE_INTERVAL_TICKS, true);
    private static final ActionBattleChannelController CHANNELS = new ActionBattleChannelController();
    private static final ActionBattlePersistentAreaController AREAS = new ActionBattlePersistentAreaController();
    private static final Map<UUID, CastContext> CASTS = new HashMap<>();

    private ActionBattleToxicSpikesHandler() {}

    public static boolean isToxicSpikes(Move move) { return move != null && MOVE_ID.equals(move.getName()); }

    public static StartResult tryStart(ActionBattleSession session, ServerLevel level, PokemonEntity caster, PokemonEntity target, Move move) {
        if (session == null || level == null || caster == null || target == null || move == null || !isToxicSpikes(move)) return StartResult.INVALID;
        UUID casterPokemonUUID = caster.getPokemon().getUuid();
        if (CHANNELS.isChanneling(casterPokemonUUID)) return StartResult.ALREADY_CHANNELING;
        long currentTick = level.getGameTime();
        if (!FightOrFlightAdapter.canCommitHail(caster, target)) {
            session.setPokemonAllCommandCooldown(casterPokemonUUID, currentTick, FAILURE_COOLDOWN_TICKS);
            return StartResult.TARGET_UNREACHABLE;
        }
        if (!FightOrFlightAdapter.consumeOnePp(move)) return StartResult.NO_PP;
        session.startPokemonMoveCooldown(casterPokemonUUID, currentTick, FightOrFlightAdapter.cooldownTicks(move));
        boolean playerSide = casterPokemonUUID.equals(session.playerActivePokemonUUID());
        CastContext context = new CastContext(session, level, move, casterPokemonUUID, playerSide);
        CASTS.put(casterPokemonUUID, context);
        boolean started = CHANNELS.start(
                session.battleId(), casterPokemonUUID, target.getPokemon().getUuid(), MOVE_ID, CHANNEL,
                positionOf(target), caster.getPokemon().getCurrentHealth(),
                ActionBattleToxicSpikesHandler::complete,
                ActionBattleToxicSpikesHandler::cancel
        );
        if (!started) {
            CASTS.remove(casterPokemonUUID);
            FightOrFlightAdapter.refundOnePp(move);
            return StartResult.INVALID;
        }
        caster.getNavigation().stop();
        DebugLog.log("[CobblemonNML] Toxic Spikes channel started. Battle=" + session.battleId() + ", caster=" + casterPokemonUUID + ", target=" + target.getPokemon().getUuid());
        return StartResult.STARTED;
    }

    public static void tickBattle(ActionBattleSession session, ServerLevel level) {
        if (session == null || level == null) return;
        AREAS.tick(session.battleId());
        for (ActionBattlePersistentAreaState area : AREAS.statesForBattle(session.battleId())) emitAreaAmbient(level, area);
        CHANNELS.tick(session.battleId(), state -> trackTarget(session, level, state));
        for (ActionBattleChannelState state : CHANNELS.statesForBattle(session.battleId())) {
            PokemonEntity caster = activePokemonEntity(session, level, state.casterPokemonUUID());
            if (caster == null || caster.isRemoved()) {
                CHANNELS.cancel(state.casterPokemonUUID(), ActionBattleChannelCancelReason.CASTER_INVALID);
                continue;
            }
            if (state.preset().immobilizeCaster()) caster.getNavigation().stop();
            ActionBattleChannelVisuals.emitAura(level, caster, "poison", state.progress());
            CHANNELS.observeHealth(state.casterPokemonUUID(), caster.getPokemon().getCurrentHealth());
        }
    }

    public static void onCommand(UUID casterPokemonUUID) { CHANNELS.onCommand(casterPokemonUUID); }
    public static void onControlEffect(UUID casterPokemonUUID) { CHANNELS.cancel(casterPokemonUUID, ActionBattleChannelCancelReason.CONTROL_EFFECT); }
    public static boolean isChanneling(UUID casterPokemonUUID) { return CHANNELS.isChanneling(casterPokemonUUID); }

    public static void clearBattle(UUID battleId) {
        CHANNELS.clearBattle(battleId);
        AREAS.clearBattle(battleId);
        CASTS.entrySet().removeIf(entry -> entry.getValue().session().battleId().equals(battleId));
    }

    private static ActionBattleChannelController.TargetUpdate trackTarget(ActionBattleSession session, ServerLevel level, ActionBattleChannelState state) {
        CastContext context = CASTS.get(state.casterPokemonUUID());
        if (context == null || context.session() != session) return new ActionBattleChannelController.TargetUpdate(false, null);
        PokemonEntity caster = activePokemonEntity(session, level, state.casterPokemonUUID());
        PokemonEntity target = activePokemonEntity(session, level, state.targetPokemonUUID());
        if (caster == null || target == null || caster.isRemoved() || target.isRemoved() || !FightOrFlightAdapter.canCommitHail(caster, target)) {
            return new ActionBattleChannelController.TargetUpdate(false, null);
        }
        return new ActionBattleChannelController.TargetUpdate(true, positionOf(target));
    }

    private static void complete(ActionBattleChannelState state) {
        CastContext context = CASTS.remove(state.casterPokemonUUID());
        if (context == null || state.lastTargetablePosition() == null) return;
        AREAS.create(state.battleId(), state.casterPokemonUUID(), MOVE_ID, state.lastTargetablePosition(), AREA, area -> pulse(context, area));
        ActionBattleParalysisController.onAbilitySucceeded(state.battleId(), state.casterPokemonUUID(), context.level().getGameTime());
        DebugLog.log("[CobblemonNML] Toxic Spikes channel completed. Battle=" + state.battleId() + ", caster=" + state.casterPokemonUUID() + ", anchor=" + state.lastTargetablePosition());
    }

    private static void cancel(ActionBattleChannelState state, ActionBattleChannelCancelReason reason) {
        CastContext context = CASTS.remove(state.casterPokemonUUID());
        if (context == null) return;
        if (reason == ActionBattleChannelCancelReason.TARGET_UNREACHABLE) {
            FightOrFlightAdapter.refundOnePp(context.move());
            context.session().setPokemonAllCommandCooldown(state.casterPokemonUUID(), context.level().getGameTime(), FAILURE_COOLDOWN_TICKS);
        }
        PokemonEntity caster = activePokemonEntity(context.session(), context.level(), state.casterPokemonUUID());
        if (caster != null) ActionBattleChannelVisuals.emitCancellationBurst(context.level(), caster, "poison");
        DebugLog.log("[CobblemonNML] Toxic Spikes channel cancelled. Battle=" + state.battleId() + ", caster=" + state.casterPokemonUUID() + ", reason=" + reason);
    }

    private static void pulse(CastContext context, ActionBattlePersistentAreaState area) {
        if (context == null || area == null) return;
        emitPulse(context.level(), area);
        PokemonEntity enemy = context.playerSide()
                ? activePokemonEntity(context.session(), context.level(), context.session().trainerActivePokemonUUID())
                : activePokemonEntity(context.session(), context.level(), context.session().playerActivePokemonUUID());
        if (enemy == null || enemy.isRemoved() || !area.contains(enemy.getX(), enemy.getY(), enemy.getZ())) return;
        long currentTick = context.level().getGameTime();
        ActionBattleProtectController.EffectInterception interception = ActionBattleProtectController.global()
                .interceptTimedEffect(area.battleId(), enemy.getPokemon().getUuid(), currentTick, "poison", 360);
        if (!interception.allowed()) return;
        float durationMultiplier = interception.durationTicks() / 360.0F;
        ActionBattleEffectController.global().applyPoison(area.battleId(), enemy.getPokemon().getUuid(), 1, context.level().getGameTime(), durationMultiplier);
        DebugLog.log("[CobblemonNML] Toxic Spikes pulse applied +1 Poison. Battle=" + area.battleId() + ", area=" + area.areaId() + ", target=" + enemy.getPokemon().getUuid());
    }

    private static void emitAreaAmbient(ServerLevel level, ActionBattlePersistentAreaState area) {
        if (level == null || area == null || level.getGameTime() % 4L != 0L) return;
        ActionBattlePosition anchor = area.anchor();
        level.sendParticles(ParticleTypes.WITCH, anchor.x(), anchor.y() + 0.15D, anchor.z(), 5, RADIUS * 0.55D, 0.08D, RADIUS * 0.55D, 0.01D);
    }

    private static void emitPulse(ServerLevel level, ActionBattlePersistentAreaState area) {
        if (level == null || area == null) return;
        ActionBattlePosition anchor = area.anchor();
        level.sendParticles(ParticleTypes.WITCH, anchor.x(), anchor.y() + 0.25D, anchor.z(), 22, RADIUS * 0.65D, 0.15D, RADIUS * 0.65D, 0.03D);
    }

    private static PokemonEntity activePokemonEntity(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        if (session == null || level == null || pokemonUUID == null) return null;
        UUID entityUUID = null;
        if (pokemonUUID.equals(session.playerActivePokemonUUID())) entityUUID = session.playerActiveEntityUUID();
        else if (pokemonUUID.equals(session.trainerActivePokemonUUID())) entityUUID = session.trainerActiveEntityUUID();
        Entity raw = entityUUID != null ? level.getEntity(entityUUID) : null;
        return raw instanceof PokemonEntity pokemonEntity ? pokemonEntity : null;
    }

    private static ActionBattlePosition positionOf(PokemonEntity entity) { return new ActionBattlePosition(entity.getX(), entity.getY(), entity.getZ()); }

    public enum StartResult { STARTED, TARGET_UNREACHABLE, NO_PP, ALREADY_CHANNELING, INVALID }
    private record CastContext(ActionBattleSession session, ServerLevel level, Move move, UUID casterPokemonUUID, boolean playerSide) {}
}

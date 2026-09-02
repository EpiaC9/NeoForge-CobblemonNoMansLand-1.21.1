package net.epiac9.cobblemonnml.battle.action.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import net.epiac9.cobblemonnml.battle.action.ActionBattleParalysisController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleConfusionController;
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
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleHailVisuals;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleChannelVisuals;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleHailHandler {
    public static final String MOVE_ID = "hail";
    public static final int CHANNEL_TICKS = 40;
    public static final int FAILURE_COOLDOWN_TICKS = 20;
    public static final int NORMAL_DURATION_TICKS = 180;
    public static final int ICY_ROCK_DURATION_TICKS = 240;
    public static final int PULSE_INTERVAL_TICKS = 20;
    public static final double RADIUS = 7.0D;
    public static final double HEIGHT = 6.0D;

    private static final ActionBattleChannelPreset HAIL_CHANNEL = new ActionBattleChannelPreset(CHANNEL_TICKS, true, true, true, true);
    private static final ActionBattlePersistentAreaPreset HAIL_NORMAL = new ActionBattlePersistentAreaPreset(RADIUS, HEIGHT, NORMAL_DURATION_TICKS, PULSE_INTERVAL_TICKS, true);
    private static final ActionBattlePersistentAreaPreset HAIL_ICY_ROCK = new ActionBattlePersistentAreaPreset(RADIUS, HEIGHT, ICY_ROCK_DURATION_TICKS, PULSE_INTERVAL_TICKS, true);
    private static final ResourceLocation ICY_ROCK_ID = ResourceLocation.fromNamespaceAndPath("cobblemon", "icy_rock");
    private static final Map<UUID, HailCastContext> CASTS = new HashMap<>();
    private static final Map<UUID, HailCloudContext> CEILING_CLOUDS = new HashMap<>();

    private ActionBattleHailHandler() {}

    public static boolean isHail(Move move) { return move != null && MOVE_ID.equals(move.getName()); }

    public static StartResult tryStart(ActionBattleSession session, ServerLevel level, PokemonEntity caster, PokemonEntity target, Move move) {
        return tryStart(session, level, caster, target, move, 0L, false);
    }

    public static StartResult tryStart(ActionBattleSession session, ServerLevel level, PokemonEntity caster, PokemonEntity target, Move move, long confusionBonusTicks, boolean confusionSelfCancel) {
        if (session == null || level == null || caster == null || move == null || (target == null && confusionBonusTicks <= 0L) || !isHail(move)) return StartResult.INVALID;
        UUID casterPokemonUUID = caster.getPokemon().getUuid();
        if (ActionBattleChannelController.global().isChanneling(casterPokemonUUID)) return StartResult.ALREADY_CHANNELING;
        long currentTick = level.getGameTime();
        if (confusionBonusTicks <= 0L && !FightOrFlightAdapter.canCommitHail(caster, target)) {
            session.setPokemonAllCommandCooldown(casterPokemonUUID, currentTick, FAILURE_COOLDOWN_TICKS);
            return StartResult.TARGET_UNREACHABLE;
        }
        if (!FightOrFlightAdapter.consumeOnePp(move)) return StartResult.NO_PP;
        session.startPokemonMoveCooldown(casterPokemonUUID, currentTick, FightOrFlightAdapter.cooldownTicks(move));
        boolean playerSide = casterPokemonUUID.equals(session.playerActivePokemonUUID());
        boolean confusedChannel = confusionBonusTicks > 0L;
        ActionBattlePosition initialTargetPosition = ActionBattleAreaEffectSupport.targetPosition(caster, target, confusionBonusTicks);
        int totalChannelTicks = ActionBattleAreaEffectSupport.totalChannelTicks(confusionBonusTicks);
        int cancelAtElapsedTick = ActionBattleAreaEffectSupport.cancelAtElapsedTick(caster, totalChannelTicks, confusionSelfCancel);
        HailCastContext context = new HailCastContext(session, level, move, casterPokemonUUID, playerSide, confusedChannel, cancelAtElapsedTick);
        CASTS.put(casterPokemonUUID, context);
        boolean started = ActionBattleChannelController.global().start(
                session.battleId(), casterPokemonUUID, confusedChannel ? null : target.getPokemon().getUuid(), MOVE_ID,
                confusedChannel ? new ActionBattleChannelPreset(totalChannelTicks, true, true, false, true) : HAIL_CHANNEL,
                initialTargetPosition, caster.getPokemon().getCurrentHealth(),
                ActionBattleHailHandler::complete,
                ActionBattleHailHandler::cancel
        );
        if (!started) {
            CASTS.remove(casterPokemonUUID);
            FightOrFlightAdapter.refundOnePp(move);
            return StartResult.INVALID;
        }
        caster.getNavigation().stop();
        DebugLog.log("[CobblemonNML] Hail channel started. Battle=" + session.battleId() + ", caster=" + casterPokemonUUID + ", target=" + (target != null ? target.getPokemon().getUuid() : "none") + ", confused=" + confusedChannel + ", durationTicks=" + totalChannelTicks);
        return StartResult.STARTED;
    }

    public static void tickBattle(ActionBattleSession session, ServerLevel level) {
        if (session == null || level == null) return;
        ActionBattlePersistentAreaController.global().tick(session.battleId());
        cleanupCeilingClouds(session.battleId());
        for (ActionBattlePersistentAreaState area : ActionBattlePersistentAreaController.global().statesForBattle(session.battleId())) {
            if (MOVE_ID.equals(area.effectId())) ActionBattleHailVisuals.emitAreaAmbient(level, area);
        }
        for (ActionBattleChannelState state : ActionBattleChannelController.global().statesForBattle(session.battleId())) {
            HailCastContext context = CASTS.get(state.casterPokemonUUID());
            if (context != null && context.confused() && context.cancelAtElapsedTick() >= 0 && state.elapsedTicks() >= context.cancelAtElapsedTick()) {
                ActionBattleChannelController.global().cancel(state.casterPokemonUUID(), ActionBattleChannelCancelReason.CONFUSION_SELF_CANCEL);
            }
        }
        ActionBattleChannelController.global().tick(session.battleId(), state -> trackTarget(session, level, state));
        for (ActionBattleChannelState state : ActionBattleChannelController.global().statesForBattle(session.battleId())) {
            PokemonEntity caster = ActionBattleAreaEffectSupport.activePokemonEntity(session, level, state.casterPokemonUUID());
            if (caster == null || caster.isRemoved()) {
                ActionBattleChannelController.global().cancel(state.casterPokemonUUID(), ActionBattleChannelCancelReason.CASTER_INVALID);
                continue;
            }
            if (state.preset().immobilizeCaster()) caster.getNavigation().stop();
            ActionBattleChannelVisuals.emitAura(level, caster, "ice", state.progress());
            ActionBattleChannelController.global().observeHealth(state.casterPokemonUUID(), caster.getPokemon().getCurrentHealth());
        }
    }

    public static void onCommand(UUID casterPokemonUUID) { ActionBattleChannelController.global().onCommand(casterPokemonUUID); }
    public static void onControlEffect(UUID casterPokemonUUID) { ActionBattleChannelController.global().cancel(casterPokemonUUID, ActionBattleChannelCancelReason.CONTROL_EFFECT); }
    public static boolean isChanneling(UUID casterPokemonUUID) { return ActionBattleChannelController.global().isChanneling(casterPokemonUUID); }

    public static void clearBattle(UUID battleId) {
        ActionBattleChannelController.global().clearBattle(battleId);
        ActionBattlePersistentAreaController.global().clearBattle(battleId);
        removeCeilingCloudsForBattle(battleId);
        CASTS.entrySet().removeIf(entry -> entry.getValue().session().battleId().equals(battleId));
    }

    private static ActionBattleChannelController.TargetUpdate trackTarget(ActionBattleSession session, ServerLevel level, ActionBattleChannelState state) {
        HailCastContext context = CASTS.get(state.casterPokemonUUID());
        if (context == null || context.session() != session) return new ActionBattleChannelController.TargetUpdate(false, null);
        if (context.confused()) return new ActionBattleChannelController.TargetUpdate(true, state.lastTargetablePosition());
        PokemonEntity caster = ActionBattleAreaEffectSupport.activePokemonEntity(session, level, state.casterPokemonUUID());
        PokemonEntity target = ActionBattleAreaEffectSupport.activePokemonEntity(session, level, state.targetPokemonUUID());
        if (caster == null || target == null || caster.isRemoved() || target.isRemoved() || !FightOrFlightAdapter.canCommitHail(caster, target)) {
            return new ActionBattleChannelController.TargetUpdate(false, null);
        }
        return new ActionBattleChannelController.TargetUpdate(true, ActionBattleAreaEffectSupport.positionOf(target));
    }

    private static void complete(ActionBattleChannelState state) {
        HailCastContext context = CASTS.remove(state.casterPokemonUUID());
        if (context == null || state.lastTargetablePosition() == null) return;
        PokemonEntity caster = ActionBattleAreaEffectSupport.activePokemonEntity(context.session(), context.level(), state.casterPokemonUUID());
        ActionBattlePersistentAreaPreset preset = caster != null && holdsIcyRock(caster) ? HAIL_ICY_ROCK : HAIL_NORMAL;
        UUID areaId = ActionBattlePersistentAreaController.global().create(
                state.battleId(), state.casterPokemonUUID(), MOVE_ID, state.lastTargetablePosition(), preset,
                area -> pulse(context, area)
        );
        if (areaId != null) {
            ActionBattlePersistentAreaState area = ActionBattlePersistentAreaController.global().statesForBattle(state.battleId()).stream()
                    .filter(candidate -> areaId.equals(candidate.areaId()))
                    .findFirst()
                    .orElse(null);
            if (area != null) spawnCeilingCloud(context.level(), area);
        }
        ActionBattleParalysisController.onAbilitySucceeded(state.battleId(), state.casterPokemonUUID(), context.level().getGameTime());
        DebugLog.log("[CobblemonNML] Hail channel completed. Battle=" + state.battleId() + ", caster=" + state.casterPokemonUUID()
                + ", anchor=" + state.lastTargetablePosition() + ", durationTicks=" + preset.durationTicks());
    }

    private static void cancel(ActionBattleChannelState state, ActionBattleChannelCancelReason reason) {
        HailCastContext context = CASTS.remove(state.casterPokemonUUID());
        if (context == null) return;
        if (reason == ActionBattleChannelCancelReason.TARGET_UNREACHABLE) {
            FightOrFlightAdapter.refundOnePp(context.move());
            context.session().setPokemonAllCommandCooldown(state.casterPokemonUUID(), context.level().getGameTime(), FAILURE_COOLDOWN_TICKS);
        }
        PokemonEntity caster = ActionBattleAreaEffectSupport.activePokemonEntity(context.session(), context.level(), state.casterPokemonUUID());
        if (caster != null) ActionBattleChannelVisuals.emitCancellationBurst(context.level(), caster, "ice");
        DebugLog.log("[CobblemonNML] Hail channel cancelled. Battle=" + state.battleId() + ", caster=" + state.casterPokemonUUID() + ", reason=" + reason);
    }

    private static void pulse(HailCastContext context, ActionBattlePersistentAreaState area) {
        if (context == null || area == null) return;
        ActionBattleHailVisuals.emitPulse(context.level(), area);
        PokemonEntity enemy = context.playerSide()
                ? ActionBattleAreaEffectSupport.activePokemonEntity(context.session(), context.level(), context.session().trainerActivePokemonUUID())
                : ActionBattleAreaEffectSupport.activePokemonEntity(context.session(), context.level(), context.session().playerActivePokemonUUID());
        if (enemy == null || enemy.isRemoved() || !area.contains(enemy.getX(), enemy.getY(), enemy.getZ())) return;
        long currentTick = context.level().getGameTime();
        ActionBattleProtectController.EffectInterception interception = ActionBattleProtectController.global()
                .interceptTimedEffect(area.battleId(), enemy.getPokemon().getUuid(), currentTick, "freeze", 120);
        if (!interception.allowed()) return;
        float durationMultiplier = interception.durationTicks() / 120.0F;
        ActionBattleEffectController.global().applyFreezeCapableHit(area.battleId(), enemy.getPokemon().getUuid(), context.level().getGameTime(), durationMultiplier);
        DebugLog.log("[CobblemonNML] Hail pulse applied Freeze-capable effect. Battle=" + area.battleId() + ", area=" + area.areaId()
                + ", target=" + enemy.getPokemon().getUuid());
    }

    private static void spawnCeilingCloud(ServerLevel level, ActionBattlePersistentAreaState area) {
        if (level == null || area == null) return;
        ActionBattlePosition anchor = area.anchor();
        AreaEffectCloud cloud = new AreaEffectCloud(level, anchor.x(), anchor.y() + HEIGHT, anchor.z());
        cloud.setRadius((float) RADIUS);
        cloud.setDuration(area.preset().durationTicks());
        cloud.setWaitTime(0);
        cloud.setRadiusPerTick(0.0F);
        cloud.setRadiusOnUse(0.0F);
        cloud.setParticle(ParticleTypes.CLOUD);
        cloud.setNoGravity(true);
        cloud.setInvulnerable(true);
        if (!level.addFreshEntity(cloud)) return;
        CEILING_CLOUDS.put(area.areaId(), new HailCloudContext(area.battleId(), level, cloud.getUUID()));
        DebugLog.log("[CobblemonNML] Hail ceiling cloud spawned. Battle=" + area.battleId() + ", area=" + area.areaId()
                + ", position=(" + anchor.x() + ", " + (anchor.y() + HEIGHT) + ", " + anchor.z() + ")");
    }

    private static void cleanupCeilingClouds(UUID battleId) {
        if (battleId == null) return;
        java.util.Set<UUID> activeAreaIds = ActionBattlePersistentAreaController.global().statesForBattle(battleId).stream()
                .map(ActionBattlePersistentAreaState::areaId)
                .collect(java.util.stream.Collectors.toSet());
        CEILING_CLOUDS.entrySet().removeIf(entry -> {
            HailCloudContext context = entry.getValue();
            if (!battleId.equals(context.battleId()) || activeAreaIds.contains(entry.getKey())) return false;
            discardCloud(context);
            return true;
        });
    }

    private static void removeCeilingCloudsForBattle(UUID battleId) {
        if (battleId == null) return;
        CEILING_CLOUDS.entrySet().removeIf(entry -> {
            HailCloudContext context = entry.getValue();
            if (!battleId.equals(context.battleId())) return false;
            discardCloud(context);
            return true;
        });
    }

    private static void discardCloud(HailCloudContext context) {
        if (context == null || context.level() == null || context.entityUUID() == null) return;
        Entity entity = context.level().getEntity(context.entityUUID());
        if (entity != null && !entity.isRemoved()) entity.discard();
    }

    private static boolean holdsIcyRock(PokemonEntity caster) {
        if (caster == null) return false;
        Object pokemon = caster.getPokemon();
        for (String methodName : new String[]{"heldItem", "getHeldItem"}) {
            try {
                Method method = pokemon.getClass().getMethod(methodName);
                Object value = method.invoke(pokemon);
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    return ICY_ROCK_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        return false;
    }

    public enum StartResult { STARTED, TARGET_UNREACHABLE, NO_PP, ALREADY_CHANNELING, INVALID }
    private record HailCastContext(ActionBattleSession session, ServerLevel level, Move move, UUID casterPokemonUUID, boolean playerSide, boolean confused, int cancelAtElapsedTick) {}
    private record HailCloudContext(UUID battleId, ServerLevel level, UUID entityUUID) {}
}

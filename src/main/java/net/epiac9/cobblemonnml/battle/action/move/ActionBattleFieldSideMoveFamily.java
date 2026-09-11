package net.epiac9.cobblemonnml.battle.action.move;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaController;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaPreset;
import net.epiac9.cobblemonnml.battle.action.area.ActionBattlePersistentAreaState;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelCancelReason;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelController;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelPreset;
import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelState;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlController;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleParalysisController;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleParalysisRules;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostCurseType;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostDamageRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostVisuals;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleChannelVisuals;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleHailVisuals;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class ActionBattleFieldSideMoveFamily {
    public static final String MOVE_ID_HAIL = "hail";
    public static final String MOVE_ID_TOXIC_SPIKES = "toxicspikes";

    private ActionBattleFieldSideMoveFamily() {}

    public static boolean isHail(Move move) { return Hail.isHail(move); }
    public static boolean isToxicSpikes(Move move) { return ToxicSpikes.isToxicSpikes(move); }
    public static boolean isFieldSideMove(Move move) { return isHail(move) || isToxicSpikes(move); }

    public static StartResult tryStart(ActionBattleSession session, ServerLevel level, PokemonEntity caster, PokemonEntity target, Move move) {
        return tryStart(session, level, caster, target, move, 0L, false);
    }

    public static StartResult tryStart(ActionBattleSession session, ServerLevel level, PokemonEntity caster, PokemonEntity target, Move move, long confusionBonusTicks, boolean confusionSelfCancel) {
        if (isHail(move)) return Hail.tryStart(session, level, caster, target, move, confusionBonusTicks, confusionSelfCancel);
        if (isToxicSpikes(move)) return ToxicSpikes.tryStart(session, level, caster, target, move, confusionBonusTicks, confusionSelfCancel);
        return StartResult.INVALID;
    }

    public static void tickBattle(ActionBattleSession session, ServerLevel level) {
        Hail.tickBattle(session, level);
        ToxicSpikes.tickBattle(session, level);
    }

    public static void onCommand(UUID casterPokemonUUID) {
        Hail.onCommand(casterPokemonUUID);
        ToxicSpikes.onCommand(casterPokemonUUID);
    }

    public static void onControlEffect(UUID casterPokemonUUID) {
        Hail.onControlEffect(casterPokemonUUID);
        ToxicSpikes.onControlEffect(casterPokemonUUID);
    }

    public static boolean isChanneling(UUID casterPokemonUUID) {
        return Hail.isChanneling(casterPokemonUUID) || ToxicSpikes.isChanneling(casterPokemonUUID);
    }

    public static void clearBattle(UUID battleId) {
        Hail.clearBattle(battleId);
        ToxicSpikes.clearBattle(battleId);
    }

    public enum StartResult { STARTED, SILENCED, TARGET_UNREACHABLE, NO_PP, ALREADY_CHANNELING, INVALID }

    private static final class Hail {
        public static final String MOVE_ID = "hail";
        public static final int CHANNEL_TICKS = 40;
        public static final int FAILURE_COOLDOWN_TICKS = 20;
        public static final int NORMAL_DURATION_TICKS = 180;
        public static final int ICY_ROCK_DURATION_TICKS = 240;
        public static final int PULSE_INTERVAL_TICKS = 20;
        public static final double RADIUS = 7.0D;
        public static final double HEIGHT = 6.0D;

        private static final ActionBattleChannelPreset HAIL_CHANNEL = new ActionBattleChannelPreset(
                CHANNEL_TICKS, true, true, true, true, false);
        private static final ActionBattlePersistentAreaPreset HAIL_NORMAL = new ActionBattlePersistentAreaPreset(RADIUS, HEIGHT, NORMAL_DURATION_TICKS, PULSE_INTERVAL_TICKS, true);
        private static final ActionBattlePersistentAreaPreset HAIL_ICY_ROCK = new ActionBattlePersistentAreaPreset(RADIUS, HEIGHT, ICY_ROCK_DURATION_TICKS, PULSE_INTERVAL_TICKS, true);
        private static final ResourceLocation ICY_ROCK_ID = ResourceLocation.fromNamespaceAndPath("cobblemon", "icy_rock");
        private static final Map<UUID, HailCastContext> CASTS = new HashMap<>();
        private static final Map<UUID, HailCloudContext> CEILING_CLOUDS = new HashMap<>();


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
            if (!FightOrFlightAdapter.hasPp(move)) return StartResult.NO_PP;
            boolean playerSide = session.isPlayerPokemon(casterPokemonUUID);
            boolean confusedChannel = confusionBonusTicks > 0L;
            ActionBattlePosition initialTargetPosition = ActionBattleAreaEffectSupport.targetPosition(caster, target, confusionBonusTicks);
            int totalChannelTicks = ActionBattleAreaEffectSupport.totalChannelTicks(confusionBonusTicks);
            int cancelAtElapsedTick = ActionBattleAreaEffectSupport.cancelAtElapsedTick(caster, totalChannelTicks, confusionSelfCancel);
            int moveSlot = ActionBattleGhostRuntime.global().findMoveSlot(caster, move);
            HailCastContext context = new HailCastContext(session, level, move, casterPokemonUUID,
                    playerSide, confusedChannel, cancelAtElapsedTick, moveSlot, null);
            CASTS.put(casterPokemonUUID, context);
            boolean started = ActionBattleChannelController.global().start(
                    session.battleId(), casterPokemonUUID, confusedChannel ? null : target.getPokemon().getUuid(), MOVE_ID,
                    confusedChannel ? new ActionBattleChannelPreset(totalChannelTicks, true, true, false, true, false) : HAIL_CHANNEL,
                    initialTargetPosition, caster.getPokemon().getCurrentHealth(),
                    Hail::complete,
                    Hail::cancel
            );
            if (!started) {
                CASTS.remove(casterPokemonUUID);
                return StartResult.INVALID;
            }
            ActionBattleGhostDamageRules.CooldownPlan cooldownPlan = ActionBattleGhostRuntime.global()
                    .abilityCooldownPlan(session.battleId(), casterPokemonUUID, moveSlot, currentTick);
            ActionBattleGhostRuntime.global().emitAbilityCooldownConsumption(caster, cooldownPlan);
            CASTS.put(casterPokemonUUID, new HailCastContext(session, level, move, casterPokemonUUID,
                    playerSide, confusedChannel, cancelAtElapsedTick, moveSlot, cooldownPlan));
            caster.getNavigation().stop();
            var silence = ActionBattleGhostRuntime.global().damageRules().silencePlan(
                    session.battleId(), casterPokemonUUID, currentTick, true);
            if (silence.interrupted()) {
                FightOrFlightAdapter.consumeOnePp(caster, move);
                ActionBattleGhostVisuals.emitEvent(caster, ActionBattleGhostCurseType.SILENCE);
                ActionBattleChannelController.global().cancel(
                        casterPokemonUUID, ActionBattleChannelCancelReason.CONTROL_EFFECT);
                return StartResult.SILENCED;
            }
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
            PokemonEntity caster = ActionBattleAreaEffectSupport.activePokemonEntity(
                    context.session(), context.level(), state.casterPokemonUUID());
            if (!FightOrFlightAdapter.consumeOnePp(caster, context.move())) return;
            long currentTick = context.level().getGameTime();
            applyCooldown(context, currentTick);
            if (ActionBattleParalysisRules.failsAction(
                    ActionBattleParalysisController.active(context.session(), state.casterPokemonUUID(), currentTick),
                    caster.getRandom().nextDouble())) {
                DebugLog.log("[CobblemonNML] Paralyzed Hail channel committed but failed at completion. Battle="
                        + state.battleId() + ", caster=" + state.casterPokemonUUID());
                return;
            }
            ActionBattleControlController.global().recordSuccessfulMove(
                    state.battleId(), state.casterPokemonUUID(), context.move());
            ActionBattleProtectController.global().onSuccessfulNonProtectMove(
                    state.battleId(), state.casterPokemonUUID());
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
            DebugLog.log("[CobblemonNML] Hail channel completed. Battle=" + state.battleId() + ", caster=" + state.casterPokemonUUID()
                    + ", anchor=" + state.lastTargetablePosition() + ", durationTicks=" + preset.durationTicks());
        }

        private static void cancel(ActionBattleChannelState state, ActionBattleChannelCancelReason reason) {
            HailCastContext context = CASTS.remove(state.casterPokemonUUID());
            if (context == null) return;
            if (reason == ActionBattleChannelCancelReason.TARGET_UNREACHABLE) {
                context.session().setPokemonAllCommandCooldown(state.casterPokemonUUID(), context.level().getGameTime(), FAILURE_COOLDOWN_TICKS);
            } else if (reason == ActionBattleChannelCancelReason.DAMAGE
                    || reason == ActionBattleChannelCancelReason.COMMAND
                    || reason == ActionBattleChannelCancelReason.CONTROL_EFFECT
                    || reason == ActionBattleChannelCancelReason.CONFUSION_SELF_CANCEL) {
                applyCooldown(context, context.level().getGameTime());
            }
            PokemonEntity caster = ActionBattleAreaEffectSupport.activePokemonEntity(context.session(), context.level(), state.casterPokemonUUID());
            if (caster != null) ActionBattleChannelVisuals.emitCancellationBurst(context.level(), caster, "ice");
            DebugLog.log("[CobblemonNML] Hail channel cancelled. Battle=" + state.battleId() + ", caster=" + state.casterPokemonUUID() + ", reason=" + reason);
        }

        private static void pulse(HailCastContext context, ActionBattlePersistentAreaState area) {
            if (context == null || area == null) return;
            ActionBattleHailVisuals.emitPulse(context.level(), area);
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

        private static void applyCooldown(HailCastContext context, long currentTick) {
            ActionBattleGhostDamageRules.CooldownPlan plan = context.cooldownPlan();
            long shared = plan != null ? plan.sharedTicks() : ActionBattleTiming.ABILITY_SHARED_COOLDOWN_TICKS;
            context.session().startPokemonSharedAbilityCooldown(context.casterPokemonUUID(), currentTick, shared);
            if (plan != null && plan.personalTicks() > 0L && context.moveSlot() >= 0) {
                context.session().startPokemonPersonalMoveCooldown(context.casterPokemonUUID(), context.moveSlot(),
                        currentTick, plan.personalTicks());
            }
        }
    private record HailCastContext(ActionBattleSession session, ServerLevel level, Move move,
                                       UUID casterPokemonUUID, boolean playerSide, boolean confused,
                                       int cancelAtElapsedTick, int moveSlot,
                                       ActionBattleGhostDamageRules.CooldownPlan cooldownPlan) {}
        private record HailCloudContext(UUID battleId, ServerLevel level, UUID entityUUID) {}
    }

    private static final class ToxicSpikes {
        public static final String MOVE_ID = "toxicspikes";
        public static final int CHANNEL_TICKS = 40;
        public static final int FAILURE_COOLDOWN_TICKS = 20;
        public static final int DURATION_TICKS = 180;
        public static final int PULSE_INTERVAL_TICKS = 20;
        public static final double RADIUS = 7.0D;
        public static final double HEIGHT = 2.0D;

        private static final ActionBattleChannelPreset CHANNEL = new ActionBattleChannelPreset(
                CHANNEL_TICKS, true, true, true, true, true);
        private static final ActionBattlePersistentAreaPreset AREA = new ActionBattlePersistentAreaPreset(RADIUS, HEIGHT, DURATION_TICKS, PULSE_INTERVAL_TICKS, true);
        private static final ActionBattleChannelController CHANNELS = new ActionBattleChannelController();
        private static final ActionBattlePersistentAreaController AREAS = new ActionBattlePersistentAreaController();
        private static final Map<UUID, CastContext> CASTS = new HashMap<>();


        public static boolean isToxicSpikes(Move move) { return move != null && MOVE_ID.equals(move.getName()); }

        public static StartResult tryStart(ActionBattleSession session, ServerLevel level, PokemonEntity caster, PokemonEntity target, Move move) {
            return tryStart(session, level, caster, target, move, 0L, false);
        }

        public static StartResult tryStart(ActionBattleSession session, ServerLevel level, PokemonEntity caster, PokemonEntity target, Move move, long confusionBonusTicks, boolean confusionSelfCancel) {
            if (session == null || level == null || caster == null || move == null || (target == null && confusionBonusTicks <= 0L) || !isToxicSpikes(move)) return StartResult.INVALID;
            UUID casterPokemonUUID = caster.getPokemon().getUuid();
            if (CHANNELS.isChanneling(casterPokemonUUID)) return StartResult.ALREADY_CHANNELING;
            long currentTick = level.getGameTime();
            if (confusionBonusTicks <= 0L && !FightOrFlightAdapter.canCommitHail(caster, target)) {
                session.setPokemonAllCommandCooldown(casterPokemonUUID, currentTick, FAILURE_COOLDOWN_TICKS);
                return StartResult.TARGET_UNREACHABLE;
            }
            if (!FightOrFlightAdapter.consumeOnePp(caster, move)) return StartResult.NO_PP;
            ActionBattleGhostRuntime.global().applyAbilityCooldown(session, caster,
                    ActionBattleGhostRuntime.global().findMoveSlot(caster, move), currentTick);
            boolean playerSide = session.isPlayerPokemon(casterPokemonUUID);
            boolean confusedChannel = confusionBonusTicks > 0L;
            ActionBattlePosition initialTargetPosition = ActionBattleAreaEffectSupport.targetPosition(caster, target, confusionBonusTicks);
            int totalChannelTicks = ActionBattleAreaEffectSupport.totalChannelTicks(confusionBonusTicks);
            int cancelAtElapsedTick = ActionBattleAreaEffectSupport.cancelAtElapsedTick(caster, totalChannelTicks, confusionSelfCancel);
            CastContext context = new CastContext(session, level, move, casterPokemonUUID, playerSide, confusedChannel, cancelAtElapsedTick);
            CASTS.put(casterPokemonUUID, context);
            boolean started = CHANNELS.start(
                    session.battleId(), casterPokemonUUID, confusedChannel ? null : target.getPokemon().getUuid(), MOVE_ID,
                    confusedChannel ? new ActionBattleChannelPreset(totalChannelTicks, true, true, false, true, true) : CHANNEL,
                    initialTargetPosition, caster.getPokemon().getCurrentHealth(),
                    ToxicSpikes::complete,
                    ToxicSpikes::cancel
            );
            if (!started) {
                CASTS.remove(casterPokemonUUID);
                FightOrFlightAdapter.refundOnePp(move);
                return StartResult.INVALID;
            }
            caster.getNavigation().stop();
            DebugLog.log("[CobblemonNML] Toxic Spikes channel started. Battle=" + session.battleId() + ", caster=" + casterPokemonUUID + ", target=" + (target != null ? target.getPokemon().getUuid() : "none") + ", confused=" + confusedChannel + ", durationTicks=" + totalChannelTicks);
            return StartResult.STARTED;
        }

        public static void tickBattle(ActionBattleSession session, ServerLevel level) {
            if (session == null || level == null) return;
            AREAS.tick(session.battleId());
            for (ActionBattlePersistentAreaState area : AREAS.statesForBattle(session.battleId())) emitAreaAmbient(level, area);
            for (ActionBattleChannelState state : CHANNELS.statesForBattle(session.battleId())) {
                CastContext context = CASTS.get(state.casterPokemonUUID());
                if (context != null && context.confused() && context.cancelAtElapsedTick() >= 0 && state.elapsedTicks() >= context.cancelAtElapsedTick()) {
                    CHANNELS.cancel(state.casterPokemonUUID(), ActionBattleChannelCancelReason.CONFUSION_SELF_CANCEL);
                }
            }
            CHANNELS.tick(session.battleId(), state -> trackTarget(session, level, state));
            for (ActionBattleChannelState state : CHANNELS.statesForBattle(session.battleId())) {
                PokemonEntity caster = ActionBattleAreaEffectSupport.activePokemonEntity(session, level, state.casterPokemonUUID());
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
            if (context.confused()) return new ActionBattleChannelController.TargetUpdate(true, state.lastTargetablePosition());
            PokemonEntity caster = ActionBattleAreaEffectSupport.activePokemonEntity(session, level, state.casterPokemonUUID());
            PokemonEntity target = ActionBattleAreaEffectSupport.activePokemonEntity(session, level, state.targetPokemonUUID());
            if (caster == null || target == null || caster.isRemoved() || target.isRemoved() || !FightOrFlightAdapter.canCommitHail(caster, target)) {
                return new ActionBattleChannelController.TargetUpdate(false, null);
            }
            return new ActionBattleChannelController.TargetUpdate(true, ActionBattleAreaEffectSupport.positionOf(target));
        }

        private static void complete(ActionBattleChannelState state) {
            CastContext context = CASTS.remove(state.casterPokemonUUID());
            if (context == null || state.lastTargetablePosition() == null) return;
            PokemonEntity caster = ActionBattleAreaEffectSupport.activePokemonEntity(
                    context.session(), context.level(), state.casterPokemonUUID());
            long currentTick = context.level().getGameTime();
            if (caster != null && ActionBattleParalysisRules.failsAction(
                    ActionBattleParalysisController.active(context.session(), state.casterPokemonUUID(), currentTick),
                    caster.getRandom().nextDouble())) {
                DebugLog.log("[CobblemonNML] Paralyzed Toxic Spikes channel committed but failed at completion. Battle="
                        + state.battleId() + ", caster=" + state.casterPokemonUUID());
                return;
            }
            AREAS.create(state.battleId(), state.casterPokemonUUID(), MOVE_ID, state.lastTargetablePosition(), AREA, area -> pulse(context, area));
            DebugLog.log("[CobblemonNML] Toxic Spikes channel completed. Battle=" + state.battleId() + ", caster=" + state.casterPokemonUUID() + ", anchor=" + state.lastTargetablePosition());
        }

        private static void cancel(ActionBattleChannelState state, ActionBattleChannelCancelReason reason) {
            CastContext context = CASTS.remove(state.casterPokemonUUID());
            if (context == null) return;
            if (reason == ActionBattleChannelCancelReason.TARGET_UNREACHABLE) {
                FightOrFlightAdapter.refundOnePp(context.move());
                context.session().setPokemonAllCommandCooldown(state.casterPokemonUUID(), context.level().getGameTime(), FAILURE_COOLDOWN_TICKS);
            }
            PokemonEntity caster = ActionBattleAreaEffectSupport.activePokemonEntity(context.session(), context.level(), state.casterPokemonUUID());
            if (caster != null) ActionBattleChannelVisuals.emitCancellationBurst(context.level(), caster, "poison");
            DebugLog.log("[CobblemonNML] Toxic Spikes channel cancelled. Battle=" + state.battleId() + ", caster=" + state.casterPokemonUUID() + ", reason=" + reason);
        }

        private static void pulse(CastContext context, ActionBattlePersistentAreaState area) {
            if (context == null || area == null) return;
            emitPulse(context.level(), area);
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
    private record CastContext(ActionBattleSession session, ServerLevel level, Move move, UUID casterPokemonUUID, boolean playerSide, boolean confused, int cancelAtElapsedTick) {}
    }
}

package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.compat.ActionBattleMoveEffectResolver;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleConfusionRules;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleProtectMoveFamily;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleFieldSideMoveFamily;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveTimingRules;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleRampageController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattlePropulsionRules;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleParalysisController;
import net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleParalysisRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class ActionBattleTrainerAiController {
    private ActionBattleTrainerAiController() {}

    private static java.util.UUID targetPlayer(ActionBattleSession session) {
        if (session == null) return null;
        java.util.Set<java.util.UUID> candidates = session.arena() != null
                ? session.arena().insideParticipants() : java.util.Set.of(session.playerUUID());
        for (java.util.UUID playerUUID : candidates) {
            if (session.playerActiveEntityUUID(playerUUID) != null) return playerUUID;
        }
        return null;
    }

    private static Pokemon targetPokemon(ActionBattleSession session, ActionBattlePokemonRefs refs) {
        java.util.UUID playerUUID = targetPlayer(session);
        return playerUUID != null && refs != null ? refs.playerPokemon(playerUUID) : null;
    }

    static void tick(ActionBattleSession session, ServerLevel level, ActionBattlePokemonRefs refs) {
        if (session == null || level == null || refs == null || session.state() != ActionBattleState.ACTIVE) return;
        if (session.arena() != null && session.arena().isEmpty()) return;
        if (session.isTrainerSendOutPending() || session.isPlayerSendOutPending()) return;
        java.util.UUID targetPlayerUUID = targetPlayer(session);
        Pokemon playerPokemon = targetPlayerUUID != null ? refs.playerPokemon(targetPlayerUUID) : null;
        java.util.UUID playerEntityUUID = targetPlayerUUID != null ? session.playerActiveEntityUUID(targetPlayerUUID) : null;
        if (refs.trainerPokemon() == null || playerPokemon == null) return;
        Pokemon trainerPokemon = refs.trainerPokemon();
        if (trainerPokemon.isFainted() || playerPokemon.isFainted()) return;
        if (session.trainerActiveEntityUUID() == null || playerEntityUUID == null) return;
        Entity rawTrainerPokemon = level.getEntity(session.trainerActiveEntityUUID());
        Entity rawPlayerPokemon = level.getEntity(playerEntityUUID);
        if (!(rawTrainerPokemon instanceof PokemonEntity trainerPokemonEntity) || trainerPokemonEntity.isRemoved()) return;
        if (!(rawPlayerPokemon instanceof PokemonEntity playerPokemonEntity) || playerPokemonEntity.isRemoved()) {
            stopTrainerMovement(session, trainerPokemonEntity, ActionBattleCommandController.InterruptReason.TARGET_INVALID);
            return;
        }

        long currentTick = level.getGameTime();
        if (net.epiac9.cobblemonnml.battle.action.interrupt.ActionBattleInterruptController.isActive(
                session.battleId(), trainerPokemon.getUuid(), currentTick)) {
            trainerPokemonEntity.getNavigation().stop();
            session.clearTrainerMoveState();
            return;
        }
        if (net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleInfatuationController
                .blocksCommands(session.battleId(), trainerPokemon.getUuid(), currentTick)) {
            trainerPokemonEntity.getNavigation().stop();
            session.clearTrainerMoveState();
            return;
        }
        if (net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime
                .isRoarStunned(trainerPokemon.getUuid())) {
            stopTrainerMovement(session, trainerPokemonEntity,
                    ActionBattleCommandController.InterruptReason.CONTROL_EFFECT);
            return;
        }
        if (session.trainerRepositionAttempt() >= ActionBattleTrainerTactics.maxRepositionAttempts()) {
            handleExhaustedReposition(session, level, refs, trainerPokemon, trainerPokemonEntity, currentTick);
            return;
        }
        if (!ActionBattleSleepController.canIssueCommand(session, trainerPokemon.getUuid(), currentTick,
                ActionBattleSleepController.CommandKind.MOVE)) {
            stopTrainerMovement(session, trainerPokemonEntity, ActionBattleCommandController.InterruptReason.SLEEP);
            return;
        }
        if (ActionBattleCommandController.isChanneling(trainerPokemon.getUuid())) {
            trainerPokemonEntity.getNavigation().stop();
            return;
        }
        boolean enemyVisible = ActionBattleDarkRuntime.canPerceive(
                session, trainerPokemonEntity, playerPokemonEntity, currentTick);
        if (!session.hasTrainerMoveCommand()) {
            int moveSlot = selectMoveSlot(session, trainerPokemon, playerPokemon, trainerPokemonEntity,
                    playerPokemonEntity, currentTick, enemyVisible);
            if (moveSlot < 0) return;
            Move selectedMove = trainerPokemon.getMoveSet().get(moveSlot);
            ActionBattleCommandController.onCommandIssued(session, trainerPokemon.getUuid());
            if (handleConfusedCommand(session, level, trainerPokemon, trainerPokemonEntity, selectedMove, currentTick)) return;
            long revision = session.replaceTrainerMoveCommand(moveSlot, playerEntityUUID);
            DebugLog.log("[CobblemonNML] Trainer AI move " + (moveSlot + 1) + " queued. Battle=" + session.battleId()
                    + ", revision=" + revision + ", target=" + playerEntityUUID);
        }

        if (!playerEntityUUID.equals(session.trainerMoveTargetEntityUUID())) {
            stopTrainerMovement(session, trainerPokemonEntity, ActionBattleCommandController.InterruptReason.TARGET_INVALID);
            return;
        }
        Move move = trainerPokemon.getMoveSet().get(session.trainerMoveSlot());
        if (move == null || !FightOrFlightAdapter.supports(move) || !FightOrFlightAdapter.hasPp(move) || !ActionBattleControlController.global().canUseMove(session.battleId(), trainerPokemon.getUuid(), move, currentTick)
                || !ActionBattleRampageController.global().canUseAbility(session.battleId(), trainerPokemon.getUuid(), move.getName(), currentTick)) {
            stopTrainerMovement(session, trainerPokemonEntity, ActionBattleCommandController.InterruptReason.TARGET_INVALID);
            return;
        }
        boolean enemyTargeted = !FightOrFlightAdapter.isSelfOrAllyTargetCategory(
                FightOrFlightAdapter.moveTargetCategory(move));
        if (enemyTargeted && !enemyVisible) {
            trainerPokemonEntity.setTarget(null);
            stopTrainerMovement(session, trainerPokemonEntity,
                    ActionBattleCommandController.InterruptReason.TARGET_INVALID);
            return;
        }
        if (!ActionBattleMovementActionRules.canUseAction(
                ActionBattleMovementActionRules.isMovementBlocked(session, trainerPokemon.getUuid(), currentTick),
                ActionBattleMovementActionRules.requiresMovement(move))) {
            stopTrainerMovement(session, trainerPokemonEntity, ActionBattleCommandController.InterruptReason.CONTROL_EFFECT);
            return;
        }
        boolean onCooldown = session.isPokemonAbilitySlotOnCooldown(
                trainerPokemon.getUuid(), session.trainerMoveSlot(), currentTick);
        if (!onCooldown && ActionBattleProtectMoveFamily.isBalefulBunker(move)) {
            trainerPokemonEntity.getNavigation().stop();
            ActionBattleProtectMoveFamily.StartResult result = ActionBattleProtectMoveFamily.tryStart(session, trainerPokemonEntity, move);
            if (result == ActionBattleProtectMoveFamily.StartResult.STARTED) dispatchOwnedMechanics(trainerPokemonEntity, trainerPokemonEntity, move);
            finishTrainerMove(session, trainerPokemon, move, currentTick, result == ActionBattleProtectMoveFamily.StartResult.STARTED, () -> {});
            DebugLog.log("[CobblemonNML] Trainer Baleful Bunker ACTION start result. Battle=" + session.battleId() + ", result=" + result);
            return;
        }
        if (!onCooldown && ActionBattleFieldSideMoveFamily.isHail(move) && FightOrFlightAdapter.canCommit(trainerPokemonEntity, playerPokemonEntity, move)) {
            if (!trainerMoveStartupReady(session, trainerPokemonEntity, move, currentTick)) {
                trainerPokemonEntity.getNavigation().stop();
                return;
            }
            if (!ActionBattleParalysisController.executionReady(trainerPokemonEntity,
                    ActionBattleParalysisController.active(session, trainerPokemon.getUuid(), currentTick), currentTick)) return;
            trainerPokemonEntity.getNavigation().stop();
            ActionBattleFieldSideMoveFamily.StartResult result = ActionBattleFieldSideMoveFamily.tryStart(session, level, trainerPokemonEntity, playerPokemonEntity, move);
            if (result == ActionBattleFieldSideMoveFamily.StartResult.STARTED) dispatchOwnedMechanics(trainerPokemonEntity, playerPokemonEntity, move);
            session.clearTrainerMoveState();
            DebugLog.log("[CobblemonNML] Trainer Hail ACTION start result. Battle=" + session.battleId() + ", result=" + result);
            return;
        }
        if (!onCooldown && ActionBattleFieldSideMoveFamily.isToxicSpikes(move) && FightOrFlightAdapter.canCommit(trainerPokemonEntity, playerPokemonEntity, move)) {
            if (!trainerMoveStartupReady(session, trainerPokemonEntity, move, currentTick)) {
                trainerPokemonEntity.getNavigation().stop();
                return;
            }
            if (!ActionBattleParalysisController.executionReady(trainerPokemonEntity,
                    ActionBattleParalysisController.active(session, trainerPokemon.getUuid(), currentTick), currentTick)) return;
            trainerPokemonEntity.getNavigation().stop();
            ActionBattleFieldSideMoveFamily.StartResult result = ActionBattleFieldSideMoveFamily.tryStart(session, level, trainerPokemonEntity, playerPokemonEntity, move);
            if (result == ActionBattleFieldSideMoveFamily.StartResult.STARTED) dispatchOwnedMechanics(trainerPokemonEntity, playerPokemonEntity, move);
            finishTrainerMove(session, trainerPokemon, move, currentTick, result == ActionBattleFieldSideMoveFamily.StartResult.STARTED, () -> ActionBattleProtectController.global().onSuccessfulNonProtectMove(session.battleId(), trainerPokemon.getUuid()));
            DebugLog.log("[CobblemonNML] Trainer Toxic Spikes ACTION start result. Battle=" + session.battleId() + ", result=" + result);
            return;
        }
        int momentum = ActionBattleFlyingRuntime.momentum(session, trainerPokemon.getUuid());
        ActionBattlePropulsionRules.CommitMode commitMode = FightOrFlightAdapter.commitMode(
                trainerPokemonEntity, playerPokemonEntity, move, momentum);
        if (!onCooldown && commitMode != ActionBattlePropulsionRules.CommitMode.REPOSITION) {
            if (!trainerMoveStartupReady(session, trainerPokemonEntity, move, currentTick)) {
                trainerPokemonEntity.getNavigation().stop();
                return;
            }
            if (!ActionBattleParalysisController.executionReady(trainerPokemonEntity,
                    ActionBattleParalysisController.active(session, trainerPokemon.getUuid(), currentTick), currentTick)) return;
            trainerPokemonEntity.getNavigation().stop();
            if (!FightOrFlightAdapter.consumeOnePp(trainerPokemonEntity, move)) {
                ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.MOVE_FAILED);
                return;
            }
            if (ActionBattleParalysisRules.failsAction(
                    ActionBattleParalysisController.active(session, trainerPokemon.getUuid(), currentTick),
                    trainerPokemonEntity.getRandom().nextDouble())) {
                ActionBattleGhostRuntime.global().applyAbilityCooldown(
                        session, trainerPokemonEntity, session.trainerMoveSlot(), currentTick);
                session.clearTrainerMoveState();
                DebugLog.log("[CobblemonNML] Paralyzed trainer ACTION move committed but failed. Battle="
                        + session.battleId() + ", move=" + move.getName());
                return;
            }
            ActionBattleCommittedMove committedMove = captureCommittedMove(session, trainerPokemonEntity, move);
            BlockPos plasmaTarget = ActionBattleTypeMechanicIdentity.hasMechanicBenefit(trainerPokemonEntity, "electric")
                    && !ActionBattleTypeMechanicIdentity.hasMechanicBenefit(trainerPokemonEntity, "psychic")
                    && FightOrFlightAdapter.isRangedMove(move) && FightOrFlightAdapter.movePower(move) > 0
                    && ActionBattleElectricController.activeCount(session.dungeonSessionId()) > 3
                    ? ActionBattleElectricController.nearestPlasmaBallTo(
                    session.dungeonSessionId(), playerPokemonEntity.blockPosition()) : null;
            boolean executed = plasmaTarget != null
                    ? FightOrFlightAdapter.executeRangedAtPoint(trainerPokemonEntity, playerPokemonEntity, move,
                    net.minecraft.world.phys.Vec3.atCenterOf(plasmaTarget), 1.0D, committedMove)
                    : FightOrFlightAdapter.execute(trainerPokemonEntity, playerPokemonEntity, move, 1.0D, committedMove);
            if (executed) {
                if (plasmaTarget != null) {
                    DebugLog.log("[CobblemonNML] Electric trainer AI aimed at Plasma Ball. Battle="
                            + session.battleId() + ", ball=" + plasmaTarget + ", enemy="
                            + playerPokemonEntity.blockPosition());
                }
                net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime.onSuccessfulSelfMoveCommit(trainerPokemonEntity, move);
                long cooldownTicks = ActionBattleGhostRuntime.global().applyAbilityCooldown(
                        session, trainerPokemonEntity, session.trainerMoveSlot(), currentTick).sharedTicks();
                ActionBattleProtectController.global().onSuccessfulNonProtectMove(session.battleId(), trainerPokemon.getUuid());
                ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), trainerPokemon.getUuid(), move);
                session.clearTrainerMoveState();
                DebugLog.log("[CobblemonNML] Trainer AI move committed through Fight or Flight. Battle=" + session.battleId()
                        + ", move=" + move.getName() + ", cooldownTicks=" + cooldownTicks);
            } else {
                FightOrFlightAdapter.refundOnePp(move);
                if (commitMode == ActionBattlePropulsionRules.CommitMode.PROPULSION) {
                    repositionPendingMove(session, trainerPokemon, trainerPokemonEntity,
                            playerPokemonEntity, move, false, currentTick);
                } else {
                    ActionBattleCommandController.cancelPendingOrders(session,
                            ActionBattleCommandController.Side.TRAINER,
                            ActionBattleCommandController.InterruptReason.MOVE_FAILED);
                }
            }
            return;
        }
        session.resetTrainerMoveCommitReady();
        repositionPendingMove(session, trainerPokemon, trainerPokemonEntity, playerPokemonEntity, move, onCooldown, currentTick);
    }

    private static boolean trainerMoveStartupReady(ActionBattleSession session, PokemonEntity trainerEntity,
                                                   Move move, long currentTick) {
        if (session == null || trainerEntity == null || move == null) return false;
        int speedStage = ActionBattleStatResolver.effectiveStage(session.battleId(),
                trainerEntity.getPokemon().getUuid(), ActionBattleStat.SPEED, currentTick);
        int priority = FightOrFlightAdapter.movePriority(move);
        long readySince = session.markTrainerMoveCommitReady(currentTick);
        return ActionBattleMoveTimingRules.ready(readySince, currentTick, priority, speedStage);
    }


    private static void finishTrainerMove(ActionBattleSession session, Pokemon trainerPokemon, Move move, long currentTick, boolean started, Runnable sideEffect) {
        if (started && trainerPokemon != null && move != null) {
            ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), trainerPokemon.getUuid(), move);
        }
        if (sideEffect != null) sideEffect.run();
        session.clearTrainerMoveState();
    }

    private static void stopTrainerMovement(ActionBattleSession session, PokemonEntity trainerEntity, ActionBattleCommandController.InterruptReason reason) {
        if (trainerEntity != null) trainerEntity.getNavigation().stop();
        if (session != null && reason != null) {
            ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, reason);
        }
    }

    private static boolean handleConfusedCommand(ActionBattleSession session, ServerLevel level, Pokemon trainerPokemon,
                                                   PokemonEntity trainerEntity, Move move, long currentTick) {
        if (move == null) return false;
        ActionBattleConfusionRules.CommandKind kind = ActionBattleConfusionRules.commandKindFor(move);
        ActionBattleConfusionController.CommandPlan plan = ActionBattleConfusionController.roll(session, trainerEntity, kind, currentTick);
        if (!plan.corrupted()) return false;

        trainerEntity.getNavigation().stop();
        session.resetTrainerRepositionState();
        if (kind == ActionBattleConfusionRules.CommandKind.PROTECT) {
            if (!FightOrFlightAdapter.consumeOnePp(trainerEntity, move)) return true;
            ActionBattleGhostRuntime.global().applyAbilityCooldown(session,
                    trainerEntity, session.trainerMoveSlot(), currentTick);
            ActionBattleProtectController.global().recordFailedProtectAttempt(session.battleId(),
                    trainerPokemon.getUuid(), trainerEntity.getRandom().nextBoolean() ? 2 : 1);
            DebugLog.log("[CobblemonNML] Trainer Confusion caused move to fail without effect. Battle=" + session.battleId() + ", move=" + move.getName());
            return true;
        }
        if (kind == ActionBattleConfusionRules.CommandKind.SUPPORT) {
            if (!FightOrFlightAdapter.consumeOnePp(trainerEntity, move)) return true;
            ActionBattleGhostRuntime.global().applyAbilityCooldown(session,
                    trainerEntity, session.trainerMoveSlot(), currentTick);
            Pokemon enemyPokemon = ActionBattleManager.findActivePokemon(
                    session.playerActivePokemonUUID(session.playerUUID()));
            PokemonEntity enemy = enemyPokemon != null ? enemyPokemon.getEntity() : null;
            if (ActionBattleMoveEffectResolver.applyCorruptedSupport(
                    trainerEntity, enemy, move, plan.supportCorruption())) {
                ActionBattleControlController.global().recordSuccessfulMove(
                        session.battleId(), trainerPokemon.getUuid(), move);
                dispatchOwnedMechanics(trainerEntity, enemy, move);
            }
            return true;
        }
        if (kind == ActionBattleConfusionRules.CommandKind.RANGED) {
            if (!FightOrFlightAdapter.consumeOnePp(trainerEntity, move)) return true;
            ActionBattleGhostRuntime.global().applyAbilityCooldown(session,
                    trainerEntity, session.trainerMoveSlot(), currentTick);
            ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), trainerPokemon.getUuid(), move);
            Pokemon confusedPokemon = ActionBattleManager.findActivePokemon(
                    session.playerActivePokemonUUID(session.playerUUID()));
            PokemonEntity confusedTarget = confusedPokemon != null ? confusedPokemon.getEntity() : null;
            var redirectedDirection = ActionBattleConfusionController.corruptedShotDirection(
                    trainerEntity, confusedTarget, plan.rangedCorruption());
            ActionBattleCommittedMove committedMove = captureCommittedMove(session, trainerEntity, move);
            FightOrFlightAdapter.executeConfusedRanged(trainerEntity, move, redirectedDirection,
                    1.0D, committedMove);
            net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime.onSuccessfulSelfMoveCommit(trainerEntity, move);
            DebugLog.log("[CobblemonNML] Trainer Confusion fired ranged move in random direction. Battle=" + session.battleId() + ", move=" + move.getName());
            return true;
        }
        if (kind == ActionBattleConfusionRules.CommandKind.MELEE) {
            if (!FightOrFlightAdapter.consumeOnePp(trainerEntity, move)) return true;
            ActionBattleGhostRuntime.global().applyAbilityCooldown(session,
                    trainerEntity, session.trainerMoveSlot(), currentTick);
            ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), trainerPokemon.getUuid(), move);
            ActionBattleCommittedMove committedMove = captureCommittedMove(session, trainerEntity, move);
            ActionBattleConfusionController.startMeleeDash(session, level, trainerEntity, move, currentTick,
                    1.0D, committedMove);
            dispatchOwnedMechanics(trainerEntity, null, move);
            net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime.onSuccessfulSelfMoveCommit(trainerEntity, move);
            DebugLog.log("[CobblemonNML] Trainer Confusion started uncontrolled melee dash. Battle=" + session.battleId() + ", move=" + move.getName());
            return true;
        }
        if (kind == ActionBattleConfusionRules.CommandKind.CHANNEL) {
            if (ActionBattleFieldSideMoveFamily.isHail(move)) {
                var result = ActionBattleFieldSideMoveFamily.tryStart(session, level, trainerEntity, null, move, plan.channelBonusTicks(), plan.channelSelfCancel());
                if (result == ActionBattleFieldSideMoveFamily.StartResult.STARTED) dispatchOwnedMechanics(trainerEntity, null, move);
            } else {
                var result = ActionBattleFieldSideMoveFamily.tryStart(session, level, trainerEntity, null, move, plan.channelBonusTicks(), plan.channelSelfCancel());
                if (result == ActionBattleFieldSideMoveFamily.StartResult.STARTED) {
                    ActionBattleControlController.global().recordSuccessfulMove(session.battleId(), trainerPokemon.getUuid(), move);
                    dispatchOwnedMechanics(trainerEntity, null, move);
                }
            }
            return true;
        }
        return false;
    }

    private static void dispatchOwnedMechanics(PokemonEntity pokemon, LivingEntity target, Move move) {
        net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicRuntime.onActionStarted(
                net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext.started(
                        pokemon, target, move));
    }

    private static ActionBattleCommittedMove captureCommittedMove(ActionBattleSession session,
                                                                   PokemonEntity attacker, Move move) {
        int momentum = ActionBattleFlyingRuntime.momentum(session, attacker.getPokemon().getUuid());
        return ActionBattleCommittedMove.capture(attacker, move, momentum, attacker.getRandom()::nextDouble);
    }

    private static void handleExhaustedReposition(ActionBattleSession session, ServerLevel level, ActionBattlePokemonRefs refs,
                                                   Pokemon trainerPokemon, PokemonEntity trainerPokemonEntity, long currentTick) {
        trainerPokemonEntity.getNavigation().stop();
        session.clearTrainerMoveState();
        if (session.isTrainerSwapOnCooldown(currentTick)) {
            session.resetTrainerRepositionState();
            DebugLog.log("[CobblemonNML] Trainer voluntary swap is on cooldown; continuing reposition attempts. Battle=" + session.battleId());
            return;
        }
        Entity rawTrainer = level.getEntity(session.trainerUUID());
        ServerPlayer player = ActionBattlePokemonRuntime.findServerPlayer(session);
        if (!(rawTrainer instanceof LivingEntity trainerEntity) || trainerEntity.isRemoved() || player == null || player.level() != level) {
            session.resetTrainerRepositionState();
            return;
        }
        TrainerNPC runtimeTrainer = ActionBattleTrainerResolver.resolve(session.runtimeTrainerId(), trainerEntity);
        if (runtimeTrainer == null) {
            session.resetTrainerRepositionState();
            return;
        }
        if (ActionBattleControlController.global().blocksSwap(session.battleId(), trainerPokemon.getUuid(), currentTick)) {
            session.resetTrainerRepositionState();
            DebugLog.log("[CobblemonNML] Trainer voluntary swap blocked by Trapped. Battle=" + session.battleId());
            return;
        }
        if (ActionBattleMovementActionRules.isVoluntaryRecallBlocked(session, trainerPokemon.getUuid(), currentTick)) {
            session.resetTrainerRepositionState();
            DebugLog.log("[CobblemonNML] Trainer voluntary swap blocked by Ground depth. Battle=" + session.battleId());
            return;
        }
        if (ActionBattleGhostRuntime.global().blocksVoluntarySwap(
                session.battleId(), trainerPokemon.getUuid(), currentTick)) {
            session.resetTrainerRepositionState();
            DebugLog.log("[CobblemonNML] Trainer voluntary swap blocked by Binding. Battle="
                    + session.battleId());
            return;
        }
        Pokemon targetPokemon = targetPokemon(session, refs);
        if (targetPokemon == null) return;
        int currentScore = swapScore(trainerPokemon, targetPokemon);
        ActionBattlePokemonSelection.Selection replacement = findBetterSwapCandidate(runtimeTrainer, session.trainerActivePartyIndex(), currentScore, targetPokemon);
        if (replacement == null) {
            session.resetTrainerRepositionState();
            DebugLog.log("[CobblemonNML] Trainer AI found no meaningfully better voluntary swap; continuing reposition attempts. Battle=" + session.battleId());
            return;
        }
        int previousSlot = session.trainerActivePartyIndex();
        session.setTrainerSendOutPending(true);
        ActionBattleSwapTransitionGuard.begin(session.battleId(), ActionBattleCommandController.Side.TRAINER,
                trainerPokemon.getUuid(), replacement.pokemon().getUuid());
        ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.SWAP);
        ActionBattleEffectRuntime.onPokemonUnavailable(session, trainerPokemon.getUuid(), false, currentTick);
        ActionBattlePokemonRuntime.recall(trainerPokemon);
        session.clearTrainerActivePokemon();
        refs.setTrainerPokemon(replacement.pokemon());
        ActionBattlePokemonRuntime.seedDamageFeedback(session, replacement.pokemon());
        ActionBattlePokemonRuntime.sendOut(session, false, trainerEntity, player, replacement);
        session.startTrainerSwapCooldown(currentTick, ActionBattleTiming.SWAP_COOLDOWN_TICKS);
        DebugLog.log("[CobblemonNML] Trainer voluntary swap started. Battle=" + session.battleId() + ", fromSlot=" + previousSlot
                + ", toSlot=" + replacement.slot() + ", cooldownTicks=" + ActionBattleTiming.SWAP_COOLDOWN_TICKS);
    }

    private static ActionBattlePokemonSelection.Selection findBetterSwapCandidate(TrainerNPC trainer, int currentIndex, int currentScore, Pokemon targetPokemon) {
        if (trainer == null || trainer.getTeam() == null || trainer.getTeam().length == 0) return null;
        Pokemon[] team = trainer.getTeam();
        int tier = aiTier();
        for (int offset = 1; offset < team.length; offset++) {
            int slot = Math.floorMod(currentIndex + offset, team.length);
            Pokemon candidate = team[slot];
            if (candidate == null || candidate.isFainted()) continue;
            int engagement = engagementScore(candidate);
            double hpRatio = hpRatio(candidate);
            double typeMultiplier = bestTypeMultiplier(candidate, targetPokemon);
            int candidateScore = ActionBattleTrainerAiTier.swapScore(tier, engagement, hpRatio, typeMultiplier);
            if (ActionBattleTrainerTactics.isMeaningfullyBetter(currentScore, candidateScore)) {
                if (tier >= 2) {
                    DebugLog.log("[CobblemonNML] Trainer AI swap evaluation. Tier=" + tier + ", currentScore=" + currentScore
                            + ", candidateScore=" + candidateScore + ", candidateSlot=" + slot + ", engagement=" + engagement
                            + ", hpRatio=" + hpRatio + ", bestTypeMultiplier=" + typeMultiplier + ", selected=true");
                }
                return new ActionBattlePokemonSelection.Selection(slot, candidate);
            }
        }
        return null;
    }

    private static int engagementScore(Pokemon pokemon) {
        if (pokemon == null) return 0;
        boolean hasRanged = false;
        boolean hasMelee = false;
        for (int slot = 0; slot < 4; slot++) {
            Move move = pokemon.getMoveSet().get(slot);
            if (move == null || !FightOrFlightAdapter.supports(move) || !FightOrFlightAdapter.hasPp(move)) continue;
            if (FightOrFlightAdapter.isRangedMove(move)) hasRanged = true;
            else hasMelee = true;
        }
        return ActionBattleTrainerTactics.engagementScore(hasRanged, hasMelee);
    }

    private static int aiTier() {
        var tier = DungeonSession.getTier();
        return tier != null ? tier.ordinal() + 1 : 1;
    }

    private static double hpRatio(Pokemon pokemon) {
        if (pokemon == null || pokemon.getMaxHealth() <= 0) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, pokemon.getCurrentHealth() / (double) pokemon.getMaxHealth()));
    }

    private static double moveTypeMultiplier(Move move, Pokemon targetPokemon) {
        if (move == null || targetPokemon == null) return 1.0D;
        String attack = move.getType().getName();
        String primary = targetPokemon.getPrimaryType().getName();
        String secondary = targetPokemon.getSecondaryType() != null ? targetPokemon.getSecondaryType().getName() : null;
        return ActionBattleTrainerAiTier.typeMultiplier(attack, primary, secondary);
    }

    private static double bestTypeMultiplier(Pokemon pokemon, Pokemon targetPokemon) {
        if (pokemon == null || targetPokemon == null) return 1.0D;
        double best = 0.0D;
        for (int slot = 0; slot < 4; slot++) {
            Move move = pokemon.getMoveSet().get(slot);
            if (move == null || !FightOrFlightAdapter.supports(move) || !FightOrFlightAdapter.hasPp(move)) continue;
            best = Math.max(best, moveTypeMultiplier(move, targetPokemon));
        }
        return best > 0.0D ? best : 1.0D;
    }

    private static int swapScore(Pokemon pokemon, Pokemon targetPokemon) {
        return ActionBattleTrainerAiTier.swapScore(aiTier(), engagementScore(pokemon), hpRatio(pokemon), bestTypeMultiplier(pokemon, targetPokemon));
    }

    private static int selectMoveSlot(ActionBattleSession session, Pokemon trainerPokemon, Pokemon targetPokemon,
                                      PokemonEntity trainerEntity, PokemonEntity targetEntity,
                                      long currentTick, boolean enemyVisible) {
        if (trainerPokemon == null) return -1;
        List<Integer> usableSlots = new ArrayList<>(4);
        for (int slot = 0; slot < 4; slot++) {
            Move move = trainerPokemon.getMoveSet().get(slot);
            if (move != null && FightOrFlightAdapter.supports(move) && FightOrFlightAdapter.hasPp(move)
                    && ActionBattleControlController.global().canUseMove(session.battleId(), trainerPokemon.getUuid(), move, currentTick)
                    && ActionBattleRampageController.global().canUseAbility(session.battleId(), trainerPokemon.getUuid(), move.getName(), currentTick)
                    && ActionBattleTargetingRules.maySelectMove(enemyVisible,
                    !FightOrFlightAdapter.isSelfOrAllyTargetCategory(
                            FightOrFlightAdapter.moveTargetCategory(move)))) usableSlots.add(slot);
        }
        if (usableSlots.isEmpty()) return -1;
        int tier = aiTier();
        List<Integer> plasmaPreferredSlots = usableSlots.stream().filter(slot -> {
            Move move = trainerPokemon.getMoveSet().get(slot);
            boolean validInteraction = ActionBattleTypeMechanicIdentity.hasMechanicBenefit(trainerEntity, "electric")
                    && !ActionBattleTypeMechanicIdentity.hasMechanicBenefit(trainerEntity, "psychic")
                    && FightOrFlightAdapter.isRangedMove(move) && FightOrFlightAdapter.movePower(move) > 0
                    && ActionBattleElectricController.nearestPlasmaBallTo(
                    session.dungeonSessionId(), targetEntity.blockPosition()) != null;
            return ActionBattleElectricController.trainerPrefersPlasma(
                    session.dungeonSessionId(), validInteraction, false);
        }).toList();
        if (tier <= 1) {
            List<Integer> pool = !plasmaPreferredSlots.isEmpty() ? plasmaPreferredSlots : usableSlots;
            return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
        int bestScore = Integer.MIN_VALUE;
        List<Integer> bestSlots = new ArrayList<>(4);
        double hpRatio = hpRatio(trainerPokemon);
        for (int slot : usableSlots) {
            Move move = trainerPokemon.getMoveSet().get(slot);
            int score = scoreTrainerMoveChoice(tier, trainerPokemon, targetPokemon, trainerEntity, targetEntity, move, hpRatio);
            boolean plasmaInteraction = ActionBattleTypeMechanicIdentity.hasMechanicBenefit(trainerEntity, "electric")
                    && !ActionBattleTypeMechanicIdentity.hasMechanicBenefit(trainerEntity, "psychic")
                    && FightOrFlightAdapter.isRangedMove(move) && FightOrFlightAdapter.movePower(move) > 0
                    && ActionBattleElectricController.nearestPlasmaBallTo(
                    session.dungeonSessionId(), targetEntity.blockPosition()) != null;
            if (ActionBattleElectricController.trainerPrefersPlasma(
                    session.dungeonSessionId(), plasmaInteraction, false)) score += 1_000;
            if (score > bestScore) {
                bestScore = score;
                bestSlots.clear();
                bestSlots.add(slot);
            } else if (score == bestScore) bestSlots.add(slot);
        }
        int selectedSlot = bestSlots.get(ThreadLocalRandom.current().nextInt(bestSlots.size()));
        Move selectedMove = trainerPokemon.getMoveSet().get(selectedSlot);
        int selectedScore = scoreTrainerMoveChoice(tier, trainerPokemon, targetPokemon, trainerEntity, targetEntity, selectedMove, hpRatio);
        boolean selectedCanCommitNow = FightOrFlightAdapter.canCommit(trainerEntity, targetEntity, selectedMove);
        double selectedMultiplier = tier >= 3 ? moveTypeMultiplier(selectedMove, targetPokemon) : 1.0D;
        DebugLog.log("[CobblemonNML] Trainer AI decision. Tier=" + tier + ", move=" + selectedMove.getName()
                + ", slot=" + (selectedSlot + 1) + ", score=" + selectedScore + ", canCommitNow=" + selectedCanCommitNow
                + ", typeMultiplier=" + selectedMultiplier + ", power=" + FightOrFlightAdapter.movePower(selectedMove)
                + ", priority=" + FightOrFlightAdapter.movePriority(selectedMove) + ", hpRatio=" + hpRatio);
        return selectedSlot;
    }

    private static int scoreTrainerMoveChoice(int tier, Pokemon trainerPokemon, Pokemon targetPokemon, PokemonEntity trainerEntity,
                                              PokemonEntity targetEntity, Move move, double hpRatio) {
        if (move == null) return Integer.MIN_VALUE;
        boolean canCommitNow = FightOrFlightAdapter.canCommit(trainerEntity, targetEntity, move);
        double multiplier = tier >= 3 ? moveTypeMultiplier(move, targetPokemon) : 1.0D;
        return ActionBattleTrainerAiTier.moveScore(tier, canCommitNow, multiplier,
                FightOrFlightAdapter.movePower(move), FightOrFlightAdapter.movePriority(move), hpRatio);
    }

    private static void repositionPendingMove(ActionBattleSession session, Pokemon trainerPokemon, PokemonEntity trainerPokemonEntity,
                                              PokemonEntity playerPokemonEntity, Move move, boolean onCooldown, long currentTick) {
        if (ActionBattleMovementActionRules.isMovementBlocked(session, trainerPokemon.getUuid(), currentTick)) {
            stopTrainerMovement(session, trainerPokemonEntity, ActionBattleCommandController.InterruptReason.CONTROL_EFFECT);
            return;
        }
        int attempt = session.trainerRepositionAttempt();
        if (attempt >= ActionBattleTrainerTactics.maxRepositionAttempts()) {
            trainerPokemonEntity.getNavigation().stop();
            return;
        }
        if (session.hasTrainerRepositionTarget()) {
            double dx = trainerPokemonEntity.getX() - session.trainerRepositionTargetX();
            double dy = trainerPokemonEntity.getY() - session.trainerRepositionTargetY();
            double dz = trainerPokemonEntity.getZ() - session.trainerRepositionTargetZ();
            boolean reached = dx * dx + dy * dy + dz * dz <= 2.25D;
            if (reached) {
                trainerPokemonEntity.getNavigation().stop();
                if (onCooldown) return;
                failRepositionAttempt(session, trainerPokemonEntity, "position reached without a valid attack angle");
                return;
            }
            if (trainerPokemonEntity.getNavigation().isDone()) {
                failRepositionAttempt(session, trainerPokemonEntity, "navigation stopped before reaching tactical position");
            }
            return;
        }
        if (session.isPokemonMovementCommandOnCooldown(trainerPokemon.getUuid(), currentTick)) return;
        ActionBattleVisualTrackingRules.faceTarget(trainerPokemonEntity, playerPokemonEntity);
        var remembered = ActionBattleTargetTracker.global().lastVisible(session.battleId(), trainerPokemon.getUuid(),
                playerPokemonEntity.getPokemon().getUuid());
        var trackedPlayerPosition = remembered
                .map(point -> new net.minecraft.world.phys.Vec3(point.x(), point.y(), point.z()))
                .orElseGet(() -> ActionBattleEvasionController.trackedPosition(playerPokemonEntity, currentTick));
        ActionBattleTrainerTactics.Point[] candidates = ActionBattleTrainerTactics.repositionCandidates(
                trainerPokemonEntity.getX(), trainerPokemonEntity.getZ(), trackedPlayerPosition.x, trackedPlayerPosition.z,
                FightOrFlightAdapter.isRangedMove(move));
        ActionBattleTrainerTactics.Point candidate = candidates[attempt];
        BlockPos targetPos = BlockPos.containing(candidate.x(), trackedPlayerPosition.y, candidate.z());
        Path path = trainerPokemonEntity.getNavigation().createPath(targetPos, 0);
        if (path == null || !path.canReach()) {
            failRepositionAttempt(session, trainerPokemonEntity, "candidate was unreachable");
            return;
        }
        if (!trainerPokemonEntity.getNavigation().moveTo(path,
                ActionBattleMovementController.movementSpeed(session, trainerPokemon.getUuid(), currentTick))) {
            failRepositionAttempt(session, trainerPokemonEntity, "navigation refused tactical position");
            return;
        }
        session.setTrainerRepositionTarget(candidate.x(), trackedPlayerPosition.y, candidate.z());
        session.startPokemonMovementCommandCooldown(trainerPokemon.getUuid(), currentTick, ActionBattleTiming.MOVE_HERE_COOLDOWN_TICKS);
        DebugLog.log("[CobblemonNML] Trainer AI reposition attempt " + (attempt + 1) + "/" + ActionBattleTrainerTactics.maxRepositionAttempts()
                + " started. Battle=" + session.battleId() + ", target=(" + candidate.x() + ", " + trackedPlayerPosition.y + ", " + candidate.z()
                + "), moveHereCooldownTicks=" + ActionBattleTiming.MOVE_HERE_COOLDOWN_TICKS);
    }

    private static void failRepositionAttempt(ActionBattleSession session, PokemonEntity trainerPokemonEntity, String reason) {
        trainerPokemonEntity.getNavigation().stop();
        int attempts = session.advanceTrainerRepositionAttempt();
        DebugLog.log("[CobblemonNML] Trainer AI reposition attempt " + attempts + "/" + ActionBattleTrainerTactics.maxRepositionAttempts()
                + " failed. Battle=" + session.battleId() + ", reason=" + reason);
        if (attempts >= ActionBattleTrainerTactics.maxRepositionAttempts()) {
            session.clearTrainerMoveCommand();
            DebugLog.log("[CobblemonNML] Trainer AI exhausted reposition attempts. Battle=" + session.battleId());
        }
    }
}

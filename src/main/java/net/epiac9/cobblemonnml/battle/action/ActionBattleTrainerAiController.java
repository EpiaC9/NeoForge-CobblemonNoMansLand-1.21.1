package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleConfusionRules;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleBalefulBunkerHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleHailHandler;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleToxicSpikesHandler;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
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

    static void tick(ActionBattleSession session, ServerLevel level, ActionBattlePokemonRefs refs) {
        if (session == null || level == null || refs == null || session.state() != ActionBattleState.ACTIVE) return;
        if (session.isTrainerSendOutPending() || session.isPlayerSendOutPending()) return;
        if (refs.trainerPokemon() == null || refs.playerPokemon() == null) return;
        Pokemon trainerPokemon = refs.trainerPokemon();
        if (trainerPokemon.isFainted() || refs.playerPokemon().isFainted()) return;
        if (session.trainerActiveEntityUUID() == null || session.playerActiveEntityUUID() == null) return;
        Entity rawTrainerPokemon = level.getEntity(session.trainerActiveEntityUUID());
        Entity rawPlayerPokemon = level.getEntity(session.playerActiveEntityUUID());
        if (!(rawTrainerPokemon instanceof PokemonEntity trainerPokemonEntity) || trainerPokemonEntity.isRemoved()) return;
        if (!(rawPlayerPokemon instanceof PokemonEntity playerPokemonEntity) || playerPokemonEntity.isRemoved()) {
            trainerPokemonEntity.getNavigation().stop();
            ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.TARGET_INVALID);
            return;
        }

        long currentTick = level.getGameTime();
        if (ActionBattleSleepController.isSleeping(session, trainerPokemon.getUuid(), currentTick)) {
            trainerPokemonEntity.getNavigation().stop();
            ActionBattleCommandController.cancelPendingOrders(session, trainerPokemon.getUuid(), ActionBattleCommandController.InterruptReason.CONTROL_EFFECT);
            return;
        }
        if (ActionBattleCommandController.isChanneling(trainerPokemon.getUuid())) {
            trainerPokemonEntity.getNavigation().stop();
            return;
        }
        if (session.trainerRepositionAttempt() >= ActionBattleTrainerTactics.maxRepositionAttempts()) {
            handleExhaustedReposition(session, level, refs, trainerPokemon, trainerPokemonEntity, currentTick);
            return;
        }
        if (!session.hasTrainerMoveCommand()) {
            int moveSlot = selectMoveSlot(trainerPokemon, refs.playerPokemon(), trainerPokemonEntity, playerPokemonEntity);
            if (moveSlot < 0) return;
            Move selectedMove = trainerPokemon.getMoveSet().get(moveSlot);
            ActionBattleCommandController.onCommandIssued(session, trainerPokemon.getUuid());
            if (handleConfusedCommand(session, level, trainerPokemon, trainerPokemonEntity, selectedMove, currentTick)) return;
            long revision = session.replaceTrainerMoveCommand(moveSlot, session.playerActiveEntityUUID());
            DebugLog.log("[CobblemonNML] Trainer AI move " + (moveSlot + 1) + " queued. Battle=" + session.battleId()
                    + ", revision=" + revision + ", target=" + session.playerActiveEntityUUID());
        }

        if (!session.playerActiveEntityUUID().equals(session.trainerMoveTargetEntityUUID())) {
            trainerPokemonEntity.getNavigation().stop();
            ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.TARGET_INVALID);
            return;
        }
        Move move = trainerPokemon.getMoveSet().get(session.trainerMoveSlot());
        if (move == null || !FightOrFlightAdapter.supports(move) || !FightOrFlightAdapter.hasPp(move)) {
            trainerPokemonEntity.getNavigation().stop();
            ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.TARGET_INVALID);
            return;
        }
        boolean onCooldown = session.isPokemonMoveOnCooldown(trainerPokemon.getUuid(), currentTick);
        if (!onCooldown && ActionBattleBalefulBunkerHandler.isBalefulBunker(move)) {
            trainerPokemonEntity.getNavigation().stop();
            ActionBattleBalefulBunkerHandler.StartResult result = ActionBattleBalefulBunkerHandler.tryStart(session, trainerPokemonEntity, move);
            session.clearTrainerMoveCommand();
            session.resetTrainerRepositionState();
            if (result == ActionBattleBalefulBunkerHandler.StartResult.STARTED) ActionBattleParalysisController.onAbilitySucceeded(session.battleId(), trainerPokemon.getUuid(), currentTick);
            DebugLog.log("[CobblemonNML] Trainer Baleful Bunker ACTION start result. Battle=" + session.battleId() + ", result=" + result);
            return;
        }
        if (!onCooldown && ActionBattleHailHandler.isHail(move) && FightOrFlightAdapter.canCommit(trainerPokemonEntity, playerPokemonEntity, move)) {
            trainerPokemonEntity.getNavigation().stop();
            ActionBattleHailHandler.StartResult result = ActionBattleHailHandler.tryStart(session, level, trainerPokemonEntity, playerPokemonEntity, move);
            session.clearTrainerMoveCommand();
            session.resetTrainerRepositionState();
            if (result == ActionBattleHailHandler.StartResult.STARTED) ActionBattleProtectController.global().onSuccessfulNonProtectMove(session.battleId(), trainerPokemon.getUuid());
            DebugLog.log("[CobblemonNML] Trainer Hail ACTION start result. Battle=" + session.battleId() + ", result=" + result);
            return;
        }
        if (!onCooldown && ActionBattleToxicSpikesHandler.isToxicSpikes(move) && FightOrFlightAdapter.canCommit(trainerPokemonEntity, playerPokemonEntity, move)) {
            trainerPokemonEntity.getNavigation().stop();
            ActionBattleToxicSpikesHandler.StartResult result = ActionBattleToxicSpikesHandler.tryStart(session, level, trainerPokemonEntity, playerPokemonEntity, move);
            session.clearTrainerMoveCommand();
            session.resetTrainerRepositionState();
            if (result == ActionBattleToxicSpikesHandler.StartResult.STARTED) ActionBattleProtectController.global().onSuccessfulNonProtectMove(session.battleId(), trainerPokemon.getUuid());
            DebugLog.log("[CobblemonNML] Trainer Toxic Spikes ACTION start result. Battle=" + session.battleId() + ", result=" + result);
            return;
        }
        if (!onCooldown && FightOrFlightAdapter.canCommit(trainerPokemonEntity, playerPokemonEntity, move)) {
            trainerPokemonEntity.getNavigation().stop();
            if (!FightOrFlightAdapter.consumeOnePp(move)) {
                ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.MOVE_FAILED);
                return;
            }
            if (FightOrFlightAdapter.execute(trainerPokemonEntity, playerPokemonEntity, move)) {
                long cooldownTicks = FightOrFlightAdapter.cooldownTicks(move);
                session.startPokemonMoveCooldown(trainerPokemon.getUuid(), currentTick, cooldownTicks);
                ActionBattleProtectController.global().onSuccessfulNonProtectMove(session.battleId(), trainerPokemon.getUuid());
                ActionBattleParalysisController.onAbilitySucceeded(session.battleId(), trainerPokemon.getUuid(), currentTick);
                session.clearTrainerMoveCommand();
                session.resetTrainerRepositionState();
                DebugLog.log("[CobblemonNML] Trainer AI move committed through Fight or Flight. Battle=" + session.battleId()
                        + ", move=" + move.getName() + ", cooldownTicks=" + cooldownTicks);
            } else {
                FightOrFlightAdapter.refundOnePp(move);
                ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.MOVE_FAILED);
            }
            return;
        }
        repositionPendingMove(session, trainerPokemon, trainerPokemonEntity, playerPokemonEntity, move, onCooldown, currentTick);
    }


    private static boolean handleConfusedCommand(ActionBattleSession session, ServerLevel level, Pokemon trainerPokemon,
                                                   PokemonEntity trainerEntity, Move move, long currentTick) {
        if (move == null) return false;
        ActionBattleConfusionRules.CommandKind kind = ActionBattleBalefulBunkerHandler.isBalefulBunker(move)
                ? ActionBattleConfusionRules.CommandKind.PROTECT
                : (ActionBattleHailHandler.isHail(move) || ActionBattleToxicSpikesHandler.isToxicSpikes(move))
                ? ActionBattleConfusionRules.CommandKind.CHANNEL
                : FightOrFlightAdapter.isMeleeMove(move) ? ActionBattleConfusionRules.CommandKind.MELEE
                : FightOrFlightAdapter.isRangedMove(move) ? ActionBattleConfusionRules.CommandKind.RANGED
                : ActionBattleConfusionRules.CommandKind.SUPPORT;
        ActionBattleConfusionController.CommandPlan plan = ActionBattleConfusionController.roll(session, trainerEntity, kind, currentTick);
        if (!plan.corrupted()) return false;
        trainerEntity.getNavigation().stop();
        session.resetTrainerRepositionState();
        if (kind == ActionBattleConfusionRules.CommandKind.PROTECT || kind == ActionBattleConfusionRules.CommandKind.SUPPORT) {
            if (!FightOrFlightAdapter.consumeOnePp(move)) return true;
            long cooldownTicks = kind == ActionBattleConfusionRules.CommandKind.PROTECT
                    ? ActionBattleBalefulBunkerHandler.GLOBAL_COOLDOWN_TICKS : FightOrFlightAdapter.cooldownTicks(move);
            session.startPokemonMoveCooldown(trainerPokemon.getUuid(), currentTick, cooldownTicks);
            ActionBattleConfusionController.applyCooldownPenalty(session, trainerEntity, currentTick);
            DebugLog.log("[CobblemonNML] Trainer Confusion caused move to fail without effect. Battle=" + session.battleId() + ", move=" + move.getName());
            return true;
        }
        if (kind == ActionBattleConfusionRules.CommandKind.RANGED) {
            if (!FightOrFlightAdapter.consumeOnePp(move)) return true;
            session.startPokemonMoveCooldown(trainerPokemon.getUuid(), currentTick, FightOrFlightAdapter.cooldownTicks(move));
            ActionBattleConfusionController.applyCooldownPenalty(session, trainerEntity, currentTick);
            FightOrFlightAdapter.executeConfusedRanged(trainerEntity, move, ActionBattleConfusionController.randomShotDirection(trainerEntity));
            DebugLog.log("[CobblemonNML] Trainer Confusion fired ranged move in random direction. Battle=" + session.battleId() + ", move=" + move.getName());
            return true;
        }
        if (kind == ActionBattleConfusionRules.CommandKind.MELEE) {
            if (!FightOrFlightAdapter.consumeOnePp(move)) return true;
            session.startPokemonMoveCooldown(trainerPokemon.getUuid(), currentTick, FightOrFlightAdapter.cooldownTicks(move));
            ActionBattleConfusionController.applyCooldownPenalty(session, trainerEntity, currentTick);
            ActionBattleConfusionController.startMeleeDash(session, level, trainerEntity, move, currentTick);
            DebugLog.log("[CobblemonNML] Trainer Confusion started uncontrolled melee dash. Battle=" + session.battleId() + ", move=" + move.getName());
            return true;
        }
        if (kind == ActionBattleConfusionRules.CommandKind.CHANNEL) {
            if (ActionBattleHailHandler.isHail(move)) {
                var result = ActionBattleHailHandler.tryStart(session, level, trainerEntity, null, move, plan.channelBonusTicks(), plan.channelSelfCancel());
                if (result == ActionBattleHailHandler.StartResult.STARTED) ActionBattleConfusionController.applyCooldownPenalty(session, trainerEntity, currentTick);
            } else {
                var result = ActionBattleToxicSpikesHandler.tryStart(session, level, trainerEntity, null, move, plan.channelBonusTicks(), plan.channelSelfCancel());
                if (result == ActionBattleToxicSpikesHandler.StartResult.STARTED) ActionBattleConfusionController.applyCooldownPenalty(session, trainerEntity, currentTick);
            }
            return true;
        }
        return false;
    }
    private static void handleExhaustedReposition(ActionBattleSession session, ServerLevel level, ActionBattlePokemonRefs refs,
                                                   Pokemon trainerPokemon, PokemonEntity trainerPokemonEntity, long currentTick) {
        trainerPokemonEntity.getNavigation().stop();
        session.clearTrainerMoveCommand();
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
        int currentScore = swapScore(trainerPokemon, refs.playerPokemon());
        ActionBattlePokemonSelection.Selection replacement = findBetterSwapCandidate(runtimeTrainer, session.trainerActivePartyIndex(), currentScore, refs.playerPokemon());
        if (replacement == null) {
            session.resetTrainerRepositionState();
            DebugLog.log("[CobblemonNML] Trainer AI found no meaningfully better voluntary swap; continuing reposition attempts. Battle=" + session.battleId());
            return;
        }
        int previousSlot = session.trainerActivePartyIndex();
        session.setTrainerSendOutPending(true);
        ActionBattleCommandController.cancelPendingOrders(session, ActionBattleCommandController.Side.TRAINER, ActionBattleCommandController.InterruptReason.SWAP);
        ActionBattleEffectController.global().onPokemonRecalled(session.battleId(), trainerPokemon.getUuid(), currentTick);
        ActionBattleParalysisController.onPokemonRecalled(session.battleId(), trainerPokemon.getUuid());
        ActionBattleProtectController.global().onPokemonRecalled(session.battleId(), trainerPokemon.getUuid());
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

    private static int selectMoveSlot(Pokemon trainerPokemon, Pokemon targetPokemon, PokemonEntity trainerEntity, PokemonEntity targetEntity) {
        if (trainerPokemon == null) return -1;
        List<Integer> usableSlots = new ArrayList<>(4);
        for (int slot = 0; slot < 4; slot++) {
            Move move = trainerPokemon.getMoveSet().get(slot);
            if (move != null && FightOrFlightAdapter.supports(move) && FightOrFlightAdapter.hasPp(move)) usableSlots.add(slot);
        }
        if (usableSlots.isEmpty()) return -1;
        int tier = aiTier();
        if (tier <= 1) return usableSlots.get(ThreadLocalRandom.current().nextInt(usableSlots.size()));
        int bestScore = Integer.MIN_VALUE;
        List<Integer> bestSlots = new ArrayList<>(4);
        double hpRatio = hpRatio(trainerPokemon);
        for (int slot : usableSlots) {
            Move move = trainerPokemon.getMoveSet().get(slot);
            boolean canCommitNow = FightOrFlightAdapter.canCommit(trainerEntity, targetEntity, move);
            double multiplier = tier >= 3 ? moveTypeMultiplier(move, targetPokemon) : 1.0D;
            int score = ActionBattleTrainerAiTier.moveScore(tier, canCommitNow, multiplier,
                    FightOrFlightAdapter.movePower(move), FightOrFlightAdapter.movePriority(move), hpRatio);
            if (score > bestScore) {
                bestScore = score;
                bestSlots.clear();
                bestSlots.add(slot);
            } else if (score == bestScore) bestSlots.add(slot);
        }
        int selectedSlot = bestSlots.get(ThreadLocalRandom.current().nextInt(bestSlots.size()));
        Move selectedMove = trainerPokemon.getMoveSet().get(selectedSlot);
        boolean selectedCanCommitNow = FightOrFlightAdapter.canCommit(trainerEntity, targetEntity, selectedMove);
        double selectedMultiplier = tier >= 3 ? moveTypeMultiplier(selectedMove, targetPokemon) : 1.0D;
        int selectedPower = FightOrFlightAdapter.movePower(selectedMove);
        int selectedPriority = FightOrFlightAdapter.movePriority(selectedMove);
        int selectedScore = ActionBattleTrainerAiTier.moveScore(tier, selectedCanCommitNow, selectedMultiplier,
                selectedPower, selectedPriority, hpRatio);
        DebugLog.log("[CobblemonNML] Trainer AI decision. Tier=" + tier + ", move=" + selectedMove.getName()
                + ", slot=" + (selectedSlot + 1) + ", score=" + selectedScore + ", canCommitNow=" + selectedCanCommitNow
                + ", typeMultiplier=" + selectedMultiplier + ", power=" + selectedPower + ", priority=" + selectedPriority
                + ", hpRatio=" + hpRatio);
        return selectedSlot;
    }

    private static void repositionPendingMove(ActionBattleSession session, Pokemon trainerPokemon, PokemonEntity trainerPokemonEntity,
                                              PokemonEntity playerPokemonEntity, Move move, boolean onCooldown, long currentTick) {
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
        ActionBattleTrainerTactics.Point[] candidates = ActionBattleTrainerTactics.repositionCandidates(
                trainerPokemonEntity.getX(), trainerPokemonEntity.getZ(), playerPokemonEntity.getX(), playerPokemonEntity.getZ(),
                FightOrFlightAdapter.isRangedMove(move));
        ActionBattleTrainerTactics.Point candidate = candidates[attempt];
        BlockPos targetPos = BlockPos.containing(candidate.x(), playerPokemonEntity.getY(), candidate.z());
        Path path = trainerPokemonEntity.getNavigation().createPath(targetPos, 0);
        if (path == null || !path.canReach()) {
            failRepositionAttempt(session, trainerPokemonEntity, "candidate was unreachable");
            return;
        }
        if (!trainerPokemonEntity.getNavigation().moveTo(path, ActionBattleMovementController.movementSpeed(session, trainerPokemon.getUuid(), currentTick))) {
            failRepositionAttempt(session, trainerPokemonEntity, "navigation refused tactical position");
            return;
        }
        session.setTrainerRepositionTarget(candidate.x(), playerPokemonEntity.getY(), candidate.z());
        session.startPokemonMovementCommandCooldown(trainerPokemon.getUuid(), currentTick, ActionBattleTiming.MOVE_HERE_COOLDOWN_TICKS);
        DebugLog.log("[CobblemonNML] Trainer AI reposition attempt " + (attempt + 1) + "/" + ActionBattleTrainerTactics.maxRepositionAttempts()
                + " started. Battle=" + session.battleId() + ", target=(" + candidate.x() + ", " + playerPokemonEntity.getY() + ", " + candidate.z()
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

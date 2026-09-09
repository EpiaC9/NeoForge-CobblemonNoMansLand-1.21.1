package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlController;
import net.epiac9.cobblemonnml.battle.action.control.ActionBattleControlEffect;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackEvent;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentType;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.poison.ActionBattlePoisonVisuals;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricVisuals;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterVisuals;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassVisuals;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockVisualRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fighting.ActionBattleFightingRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleBoostedMoveRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleEffectiveMoveTypeResolver;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fighting.ActionBattleFightingVisuals;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugTrainingStat;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugVisuals;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ActionBattleHudSync {
    private ActionBattleHudSync() {}

    public static void send(ServerPlayer player, ActionBattleSession session, Pokemon playerPokemon, Pokemon trainerPokemon) {
        if (player == null || session == null || playerPokemon == null || trainerPokemon == null || session.state() != ActionBattleState.ACTIVE) return;
        long currentTick = player.serverLevel().getGameTime();
        long swapCooldownRemaining = Math.max(0L, session.playerSwapCooldownEndTick() - currentTick);
        long swapCooldownDuration = session.playerSwapCooldownDurationTicks();
        var binding = ActionBattleGhostRuntime.global().curses().view(session.battleId(),
                playerPokemon.getUuid(), net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostCurseType.BINDING,
                currentTick);
        if (binding.isPresent() && binding.orElseThrow().remainingTicks() > swapCooldownRemaining) {
            swapCooldownRemaining = binding.orElseThrow().remainingTicks();
            swapCooldownDuration = binding.orElseThrow().totalTicks();
        }
        long moveHereCooldownRemaining = Math.max(0L, session.pokemonMovementCommandCooldownEndTick(playerPokemon.getUuid()) - currentTick);
        long moveHereCooldownDuration = session.pokemonMovementCommandCooldownDurationTicks(playerPokemon.getUuid());
        boolean uproarCommandsBlocked = net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime
                .blocksTrainerCommands(session, playerPokemon.getUuid(), currentTick);
        if (uproarCommandsBlocked) {
            moveHereCooldownRemaining = 1L;
            moveHereCooldownDuration = 1L;
        }
        if (net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime
                .blocksSwap(playerPokemon.getUuid())) {
            swapCooldownRemaining = 1L;
            swapCooldownDuration = 1L;
        }
        ActionBattleHudPayload payload = new ActionBattleHudPayload(
                true,
                playerPokemon.getSpecies().getName(), playerPokemon.getUuid().toString(), playerPokemon.getLevel(), currentHealth(playerPokemon), maxHealth(playerPokemon), session.playerActivePartyIndex(player.getUUID()),
                trainerPokemon.getSpecies().getName(), trainerPokemon.getUuid().toString(), trainerPokemon.getLevel(), currentHealth(trainerPokemon), maxHealth(trainerPokemon), session.trainerActivePartyIndex(),
                statusStates(session.battleId(), session.dungeonSessionId(), playerPokemon.getUuid(), currentTick),
                statusStates(session.battleId(), session.dungeonSessionId(), trainerPokemon.getUuid(), currentTick),
                statStages(session.battleId(), playerPokemon.getUuid(), currentTick),
                statStages(session.battleId(), trainerPokemon.getUuid(), currentTick),
                damageStates(ActionBattleDamageFeedbackController.global().drain(session.battleId(), playerPokemon.getUuid())),
                damageStates(ActionBattleDamageFeedbackController.global().drain(session.battleId(), trainerPokemon.getUuid())),
                ActionBattleDarkRuntime.view(session, playerPokemon.getUuid(), currentTick)
                        .map(view -> new ActionBattleHudPayload.ObscurityState(view.obscurityStage()))
                        .orElseGet(ActionBattleHudPayload.ObscurityState::clear),
                ActionBattleDarkRuntime.view(session, trainerPokemon.getUuid(), currentTick)
                        .map(view -> new ActionBattleHudPayload.ObscurityState(view.obscurityStage()))
                        .orElseGet(ActionBattleHudPayload.ObscurityState::clear),
                partyState(player, session, currentTick),
                swapCooldownRemaining, swapCooldownDuration,
                moveHereCooldownRemaining, moveHereCooldownDuration,
                moveState(session, playerPokemon, 0, currentTick), moveState(session, playerPokemon, 1, currentTick),
                moveState(session, playerPokemon, 2, currentTick), moveState(session, playerPokemon, 3, currentTick)
        );
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void hide(ServerPlayer player) {
        if (player != null) PacketDistributor.sendToPlayer(player, ActionBattleHudPayload.hidden());
    }

    private static ActionBattleHudPayload.PartyState partyState(ServerPlayer player, ActionBattleSession session,
                                                                 long currentTick) {
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        List<ActionBattlePartyProjection.Input> inputs = new ArrayList<>();
        for (int slot = 0; slot < ActionBattlePartyProjection.MAX_SLOTS; slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon == null) continue;
            List<ActionBattleHudPayload.StatusState> effects = statusStates(session.battleId(), session.dungeonSessionId(),
                    pokemon.getUuid(), currentTick);
            List<ActionBattlePartyProjection.Effect> projectedEffects = effects.stream()
                    .map(effect -> new ActionBattlePartyProjection.Effect(effect.statusId(),
                            effect.remainingTicks(), effect.totalTicks()))
                    .toList();
            inputs.add(new ActionBattlePartyProjection.Input(slot, pokemon.getUuid(), pokemon.getSpecies().getName(),
                    currentHealth(pokemon), maxHealth(pokemon), projectedEffects));
        }
        List<ActionBattleHudPayload.PartyPokemonState> entries = ActionBattlePartyProjection.from(inputs).entries().stream()
                .map(entry -> new ActionBattleHudPayload.PartyPokemonState(entry.partySlot(), entry.pokemonUUID().toString(), entry.name(),
                        entry.currentHp(), entry.maxHp(), entry.fainted(),
                        entry.effects().stream().map(effect -> new ActionBattleHudPayload.StatusState(
                                effect.effectId(), effect.remainingTicks(), effect.totalTicks())).toList()))
                .toList();
        return new ActionBattleHudPayload.PartyState(entries);
    }

    private static List<ActionBattleHudPayload.StatusState> statusStates(UUID battleId, UUID dungeonSessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleEffectController controller = ActionBattleEffectController.global();
        ActionBattleProtectController protect = ActionBattleProtectController.global();
        List<ActionBattleHudPayload.StatusState> states = new ArrayList<>();
        int shieldLevel = protect.deterioratingShieldLevel(battleId, pokemonUUID);
        long shieldRemaining = protect.deterioratingShieldRemainingTicks(battleId, pokemonUUID);
        if (shieldLevel > 0 && shieldRemaining > 0L) {
            long shieldDuration = shieldLevel * 200L;
            states.add(new ActionBattleHudPayload.StatusState("DETERIORATING_SHIELD_" + shieldLevel, shieldRemaining, shieldDuration));
        }
        for (ActionBattleStatus status : ActionBattleStatus.values()) {
            UUID effectScope = status == ActionBattleStatus.SLEEP ? dungeonSessionId : battleId;
            long remaining = controller.statusRemainingTicks(effectScope, pokemonUUID, status, currentTick);
            if (remaining <= 0L) continue;
            long duration = controller.statusDurationTicks(effectScope, pokemonUUID, status, currentTick);
            states.add(new ActionBattleHudPayload.StatusState(status.name(), remaining, duration));
        }
        ActionBattleControlController controls = ActionBattleControlController.global();
        ActionBattleControlEffect activeControl = controls.activeEffect(battleId, pokemonUUID, currentTick);
        if (activeControl != null) {
            long controlRemaining = controls.activeRemainingTicks(battleId, pokemonUUID, currentTick);
            long controlDuration = controls.activeDurationTicks(battleId, pokemonUUID, currentTick);
            long shownRemaining = controlRemaining == Long.MAX_VALUE ? 1L : controlRemaining;
            long shownDuration = controlDuration == Long.MAX_VALUE ? 1L : Math.max(1L, controlDuration);
            states.add(new ActionBattleHudPayload.StatusState("CONTROL_" + activeControl.type().name(), shownRemaining, shownDuration));
        }
        ActionBattlePersistentController persistent = ActionBattlePersistentController.global();
        for (ActionBattlePersistentType type : ActionBattlePersistentType.values()) {
            long persistentRemaining = persistent.remainingTicks(battleId, pokemonUUID, type, currentTick);
            if (persistentRemaining <= 0L) continue;
            long persistentDuration = persistent.durationTicks(battleId, pokemonUUID, type);
            if (persistentRemaining == Long.MAX_VALUE || persistentDuration == Long.MAX_VALUE) {
                persistentRemaining = 1L;
                persistentDuration = 1L;
            }
            states.add(new ActionBattleHudPayload.StatusState("PERSISTENT_" + type.name(), persistentRemaining, Math.max(1L, persistentDuration)));
        }
        fireStatusState(pokemonUUID, currentTick).ifPresent(states::add);
        iceStatusState(pokemonUUID, currentTick).ifPresent(states::add);
        drowsyStatusState(pokemonUUID, currentTick).ifPresent(states::add);
        poisonStatusState(pokemonUUID, currentTick).ifPresent(states::add);
        electricStatusState(pokemonUUID, currentTick).ifPresent(states::add);
        net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime
                .view(battleId, pokemonUUID, currentTick).ifPresent(view -> states.add(
                        new ActionBattleHudPayload.StatusState(
                                view.branch() == net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRules.Branch.MAGNET_RISE
                                        ? net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelVisuals.MAGNET_RISE_STATUS_ID
                                        : net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelVisuals.WEIGHTED_STATUS_ID,
                                view.remainingTicks(), view.totalTicks())));
        ActionBattleRockController rock = ActionBattleRockController.global();
        rock.stockpileView(battleId, pokemonUUID, currentTick).ifPresent(view ->
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleRockVisualRules.STOCKPILE_STATUS_ID,
                        view.remainingTicks(), view.totalDurationTicks())));
        rock.enduranceView(battleId, pokemonUUID, currentTick).ifPresent(view ->
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleRockVisualRules.ENDURANCE_STATUS_ID,
                        view.remainingTicks(), view.totalDurationTicks())));
        for (var view : ActionBattleGhostRuntime.global().curses().views(
                battleId, pokemonUUID, currentTick)) {
            states.add(new ActionBattleHudPayload.StatusState("TYPE_GHOST_" + view.type().name(),
                    view.remainingTicks(), view.totalTicks()));
        }
        ActionBattleTypeEffectController typeEffects = ActionBattleTypeEffectController.global();
        typeEffects.aquaShieldView(dungeonSessionId, pokemonUUID, currentTick).ifPresent(view ->
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleWaterVisuals.AQUA_SHIELD_STATUS_ID,
                        view.remainingTicks(), view.totalDurationTicks())));
        typeEffects.immobilizedView(dungeonSessionId, pokemonUUID, currentTick).ifPresent(view ->
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleWaterVisuals.IMMOBILIZED_STATUS_ID,
                        view.remainingTicks(), view.totalDurationTicks())));
        typeEffects.grassEmpowerView(dungeonSessionId, pokemonUUID).ifPresent(view ->
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleGrassVisuals.EMPOWER_STATUS_ID, 1L, 1L)));
        typeEffects.leechSeedView(dungeonSessionId, pokemonUUID, currentTick).ifPresent(view ->
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleGrassVisuals.LEECH_SEED_STATUS_ID,
                        view.remainingTicks(), ActionBattleGrassRules.LEECH_SEED_DURATION_TICKS)));
        typeEffects.grassMovementView(dungeonSessionId, pokemonUUID, currentTick).ifPresent(view ->
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleGrassVisuals.MOVEMENT_STATUS_ID,
                        view.remainingTicks(), view.totalDurationTicks())));
        ActionBattleFightingRuntime.view(dungeonSessionId, pokemonUUID, currentTick).ifPresent(view -> {
            if (view.buildupCount() > 0) {
                states.add(new ActionBattleHudPayload.StatusState(
                        ActionBattleFightingVisuals.OUTRAGE_BUILDUP_STATUS_ID,
                        view.buildupCount(), net.epiac9.cobblemonnml.battle.action.typeeffect.fighting.ActionBattleFightingRules.BUILDUP_HITS));
            } else if (view.outrageActive()) {
                states.add(new ActionBattleHudPayload.StatusState(
                        ActionBattleFightingVisuals.OUTRAGE_STATUS_ID,
                        view.outrageRemainingTicks(), net.epiac9.cobblemonnml.battle.action.typeeffect.fighting.ActionBattleFightingRules.OUTRAGE_DURATION_TICKS));
            } else if (view.exhaustedActive()) {
                states.add(new ActionBattleHudPayload.StatusState(
                        ActionBattleFightingVisuals.EXHAUSTED_STATUS_ID,
                        view.exhaustedRemainingTicks(), net.epiac9.cobblemonnml.battle.action.typeeffect.fighting.ActionBattleFightingRules.EXHAUSTED_DURATION_TICKS));
            }
        });
        net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonController.global()
                .view(dungeonSessionId, pokemonUUID, currentTick).ifPresent(view -> {
            if (view.phase() == net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonState.Phase.BUILDUP) {
                states.add(new ActionBattleHudPayload.StatusState(
                        net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonVisuals.BUILDUP_STATUS_ID,
                        view.maintenanceRemainingTicks(),
                        net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRules.MAINTENANCE_DURATION_TICKS));
            } else if (view.phase() == net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonState.Phase.ACTIVE) {
                states.add(new ActionBattleHudPayload.StatusState(
                        net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonVisuals.ACTIVE_STATUS_ID,
                        view.phaseRemainingTicks(),
                        net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRules.ACTIVE_DURATION_TICKS));
            }
        });
        ActionBattleBugController.global().state(battleId, pokemonUUID).ifPresent(bug -> {
            addBugLock(states, bug, ActionBattleBugTrainingStat.HP,
                    ActionBattleBugVisuals.SHEDDING_STATUS_ID, currentTick);
            addBugLock(states, bug, ActionBattleBugTrainingStat.ATTACK,
                    ActionBattleBugVisuals.ATTACK_STATUS_ID, currentTick);
            addBugLock(states, bug, ActionBattleBugTrainingStat.SPECIAL_ATTACK,
                    ActionBattleBugVisuals.SPECIAL_ATTACK_STATUS_ID, currentTick);
            boolean physical = bug.physicalCarapaceActive(currentTick);
            boolean effectGuard = bug.effectZoneActive(currentTick);
            if (physical && effectGuard) {
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleBugVisuals.COMBINED_CARAPACE_STATUS_ID,
                        Math.max(bug.physicalCarapaceRemaining(currentTick), bug.effectZoneRemaining(currentTick)),
                        ActionBattleBugRules.CARAPACE_TICKS));
            } else if (physical) {
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleBugVisuals.CARAPACE_STATUS_ID,
                        bug.physicalCarapaceRemaining(currentTick), ActionBattleBugRules.CARAPACE_TICKS));
            } else if (effectGuard) {
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleBugVisuals.EFFECT_GUARD_STATUS_ID,
                        bug.effectZoneRemaining(currentTick), ActionBattleBugRules.LOCKOUT_TICKS));
            }
            if (bug.slowdownRemaining(currentTick) > 0L) {
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleBugVisuals.SPEED_STATUS_ID,
                        bug.slowdownRemaining(currentTick), ActionBattleBugRules.LOCKOUT_TICKS));
            }
        });
        return List.copyOf(states);
    }

    private static void addBugLock(List<ActionBattleHudPayload.StatusState> states,
                                   net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugState bug,
                                   ActionBattleBugTrainingStat branch, String statusId, long currentTick) {
        long remaining = bug.lockRemaining(branch, currentTick);
        if (remaining > 0L) states.add(new ActionBattleHudPayload.StatusState(
                statusId, remaining, ActionBattleBugRules.LOCKOUT_TICKS));
    }

        private static java.util.Optional<ActionBattleHudPayload.StatusState> electricStatusState(UUID pokemonUUID, long currentTick) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId == null) return java.util.Optional.empty();
        ActionBattleTypeEffectController controller = ActionBattleTypeEffectController.global();
        java.util.Optional<ActionBattleTypeEffectState.ElectricParalysisView> paralysis =
            controller.electricParalysisView(sessionId, pokemonUUID, currentTick);
        if (paralysis.isPresent()) {
            ActionBattleTypeEffectState.ElectricParalysisView view = paralysis.get();
            return java.util.Optional.of(new ActionBattleHudPayload.StatusState(ActionBattleElectricVisuals.paralysisStatusId(),
                ActionBattleElectricVisuals.paralysisRemaining(view.remainingTicks()), ActionBattleElectricVisuals.paralysisDuration()));
        }
        return controller.electricChargeView(sessionId, pokemonUUID, currentTick)
            .map(view -> new ActionBattleHudPayload.StatusState(ActionBattleElectricVisuals.chargeStatusId(),
                ActionBattleElectricVisuals.chargeRemaining(view.charge()), ActionBattleElectricVisuals.chargeDuration()));
        }

    private static java.util.Optional<ActionBattleHudPayload.StatusState> poisonStatusState(UUID pokemonUUID, long currentTick) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId == null) return java.util.Optional.empty();
        return ActionBattleTypeEffectController.global().poisonView(sessionId, pokemonUUID, currentTick)
                .map(view -> new ActionBattleHudPayload.StatusState(
                        ActionBattlePoisonVisuals.hudStatusId(view.level()),
                        ActionBattlePoisonVisuals.hudRemaining(view.level(), view.accumulation(), view.toxicRemainingTicks()),
                        ActionBattlePoisonVisuals.hudDuration(view.level())));
    }

    private static java.util.Optional<ActionBattleHudPayload.StatusState> drowsyStatusState(UUID pokemonUUID, long currentTick) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId == null) return java.util.Optional.empty();
        return ActionBattleTypeEffectController.global().drowsyView(sessionId, pokemonUUID, currentTick)
                .filter(view -> view.remainingTicks() > 0L)
                .map(view -> new ActionBattleHudPayload.StatusState(
                        net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyRules.HUD_STATUS_ID,
                        view.remainingTicks(), view.totalDurationTicks()));
    }

    private static java.util.Optional<ActionBattleHudPayload.StatusState> fireStatusState(UUID pokemonUUID, long currentTick) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId == null) return java.util.Optional.empty();
        return ActionBattleTypeEffectController.global().fireView(sessionId, pokemonUUID, currentTick)
                .map(ActionBattleHudSync::toFireStatusState);
    }

    private static ActionBattleHudPayload.StatusState toFireStatusState(ActionBattleTypeEffectState.FireView fire) {
        if (fire.phase() == ActionBattleFireState.Phase.BURN) {
            return new ActionBattleHudPayload.StatusState("TYPE_FIRE_BURN", fire.burnRemainingTicks(), ActionBattleFireRules.BURN_DURATION_TICKS);
        }
        long pressure = Math.clamp(Math.round(fire.pressure()), 1L, Math.round(ActionBattleFireRules.BURN_THRESHOLD));
        String statusId = fire.phase() == ActionBattleFireState.Phase.CINDERS ? "TYPE_FIRE_CINDERS" : "TYPE_FIRE_BUILDUP";
        return new ActionBattleHudPayload.StatusState(statusId, pressure, Math.round(ActionBattleFireRules.BURN_THRESHOLD));
    }

    private static java.util.Optional<ActionBattleHudPayload.StatusState> iceStatusState(UUID pokemonUUID, long currentTick) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId == null) return java.util.Optional.empty();
        return ActionBattleTypeEffectController.global().iceView(sessionId, pokemonUUID, currentTick)
                .map(ActionBattleHudSync::toIceStatusState);
    }

    private static ActionBattleHudPayload.StatusState toIceStatusState(ActionBattleTypeEffectState.IceView ice) {
        return new ActionBattleHudPayload.StatusState(
                ActionBattleIceRules.hudStatusId(ice.phase()),
                ActionBattleIceRules.hudRemaining(ice.phase(), ice.currentHits(), ice.frostbiteRemainingTicks()),
                ActionBattleIceRules.hudDuration(ice.phase(), ice.hitsRequired()));
    }


    private static ActionBattleHudPayload.StatStageState statStages(UUID battleId, UUID pokemonUUID, long currentTick) {
        return new ActionBattleHudPayload.StatStageState(
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.ATTACK, currentTick),
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.DEFENSE, currentTick),
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.SPECIAL_ATTACK, currentTick),
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.SPECIAL_DEFENSE, currentTick),
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.SPEED, currentTick),
                ActionBattleStatResolver.effectiveStage(battleId, pokemonUUID, ActionBattleStat.ACCURACY, currentTick)
        );
    }

    private static List<ActionBattleHudPayload.DamageState> damageStates(List<ActionBattleDamageFeedbackEvent> events) {
        if (events == null || events.isEmpty()) return List.of();
        List<ActionBattleHudPayload.DamageState> states = new ArrayList<>(events.size());
        for (ActionBattleDamageFeedbackEvent event : events) {
            if (event == null || event.damage() <= 0) continue;
            states.add(new ActionBattleHudPayload.DamageState(event.eventId(), event.damage(), event.category().name()));
        }
        return List.copyOf(states);
    }

    private static ActionBattleHudPayload.MoveState moveState(ActionBattleSession session, Pokemon pokemon,
                                                               int slot, long currentTick) {
        Move move = pokemon.getMoveSet().get(slot);
        if (move == null) return ActionBattleHudPayload.MoveState.empty();
        boolean controlAllowed = ActionBattleControlController.global().canUseMove(
                session.battleId(), pokemon.getUuid(), move, currentTick);
        boolean fightingAllowed = ActionBattleFightingRuntime.canUseAbility(
                session, pokemon, move, currentTick);
        boolean dragonAllowed = !net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime
                .blocksTrainerCommands(session, pokemon.getUuid(), currentTick);
        PokemonEntity entity = pokemon.getEntity();
        String effectiveType = ActionBattleEffectiveMoveTypeResolver.resolve(entity, move);
        boolean boosted = ActionBattleBoostedMoveRules.isMechanicallyBoosted(entity, move);
        return new ActionBattleHudPayload.MoveState(
                move.getName(), effectiveType, FightOrFlightAdapter.currentPp(move), FightOrFlightAdapter.maxPp(move), FightOrFlightAdapter.supports(move) && controlAllowed && fightingAllowed && dragonAllowed,
                session.pokemonAbilitySlotCooldownRemainingTicks(pokemon.getUuid(), slot, currentTick),
                session.pokemonAbilitySlotCooldownDurationTicks(pokemon.getUuid(), slot, currentTick),
                net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRuntime
                        .momentum(session, pokemon.getUuid()), boosted
        );
    }

    private static int currentHealth(Pokemon pokemon) { return invokeIntGetter(pokemon, "getCurrentHealth", 0); }
    private static int maxHealth(Pokemon pokemon) { return Math.max(1, invokeIntGetter(pokemon, "getMaxHealth", Math.max(1, currentHealth(pokemon)))); }

    private static int invokeIntGetter(Object target, String methodName, int fallback) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Number number ? number.intValue() : fallback;
        } catch (ReflectiveOperationException exception) {
            return fallback;
        }
    }
}

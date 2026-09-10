package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveDescriptor;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveMetadataResolver;
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
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterVisuals;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockVisualRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugTrainingStat;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugVisuals;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicHudProjection;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
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
        if (ActionBattlePokemonSelection.nextUsable(player, session.playerActivePartyIndex(player.getUUID())) == null) {
            swapCooldownRemaining = 1L;
            swapCooldownDuration = 1L;
        }
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
        if (ActionBattlePersistentController.global().has(
                session.battleId(), playerPokemon.getUuid(), ActionBattlePersistentType.BOUND, currentTick)) {
            moveHereCooldownRemaining = Math.max(1L, ActionBattlePersistentController.global().remainingTicks(
                    session.battleId(), playerPokemon.getUuid(), ActionBattlePersistentType.BOUND, currentTick));
            moveHereCooldownDuration = Math.max(1L, ActionBattlePersistentController.global().durationTicks(
                    session.battleId(), playerPokemon.getUuid(), ActionBattlePersistentType.BOUND));
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
                activeStates(session, playerPokemon, currentTick),
                activeStates(session, trainerPokemon, currentTick),
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

    private static List<ActionBattleHudPayload.StatusState> activeStates(ActionBattleSession session,
                                                                          Pokemon pokemon, long currentTick) {
        List<ActionBattleHudPayload.StatusState> states = new ArrayList<>(mechanicStates(session, pokemon, currentTick));
        states.addAll(statusStates(session.battleId(), session.dungeonSessionId(), pokemon.getUuid(), currentTick));
        return List.copyOf(states);
    }

    private static List<ActionBattleHudPayload.StatusState> mechanicStates(ActionBattleSession session,
                                                                            Pokemon pokemon, long currentTick) {
        if (session == null || pokemon == null || pokemon.getEntity() == null || pokemon.getEntity().isRemoved()) {
            return List.of();
        }
        List<String> actual = new ArrayList<>(2);
        if (pokemon.getPrimaryType() != null) actual.add(pokemon.getPrimaryType().getName());
        if (pokemon.getSecondaryType() != null) actual.add(pokemon.getSecondaryType().getName());
        List<String> adaptive = new ArrayList<>(ActionBattleTypeMechanicIdentity.adaptiveTypes(pokemon.getEntity()));
        List<String> identities = ActionBattleTypeMechanicHudProjection.identities(true, actual, adaptive);
        List<ActionBattleHudPayload.StatusState> states = new ArrayList<>(identities.size());
        for (String identity : identities) {
            String id = "MECHANIC_" + identity.toUpperCase(java.util.Locale.ROOT);
            long value = 1L;
            long maximum = 1L;
            if ("normal".equals(identity) && !adaptive.isEmpty()) {
                id += "__" + adaptive.stream().sorted().map(valueType -> valueType.toUpperCase(java.util.Locale.ROOT))
                        .collect(java.util.stream.Collectors.joining("_"));
            } else if ("fire".equals(identity)) {
                var view = net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireRuntime
                        .view(session.battleId(), pokemon.getUuid(), currentTick);
                id += view.inferno() ? "_INFERNO" : "_PRESSURE";
                value = view.amount();
                maximum = net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFirePressureState.MAX_PRESSURE;
            } else if ("flying".equals(identity)) {
                value = net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRuntime
                        .momentum(session, pokemon.getUuid());
                maximum = net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRules.MAX_MOMENTUM;
            } else if ("bug".equals(identity)) {
                var bug = ActionBattleBugController.global().state(session.battleId(), pokemon.getUuid()).orElse(null);
                if (bug != null) {
                    for (ActionBattleBugTrainingStat branch : ActionBattleBugTrainingStat.values()) {
                        if (bug.lockRemaining(branch, currentTick) > 0L) {
                            id += "_" + branch.name();
                            break;
                        }
                    }
                }
            } else if ("dragon".equals(identity)) {
                if (net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime
                        .dragonSleep(session, pokemon.getUuid(), currentTick)) {
                    id += "_SLEEP";
                    value = controllerStatusRemaining(session.dungeonSessionId(), pokemon.getUuid(),
                            ActionBattleStatus.SLEEP, currentTick);
                    maximum = controllerStatusDuration(session.dungeonSessionId(), pokemon.getUuid(),
                            ActionBattleStatus.SLEEP, currentTick);
                } else {
                    var dragon = net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonController
                            .global().view(session.dungeonSessionId(), pokemon.getUuid(), currentTick).orElse(null);
                    if (dragon != null) {
                        id += "_" + dragon.phase().name();
                        value = dragon.phase() == net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonState.Phase.BUILDUP
                                ? Math.max(0L, net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRules.ACTIVATION_DURATION_TICKS
                                - dragon.activationRemainingTicks()) : dragon.phaseRemainingTicks();
                        maximum = dragon.phase() == net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonState.Phase.BUILDUP
                                ? net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRules.ACTIVATION_DURATION_TICKS
                                : Math.max(1L, net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRules.ACTIVE_DURATION_TICKS);
                    }
                }
            } else if ("steel".equals(identity)) {
                var steel = net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime
                        .view(session.battleId(), pokemon.getUuid(), currentTick).orElse(null);
                if (steel != null) {
                    id += "_" + steel.branch().name();
                    value = steel.remainingTicks();
                    maximum = steel.totalTicks();
                }
            }
            states.add(new ActionBattleHudPayload.StatusState(id, value, Math.max(1L, maximum)));
        }
        return List.copyOf(states);
    }

    private static long controllerStatusRemaining(UUID scope, UUID pokemonId,
                                                   ActionBattleStatus status, long tick) {
        return ActionBattleEffectController.global().statusRemainingTicks(scope, pokemonId, status, tick);
    }

    private static long controllerStatusDuration(UUID scope, UUID pokemonId,
                                                  ActionBattleStatus status, long tick) {
        return Math.max(1L, ActionBattleEffectController.global().statusDurationTicks(scope, pokemonId, status, tick));
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
        drowsyStatusState(pokemonUUID, currentTick).ifPresent(states::add);
        long infatuationRemaining = net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleInfatuationController
                .remainingTicks(battleId, pokemonUUID, currentTick);
        if (infatuationRemaining > 0L) states.add(new ActionBattleHudPayload.StatusState("INFATUATION",
                infatuationRemaining, net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleInfatuationRules.DURATION_TICKS));
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
        typeEffects.immobilizedView(dungeonSessionId, pokemonUUID, currentTick).ifPresent(view ->
                states.add(new ActionBattleHudPayload.StatusState(ActionBattleWaterVisuals.IMMOBILIZED_STATUS_ID,
                        view.remainingTicks(), view.totalDurationTicks())));
        net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleRampageController.global()
                .view(battleId, pokemonUUID, currentTick).ifPresent(view -> {
                    if (view.active()) states.add(new ActionBattleHudPayload.StatusState("RAMPAGE",
                            view.activeRemainingTicks(), net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleRampageState.ACTIVE_DURATION_TICKS));
                    else if (view.exhausted()) states.add(new ActionBattleHudPayload.StatusState("EXHAUSTED",
                            view.exhaustedRemainingTicks(), net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleRampageState.EXHAUSTED_DURATION_TICKS));
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

    private static java.util.Optional<ActionBattleHudPayload.StatusState> drowsyStatusState(UUID pokemonUUID, long currentTick) {
        UUID sessionId = DungeonSession.isActive() ? DungeonSession.getSessionId() : null;
        if (sessionId == null) return java.util.Optional.empty();
        return ActionBattleEffectController.global().drowsyView(sessionId, pokemonUUID, currentTick)
                .filter(view -> view.remainingTicks() > 0L)
                .map(view -> new ActionBattleHudPayload.StatusState(
                        net.epiac9.cobblemonnml.battle.action.effect.status.ActionBattleDrowsyRules.HUD_STATUS_ID,
                        view.remainingTicks(), view.totalDurationTicks()));
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
        boolean fightingAllowed = net.epiac9.cobblemonnml.battle.action.effect.control.ActionBattleRampageController
                .global().canUseAbility(session.battleId(), pokemon.getUuid(), move.getName(), currentTick);
        boolean dragonAllowed = !net.epiac9.cobblemonnml.battle.action.typeeffect.dragon.ActionBattleDragonRuntime
                .blocksTrainerCommands(session, pokemon.getUuid(), currentTick);
        PokemonEntity entity = pokemon.getEntity();
        ActionBattleMoveDescriptor descriptor = ActionBattleMoveMetadataResolver.resolve(entity, move);
        return new ActionBattleHudPayload.MoveState(
                move.getName(), descriptor.effectiveType(), descriptor.currentPp(), descriptor.maxPp(), FightOrFlightAdapter.supports(move) && controlAllowed && fightingAllowed && dragonAllowed,
                session.pokemonAbilitySlotCooldownRemainingTicks(pokemon.getUuid(), slot, currentTick),
                session.pokemonAbilitySlotCooldownDurationTicks(pokemon.getUuid(), slot, currentTick),
                net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRuntime
                        .momentum(session, pokemon.getUuid())
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

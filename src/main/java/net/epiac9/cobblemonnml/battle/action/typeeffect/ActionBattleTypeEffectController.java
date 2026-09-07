package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleDrowsyTracker;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.poison.ActionBattlePoisonRules;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricTracker;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleParalysisState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActionBattleTypeEffectController {
    private static final ActionBattleTypeEffectController GLOBAL = new ActionBattleTypeEffectController();

    private final Map<UUID, ActionBattleTypeEffectState> states = new HashMap<>();
    private UUID activeSessionId;

    public static ActionBattleTypeEffectController global() { return GLOBAL; }

    public void guardSession(UUID sessionId) {
        if (sessionId == null) {
            clearAll();
            return;
        }
        if (sessionId.equals(activeSessionId)) return;
        states.clear();
        net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleParalysisController.global().clearSession(activeSessionId);
        activeSessionId = sessionId;
    }

    public boolean applyFirePressure(UUID sessionId, UUID pokemonUUID, double amount, long currentTick,
                                     boolean fireTyped, boolean hazeActive) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return false;
        ActionBattleTypeEffectState state = states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new);
        boolean applied = state.applyFirePressure(amount, currentTick, fireTyped, hazeActive);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return applied;
    }

    public boolean applyIceApplication(UUID sessionId, UUID pokemonUUID, long currentTick,
                                       boolean iceTyped, boolean hazeActive) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return false;
        ActionBattleTypeEffectState state = states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new);
        boolean applied = state.applyIceApplication(currentTick, iceTyped, hazeActive);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return applied;
    }

    public boolean applyDrowsy(UUID sessionId, UUID pokemonUUID, long currentTick) {
        return applyDrowsy(sessionId, pokemonUUID, currentTick, ActionBattleDrowsyTracker.CompletionRoute.SLEEP);
    }

    public boolean applyDrowsy(UUID sessionId, UUID pokemonUUID, long currentTick,
                               ActionBattleDrowsyTracker.CompletionRoute route) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return false;
        ActionBattleTypeEffectState state = states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new);
        boolean applied = state.applyDrowsy(currentTick, route);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return applied;
    }

    public boolean completeDrowsy(UUID sessionId, UUID pokemonUUID, long currentTick, int durationTicks,
                                   ActionBattleDrowsyTracker.CompletionRoute route) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null && state.completeDrowsy(currentTick, durationTicks, route);
    }

    public boolean applyPoisonMove(UUID sessionId, UUID pokemonUUID, long currentTick,
                                   boolean poisonTyped, int penetratedGain) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L || penetratedGain <= 0) return false;
        ActionBattleTypeEffectState state = states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new);
        boolean applied = state.applyPoisonMove(currentTick, poisonTyped, penetratedGain);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return applied;
    }

    public ActionBattleElectricTracker.ApplyChargeResult addElectricCharge(UUID sessionId, UUID pokemonUUID,
                                                                            int amount, long currentTick,
                                                                            boolean electricTyped, boolean hazeActive) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L || amount <= 0) {
            return ActionBattleElectricTracker.ApplyChargeResult.IGNORED;
        }
        ActionBattleTypeEffectState state = states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new);
        ActionBattleElectricTracker.ApplyChargeResult result = state.addElectricCharge(amount, currentTick, electricTyped, hazeActive);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return result;
    }

    public boolean applyExternalElectricParalysis(UUID sessionId, UUID pokemonUUID, long currentTick,
                                                    boolean electricTyped, boolean hazeActive) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return false;
        ActionBattleTypeEffectState state = states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new);
        boolean result = state.applyExternalElectricParalysis(currentTick, electricTyped, hazeActive);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return result;
    }

    public ActionBattleParalysisState.FlinchContributionResult addElectricParalysisFlinch(UUID sessionId,
                                                                                            UUID pokemonUUID,
                                                                                            int amount, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? ActionBattleParalysisState.FlinchContributionResult.IGNORED
                : state.addElectricParalysisFlinch(amount, currentTick);
    }

    public ActionBattleWaterState.ApplyShieldResult applyAquaShield(UUID sessionId, UUID pokemonUUID,
                                                                     long currentTick, boolean waterTyped,
                                                                     boolean protectActive) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return null;
        return states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new)
                .applyAquaShield(currentTick, waterTyped, protectActive);
    }

    public boolean breakAquaShield(UUID sessionId, UUID pokemonUUID, long currentTick, boolean protectActive) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null && state.breakAquaShield(currentTick, protectActive);
    }

    public boolean applyImmobilized(UUID sessionId, UUID pokemonUUID, long currentTick) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return false;
        return states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new).applyImmobilized(currentTick);
    }

    public Optional<ActionBattleWaterState.AquaShieldView> aquaShieldView(UUID sessionId, UUID pokemonUUID,
                                                                           long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? Optional.empty() : state.aquaShieldView(currentTick);
    }

    public Optional<ActionBattleWaterState.ImmobilizedView> immobilizedView(UUID sessionId, UUID pokemonUUID,
                                                                             long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? Optional.empty() : state.immobilizedView(currentTick);
    }

    public java.util.List<ActionBattleWaterState.ShieldEndEvent> drainWaterShieldEndEvents(
            UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? java.util.List.of() : state.drainWaterShieldEndEvents();
    }

    public void applyGrassEmpower(UUID sessionId, UUID pokemonUUID, double multiplier) {
        if (!validSession(sessionId) || pokemonUUID == null) return;
        states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new).applyGrassEmpower(multiplier);
    }

    public void applyGrassMovement(UUID sessionId, UUID pokemonUUID, long currentTick) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return;
        states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new).applyGrassMovement(currentTick);
    }

    public boolean applyLeechSeed(UUID sessionId, UUID pokemonUUID, long currentTick) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) return false;
        return states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new).applyLeechSeed(currentTick);
    }

    public ActionBattleGrassState.GrassMoveCommit commitGrassMove(UUID sessionId, UUID pokemonUUID, boolean grassMove) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? new ActionBattleGrassState.GrassMoveCommit(1.0D, false) : state.commitGrassMove(grassMove);
    }

    public double grassMovementMultiplier(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? 1.0D : state.grassMovementMultiplier(currentTick);
    }

    public Optional<ActionBattleGrassState.EmpowerView> grassEmpowerView(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? Optional.empty() : state.grassEmpowerView();
    }

    public Optional<ActionBattleGrassState.MovementView> grassMovementView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? Optional.empty() : state.grassMovementView(currentTick);
    }

    public Optional<ActionBattleGrassState.LeechSeedView> leechSeedView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? Optional.empty() : state.leechSeedView(currentTick);
    }

    public ActionBattleGroundState.ApplyResult applyGround(UUID sessionId, UUID pokemonUUID, long currentTick,
                                                            boolean groundTypedTarget) {
        if (!validSession(sessionId) || pokemonUUID == null || currentTick < 0L) {
            return ActionBattleGroundState.ApplyResult.IGNORED;
        }
        return states.computeIfAbsent(pokemonUUID, ActionBattleTypeEffectState::new)
                .applyGround(currentTick, groundTypedTarget);
    }

    public Optional<ActionBattleGroundState.View> groundView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null && currentTick >= 0L
                ? states.get(pokemonUUID) : null;
        return state == null ? Optional.empty() : state.groundView(currentTick);
    }

    public double groundMovementMultiplier(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null && currentTick >= 0L
                ? states.get(pokemonUUID) : null;
        return state == null ? 1.0D : state.groundMovementMultiplier(currentTick);
    }

    public boolean groundBlocksMovement(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null && currentTick >= 0L
                ? states.get(pokemonUUID) : null;
        return state != null && state.groundBlocksMovement(currentTick);
    }

    public boolean groundBlocksRecall(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null && currentTick >= 0L
                ? states.get(pokemonUUID) : null;
        return state != null && state.groundBlocksRecall(currentTick);
    }

    public boolean expelGround(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state == null || !state.expelGround()) return false;
        if (state.isEmpty()) states.remove(pokemonUUID);
        return true;
    }

    public boolean clearGroundPokemon(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state == null || !state.clearGround()) return false;
        if (state.isEmpty()) states.remove(pokemonUUID);
        return true;
    }

    public List<GroundTickEvent> tickSession(UUID sessionId, long currentTick) {
        if (!validSession(sessionId) || currentTick < 0L) return List.of();
        List<GroundTickEvent> groundEvents = new ArrayList<>();
        for (Map.Entry<UUID, ActionBattleTypeEffectState> entry : states.entrySet()) {
            ActionBattleGroundState.Branch branch = entry.getValue().groundBranch();
            ActionBattleGroundState.TickResult groundResult = entry.getValue().tickGround(currentTick);
            if (branch != null && groundResult != ActionBattleGroundState.TickResult.NONE) {
                groundEvents.add(new GroundTickEvent(entry.getKey(), branch, groundResult));
            }
            boolean sleeping = ActionBattleEffectController.global()
                    .hasStatus(sessionId, entry.getKey(), ActionBattleStatus.SLEEP, currentTick);
            entry.getValue().tick(currentTick, sleeping);
        }
        states.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return List.copyOf(groundEvents);
    }

    public Optional<ActionBattleTypeEffectState.FireView> fireView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state == null) return Optional.empty();
        Optional<ActionBattleTypeEffectState.FireView> view = state.fireView(currentTick);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return view;
    }

    public Optional<ActionBattleTypeEffectState.IceView> iceView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state == null) return Optional.empty();
        Optional<ActionBattleTypeEffectState.IceView> view = state.iceView(currentTick);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return view;
    }

    public Optional<ActionBattleTypeEffectState.DrowsyView> drowsyView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.drowsyView(currentTick) : Optional.empty();
    }

    public Optional<ActionBattleTypeEffectState.PoisonView> poisonView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state == null) return Optional.empty();
        Optional<ActionBattleTypeEffectState.PoisonView> view = state.poisonView(currentTick);
        if (state.isEmpty()) states.remove(pokemonUUID);
        return view;
    }

    public Optional<ActionBattleTypeEffectState.ElectricChargeView> electricChargeView(UUID sessionId, UUID pokemonUUID,
                                                                                        long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? Optional.empty() : state.electricChargeView(currentTick);
    }

    public Optional<ActionBattleTypeEffectState.ElectricParalysisView> electricParalysisView(UUID sessionId, UUID pokemonUUID,
                                                                                              long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? Optional.empty() : state.electricParalysisView(currentTick);
    }

    public int electricSpeedStages(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? 0 : state.electricSpeedStages(currentTick);
    }

    public double modifyOutgoingElectricDamage(UUID sessionId, UUID pokemonUUID, boolean electricMove,
                                               boolean damaging, double damage, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state == null ? damage : state.modifyOutgoingElectricDamage(electricMove, damaging, damage, currentTick);
    }

    public boolean hasActiveDrowsy(UUID sessionId, UUID pokemonUUID, long currentTick) {
        return drowsyView(sessionId, pokemonUUID, currentTick).filter(view -> view.remainingTicks() > 0L).isPresent();
    }

    public Optional<ActionBattleDrowsyTracker.CompletionState> fairyCompletionView(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.fairyCompletionView(currentTick) : Optional.empty();
    }

    public int fireAttackStages(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.fireAttackStages(currentTick) : 0;
    }

    public int iceDefenseStages(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.iceDefenseStages(currentTick) : 0;
    }

    public int fairySpecialDefenseStages(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.fairySpecialDefenseStages(currentTick) : 0;
    }

    public int poisonSpecialAttackStages(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.poisonSpecialAttackStages(currentTick) : 0;
    }

    public int poisonMoveAccumulationGain(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.poisonMoveAccumulationGain() : ActionBattlePoisonRules.BASE_MOVE_GAIN;
    }

    public int nextDrowsyDurationTicks(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.nextDrowsyDurationTicks() : ActionBattleFairyRules.BASE_DROWSY_DURATION_TICKS;
    }

    public long drowsyCleanResetEndTick(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.drowsyCleanResetEndTick() : -1L;
    }

    public ActionBattleDrowsyTracker.CompletionRoute pendingDrowsyCompletionRoute(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.pendingDrowsyCompletionRoute() : ActionBattleDrowsyTracker.CompletionRoute.SLEEP;
    }

    public java.util.Set<UUID> trackedPokemonIds(UUID sessionId) {
        return validSession(sessionId) ? java.util.Set.copyOf(states.keySet()) : java.util.Set.of();
    }

    public List<ActionBattleTypeEffectState.StatStageEvent> drainStatStageEvents(UUID sessionId) {
        if (!validSession(sessionId)) return List.of();
        List<ActionBattleTypeEffectState.StatStageEvent> events = new ArrayList<>();
        for (ActionBattleTypeEffectState state : states.values()) events.addAll(state.drainStatStageEvents());
        return List.copyOf(events);
    }

    public int iceHitsRequired(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null ? state.iceHitsRequired(currentTick) : ActionBattleIceRules.BASE_HITS_REQUIRED;
    }

    public void suppressFireBonusByHaze(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state != null) state.suppressFireBonusByHaze();
    }

    public void suppressIceDefenseByHaze(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state != null) state.suppressIceDefenseByHaze();
    }

    public void suppressFairySpecialDefenseByHaze(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state != null) state.suppressFairySpecialDefenseByHaze();
    }

    public void suppressPoisonSpecialAttackByHaze(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state != null) state.suppressPoisonSpecialAttackByHaze();
    }

    public void suppressElectricSpeedByHaze(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state != null) state.suppressElectricSpeedByHaze();
    }

    public boolean cancelDrowsyOnRecall(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        return state != null && state.cancelDrowsyOnRecall(currentTick);
    }

    public void onPokemonUnavailable(UUID sessionId, UUID pokemonUUID, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state != null) {
            state.onPokemonUnavailable(currentTick);
            if (state.isEmpty()) states.remove(pokemonUUID);
        }
    }

    public void onPokemonAvailable(UUID sessionId, UUID pokemonUUID) {
        ActionBattleTypeEffectState state = validSession(sessionId) && pokemonUUID != null ? states.get(pokemonUUID) : null;
        if (state != null) state.onPokemonAvailable();
    }

    public double modifyDamage(UUID sessionId, UUID targetPokemonUUID, boolean fireMove, double damage, long currentTick) {
        return modifyDamage(sessionId, targetPokemonUUID, fireMove, false, damage, currentTick);
    }

    public double modifyDamage(UUID sessionId, UUID targetPokemonUUID, boolean fireMove, boolean iceMove,
                               double damage, long currentTick) {
        return modifyDamage(sessionId, targetPokemonUUID, fireMove, iceMove, false, damage, currentTick);
    }

    public double modifyDamage(UUID sessionId, UUID targetPokemonUUID, boolean fireMove, boolean iceMove,
                               boolean poisonMove, double damage, long currentTick) {
        ActionBattleTypeEffectState state = validSession(sessionId) && targetPokemonUUID != null ? states.get(targetPokemonUUID) : null;
        boolean burned = state != null && state.isBurning(currentTick);
        boolean frostbitten = state != null && state.isFrostbitten(currentTick);
        boolean toxic = state != null && state.isToxic(currentTick);
        double fireModified = ActionBattleFireRules.modifyIncomingDamage(damage, fireMove, burned);
        double iceModified = ActionBattleIceRules.modifyIncomingDamage(fireModified, iceMove, frostbitten);
        return ActionBattlePoisonRules.modifyIncomingDamage(iceModified, poisonMove, toxic);
    }

    public void clearPokemon(UUID sessionId, UUID pokemonUUID) {
        if (validSession(sessionId) && pokemonUUID != null) {
            states.remove(pokemonUUID);
            net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleParalysisController.global().clearPokemon(sessionId, pokemonUUID);
        }
    }

    public void clearSession(UUID sessionId) {
        if (!validSession(sessionId)) return;
        states.clear();
        net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleParalysisController.global().clearSession(sessionId);
        activeSessionId = null;
    }

    public void clearAll() {
        states.clear();
        net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleParalysisController.global().clearSession(activeSessionId);
        activeSessionId = null;
    }

    public int trackedPokemonCount(UUID sessionId) { return validSession(sessionId) ? states.size() : 0; }

    private boolean validSession(UUID sessionId) {
        return sessionId != null && sessionId.equals(activeSessionId);
    }

    public record GroundTickEvent(UUID pokemonId, ActionBattleGroundState.Branch branch,
                                  ActionBattleGroundState.TickResult result) {}
}

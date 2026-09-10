package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterState;

import java.util.Optional;
import java.util.UUID;

public final class ActionBattleTypeEffectState {
    private final UUID pokemonUUID;
    private ActionBattleWaterState water;
    private ActionBattleGrassState grass;
    private ActionBattleGroundState ground;

    ActionBattleTypeEffectState(UUID pokemonUUID) {
        if (pokemonUUID == null) throw new IllegalArgumentException("Pokemon ID cannot be null.");
        this.pokemonUUID = pokemonUUID;
    }

    void tick(long currentTick) {
        if (water != null) {
            water.tick(currentTick);
            if (water.isEmpty()) water = null;
        }
        if (grass != null) {
            grass.tick(currentTick);
            if (grass.isEmpty(currentTick)) grass = null;
        }
    }

    ActionBattleWaterState.ApplyShieldResult applyAquaShield(long currentTick, boolean waterTyped, boolean protectActive) {
        if (water == null) water = new ActionBattleWaterState();
        return water.applyShield(currentTick, waterTyped, protectActive);
    }

    boolean breakAquaShield(long currentTick, boolean protectActive) {
        return water != null && water.breakShield(currentTick, protectActive);
    }

    boolean applyImmobilized(long currentTick) {
        if (water == null) water = new ActionBattleWaterState();
        return water.applyImmobilized(currentTick);
    }

    Optional<ActionBattleWaterState.AquaShieldView> aquaShieldView(long currentTick) {
        return water == null ? Optional.empty() : water.aquaShieldView(currentTick);
    }

    Optional<ActionBattleWaterState.ImmobilizedView> immobilizedView(long currentTick) {
        return water == null ? Optional.empty() : water.immobilizedView(currentTick);
    }

    java.util.List<ActionBattleWaterState.ShieldEndEvent> drainWaterShieldEndEvents() {
        return water == null ? java.util.List.of() : water.drainShieldEndEvents();
    }

    void applyGrassEmpower(double multiplier) {
        if (grass == null) grass = new ActionBattleGrassState();
        grass.applyEmpower(multiplier);
    }

    void applyGrassMovement(long currentTick) {
        if (grass == null) grass = new ActionBattleGrassState();
        grass.applyMovementBurst(currentTick);
    }

    boolean applyLeechSeed(long currentTick) {
        if (grass == null) grass = new ActionBattleGrassState();
        return grass.applyLeechSeed(currentTick);
    }

    ActionBattleGrassState.GrassMoveCommit commitGrassMove(boolean grassMove) {
        return grass == null ? new ActionBattleGrassState.GrassMoveCommit(1.0D, false) : grass.commitMove(grassMove);
    }

    double grassMovementMultiplier(long currentTick) {
        return grass == null ? 1.0D : grass.movementMultiplier(currentTick);
    }

    Optional<ActionBattleGrassState.EmpowerView> grassEmpowerView() { return grass == null ? Optional.empty() : grass.empowerView(); }
    Optional<ActionBattleGrassState.MovementView> grassMovementView(long currentTick) { return grass == null ? Optional.empty() : grass.movementView(currentTick); }
    Optional<ActionBattleGrassState.LeechSeedView> leechSeedView(long currentTick) { return grass == null ? Optional.empty() : grass.leechSeedView(currentTick); }

    ActionBattleGroundState.ApplyResult applyGround(long currentTick, boolean groundTyped) {
        if (ground == null) ground = new ActionBattleGroundState(groundTyped);
        return ground.apply(currentTick);
    }

    Optional<ActionBattleGroundState.View> groundView(long currentTick) { return ground == null ? Optional.empty() : ground.view(currentTick); }
    ActionBattleGroundState.TickResult tickGround(long currentTick) { return ground == null ? ActionBattleGroundState.TickResult.NONE : ground.tick(currentTick); }
    ActionBattleGroundState.Branch groundBranch() { return ground == null ? null : ground.branch(); }

    double groundMovementMultiplier(long currentTick) {
        return groundView(currentTick).map(value -> ActionBattleGroundRules.movementMultiplier(
                value.depthPercent(), value.branch() == ActionBattleGroundState.Branch.DIG)).orElse(1.0D);
    }

    boolean groundBlocksMovement(long currentTick) {
        return groundView(currentTick).map(view -> view.branch() == ActionBattleGroundState.Branch.SINK
                && view.depthPercent() == 90).orElse(false);
    }

    boolean groundBlocksRecall(long currentTick) { return groundView(currentTick).isPresent(); }
    boolean expelGround() { return ground != null && ground.expel(); }

    boolean clearGround() {
        if (ground == null || ground.isEmpty()) return false;
        ground.clearSilently();
        ground = null;
        return true;
    }

    boolean isEmpty() { return water == null && grass == null && (ground == null || ground.isEmpty()); }
    UUID pokemonUUID() { return pokemonUUID; }
}

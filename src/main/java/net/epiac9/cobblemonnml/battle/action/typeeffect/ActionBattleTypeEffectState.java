package net.epiac9.cobblemonnml.battle.action.typeeffect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterState;

import java.util.Optional;
import java.util.UUID;

public final class ActionBattleTypeEffectState {
    private final UUID pokemonUUID;
    private ActionBattleWaterState water;
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
    }

    boolean applyImmobilized(long currentTick) {
        if (water == null) water = new ActionBattleWaterState();
        return water.applyImmobilized(currentTick);
    }

    boolean breakWaterTrapOnDamage(int actualDamage) {
        return water != null && water.breakImmobilizedOnDamage(actualDamage);
    }

    Optional<ActionBattleWaterState.ImmobilizedView> immobilizedView(long currentTick) {
        return water == null ? Optional.empty() : water.immobilizedView(currentTick);
    }


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
        return groundView(currentTick).map(view -> view.depthPercent() >= 100).orElse(false);
    }

    boolean groundBlocksRecall(long currentTick) {
        return groundView(currentTick).map(view -> view.depthPercent()
                >= ActionBattleGroundRules.SWAP_LOCK_PERCENT).orElse(false);
    }
    boolean expelGround() { return ground != null && ground.expel(); }

    boolean clearGround() {
        if (ground == null || ground.isEmpty()) return false;
        ground.clearSilently();
        ground = null;
        return true;
    }

    boolean isEmpty() { return water == null && (ground == null || ground.isEmpty()); }
    UUID pokemonUUID() { return pokemonUUID; }
}

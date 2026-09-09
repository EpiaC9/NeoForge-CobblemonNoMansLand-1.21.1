package net.epiac9.cobblemonnml.battle.action.typeeffect.flying;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleLineOfSight;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleTargetTracker;
import net.epiac9.cobblemonnml.battle.action.ActionBattleTargetingRules;
import net.epiac9.cobblemonnml.battle.action.ActionBattleVisualTrackingRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public final class ActionBattleFlyingRuntime {
    private ActionBattleFlyingRuntime() {}

    public static void tickPokemon(ActionBattleSession session, PokemonEntity pokemon, long currentTick) {
        if (session == null || pokemon == null || pokemon.isRemoved()
                || !(pokemon.level() instanceof ServerLevel level)) return;
        PokemonEntity target = trackedEnemy(session, level, pokemon.getPokemon().getUuid());
        boolean visible = false;
        if (target != null && !target.isRemoved() && target.isAlive()
                && ActionBattleDarkRuntime.canPerceive(session, pokemon, target, currentTick)) {
            var result = ActionBattleLineOfSight.evaluate(session, pokemon, target);
            ActionBattleTargetTracker.global().observe(session.battleId(), pokemon.getPokemon().getUuid(),
                    target.getPokemon().getUuid(), point(target), result);
            visible = result.visible();
            if (visible && ActionBattleVisualTrackingRules.shouldApplyVisualTracking(
                    session.hasDirectionalMovementIntent(pokemon.getPokemon().getUuid()),
                    ActionBattlePropulsionController.isActive(
                            session, pokemon.getPokemon().getUuid()))) {
                ActionBattleVisualTrackingRules.faceTarget(pokemon, target);
            }
        }
        ActionBattleFlyingController controller = ActionBattleFlyingController.global();
        if (controller.tick(session.battleId(), pokemon.getPokemon().getUuid(),
                ActionBattleTypeMechanicIdentity.hasMechanicBenefit(pokemon, "flying"), visible, currentTick)) {
            DebugLog.log("[CobblemonNML] Flying Momentum changed. Battle=" + session.battleId()
                    + ", pokemon=" + pokemon.getPokemon().getUuid()
                    + ", level=" + controller.momentum(session.battleId(), pokemon.getPokemon().getUuid())
                    + ", visible=" + visible);
        }
    }

    public static int momentum(ActionBattleSession session, UUID pokemonId) {
        return session != null ? ActionBattleFlyingController.global().momentum(session.battleId(), pokemonId) : 0;
    }

    public static void clearPokemon(ActionBattleSession session, UUID pokemonId) {
        if (session == null || pokemonId == null) return;
        ActionBattleFlyingController.global().clearPokemon(session.battleId(), pokemonId);
        ActionBattleTargetTracker.global().clearPokemon(session.battleId(), pokemonId);
    }

    public static void clearBattle(UUID battleId) {
        ActionBattleFlyingController.global().clearBattle(battleId);
        ActionBattleTargetTracker.global().clearBattle(battleId);
    }

    public static void clearAll() {
        ActionBattleFlyingController.global().clearAll();
        ActionBattleTargetTracker.global().clearAll();
    }

    private static PokemonEntity trackedEnemy(ActionBattleSession session, ServerLevel level, UUID pokemonId) {
        UUID targetEntityId = null;
        if (session.isPlayerPokemon(pokemonId)) {
            targetEntityId = session.trainerActiveEntityUUID();
        } else if (pokemonId.equals(session.trainerActivePokemonUUID())) {
            targetEntityId = session.trainerMoveTargetEntityUUID();
            if (targetEntityId == null) {
                for (UUID playerId : session.playerUUIDs()) {
                    targetEntityId = session.playerActiveEntityUUID(playerId);
                    if (targetEntityId != null) break;
                }
            }
        }
        Entity raw = targetEntityId != null ? level.getEntity(targetEntityId) : null;
        return raw instanceof PokemonEntity target ? target : null;
    }

    private static ActionBattleTargetingRules.Point point(PokemonEntity pokemon) {
        return new ActionBattleTargetingRules.Point(pokemon.getX(), pokemon.getY(), pokemon.getZ());
    }
}

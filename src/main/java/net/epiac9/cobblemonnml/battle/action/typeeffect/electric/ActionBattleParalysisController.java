package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleFlinchController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleParalysisController {
    private static final ActionBattleParalysisController GLOBAL = new ActionBattleParalysisController();
    private final ActionBattleTypeEffectController effects;
    private final Map<Key, Observation> observations = new HashMap<>();

    public ActionBattleParalysisController() { this(ActionBattleTypeEffectController.global()); }

    public ActionBattleParalysisController(ActionBattleTypeEffectController effects) {
        this.effects = effects != null ? effects : ActionBattleTypeEffectController.global();
    }

    public static ActionBattleParalysisController global() { return GLOBAL; }

    public ActionBattleParalysisState.FlinchContributionResult addElectricHit(
            ActionBattleTypeEffectController effects, UUID sessionId, UUID pokemonUUID,
            long currentTick, int suppliedAmount) {
        ActionBattleTypeEffectController owner = effects != null ? effects : this.effects;
        return owner.addElectricParalysisFlinch(sessionId, pokemonUUID, suppliedAmount, currentTick);
    }

    public ActionBattleParalysisState.FlinchContributionResult observeMovement(
            ActionBattleTypeEffectController effects, UUID sessionId, UUID pokemonUUID,
            Vec3 currentPosition, long currentTick, int suppliedAmount) {
        if (currentPosition == null) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        return observeMovement(effects, sessionId, pokemonUUID, currentPosition.x, currentPosition.y,
                currentPosition.z, currentTick, suppliedAmount);
    }

    public ActionBattleParalysisState.FlinchContributionResult addElectricHit(
            ActionBattleTypeEffectController effects, ActionBattleSession session, PokemonEntity target,
            long currentTick, int suppliedAmount, boolean contact) {
        if (session == null || target == null || target.isRemoved()) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        ActionBattleParalysisState.FlinchContributionResult result = addElectricHit(effects,
                session.dungeonSessionId(), target.getPokemon().getUuid(), currentTick, suppliedAmount);
        present(result, session, target, currentTick, contact);
        return result;
    }

    public ActionBattleParalysisState.FlinchContributionResult observeMovement(
            ActionBattleTypeEffectController effects, ActionBattleSession session, PokemonEntity target,
            long currentTick, int suppliedAmount) {
        if (session == null || target == null || target.isRemoved()) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        ActionBattleParalysisState.FlinchContributionResult result = observeMovement(effects,
                session.dungeonSessionId(), target.getPokemon().getUuid(), target.position(), currentTick, suppliedAmount);
        present(result, session, target, currentTick, false);
        return result;
    }

    public ActionBattleParalysisState.FlinchContributionResult observeMovement(
            ActionBattleTypeEffectController effects, UUID sessionId, UUID pokemonUUID,
            double x, double y, double z, long currentTick, int suppliedAmount) {
        if (sessionId == null || pokemonUUID == null || !finite(x, y, z) || currentTick < 0L) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        Key key = new Key(sessionId, pokemonUUID);
        ActionBattleTypeEffectController owner = effects != null ? effects : this.effects;
        if (owner.electricParalysisView(sessionId, pokemonUUID, currentTick).isEmpty()) {
            observations.remove(key);
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        Position currentPosition = new Position(x, y, z);
        Observation previous = observations.get(key);
        if (previous != null && currentTick < previous.tick()) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        observations.put(key, new Observation(currentPosition, currentTick));
        if (previous == null || previous.position().equals(currentPosition)) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        return addElectricHit(effects, sessionId, pokemonUUID, currentTick, suppliedAmount);
    }

    public ActionBattleParalysisState.FlinchContributionResult addElectricHit(UUID sessionId, UUID pokemonUUID,
                                                                                long currentTick, int suppliedAmount) {
        return effects.addElectricParalysisFlinch(sessionId, pokemonUUID, suppliedAmount, currentTick);
    }

    public ActionBattleParalysisState.FlinchContributionResult observeMovement(
            UUID sessionId, UUID pokemonUUID, Vec3 currentPosition, long currentTick, int suppliedAmount) {
        if (currentPosition == null) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        return observeMovement(sessionId, pokemonUUID, currentPosition.x, currentPosition.y, currentPosition.z,
                currentTick, suppliedAmount);
    }

    public ActionBattleParalysisState.FlinchContributionResult observeMovement(
            UUID sessionId, UUID pokemonUUID, double x, double y, double z, long currentTick, int suppliedAmount) {
        if (sessionId == null || pokemonUUID == null || !finite(x, y, z) || currentTick < 0L) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        Key key = new Key(sessionId, pokemonUUID);
        if (effects.electricParalysisView(sessionId, pokemonUUID, currentTick).isEmpty()) {
            observations.remove(key);
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        Position currentPosition = new Position(x, y, z);
        Observation previous = observations.get(key);
        if (previous != null && currentTick < previous.tick()) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        observations.put(key, new Observation(currentPosition, currentTick));
        if (previous == null || previous.position().equals(currentPosition)) {
            return ActionBattleParalysisState.FlinchContributionResult.IGNORED;
        }
        return addElectricHit(sessionId, pokemonUUID, currentTick, suppliedAmount);
    }

    public void clearPokemon(UUID sessionId, UUID pokemonUUID) {
        if (sessionId != null && pokemonUUID != null) observations.remove(new Key(sessionId, pokemonUUID));
    }

    public void clearSession(UUID sessionId) {
        if (sessionId != null) observations.keySet().removeIf(key -> sessionId.equals(key.sessionId()));
    }

    int trackedMovementCount(UUID sessionId) {
        if (sessionId == null) return 0;
        int count = 0;
        for (Key key : observations.keySet()) if (sessionId.equals(key.sessionId())) count++;
        return count;
    }

    private record Key(UUID sessionId, UUID pokemonUUID) {}
    private record Position(double x, double y, double z) {}
    private record Observation(Position position, long tick) {}

    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    private static void present(ActionBattleParalysisState.FlinchContributionResult result,
                                ActionBattleSession session, PokemonEntity target, long currentTick, boolean contact) {
        switch (ActionBattleElectricVisuals.visualFor(result)) {
            case SUBTLE_STATIC -> ActionBattleElectricVisuals.emitSubtleStatic(target);
            case PARALYSIS_FLINCH -> {
                if (ActionBattleFlinchController.applyWithoutVisuals(
                        session, target.getPokemon().getUuid(), currentTick, contact)) {
                    ActionBattleElectricVisuals.emitParalysisFlinch(target);
                }
            }
            case NONE -> {}
        }
    }
}

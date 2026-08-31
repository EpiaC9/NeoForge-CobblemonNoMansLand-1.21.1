package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatusApplication;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleStatusParticleController;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleParalysisController {
    private static final Map<Key, ActionBattleParalysisMovementTracker> MOVEMENT = new HashMap<>();

    private ActionBattleParalysisController() {}

    public static ApplicationResult apply(ActionBattleSession session, PokemonEntity target, long currentTick, float durationMultiplier, boolean contact) {
        if (session == null || target == null || target.isRemoved() || currentTick < 0L || !(durationMultiplier > 0.0F)) return null;
        UUID pokemonUUID = target.getPokemon().getUuid();
        ActionBattleEffectController effects = ActionBattleEffectController.global();
        ActionBattleStatusApplication application = effects.applyParalysis(session.battleId(), pokemonUUID, currentTick, durationMultiplier);
        if (application == null) return null;
        boolean flinched = false;
        float chance = effects.paralysisCheckChance(session.battleId(), pokemonUUID, currentTick);
        if (application == ActionBattleStatusApplication.PARALYSIS_REFRESHED && chance > 0.0F && chance > target.getRandom().nextFloat()) {
            effects.resetParalysisBuildup(session.battleId(), pokemonUUID, currentTick);
            resetMovementTracker(session.battleId(), pokemonUUID);
            flinched = ActionBattleFlinchController.apply(session, pokemonUUID, currentTick, contact);
            if (flinched && target.level() instanceof ServerLevel level) ActionBattleStatusParticleController.emitParalysisTriggerBurst(level, target);
        }
        return new ApplicationResult(application, flinched, chance);
    }

    public static void tickBattle(ActionBattleSession session, ServerLevel level) {
        if (session == null || level == null || session.state() != ActionBattleState.ACTIVE) return;
        tickPokemon(session, level, session.playerActivePokemonUUID(), session.playerActiveEntityUUID(),
                session.hasPlayerMoveTarget() || session.hasPlayerMoveCommand());
        tickPokemon(session, level, session.trainerActivePokemonUUID(), session.trainerActiveEntityUUID(),
                session.hasTrainerMoveCommand() || session.hasTrainerRepositionTarget());
    }

    public static void onAbilitySucceeded(UUID battleId, UUID pokemonUUID, long currentTick) {
        if (battleId == null || pokemonUUID == null || currentTick < 0L) return;
        ActionBattleEffectController.global().resetParalysisBuildup(battleId, pokemonUUID, currentTick);
        resetMovementTracker(battleId, pokemonUUID);
    }

    public static void onPokemonRecalled(UUID battleId, UUID pokemonUUID) {
        resetMovementTracker(battleId, pokemonUUID);
    }

    public static void clearBattle(UUID battleId) {
        if (battleId != null) MOVEMENT.keySet().removeIf(key -> battleId.equals(key.battleId()));
    }

    private static void tickPokemon(ActionBattleSession session, ServerLevel level, UUID pokemonUUID, UUID entityUUID, boolean movementActive) {
        if (pokemonUUID == null) return;
        ActionBattleEffectController effects = ActionBattleEffectController.global();
        long currentTick = level.getGameTime();
        Key key = new Key(session.battleId(), pokemonUUID);
        if (!effects.hasStatus(session.battleId(), pokemonUUID, ActionBattleStatus.PARALYSIS, currentTick)) {
            MOVEMENT.remove(key);
            return;
        }
        Entity raw = entityUUID != null ? level.getEntity(entityUUID) : null;
        if (!(raw instanceof PokemonEntity pokemonEntity) || pokemonEntity.isRemoved()) {
            MOVEMENT.remove(key);
            return;
        }
        ActionBattleParalysisMovementTracker tracker = MOVEMENT.computeIfAbsent(key, ignored -> new ActionBattleParalysisMovementTracker());
        int blocks = tracker.observe(pokemonEntity.getX(), pokemonEntity.getY(), pokemonEntity.getZ(), movementActive);
        for (int i = 0; i < blocks; i++) {
            float chance = effects.advanceParalysisChecks(session.battleId(), pokemonUUID, 1, currentTick);
            if (!(chance > 0.0F) || !(chance > pokemonEntity.getRandom().nextFloat())) continue;
            effects.resetParalysisBuildup(session.battleId(), pokemonUUID, currentTick);
            tracker.resetChain();
            boolean flinched = ActionBattleFlinchController.apply(session, pokemonUUID, currentTick, false);
            if (flinched) {
                ActionBattleStatusParticleController.emitParalysisTriggerBurst(level, pokemonEntity);
                DebugLog.log("[CobblemonNML] Paralysis movement check triggered Flinch. Battle=" + session.battleId()
                        + ", pokemon=" + pokemonUUID + ", chance=" + chance);
            }
            return;
        }
    }

    private static void resetMovementTracker(UUID battleId, UUID pokemonUUID) {
        if (battleId == null || pokemonUUID == null) return;
        ActionBattleParalysisMovementTracker tracker = MOVEMENT.get(new Key(battleId, pokemonUUID));
        if (tracker != null) tracker.resetChain();
    }

    public record ApplicationResult(ActionBattleStatusApplication application, boolean flinched, float flinchChance) {}
    private record Key(UUID battleId, UUID pokemonUUID) {}

}

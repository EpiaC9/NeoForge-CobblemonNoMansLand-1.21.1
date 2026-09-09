package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleFlinchVisualType;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleFlinchVisuals;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;

import java.util.UUID;

public final class ActionBattleFlinchController {
    public static final long COOLDOWN_PENALTY_TICKS = ActionBattleTiming.seconds(2L);

    private ActionBattleFlinchController() {}

    public static boolean apply(ActionBattleSession session, UUID targetPokemonUUID, long currentTick, boolean contact) {
        return apply(session, targetPokemonUUID, currentTick, contact, ActionBattleFlinchVisualType.NORMAL);
    }

    public static boolean apply(ActionBattleSession session, UUID targetPokemonUUID, long currentTick, boolean contact, ActionBattleFlinchVisualType visualType) {
        return applyInternal(session, targetPokemonUUID, currentTick, contact, visualType, true);
    }

    public static boolean applyWithoutVisuals(ActionBattleSession session, UUID targetPokemonUUID,
                                               long currentTick, boolean contact) {
        return applyInternal(session, targetPokemonUUID, currentTick, contact,
                ActionBattleFlinchVisualType.NORMAL, false);
    }

    private static boolean applyInternal(ActionBattleSession session, UUID targetPokemonUUID, long currentTick,
                                         boolean contact, ActionBattleFlinchVisualType visualType, boolean emitVisuals) {
        if (session == null || targetPokemonUUID == null || currentTick < 0L) return false;
        PokemonEntity targetEntity = findTargetEntity(session, targetPokemonUUID);
        if (targetEntity != null && !ActionBattleEffectApplicationGuard.allowsNewApplication(
                session, targetEntity, currentTick)) return false;
        if (!ActionBattleCommandController.cancelPendingOrders(
                session, targetPokemonUUID, ActionBattleCommandController.InterruptReason.CONTROL_EFFECT)) return false;
        if (!ActionBattleCommandController.addCooldownPenalty(session, targetPokemonUUID, currentTick, COOLDOWN_PENALTY_TICKS)) return false;
        ActionBattleProtectController.global().breakForControl(session.battleId(), targetPokemonUUID, currentTick, contact);
        if (emitVisuals && targetEntity != null) ActionBattleFlinchVisuals.emit(targetEntity, visualType);
        return true;
    }

    private static PokemonEntity findTargetEntity(ActionBattleSession session, UUID targetPokemonUUID) {
        ActionBattlePokemonRefs refs = ActionBattleRegistry.pokemonRefs(session.battleId());
        if (refs == null) return null;
        Pokemon pokemon = null;
        for (Pokemon candidate : refs.allPlayerPokemon()) {
            if (candidate != null && targetPokemonUUID.equals(candidate.getUuid())) { pokemon = candidate; break; }
        }
        if (pokemon == null) pokemon = refs.trainerPokemon();
        if (pokemon == null || !targetPokemonUUID.equals(pokemon.getUuid())) return null;
        PokemonEntity entity = pokemon.getEntity();
        return entity != null && !entity.isRemoved() ? entity : null;
    }
}

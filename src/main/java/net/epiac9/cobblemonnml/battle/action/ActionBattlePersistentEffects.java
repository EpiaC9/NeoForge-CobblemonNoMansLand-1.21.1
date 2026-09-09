package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentType;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;

public final class ActionBattlePersistentEffects {
    private ActionBattlePersistentEffects() {}

    public static boolean applyPerishSong(ActionBattleSession session, PokemonEntity source, PokemonEntity target, long currentTick) {
        if (!valid(session, source, target, currentTick)) return false;
        return ActionBattlePersistentController.global().applyPerishSong(session.battleId(), target.getPokemon().getUuid(), source.getPokemon().getUuid(), currentTick);
    }

    public static boolean applyNightmare(ActionBattleSession session, PokemonEntity source, PokemonEntity target, long currentTick) {
        if (!valid(session, source, target, currentTick)) return false;
        boolean sleeping = ActionBattleSleepController.isSleeping(session, target.getPokemon().getUuid(), currentTick);
        return ActionBattlePersistentController.global().applyNightmare(session.battleId(), target.getPokemon().getUuid(), source.getPokemon().getUuid(), sleeping, currentTick);
    }

    public static boolean applyBound(ActionBattleSession session, PokemonEntity source, PokemonEntity target, long currentTick) {
        if (!valid(session, source, target, currentTick)) return false;
        return ActionBattlePersistentController.global().applyBound(session.battleId(), target.getPokemon().getUuid(), source.getPokemon().getUuid(), currentTick);
    }

    public static boolean clear(ActionBattleSession session, Pokemon pokemon, ActionBattlePersistentType type, long currentTick) {
        return session != null && pokemon != null && type != null && currentTick >= 0L
                && ActionBattlePersistentController.global().clearEffect(session.battleId(), pokemon.getUuid(), type, currentTick);
    }

    private static boolean valid(ActionBattleSession session, PokemonEntity source, PokemonEntity target, long currentTick) {
        return session != null && source != null && target != null && !source.isRemoved() && !target.isRemoved()
                && currentTick >= 0L && ActionBattleEffectApplicationGuard.allowsNewApplication(session, target, currentTick);
    }
}

package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentController;
import net.epiac9.cobblemonnml.battle.action.persistent.ActionBattlePersistentType;

public final class ActionBattlePersistentEffects {
    private ActionBattlePersistentEffects() {}

    public static boolean applyLeechSeed(ActionBattleSession session, PokemonEntity source, PokemonEntity target, long currentTick) {
        if (!valid(session, source, target, currentTick)) return false;
        Pokemon pokemon = target.getPokemon();
        String primary = pokemon.getPrimaryType().getName();
        String secondary = pokemon.getSecondaryType() != null ? pokemon.getSecondaryType().getName() : null;
        return ActionBattlePersistentController.global().applyLeechSeed(session.battleId(), pokemon.getUuid(), source.getPokemon().getUuid(), primary, secondary, currentTick);
    }

    public static boolean applyGhostCurse(ActionBattleSession session, PokemonEntity source, PokemonEntity target, long currentTick) {
        if (!valid(session, source, target, currentTick)) return false;
        Pokemon caster = source.getPokemon();
        if (!ActionBattlePersistentController.global().applyGhostCurse(session.battleId(), target.getPokemon().getUuid(), caster.getUuid(), currentTick)) return false;
        int before = caster.getCurrentHealth();
        int cost = Math.max(1, caster.getMaxHealth() / 2);
        int after = Math.max(0, before - cost);
        caster.setCurrentHealth(after);
        ActionBattleDamageFeedbackController.global().recordDamage(session.battleId(), caster.getUuid(), before, after, ActionBattleDamageFeedbackCategory.DOT);
        return true;
    }

    public static boolean applyPerishSong(ActionBattleSession session, PokemonEntity source, PokemonEntity target, long currentTick) {
        if (!valid(session, source, target, currentTick)) return false;
        return ActionBattlePersistentController.global().applyPerishSong(session.battleId(), target.getPokemon().getUuid(), source.getPokemon().getUuid(), currentTick);
    }

    public static boolean applyNightmare(ActionBattleSession session, PokemonEntity source, PokemonEntity target, long currentTick) {
        if (!valid(session, source, target, currentTick)) return false;
        boolean sleeping = ActionBattleEffectController.global().hasStatus(session.battleId(), target.getPokemon().getUuid(), ActionBattleStatus.SLEEP, currentTick);
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
        return session != null && source != null && target != null && !source.isRemoved() && !target.isRemoved() && currentTick >= 0L;
    }
}

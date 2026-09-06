package net.epiac9.cobblemonnml.battle.action.effect;

import net.epiac9.cobblemonnml.battle.action.typeeffect.psychic.ActionBattlePsycUpController;

import java.util.UUID;

public final class ActionBattleStatLinkCleanup {
    private final ActionBattleEffectController effects;
    private final ActionBattlePsycUpController psycUp;

    public ActionBattleStatLinkCleanup(ActionBattleEffectController effects,
                                       ActionBattlePsycUpController psycUp) {
        if (effects == null || psycUp == null) throw new IllegalArgumentException("Cleanup services cannot be null.");
        this.effects = effects;
        this.psycUp = psycUp;
    }

    public void onPokemonUnavailable(UUID battleId, UUID pokemonId, long currentTick) {
        if (battleId == null || pokemonId == null || currentTick < 0L) return;
        effects.onPokemonRecalled(battleId, pokemonId, currentTick);
        psycUp.onPokemonUnavailable(battleId, pokemonId);
    }

    public void clearBattle(UUID battleId) {
        effects.clearBattle(battleId);
        psycUp.clearBattle(battleId);
    }

    public void clearAll() {
        effects.clearAll();
        psycUp.clearAll();
    }
}

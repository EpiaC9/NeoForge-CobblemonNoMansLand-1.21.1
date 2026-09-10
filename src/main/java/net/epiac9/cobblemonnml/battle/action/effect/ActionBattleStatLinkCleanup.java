package net.epiac9.cobblemonnml.battle.action.effect;

import java.util.UUID;

public final class ActionBattleStatLinkCleanup {
    private final ActionBattleEffectController effects;
    public ActionBattleStatLinkCleanup(ActionBattleEffectController effects) {
        if (effects == null) throw new IllegalArgumentException("Cleanup service cannot be null.");
        this.effects = effects;
    }

    public void onPokemonUnavailable(UUID battleId, UUID pokemonId, long currentTick) {
        if (battleId == null || pokemonId == null || currentTick < 0L) return;
        effects.onPokemonRecalled(battleId, pokemonId, currentTick);
    }

    public void clearBattle(UUID battleId) {
        effects.clearBattle(battleId);
    }

    public void clearAll() {
        effects.clearAll();
    }
}

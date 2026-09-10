package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

public final class ActionBattleWaterHealth {
    private ActionBattleWaterHealth() {}

    public static int toPokemonDamage(int pokemonMaxHealth, float liveMaxHealth, float liveDamage) {
        if (pokemonMaxHealth <= 0 || !(liveMaxHealth > 0.0F) || !(liveDamage > 0.0F)) return 0;
        return Math.max(1, (int) Math.ceil(pokemonMaxHealth * liveDamage / liveMaxHealth));
    }
}

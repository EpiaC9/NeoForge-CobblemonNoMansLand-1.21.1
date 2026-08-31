package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.pokemon.Pokemon;

final class ActionBattlePokemonRefs {
    private Pokemon playerPokemon;
    private Pokemon trainerPokemon;

    ActionBattlePokemonRefs(Pokemon playerPokemon, Pokemon trainerPokemon) {
        this.playerPokemon = playerPokemon;
        this.trainerPokemon = trainerPokemon;
    }

    Pokemon playerPokemon() { return playerPokemon; }
    Pokemon trainerPokemon() { return trainerPokemon; }
    void setPlayerPokemon(Pokemon pokemon) { playerPokemon = pokemon; }
    void setTrainerPokemon(Pokemon pokemon) { trainerPokemon = pokemon; }
}

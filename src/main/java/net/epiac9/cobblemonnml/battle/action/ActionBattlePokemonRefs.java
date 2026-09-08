package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ActionBattlePokemonRefs {
    private Pokemon playerPokemon;
    private Pokemon trainerPokemon;
    private final UUID initialPlayerUUID;
    private final Map<UUID, Pokemon> additionalPlayerPokemon = new HashMap<>();

    ActionBattlePokemonRefs(UUID initialPlayerUUID, Pokemon playerPokemon, Pokemon trainerPokemon) {
        this.initialPlayerUUID = initialPlayerUUID;
        this.playerPokemon = playerPokemon;
        this.trainerPokemon = trainerPokemon;
    }

    ActionBattlePokemonRefs(Pokemon playerPokemon, Pokemon trainerPokemon) {
        this(null, playerPokemon, trainerPokemon);
    }

    Pokemon playerPokemon() { return playerPokemon; }
    Pokemon trainerPokemon() { return trainerPokemon; }
    void setPlayerPokemon(Pokemon pokemon) { playerPokemon = pokemon; }
    void setTrainerPokemon(Pokemon pokemon) { trainerPokemon = pokemon; }
    Pokemon playerPokemon(UUID playerUUID) {
        if (playerUUID != null && playerUUID.equals(initialPlayerUUID)) return playerPokemon;
        return additionalPlayerPokemon.get(playerUUID);
    }
    void setPlayerPokemon(UUID playerUUID, Pokemon pokemon) {
        if (playerUUID != null && playerUUID.equals(initialPlayerUUID)) playerPokemon = pokemon;
        else if (playerUUID != null && pokemon != null) additionalPlayerPokemon.put(playerUUID, pokemon);
    }
    java.util.Collection<Pokemon> allPlayerPokemon() {
        java.util.ArrayList<Pokemon> all = new java.util.ArrayList<>();
        if (playerPokemon != null) all.add(playerPokemon);
        all.addAll(additionalPlayerPokemon.values());
        return java.util.List.copyOf(all);
    }
}

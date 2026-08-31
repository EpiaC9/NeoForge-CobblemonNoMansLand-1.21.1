package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.gitlab.srcmc.rctapi.api.trainer.TrainerNPC;
import net.minecraft.server.level.ServerPlayer;

final class ActionBattlePokemonSelection {
    record Selection(int slot, Pokemon pokemon) {}

    private ActionBattlePokemonSelection() {}

    static Selection firstUsable(ServerPlayer player) {
        if (player == null) return null;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon != null && !pokemon.isFainted()) return new Selection(slot, pokemon);
        }
        return null;
    }

    static Selection nextUsable(ServerPlayer player, int currentIndex) {
        if (player == null) return null;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        boolean[] usable = new boolean[party.size()];
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            usable[slot] = pokemon != null && !pokemon.isFainted();
        }
        int nextIndex = ActionPartyCycle.nextUsableIndex(currentIndex, usable);
        if (nextIndex < 0) return null;
        Pokemon pokemon = party.get(nextIndex);
        return pokemon != null ? new Selection(nextIndex, pokemon) : null;
    }

    static Selection firstUsable(TrainerNPC trainer) {
        if (trainer == null || trainer.getTeam() == null) return null;
        Pokemon[] team = trainer.getTeam();
        for (int slot = 0; slot < team.length; slot++) {
            Pokemon pokemon = team[slot];
            if (pokemon != null && !pokemon.isFainted()) return new Selection(slot, pokemon);
        }
        return null;
    }

    static Selection nextUsable(TrainerNPC trainer, int currentIndex) {
        if (trainer == null || trainer.getTeam() == null) return null;
        Pokemon[] team = trainer.getTeam();
        boolean[] usable = new boolean[team.length];
        for (int slot = 0; slot < team.length; slot++) {
            Pokemon pokemon = team[slot];
            usable[slot] = pokemon != null && !pokemon.isFainted();
        }
        int nextIndex = ActionPartyCycle.nextUsableIndex(currentIndex, usable);
        if (nextIndex < 0) return null;
        Pokemon pokemon = team[nextIndex];
        return pokemon != null ? new Selection(nextIndex, pokemon) : null;
    }
}

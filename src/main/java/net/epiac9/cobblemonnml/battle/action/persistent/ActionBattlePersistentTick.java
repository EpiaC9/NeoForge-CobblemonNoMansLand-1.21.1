package net.epiac9.cobblemonnml.battle.action.persistent;

import java.util.UUID;

public record ActionBattlePersistentTick(UUID targetPokemonUUID, ActionBattlePersistentEvent event) {}

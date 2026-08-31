package net.epiac9.cobblemonnml.battle.action.effect;

import java.util.UUID;

public record ActionBattleDotEvent(UUID pokemonUUID, ActionBattleStatus status, double maxHealthFraction, boolean canKo) {}

package net.epiac9.cobblemonnml.battle.action;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ActionBattlePartyProjection(List<Entry> entries) {
    public static final int MAX_SLOTS = 6;

    public ActionBattlePartyProjection {
        entries = entries == null ? List.of() : List.copyOf(entries.subList(0, Math.min(MAX_SLOTS, entries.size())));
    }

    public static ActionBattlePartyProjection from(List<Input> inputs) {
        if (inputs == null || inputs.isEmpty()) return new ActionBattlePartyProjection(List.of());
        List<Entry> entries = new ArrayList<>(Math.min(MAX_SLOTS, inputs.size()));
        for (Input input : inputs) {
            if (input == null || input.partySlot() < 0 || input.partySlot() >= MAX_SLOTS
                    || input.pokemonUUID() == null || entries.size() >= MAX_SLOTS) continue;
            entries.add(new Entry(input.partySlot(), input.pokemonUUID(), input.name(), input.currentHp(), input.maxHp(),
                    input.currentHp() <= 0, input.effects()));
        }
        return new ActionBattlePartyProjection(entries);
    }

    public record Input(int partySlot, UUID pokemonUUID, String name, int currentHp, int maxHp,
                        List<Effect> effects) {
        public Input {
            name = name == null ? "" : name;
            currentHp = Math.max(0, currentHp);
            maxHp = Math.max(1, maxHp);
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }

    public record Entry(int partySlot, UUID pokemonUUID, String name, int currentHp, int maxHp, boolean fainted,
                        List<Effect> effects) {
        public Entry {
            name = name == null ? "" : name;
            currentHp = Math.max(0, currentHp);
            maxHp = Math.max(1, maxHp);
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }

    public record Effect(String effectId, long remainingTicks, long totalTicks) {
        public Effect {
            effectId = effectId == null ? "" : effectId;
            remainingTicks = Math.max(0L, remainingTicks);
            totalTicks = Math.max(0L, totalTicks);
        }
    }
}

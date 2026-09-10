package net.epiac9.cobblemonnml.battle.action.move;

import java.util.Set;

public enum ActionBattleMoveFlag {
    CONTACT(Set.of("contact")),
    SOUND(Set.of("sound", "soundbased")),
    POWDER(Set.of("powder", "powderbased")),
    PUNCH(Set.of("punch", "punching")),
    BITE(Set.of("bite", "biting")),
    SLICING(Set.of("slicing", "slice", "slicingmove")),
    WIND(Set.of("wind", "windmove")),
    BALL_BOMB(Set.of("ballbomb", "ball", "bomb", "bullet", "bombmove"));

    private final Set<String> aliases;

    ActionBattleMoveFlag(Set<String> aliases) {
        this.aliases = aliases;
    }

    public boolean matches(String normalizedFlag) {
        return normalizedFlag != null && aliases.contains(normalizedFlag);
    }
}

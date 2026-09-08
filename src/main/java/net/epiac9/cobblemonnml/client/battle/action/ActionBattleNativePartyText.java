package net.epiac9.cobblemonnml.client.battle.action;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class ActionBattleNativePartyText {
    private ActionBattleNativePartyText() {}

    public static <T> T present(T text, int stage, UnaryOperator<T> alternateFont,
                                Supplier<T> hiddenText) {
        if (ActionBattleObscurityHudRules.hideInformation(stage)) return hiddenText.get();
        if (ActionBattleObscurityHudRules.usesMinecraftAltFont(stage)) {
            return alternateFont.apply(text);
        }
        return text;
    }
}

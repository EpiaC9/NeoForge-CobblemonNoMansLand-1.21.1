package net.epiac9.cobblemonnml.battle.action.typeeffect.psychic;

import net.epiac9.cobblemonnml.battle.action.channel.ActionBattleChannelPreset;

public final class ActionBattlePsychicDeliveryRules {
    private ActionBattlePsychicDeliveryRules() {}

    public static Path path(boolean psychicIdentity, boolean targeted, boolean selfOrAlly) {
        if (!psychicIdentity) return Path.STANDARD;
        if (targeted) return Path.TARGETED_DIRECT_CHANNEL;
        if (selfOrAlly) return Path.SELF_MOBILE_CHANNEL;
        return Path.STANDARD;
    }

    public static ActionBattleChannelPreset presetFor(Path path, int durationTicks) {
        boolean self = path == Path.SELF_MOBILE_CHANNEL;
        return new ActionBattleChannelPreset(durationTicks, !self,
                true, true, !self, !self);
    }

    public enum Path { STANDARD, TARGETED_DIRECT_CHANNEL, SELF_MOBILE_CHANNEL }
}

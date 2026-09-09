package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleFlyingHudRules {
    public static final int MAX_MOMENTUM = 6;
    public static final int NORMAL_COLOR = 0xFFFFFFFF;
    public static final int MAX_COLOR = 0xFFFF4444;

    private ActionBattleFlyingHudRules() {}

    public static String label(String type, int momentum, int obscurityStage) {
        if (!isFlying(type) || ActionBattleObscurityHudRules.hideInformation(obscurityStage)) return "";
        return Math.max(0, Math.min(MAX_MOMENTUM, momentum)) + "/" + MAX_MOMENTUM;
    }

    public static int color(String type, int momentum, int obscurityStage) {
        int base = isFlying(type) && momentum >= MAX_MOMENTUM && obscurityStage <= 0
                ? MAX_COLOR : NORMAL_COLOR;
        return ActionBattleObscurityHudRules.presentationColor(base, obscurityStage);
    }

    public static boolean showLockMarker(String type, int momentum, int obscurityStage) {
        return isFlying(type) && momentum >= MAX_MOMENTUM && obscurityStage <= 0
                && ActionBattleObscurityHudRules.showsAuxiliaryIcon(obscurityStage);
    }

    private static boolean isFlying(String type) {
        return type != null && "flying".equalsIgnoreCase(type.trim());
    }
}

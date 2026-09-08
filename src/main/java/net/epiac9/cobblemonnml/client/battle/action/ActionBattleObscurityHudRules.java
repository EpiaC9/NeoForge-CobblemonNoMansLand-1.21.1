package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleObscurityHudRules {

    private ActionBattleObscurityHudRules() {}

    public static int clampStage(int stage) { return Math.max(0, Math.min(4, stage)); }
    public static boolean grayscale(int stage) { return clampStage(stage) >= 1; }
    public static boolean glyphText(int stage) { return clampStage(stage) >= 2; }
    public static boolean hideInformation(int stage) { return clampStage(stage) >= 3; }
    public static boolean smokeCovered(int stage) { return clampStage(stage) >= 4; }
    public static boolean controlsRemainEnabled(int stage) { return true; }
    public static boolean usesMinecraftAltFont(int stage) { return glyphText(stage); }
    public static boolean drawsSurfaceShade(int stage) { return false; }
    public static boolean drawsSmoke(int stage) { return smokeCovered(stage); }
    public static boolean showsFloatingDamage(int stage) { return !hideInformation(stage); }
    public static boolean showsAuxiliaryIcon(int stage) { return !hideInformation(stage); }
    public static int presentationColor(int color, int stage) {
        if (!grayscale(stage)) return color;
        int alpha = color & 0xFF000000;
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        int gray = Math.clamp(Math.round(red * 0.30F + green * 0.59F + blue * 0.11F), 72, 190);
        return alpha | gray << 16 | gray << 8 | gray;
    }
}

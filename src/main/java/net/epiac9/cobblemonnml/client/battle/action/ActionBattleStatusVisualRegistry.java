package net.epiac9.cobblemonnml.client.battle.action;

import net.minecraft.resources.ResourceLocation;

public final class ActionBattleStatusVisualRegistry {
    private static final StatusVisual CINDERS = new StatusVisual(ResourceLocation.fromNamespaceAndPath("cobblemonnml", "textures/gui/action/status/cinders.png"), 0xFFE08A36);
    private static final StatusVisual BURN = new StatusVisual(ResourceLocation.fromNamespaceAndPath("cobblemonnml", "textures/gui/action/status/burn.png"), 0xFFFF5A24);
    private static final StatusVisual FREEZE = new StatusVisual(ResourceLocation.fromNamespaceAndPath("cobblemonnml", "textures/gui/action/status/freeze.png"), 0xFF8FE8F7);
    private static final StatusVisual FROSTBITE = new StatusVisual(ResourceLocation.fromNamespaceAndPath("cobblemonnml", "textures/gui/action/status/frostbite.png"), 0xFF5FA8FF);

    private ActionBattleStatusVisualRegistry() {}

    public static StatusVisual visualFor(String statusId) {
        if (statusId == null) return null;
        return switch (statusId) {
            case "CINDERS" -> CINDERS;
            case "BURN" -> BURN;
            case "FREEZE" -> FREEZE;
            case "FROSTBITE" -> FROSTBITE;
            default -> null;
        };
    }

    public record StatusVisual(ResourceLocation icon, int ringArgb) {}
}

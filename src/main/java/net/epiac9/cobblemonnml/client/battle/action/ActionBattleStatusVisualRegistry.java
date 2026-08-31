package net.epiac9.cobblemonnml.client.battle.action;

import net.minecraft.resources.ResourceLocation;

public final class ActionBattleStatusVisualRegistry {
    private static final StatusVisual CINDERS = new StatusVisual(ResourceLocation.fromNamespaceAndPath("cobblemonnml", "textures/gui/action/status/cinders.png"), 0xFFE08A36);
    private static final StatusVisual BURN = new StatusVisual(ResourceLocation.fromNamespaceAndPath("cobblemonnml", "textures/gui/action/status/burn.png"), 0xFFFF5A24);

    private ActionBattleStatusVisualRegistry() {}

    public static StatusVisual visualFor(String statusId) {
        if (statusId == null) return null;
        return switch (statusId) {
            case "CINDERS" -> CINDERS;
            case "BURN" -> BURN;
            default -> null;
        };
    }

    public record StatusVisual(ResourceLocation icon, int ringArgb) {}
}

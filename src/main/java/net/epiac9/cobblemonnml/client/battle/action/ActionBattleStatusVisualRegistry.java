package net.epiac9.cobblemonnml.client.battle.action;

import net.minecraft.resources.ResourceLocation;

public final class ActionBattleStatusVisualRegistry {
    private static final StatusVisual CINDERS = visual("cinders", 0xFFE08A36);
    private static final StatusVisual BURN = visual("burn", 0xFFFF5A24);
    private static final StatusVisual FREEZE = visual("freeze", 0xFF8FE8F7);
    private static final StatusVisual FROSTBITE = visual("frostbite", 0xFF5FA8FF);
    private static final StatusVisual POISON = visual("poison", 0xFF6BBE45);
    private static final StatusVisual TOXIC_1 = visual("toxic_1", 0xFF7B9B54);
    private static final StatusVisual TOXIC_2 = visual("toxic_2", 0xFF8E5DB2);
    private static final StatusVisual TOXIC_3 = visual("toxic_3", 0xFFA04AC0);
    private static final StatusVisual[] DETERIORATING_SHIELD = {
            visual("deteriorating_shield_1", 0xFFD8E7EC), visual("deteriorating_shield_2", 0xFFD0DFE5), visual("deteriorating_shield_3", 0xFFC5D5DC),
            visual("deteriorating_shield_4", 0xFFBACAD2), visual("deteriorating_shield_5", 0xFFADBFC8), visual("deteriorating_shield_6", 0xFFA0B4BE),
            visual("deteriorating_shield_7", 0xFF91A6B1), visual("deteriorating_shield_8", 0xFF8197A3), visual("deteriorating_shield_9", 0xFF708792)
    };

    private ActionBattleStatusVisualRegistry() {}

    public static StatusVisual visualFor(String statusId) {
        if (statusId == null) return null;
        return switch (statusId) {
            case "DETERIORATING_SHIELD_1" -> DETERIORATING_SHIELD[0];
            case "DETERIORATING_SHIELD_2" -> DETERIORATING_SHIELD[1];
            case "DETERIORATING_SHIELD_3" -> DETERIORATING_SHIELD[2];
            case "DETERIORATING_SHIELD_4" -> DETERIORATING_SHIELD[3];
            case "DETERIORATING_SHIELD_5" -> DETERIORATING_SHIELD[4];
            case "DETERIORATING_SHIELD_6" -> DETERIORATING_SHIELD[5];
            case "DETERIORATING_SHIELD_7" -> DETERIORATING_SHIELD[6];
            case "DETERIORATING_SHIELD_8" -> DETERIORATING_SHIELD[7];
            case "DETERIORATING_SHIELD_9" -> DETERIORATING_SHIELD[8];
            case "CINDERS" -> CINDERS;
            case "BURN" -> BURN;
            case "FREEZE" -> FREEZE;
            case "FROSTBITE" -> FROSTBITE;
            case "POISON" -> POISON;
            case "TOXIC_1" -> TOXIC_1;
            case "TOXIC_2" -> TOXIC_2;
            case "TOXIC_3" -> TOXIC_3;
            default -> null;
        };
    }

    private static StatusVisual visual(String name, int ringArgb) {
        return new StatusVisual(ResourceLocation.fromNamespaceAndPath("cobblemonnml", "textures/gui/action/status/" + name + ".png"), ringArgb);
    }

    public record StatusVisual(ResourceLocation icon, int ringArgb) {}
}

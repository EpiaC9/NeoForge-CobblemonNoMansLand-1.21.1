package net.epiac9.cobblemonnml.client.battle.action;

import net.minecraft.resources.ResourceLocation;

public final class ActionBattleStatusVisualRegistry {
    private static final StatusVisual SLEEP = visual("sleep", 0xFF7E73C7);
    private static final StatusVisual CONFUSION = visual("confusion", 0xFFC56BFF);
    private static final StatusVisual EVASION = visual("evasion", 0xFF8FD7E8);
    private static final StatusVisual CONTROL_TAUNT = visual("control_taunt", 0xFFE56A54);
    private static final StatusVisual CONTROL_DISABLE = visual("control_disable", 0xFFB66DE8);
    private static final StatusVisual CONTROL_ENCORE = visual("control_encore", 0xFFFFB84D);
    private static final StatusVisual CONTROL_HEAL_BLOCK = visual("control_heal_block", 0xFFD95B7A);
    private static final StatusVisual CONTROL_TORMENT = visual("control_torment", 0xFF8B5AD6);
    private static final StatusVisual CONTROL_IMPRISON = visual("control_imprison", 0xFF6B79C8);
    private static final StatusVisual CONTROL_TRAPPED = visual("control_trapped", 0xFF8C8C8C);
    private static final StatusVisual PERSISTENT_PERISH_SONG = visual("persistent_perish_song", 0xFFE9E9E9);
    private static final StatusVisual PERSISTENT_BOUND = visual("persistent_bound", 0xFFB58B5A);
    private static final StatusVisual PERSISTENT_NIGHTMARE = visual("persistent_nightmare", 0xFF5C3E86);
    private static final StatusVisual TYPE_FIRE_BUILDUP = typeEffectVisual("fire_build_up", 0xFFFFA24A);
    private static final StatusVisual TYPE_FIRE_CINDERS = typeEffectVisual("cinders", 0xFFB97857);
    private static final StatusVisual TYPE_FIRE_BURN = typeEffectVisual("burn", 0xFFFF5A32);
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
            case "SLEEP" -> SLEEP;
            case "CONFUSION" -> CONFUSION;
            case "EVASION" -> EVASION;
            case "CONTROL_TAUNT" -> CONTROL_TAUNT;
            case "CONTROL_DISABLE" -> CONTROL_DISABLE;
            case "CONTROL_ENCORE" -> CONTROL_ENCORE;
            case "CONTROL_HEAL_BLOCK" -> CONTROL_HEAL_BLOCK;
            case "CONTROL_TORMENT" -> CONTROL_TORMENT;
            case "CONTROL_IMPRISON" -> CONTROL_IMPRISON;
            case "CONTROL_TRAPPED" -> CONTROL_TRAPPED;
            case "PERSISTENT_PERISH_SONG" -> PERSISTENT_PERISH_SONG;
            case "PERSISTENT_BOUND" -> PERSISTENT_BOUND;
            case "PERSISTENT_NIGHTMARE" -> PERSISTENT_NIGHTMARE;
            case "TYPE_FIRE_BUILDUP" -> TYPE_FIRE_BUILDUP;
            case "TYPE_FIRE_CINDERS" -> TYPE_FIRE_CINDERS;
            case "TYPE_FIRE_BURN" -> TYPE_FIRE_BURN;
            default -> null;
        };
    }

    private static StatusVisual visual(String name, int ringArgb) {
        return new StatusVisual(ResourceLocation.fromNamespaceAndPath("cobblemonnml", "textures/gui/action/status/" + name + ".png"), ringArgb);
    }

    private static StatusVisual typeEffectVisual(String name, int ringArgb) {
        return new StatusVisual(ResourceLocation.fromNamespaceAndPath("cobblemonnml", "textures/gui/action/type_effect/" + name + ".png"), ringArgb);
    }

    public record StatusVisual(ResourceLocation icon, int ringArgb) {}
}

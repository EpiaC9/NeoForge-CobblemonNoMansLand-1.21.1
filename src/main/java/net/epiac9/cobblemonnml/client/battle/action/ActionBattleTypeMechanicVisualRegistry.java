package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class ActionBattleTypeMechanicVisualRegistry {
    private static final String KEY_PREFIX = "action.cobblemonnml.type_mechanic.";

    private ActionBattleTypeMechanicVisualRegistry() {}

    public static MechanicVisual visualFor(String stateId) {
        String type = typeFrom(stateId);
        if (type == null) return null;
        String alternate = alternateIcon(stateId);
        return new MechanicVisual(type, typeIndex(type), alternate,
                key(type, "name"), key(type, "description"), key(type, "activation"),
                showsProgress(stateId), showsCounter(type), ringColor(type));
    }

    public static boolean isMechanic(String stateId) {
        return stateId != null && stateId.startsWith("MECHANIC_");
    }

    public static String typeNameKey(String type) {
        return key(type, "type");
    }

    private static String typeFrom(String stateId) {
        if (!isMechanic(stateId)) return null;
        String body = stateId.substring("MECHANIC_".length());
        int split = body.indexOf('_');
        String type = (split >= 0 ? body.substring(0, split) : body).toLowerCase(Locale.ROOT);
        return typeIndex(type) >= 0 ? type : null;
    }

    private static String alternateIcon(String stateId) {
        if (stateId == null) return null;
        String name = switch (stateId) {
            case "MECHANIC_DRAGON_SLEEP" -> "dragon_sleep";
            case "MECHANIC_BUG_HP" -> "bug_shedding";
            case "MECHANIC_BUG_ATTACK", "MECHANIC_BUG_SPECIAL_ATTACK" -> "bug_offense";
            case "MECHANIC_BUG_DEFENSE" -> "bug_carapace";
            case "MECHANIC_BUG_SPECIAL_DEFENSE" -> "bug_effect_guard";
            case "MECHANIC_BUG_SPEED" -> "bug_speed";
            case "MECHANIC_STEEL_MAGNET_RISE" -> "magnet_rise";
            case "MECHANIC_STEEL_WEIGHTED" -> "weighted";
            default -> null;
        };
        return name == null ? null : "textures/gui/action/type_effect/" + name + ".png";
    }

    private static boolean showsProgress(String stateId) {
        return stateId != null && (stateId.startsWith("MECHANIC_FIRE_")
                || stateId.startsWith("MECHANIC_DRAGON_")
                || stateId.startsWith("MECHANIC_STEEL_"));
    }

    private static boolean showsCounter(String type) {
        return "flying".equals(type);
    }

    private static int typeIndex(String type) {
        return switch (type == null ? "" : type.toLowerCase(Locale.ROOT)) {
            case "fire" -> 1;
            case "water" -> 2;
            case "grass" -> 3;
            case "electric" -> 4;
            case "ice" -> 5;
            case "fighting" -> 6;
            case "poison" -> 7;
            case "ground" -> 8;
            case "flying" -> 9;
            case "psychic" -> 10;
            case "bug" -> 11;
            case "rock" -> 12;
            case "ghost" -> 13;
            case "dragon" -> 14;
            case "dark" -> 15;
            case "steel" -> 16;
            case "fairy" -> 17;
            default -> 0;
        };
    }

    private static int ringColor(String type) {
        return 0xFF000000 | switch (type) {
            case "fire" -> 0xE66A39;
            case "water" -> 0x4F86E8;
            case "grass" -> 0x55A94F;
            case "electric" -> 0xE4C13A;
            case "ice" -> 0x58BFC8;
            case "fighting" -> 0xB4473D;
            case "poison" -> 0xA257A9;
            case "ground" -> 0xC99C55;
            case "flying" -> 0x8098DF;
            case "psychic" -> 0xE45C93;
            case "bug" -> 0x9CAD3A;
            case "rock" -> 0xB09A58;
            case "ghost" -> 0x6E5A9D;
            case "dragon" -> 0x6652C9;
            case "dark" -> 0x62554F;
            case "steel" -> 0x8795A5;
            case "fairy" -> 0xD889C3;
            default -> 0x8D8D8D;
        };
    }

    private static String key(String type, String suffix) {
        return KEY_PREFIX + type + "." + suffix;
    }

    public record MechanicVisual(String type, int typeIndex, String alternateIcon,
                                 String displayNameKey, String descriptionKey, String activationKey,
                                 boolean progressRing, boolean counter, int ringArgb) {
        public boolean hasAlternateIcon() {
            return alternateIcon != null;
        }

        public Component displayName() {
            return Component.translatable(displayNameKey);
        }

        public Component typeName() {
            return Component.translatable(typeNameKey(type));
        }

        public Component description() {
            return Component.translatable(descriptionKey);
        }

        public Component activationDescription() {
            return Component.translatable(activationKey);
        }

        public Component liveState(ActionBattleHudPayload.StatusState state) {
            if (state == null) return Component.empty();
            String id = state.statusId();
            if ("fire".equals(type)) {
                return Component.translatable(id.contains("INFERNO")
                                ? KEY_PREFIX + "live.inferno_ammo"
                                : KEY_PREFIX + "live.pressure",
                        state.remainingTicks(), state.totalTicks());
            }
            if ("flying".equals(type)) {
                return Component.translatable(KEY_PREFIX + "live.momentum",
                        state.remainingTicks(), state.totalTicks());
            }
            if ("bug".equals(type)) {
                String branch = id.equals("MECHANIC_BUG") ? "none"
                        : id.substring("MECHANIC_BUG_".length()).toLowerCase(Locale.ROOT);
                return Component.translatable(KEY_PREFIX + "live.branch",
                        Component.translatable(KEY_PREFIX + "bug.branch." + branch));
            }
            if ("dragon".equals(type)) {
                if (id.contains("SLEEP")) {
                    return Component.translatable(KEY_PREFIX + "live.dragon_sleep", seconds(state.remainingTicks()));
                }
                if (id.contains("BUILDUP")) {
                    return Component.translatable(KEY_PREFIX + "live.uproar_buildup",
                            state.remainingTicks(), state.totalTicks());
                }
                if (id.contains("ACTIVE") || id.contains("ROARING")) {
                    return Component.translatable(KEY_PREFIX + "live.uproar_remaining", seconds(state.remainingTicks()));
                }
            }
            if ("steel".equals(type)) {
                if (id.equals("MECHANIC_STEEL")) {
                    return Component.translatable(KEY_PREFIX + "live.steel_inactive");
                }
                String stateName = id.substring("MECHANIC_STEEL_".length()).toLowerCase(Locale.ROOT);
                return Component.translatable(KEY_PREFIX + "live.steel_state",
                        Component.translatable(KEY_PREFIX + "steel.state." + stateName), seconds(state.remainingTicks()));
            }
            if ("normal".equals(type) && id.contains("__")) {
                String adapted = id.substring(id.indexOf("__") + 2).toLowerCase(Locale.ROOT);
                return Component.translatable(KEY_PREFIX + "live.adapted",
                        Component.translatable(KEY_PREFIX + adapted + ".type"));
            }
            return Component.empty();
        }

        private static Component seconds(long ticks) {
            long seconds = (long) Math.ceil(Math.max(0L, ticks) / 20.0D);
            return Component.translatable(KEY_PREFIX + "live.seconds", seconds);
        }
    }
}

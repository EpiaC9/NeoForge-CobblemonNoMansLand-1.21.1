package net.epiac9.cobblemonnml.client.battle.action;

import com.mojang.blaze3d.systems.RenderSystem;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class ActionBattleHud {
    private static final ResourceLocation BATTLE_INFO = cobblemon("textures/gui/battle/battle_info_base.png");
    private static final ResourceLocation BATTLE_INFO_FLIPPED = cobblemon("textures/gui/battle/battle_info_base_flipped.png");
    private static final ResourceLocation BATTLE_MOVE = cobblemon("textures/gui/battle/battle_move.png");
    private static final ResourceLocation BATTLE_MOVE_OVERLAY = cobblemon("textures/gui/battle/battle_move_overlay.png");
    private static final ResourceLocation TYPES = cobblemon("textures/gui/types.png");
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int PP_LOW = 0xFFFFC85A;
    private static final int PP_EMPTY = 0xFFFF6666;
    private static final int DISABLED = 0x80606060;
    private static final int COOLDOWN = 0xB3808080;
    private static final int TYPE_ICON_SIZE = 36;
    private static final int TYPE_ATLAS_WIDTH = 648;
    private static final int TYPE_ATLAS_HEIGHT = 36;
    private static final String[] KEYS = {"Z", "X", "C", "B"};

    private ActionBattleHud() {}

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ActionBattleHudClientState.isVisible()) return;
        if (!minecraft.player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return;
        ActionBattleHudPayload state = ActionBattleHudClientState.get();
        Font font = minecraft.font;
        ActionBattleHudLayout layout = ActionBattleHudLayout.forScreen(graphics.guiWidth(), graphics.guiHeight());
        renderPokemonPanel(graphics, font, layout.enemyPanel(), false, state.trainerPokemonName(), state.trainerPokemonLevel(), state.trainerCurrentHp(), state.trainerMaxHp());
        renderPokemonPanel(graphics, font, layout.allyPanel(), true, state.playerPokemonName(), state.playerPokemonLevel(), state.playerCurrentHp(), state.playerMaxHp());
        renderCommand(graphics, font, layout.commandButton(0), "Swap", "G");
        renderCommand(graphics, font, layout.commandButton(1), "Move Here", "V");
        for (int slot = 0; slot < 4; slot++) renderMove(graphics, font, layout.moveButton(slot), slot, state.move(slot));
    }

    public static ActionBattleHudLayout layoutForScreen(int width, int height) { return ActionBattleHudLayout.forScreen(width, height); }

    private static void renderPokemonPanel(GuiGraphics graphics, Font font, ActionBattleHudLayout.Rect rect, boolean flipped, String rawName, int level, int hp, int maxHp) {
        int x = rect.x();
        int y = rect.y();
        graphics.blit(flipped ? BATTLE_INFO_FLIPPED : BATTLE_INFO, x, y, 0.0F, 0.0F, rect.width(), rect.height(), rect.width(), rect.height());
        int infoX = flipped ? x + 7 : x + 40;
        String name = displayName(rawName);
        drawScaled(graphics, font, name, infoX, y + 7, 0.75F, TEXT, false);
        String levelText = "Lv. " + Math.max(1, level);
        drawScaledRight(graphics, font, levelText, flipped ? x + 100 : x + 137, y + 7, 0.70F, TEXT);
        double ratio = maxHp > 0 ? Math.clamp((double) hp / maxHp, 0.0D, 1.0D) : 0.0D;
        int fullWidth = 97;
        int barWidth = (int) Math.round(fullWidth * ratio);
        int barX = flipped ? infoX - 2 + (fullWidth - barWidth) : infoX - 2;
        int barColor = hpColor(ratio);
        if (barWidth > 0) graphics.fill(barX, y + 22, barX + barWidth, y + 26, barColor);
        String hpText = Math.max(0, hp) + "/" + Math.max(1, maxHp);
        int centerX = flipped ? infoX + 49 : infoX + 48;
        drawScaledCentered(graphics, font, hpText, centerX, y + 22, 0.50F, TEXT);
    }

    private static void renderCommand(GuiGraphics graphics, Font font, ActionBattleHudLayout.Rect rect, String label, String key) {
        int x = rect.x();
        int y = rect.y();
        RenderSystem.setShaderColor(0.68F, 0.68F, 0.68F, 1.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(rect.width() / 92.0F, rect.height() / 24.0F, 1.0F);
        graphics.blit(BATTLE_MOVE, 0, 0, 0.0F, 0.0F, 92, 24, 92, 48);
        graphics.blit(BATTLE_MOVE_OVERLAY, 0, 0, 0.0F, 0.0F, 92, 24, 92, 24);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (label.contains(" ")) {
            String[] parts = label.split(" ", 2);
            drawScaledCentered(graphics, font, parts[0], x + rect.width() / 2, y + 2, 0.38F, TEXT);
            drawScaledCentered(graphics, font, parts[1], x + rect.width() / 2, y + 7, 0.38F, TEXT);
            drawScaledCentered(graphics, font, key, x + rect.width() / 2, y + 14, 0.48F, TEXT);
        } else {
            drawScaledCentered(graphics, font, label, x + rect.width() / 2, y + 4, 0.42F, TEXT);
            drawScaledCentered(graphics, font, key, x + rect.width() / 2, y + 13, 0.50F, TEXT);
        }
    }

    private static void renderMove(GuiGraphics graphics, Font font, ActionBattleHudLayout.Rect rect, int slot, ActionBattleHudPayload.MoveState move) {
        int x = rect.x();
        int y = rect.y();
        boolean missing = move.name() == null || move.name().isBlank();
        boolean disabled = missing || !move.supported() || move.currentPp() <= 0;
        float[] tint = typeTint(move.type());
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], disabled ? 0.50F : 1.0F);
        graphics.blit(BATTLE_MOVE, x, y, 0.0F, 0.0F, rect.width(), rect.height(), rect.width(), rect.height() * 2);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(BATTLE_MOVE_OVERLAY, x, y, 0.0F, 0.0F, rect.width(), rect.height(), rect.width(), rect.height());
        String name = missing ? "---" : displayName(move.name());
        drawScaled(graphics, font, name, x + 17, y + 3, 0.72F, disabled ? MUTED : TEXT, false);
        int ppColor = move.currentPp() <= 0 ? PP_EMPTY : move.maxPp() > 0 && move.currentPp() * 2 <= move.maxPp() ? PP_LOW : TEXT;
        String pp = move.maxPp() > 0 ? move.currentPp() + "/" + move.maxPp() : "--/--";
        drawScaledCentered(graphics, font, pp, x + 75, y + 15, 0.58F, ppColor);
        drawScaledRight(graphics, font, KEYS[slot], x + 89, y + 3, 0.55F, disabled ? MUTED : TEXT);
        if (disabled) graphics.fill(x, y, x + rect.width(), y + rect.height(), DISABLED);
        if (move.cooldownRemainingTicks() > 0L && move.cooldownDurationTicks() > 0L) {
            double elapsedFraction = 1.0D - Math.clamp((double) move.cooldownRemainingTicks() / move.cooldownDurationTicks(), 0.0D, 1.0D);
            renderCooldownFill(graphics, rect, elapsedFraction);
        }
        renderTypeIcon(graphics, x - 9, y + 2, move.type(), disabled ? 0.55F : 1.0F);
    }

    private static void renderTypeIcon(GuiGraphics graphics, int x, int y, String type, float alpha) {
        int index = typeIndex(type);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.blit(TYPES, 0, 0, (float) (index * TYPE_ICON_SIZE), 0.0F, TYPE_ICON_SIZE, TYPE_ICON_SIZE, TYPE_ATLAS_WIDTH, TYPE_ATLAS_HEIGHT);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderCooldownFill(GuiGraphics graphics, ActionBattleHudLayout.Rect rect, double fraction) {
        int x = rect.x();
        int y = rect.y();
        int width = rect.width();
        int height = rect.height();
        int filledHeight = (int) Math.ceil(height * fraction);
        if (filledHeight <= 0) return;
        int sourceY = height - filledHeight;
        float alpha = ((COOLDOWN >>> 24) & 255) / 255.0F;
        float gray = 128.0F / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(gray, gray, gray, alpha);
        graphics.blit(BATTLE_MOVE, x, y + sourceY, 0.0F, (float) sourceY, width, filledHeight, width, height * 2);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static int hpColor(double ratio) {
        if (ratio <= 0.20D) return 0xFFD9534F;
        if (ratio <= 0.50D) return 0xFFE4C34A;
        return 0xFF62C45B;
    }

    private static float[] typeTint(String type) {
        int rgb = switch (normalize(type)) {
            case "fire" -> 0xE66A39; case "water" -> 0x4F86E8; case "grass" -> 0x55A94F; case "electric" -> 0xE4C13A;
            case "ice" -> 0x58BFC8; case "fighting" -> 0xB4473D; case "poison" -> 0xA257A9; case "ground" -> 0xC99C55;
            case "flying" -> 0x8098DF; case "psychic" -> 0xE45C93; case "bug" -> 0x9CAD3A; case "rock" -> 0xB09A58;
            case "ghost" -> 0x6E5A9D; case "dragon" -> 0x6652C9; case "dark" -> 0x62554F; case "steel" -> 0x8795A5;
            case "fairy" -> 0xD889C3; default -> 0x8D8D8D;
        };
        return new float[]{((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F};
    }

    private static int typeIndex(String type) {
        return switch (normalize(type)) {
            case "fire" -> 1; case "water" -> 2; case "grass" -> 3; case "electric" -> 4; case "ice" -> 5; case "fighting" -> 6;
            case "poison" -> 7; case "ground" -> 8; case "flying" -> 9; case "psychic" -> 10; case "bug" -> 11; case "rock" -> 12;
            case "ghost" -> 13; case "dragon" -> 14; case "dark" -> 15; case "steel" -> 16; case "fairy" -> 17; default -> 0;
        };
    }

    private static void drawScaled(GuiGraphics graphics, Font font, String text, int x, int y, float scale, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private static void drawScaledRight(GuiGraphics graphics, Font font, String text, int rightX, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(rightX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text), 0, color, true);
        graphics.pose().popPose();
    }

    private static void drawScaledCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, true);
        graphics.pose().popPose();
    }

    private static ResourceLocation cobblemon(String path) { return ResourceLocation.fromNamespaceAndPath("cobblemon", path); }
    private static String normalize(String value) { return value == null ? "normal" : value.trim().toLowerCase(); }
    private static String displayName(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String spaced = value.replace('_', ' ');
        StringBuilder out = new StringBuilder(spaced.length());
        boolean upper = true;
        for (char c : spaced.toCharArray()) {
            if (c == ' ') { upper = true; out.append(c); }
            else { out.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return out.toString();
    }
}

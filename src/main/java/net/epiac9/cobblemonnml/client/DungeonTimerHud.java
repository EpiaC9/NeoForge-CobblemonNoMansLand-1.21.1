package net.epiac9.cobblemonnml.client;

import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class DungeonTimerHud {
    private static final ResourceLocation EXPERIENCE_BAR_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation BAD_OMEN_TEXTURE = ResourceLocation.withDefaultNamespace("textures/mob_effect/bad_omen.png");
    private static final ResourceLocation TYPE_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath("cobblemon", "textures/gui/types.png");
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int SEGMENT_COUNT = 18;
    private static final int SEGMENT_GAP = 1;
    private static final int SHIMMER_WIDTH = 18;
    private static final double SHIMMER_CYCLE_SECONDS = 2.85D;
    private static final double WARNING_START_SECONDS = 60.0D;
    private static final double CRITICAL_START_SECONDS = 10.0D;
    private static final int TYPE_CURSOR_SIZE = 7;
    private static final int TIER_ICON_SIZE = 12;
    private static final int COBBLEMON_TYPE_ICON_SIZE = 36;
    private static final int COBBLEMON_TYPE_ATLAS_WIDTH = 648;
    private static final int COBBLEMON_TYPE_ATLAS_HEIGHT = 36;

    private DungeonTimerHud() {}

    public static boolean shouldReplaceExperienceHud() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !DungeonTimerClientState.isActive()) return false;
        if (minecraft.player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return true;
        DungeonTimerClientState.clear();
        return false;
    }

    public static void renderExperienceTimer(GuiGraphics graphics) {
        if (!shouldReplaceExperienceHud()) return;
        DungeonTheme theme = DungeonTimerClientState.getTheme();
        int barX = graphics.guiWidth() / 2 - 91;
        int barY = graphics.guiHeight() - 29;
        renderTimerBar(graphics, theme, barX, barY);
        renderTierIcon(graphics, DungeonTimerClientState.getTierIndex(), graphics.guiWidth() / 2, graphics.guiHeight() - 39);
    }

    private static void renderTimerBar(GuiGraphics graphics, DungeonTheme theme, int x, int y) {
        graphics.blitSprite(EXPERIENCE_BAR_BACKGROUND, x, y, BAR_WIDTH, BAR_HEIGHT);
        double remainingSeconds = DungeonTimerClientState.getEstimatedRemainingSeconds();
        double progress = DungeonTimerClientState.getProgress(remainingSeconds);
        int fillWidth = (int) Math.round(progress * BAR_WIDTH);
        if (fillWidth > 0) {
            renderSegmentedFill(graphics, x, y, fillWidth, theme.getPortalColor());
            renderShimmer(graphics, x, y, fillWidth, remainingSeconds);
        }
        int halfCursor = TYPE_CURSOR_SIZE / 2;
        int markerCenterX = x + (int) Math.round(progress * BAR_WIDTH);
        markerCenterX = Math.clamp(markerCenterX, x + halfCursor, x + BAR_WIDTH - halfCursor);
        renderTypeIcon(graphics, theme, markerCenterX, y + BAR_HEIGHT / 2);
    }

    private static void renderSegmentedFill(GuiGraphics graphics, int x, int y, int fillWidth, int color) {
        int fillEnd = x + Math.clamp(fillWidth, 0, BAR_WIDTH);
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            int segmentStart = x + (i * BAR_WIDTH) / SEGMENT_COUNT;
            int segmentEnd = x + ((i + 1) * BAR_WIDTH) / SEGMENT_COUNT;
            if (i < SEGMENT_COUNT - 1) segmentEnd -= SEGMENT_GAP;
            int visibleEnd = Math.min(segmentEnd, fillEnd);
            if (visibleEnd > segmentStart) graphics.fill(segmentStart, y + 1, visibleEnd, y + BAR_HEIGHT - 1, color);
            if (segmentStart >= fillEnd) break;
        }
    }

    private static void renderShimmer(GuiGraphics graphics, int x, int y, int fillWidth, double remainingSeconds) {
        if (fillWidth <= 0) return;
        double cycle = (System.nanoTime() / 1_000_000_000.0D) % SHIMMER_CYCLE_SECONDS;
        double normalized = cycle / SHIMMER_CYCLE_SECONDS;
        int shimmerLeft = x - SHIMMER_WIDTH + (int) Math.round(normalized * (fillWidth + SHIMMER_WIDTH));
        int shimmerRight = shimmerLeft + SHIMMER_WIDTH;
        double warning = warningPulse(remainingSeconds);
        int shimmerColor;
        if (remainingSeconds <= WARNING_START_SECONDS) {
            int alpha = Math.clamp((int) Math.round(30.0D + warning * 190.0D), 0, 235);
            shimmerColor = (alpha << 24) | 0x00FF3030;
        } else {
            shimmerColor = 0x58FFFFFF;
        }
        int fillEnd = x + fillWidth;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            int segmentStart = x + (i * BAR_WIDTH) / SEGMENT_COUNT;
            int segmentEnd = x + ((i + 1) * BAR_WIDTH) / SEGMENT_COUNT;
            if (i < SEGMENT_COUNT - 1) segmentEnd -= SEGMENT_GAP;
            int left = Math.max(segmentStart, shimmerLeft);
            int right = Math.min(Math.min(segmentEnd, fillEnd), shimmerRight);
            if (right > left) graphics.fill(left, y + 1, right, y + BAR_HEIGHT - 1, shimmerColor);
            if (segmentStart >= fillEnd) break;
        }
    }

    private static double warningPulse(double remainingSeconds) {
        if (remainingSeconds > WARNING_START_SECONDS) return 0.0D;
        if (remainingSeconds <= CRITICAL_START_SECONDS) {
            double fraction = remainingSeconds - Math.floor(remainingSeconds);
            double distanceToSecond = Math.min(fraction, 1.0D - fraction);
            return Math.clamp(1.0D - distanceToSecond * 6.0D, 0.0D, 1.0D);
        }
        double urgency = Math.clamp((WARNING_START_SECONDS - remainingSeconds) / (WARNING_START_SECONDS - CRITICAL_START_SECONDS), 0.0D, 1.0D);
        double periodSeconds = 2.0D - 1.35D * urgency;
        double timeSeconds = System.nanoTime() / 1_000_000_000.0D;
        double wave = (Math.sin((timeSeconds / periodSeconds) * Math.PI * 2.0D) + 1.0D) * 0.5D;
        return wave * (0.20D + 0.65D * urgency);
    }

    private static void renderTierIcon(GuiGraphics graphics, int tierIndex, int centerX, int centerY) {
        if (tierIndex <= 0) return;
        int iconX = centerX - TIER_ICON_SIZE / 2;
        int iconY = centerY - TIER_ICON_SIZE / 2;
        graphics.blit(BAD_OMEN_TEXTURE, iconX, iconY, 0.0F, 0.0F, TIER_ICON_SIZE, TIER_ICON_SIZE, TIER_ICON_SIZE, TIER_ICON_SIZE);
        String numeral = switch (tierIndex) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "";
        };
        if (numeral.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        float scale = 0.55F;
        int width = font.width(numeral);
        graphics.pose().pushPose();
        graphics.pose().translate(iconX + TIER_ICON_SIZE - 1, iconY + TIER_ICON_SIZE - 5, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, numeral, -width, 0, 0xFF000000, false);
        graphics.drawString(font, numeral, -width - 1, -1, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }

    private static void renderTypeIcon(GuiGraphics graphics, DungeonTheme theme, int centerX, int centerY) {
        int typeIndex = switch (theme.getId()) {
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
        int sourceX = typeIndex * COBBLEMON_TYPE_ICON_SIZE;
        int iconX = centerX - TYPE_CURSOR_SIZE / 2;
        int iconY = centerY - TYPE_CURSOR_SIZE / 2;
        float scale = TYPE_CURSOR_SIZE / (float) COBBLEMON_TYPE_ICON_SIZE;
        graphics.pose().pushPose();
        graphics.pose().translate(iconX, iconY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(TYPE_ICON_TEXTURE, 0, 0, (float) sourceX, 0.0F, COBBLEMON_TYPE_ICON_SIZE, COBBLEMON_TYPE_ICON_SIZE, COBBLEMON_TYPE_ATLAS_WIDTH, COBBLEMON_TYPE_ATLAS_HEIGHT);
        graphics.pose().popPose();
    }
}

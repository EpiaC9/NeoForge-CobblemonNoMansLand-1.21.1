package net.epiac9.cobblemonnml.client;

import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class DungeonTimerHud {
    // SIDEBAR TIMER BOX
    private static final int BOX_WIDTH = 66;
    private static final int BOX_HEIGHT = 46;
    private static final int BOX_RIGHT_MARGIN = 2;
    private static final int BOX_TOP = 105;
    private static final int BOX_BACKGROUND_COLOR = 0x40202020;
    private static final int BOX_BORDER_COLOR = 0x60202020;
    // TYPE ICON
    private static final int ICON_SIZE = 18;
    private static final int COBBLEMON_TYPE_ICON_SIZE = 36;
    private static final int COBBLEMON_TYPE_ATLAS_WIDTH = 648;
    private static final int COBBLEMON_TYPE_ATLAS_HEIGHT = 36;
    // DANGER COUNTDOWN
    private static final int DANGER_START_SECONDS = 60;
    // COLOURS
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int DANGER_RED = 0xFFFF3030;
    // RENDER
    public static void render( GuiGraphics graphics ) {
        Minecraft minecraft = Minecraft.getInstance();
        // CLIENT READY?
        if (minecraft.player == null) {
            return;
        }
        // HIDE WITH F1
        if (minecraft.options.hideGui) {
            return;
        }
        // TIMER ACTIVE?
        if (!DungeonTimerClientState .isVisible()) {
            return;
        }
        // ONLY SHOW IN DUNGEON
        if (!minecraft.player .level() .dimension() .equals( DungeonDimension .DUNGEON_DIMENSION )) {
            return;
        }
        // THEME
        DungeonTheme theme =
                DungeonTimerClientState
                        .getTheme();
        // TIER
        int tierIndex =
                DungeonTimerClientState
                        .getTierIndex();
        // TIME
        int totalSeconds =
                DungeonTimerClientState
                        .getDisplayedSeconds();
        int minutes =
                totalSeconds
                        / 60;
        int seconds =
                totalSeconds
                        % 60;
        String timerText = String.format( "%02d:%02d", minutes, seconds );
        // DANGER STATE
        double dangerUrgency = getDangerUrgency( totalSeconds );
        double dangerPulse = getDangerPulse( totalSeconds );
        // FONT
        Font font = minecraft.font;
        // SIDEBAR BOX POSITION
        int boxRight = graphics.guiWidth() - BOX_RIGHT_MARGIN;
        int boxLeft = boxRight - BOX_WIDTH;
        int boxTop = BOX_TOP;
        int boxBottom = boxTop + BOX_HEIGHT;
        int boxCenterX = (boxLeft + boxRight) / 2;
        // BOX SHADOW
        graphics.fill( boxLeft + 2, boxTop + 2, boxRight + 2, boxBottom + 2, 0x60000000 );
        // BOX BORDER
        graphics.fill( boxLeft, boxTop, boxRight, boxBottom, BOX_BORDER_COLOR );
        // BOX BACKGROUND
        graphics.fill( boxLeft + 1, boxTop + 1, boxRight - 1, boxBottom - 1, BOX_BACKGROUND_COLOR );
        // TOP ICON ROW
        int iconCenterY = boxTop + 14;
        if (theme != null) {
            int themeIconX =
                    tierIndex >= 2
                            ? boxCenterX - 10
                            : boxCenterX;
            renderSidebarTypeIcon( graphics, theme, themeIconX, iconCenterY );
        }
        if (tierIndex >= 2) {
            int difficultyIconX = boxCenterX + 10;
            renderDifficultyIcon( graphics, tierIndex, difficultyIconX, iconCenterY );
        }
        // TIMER COLOUR
        int timerColor = getTimerColor( totalSeconds, dangerUrgency, dangerPulse );
        // TIMER TEXT
        int timerY = boxTop + 30;
        graphics.drawCenteredString( font, timerText, boxCenterX, timerY, timerColor );
    }
    // DANGER URGENCY
    private static double getDangerUrgency( int secondsRemaining ) {
        if (secondsRemaining > DANGER_START_SECONDS) {
            return 0.0D;
        }
        if (secondsRemaining <= 0) {
            return 1.0D;
        }
        return 1.0D
                - ( (double) secondsRemaining / (double) DANGER_START_SECONDS );
    }
    // DANGER PULSE
    private static double getDangerPulse( int secondsRemaining ) {
        if (secondsRemaining > DANGER_START_SECONDS) {
            return 0.0D;
        }
        if (secondsRemaining <= 0) {
            return 1.0D;
        }
        double remaining = DungeonTimerClientState.getEstimatedRemainingSeconds();
        double fraction = remaining - Math.floor(remaining);

        /*
         * Strongest pulse immediately when the displayed second changes, then fades back down during that second.
         */
        return 1.0D - fraction;
    }
    // TIMER TEXT COLOUR
    private static int getTimerColor( int secondsRemaining, double urgency, double pulse ) {
        if (secondsRemaining > DANGER_START_SECONDS) {
            return TEXT_COLOR;
        }
        if (secondsRemaining <= 0) {
            return DANGER_RED;
        }
        double minimumStrength = 0.15D + urgency * 0.35D;
        double redAmount = getRedAmount(urgency, pulse, minimumStrength);
        return blendColor( redAmount );
    }

    private static double getRedAmount(double urgency, double pulse, double minimumStrength) {
        var pulseStrength = pulse * (0.30D + urgency * 0.70D);
        double redAmount = Math.max( minimumStrength, pulseStrength );
        redAmount = Math.clamp(redAmount, 0.0D, 1.0D);
        return redAmount;
    }
    // BLEND COLOURS
    private static int blendColor( double amount ) {
        amount = Math.clamp(amount, 0.0D, 1.0D);

        int fromA = (DungeonTimerHud.TEXT_COLOR >> 24) & 0xFF;
        int fromR = (DungeonTimerHud.TEXT_COLOR >> 16) & 0xFF;
        int fromG = (DungeonTimerHud.TEXT_COLOR >> 8) & 0xFF;
        int fromB = DungeonTimerHud.TEXT_COLOR & 0xFF;
        int toA = (DungeonTimerHud.DANGER_RED >> 24) & 0xFF;
        int toR = (DungeonTimerHud.DANGER_RED >> 16) & 0xFF;
        int toG = (DungeonTimerHud.DANGER_RED >> 8)   & 0xFF;
        int toB = DungeonTimerHud.DANGER_RED & 0xFF;
        int a = (int) Math.round(leap(fromA, toA, amount));
        int r = (int) Math.round(leap(fromR, toR, amount));
        int g = (int) Math.round(leap(fromG, toG, amount));
        int b = (int) Math.round(leap(fromB, toB, amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    // LERP
    private static double leap( double start, double end, double amount ) {
        return start + (end - start) * amount;
    }
    // DIFFICULTY ICON
    private static void renderDifficultyIcon( GuiGraphics graphics, int tierIndex, int centerX, int centerY ) {
        if (tierIndex < 2) {
            return;
        }
        ResourceLocation badOmenTexture =
                ResourceLocation.fromNamespaceAndPath( "minecraft", "textures/mob_effect/bad_omen.png" );
        int iconSize = 18;
        int iconX =
                centerX
                        - iconSize / 2;
        int iconY =
                centerY
                        - iconSize / 2;
        graphics.blit( badOmenTexture, iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize );
        // ROMAN NUMERAL
        String numeral =
                switch (tierIndex) {
                    case 2 -> "I";
                    case 3 -> "II";
                    case 4 -> "III";
                    default -> "";
                };
        if (numeral.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int numeralWidth = font.width( numeral );

        /*
         * Bottom-right corner of the Bad Omen icon.
         */
        int numeralX = iconX + iconSize - numeralWidth + 1;

        int numeralY = iconY + iconSize - font.lineHeight + 2;

        // Small dark backing/shadow for readability.
        graphics.drawString( font, numeral, numeralX + 1, numeralY + 1, 0xFF000000, false );
        graphics.drawString( font, numeral, numeralX, numeralY, 0xFFFFFFFF, false );
    }
    // SIDEBAR TYPE ICON
    private static void renderSidebarTypeIcon( GuiGraphics graphics, DungeonTheme theme, int centerX, int centerY ) {
        if (theme == null) {
            return;
        }
        int typeIndex =
                switch (theme.getId()) {
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
        int iconX = centerX - ICON_SIZE / 2;
        int iconY = centerY - ICON_SIZE / 2;
        int sourceX = typeIndex * COBBLEMON_TYPE_ICON_SIZE;

        ResourceLocation iconTexture = ResourceLocation.fromNamespaceAndPath( "cobblemon", "textures/gui/types.png" );
        float scale = ICON_SIZE / (float) COBBLEMON_TYPE_ICON_SIZE;
        graphics.pose().pushPose();
        graphics.pose().translate( iconX, iconY, 0.0F );
        graphics.pose().scale( scale, scale, 1.0F );
        graphics.blit(
                iconTexture,
                0,
                0,
                (float) sourceX,
                0.0F,
                COBBLEMON_TYPE_ICON_SIZE,
                COBBLEMON_TYPE_ICON_SIZE,
                COBBLEMON_TYPE_ATLAS_WIDTH,
                COBBLEMON_TYPE_ATLAS_HEIGHT
        );
        graphics.pose().popPose();
    }
}

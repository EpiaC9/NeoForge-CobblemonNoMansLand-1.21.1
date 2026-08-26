package net.epiac9.cobblemonnml.portal;

import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public final class DungeonPortalParticles {
    // PORTAL AREA
    /*
     * Full 3x3 portal surface.
     */
    private static final double PORTAL_RADIUS = 1.42D;
    // ANIMATE PORTAL
    public static void animate( BlockState state, Level level, BlockPos pos, RandomSource random ) {
        if (state == null || level == null || pos == null || random == null) {
            return;
        }
        // ONLY ANIMATE AFTER PORTAL IS READY
        /*
         * During dungeon generation the overworld core keeps its resolved tier/theme preview but ACTIVATED remains false.
         * Suppress every portal particle effect until generation completes and the portal is actually unlocked.
         */
        if (!state.getValue(DungeonPortalVisualState.ACTIVATED)) {
            return;
        }
        // ONLY CENTER CELL EMITS
        /*
         * 0 1 2
         * 3 4 5
         * 6 7 8
         * CELL 4 is the only particle emitter.
         * It then spreads particles across the complete
         * 3x3 portal.
         */
        if (state.getValue(DungeonPortalVisualState.CELL) != 4) {
            return;
        }
        // SELECTED TIER
        int tier = state.getValue( DungeonPortalVisualState.TIER );
        if (tier <= 0) {
            return;
        }
        // PARTICLE STYLE
        switch (tier) {
            case 1 ->
                    spawnTierOneDust( state, level, pos, random );
            case 2 ->
                    spawnTierTwoEnchant( level, pos, random );
            case 3 ->
                    spawnTierThreePortal( level, pos, random );
            case 4 ->
                    spawnTierFourOminous( level, pos, random );
            default -> {
            }
        }
    }
    // TIER 1
    // BRIGHT THEME-COLOURED DUST
    private static void spawnTierOneDust( BlockState state, Level level, BlockPos pos, RandomSource random ) {
        // THEME
        int themeIndex = state.getValue( DungeonPortalVisualState.THEME );
        DustParticleOptions dust = getDust(themeIndex);
        // PARTICLE COUNT
        int count =
                2
                        + random.nextInt( 2 );
        for (int i = 0; i < count; i++) {
            double particleX = randomPortalX( pos, random );
            double particleY = pos.getY() + 0.72D + random.nextDouble() * 0.18D;
            double particleZ = randomPortalZ( pos, random );
            double velocityX = (random.nextDouble() - 0.5D) * 0.018D;
            double velocityY = 0.045D + random.nextDouble() * 0.035D;
            double velocityZ = (random.nextDouble() - 0.5D)* 0.018D;
            level.addParticle( dust, particleX, particleY, particleZ, velocityX, velocityY, velocityZ );
        }
    }
    private static @NotNull DustParticleOptions getDust(int themeIndex) {
        DungeonTheme theme =
                DungeonPortalVisualState
                        .themeFromIndex( themeIndex );
        int rgb =
                theme != null
                        ? theme.getPortalColor()
                        & 0x00FFFFFF
                        : 0x00FFFFFF;
        float red =
                ((rgb >> 16) & 0xFF)
                        / 255.0F;
        float green =
                ((rgb >> 8) & 0xFF)
                        / 255.0F;
        float blue =
                (rgb & 0xFF)
                        / 255.0F;

        /*
         * Larger than before so Tier 1 is clearly visible.
         */
        return new DustParticleOptions( new Vector3f( red, green, blue ), 1.35F );
    }
    // TIER 2
    // ENCHANTMENT GLYPHS
    private static void spawnTierTwoEnchant( Level level, BlockPos pos, RandomSource random ) {

        /*
         * Several glyphs each tick so the player can immediately
         * tell that Tier 2 has been selected.
         */
        int count = 3 + random.nextInt(3);
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        for (int i = 0; i < count; i++) {
            // PICK EDGE
            int side = random.nextInt(4);
            double particleX;
            double particleZ;
            switch (side) {
                // WEST
                case 0 -> {
                    particleX =
                            centerX
                                    - PORTAL_RADIUS;
                    particleZ =
                            centerZ
                                    + randomRange( random );
                }
                // EAST
                case 1 -> {
                    particleX =
                            centerX
                                    + PORTAL_RADIUS;
                    particleZ =
                            centerZ
                                    + randomRange( random );
                }
                // NORTH
                case 2 -> {
                    particleX =
                            centerX
                                    + randomRange( random );
                    particleZ =
                            centerZ
                                    - PORTAL_RADIUS;
                }

                // SOUTH
                default -> {
                    particleX =
                            centerX
                                    + randomRange( random );
                    particleZ =
                            centerZ
                                    + PORTAL_RADIUS;
                }
            }
            // HEIGHT
            double particleY =
                    pos.getY()
                            + 0.78D
                            + random.nextDouble()
                            * 0.75D;
            // DRAW TOWARD CENTER
            double velocityX = (centerX - particleX) * 0.16D;
            double velocityY = 0.045D + random.nextDouble() * 0.035D;
            double velocityZ = (centerZ - particleZ) * 0.16D;
            level.addParticle(
                    ParticleTypes.ENCHANT,
                    particleX,
                    particleY,
                    particleZ,
                    velocityX,
                    velocityY,
                    velocityZ
            );
        }
    }
    // TIER 3
    // DENSE UNSTABLE PORTAL SWIRL
    private static void spawnTierThreePortal( Level level, BlockPos pos, RandomSource random ) {
        /*
         * Tier 3 should be immediately obvious.
         * A larger group of reverse portal particles continuously swirls across the complete 3x3 surface.
         */
        int count = 4 + random.nextInt(4);
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        for (int i = 0; i < count; i++) {
            double particleX = randomPortalX( pos, random );
            double particleY = pos.getY() + 0.70D + random.nextDouble() * 0.50D;
            double particleZ = randomPortalZ( pos, random );
            // OFFSET FROM PORTAL CENTER
            double offsetX = particleX - centerX;
            double offsetZ = particleZ - centerZ;
            // SWIRL
            double velocityX = -offsetZ * 0.060D + (random.nextDouble() - 0.5D) * 0.030D;
            double velocityY = 0.075D + random.nextDouble() * 0.055D;
            double velocityZ = offsetX * 0.060D + (random.nextDouble() - 0.5D) * 0.030D;
            level.addParticle(
                    ParticleTypes.REVERSE_PORTAL,
                    particleX,
                    particleY,
                    particleZ,
                    velocityX,
                    velocityY,
                    velocityZ
            );
        }
    }
    // TIER 4
    // STRONG OMINOUS EFFECT
    private static void spawnTierFourOminous( Level level, BlockPos pos, RandomSource random ) {
        /*
         * Tier 4 should feel substantially more threatening than all lower tiers.
         */
        int count = 3 + random.nextInt(3);
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        for (int i = 0; i < count; i++) {
            double particleX = randomPortalX( pos, random );

            /*
             * Some ominous particles hover significantly higher above the portal so the effect remains visible when
             * approaching from a distance.
             */
            double particleY = pos.getY() + 0.80D + random.nextDouble() * 1.20D;
            double particleZ = randomPortalZ( pos, random );
            // SLIGHT PULL TOWARD CENTER
            double velocityX = (centerX - particleX) * 0.020D + (random.nextDouble() - 0.5D) * 0.035D;
            double velocityY = 0.050D + random.nextDouble() * 0.050D;
            double velocityZ = (centerZ - particleZ) * 0.020D + (random.nextDouble() - 0.5D) * 0.035D;
            level.addParticle(
                    ParticleTypes.OMINOUS_SPAWNING,
                    particleX,
                    particleY,
                    particleZ,
                    velocityX,
                    velocityY,
                    velocityZ
            );
        }
    }
    // RANDOM X ACROSS PORTAL
    private static double randomPortalX( BlockPos center, RandomSource random ) {
        return center.getX() + 0.5D + randomRange(random);
    }
    // RANDOM Z ACROSS PORTAL
    private static double randomPortalZ( BlockPos center, RandomSource random ) {
        return center.getZ() + 0.5D + randomRange(random);
    }
    // RANDOM RANGE
    private static double randomRange(RandomSource random) {
        return -1.42 + random.nextDouble() * ( DungeonPortalParticles.PORTAL_RADIUS - 1.42 );
    }
}

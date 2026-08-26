package net.epiac9.cobblemonnml.portal;

import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class DungeonPortalVisualState {
    // ACTIVATOR PRESENT
    public static final BooleanProperty ACTIVATED = BooleanProperty.create( "activated" );
    // SELECTED TIER
    /*
     * 0 = none
     * 1 = tier 1
     * 2 = tier 2
     * 3 = tier 3
     * 4 = tier 4
     * Tier no longer selects a model overlay. It controls the
     * strength of the portal particle effect.
     */
    public static final IntegerProperty TIER = IntegerProperty.create( "tier", 0, 4 );
    // SELECTED THEME
    public static final IntegerProperty THEME = IntegerProperty.create( "theme", 0, DungeonTheme.values().length );
    // PORTAL CELL
    public static final IntegerProperty CELL = IntegerProperty.create( "cell", 0, 8 );
    // TIER -> STATE INDEX
    public static int tierIndex(DungeonTier tier) {
        if (tier == null) {
            return 0;
        }
        return tier.ordinal()
                + 1;
    }
    // THEME -> STATE INDEX
    public static int themeIndex(DungeonTheme theme) {
        if (theme == null) {
            return 0;
        }
        return theme.getVisualIndex();
    }
    // STATE INDEX -> THEME
    public static DungeonTheme themeFromIndex(int index) {
        return DungeonTheme.fromVisualIndex( index );
    }
    // CELL INDEX
    public static int cellIndex(int relativeX, int relativeZ) {
        return (relativeZ + 1) * 3
                + (relativeX + 1);
    }
}

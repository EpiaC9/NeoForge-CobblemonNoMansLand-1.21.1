package net.epiac9.cobblemonnml.dimension.theme;

import net.minecraft.util.RandomSource;

public enum DungeonTheme {
    BUG( "bug", "Bug", 0xFFB9E63A ),
    DARK( "dark", "Dark", 0xFF9A7B72 ),
    DRAGON( "dragon", "Dragon", 0xFF9A68FF ),
    ELECTRIC( "electric", "Electric", 0xFFFFE45A ),
    FAIRY( "fairy", "Fairy", 0xFFFFB8D8 ),
    FIGHTING( "fighting", "Fighting", 0xFFF05A50 ),
    FIRE( "fire", "Fire", 0xFFFF8A42 ),
    FLYING( "flying", "Flying", 0xFFB8A8FF ),
    GHOST( "ghost", "Ghost", 0xFF9B7DDB ),
    GRASS( "grass", "Grass", 0xFF7EE35C ),
    GROUND( "ground", "Ground", 0xFFF1D27A ),
    ICE( "ice", "Ice", 0xFFB7F4FF ),
    NORMAL( "normal", "Normal", 0xFFD4D4AF ),
    POISON( "poison", "Poison", 0xFFD968D9 ),
    PSYCHIC( "psychic", "Psychic", 0xFFFF74AA ),
    ROCK( "rock", "Rock", 0xFFD9BF59 ),
    STEEL( "steel", "Steel", 0xFFDADAF2 ),
    WATER( "water", "Water", 0xFF66B5FF );
    // THEME DATA
    private final String id;
    private final String displayName;
    private final int portalColor;
    DungeonTheme( String id, String displayName, int portalColor ) {
        this.id = id;
        this.displayName = displayName;
        this.portalColor = portalColor;
    }
    // ID
    public String getId() {
        return id;
    }
    // DISPLAY NAME
    public String getDisplayName() {
        return displayName;
    }
    // PORTAL COLOUR
    public int getPortalColor() {
        return portalColor;
    }
    // VISUAL INDEX
    public int getVisualIndex() {
        return ordinal()
                + 1;
    }
    // FROM VISUAL INDEX
    public static DungeonTheme fromVisualIndex( int index ) {
        if (index <= 0) {
            return null;
        }
        DungeonTheme[] themes = values();
        int arrayIndex = index - 1;
        if (arrayIndex >= themes.length) {
            return null;
        }
        return themes[
                arrayIndex
                ];
    }
    // START TEMPLATE POOL
    public String getStartPool() {
        return "dungeon/"
                + id
                + "/start";
    }
    // SPECIAL CORRIDOR TEMPLATE POOL
    public String getSpecialCorridorPool() {
        return "dungeon/"
                + id
                + "/special/corridor";
    }
    // RANDOM THEME
    public static DungeonTheme getRandom(RandomSource random) {
        DungeonTheme[] themes = values();
        return themes[
                random.nextInt(themes.length)
                ];
    }
}

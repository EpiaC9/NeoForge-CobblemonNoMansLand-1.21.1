package net.epiac9.cobblemonnml.dimension.theme;

import net.minecraft.util.RandomSource;

public enum DungeonTheme {
    BUG( "bug", "Bug", 0xFFA8B820 ),
    DARK( "dark", "Dark", 0xFF705848 ),
    DRAGON( "dragon", "Dragon", 0xFF7038F8 ),
    ELECTRIC( "electric", "Electric", 0xFFF8D030 ),
    FAIRY( "fairy", "Fairy", 0xFFEE99AC ),
    FIGHTING( "fighting", "Fighting", 0xFFC03028 ),
    FIRE( "fire", "Fire", 0xFFF08030 ),
    FLYING( "flying", "Flying", 0xFFA890F0 ),
    GHOST( "ghost", "Ghost", 0xFF705898 ),
    GRASS( "grass", "Grass", 0xFF78C850 ),
    GROUND( "ground", "Ground", 0xFFE0C068 ),
    ICE( "ice", "Ice", 0xFF98D8D8 ),
    NORMAL( "normal", "Normal", 0xFFA8A878 ),
    POISON( "poison", "Poison", 0xFFA040A0 ),
    PSYCHIC( "psychic", "Psychic", 0xFFF85888 ),
    ROCK( "rock", "Rock", 0xFFB8A038 ),
    STEEL( "steel", "Steel", 0xFFB8B8D0 ),
    WATER( "water", "Water", 0xFF6890F0 );
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
package net.epiac9.cobblemonnml;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec SPEC;
    // OVERWORLD PORTAL DISTANCE
    public static final ModConfigSpec.IntValue OVERWORLD_PORTAL_MIN_DISTANCE;
    public static final ModConfigSpec.IntValue OVERWORLD_PORTAL_MAX_DISTANCE;
    // DEBUG
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;
    // DUNGEON TIERS
    public static final TierConfig TIER_1;
    public static final TierConfig TIER_2;
    public static final TierConfig TIER_3;
    public static final TierConfig TIER_4;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        // DEBUG LOGGING
        DEBUG_LOGGING =
                builder.comment(
                        "Enables detailed CobblemonNML diagnostic logging.",
                        "",
                        "false = only important warnings, errors, and fail-safes are logged",
                        "true = additional dungeon generation, raid, trainer, portal, and cleanup details are logged",
                        "",
                        "Default: false"
                ).define(
                        "debugLogging",
                        false
                );
        // OVERWORLD PORTAL
        builder.push( "overworld_portal" );

        OVERWORLD_PORTAL_MIN_DISTANCE =
                builder.comment(
                        "Minimum distance in blocks from world spawn",
                        "for the one-time Overworld dungeon portal.",
                        "",
                        "Default: 300"
                ).defineInRange(
                        "minimumDistance",
                        300,
                        256,
                        50000
                );

        OVERWORLD_PORTAL_MAX_DISTANCE =
                builder.comment(
                        "Maximum distance in blocks from world spawn",
                        "for the one-time Overworld dungeon portal.",
                        "",
                        "Default: 500"
                ).defineInRange(
                        "maximumDistance",
                        500,
                        256,
                        100000
                );

        builder.pop();
        // TIER 1
        TIER_1 = createTier(
                builder,
                "tier1",

                // Timer in seconds
                15 * 60,

                // Room generation weights
                // Vault + Spawner
                // Trainer
                // Alpha
                // Raid

                // Spawner type weights
                80, // Normal Trial Spawner
                18, // Elite Spawner
                2,  // Boss Spawner

                // Vault type weights
                90, // Normal Vault
                10, // Ominous Vault

                // Special room settings
                // 10% chance per attempt
                1,   // 1 attempt

                // Dungeon Pokemon levels
                40, // Minimum level
                70, // Maximum level

                // Battle life transfer
                false, // Disabled
                4.0,   // Damage per fainted Pokemon
                5      // Revive health percentage
        );
        // TIER 2
        TIER_2 = createTier(
                builder,
                "tier2",

                // Timer in seconds
                10 * 60,

                // Room generation weights
                // Vault + Spawner
                // Trainer
                // Alpha
                // Raid

                // Spawner type weights
                65, // Normal Trial Spawner
                30, // Elite Spawner
                5,  // Boss Spawner

                // Vault type weights
                80, // Normal Vault
                20, // Ominous Vault

                // Special room settings
                // 10% chance per attempt
                2,   // 2 attempts

                // Dungeon Pokemon levels
                70, // Minimum level
                100, // Maximum level

                // Battle life transfer
                false, // Disabled
                5.0,   // Damage per fainted Pokemon
                5      // Revive health percentage
        );
        // TIER 3
        TIER_3 = createTier(
                builder,
                "tier3",

                // Timer in seconds
                8 * 60,

                // Room generation weights
                // Vault + Spawner
                // Trainer
                // Alpha
                // Raid

                // Spawner type weights
                45, // Normal Trial Spawner
                40, // Elite Spawner
                15, // Boss Spawner

                // Vault type weights
                65, // Normal Vault
                35, // Ominous Vault

                // Special room settings
                // 10% chance per attempt
                3,   // 3 attempts

                // Dungeon Pokemon levels
                100, // Minimum level
                150, // Maximum level

                // Battle life transfer
                true, // Enabled
                6.0,  // Damage per fainted Pokemon
                5     // Revive health percentage
        );
        // TIER 4
        TIER_4 = createTier(
                builder,
                "tier4",

                // Timer in seconds
                6 * 60,

                // Room generation weights
                // Vault + Spawner
                // Trainer
                // Alpha
                // Raid

                // Spawner type weights
                25, // Normal Trial Spawner
                45, // Elite Spawner
                30, // Boss Spawner

                // Vault type weights
                50, // Normal Vault
                50, // Ominous Vault

                // Special room settings
                // 10% chance per attempt
                4,   // 4 attempts

                // Dungeon Pokemon levels
                150, // Minimum level
                200, // Maximum level

                // Battle life transfer
                true, // Enabled
                10.0, // Damage per fainted Pokemon
                5     // Revive health percentage
        );
        // BUILD CONFIG
        SPEC = builder.build();
    }

    public static boolean isDebugLoggingEnabled() {
        try {
            return DEBUG_LOGGING.get();
        } catch (IllegalStateException exception) {
            return false;
        }
    }
    // CREATE TIER CONFIG
    private static TierConfig createTier(
            ModConfigSpec.Builder builder,
            String name,
            int defaultTimerSeconds,
            int defaultNormalSpawnerChance,
            int defaultEliteSpawnerChance,
            int defaultBossSpawnerChance,
            int defaultNormalVaultChance,
            int defaultOminousVaultChance,
            int defaultSpecialRoomAttempts,
            int defaultMinimumPokemonLevel,
            int defaultMaximumPokemonLevel,
            boolean defaultLifeTransferEnabled,
            double defaultLifeTransferDamagePerPokemon,
            int defaultLifeTransferRevivePercent
    ) {
        builder.push( name );
        // TIMER
        ModConfigSpec.IntValue timerSeconds =
                builder.comment(
                        "Amount of time players have inside this dungeon tier, in seconds."
                ).defineInRange(
                        "timerSeconds",
                        defaultTimerSeconds,
                        30,
                        7200
                );
        // ROOM GENERATION
        builder.push( "room_generation" );
        // VAULT + SPAWNER ROOM
        ModConfigSpec.IntValue vaultSpawnerChance =
                builder.comment(
                        "Relative chance for a Vault + Spawner room.",
                        "",
                        "A Vault + Spawner room generates:",
                        "- one spawner type",
                        "- one vault type",
                        "",
                        "The exact spawner and vault types are",
                        "controlled separately below.",
                        "",
                        "This value is treated as a weight.",
                        "Default: "
                                + 30
                ).defineInRange(
                        "vaultSpawnerChance",
                        30,
                        0,
                        1000
                );
        // TRAINER
        ModConfigSpec.IntValue trainerChance =
                builder.comment(
                        "Relative chance for a Trainer room.",
                        "",
                        "This value is treated as a weight.",
                        "Default: "
                                + 30
                ).defineInRange(
                        "trainerChance",
                        30,
                        0,
                        1000
                );
        // ALPHA
        ModConfigSpec.IntValue alphaChance =
                builder.comment(
                        "Relative chance for an Alpha room.",
                        "",
                        "This value is treated as a weight.",
                        "",
                        "Alpha encounters are currently disabled",
                        "in DungeonGenerationQueue until Cobblemon",
                        "Alpha support is implemented.",
                        "",
                        "Default: "
                                + 20
                ).defineInRange(
                        "alphaChance",
                        20,
                        0,
                        1000
                );
        // RAID
        ModConfigSpec.IntValue raidChance =
                builder.comment(
                        "Relative chance for a Raid Boss room.",
                        "",
                        "This value is treated as a weight.",
                        "Default: "
                                + 20
                ).defineInRange(
                        "raidChance",
                        20,
                        0,
                        1000
                );
        // VAULT + SPAWNER TYPE WEIGHTS
        builder.push( "vault_spawner_types" );
        // NORMAL SPAWNER
        ModConfigSpec.IntValue normalSpawnerChance =
                builder.comment(
                        "Relative weight for a normal Trial Spawner",
                        "when a Vault + Spawner room is selected.",
                        "",
                        "Only spawner types whose marker exists",
                        "inside the room can be selected.",
                        "",
                        "0 = this spawner type cannot be selected.",
                        "",
                        "This is a relative weight, not a required percentage.",
                        "Default: "
                                + defaultNormalSpawnerChance
                ).defineInRange(
                        "normalSpawnerChance",
                        defaultNormalSpawnerChance,
                        0,
                        1000
                );
        // ELITE SPAWNER
        ModConfigSpec.IntValue eliteSpawnerChance =
                builder.comment(
                        "Relative weight for an Elite Spawner",
                        "when a Vault + Spawner room is selected.",
                        "",
                        "Only spawner types whose marker exists",
                        "inside the room can be selected.",
                        "",
                        "0 = this spawner type cannot be selected.",
                        "",
                        "This is a relative weight, not a required percentage.",
                        "Default: "
                                + defaultEliteSpawnerChance
                ).defineInRange(
                        "eliteSpawnerChance",
                        defaultEliteSpawnerChance,
                        0,
                        1000
                );
        // BOSS SPAWNER
        ModConfigSpec.IntValue bossSpawnerChance =
                builder.comment(
                        "Relative weight for a Boss Spawner",
                        "when a Vault + Spawner room is selected.",
                        "",
                        "Only spawner types whose marker exists",
                        "inside the room can be selected.",
                        "",
                        "0 = this spawner type cannot be selected.",
                        "",
                        "This is a relative weight, not a required percentage.",
                        "Default: "
                                + defaultBossSpawnerChance
                ).defineInRange(
                        "bossSpawnerChance",
                        defaultBossSpawnerChance,
                        0,
                        1000
                );
        // NORMAL VAULT
        ModConfigSpec.IntValue normalVaultChance =
                builder.comment(
                        "Relative weight for a Normal Vault",
                        "when a Vault + Spawner room is selected.",
                        "",
                        "Only vault types whose marker exists",
                        "inside the room can be selected.",
                        "",
                        "0 = this vault type cannot be selected.",
                        "",
                        "This is a relative weight, not a required percentage.",
                        "Default: "
                                + defaultNormalVaultChance
                ).defineInRange(
                        "normalVaultChance",
                        defaultNormalVaultChance,
                        0,
                        1000
                );
        // OMINOUS VAULT
        ModConfigSpec.IntValue ominousVaultChance =
                builder.comment(
                        "Relative weight for an Ominous Vault",
                        "when a Vault + Spawner room is selected.",
                        "",
                        "Only vault types whose marker exists",
                        "inside the room can be selected.",
                        "",
                        "0 = this vault type cannot be selected.",
                        "",
                        "This is a relative weight, not a required percentage.",
                        "Default: "
                                + defaultOminousVaultChance
                ).defineInRange(
                        "ominousVaultChance",
                        defaultOminousVaultChance,
                        0,
                        1000
                );

        builder.pop();
        // END ROOM GENERATION
        builder.pop();
        // SPECIAL ROOMS
        builder.push( "special_rooms" );
        // SPECIAL ROOM CHANCE
        ModConfigSpec.IntValue specialRoomChance =
                builder.comment(
                        "Chance for each allowed special-room marker attempt to succeed.",
                        "",
                        "This is a real percentage, not a relative weight.",
                        "",
                        "Examples:",
                        "0 = special rooms are disabled",
                        "10 = 10% chance per attempt",
                        "25 = 25% chance per attempt",
                        "100 = guaranteed success if an eligible marker exists",
                        "",
                        "Only ONE special room can ever generate per dungeon.",
                        "That maximum is hardcoded and cannot be increased through this config.",
                        "",
                        "Default: "
                                + 25
                                + "%"
                ).defineInRange(
                        "chance",
                        25,
                        0,
                        100
                );
        // SPECIAL ROOM ATTEMPTS
        ModConfigSpec.IntValue specialRoomAttempts =
                builder.comment(
                        "Maximum number of special-room markers allowed to attempt generation.",
                        "",
                        "Markers are shuffled before attempts are made,",
                        "so the same physical marker does not always receive priority.",
                        "",
                        "Generation stops immediately after the first successful special room.",
                        "All remaining special-room markers are then removed.",
                        "",
                        "If there are fewer markers than this value,",
                        "only the available markers are attempted.",
                        "",
                        "0 = no special-room attempts",
                        "",
                        "Default: "
                                + defaultSpecialRoomAttempts
                ).defineInRange(
                        "attempts",
                        defaultSpecialRoomAttempts,
                        0,
                        64
                );

        builder.pop();
        // DUNGEON POKEMON LEVELS
        builder.push( "pokemon_levels" );
        // MINIMUM LEVEL
        ModConfigSpec.IntValue minimumPokemonLevel =
                builder.comment(
                        "Minimum level for Pokemon spawned by this dungeon tier.",
                        "",
                        "Each Pokemon independently rolls a level",
                        "between this value and maximumPokemonLevel.",
                        "",
                        "Cobblemon's maxPokemonLevel must be at least",
                        "as high as this dungeon tier's configured maximum.",
                        "",
                        "Default: "
                                + defaultMinimumPokemonLevel
                ).defineInRange(
                        "minimumPokemonLevel",
                        defaultMinimumPokemonLevel,
                        1,
                        1000
                );
        // MAXIMUM LEVEL
        ModConfigSpec.IntValue maximumPokemonLevel =
                builder.comment(
                        "Maximum level for Pokemon spawned by this dungeon tier.",
                        "",
                        "Each Pokemon independently rolls a level",
                        "between minimumPokemonLevel and this value.",
                        "",
                        "Cobblemon's maxPokemonLevel must be at least",
                        "as high as this value or Cobblemon will clamp levels.",
                        "",
                        "Default: "
                                + defaultMaximumPokemonLevel
                ).defineInRange(
                        "maximumPokemonLevel",
                        defaultMaximumPokemonLevel,
                        1,
                        1000
                );

        builder.pop();
        // BATTLE LIFE TRANSFER
        builder.push( "battle_life_transfer" );
        // ENABLED
        ModConfigSpec.BooleanValue lifeTransferEnabled =
                builder.comment(
                        "Whether losing a supported Pokemon battle",
                        "inside this dungeon tier activates life transfer.",
                        "",
                        "When enabled, fainted Pokemon are revived",
                        "and the player takes damage for each Pokemon revived.",
                        "",
                        "false = life transfer is disabled",
                        "true = life transfer is enabled",
                        "",
                        "Default: "
                                + defaultLifeTransferEnabled
                ).define(
                        "enabled",
                        defaultLifeTransferEnabled
                );
        // DAMAGE PER FAINTED POKEMON
        ModConfigSpec.DoubleValue lifeTransferDamagePerPokemon =
                builder.comment(
                        "Base damage dealt to the player for each",
                        "fainted Pokemon revived by life transfer.",
                        "",
                        "Minecraft uses 2 damage points per heart.",
                        "",
                        "Examples:",
                        "2.0 = 1 heart",
                        "6.0 = 3 hearts",
                        "10.0 = 5 hearts",
                        "",
                        "This damage is intended to use the normal",
                        "Minecraft damage system, allowing armor,",
                        "Resistance, Protection, absorption, and",
                        "other applicable damage mitigation.",
                        "",
                        "Default: "
                                + defaultLifeTransferDamagePerPokemon
                ).defineInRange(
                        "damagePerPokemon",
                        defaultLifeTransferDamagePerPokemon,
                        0.0,
                        1000.0
                );
        // REVIVE HEALTH PERCENTAGE
        ModConfigSpec.IntValue lifeTransferRevivePercent =
                builder.comment(
                        "Percentage of maximum HP restored to each",
                        "fainted Pokemon when life transfer activates.",
                        "",
                        "5 = revive at 5% maximum HP",
                        "25 = revive at 25% maximum HP",
                        "100 = revive at full HP",
                        "",
                        "Default: "
                                + defaultLifeTransferRevivePercent
                                + "%"
                ).defineInRange(
                        "revivePercent",
                        defaultLifeTransferRevivePercent,
                        1,
                        100
                );

        builder.pop();
        // END TIER
        builder.pop();
        // RETURN TIER CONFIG
        return new TierConfig(
                timerSeconds,
                vaultSpawnerChance,
                trainerChance,
                alphaChance,
                raidChance,
                normalSpawnerChance,
                eliteSpawnerChance,
                bossSpawnerChance,
                normalVaultChance,
                ominousVaultChance,
                specialRoomChance,
                specialRoomAttempts,
                minimumPokemonLevel,
                maximumPokemonLevel,
                lifeTransferEnabled,
                lifeTransferDamagePerPokemon,
                lifeTransferRevivePercent
        );
    }
    // TIER CONFIG DATA
    public record TierConfig(
            ModConfigSpec.IntValue timerSeconds,
            ModConfigSpec.IntValue vaultSpawnerChance,
            ModConfigSpec.IntValue trainerChance,
            ModConfigSpec.IntValue alphaChance,
            ModConfigSpec.IntValue raidChance,
            ModConfigSpec.IntValue normalSpawnerChance,
            ModConfigSpec.IntValue eliteSpawnerChance,
            ModConfigSpec.IntValue bossSpawnerChance,
            ModConfigSpec.IntValue normalVaultChance,
            ModConfigSpec.IntValue ominousVaultChance,
            ModConfigSpec.IntValue specialRoomChance,
            ModConfigSpec.IntValue specialRoomAttempts,
            ModConfigSpec.IntValue minimumPokemonLevel,
            ModConfigSpec.IntValue maximumPokemonLevel,
            ModConfigSpec.BooleanValue lifeTransferEnabled,
            ModConfigSpec.DoubleValue lifeTransferDamagePerPokemon,
            ModConfigSpec.IntValue lifeTransferRevivePercent

    ) {
    }
}

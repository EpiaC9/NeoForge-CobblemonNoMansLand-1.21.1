package net.epiac9.cobblemonnml.events.vault;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.theme.DungeonTheme;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;

import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DungeonVaultManager {
    // OMINOUS VAULT TRACKING
    private static final Set<BlockPos> OMINOUS_VAULTS = new HashSet<>();

    /*
     * Tracks which players have already triggered each ominous vault.
     */
    private static final Map<BlockPos, Set<UUID>>
            OMINOUS_VAULT_TRIGGERED_PLAYERS = new HashMap<>();
    // OMINOUS SCAN RATE
    /*
     * Four checks per second.
     */
    private static final long OMINOUS_VAULT_SCAN_INTERVAL_TICKS = 5L;
    private static long nextOminousVaultScanGameTime = 0L;
    // BAD OMEN SETTINGS
    private static final double BAD_OMEN_RANGE = 6.0D;
    private static final int BAD_OMEN_DURATION = 20 * 60;

    /*
     * Amplifiers are zero-based.
     * 0 = I
     * 1 = II
     * 2 = III
     * 3 = IV
     * 4 = V
     */
    private static final int MAX_BAD_OMEN_AMPLIFIER = 4;
    // VAULT TYPE
    public enum VaultType {
        NORMAL,
        OMINOUS
    }
    // SPAWN VAULT
    public static boolean spawn( ServerLevel level, BlockPos markerPos, VaultType vaultType ) {
        if (level == null || markerPos == null || vaultType == null) {
            return false;
        }
        // ACTIVE DUNGEON TIER
        DungeonTier tier =
                DungeonSession
                        .getTier();
        if (tier == null) {
            DebugLog.log( "Cannot create dungeon vault: " + "no active dungeon tier." );
            return false;
        }
        // ACTIVE DUNGEON THEME
        DungeonTheme theme =
                DungeonSession
                        .getTheme();
        if (theme == null) {
            DebugLog.log( "Cannot create dungeon vault: " + "no active dungeon theme." );
            return false;
        }
        // THEMED LOOT TABLE
        ResourceKey<LootTable> lootTable = getLootTable( theme, tier, vaultType );
        // SELECT KEY
        ItemStack keyItem =
                switch (vaultType) {
                    case NORMAL ->
                            new ItemStack( Items.TRIAL_KEY );
                    case OMINOUS ->
                            new ItemStack( Items.OMINOUS_TRIAL_KEY );
                };
        // OMINOUS STATE
        boolean ominous =
                vaultType
                        == VaultType.OMINOUS;
        // PLACE VANILLA VAULT
        BlockState vaultState =
                Blocks.VAULT
                        .defaultBlockState()
                        .setValue( VaultBlock.OMINOUS, ominous );
        boolean placed = level.setBlock( markerPos, vaultState, 3 );
        if (!placed) {
            DebugLog.log( "Failed to place dungeon vault at " + markerPos );
            return false;
        }
        // GET BLOCK ENTITY
        BlockEntity blockEntity = level.getBlockEntity( markerPos );
        if (!(blockEntity instanceof VaultBlockEntity vault)) {
            DebugLog.log( "Dungeon vault block entity was not created at " + markerPos );
            return false;
        }
        // COPY VANILLA RANGE SETTINGS
        VaultConfig dungeonConfig = getDungeonConfig(vault, lootTable, keyItem);
        // APPLY CONFIG
        vault.setConfig( dungeonConfig );
        vault.setChanged();
        // SYNC
        BlockState currentState = level.getBlockState( markerPos );
        level.sendBlockUpdated( markerPos, currentState, currentState, 3 );
        // TRACK OMINOUS VAULT
        if (vaultType == VaultType.OMINOUS) {
            BlockPos vaultPos = markerPos.immutable();
            OMINOUS_VAULTS.add( vaultPos );
            OMINOUS_VAULT_TRIGGERED_PLAYERS
                    .putIfAbsent( vaultPos, new HashSet<>() );
            DebugLog.log( "Tracking ominous dungeon vault at " + vaultPos );
        }
        // DEBUG
        DebugLog.log( "Themed dungeon vault created successfully." );
        DebugLog.log( "Dungeon theme: " + theme.getDisplayName());
        DebugLog.log( "Dungeon tier: " + tier.getDisplayName());
        DebugLog.log( "Vault type: " + vaultType );
        DebugLog.log( "Vault ominous: " + ominous );
        DebugLog.log( "Vault loot table: " + lootTable.location());
        DebugLog.log( "Vault position: " + markerPos );
        DebugLog.log( "Vault key item: " + keyItem );
        return true;
    }

    private static @NotNull VaultConfig getDungeonConfig(VaultBlockEntity vault, ResourceKey<LootTable> lootTable, ItemStack keyItem) {
        VaultConfig vanillaConfig = vault.getConfig();
        // CUSTOM DUNGEON CONFIG
        return new VaultConfig(
                lootTable,
                vanillaConfig.activationRange(),
                vanillaConfig.deactivationRange(),
                keyItem,
                Optional.empty()
        );
    }
    // SERVER TICK
    public static void tick(ServerLevel level) {
        if (level == null || OMINOUS_VAULTS.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime < nextOminousVaultScanGameTime) {
            return;
        }
        nextOminousVaultScanGameTime = gameTime + OMINOUS_VAULT_SCAN_INTERVAL_TICKS;
        double rangeSquared = BAD_OMEN_RANGE * BAD_OMEN_RANGE;
        Iterator<BlockPos> iterator = OMINOUS_VAULTS.iterator();
        while (iterator.hasNext()) {
            BlockPos vaultPos = iterator.next();
            // VAULT STILL EXISTS?
            BlockState state = level.getBlockState( vaultPos );
            if (!state.is(Blocks.VAULT)) {
                iterator.remove();
                OMINOUS_VAULT_TRIGGERED_PLAYERS.remove( vaultPos );
                DebugLog.log( "Stopped tracking removed ominous vault at " + vaultPos );
                continue;
            }
            // STILL OMINOUS?
            boolean ominous = state.getValue( VaultBlock.OMINOUS );
            if (!ominous) {
                iterator.remove();
                OMINOUS_VAULT_TRIGGERED_PLAYERS.remove( vaultPos );
                DebugLog.log( "Stopped tracking non-ominous vault at " + vaultPos );
                continue;
            }
            // TRIGGERED PLAYERS
            Set<UUID> triggeredPlayers =
                    OMINOUS_VAULT_TRIGGERED_PLAYERS
                            .computeIfAbsent( vaultPos, ignored -> new HashSet<>() );
            // NEARBY PLAYERS
            for (ServerPlayer player : level.players()) {
                double distanceSquared =
                        player.distanceToSqr( vaultPos.getX() + 0.5D, vaultPos.getY() + 0.5D, vaultPos.getZ() + 0.5D );
                if (distanceSquared > rangeSquared) {
                    continue;
                }
                UUID playerUUID = player.getUUID();
                // ALREADY TRIGGERED?
                if (!triggeredPlayers.add(playerUUID)) {
                    continue;
                }
                // CURRENT BAD OMEN
                MobEffectInstance currentEffect = player.getEffect( MobEffects.BAD_OMEN );
                int newAmplifier;
                if (currentEffect == null) {
                    newAmplifier = 0;
                } else {
                    newAmplifier = Math.min( currentEffect .getAmplifier() + 1, MAX_BAD_OMEN_AMPLIFIER );
                }
                // APPLY / UPGRADE
                player.addEffect(
                        new MobEffectInstance( MobEffects.BAD_OMEN, BAD_OMEN_DURATION, newAmplifier, false, true, true )
                );
                DebugLog.log(
                        "Applied Bad Omen "
                                + (newAmplifier + 1)
                                + " to "
                                + player
                                .getName()
                                .getString()
                                + " near ominous vault at "
                                + vaultPos
                );
            }
        }
    }
    // CLEAR TRACKED VAULTS
    public static void clearTrackedVaults() {
        int count = OMINOUS_VAULTS.size();
        OMINOUS_VAULTS.clear();
        OMINOUS_VAULT_TRIGGERED_PLAYERS
                .clear();
        nextOminousVaultScanGameTime = 0L;
        if (count > 0) {
            DebugLog.log( "Cleared " + count + " tracked ominous dungeon vault(s)." );
        }
    }
    // THEMED LOOT TABLE
    private static ResourceKey<LootTable> getLootTable( DungeonTheme theme, DungeonTier tier, VaultType vaultType ) {
        // VAULT TYPE PATH
        String typePath =
                switch (vaultType) {
                    case NORMAL ->
                            "normal";
                    case OMINOUS ->
                            "ominous";
                };
        // TIER PATH
        String path = getPath(theme, tier, typePath);
        return ResourceKey.create(
                Registries.LOOT_TABLE,
                ResourceLocation
                        .fromNamespaceAndPath( CobblemonNML.MOD_ID, path )
        );
    }
    private static @NotNull String getPath(DungeonTheme theme, DungeonTier tier, String typePath) {
        String tierPath =
                switch (tier) {
                    case TIER_1 -> "tier1";
                    case TIER_2 -> "tier2";
                    case TIER_3 -> "tier3";
                    case TIER_4 -> "tier4";
                };
        // FINAL PATH
        /*
         * Examples: dungeon/vault/themes/ghost/normal/tier1
         */
        return "dungeon/vault/themes/"
                + theme.getId()
                + "/"
                + typePath
                + "/"
                + tierPath;
    }
}

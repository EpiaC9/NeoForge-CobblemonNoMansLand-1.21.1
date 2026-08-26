package net.epiac9.cobblemonnml.dimension.gameplay;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;

import kotlin.Unit;

import net.epiac9.cobblemonnml.Config;
import net.epiac9.cobblemonnml.events.damage.ModDamageTypes;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.util.DebugLog;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DungeonBattleLifeTransfer {

    private static boolean initialized = false;

    private static final Set<BattlePlayerKey> HANDLED_BATTLES = new HashSet<>();
    private static final int MAX_HANDLED_BATTLES = 1024;

    private DungeonBattleLifeTransfer() {
    }
    // REGISTER EVENTS
    public static void register() {
        if (initialized) {
            return;
        }

        initialized = true;

        CobblemonEvents.BATTLE_VICTORY.subscribe(
                Priority.NORMAL,
                event -> {
                    PokemonBattle battle = event.getBattle();

                    if (battle == null) {
                        return Unit.INSTANCE;
                    }

                    for (ServerPlayer player : battle.getPlayers()) {
                        if (player == null) {
                            continue;
                        }

                        if (!event.getLosers().contains( battle.getActor(player.getUUID()) )) {
                            continue;
                        }

                        queueBattleLoss( player, battle, "LOSS" );
                    }

                    return Unit.INSTANCE;
                }
        );

        CobblemonEvents.BATTLE_FLED.subscribe(
                Priority.NORMAL,
                event -> {
                    PokemonBattle battle = event.getBattle();

                    if (battle == null) {
                        return Unit.INSTANCE;
                    }

                    for (ServerPlayer player : battle.getPlayers()) {
                        if (player != null) {
                            queueBattleLoss( player, battle, "FLED" );
                        }
                    }

                    return Unit.INSTANCE;
                }
        );

        DebugLog.log( "Dungeon battle life transfer events registered." );
    }
    // QUEUE BATTLE LOSS
    private static void queueBattleLoss( ServerPlayer player, PokemonBattle battle, String reason ) {
        if (player == null || battle == null) {
            return;
        }

        player.getServer().execute(() -> handleBattleLoss( player, battle, reason ) );
    }
    // HANDLE BATTLE LOSS
    private static void handleBattleLoss( ServerPlayer player, PokemonBattle battle, String reason ) {
        if (!player.level().dimension().equals( DungeonDimension.DUNGEON_DIMENSION )) {
            return;
        }

        if (!DungeonSession.isActive()) {
            return;
        }

        DungeonTier tier = DungeonSession.getTier();

        if (tier == null) {
            return;
        }

        Config.TierConfig tierConfig = getTierConfig(tier);

        if (tierConfig == null || !tierConfig.lifeTransferEnabled().get()) {
            return;
        }

        BattlePlayerKey key = new BattlePlayerKey( battle.getBattleId(), player.getUUID() );

        if (!HANDLED_BATTLES.add(key)) {
            return;
        }

        if (HANDLED_BATTLES.size() >= MAX_HANDLED_BATTLES) {
            HANDLED_BATTLES.clear();
            HANDLED_BATTLES.add(key);
        }

        PlayerPartyStore party = Cobblemon.INSTANCE
                .getStorage()
                .getParty(player);

        Set<Pokemon> faintedPokemon = new HashSet<>();

        for (Pokemon pokemon : party) {
            if (pokemon != null && pokemon.isFainted()) {
                faintedPokemon.add(pokemon);
            }
        }

        int faintedCount = faintedPokemon.size();

        if (faintedCount == 0) {
            DebugLog.log(
                    "Dungeon life transfer skipped for "
                            + player.getGameProfile().getName()
                            + ": no fainted Pokemon."
            );
            return;
        }

        int revivePercent = tierConfig
                .lifeTransferRevivePercent()
                .get();

        double damagePerPokemon = tierConfig
                .lifeTransferDamagePerPokemon()
                .get();
        // REVIVE POKEMON
        for (Pokemon pokemon : faintedPokemon) {
            int restoredHealth = Math.max( 1, (int) Math.ceil( pokemon.getMaxHealth() * (revivePercent / 100.0D) ) );

            pokemon.setCurrentHealth( Math.min( pokemon.getMaxHealth(), restoredHealth ) );
        }
        // DAMAGE PLAYER
        float totalDamage = (float) ( damagePerPokemon * faintedCount );

        if (totalDamage > 0.0F && player.level() instanceof ServerLevel level) {
            player.hurt( ModDamageTypes.lifeTransfer(level), totalDamage );
        }

        DebugLog.log(
                "Dungeon life transfer: "
                        + player.getGameProfile().getName()
                        + " | Result="
                        + reason
                        + " | Tier="
                        + tier
                        + " | Revived="
                        + faintedCount
                        + " | Revive="
                        + revivePercent
                        + "% | Damage="
                        + totalDamage
        );
    }
    // TIER CONFIG
    private static Config.TierConfig getTierConfig( DungeonTier tier ) {
        return switch (tier) {
            case TIER_1 -> Config.TIER_1;
            case TIER_2 -> Config.TIER_2;
            case TIER_3 -> Config.TIER_3;
            case TIER_4 -> Config.TIER_4;
        };
    }

    private record BattlePlayerKey( UUID battleId, UUID playerId ) {
    }
}

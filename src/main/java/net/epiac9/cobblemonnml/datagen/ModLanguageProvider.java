package net.epiac9.cobblemonnml.datagen;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.epiac9.cobblemonnml.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, CobblemonNML.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        // ITEMS
        add(ModItems.DUNGEON_ACTIVATOR.get(), "Dungeon Activator");
        add(ModItems.SPECIAL_ROOM_KEY.get(), "Special Room Key");
        add(ModItems.TOWN_INVITATION.get(), "Town Invitation");
        add(ModItems.BUG_THEME_KEY.get(), "Bug Theme Key");
        add(ModItems.DARK_THEME_KEY.get(), "Dark Theme Key");
        add(ModItems.DRAGON_THEME_KEY.get(), "Dragon Theme Key");
        add(ModItems.ELECTRIC_THEME_KEY.get(), "Electric Theme Key");
        add(ModItems.FAIRY_THEME_KEY.get(), "Fairy Theme Key");
        add(ModItems.FIGHTING_THEME_KEY.get(), "Fighting Theme Key");
        add(ModItems.FIRE_THEME_KEY.get(), "Fire Theme Key");
        add(ModItems.FLYING_THEME_KEY.get(), "Flying Theme Key");
        add(ModItems.GHOST_THEME_KEY.get(), "Ghost Theme Key");
        add(ModItems.GRASS_THEME_KEY.get(), "Grass Theme Key");
        add(ModItems.GROUND_THEME_KEY.get(), "Ground Theme Key");
        add(ModItems.ICE_THEME_KEY.get(), "Ice Theme Key");
        add(ModItems.NORMAL_THEME_KEY.get(), "Normal Theme Key");
        add(ModItems.POISON_THEME_KEY.get(), "Poison Theme Key");
        add(ModItems.PSYCHIC_THEME_KEY.get(), "Psychic Theme Key");
        add(ModItems.ROCK_THEME_KEY.get(), "Rock Theme Key");
        add(ModItems.STEEL_THEME_KEY.get(), "Steel Theme Key");
        add(ModItems.WATER_THEME_KEY.get(), "Water Theme Key");
        add(ModItems.TIER_1_KEY.get(), "Tier 1 Dungeon Key");
        add(ModItems.TIER_2_KEY.get(), "Tier 2 Dungeon Key");
        add(ModItems.TIER_3_KEY.get(), "Tier 3 Dungeon Key");
        add(ModItems.TIER_4_KEY.get(), "Tier 4 Dungeon Key");
        // PORTAL BLOCKS
        add(ModBlocks.DUNGEON_PORTAL_CORE.get(), "Dungeon Portal Core");
        add(ModBlocks.DUNGEON_PORTAL.get(), "Dungeon Portal");
        // MARKERS
        add(ModBlocks.TRAINER_MARKER.get(), "Trainer Marker");
        add(ModBlocks.ALPHA_MARKER.get(), "Alpha Marker");
        add(ModBlocks.RAID_MARKER.get(), "Raid Marker");
        add(ModBlocks.TRIAL_SPAWNER_MARKER.get(), "Trial Spawner Marker");
        add(ModBlocks.ELITE_SPAWNER_MARKER.get(), "Elite Spawner Marker");
        add(ModBlocks.BOSS_SPAWNER_MARKER.get(), "Boss Spawner Marker");
        add(ModBlocks.VAULT_MARKER.get(), "Vault Marker");
        add(ModBlocks.OMINOUS_VAULT_MARKER.get(), "Ominous Vault Marker");
        add(ModBlocks.QUEST_ITEM_MARKER.get(), "Quest Item Marker");
        add(ModBlocks.PORTAL_MARKER.get(), "Dungeon Return Portal Marker");
        add(ModBlocks.ROOM_MARKER.get(), "Room Marker");
        add(ModBlocks.SPECIAL_ROOM_MARKER.get(), "Special Room Marker");
        add(ModBlocks.GRAVE_MARKER.get(), "Cemetery Grave Marker");
        add(ModBlocks.GRAVE_PLOT.get(), "Empty Grave Plot");

        // CREATIVE TAB
        add("itemGroup.cobblemonnml.cobblemon_nml", "Cobblemon: No Man's Land");

        // ACTION BATTLE HUD / MESSAGES
        add("action.cobblemonnml.hud.command.swap", "Swap");
        add("action.cobblemonnml.hud.command.move_here", "Move Here");
        add("action.cobblemonnml.hud.level", "Lv. %s");
        add("action.cobblemonnml.warning.recall_blocked", "You can't recall at the moment.");
        add("action.cobblemonnml.warning.use_swap", "Use Swap Out during an action battle.");

        // ACTION TYPE-MECHANIC HUD
        addTypeMechanicTranslations();
        // DAMAGE DEATH MESSAGES
        add("death.attack.life_transfer", "%1$s gave their life for their Pokemon");
        add("death.attack.life_transfer.player", "%1$s gave their life for their Pokemon");
    }

    private void addTypeMechanicTranslations() {
        add("action.cobblemonnml.type_mechanic.tooltip.type", "Type: %s");
        add("action.cobblemonnml.type_mechanic.tooltip.activation", "Activation: %s");
        add("action.cobblemonnml.type_mechanic.tooltip.current", "Current: %s");

        addTypeMechanic("normal", "Normal", "Adaptation",
                "Adapts to repeated non-Normal move types and gains those ACTION type mechanics.",
                "A non-Normal effective move type appears at least twice in the current moveset.");
        addTypeMechanic("fire", "Fire", "Inferno",
                "Builds Fire Pressure. At full Pressure, Inferno turns the gauge into ammo, raises the highest offense and defense stats, and adds explosions to actions.",
                "Use any committed move or take damage.");
        addTypeMechanic("water", "Water", "Bubble",
                "Creates floating Aqua Bubbles that seek enemies, trap and immobilize on contact, and break when the trapped Pokémon takes damaging move damage.",
                "Use any move.");
        addTypeMechanic("grass", "Grass", "Seed & Flower",
                "Plants Seeds that bloom into Flowers. Any Grass mechanic identity can collect a Flower for healing over time and a random +1 stat.",
                "Use any move.");
        addTypeMechanic("electric", "Electric", "Plasma Ball",
                "Creates roaming Plasma Balls. Damaging projectile moves can chain through nearby balls and create secondary damage areas.",
                "Use any move; damaging projectile moves trigger Plasma chains.");
        addTypeMechanic("ice", "Ice", "Chilling Aura",
                "Creates a Chilling Aura that continuously slows Pokémon movement and projectile motion, up to 99%.",
                "Use any move.");
        addTypeMechanic("fighting", "Fighting", "Impact Guard",
                "Raises a brief guard that blocks incoming melee and projectile damage.",
                "Use a melee move.");
        addTypeMechanic("poison", "Poison", "Sludge",
                "Leaves Sludge based on move delivery. Pokémon standing in Sludge lose a random stat stage each second, and Poison is converted to Toxic.",
                "Use a projectile, melee, or self move.");
        addTypeMechanic("ground", "Ground", "Shockwave Sink",
                "Creates Shockwaves that build Sink while Pokémon remain inside. Sink slows movement, disables Swap at 60%, and KOs at 100%.",
                "Use a damaging targeted move or a self move.");
        addTypeMechanic("flying", "Flying", "Momentum",
                "Builds Momentum while maintaining line of sight. Ranged actions gain projectile/channel benefits and melee actions gain Flying propulsion behavior.",
                "Maintain valid line of sight and use ranged or melee actions while the Flying mechanic is active.");
        addTypeMechanic("psychic", "Psychic", "Psychic Delivery",
                "Targeted moves channel and resolve directly on the target without a travelling projectile or melee dash. Self-channel moves allow movement while channeling.",
                "Use a targeted move or a self-channel move.");
        addTypeMechanic("bug", "Bug", "Bug Adaptation",
                "Adapts through the highest eligible IV+EV trained stat. Targeted moves use offensive/speed branches; self moves use HP/defensive branches.",
                "Use a targeted or self move; the eligible stat group follows targeting mode.");
        addTypeMechanic("rock", "Rock", "Rock Construct",
                "Creates constructs that reflect projectiles and block melee attacks. Target constructs persist for 9 seconds; self constructs last through the action/channel.",
                "Use any move.");
        addTypeMechanic("ghost", "Ghost", "Ghost Curse",
                "Targeted actions can apply the current Ghost curse and subeffect mechanics.",
                "Use a targeted move.");
        addTypeMechanic("dragon", "Dragon", "Uproar",
                "Builds toward Uproar. Active Uproar uses its Dragon combat state and is followed by Dragon-specific Sleep.",
                "Use qualifying actions while the Dragon mechanic is active.");
        addTypeMechanic("dark", "Dark", "Obscurity",
                "Qualifying interactions progressively obscure the opposing Pokémon's HUD and awareness.",
                "Land a qualifying interaction while the Dark mechanic is active.");
        addTypeMechanic("steel", "Steel", "Steel Weight",
                "A self move activates Magnet Rise or Weighted from effective weight. While active, the state modifies any targeted damaging move.",
                "Successfully use a self move.");
        addTypeMechanic("fairy", "Fairy", "Illusion",
                "Creates one movement-mimicking illusion. Targeted moves copy the target; self moves copy the user.",
                "Use any move.");

        add("action.cobblemonnml.type_mechanic.live.pressure", "Pressure: %s/%s");
        add("action.cobblemonnml.type_mechanic.live.inferno_ammo", "Inferno ammo: %s/%s");
        add("action.cobblemonnml.type_mechanic.live.momentum", "Momentum: %s/%s");
        add("action.cobblemonnml.type_mechanic.live.branch", "Branch: %s");
        add("action.cobblemonnml.type_mechanic.live.uproar_buildup", "Uproar buildup: %s/%s");
        add("action.cobblemonnml.type_mechanic.live.uproar_remaining", "Uproar remaining: %s");
        add("action.cobblemonnml.type_mechanic.live.dragon_sleep", "Dragon Sleep: %s");
        add("action.cobblemonnml.type_mechanic.live.steel_inactive", "State: inactive");
        add("action.cobblemonnml.type_mechanic.live.steel_state", "State: %s (%s)");
        add("action.cobblemonnml.type_mechanic.live.adapted", "Adapted: %s");
        add("action.cobblemonnml.type_mechanic.live.seconds", "%ss");

        add("action.cobblemonnml.type_mechanic.bug.branch.none", "None");
        add("action.cobblemonnml.type_mechanic.bug.branch.hp", "HP");
        add("action.cobblemonnml.type_mechanic.bug.branch.attack", "Attack");
        add("action.cobblemonnml.type_mechanic.bug.branch.defense", "Defense");
        add("action.cobblemonnml.type_mechanic.bug.branch.special_attack", "Special Attack");
        add("action.cobblemonnml.type_mechanic.bug.branch.special_defense", "Special Defense");
        add("action.cobblemonnml.type_mechanic.bug.branch.speed", "Speed");
        add("action.cobblemonnml.type_mechanic.steel.state.magnet_rise", "Magnet Rise");
        add("action.cobblemonnml.type_mechanic.steel.state.weighted", "Weighted");
    }

    private void addTypeMechanic(String id, String typeName, String mechanicName, String description, String activation) {
        String prefix = "action.cobblemonnml.type_mechanic." + id + ".";
        add(prefix + "type", typeName);
        add(prefix + "name", mechanicName);
        add(prefix + "description", description);
        add(prefix + "activation", activation);
    }

}

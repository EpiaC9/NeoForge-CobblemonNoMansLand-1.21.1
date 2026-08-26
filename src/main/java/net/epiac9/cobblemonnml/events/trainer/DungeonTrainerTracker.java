package net.epiac9.cobblemonnml.events.trainer;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DungeonTrainerTracker {
    // TRACKED DUNGEON TRAINERS
    /*
     * Key: EasyNPC entity UUID
     * Value:Runtime TBCS/RCT trainer ID
     * Example:
     * UUID: 12345678-1234-1234-1234-123456789abc
     * Runtime trainer ID: tbcs:test_trainer_tier_2_2__team_3__dungeon_12345678123412341234123456789abc
     */
    private static final Map<UUID, String> TRAINERS = new HashMap<>();
    // TRACK TRAINER + RCT ID
    public static void track( UUID trainerUUID, String rctTrainerId ) {
        track( trainerUUID, rctTrainerId, null );
    }
    // TRACKED EASY NPC PRESETS
    /*
     * Key: EasyNPC entity UUID
     * Value: Original EasyNPC preset resource.
     * Example:
     * cobblemonnml:easy_npc/preset/humanoid/trainers/bug/tier_2/test_trainer_1.npc.nbt
     */
    private static final Map<UUID, ResourceLocation> PRESETS = new HashMap<>();
    // TRACK TRAINER + RCT ID + PRESET
    public static void track( UUID trainerUUID, String rctTrainerId, ResourceLocation preset ) {
        if (trainerUUID == null) {
            return;
        }
        TRAINERS.put( trainerUUID, rctTrainerId );
        if (preset != null) {
            PRESETS.put( trainerUUID, preset );
        } else {
            PRESETS.remove( trainerUUID );
        }
    }
    // UNTRACK
    public static void untrack(UUID trainerUUID) {
        if (trainerUUID == null) {
            return;
        }
        TRAINERS.remove( trainerUUID );
        PRESETS.remove( trainerUUID );
    }
    // GET ALL TRACKED EASY NPC UUIDS
    public static Set<UUID> getTrackedTrainers() {
        return new HashSet<>( TRAINERS.keySet() );
    }
    // GET RUNTIME RCT / TBCS TRAINER ID
    public static String getRCTTrainerId(UUID trainerUUID) {
        if (trainerUUID == null) {
            return null;
        }
        return TRAINERS.get( trainerUUID );
    }
    // GET ORIGINAL EASY NPC PRESET
    public static ResourceLocation getPreset(UUID trainerUUID) {
        if (trainerUUID == null) {
            return null;
        }
        return PRESETS.get( trainerUUID );
    }
    // SIZE
    public static int size() {
        return TRAINERS.size();
    }
    // CLEAR
    public static void clear() {
        TRAINERS.clear();
        PRESETS.clear();
    }
}

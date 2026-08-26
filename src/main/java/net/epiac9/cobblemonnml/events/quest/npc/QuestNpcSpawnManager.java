package net.epiac9.cobblemonnml.events.quest.npc;

import de.markusbordihn.easynpc.api.handler.EasyNPCEntityHandler;
import de.markusbordihn.easynpc.data.npc.NPCRemovalReason;
import de.markusbordihn.easynpc.data.preset.PresetType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import net.epiac9.cobblemonnml.dimension.tier.DungeonTier;
import net.epiac9.cobblemonnml.overworld.town.RecruitableNpcDefinition;
import net.epiac9.cobblemonnml.overworld.town.RecruitableNpcRegistry;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestNpcSpawnManager {
    public static final String RECRUITMENT_ID_TAG = "cobblemonnml_recruitment_id";
    private static final double EXISTING_NPC_RADIUS = 1.75D;
    private static final String MOD_ID = "cobblemonnml";
    private static final String PRESET_ROOT = "easy_npc/preset/humanoid/quests_givers/";
    private static final String PRESET_SUFFIX = ".npc.nbt";

    private QuestNpcSpawnManager() {
    }

    public static boolean spawn(ServerLevel level, BlockPos markerPos, DungeonTier tier) {
        if (level == null || markerPos == null || tier == null) return false;
        if (tier != DungeonTier.TIER_1 && tier != DungeonTier.TIER_2) {
            DebugLog.log("[CobblemonNML] Special-room Quest NPCs are not enabled for " + tier.getDisplayName());
            return true;
        }

        List<ResourceLocation> pool = findQuestGiverPresets(level, tier);
        if (pool.isEmpty()) {
            DebugLog.log("[CobblemonNML] No Quest NPC presets are available for " + tier.getDisplayName());
            return false;
        }

        BlockPos spawnPos = markerPos.above();
        Vec3 position = new Vec3(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
        UUID existing = findTrackedNpcNear(level, position);
        if (existing != null) {
            DebugLog.log("[CobblemonNML] Skipping duplicate Quest NPC spawn near " + markerPos);
            return true;
        }

        ResourceLocation selectedPreset = pool.get(level.getRandom().nextInt(pool.size()));
        UUID npcId = UUID.randomUUID();
        Optional<EasyNPC<?>> spawned = EasyNPCEntityHandler.spawnFromPreset(PresetType.DATA, selectedPreset, level, position, npcId, null);
        if (spawned.isEmpty()) {
            DebugLog.log("[CobblemonNML] Failed to spawn Quest NPC from preset " + selectedPreset);
            return false;
        }

        EasyNPC<?> npc = spawned.get();
        RecruitableNpcDefinition recruitable = RecruitableNpcRegistry.getBySourcePreset(selectedPreset);
        if (recruitable != null) npc.getEntity().getPersistentData().putString(RECRUITMENT_ID_TAG, recruitable.id());

        QuestNpcTracker.track(npc.getEntityUUID(), selectedPreset);
        DungeonQuestDuplicateGuard.scheduleCheck(level, npc.getEntityUUID(), npc.getEntity().position());

        DebugLog.log("[CobblemonNML] Quest NPC spawned successfully.");
        DebugLog.log("[CobblemonNML] Preset: " + selectedPreset);
        DebugLog.log("[CobblemonNML] Tier: " + tier.getDisplayName());
        if (recruitable != null) DebugLog.log("[CobblemonNML] Recruitable NPC id: " + recruitable.id());
        DebugLog.log("[CobblemonNML] NPC UUID: " + npc.getEntityUUID());
        return true;
    }

    public static void cleanupAll(ServerLevel level) {
        DungeonQuestDuplicateGuard.clear();
        if (level == null) {
            QuestNpcTracker.clear();
            return;
        }
        for (UUID npcId : QuestNpcTracker.getTrackedNpcs()) {
            try {
                EasyNPCEntityHandler.despawn(npcId, level, NPCRemovalReason.DESPAWNED);
            } catch (Exception exception) {
                DebugLog.log("[CobblemonNML] EasyNPC Quest NPC despawn failed for " + npcId);
            }
            Entity remaining = level.getEntity(npcId);
            if (remaining != null && !remaining.isRemoved()) remaining.discard();
            QuestNpcTracker.untrack(npcId);
        }
        QuestNpcTracker.clear();
    }

    private static List<ResourceLocation> findQuestGiverPresets(ServerLevel level, DungeonTier tier) {
        String tierFolder = switch (tier) {
            case TIER_1 -> "tier_1";
            case TIER_2 -> "tier_2";
            default -> null;
        };
        if (tierFolder == null) return List.of();
        String directory = PRESET_ROOT + tierFolder;
        Map<ResourceLocation, Resource> resources = level.getServer().getResourceManager().listResources(
                directory,
                id -> MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith(directory + "/") && id.getPath().endsWith(PRESET_SUFFIX)
        );
        if (resources.isEmpty()) return List.of();
        List<ResourceLocation> presets = new ArrayList<>(resources.keySet());
        presets.sort(Comparator.comparing(ResourceLocation::toString));
        DebugLog.log("[CobblemonNML] Found " + presets.size() + " Quest NPC preset(s) for " + tier.getDisplayName() + " under " + directory);
        return List.copyOf(presets);
    }

    private static UUID findTrackedNpcNear(ServerLevel level, Vec3 position) {
        double maxDistanceSquared = EXISTING_NPC_RADIUS * EXISTING_NPC_RADIUS;
        for (UUID npcId : QuestNpcTracker.getTrackedNpcs()) {
            Entity entity = level.getEntity(npcId);
            if (entity == null || entity.isRemoved() || !entity.isAlive()) continue;
            if (entity.position().distanceToSqr(position) <= maxDistanceSquared) return npcId;
        }
        return null;
    }
}

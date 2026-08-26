package net.epiac9.cobblemonnml.overworld.town;

import de.markusbordihn.easynpc.api.handler.EasyNPCEntityHandler;
import de.markusbordihn.easynpc.data.npc.NPCRemovalReason;
import de.markusbordihn.easynpc.data.preset.PresetType;
import de.markusbordihn.easynpc.entity.easynpc.EasyNPC;
import net.epiac9.cobblemonnml.events.quest.npc.QuestNpcSpawnManager;
import net.epiac9.cobblemonnml.events.quest.npc.QuestNpcTracker;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.epiac9.cobblemonnml.overworld.village.overworld.OverworldPortalGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public final class TownRecruitmentManager {
    public static final String TOWN_RESIDENT_TAG = "cobblemonnml_town_resident";

    private TownRecruitmentManager() {
    }

    public static RecruitmentOutcome recruit(ServerPlayer player, Entity sourceNpc) {
        if (player == null || sourceNpc == null) {
            return RecruitmentOutcome.NOT_RECRUITABLE;
        }
        if (sourceNpc.getPersistentData().getBoolean(TOWN_RESIDENT_TAG)) {
            return RecruitmentOutcome.NOT_RECRUITABLE;
        }

        String recruitmentId = resolveRecruitmentId(sourceNpc);
        RecruitableNpcDefinition definition = RecruitableNpcRegistry.get(recruitmentId);
        if (definition == null) {
            return RecruitmentOutcome.NOT_RECRUITABLE;
        }

        TownRecruitmentSavedData savedData = TownRecruitmentSavedData.get(player.serverLevel().getServer());
        if (savedData.isRecruited(definition.id())) {
            return RecruitmentOutcome.ALREADY_RECRUITED;
        }

        return switch (definition.id()) {
            case "gravekeeper" -> recruitGravekeeper(player, sourceNpc, definition, savedData);
            default -> RecruitmentOutcome.NOT_RECRUITABLE;
        };
    }

    public static String getAlreadyMovedInDialog(Entity sourceNpc) {
        RecruitableNpcDefinition definition = RecruitableNpcRegistry.get(resolveRecruitmentId(sourceNpc));
        return definition == null ? null : definition.alreadyMovedInDialog();
    }

    private static RecruitmentOutcome recruitGravekeeper(
            ServerPlayer player,
            Entity sourceNpc,
            RecruitableNpcDefinition definition,
            TownRecruitmentSavedData savedData
    ) {
        ServerLevel overworld = player.serverLevel().getServer().overworld();
        OverworldPortalGenerator.CemeteryGenerationResult cemetery =
                OverworldPortalGenerator.ensureCemeteryForRecruitment(overworld);
        if (!cemetery.success() || cemetery.origin() == null) {
            DebugLog.log("[CobblemonNML] Gravekeeper recruitment failed because the cemetery could not be prepared.");
            return RecruitmentOutcome.FAILED;
        }

        Vec3 residentPosition = findResidentPosition(overworld, cemetery);
        UUID residentUuid = UUID.randomUUID();
        Optional<EasyNPC<?>> spawned = EasyNPCEntityHandler.spawnFromPreset(
                PresetType.DATA,
                definition.residentPreset(),
                overworld,
                residentPosition,
                residentUuid,
                null
        );
        if (spawned.isEmpty()) {
            DebugLog.log(
                    "[CobblemonNML] Gravekeeper recruitment failed because resident preset could not spawn: "
                            + definition.residentPreset()
            );
            return RecruitmentOutcome.FAILED;
        }

        EasyNPC<?> resident = spawned.get();
        Entity residentEntity = resident.getEntity();
        residentEntity.getPersistentData().putString(
                QuestNpcSpawnManager.RECRUITMENT_ID_TAG,
                definition.id()
        );
        residentEntity.getPersistentData().putBoolean(TOWN_RESIDENT_TAG, true);

        savedData.markRecruited(
                definition.id(),
                resident.getEntityUUID(),
                cemetery.origin()
        );

        despawnSourceQuestNpc(sourceNpc);

        DebugLog.log("[CobblemonNML] Town NPC recruited successfully: " + definition.id());
        DebugLog.log("[CobblemonNML] Resident preset: " + definition.residentPreset());
        DebugLog.log("[CobblemonNML] Resident UUID: " + resident.getEntityUUID());
        DebugLog.log("[CobblemonNML] Town structure origin: " + cemetery.origin());
        return RecruitmentOutcome.SUCCESS;
    }

    private static Vec3 findResidentPosition(
            ServerLevel overworld,
            OverworldPortalGenerator.CemeteryGenerationResult cemetery
    ) {
        int x;
        int z;
        if (cemetery.bounds() != null) {
            x = cemetery.bounds().maxX() + 2;
            z = (cemetery.bounds().minZ() + cemetery.bounds().maxZ()) / 2;
        } else {
            x = cemetery.origin().getX() + 2;
            z = cemetery.origin().getZ() + 2;
        }
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(x + 0.5D, y, z + 0.5D);
    }

    private static void despawnSourceQuestNpc(Entity sourceNpc) {
        if (!(sourceNpc.level() instanceof ServerLevel sourceLevel)) {
            sourceNpc.discard();
            QuestNpcTracker.untrack(sourceNpc.getUUID());
            return;
        }
        try {
            EasyNPCEntityHandler.despawn(
                    sourceNpc.getUUID(),
                    sourceLevel,
                    NPCRemovalReason.DESPAWNED
            );
        } catch (Exception exception) {
            DebugLog.log("[CobblemonNML] EasyNPC despawn failed after town recruitment for " + sourceNpc.getUUID());
        }
        Entity remaining = sourceLevel.getEntity(sourceNpc.getUUID());
        if (remaining != null && !remaining.isRemoved()) {
            remaining.discard();
        }
        QuestNpcTracker.untrack(sourceNpc.getUUID());
    }

    private static String resolveRecruitmentId(Entity sourceNpc) {
        if (sourceNpc == null) {
            return null;
        }
        String persistent = sourceNpc.getPersistentData().getString(QuestNpcSpawnManager.RECRUITMENT_ID_TAG);
        return persistent == null || persistent.isBlank() ? null : persistent;
    }

    public enum RecruitmentOutcome {
        SUCCESS,
        ALREADY_RECRUITED,
        NOT_RECRUITABLE,
        FAILED
    }
}

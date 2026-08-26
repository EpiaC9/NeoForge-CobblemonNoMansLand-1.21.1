package net.epiac9.cobblemonnml.events.quest.npc;

import de.markusbordihn.easynpc.api.handler.EasyNPCEntityHandler;
import de.markusbordihn.easynpc.data.npc.NPCRemovalReason;
import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.epiac9.cobblemonnml.events.quest.QuestRuntimeManager;
import net.epiac9.cobblemonnml.registry.ModItems;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "cobblemonnml")
public final class QuestNpcInteractionEvents {
    private static final String THANKS_DIALOG = "thanks";
    private static final String WRONG_ITEM_DIALOG = "wrong_item";
    private static final String NOT_ENOUGH_DIALOG = "not_enough";

    private static final int DEPARTURE_DELAY_TICKS = 30;
    private static final int STEP_INTERVAL_TICKS = 4;
    private static final int STEP_COUNT = 5;

    private static final List<PendingDeparture> PENDING_DEPARTURES = new ArrayList<>();

    private QuestNpcInteractionEvents() {
    }

    @SubscribeEvent
    public static void onQuestNpcInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!QuestNpcTracker.isTrackedQuestNpc(event.getTarget().getUUID())) {
            return;
        }
        if (event.getItemStack().is(ModItems.TOWN_INVITATION.get())) {
            return;
        }

        QuestRuntimeManager.ItemHandInResult handIn =
                QuestRuntimeManager.tryHandInItemQuest(player, event.getItemStack());

        String dialogLabel = switch (handIn.outcome()) {
            case COMPLETED -> THANKS_DIALOG;
            case WRONG_ITEM -> WRONG_ITEM_DIALOG;
            case NOT_ENOUGH -> NOT_ENOUGH_DIALOG;
            case NONE -> null;
        };
        if (dialogLabel == null) {
            return;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        openHandInDialog(player, event, dialogLabel);

        if (handIn.outcome() == QuestRuntimeManager.ItemHandInOutcome.COMPLETED) {
            scheduleDeparture(player.serverLevel(), event.getTarget().getUUID());

            DebugLog.log(
                    "[CobblemonNML] Quest NPC accepted an item hand-in from "
                            + player.getGameProfile().getName()
                            + ". Departure scheduled."
            );
        }
    }

    private static void openHandInDialog(
            ServerPlayer player,
            PlayerInteractEvent.EntityInteract event,
            String dialogLabel
    ) {
        if (!(event.getTarget() instanceof DialogDataCapable<?> dialogNpc)) {
            DebugLog.log(
                    "[CobblemonNML] Tracked Quest NPC does not support EasyNPC dialogs: "
                            + event.getTarget().getUUID()
            );
            return;
        }

        if (!dialogNpc.hasDialog(dialogLabel)) {
            DebugLog.log(
                    "[CobblemonNML] Quest NPC is missing EasyNPC dialog label '"
                            + dialogLabel
                            + "'."
            );
            return;
        }

        UUID dialogId = dialogNpc.getDialogId(dialogLabel);
        if (dialogId == null) {
            DebugLog.log(
                    "[CobblemonNML] Quest NPC could not resolve EasyNPC dialog label '"
                            + dialogLabel
                            + "'."
            );
            return;
        }

        dialogNpc.openDialog(player, dialogId);
    }

    private static void scheduleDeparture(ServerLevel level, UUID npcUuid) {
        if (level == null || npcUuid == null) {
            return;
        }

        PENDING_DEPARTURES.removeIf(pending -> pending.npcUuid.equals(npcUuid));
        PENDING_DEPARTURES.add(
                new PendingDeparture(
                        level,
                        npcUuid,
                        level.getGameTime() + DEPARTURE_DELAY_TICKS
                )
        );
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_DEPARTURES.isEmpty()) {
            return;
        }

        MinecraftServer server = event.getServer();
        Iterator<PendingDeparture> iterator = PENDING_DEPARTURES.iterator();

        while (iterator.hasNext()) {
            PendingDeparture pending = iterator.next();
            ServerLevel level = pending.level;

            if (level == null || level.getServer() != server) {
                iterator.remove();
                continue;
            }

            Entity npc = level.getEntity(pending.npcUuid);
            if (npc == null || npc.isRemoved()) {
                QuestNpcTracker.untrack(pending.npcUuid);
                iterator.remove();
                continue;
            }

            long gameTime = level.getGameTime();
            if (gameTime < pending.nextStepGameTime) {
                continue;
            }

            if (pending.stepsPlayed < STEP_COUNT) {
                playRunningStep(level, npc, pending.stepsPlayed);
                spawnRunningDust(level, npc);
                pending.stepsPlayed++;
                pending.nextStepGameTime += STEP_INTERVAL_TICKS;
                continue;
            }

            despawnCompletedQuestNpc(level, npc);
            iterator.remove();
        }
    }

    private static void playRunningStep(ServerLevel level, Entity npc, int stepIndex) {
        SoundEvent stepSound = npc.getBlockStateOn().getSoundType().getStepSound();

        double horizontalLength = Math.sqrt(
                npc.getLookAngle().x * npc.getLookAngle().x
                        + npc.getLookAngle().z * npc.getLookAngle().z
        );
        double directionX = horizontalLength > 0.0001D ? npc.getLookAngle().x / horizontalLength : 0.0D;
        double directionZ = horizontalLength > 0.0001D ? npc.getLookAngle().z / horizontalLength : 0.0D;
        double distance = stepIndex * 0.8D;

        float pitch = 0.95F + level.getRandom().nextFloat() * 0.15F;
        level.playSound(
                null,
                npc.getX() + directionX * distance,
                npc.getY(),
                npc.getZ() + directionZ * distance,
                stepSound,
                SoundSource.PLAYERS,
                0.9F,
                pitch
        );
    }

    private static void spawnRunningDust(ServerLevel level, Entity npc) {
        BlockParticleOption dust = new BlockParticleOption(
                ParticleTypes.BLOCK,
                npc.getBlockStateOn()
        );

        level.sendParticles(
                dust,
                npc.getX(),
                npc.getY() + 0.1D,
                npc.getZ(),
                8,
                0.3D,
                0.05D,
                0.3D,
                0.08D
        );
    }

    private static void despawnCompletedQuestNpc(ServerLevel level, Entity npc) {
        UUID npcUuid = npc.getUUID();
        boolean removedByEasyNpc = false;

        try {
            removedByEasyNpc = EasyNPCEntityHandler.despawn(
                    npcUuid,
                    level,
                    NPCRemovalReason.DESPAWNED
            );
        } catch (Exception exception) {
            DebugLog.log(
                    "[CobblemonNML] EasyNPC completed Quest NPC despawn threw for "
                            + npcUuid
            );
            exception.printStackTrace();
        }

        Entity remaining = level.getEntity(npcUuid);
        if (remaining != null && !remaining.isRemoved()) {
            remaining.discard();
        }

        QuestNpcTracker.untrack(npcUuid);
        DebugLog.log(
                "[CobblemonNML] Completed Quest NPC departed. EasyNPC result="
                        + removedByEasyNpc
                        + ", UUID="
                        + npcUuid
        );
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING_DEPARTURES.clear();
    }

    private static final class PendingDeparture {
        private final ServerLevel level;
        private final UUID npcUuid;
        private long nextStepGameTime;
        private int stepsPlayed;

        private PendingDeparture(ServerLevel level, UUID npcUuid, long nextStepGameTime) {
            this.level = level;
            this.npcUuid = npcUuid;
            this.nextStepGameTime = nextStepGameTime;
            this.stepsPlayed = 0;
        }
    }
}

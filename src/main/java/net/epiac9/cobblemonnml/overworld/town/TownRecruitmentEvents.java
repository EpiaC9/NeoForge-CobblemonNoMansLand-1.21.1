package net.epiac9.cobblemonnml.overworld.town;

import de.markusbordihn.easynpc.entity.easynpc.data.DialogDataCapable;
import net.epiac9.cobblemonnml.registry.ModItems;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

@EventBusSubscriber(modid = "cobblemonnml")
public final class TownRecruitmentEvents {
    private TownRecruitmentEvents() {
    }

    @SubscribeEvent
    public static void onNpcInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.getItemStack().is(ModItems.TOWN_INVITATION.get())) {
            return;
        }

        Entity target = event.getTarget();
        TownRecruitmentManager.RecruitmentOutcome outcome =
                TownRecruitmentManager.recruit(player, target);

        switch (outcome) {
            case NOT_RECRUITABLE -> {
                return;
            }
            case SUCCESS -> {
                if (!player.getAbilities().instabuild) {
                    event.getItemStack().shrink(1);
                }
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            case ALREADY_RECRUITED -> {
                openAlreadyMovedInDialog(player, target);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
            case FAILED -> {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
            }
        }
    }

    private static void openAlreadyMovedInDialog(ServerPlayer player, Entity target) {
        String label = TownRecruitmentManager.getAlreadyMovedInDialog(target);
        if (label == null || label.isBlank()) {
            return;
        }
        if (!(target instanceof DialogDataCapable<?> dialogNpc)) {
            DebugLog.log("[CobblemonNML] Recruitable NPC does not support EasyNPC dialogs: " + target.getUUID());
            return;
        }
        if (!dialogNpc.hasDialog(label)) {
            DebugLog.log("[CobblemonNML] Recruitable NPC is missing EasyNPC dialog label '" + label + "'.");
            return;
        }
        UUID dialogId = dialogNpc.getDialogId(label);
        if (dialogId == null) {
            DebugLog.log("[CobblemonNML] Recruitable NPC could not resolve EasyNPC dialog label '" + label + "'.");
            return;
        }
        dialogNpc.openDialog(player, dialogId);
    }
}

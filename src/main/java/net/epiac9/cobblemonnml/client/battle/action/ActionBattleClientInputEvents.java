package net.epiac9.cobblemonnml.client.battle.action;

import net.epiac9.cobblemonnml.CobblemonNML;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleMoveHerePayload;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleMovePayload;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleSwapPayload;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;


@EventBusSubscriber(modid = CobblemonNML.MOD_ID, value = Dist.CLIENT)
public final class ActionBattleClientInputEvents {
    private ActionBattleClientInputEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (ActionBattleKeyMappings.MOVE_HERE.consumeClick()) sendMoveHereIntent();
        while (ActionBattleKeyMappings.MOVE_1.consumeClick()) sendMoveIntent(0);
        while (ActionBattleKeyMappings.MOVE_2.consumeClick()) sendMoveIntent(1);
        while (ActionBattleKeyMappings.MOVE_3.consumeClick()) sendMoveIntent(2);
        while (ActionBattleKeyMappings.MOVE_4.consumeClick()) sendMoveIntent(3);
        while (ActionBattleKeyMappings.SWAP_OUT.consumeClick()) sendSwapIntent();
    }

    private static void sendMoveIntent(int moveSlot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        if (!minecraft.level.dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return;
        PacketDistributor.sendToServer(new ActionBattleMovePayload(moveSlot));
    }

    private static void sendSwapIntent() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        if (!minecraft.level.dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return;
        PacketDistributor.sendToServer(new ActionBattleSwapPayload());
    }

    private static void sendMoveHereIntent() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        if (!minecraft.level.dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) return;
        double targetDistance = Math.max(64.0D, minecraft.options.getEffectiveRenderDistance() * 16.0D);
        HitResult hit = minecraft.player.pick(targetDistance, 1.0F, false);
        if (hit == null) return;
        Vec3 target = hit.getLocation();
        PacketDistributor.sendToServer(new ActionBattleMoveHerePayload(target.x, target.y, target.z));
    }
}

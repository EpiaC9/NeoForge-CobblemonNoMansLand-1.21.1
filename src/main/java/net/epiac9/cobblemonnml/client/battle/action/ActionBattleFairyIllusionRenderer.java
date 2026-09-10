package net.epiac9.cobblemonnml.client.battle.action;

import com.jorgaomc.cobblemonstonestatues.compat.CobblemonStoneBridgeImpl;
import com.jorgaomc.cobblemonstonestatues.data.StatueMaterial;
import com.jorgaomc.cobblemonstonestatues.data.StatueScale;
import com.jorgaomc.cobblemonstonestatues.data.StatueSelection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyIllusionBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public final class ActionBattleFairyIllusionRenderer implements BlockEntityRenderer<ActionBattleFairyIllusionBlockEntity> {
    public ActionBattleFairyIllusionRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ActionBattleFairyIllusionBlockEntity entity, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        StatueSelection selection = new StatueSelection(entity.speciesId(), entity.formId(), entity.aspectsCsv(),
                entity.animationName(), StatueScale.NORMAL, StatueMaterial.STONE,
                Math.floorMod(Math.round(entity.yaw() / 45.0F), 8), 0L);
        pose.pushPose(); pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(selection.rotation45() * 45.0F));
        pose.scale(1.0F, -1.0F, -1.0F);
        CobblemonStoneBridgeImpl.INSTANCE.renderStonePokemon(selection,
                CobblemonStoneBridgeImpl.INSTANCE.getBaseTexture(selection), pose, buffers, light, overlay);
        pose.popPose();
    }
}

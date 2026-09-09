package net.epiac9.cobblemonnml.client.battle.action;

import com.jorgaomc.cobblemonstonestatues.compat.CobblemonStoneBridgeImpl;
import com.jorgaomc.cobblemonstonestatues.client.render.DynamicStoneTextureCache;
import com.jorgaomc.cobblemonstonestatues.data.StatueMaterial;
import com.jorgaomc.cobblemonstonestatues.data.StatueScale;
import com.jorgaomc.cobblemonstonestatues.data.StatueSelection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugCarapaceBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugCarapaceVisualPlan;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugVisualPlan;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class ActionBattleBugCarapaceRenderer implements BlockEntityRenderer<ActionBattleBugCarapaceBlockEntity> {
    private static final int ZONE_SEGMENTS = 48;

    public ActionBattleBugCarapaceRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ActionBattleBugCarapaceBlockEntity entity, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ActionBattleBugCarapaceVisualPlan plan = ActionBattleBugCarapaceVisualPlan.from(
                entity.speciesId(), entity.formId(), entity.aspectsCsv(), entity.animationName(), entity.statueYaw(),
                entity.ownerPokemonId() == null ? 0L
                        : entity.ownerPokemonId().getMostSignificantBits() ^ entity.ownerPokemonId().getLeastSignificantBits());
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        if (ActionBattleBugVisualPlan.showsStatue(entity.physical(), entity.effectZone())) {
            renderShell(plan, poseStack, buffers, packedLight);
        }
        if (entity.effectZone()) renderZone(plan, poseStack, buffers);
        poseStack.popPose();
    }

    private static void renderShell(ActionBattleBugCarapaceVisualPlan plan, PoseStack poseStack,
                                    MultiBufferSource buffers, int packedLight) {
        StatueSelection selection = selection(plan);
        ResourceLocation baseTexture = CobblemonStoneBridgeImpl.INSTANCE.getBaseTexture(selection);
        ResourceLocation stoneTexture = DynamicStoneTextureCache.INSTANCE.getOrCreate(
                baseTexture, selection.material());
        float scale = (float) (selection.scale().value * plan.shellScale());
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(selection.rotation45() * 45.0F));
        poseStack.scale(scale, -scale, -scale);
        CobblemonStoneBridgeImpl.INSTANCE.renderStonePokemon(
                selection, stoneTexture, poseStack, buffers, packedLight, 0);
        poseStack.popPose();
    }

    private static void renderZone(ActionBattleBugCarapaceVisualPlan plan, PoseStack poseStack,
                                   MultiBufferSource buffers) {
        VertexConsumer line = buffers.getBuffer(RenderType.debugLineStrip(2.0D));
        PoseStack.Pose pose = poseStack.last();
        for (int point = 0; point <= ZONE_SEGMENTS; point++) {
            line.addVertex(pose, (float) plan.zoneX(point, ZONE_SEGMENTS), 0.08F,
                            (float) plan.zoneZ(point, ZONE_SEGMENTS))
                    .setColor(85, 220, 190, 125)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }

    public static StatueSelection selection(String species, String form, String aspects,
                                             float yaw, long seed) {
        ActionBattleBugCarapaceVisualPlan plan = ActionBattleBugCarapaceVisualPlan.from(
                species, form, aspects, yaw, seed);
        return selection(plan);
    }

    private static StatueSelection selection(ActionBattleBugCarapaceVisualPlan plan) {
        return new StatueSelection(plan.speciesId(), plan.formId(), plan.aspectsCsv(), plan.animationName(), StatueScale.NORMAL,
                StatueMaterial.STONE, plan.rotation45(), plan.stoneSeed());
    }

    @Override
    public boolean shouldRenderOffScreen(ActionBattleBugCarapaceBlockEntity entity) {
        return entity.effectZone();
    }

}

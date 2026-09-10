package net.epiac9.cobblemonnml.client.battle.action;

import com.mojang.blaze3d.systems.RenderSystem;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class ActionBattleTypeMechanicIconRenderer {
    private static final ResourceLocation TYPES = ResourceLocation.fromNamespaceAndPath(
            "cobblemon", "textures/gui/types.png");
    private static final int TYPE_SIZE = 36;

    private ActionBattleTypeMechanicIconRenderer() {}

    public static void render(GuiGraphics graphics, int x, int y, int size,
                              ActionBattleHudPayload.StatusState state,
                              ActionBattleTypeMechanicVisualRegistry.MechanicVisual visual,
                              boolean grayscale) {
        if (graphics == null || state == null || visual == null) return;
        frame(graphics, x, y, size);
        ActionBattleHudLayout.Rect inner = ActionBattleEffectIconRules.innerBounds(size);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (grayscale) RenderSystem.setShaderColor(0.58F, 0.58F, 0.58F, 1.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(x + inner.x(), y + inner.y(), 0.0F);
        if (visual.alternateIcon() != null) {
            graphics.pose().scale(inner.width() / 16.0F, inner.height() / 16.0F, 1.0F);
            graphics.blit(ResourceLocation.fromNamespaceAndPath("cobblemonnml", visual.alternateIcon()),
                    0, 0, 0.0F, 0.0F, 16, 16, 16, 16);
        } else {
            graphics.pose().scale(inner.width() / (float) TYPE_SIZE, inner.height() / (float) TYPE_SIZE, 1.0F);
            graphics.blit(TYPES, 0, 0, (float) (visual.typeIndex() * TYPE_SIZE), 0.0F,
                    TYPE_SIZE, TYPE_SIZE, TYPE_SIZE * 18, TYPE_SIZE);
        }
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (visual.progressRing()) ring(graphics, x, y, size,
                state.totalTicks() > 0L ? (float) state.remainingTicks() / state.totalTicks() : 0.0F,
                visual.ringArgb());
        if (visual.counter()) {
            Minecraft minecraft = Minecraft.getInstance();
            graphics.drawString(minecraft.font, Long.toString(state.remainingTicks()),
                    x + size - 6, y + size - 8, 0xFFFFFFFF, true);
        }
        RenderSystem.disableBlend();
        tooltipIfHovered(graphics, x, y, size, state, visual);
    }

    private static void frame(GuiGraphics graphics, int x, int y, int size) {
        graphics.fill(x + 2, y, x + size - 2, y + size, 0xF03B3D40);
        graphics.fill(x, y + 2, x + size, y + size - 2, 0xF03B3D40);
        graphics.fill(x + 2, y, x + size - 2, y + 1, 0xFF8A8D91);
        graphics.fill(x, y + 2, x + 1, y + size - 2, 0xFF8A8D91);
        graphics.fill(x + 2, y + size - 1, x + size - 2, y + size, 0xFF202226);
        graphics.fill(x + size - 1, y + 2, x + size, y + size - 2, 0xFF202226);
    }

    private static void ring(GuiGraphics graphics, int x, int y, int size, float progress, int color) {
        int visible = ActionBattleEffectIconRules.visiblePerimeterPixels(size, Math.clamp(progress, 0.0F, 1.0F));
        for (int index = 0; index < visible; index++) {
            ActionBattleEffectIconRules.Point point = ActionBattleEffectIconRules.perimeterPoint(size, index);
            graphics.fill(x + point.x(), y + point.y(), x + point.x() + 1, y + point.y() + 1, color);
        }
    }

    private static void tooltipIfHovered(GuiGraphics graphics, int x, int y, int size,
                                         ActionBattleHudPayload.StatusState state,
                                         ActionBattleTypeMechanicVisualRegistry.MechanicVisual visual) {
        Minecraft minecraft = Minecraft.getInstance();
        double scaleX = minecraft.getWindow().getGuiScaledWidth() / (double) minecraft.getWindow().getScreenWidth();
        double scaleY = minecraft.getWindow().getGuiScaledHeight() / (double) minecraft.getWindow().getScreenHeight();
        int mouseX = (int) Math.floor(minecraft.mouseHandler.xpos() * scaleX);
        int mouseY = (int) Math.floor(minecraft.mouseHandler.ypos() * scaleY);
        if (mouseX < x || mouseX >= x + size || mouseY < y || mouseY >= y + size) return;
        List<Component> lines = new ArrayList<>();
        lines.add(visual.displayName());
        lines.add(Component.translatable("action.cobblemonnml.type_mechanic.tooltip.type", visual.typeName()));
        lines.add(visual.description());
        lines.add(Component.translatable("action.cobblemonnml.type_mechanic.tooltip.activation", visual.activationDescription()));
        Component live = visual.liveState(state);
        if (!live.getString().isBlank()) {
            lines.add(Component.translatable("action.cobblemonnml.type_mechanic.tooltip.current", live));
        }
        drawTooltip(graphics, minecraft.font, lines, mouseX + 8, mouseY + 8);
    }

    private static void drawTooltip(GuiGraphics graphics, Font font, List<Component> lines, int x, int y) {
        int width = lines.stream().mapToInt(font::width).max().orElse(0) + 8;
        int height = lines.size() * 10 + 6;
        int left = Math.min(x, graphics.guiWidth() - width - 2);
        int top = Math.min(y, graphics.guiHeight() - height - 2);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        graphics.fill(left, top, left + width, top + height, 0xF0101010);
        graphics.fill(left, top, left + width, top + 1, 0xFF8A8D91);
        graphics.fill(left, top + height - 1, left + width, top + height, 0xFF202226);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font, lines.get(index), left + 4, top + 4 + index * 10,
                    index == 0 ? 0xFFFFFFFF : 0xFFD8D8D8, true);
        }
        graphics.pose().popPose();
    }
}

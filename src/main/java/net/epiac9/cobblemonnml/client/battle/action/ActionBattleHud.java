package net.epiac9.cobblemonnml.client.battle.action;

import com.cobblemon.mod.common.api.gui.GuiUtilsKt;
import com.cobblemon.mod.common.client.gui.PartyOverlay;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleHudPayload;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleBoostedMoveRules;
import net.epiac9.cobblemonnml.dimension.DungeonDimension;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public final class ActionBattleHud {
    private static final ResourceLocation BATTLE_INFO = cobblemon("textures/gui/battle/battle_info_base.png");
    private static final ResourceLocation BATTLE_INFO_FLIPPED = cobblemon("textures/gui/battle/battle_info_base_flipped.png");
    private static final ResourceLocation BATTLE_MOVE = cobblemon("textures/gui/battle/battle_move.png");
    private static final ResourceLocation BATTLE_MOVE_OVERLAY = cobblemon("textures/gui/battle/battle_move_overlay.png");
    private static final ResourceLocation TYPES = cobblemon("textures/gui/types.png");
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int PP_LOW = 0xFFFFC85A;
    private static final int PP_EMPTY = 0xFFFF6666;
    private static final int DISABLED = 0x80606060;
    private static final int COOLDOWN = 0xB3808080;
    private static final int TYPE_ICON_SIZE = 36;
    private static final int TYPE_ATLAS_WIDTH = 648;
    private static final int TYPE_ATLAS_HEIGHT = 36;
    private static final String[] KEYS = {"Z", "X", "C", "B"};
    private static final ResourceLocation ENCHANTMENT_FONT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "alt");

    private ActionBattleHud() {}

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || !ActionBattleHudClientState.isVisible()) return;
        if (!minecraft.player.level().dimension().equals(DungeonDimension.DUNGEON_DIMENSION)) {
            ActionBattleHudClientState.clear();
            return;
        }
        ActionBattleHudPayload state = ActionBattleHudClientState.get();
        Font font = minecraft.font;
        ActionBattleHudLayout layout = ActionBattleHudLayout.forScreen(graphics.guiWidth(), graphics.guiHeight());
        ActionBattleDamageHudState.RenderSnapshot playerDamage = ActionBattleHudClientState.allyDamage();
        ActionBattleDamageHudState.RenderSnapshot trainerDamage = ActionBattleHudClientState.enemyDamage();
        ActionBattleHudLayout.Rect playerPanel = layout.playerPanel();
        ActionBattleHudLayout.Rect trainerPanel = layout.trainerPanel();
        ActionBattleObscurityProjection obscurity = new ActionBattleObscurityProjection(
                state.playerObscurity().stage());
        renderPokemonPanel(graphics, font, playerPanel, false, state.playerPokemonName(), state.playerPokemonUuid(), state.playerPokemonLevel(), state.playerCurrentHp(), state.playerMaxHp(), playerDamage.trailingHp(), obscurity, ActionBattleObscurityProjection.Side.ALLY);
        renderPokemonPanel(graphics, font, trainerPanel, true, state.trainerPokemonName(), state.trainerPokemonUuid(), state.trainerPokemonLevel(), state.trainerCurrentHp(), state.trainerMaxHp(), trainerDamage.trailingHp(), obscurity, ActionBattleObscurityProjection.Side.ENEMY);
        renderStats(graphics, font, playerPanel, state.playerStatStages(), false, obscurity, ActionBattleObscurityProjection.Side.ALLY);
        renderStats(graphics, font, trainerPanel, state.trainerStatStages(), true, obscurity, ActionBattleObscurityProjection.Side.ENEMY);
        renderStatuses(graphics, playerPanel, state.playerStatuses(), false, obscurity, ActionBattleObscurityProjection.Side.ALLY);
        renderStatuses(graphics, trainerPanel, state.trainerStatuses(), true, obscurity, ActionBattleObscurityProjection.Side.ENEMY);
        renderNativePartyEffects(graphics, graphics.guiHeight(), state.playerParty(), obscurity.partyStage());
        renderNativePartyObscurity(graphics, graphics.guiHeight(), state.playerParty(),
                state.playerPokemonUuid(), obscurity.partyStage());
        ActionBattleDamageHudRenderer.renderFloating(graphics, font, playerPanel, false, playerDamage,
                obscurity.stage(ActionBattleObscurityProjection.Side.ALLY, 0));
        ActionBattleDamageHudRenderer.renderFloating(graphics, font, trainerPanel, true, trainerDamage,
                obscurity.stage(ActionBattleObscurityProjection.Side.ENEMY, 0));
        renderCommand(graphics, font, layout.commandButton(0), "Swap", "G", state.playerSwapCooldownRemainingTicks(), state.playerSwapCooldownDurationTicks(), obscurity.commandStage(6), 6);
        renderCommand(graphics, font, layout.commandButton(1), "Move Here", "V", state.playerMoveHereCooldownRemainingTicks(), state.playerMoveHereCooldownDurationTicks(), obscurity.commandStage(7), 7);
        for (int slot = 0; slot < 4; slot++) renderMove(graphics, font, layout.moveButton(slot), slot, state.move(slot), obscurity.commandStage(8 + slot));
    }

    public static ActionBattleHudLayout layoutForScreen(int width, int height) { return ActionBattleHudLayout.forScreen(width, height); }

    private static void renderPokemonPanel(GuiGraphics graphics, Font font, ActionBattleHudLayout.Rect rect,
                                           boolean flipped, String rawName, String pokemonUuid, int level,
                                           int hp, int maxHp, double trailingHp,
                                           ActionBattleObscurityProjection obscurity,
                                           ActionBattleObscurityProjection.Side side) {
        int x = rect.x();
        int y = rect.y();
        int panelStage = obscurity.stage(side, 0);
        int iconStage = Math.max(panelStage, obscurity.stage(side, 2));
        int nameStage = Math.max(panelStage, obscurity.stage(side, 3));
        int hpStage = Math.max(panelStage, obscurity.stage(side, 1));
        applyTextureTint(panelStage, 1.0F);
        graphics.blit(flipped ? BATTLE_INFO_FLIPPED : BATTLE_INFO, x, y, 0.0F, 0.0F, rect.width(), rect.height(), rect.width(), rect.height());
        resetTextureTint();
        renderPokemonPortrait(graphics, rect, flipped, pokemonUuid, iconStage);
        int infoX = flipped ? x + 7 : x + 40;
        String name = displayName(rawName);
        ActionBattleHudLayout.Rect nameBounds = ActionBattleObscurityGeometry.name(rect, flipped);
        enableScissor(graphics, nameBounds);
        drawScaled(graphics, font, obscuredText(name, nameStage), infoX, y + 7, 0.75F,
                ActionBattleObscurityHudRules.presentationColor(TEXT, nameStage), false);
        if (!ActionBattleObscurityHudRules.hideInformation(nameStage)) {
            String levelText = "Lv. " + Math.max(1, level);
            drawScaledRight(graphics, font, obscuredText(levelText, nameStage), flipped ? x + 100 : x + 137, y + 7, 0.70F,
                    ActionBattleObscurityHudRules.presentationColor(TEXT, nameStage));
        }
        graphics.disableScissor();
        double ratio = maxHp > 0 ? Math.clamp((double) hp / maxHp, 0.0D, 1.0D) : 0.0D;
        int fullWidth = 97;
        int barWidth = (int) Math.round(fullWidth * ratio);
        int barX = flipped ? infoX - 2 + (fullWidth - barWidth) : infoX - 2;
        int barColor = ActionBattleObscurityHudRules.presentationColor(hpColor(ratio), hpStage);
        if (!ActionBattleObscurityHudRules.hideInformation(hpStage)) {
            ActionBattleDamageHudRenderer.renderTrailingHp(graphics, rect, flipped, hp, maxHp, trailingHp,
                    hpStage);
        }
        if (barWidth > 0) graphics.fill(barX, y + 22, barX + barWidth, y + 26, barColor);
        if (!ActionBattleObscurityHudRules.hideInformation(hpStage)) {
            String hpText = Math.max(0, hp) + "/" + Math.max(1, maxHp);
            int centerX = flipped ? infoX + 49 : infoX + 48;
            enableScissor(graphics, ActionBattleObscurityGeometry.hpBar(rect, flipped));
            drawScaledCentered(graphics, font, obscuredText(hpText, hpStage), centerX, y + 22, 0.50F,
                    ActionBattleObscurityHudRules.presentationColor(TEXT, hpStage));
            graphics.disableScissor();
        }
        ActionBattleObscurityHudRenderer.renderSurface(graphics, ActionBattleObscurityGeometry.icon(rect, flipped), iconStage, side == ActionBattleObscurityProjection.Side.ALLY ? 2 : 102);
        ActionBattleObscurityHudRenderer.renderSurface(graphics, ActionBattleObscurityGeometry.name(rect, flipped), nameStage, side == ActionBattleObscurityProjection.Side.ALLY ? 3 : 103);
        ActionBattleObscurityHudRenderer.renderSurface(graphics, ActionBattleObscurityGeometry.hpBar(rect, flipped), hpStage, side == ActionBattleObscurityProjection.Side.ALLY ? 1 : 101);
        ActionBattleObscurityHudRenderer.renderMaskedSurface(graphics,
                ActionBattleObscurityGeometry.panel(rect), panelStage,
                side == ActionBattleObscurityProjection.Side.ALLY ? 0 : 100,
                flipped ? ActionBattleObscuritySurfaceMask.Shape.PANEL_FLIPPED
                        : ActionBattleObscuritySurfaceMask.Shape.PANEL);
    }

    private static void renderStats(GuiGraphics graphics, Font font, ActionBattleHudLayout.Rect panel,
                                    ActionBattleHudPayload.StatStageState stages, boolean flipped,
                                    ActionBattleObscurityProjection obscurity,
                                    ActionBattleObscurityProjection.Side side) {
        int stage = Math.max(obscurity.stage(side, 0), obscurity.stage(side, 5));
        ActionBattleStatStageHudRenderer.render(graphics, font, panel, stages, flipped, stage);
        ActionBattleObscurityHudRenderer.renderSurface(graphics, ActionBattleObscurityGeometry.stats(panel, flipped), stage,
                side == ActionBattleObscurityProjection.Side.ALLY ? 5 : 105);
    }

    private static void renderStatuses(GuiGraphics graphics, ActionBattleHudLayout.Rect panel,
                                       java.util.List<ActionBattleHudPayload.StatusState> statuses, boolean allyAligned,
                                       ActionBattleObscurityProjection obscurity,
                                       ActionBattleObscurityProjection.Side side) {
        int stage = Math.max(obscurity.stage(side, 0), obscurity.stage(side, 4));
        if (!ActionBattleObscurityHudRules.hideInformation(stage)) {
            boolean grayscale = ActionBattleObscurityHudRules.grayscale(stage);
            if (allyAligned) ActionBattleStatusHudRenderer.renderAlly(graphics, panel, statuses, grayscale);
            else ActionBattleStatusHudRenderer.renderEnemy(graphics, panel, statuses, grayscale);
        }
        ActionBattleObscurityHudRenderer.renderSurface(graphics, ActionBattleObscurityGeometry.statuses(panel), stage,
                side == ActionBattleObscurityProjection.Side.ALLY ? 4 : 104);
    }

    private static void renderPokemonPortrait(GuiGraphics graphics, ActionBattleHudLayout.Rect panel,
                                              boolean flipped, String pokemonUuid, int obscurityStage) {
        PokemonEntity pokemonEntity = activePokemonEntity(pokemonUuid);
        if (pokemonEntity == null) return;
        int left = flipped ? panel.x() + 106 : panel.x() + 4;
        int top = panel.y() + 4;
        int size = 30;
        graphics.enableScissor(left, top, left + size, top + size);
        graphics.pose().pushPose();
        applyTextureTint(obscurityStage, 1.0F);
        graphics.pose().translate(left + size / 2.0D, top - 11.0D, 120.0D);
        FloatingState portraitState = new FloatingState();
        portraitState.setCurrentAspects(pokemonEntity.getPokemon().getAspects());
        GuiUtilsKt.drawPosablePortrait(
                pokemonEntity.getPokemon().getSpecies().getResourceIdentifier(),
                graphics.pose(),
                13.0F,
                pokemonEntity.getPokemon().getForm().getBaseScale(),
                false,
                portraitState,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                false,
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
        graphics.pose().popPose();
        resetTextureTint();
        graphics.disableScissor();
    }

    private static void renderNativePartyEffects(GuiGraphics graphics, int guiHeight,
                                                 ActionBattleHudPayload.PartyState party,
                                                 int obscurityStage) {
        if (party == null) return;
        if (!PartyOverlay.Companion.canRender()) return;
        if (ActionBattleObscurityHudRules.hideInformation(obscurityStage)) return;
        for (ActionBattleHudPayload.PartyPokemonState pokemon : party.entries()) {
            ActionBattleHudLayout.Rect anchor = ActionBattleNativePartyLayout.effectAnchor(
                    guiHeight, 6, pokemon.partySlot());
            int rendered = 0;
            for (ActionBattleHudPayload.StatusState state : pokemon.effectStates()) {
                if (state == null || !ActionBattleStatusHudRules.shouldDisplay(
                        state.statusId(), state.remainingTicks())) continue;
                ActionBattleStatusVisualRegistry.StatusVisual visual =
                        ActionBattleStatusVisualRegistry.visualFor(state.statusId());
                if (visual == null || rendered >= 3) continue;
                int x = anchor.x() + rendered * (anchor.width() + 2);
                ActionBattleEffectIconRenderer.render(graphics, x, anchor.y(), anchor.width(),
                        state, visual, ActionBattleObscurityHudRules.grayscale(obscurityStage));
                rendered++;
            }
        }
    }

    private static void renderNativePartyObscurity(GuiGraphics graphics, int guiHeight,
                                                    ActionBattleHudPayload.PartyState party,
                                                    String activePokemonUuid,
                                                    int obscurityStage) {
        if (party == null || !PartyOverlay.Companion.canRender()) return;
        for (ActionBattleHudPayload.PartyPokemonState pokemon : party.entries()) {
            ActionBattleHudLayout.Rect slot = ActionBattleNativePartyLayout.slotBounds(
                    guiHeight, 6, pokemon.partySlot());
            ActionBattleObscuritySurfaceMask.Shape shape = pokemon.pokemonUuid().equals(activePokemonUuid)
                    ? ActionBattleObscuritySurfaceMask.Shape.PARTY_ACTIVE
                    : ActionBattleObscuritySurfaceMask.Shape.PARTY;
            ActionBattleObscurityHudRenderer.renderMaskedSurface(graphics, slot, obscurityStage,
                    200 + pokemon.partySlot(), shape);
        }
    }

    private static PokemonEntity activePokemonEntity(String pokemonUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || pokemonUuid == null || pokemonUuid.isBlank()) return null;
        UUID wanted;
        try { wanted = UUID.fromString(pokemonUuid); }
        catch (IllegalArgumentException exception) { return null; }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof PokemonEntity pokemonEntity && !pokemonEntity.isRemoved() && pokemonEntity.getPokemon().getUuid().equals(wanted)) return pokemonEntity;
        }
        return null;
    }

    private static void renderCommand(GuiGraphics graphics, Font font, ActionBattleHudLayout.Rect rect,
                                      String label, String key, long cooldownRemainingTicks,
                                      long cooldownDurationTicks, int obscurityStage, int seed) {
        int x = rect.x();
        int y = rect.y();
        RenderSystem.setShaderColor(0.68F, 0.68F, 0.68F, 1.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(rect.width() / 92.0F, rect.height() / 24.0F, 1.0F);
        graphics.blit(BATTLE_MOVE, 0, 0, 0.0F, 0.0F, 92, 24, 92, 48);
        graphics.blit(BATTLE_MOVE_OVERLAY, 0, 0, 0.0F, 0.0F, 92, 24, 92, 24);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (!ActionBattleObscurityHudRules.hideInformation(obscurityStage) && cooldownRemainingTicks > 0L && cooldownDurationTicks > 0L) {
            double elapsedFraction = 1.0D - Math.clamp((double) cooldownRemainingTicks / cooldownDurationTicks, 0.0D, 1.0D);
            renderCommandCooldownFill(graphics, rect, elapsedFraction);
        }
        int textColor = ActionBattleObscurityHudRules.presentationColor(TEXT, obscurityStage);
        enableScissor(graphics, rect);
        if (label.contains(" ")) {
            String[] parts = label.split(" ", 2);
            drawScaledCentered(graphics, font, obscuredText(parts[0], obscurityStage), x + rect.width() / 2, y + 2, 0.38F, textColor);
            drawScaledCentered(graphics, font, obscuredText(parts[1], obscurityStage), x + rect.width() / 2, y + 7, 0.38F, textColor);
            if (!ActionBattleObscurityHudRules.hideInformation(obscurityStage)) drawScaledCentered(graphics, font, obscuredText(key, obscurityStage), x + rect.width() / 2, y + 14, 0.48F, textColor);
        } else {
            drawScaledCentered(graphics, font, obscuredText(label, obscurityStage), x + rect.width() / 2, y + 4, 0.42F, textColor);
            if (!ActionBattleObscurityHudRules.hideInformation(obscurityStage)) drawScaledCentered(graphics, font, obscuredText(key, obscurityStage), x + rect.width() / 2, y + 13, 0.50F, textColor);
        }
        graphics.disableScissor();
        ActionBattleObscurityHudRenderer.renderMaskedSurface(graphics, rect, obscurityStage, seed,
                ActionBattleObscuritySurfaceMask.Shape.MOVE);
    }

    private static void renderCommandCooldownFill(GuiGraphics graphics, ActionBattleHudLayout.Rect rect, double fraction) {
        int filledHeight = (int) Math.ceil(rect.height() * fraction);
        if (filledHeight <= 0) return;
        int sourceY = rect.height() - filledHeight;
        float alpha = ((COOLDOWN >>> 24) & 255) / 255.0F;
        float gray = 128.0F / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(gray, gray, gray, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(rect.x(), rect.y() + sourceY, 0.0F);
        graphics.pose().scale(rect.width() / 92.0F, rect.height() / 24.0F, 1.0F);
        graphics.blit(BATTLE_MOVE, 0, 0, 0.0F, (float) sourceY * 24.0F / rect.height(), 92, (int) Math.ceil(filledHeight * 24.0F / rect.height()), 92, 48);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static void renderMove(GuiGraphics graphics, Font font, ActionBattleHudLayout.Rect rect, int slot,
                                   ActionBattleHudPayload.MoveState move, int obscurityStage) {
        int x = rect.x();
        int y = rect.y();
        boolean missing = move.name() == null || move.name().isBlank();
        boolean disabled = missing || !move.supported() || move.currentPp() <= 0;
        float[] tint = typeTint(move.type());
        if (ActionBattleObscurityHudRules.grayscale(obscurityStage)) {
            float gray = (tint[0] + tint[1] + tint[2]) / 3.0F;
            tint = new float[]{gray, gray, gray};
        }
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], disabled ? 0.50F : 1.0F);
        graphics.blit(BATTLE_MOVE, x, y, 0.0F, 0.0F, rect.width(), rect.height(), rect.width(), rect.height() * 2);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(BATTLE_MOVE_OVERLAY, x, y, 0.0F, 0.0F, rect.width(), rect.height(), rect.width(), rect.height());
        String name = missing ? "---" : displayName(move.name());
        enableScissor(graphics, rect);
        drawScaled(graphics, font, obscuredText(name, obscurityStage), x + 17, y + 3, 0.72F,
                ActionBattleObscurityHudRules.presentationColor(disabled ? MUTED : TEXT, obscurityStage), false);
        int ppColor = move.currentPp() <= 0 ? PP_EMPTY : move.maxPp() > 0 && move.currentPp() * 2 <= move.maxPp() ? PP_LOW : TEXT;
        String pp = move.maxPp() > 0 ? move.currentPp() + "/" + move.maxPp() : "--/--";
        if (!ActionBattleObscurityHudRules.hideInformation(obscurityStage)) {
            drawScaledCentered(graphics, font, obscuredText(pp, obscurityStage), x + 75, y + 15, 0.58F,
                    ActionBattleObscurityHudRules.presentationColor(ppColor, obscurityStage));
            drawScaledRight(graphics, font, obscuredText(KEYS[slot], obscurityStage), x + 89, y + 3, 0.55F,
                    ActionBattleObscurityHudRules.presentationColor(disabled ? MUTED : TEXT, obscurityStage));
        }
        String momentum = ActionBattleFlyingHudRules.label(move.type(), move.flyingMomentum(), obscurityStage);
        if (!momentum.isEmpty()) {
            drawScaledRight(graphics, font, obscuredText(momentum, obscurityStage), x + 74, y + 3, 0.52F,
                    ActionBattleFlyingHudRules.color(move.type(), move.flyingMomentum(), obscurityStage));
            if (ActionBattleFlyingHudRules.showLockMarker(move.type(), move.flyingMomentum(), obscurityStage)) {
                renderMomentumReticle(graphics, x + 76, y + 3);
            }
        }
        graphics.disableScissor();
        if (disabled) graphics.fill(x, y, x + rect.width(), y + rect.height(), DISABLED);
        if (!ActionBattleObscurityHudRules.hideInformation(obscurityStage) && move.cooldownRemainingTicks() > 0L && move.cooldownDurationTicks() > 0L) {
            double elapsedFraction = 1.0D - Math.clamp((double) move.cooldownRemainingTicks() / move.cooldownDurationTicks(), 0.0D, 1.0D);
            renderCooldownFill(graphics, rect, elapsedFraction);
        }
        if (ActionBattleObscurityHudRules.showsAuxiliaryIcon(obscurityStage)) {
            if (ActionBattleBoostedMoveRules.shouldRender(move.mechanicallyBoosted(), obscurityStage)) {
                renderBoostedMoveAura(graphics, x - 9, y + 2, move.type(), obscurityStage,
                        Util.getMillis());
            }
            renderTypeIcon(graphics, x - 9, y + 2, move.type(), disabled ? 0.55F : 1.0F,
                    obscurityStage);
        }
        ActionBattleObscurityHudRenderer.renderMaskedSurface(graphics, rect, obscurityStage, 8 + slot,
                ActionBattleObscuritySurfaceMask.Shape.MOVE);
    }

    private static void renderBoostedMoveAura(GuiGraphics graphics, int x, int y, String type,
                                               int obscurityStage, long animationMillis) {
        ActionBattleBoostedAuraVisualRules.AnimationFrame frame =
                ActionBattleBoostedAuraVisualRules.animationFrame(animationMillis);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        renderBoostedAuraLayer(graphics, x, y, type, obscurityStage,
                ActionBattleBoostedAuraVisualRules.outerSpans(), frame.outerAlpha(), frame.outerScale());
        renderBoostedAuraLayer(graphics, x, y, type, obscurityStage,
                ActionBattleBoostedAuraVisualRules.innerSpans(), frame.innerAlpha(), 1.0F);
        RenderSystem.disableBlend();
    }

    private static void renderBoostedAuraLayer(GuiGraphics graphics, int x, int y, String type,
                                                int obscurityStage,
                                                java.util.List<ActionBattleBoostedAuraVisualRules.Span> spans,
                                                int layerAlpha, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x + ActionBattleBoostedAuraVisualRules.ICON_CENTER,
                y + ActionBattleBoostedAuraVisualRules.ICON_CENTER, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-x - ActionBattleBoostedAuraVisualRules.ICON_CENTER,
                -y - ActionBattleBoostedAuraVisualRules.ICON_CENTER, 0.0F);
        for (ActionBattleBoostedAuraVisualRules.Span span : spans) {
            int alpha = ActionBattleBoostedAuraVisualRules.scaledAlpha(span.alpha(), layerAlpha);
            graphics.fill(x + span.startX(), y + span.y(), x + span.endX(), y + span.y() + 1,
                    ActionBattleBoostedAuraVisualRules.color(type, alpha, obscurityStage));
        }
        graphics.pose().popPose();
    }

    private static void renderMomentumReticle(GuiGraphics graphics, int x, int y) {
        int color = ActionBattleFlyingHudRules.MAX_COLOR;
        graphics.fill(x, y, x + 3, y + 1, color);
        graphics.fill(x, y, x + 1, y + 3, color);
        graphics.fill(x + 5, y, x + 8, y + 1, color);
        graphics.fill(x + 7, y, x + 8, y + 3, color);
    }

    private static void renderTypeIcon(GuiGraphics graphics, int x, int y, String type, float alpha,
                                       int obscurityStage) {
        int index = typeIndex(type);
        float channel = ActionBattleObscurityHudRules.grayscale(obscurityStage) ? 0.58F : 1.0F;
        RenderSystem.setShaderColor(channel, channel, channel, alpha);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.blit(TYPES, 0, 0, (float) (index * TYPE_ICON_SIZE), 0.0F, TYPE_ICON_SIZE, TYPE_ICON_SIZE, TYPE_ATLAS_WIDTH, TYPE_ATLAS_HEIGHT);
        graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderCooldownFill(GuiGraphics graphics, ActionBattleHudLayout.Rect rect, double fraction) {
        int x = rect.x();
        int y = rect.y();
        int width = rect.width();
        int height = rect.height();
        int filledHeight = (int) Math.ceil(height * fraction);
        if (filledHeight <= 0) return;
        int sourceY = height - filledHeight;
        float alpha = ((COOLDOWN >>> 24) & 255) / 255.0F;
        float gray = 128.0F / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(gray, gray, gray, alpha);
        graphics.blit(BATTLE_MOVE, x, y + sourceY, 0.0F, (float) sourceY, width, filledHeight, width, height * 2);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static int hpColor(double ratio) {
        if (ratio <= 0.20D) return 0xFFD9534F;
        if (ratio <= 0.50D) return 0xFFE4C34A;
        return 0xFF62C45B;
    }

    private static float[] typeTint(String type) {
        int rgb = ActionBattleBoostedAuraVisualRules.rgb(type);
        return new float[]{((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F, (rgb & 255) / 255.0F};
    }

    private static int typeIndex(String type) {
        return switch (normalize(type)) {
            case "fire" -> 1; case "water" -> 2; case "grass" -> 3; case "electric" -> 4; case "ice" -> 5; case "fighting" -> 6;
            case "poison" -> 7; case "ground" -> 8; case "flying" -> 9; case "psychic" -> 10; case "bug" -> 11; case "rock" -> 12;
            case "ghost" -> 13; case "dragon" -> 14; case "dark" -> 15; case "steel" -> 16; case "fairy" -> 17; default -> 0;
        };
    }

    private static void drawScaled(GuiGraphics graphics, Font font, String text, int x, int y, float scale, int color, boolean shadow) {
        drawScaled(graphics, font, Component.literal(text), x, y, scale, color, shadow);
    }

    private static void drawScaled(GuiGraphics graphics, Font font, Component text, int x, int y, float scale, int color, boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private static void drawScaledRight(GuiGraphics graphics, Font font, String text, int rightX, int y, float scale, int color) {
        drawScaledRight(graphics, font, Component.literal(text), rightX, y, scale, color);
    }

    private static void drawScaledRight(GuiGraphics graphics, Font font, Component text, int rightX, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(rightX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text), 0, color, true);
        graphics.pose().popPose();
    }

    private static void drawScaledCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, float scale, int color) {
        drawScaledCentered(graphics, font, Component.literal(text), centerX, y, scale, color);
    }

    private static void drawScaledCentered(GuiGraphics graphics, Font font, Component text, int centerX, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, true);
        graphics.pose().popPose();
    }

    private static ResourceLocation cobblemon(String path) { return ResourceLocation.fromNamespaceAndPath("cobblemon", path); }
    private static String normalize(String value) { return value == null ? "normal" : value.trim().toLowerCase(); }
    private static String displayName(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String spaced = value.replace('_', ' ');
        StringBuilder out = new StringBuilder(spaced.length());
        boolean upper = true;
        for (char c : spaced.toCharArray()) {
            if (c == ' ') { upper = true; out.append(c); }
            else { out.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return out.toString();
    }

    private static Component obscuredText(String text, int stage) {
        Component result = Component.literal(text != null ? text : "");
        return ActionBattleObscurityHudRules.usesMinecraftAltFont(stage)
                ? result.copy().withStyle(Style.EMPTY.withFont(ENCHANTMENT_FONT))
                : result;
    }

    private static void applyTextureTint(int stage, float alpha) {
        float channel = ActionBattleObscurityHudRules.grayscale(stage) ? 0.58F : 1.0F;
        RenderSystem.setShaderColor(channel, channel, channel, alpha);
    }

    private static void resetTextureTint() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void enableScissor(GuiGraphics graphics, ActionBattleHudLayout.Rect rect) {
        graphics.enableScissor(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height());
    }
}

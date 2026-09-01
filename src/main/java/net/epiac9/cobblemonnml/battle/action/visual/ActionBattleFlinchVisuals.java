package net.epiac9.cobblemonnml.battle.action.visual;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleFlinchVisualPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ActionBattleFlinchVisuals {
    private ActionBattleFlinchVisuals() {}

    public static void emit(PokemonEntity entity, ActionBattleFlinchVisualType visualType) {
        if (entity == null || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) return;
        ActionBattleFlinchVisualType type = visualType != null ? visualType : ActionBattleFlinchVisualType.NORMAL;
        if (type == ActionBattleFlinchVisualType.PARALYSIS) ActionBattleStatusParticleController.emitParalysisTriggerBurst(level, entity);
        else emitNormalImpactBurst(level, entity);
        ActionBattleFlinchVisualPayload payload = new ActionBattleFlinchVisualPayload(entity.getId(), entity.getPokemon().getUuid().toString(), type.name());
        for (ServerPlayer player : level.players()) PacketDistributor.sendToPlayer(player, payload);
    }

    private static void emitNormalImpactBurst(ServerLevel level, PokemonEntity entity) {
        AABB box = entity.getBoundingBox();
        double cx = box.getCenter().x, cy = box.getCenter().y + box.getYsize() * 0.15D, cz = box.getCenter().z;
        level.sendParticles(ParticleTypes.CRIT, cx, cy, cz, 10, Math.max(0.20D, box.getXsize() * 0.40D), Math.max(0.25D, box.getYsize() * 0.35D), Math.max(0.20D, box.getZsize() * 0.40D), 0.12D);
        level.sendParticles(ParticleTypes.POOF, cx, cy, cz, 4, Math.max(0.15D, box.getXsize() * 0.25D), Math.max(0.20D, box.getYsize() * 0.25D), Math.max(0.15D, box.getZsize() * 0.25D), 0.02D);
    }
}

package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.network.ActionBattleFlinchVisualPayload;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleFlinchVisualType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

final class ActionBattleElectricParticleVisuals {
    private ActionBattleElectricParticleVisuals() {}

    static void emitSubtleStatic(Object rawEntity) {
        if (!(rawEntity instanceof PokemonEntity entity) || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) return;
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, box.getCenter().x, box.getCenter().y,
                box.getCenter().z, 4, 0.12D, 0.18D, 0.12D, 0.02D);
    }

    static void emitParalysisFlinch(Object rawEntity) {
        if (!(rawEntity instanceof PokemonEntity entity) || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) return;
        AABB box = entity.getBoundingBox();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, box.getCenter().x, box.getCenter().y,
                box.getCenter().z, 20, 0.35D, 0.40D, 0.35D, 0.10D);
        ActionBattleFlinchVisualPayload payload = new ActionBattleFlinchVisualPayload(
                entity.getId(), entity.getPokemon().getUuid().toString(), ActionBattleFlinchVisualType.ELECTRIC_PARALYSIS.name());
        for (ServerPlayer player : level.players()) PacketDistributor.sendToPlayer(player, payload);
    }
}
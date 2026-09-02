package net.epiac9.cobblemonnml.battle.action.move;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleConfusionController;
import net.epiac9.cobblemonnml.battle.action.ActionBattlePosition;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

final class ActionBattleAreaEffectSupport {
    private ActionBattleAreaEffectSupport() {}

    static ActionBattlePosition positionOf(PokemonEntity entity) {
        return entity == null ? null : new ActionBattlePosition(entity.getX(), entity.getY(), entity.getZ());
    }

    static ActionBattlePosition positionOf(Vec3 position) {
        return position == null ? null : new ActionBattlePosition(position.x, position.y, position.z);
    }

    static ActionBattlePosition targetPosition(PokemonEntity caster, PokemonEntity target, long confusionBonusTicks) {
        if (confusionBonusTicks > 0L) return positionOf(ActionBattleConfusionController.randomMoveTarget(caster));
        return positionOf(target);
    }

    static int totalChannelTicks(long confusionBonusTicks) {
        return (int) Math.min(Integer.MAX_VALUE, 40L + confusionBonusTicks);
    }

    static int cancelAtElapsedTick(PokemonEntity caster, int totalChannelTicks, boolean confusionSelfCancel) {
        if (!confusionSelfCancel) return -1;
        return Math.max(1, Math.min(totalChannelTicks - 1, 1 + caster.getRandom().nextInt(Math.max(1, totalChannelTicks - 1))));
    }

    static PokemonEntity activePokemonEntity(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        if (session == null || level == null || pokemonUUID == null) return null;
        UUID entityUUID = null;
        if (pokemonUUID.equals(session.playerActivePokemonUUID())) entityUUID = session.playerActiveEntityUUID();
        else if (pokemonUUID.equals(session.trainerActivePokemonUUID())) entityUUID = session.trainerActiveEntityUUID();
        Entity raw = entityUUID != null ? level.getEntity(entityUUID) : null;
        return raw instanceof PokemonEntity pokemonEntity ? pokemonEntity : null;
    }
}

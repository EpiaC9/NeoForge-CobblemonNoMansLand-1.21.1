package net.epiac9.cobblemonnml.battle.action.typeeffect;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveDescriptor;
import net.epiac9.cobblemonnml.battle.action.move.ActionBattleMoveMetadataResolver;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionMoveDeliveryType;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public record ActionBattleTypeMechanicActionContext(
        PokemonEntity pokemon,
        Move move,
        String effectiveMoveType,
        MoveCategory moveCategory,
        TargetingMode targetingMode,
        LivingEntity target,
        ActionMoveDeliveryType deliveryType,
        boolean committed,
        UUID battleId,
        UUID arenaSessionId,
        boolean mechanicSecondary
) {
    public enum MoveCategory { MELEE, PROJECTILE, STATUS }
    public enum TargetingMode { TARGET, SELF_OR_ALLY }

    public static ActionBattleTypeMechanicActionContext started(PokemonEntity pokemon, LivingEntity target,
                                                                Move move) {
        ActionBattleSession session = pokemon != null
                ? ActionBattleManager.findSessionForBattlePokemonEntity(pokemon.getUUID()) : null;
        ActionBattleMoveDescriptor descriptor = ActionBattleMoveMetadataResolver.resolve(pokemon, move);
        return new ActionBattleTypeMechanicActionContext(
                pokemon,
                move,
                descriptor.effectiveType(),
                descriptor.damageCategory() == ActionBattleMoveDescriptor.DamageCategory.STATUS ? MoveCategory.STATUS
                        : isMeleeDelivery(descriptor.deliveryType()) ? MoveCategory.MELEE : MoveCategory.PROJECTILE,
                descriptor.targetingMode() == ActionBattleMoveDescriptor.TargetingMode.SELF_OR_ALLY
                        ? TargetingMode.SELF_OR_ALLY : TargetingMode.TARGET,
                target,
                descriptor.deliveryType(),
                true,
                session != null ? session.battleId() : null,
                session != null ? session.dungeonSessionId() : null,
                false
        );
    }

    private static boolean isMeleeDelivery(ActionMoveDeliveryType deliveryType) {
        return deliveryType == ActionMoveDeliveryType.PHYSICAL_CONTACT
                || deliveryType == ActionMoveDeliveryType.DASH_RUSH;
    }

    public ActionBattleTypeMechanicActionContext asMechanicSecondary() {
        return new ActionBattleTypeMechanicActionContext(pokemon, move, effectiveMoveType, moveCategory,
                targetingMode, target, deliveryType, committed, battleId, arenaSessionId, true);
    }
}

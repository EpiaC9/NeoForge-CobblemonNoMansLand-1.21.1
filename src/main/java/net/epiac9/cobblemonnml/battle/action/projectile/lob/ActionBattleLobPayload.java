package net.epiac9.cobblemonnml.battle.action.projectile.lob;

import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;
import net.minecraft.core.BlockPos;
import java.util.UUID;

public record ActionBattleLobPayload(PayloadKind kind, UUID sessionId, UUID battleId,
        UUID ownerPokemonId, ActionBattleFieldObject.OwnerSide ownerSide,
        BlockPos anchor, BlockPos intendedLanding) {
    public enum PayloadKind { AQUA_BUBBLE, GRASS_SEED }
    public ActionBattleLobPayload {
        if (kind == null || sessionId == null || battleId == null || ownerPokemonId == null
                || ownerSide == null || anchor == null || intendedLanding == null) {
            throw new IllegalArgumentException("Lob payload requires complete persistent context.");
        }
    }
}

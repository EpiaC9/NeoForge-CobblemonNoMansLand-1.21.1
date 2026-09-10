package net.epiac9.cobblemonnml.battle.action.typeeffect.rock;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public final class ActionBattleRockConstructRuntime {
    public static final long TARGET_LIFETIME_TICKS = 180L;
    private ActionBattleRockConstructRuntime() {}

    public static void onOwnedActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.battleId() == null || context.pokemon() == null
                || !(context.pokemon().level() instanceof ServerLevel level)) return;
        boolean self = context.targetingMode() == ActionBattleTypeMechanicActionContext.TargetingMode.SELF_OR_ALLY;
        BlockPos anchor = BlockPos.containing(self || context.target() == null
                ? context.pokemon().position() : context.target().position());
        BlockPos placed = findPlacement(level, anchor);
        if (placed == null || !level.setBlock(placed, ModBlocks.ACTION_BATTLE_ROCK_CONSTRUCT.get().defaultBlockState(), 3)
                || !(level.getBlockEntity(placed) instanceof ActionBattleRockConstructBlockEntity construct)) return;
        construct.initialize(context.battleId(), context.pokemon().getUUID(),
                context.pokemon().getPokemon().getUuid(), level.getGameTime()
                + (self ? 1L : TARGET_LIFETIME_TICKS), self);
    }

    public static ActionBattleRockConstructBlockEntity firstCollision(ServerLevel level, AABB swept, UUID battleId) {
        if (level == null || swept == null || battleId == null) return null;
        BlockPos min = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
        BlockPos max = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);
        for (int x = min.getX(); x <= max.getX(); x++) for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof ActionBattleRockConstructBlockEntity value
                        && battleId.equals(value.battleId())) return value;
            }
        }
        return null;
    }

    public static boolean blocksMelee(PokemonEntity attacker, PokemonEntity target) {
        if (!(attacker.level() instanceof ServerLevel level) || target == null) return false;
        UUID battleId = net.epiac9.cobblemonnml.battle.action.ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
        AABB path = new AABB(attacker.getEyePosition(), target.getEyePosition()).inflate(0.35D);
        return firstCollision(level, path, battleId) != null;
    }

    public static PokemonEntity owner(ServerLevel level, ActionBattleRockConstructBlockEntity construct) {
        Entity value = construct != null && construct.ownerEntityId() != null ? level.getEntity(construct.ownerEntityId()) : null;
        return value instanceof PokemonEntity pokemon ? pokemon : null;
    }

    private static BlockPos findPlacement(ServerLevel level, BlockPos anchor) {
        for (int radius = 0; radius <= 2; radius++) for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos pos = anchor.offset(x, 0, z);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(
                        level, pos.below(), net.minecraft.core.Direction.UP)) return pos;
            }
        }
        return null;
    }
}

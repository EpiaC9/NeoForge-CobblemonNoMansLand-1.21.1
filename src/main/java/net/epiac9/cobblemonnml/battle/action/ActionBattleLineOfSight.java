package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ActionBattleLineOfSight {
    private ActionBattleLineOfSight() {}

    public static ActionBattleTargetingRules.VisibilityResult evaluate(
            ActionBattleSession session, PokemonEntity attacker, PokemonEntity target) {
        if (session == null || attacker == null || target == null
                || attacker.isRemoved() || target.isRemoved() || !target.isAlive()
                || attacker.level() != target.level() || !(attacker.level() instanceof ServerLevel level)) {
            return ActionBattleTargetingRules.VisibilityResult.blocked();
        }
        Vec3 origin = headPosition(attacker);
        Vec3 facing = attacker.getLookAngle();
        AABB box = target.getBoundingBox();
        boolean insideArena = session.arena() != null
                ? session.arena().active() && session.arena().contains(target.getX(), target.getZ())
                : session.containsArena(target.getX(), target.getZ());
        return ActionBattleTargetingRules.evaluateVisibility(
                point(origin), point(facing), hitbox(box), insideArena,
                (from, to) -> clear(level, attacker, vec(from), vec(to)),
                ActionBattleEvasionController.isEvading(target, level.getGameTime())
        );
    }

    private static Vec3 headPosition(PokemonEntity attacker) {
        Vec3 eye = attacker.getEyePosition();
        if (finite(eye)) return eye;
        AABB box = attacker.getBoundingBox();
        return new Vec3((box.minX + box.maxX) * 0.5D,
                box.minY + box.getYsize() * 0.80D,
                (box.minZ + box.maxZ) * 0.5D);
    }

    private static boolean clear(ServerLevel level, PokemonEntity attacker, Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static ActionBattleTargetingRules.Point point(Vec3 value) {
        return new ActionBattleTargetingRules.Point(value.x, value.y, value.z);
    }

    private static Vec3 vec(ActionBattleTargetingRules.Point value) {
        return new Vec3(value.x(), value.y(), value.z());
    }

    private static ActionBattleTargetingRules.Hitbox hitbox(AABB value) {
        return new ActionBattleTargetingRules.Hitbox(
                value.minX, value.minY, value.minZ, value.maxX, value.maxY, value.maxZ);
    }
}

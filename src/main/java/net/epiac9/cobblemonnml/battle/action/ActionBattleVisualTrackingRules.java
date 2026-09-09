package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ActionBattleVisualTrackingRules {
    public static final float NORMAL_TURN_SPEED = 30.0F;
    public static final float CORRECTIVE_TURN_SPEED = 60.0F;
    private static final double COMFORT_CONE_COSINE = 0.5D;

    private ActionBattleVisualTrackingRules() {}

    public static double awarenessMultiplier(boolean flyingPokemon, boolean airborne, double lookY) {
        if (!flyingPokemon || !airborne || !Double.isFinite(lookY)) return 1.0D;
        return 1.0D + Math.clamp(-lookY, 0.0D, 1.0D);
    }

    public static boolean shouldApplyVisualTracking(boolean directionalMovementIntent,
                                                    boolean propulsionActive) {
        return !directionalMovementIntent && !propulsionActive;
    }

    public static float bodyYaw(ActionBattleTargetingRules.Point origin,
                                ActionBattleTargetingRules.Point targetCenter) {
        if (origin == null || targetCenter == null) return 0.0F;
        double dx = targetCenter.x() - origin.x();
        double dz = targetCenter.z() - origin.z();
        if (dx * dx + dz * dz <= 1.0E-18D) return 0.0F;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return yaw == 0.0F ? 0.0F : yaw;
    }

    public static float approachBodyYaw(float currentYaw, float targetYaw, float maximumChange) {
        if (!Float.isFinite(currentYaw) || !Float.isFinite(targetYaw)
                || !Float.isFinite(maximumChange) || maximumChange < 0.0F) return currentYaw;
        float delta = wrapDegrees(targetYaw - currentYaw);
        return currentYaw + Math.clamp(delta, -maximumChange, maximumChange);
    }

    public static float turnSpeed(ActionBattleTargetingRules.Point origin,
                                  ActionBattleTargetingRules.Point facing,
                                  ActionBattleTargetingRules.Point targetCenter) {
        if (origin == null || facing == null || targetCenter == null) return NORMAL_TURN_SPEED;
        double facingLength = Math.sqrt(facing.x() * facing.x() + facing.z() * facing.z());
        double dx = targetCenter.x() - origin.x();
        double dz = targetCenter.z() - origin.z();
        double targetLength = Math.sqrt(dx * dx + dz * dz);
        if (facingLength <= 1.0E-9D || targetLength <= 1.0E-9D) return NORMAL_TURN_SPEED;
        double cosine = (facing.x() * dx + facing.z() * dz) / (facingLength * targetLength);
        return cosine + 1.0E-9D >= COMFORT_CONE_COSINE ? NORMAL_TURN_SPEED : CORRECTIVE_TURN_SPEED;
    }

    public static ActionBattleTargetingRules.Point targetCenter(ActionBattleTargetingRules.Hitbox hitbox) {
        if (hitbox == null) return null;
        return new ActionBattleTargetingRules.Point(
                (hitbox.minX() + hitbox.maxX()) * 0.5D,
                (hitbox.minY() + hitbox.maxY()) * 0.5D,
                (hitbox.minZ() + hitbox.maxZ()) * 0.5D);
    }

    public static TrackingPlan plan(ActionBattleTargetingRules.Point origin,
                                    ActionBattleTargetingRules.Point facing,
                                    ActionBattleTargetingRules.Point targetCenter) {
        float speed = turnSpeed(origin, facing, targetCenter);
        return new TrackingPlan(targetCenter, speed, speed);
    }

    public static void faceTarget(PokemonEntity observer, Entity target) {
        if (observer == null || target == null || observer.isRemoved() || target.isRemoved()) return;
        Vec3 origin = observer.getEyePosition();
        Vec3 facing = observer.getLookAngle();
        AABB box = target.getBoundingBox();
        TrackingPlan plan = plan(
                point(origin),
                point(facing),
                targetCenter(new ActionBattleTargetingRules.Hitbox(
                        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)));
        observer.getLookControl().setLookAt(plan.aim().x(), plan.aim().y(), plan.aim().z(),
                plan.yawSpeed(), plan.pitchSpeed());
        float bodyYaw = approachBodyYaw(observer.getYRot(),
                bodyYaw(point(observer.position()), plan.aim()), plan.yawSpeed());
        observer.setYRot(bodyYaw);
        observer.yBodyRot = bodyYaw;
    }

    private static ActionBattleTargetingRules.Point point(Vec3 value) {
        return new ActionBattleTargetingRules.Point(value.x, value.y, value.z);
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    public record TrackingPlan(ActionBattleTargetingRules.Point aim, float yawSpeed, float pitchSpeed) {}
}

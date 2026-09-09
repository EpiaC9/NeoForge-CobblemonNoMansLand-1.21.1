package net.epiac9.cobblemonnml.battle.action.typeeffect.flying;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattlePropulsionController {
    private static final Map<UUID, ActivePropulsion> ACTIVE = new HashMap<>();

    private ActionBattlePropulsionController() {}

    public static boolean canLaunch(ActionBattleSession session, PokemonEntity attacker,
                                    PokemonEntity target, int momentum) {
        return createPlan(session, attacker, target, momentum) != null;
    }

    public static boolean launch(ActionBattleSession session, PokemonEntity attacker,
                                 PokemonEntity target, int momentum, Runnable impact) {
        if (impact == null) return false;
        PreparedPlan prepared = createPlan(session, attacker, target, momentum);
        if (prepared == null) return false;
        UUID pokemonId = attacker.getPokemon().getUuid();
        clearEntry(ACTIVE.remove(pokemonId));
        attacker.getNavigation().stop();
        boolean originalNoGravity = attacker.isNoGravity();
        attacker.setNoGravity(true);
        attacker.setDeltaMovement(Vec3.ZERO);
        ACTIVE.put(pokemonId, new ActivePropulsion(session.battleId(), attacker.getUUID(),
                target.getUUID(), attacker, originalNoGravity,
                new ActionBattlePropulsionState(prepared.plan, prepared.speed), impact,
                prepared.mode, prepared.lockedTarget));
        DebugLog.log("[CobblemonNML] Flying propulsion launched. Battle=" + session.battleId()
                + ", pokemon=" + pokemonId + ", mode=" + prepared.mode
                + ", speedBlocksPerSecond=" + (prepared.speed * 20.0D)
                + ", lockedTarget=" + prepared.lockedTarget
                + ", shortened=" + prepared.plan.shortened());
        return true;
    }

    public static void tickBattle(ActionBattleSession session, ServerLevel level) {
        if (session == null || level == null) return;
        for (Map.Entry<UUID, ActivePropulsion> active : new ArrayList<>(ACTIVE.entrySet())) {
            ActivePropulsion entry = active.getValue();
            if (!session.battleId().equals(entry.battleId)) continue;
            if (tickState(session, level, active.getKey(), entry)) {
                ACTIVE.remove(active.getKey());
                clearEntry(entry);
            }
        }
    }

    public static boolean isActive(ActionBattleSession session, UUID pokemonId) {
        if (session == null || pokemonId == null) return false;
        ActivePropulsion entry = ACTIVE.get(pokemonId);
        return entry != null && session.battleId().equals(entry.battleId);
    }

    public static void clearPokemon(ActionBattleSession session, UUID pokemonId) {
        if (session == null || pokemonId == null) return;
        ActivePropulsion entry = ACTIVE.get(pokemonId);
        if (entry != null && session.battleId().equals(entry.battleId)) {
            ACTIVE.remove(pokemonId);
            clearEntry(entry);
        }
    }

    public static void clearBattle(UUID battleId) {
        if (battleId == null) return;
        for (Map.Entry<UUID, ActivePropulsion> active : new ArrayList<>(ACTIVE.entrySet())) {
            if (!battleId.equals(active.getValue().battleId)) continue;
            ACTIVE.remove(active.getKey());
            clearEntry(active.getValue());
        }
    }

    public static void clearAll() {
        for (ActivePropulsion entry : ACTIVE.values()) clearEntry(entry);
        ACTIVE.clear();
    }

    private static boolean tickState(ActionBattleSession session, ServerLevel level,
                                     UUID pokemonId, ActivePropulsion entry) {
        Entity rawAttacker = level.getEntity(entry.attackerEntityId);
        if (!(rawAttacker instanceof PokemonEntity attacker) || attacker.isRemoved() || !attacker.isAlive()
                || attacker != entry.attacker) return true;
        Entity rawTarget = level.getEntity(entry.targetEntityId);
        PokemonEntity target = rawTarget instanceof PokemonEntity pokemonTarget
                && pokemonTarget.isAlive() && !pokemonTarget.isRemoved() ? pokemonTarget : null;
        Vec3 current = attacker.position();
        AABB currentBox = attacker.getBoundingBox();
        ActionBattlePropulsionState.Step step = entry.state.advance(point(current),
                segment -> safeSegment(session, level, attacker, current, currentBox,
                        vec(segment.from()), vec(segment.to())),
                segment -> target != null && sweptBox(current, currentBox,
                        vec(segment.from()), vec(segment.to())).intersects(target.getBoundingBox()));
        Vec3 destination = vec(step.position());
        Vec3 movement = destination.subtract(current);
        if (movement.lengthSqr() > 1.0E-12D) {
            faceMovement(attacker, movement);
            attacker.move(MoverType.SELF, movement);
        }
        attacker.setDeltaMovement(Vec3.ZERO);
        if (step.event() == ActionBattlePropulsionState.Event.CONTACT) {
            entry.impact.run();
            DebugLog.log("[CobblemonNML] Flying propulsion contact. Battle=" + session.battleId()
                    + ", pokemon=" + pokemonId + ", mode=" + entry.mode);
        }
        if (!step.terminal()) return false;
        DebugLog.log("[CobblemonNML] Flying propulsion ended. Battle=" + session.battleId()
                + ", pokemon=" + pokemonId + ", mode=" + entry.mode
                + ", result=" + step.event() + ", lockedTarget=" + entry.lockedTarget);
        return true;
    }

    private static PreparedPlan createPlan(ActionBattleSession session, PokemonEntity attacker,
                                           PokemonEntity target, int momentum) {
        if (session == null || attacker == null || target == null || attacker.isRemoved()
                || target.isRemoved() || !target.isAlive()
                || !(attacker.level() instanceof ServerLevel level)
                || target.level() != level) return null;
        double speed = ActionBattlePropulsionRules.speedBlocksPerTick(momentum);
        if (speed <= 0.0D) return null;
        ActionBattlePropulsionRules.Mode mode = ActionBattleFlyingRules.isFlyingPokemon(attacker.getPokemon())
                ? ActionBattlePropulsionRules.Mode.BELL : ActionBattlePropulsionRules.Mode.STRAIGHT;
        Vec3 start = attacker.position();
        Vec3 lockedTarget = lockedImpactPoint(attacker, target);
        AABB originalBox = attacker.getBoundingBox();
        ActionBattlePropulsionRules.Plan pointSafePlan = ActionBattlePropulsionRules.plan(
                point(start), point(lockedTarget), mode,
                candidate -> safePosition(session, level, attacker, start, originalBox, vec(candidate)));
        if (!pointSafePlan.valid()) return null;
        ActionBattlePropulsionRules.Plan segmentSafePlan = trimBlockedSegments(
                session, level, attacker, start, originalBox, pointSafePlan);
        return segmentSafePlan.valid()
                ? new PreparedPlan(segmentSafePlan, mode, speed, lockedTarget) : null;
    }

    private static ActionBattlePropulsionRules.Plan trimBlockedSegments(
            ActionBattleSession session, ServerLevel level, PokemonEntity attacker,
            Vec3 start, AABB originalBox, ActionBattlePropulsionRules.Plan plan) {
        List<ActionBattlePropulsionRules.Point> safePoints = new ArrayList<>();
        safePoints.add(plan.points().getFirst());
        boolean shortened = plan.shortened();
        for (int index = 1; index < plan.points().size(); index++) {
            Vec3 from = vec(plan.points().get(index - 1));
            Vec3 to = vec(plan.points().get(index));
            if (!safeSegment(session, level, attacker, start, originalBox, from, to)) {
                if (index <= plan.targetIndex()) return ActionBattlePropulsionRules.Plan.INVALID;
                shortened = true;
                break;
            }
            safePoints.add(plan.points().get(index));
        }
        if (safePoints.size() <= plan.targetIndex()) return ActionBattlePropulsionRules.Plan.INVALID;
        return new ActionBattlePropulsionRules.Plan(plan.mode(), List.copyOf(safePoints),
                plan.targetIndex(), plan.targetPoint(), plan.idealEndpoint(), shortened);
    }

    private static boolean safePosition(ActionBattleSession session, ServerLevel level,
                                        PokemonEntity attacker, Vec3 origin, AABB originalBox, Vec3 position) {
        if (!validPosition(session, level, attacker, position)) return false;
        return level.noCollision(attacker, originalBox.move(position.subtract(origin)));
    }

    private static boolean safeSegment(ActionBattleSession session, ServerLevel level,
                                       PokemonEntity attacker, Vec3 origin, AABB originalBox,
                                       Vec3 from, Vec3 to) {
        if (!safePosition(session, level, attacker, origin, originalBox, to)) return false;
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, attacker)).getType() == HitResult.Type.MISS;
    }

    private static boolean validPosition(ActionBattleSession session, ServerLevel level,
                                         PokemonEntity attacker, Vec3 position) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || !session.containsArena(position.x, position.z)) return false;
        BlockPos blockPos = BlockPos.containing(position);
        return position.y >= level.getMinBuildHeight()
                && position.y + attacker.getBbHeight() < level.getMaxBuildHeight()
                && level.getChunkSource().hasChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4)
                && level.getWorldBorder().isWithinBounds(blockPos);
    }

    private static Vec3 lockedImpactPoint(PokemonEntity attacker, PokemonEntity target) {
        double alignedY = target.getBoundingBox().getCenter().y - attacker.getBbHeight() * 0.5D;
        return new Vec3(target.getX(), Math.max(target.getY(), alignedY), target.getZ());
    }

    private static AABB sweptBox(Vec3 current, AABB currentBox, Vec3 from, Vec3 to) {
        AABB fromBox = currentBox.move(from.subtract(current));
        return fromBox.minmax(fromBox.move(to.subtract(from))).inflate(0.15D);
    }

    private static void faceMovement(PokemonEntity attacker, Vec3 movement) {
        double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-movement.x, movement.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(movement.y, horizontal));
        attacker.setYRot(yaw);
        attacker.yBodyRot = yaw;
        attacker.setYHeadRot(yaw);
        attacker.setXRot(pitch);
    }

    private static void clearEntry(ActivePropulsion entry) {
        if (entry == null || entry.attacker == null || entry.attacker.isRemoved()) return;
        entry.attacker.setNoGravity(entry.originalNoGravity);
        entry.attacker.setDeltaMovement(Vec3.ZERO);
    }

    private static ActionBattlePropulsionRules.Point point(Vec3 value) {
        return new ActionBattlePropulsionRules.Point(value.x, value.y, value.z);
    }

    private static Vec3 vec(ActionBattlePropulsionRules.Point value) {
        return new Vec3(value.x(), value.y(), value.z());
    }

    private record PreparedPlan(ActionBattlePropulsionRules.Plan plan,
                                ActionBattlePropulsionRules.Mode mode,
                                double speed, Vec3 lockedTarget) {}

    private static final class ActivePropulsion {
        private final UUID battleId;
        private final UUID attackerEntityId;
        private final UUID targetEntityId;
        private final PokemonEntity attacker;
        private final boolean originalNoGravity;
        private final ActionBattlePropulsionState state;
        private final Runnable impact;
        private final ActionBattlePropulsionRules.Mode mode;
        private final Vec3 lockedTarget;

        private ActivePropulsion(UUID battleId, UUID attackerEntityId, UUID targetEntityId,
                                 PokemonEntity attacker, boolean originalNoGravity,
                                 ActionBattlePropulsionState state, Runnable impact,
                                 ActionBattlePropulsionRules.Mode mode, Vec3 lockedTarget) {
            this.battleId = battleId;
            this.attackerEntityId = attackerEntityId;
            this.targetEntityId = targetEntityId;
            this.attacker = attacker;
            this.originalNoGravity = originalNoGravity;
            this.state = state;
            this.impact = impact;
            this.mode = mode;
            this.lockedTarget = lockedTarget;
        }
    }
}

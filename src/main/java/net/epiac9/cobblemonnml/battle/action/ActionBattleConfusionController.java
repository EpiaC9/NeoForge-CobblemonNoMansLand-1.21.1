package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.entity.PokemonAttackEffect;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.critical.ActionBattleCriticalRules;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleConfusionRules;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatus;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatusApplication;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterHealth;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostCast;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugCast;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugRuntime;
import net.epiac9.cobblemonnml.battle.action.visual.ActionBattleStatusParticleController;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleConfusionController {
    private static final double RANDOM_MOVE_MIN_DISTANCE = 4.0D;
    private static final double RANDOM_MOVE_DISTANCE_SPAN = 6.0D;
    private static final double CONFUSED_DASH_SPEED = 0.85D;
    private static final int CONFUSED_DASH_TICKS = 12;
    private static final Map<UUID, DashState> DASHES = new HashMap<>();

    private ActionBattleConfusionController() {}

    public static ActionBattleStatusApplication apply(ActionBattleSession session, PokemonEntity target, long currentTick) {
        if (session == null || target == null || target.isRemoved() || currentTick < 0L
                || !ActionBattleEffectApplicationGuard.allowsNewApplication(session, target, currentTick)) return null;
        UUID pokemonUUID = target.getPokemon().getUuid();
        var interception = ActionBattleProtectController.global().interceptTimedEffect(session.battleId(), pokemonUUID, currentTick, "confusion", (int) ActionBattleConfusionRules.DURATION_TICKS);
        if (!interception.allowed()) return null;
        ActionBattleStatusApplication result = ActionBattleEffectController.global().applyConfusion(session.battleId(), pokemonUUID, currentTick);
        if (result == ActionBattleStatusApplication.CONFUSION_APPLIED && target.level() instanceof ServerLevel level) ActionBattleStatusParticleController.emitConfusionBurst(level, target);
        return result;
    }

    public static boolean isConfused(ActionBattleSession session, UUID pokemonUUID, long currentTick) {
        return session != null && pokemonUUID != null && ActionBattleEffectController.global().hasStatus(session.battleId(), pokemonUUID, ActionBattleStatus.CONFUSION, currentTick);
    }

    public static CommandPlan roll(ActionBattleSession session, PokemonEntity pokemon, ActionBattleConfusionRules.CommandKind kind, long currentTick) {
        if (session == null || pokemon == null || kind == null || !isConfused(session, pokemon.getPokemon().getUuid(), currentTick)) return CommandPlan.NORMAL;
        if (!ActionBattleConfusionRules.shouldCorrupt(kind, pokemon.getRandom().nextFloat())) return CommandPlan.NORMAL;
        ActionBattleConfusionRules.RangedCorruption ranged = kind == ActionBattleConfusionRules.CommandKind.RANGED
                ? ActionBattleConfusionRules.rangedCorruption(pokemon.getRandom().nextInt()) : null;
        ActionBattleConfusionRules.SupportCorruption support = kind == ActionBattleConfusionRules.CommandKind.SUPPORT
                ? ActionBattleConfusionRules.supportCorruption(pokemon.getRandom().nextInt()) : null;
        long channelBonus = kind == ActionBattleConfusionRules.CommandKind.CHANNEL
                ? ActionBattleConfusionRules.channelBonusTicks(pokemon.getRandom().nextInt(7)) : 0L;
        boolean selfCancel = kind == ActionBattleConfusionRules.CommandKind.CHANNEL
                && ActionBattleConfusionRules.shouldSelfCancelChannel(pokemon.getRandom().nextFloat());
        DebugLog.log("[CobblemonNML] Confusion corrupted command. Battle=" + session.battleId() + ", pokemon=" + pokemon.getPokemon().getUuid()
                + ", kind=" + kind
                + (kind == ActionBattleConfusionRules.CommandKind.CHANNEL ? ", channelBonusTicks=" + channelBonus + ", selfCancel=" + selfCancel : ""));
        return new CommandPlan(true, channelBonus, selfCancel, ranged, support);
    }

    public static Vec3 randomMoveTarget(PokemonEntity pokemon) {
        double angle = pokemon.getRandom().nextDouble() * Math.PI * 2.0D;
        double distance = RANDOM_MOVE_MIN_DISTANCE + pokemon.getRandom().nextDouble() * RANDOM_MOVE_DISTANCE_SPAN;
        return pokemon.position().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
    }

    public static Vec3 randomShotDirection(PokemonEntity pokemon) {
        double yaw = pokemon.getRandom().nextDouble() * Math.PI * 2.0D;
        double y = (pokemon.getRandom().nextDouble() - 0.5D) * 0.7D;
        Vec3 direction = new Vec3(Math.cos(yaw), y, Math.sin(yaw));
        return direction.lengthSqr() > 0.000001D ? direction.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
    }

    public static Vec3 corruptedShotDirection(PokemonEntity pokemon, PokemonEntity target,
                                               ActionBattleConfusionRules.RangedCorruption mode) {
        if (pokemon == null) return new Vec3(1.0D, 0.0D, 0.0D);
        if (mode == ActionBattleConfusionRules.RangedCorruption.NO_TARGET) {
            Vec3 look = pokemon.getLookAngle();
            return look.lengthSqr() > 0.000001D ? look.normalize() : randomShotDirection(pokemon);
        }
        if (mode == ActionBattleConfusionRules.RangedCorruption.DIRECTION_DESYNC || target == null) {
            return randomShotDirection(pokemon);
        }
        Vec3 aim = target.getEyePosition().subtract(pokemon.getEyePosition());
        if (mode == ActionBattleConfusionRules.RangedCorruption.OFFSHOOT) {
            aim = aim.add((pokemon.getRandom().nextDouble() - 0.5D) * 3.0D,
                    (pokemon.getRandom().nextDouble() - 0.5D) * 2.0D,
                    (pokemon.getRandom().nextDouble() - 0.5D) * 3.0D);
        }
        return aim.lengthSqr() > 0.000001D ? aim.normalize() : randomShotDirection(pokemon);
    }

    public static boolean startMeleeDash(ActionBattleSession session, ServerLevel level, PokemonEntity attacker, Move move, long currentTick) {
        return startMeleeDash(session, level, attacker, move, currentTick, 1.0D);
    }

    public static boolean startMeleeDash(ActionBattleSession session, ServerLevel level, PokemonEntity attacker, Move move,
                                         long currentTick, double committedDamageMultiplier) {
        return startMeleeDash(session, level, attacker, move, currentTick, committedDamageMultiplier,
                ActionBattleCommittedMove.none());
    }

    public static boolean startMeleeDash(ActionBattleSession session, ServerLevel level, PokemonEntity attacker, Move move,
                                         long currentTick, double committedDamageMultiplier,
                                         ActionBattleCommittedMove committedMove) {
        if (session == null || level == null || attacker == null || move == null) return false;
        UUID pokemonUUID = attacker.getPokemon().getUuid();
        Vec3 direction = randomShotDirection(attacker);
        direction = new Vec3(direction.x, 0.0D, direction.z).normalize();
        attacker.getNavigation().stop();
        attacker.setDeltaMovement(direction.scale(CONFUSED_DASH_SPEED));
        double ghostDamageMultiplier = ActionBattleGhostRuntime.global().prepareDamagingAbility(attacker, move);
        ActionBattleGhostCast ghostCast = ActionBattleGhostRuntime.global().completeMove(attacker, move).orElse(null);
        ActionBattleBugCast bugCast = null;
        DASHES.put(pokemonUUID, new DashState(session.battleId(), pokemonUUID, move,
                currentTick + CONFUSED_DASH_TICKS, Math.max(1.0D, committedDamageMultiplier),
                ghostDamageMultiplier, ghostCast, bugCast,
                committedMove != null ? committedMove : ActionBattleCommittedMove.none()));
        return true;
    }

    public static void tickBattle(ActionBattleSession session, ServerLevel level) {
        if (session == null || level == null) return;
        DASHES.entrySet().removeIf(entry -> resolveDashState(session, level, entry.getValue()));
    }

    private static boolean resolveDashState(ActionBattleSession session, ServerLevel level, DashState state) {
        if (!session.battleId().equals(state.battleId())) return false;
        PokemonEntity attacker = activeEntity(session, level, state.pokemonUUID());
        if (!ActionBattleSleepController.canIssueCommand(session, state.pokemonUUID(), level.getGameTime(),
                ActionBattleSleepController.CommandKind.PENDING_CONTINUATION)) {
            ActionBattleGhostRuntime.global().discard(state.ghostCast());
            clearDashVelocity(attacker);
            return true;
        }
        if (ActionBattleMovementActionRules.isMovementBlocked(session, state.pokemonUUID(), level.getGameTime())) {
            ActionBattleGhostRuntime.global().discard(state.ghostCast());
            clearDashVelocity(attacker);
            return true;
        }
        if (shouldExpireDash(level, attacker, state)) {
            ActionBattleGhostRuntime.global().discard(state.ghostCast());
            clearDashVelocity(attacker);
            return true;
        }
        if (attacker.horizontalCollision) {
            net.epiac9.cobblemonnml.battle.action.interrupt.ActionBattleInterruptController.apply(
                    session, attacker, ActionBattleConfusionRules.CRASH_INTERRUPT_TICKS, level.getGameTime());
            damageSelf(attacker, state.move(), state.committedDamageMultiplier(),
                    state.ghostDamageMultiplier(), state.ghostCast(), state.committedMove(),
                    ActionBattleConfusionRules.crashDamageMultiplier());
            clearDashVelocity(attacker);
            return true;
        }
        AABB hitBox = attacker.getBoundingBox().inflate(0.20D);
        for (Entity raw : level.getEntities(attacker, hitBox, e -> e instanceof LivingEntity && e.isAlive())) {
            if (!(raw instanceof LivingEntity hit) || raw.getUUID().equals(attacker.getUUID())) continue;
            if (hit instanceof PokemonEntity pokemonHit && !hitBox.intersects(
                    ActionBattleGroundController.effectiveCombatBox(pokemonHit, level.getGameTime(), false))) continue;
            damageCollision(attacker, hit, state.move(), state.committedDamageMultiplier(),
                    state.ghostDamageMultiplier(), state.ghostCast(), state.bugCast(), state.committedMove());
            clearDashVelocity(attacker);
            return true;
        }
        return attacker.getDeltaMovement().horizontalDistanceSqr() < 0.05D;
    }

    private static boolean shouldExpireDash(ServerLevel level, PokemonEntity attacker, DashState state) {
        return attacker == null || attacker.isRemoved() || level.getGameTime() >= state.expiresAtTick();
    }

    private static void clearDashVelocity(PokemonEntity attacker) {
        if (attacker != null) attacker.setDeltaMovement(Vec3.ZERO);
    }

    public static void clearBattle(UUID battleId) {
        if (battleId == null) return;
        DASHES.entrySet().removeIf(entry -> {
            if (!battleId.equals(entry.getValue().battleId())) return false;
            ActionBattleGhostRuntime.global().discard(entry.getValue().ghostCast());
            return true;
        });
    }

    public static boolean cancelMeleeDash(UUID pokemonUUID) {
        if (pokemonUUID == null) return false;
        DashState removed = DASHES.remove(pokemonUUID);
        if (removed == null) return false;
        ActionBattleGhostRuntime.global().discard(removed.ghostCast());
        return true;
    }

    private static void damageCollision(PokemonEntity attacker, LivingEntity target, Move move,
                                        double committedDamageMultiplier, double ghostDamageMultiplier,
                                        ActionBattleGhostCast ghostCast, ActionBattleBugCast bugCast,
                                        ActionBattleCommittedMove committedMove) {
        PokemonEntity pokemonTarget = target instanceof PokemonEntity pokemon ? pokemon : null;
        long currentTick = attacker.level().getGameTime();
        ActionBattleBugCast resolvedBugCast = bugCast;
        if (resolvedBugCast == null && pokemonTarget != null) {
            resolvedBugCast = ActionBattleBugRuntime.arm(attacker, pokemonTarget, move).orElse(null);
        }
        float targetDamage = Math.max(1.0F, ActionBattleCriticalRules.apply(
                FightOrFlightAdapter.scaleActionDamage(attacker, target, move,
                        PokemonAttackEffect.calculatePokemonDamage(attacker, target, move), committedDamageMultiplier),
                committedMove.critical()));
        int beforeHp = pokemonTarget != null ? pokemonTarget.getPokemon().getCurrentHealth() : 0;
        int attemptedPokemonDamage = pokemonTarget != null ? ActionBattleWaterHealth.toPokemonDamage(
                pokemonTarget.getPokemon().getMaxHealth(), pokemonTarget.getMaxHealth(), targetDamage) : 0;
        boolean success = target.hurt(attacker.damageSources().mobAttack(attacker), targetDamage);
        if (success && pokemonTarget != null) {
            FightOrFlightAdapter.ProtectionOutcome protection = FightOrFlightAdapter.applyProtectImpact(
                    attacker, pokemonTarget, move, beforeHp, attemptedPokemonDamage, true);
            int actualBugTriggerDamage = ActionBattleBugRuntime.resolveIncomingDamage(pokemonTarget, beforeHp);
            ActionBattleBugRuntime.resolveHit(resolvedBugCast, pokemonTarget, actualBugTriggerDamage, move);
            net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime
                    .onConnectedHit(attacker, pokemonTarget, move, true);
            ActionBattleRockRuntime.HitResult rockHit = ActionBattleRockRuntime.resolveDirectHit(
                    attacker, pokemonTarget, beforeHp, protection.incomingDamage(), true,
                    protection.protectParticipated());
            ActionBattleGhostRuntime.global().connect(ghostCast, pokemonTarget);
            ActionBattleGhostRuntime.global().onDamageResolved(pokemonTarget, beforeHp);
            ActionBattleRockRuntime.applyReflection(attacker, rockHit);
        } else if (!success) {
            ActionBattleGhostRuntime.global().discard(ghostCast);
        }
    }

    private static void damageSelf(PokemonEntity attacker, Move move, double committedDamageMultiplier,
                                   double ghostDamageMultiplier,
                                   ActionBattleGhostCast ghostCast, ActionBattleCommittedMove committedMove,
                                   double damageMultiplier) {
        int beforeHp = attacker.getPokemon().getCurrentHealth();
        long currentTick = attacker.level().getGameTime();
        float selfDamage = Math.max(1.0F, (float) (damageMultiplier * ActionBattleCriticalRules.apply(
                FightOrFlightAdapter.scaleActionDamage(attacker, attacker, move,
                        PokemonAttackEffect.calculatePokemonDamage(attacker, attacker, move), committedDamageMultiplier),
                committedMove.critical())));
        boolean success = attacker.hurt(attacker.damageSources().magic(), selfDamage);
        if (success) {
            ActionBattleGhostRuntime.global().connect(ghostCast, attacker);
            ActionBattleGhostRuntime.global().onDamageResolved(attacker, beforeHp);
        } else {
            ActionBattleGhostRuntime.global().discard(ghostCast);
        }
    }

    private static PokemonEntity activeEntity(ActionBattleSession session, ServerLevel level, UUID pokemonUUID) {
        UUID entityUUID = null;
        if (session.isPlayerPokemon(pokemonUUID)) entityUUID = session.playerEntityForPokemon(pokemonUUID);
        else if (pokemonUUID.equals(session.trainerActivePokemonUUID())) entityUUID = session.trainerActiveEntityUUID();
        Entity raw = entityUUID != null ? level.getEntity(entityUUID) : null;
        return raw instanceof PokemonEntity pokemon ? pokemon : null;
    }

    public record CommandPlan(boolean corrupted, long channelBonusTicks, boolean channelSelfCancel,
                              ActionBattleConfusionRules.RangedCorruption rangedCorruption,
                              ActionBattleConfusionRules.SupportCorruption supportCorruption) {
        public static final CommandPlan NORMAL = new CommandPlan(false, 0L, false, null, null);
    }
    private record DashState(UUID battleId, UUID pokemonUUID, Move move, long expiresAtTick,
                             double committedDamageMultiplier, double ghostDamageMultiplier,
                             ActionBattleGhostCast ghostCast, ActionBattleBugCast bugCast,
                             ActionBattleCommittedMove committedMove) {}
}

package net.epiac9.cobblemonnml.battle.action.typeeffect.bug;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectController;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStat;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleStatSource;
import net.epiac9.cobblemonnml.battle.action.health.ActionBattleDamageSource;
import net.epiac9.cobblemonnml.battle.action.health.ActionBattleHealthResolver;
import net.epiac9.cobblemonnml.battle.action.projectile.ActionBattleProjectileEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.epiac9.cobblemonnml.util.DebugLog;
import me.rufia.fightorflight.entity.PokemonAttackEffect;
import me.rufia.fightorflight.utils.PokemonUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ActionBattleBugRuntime {
    private static final ActionBattleHealthResolver HEALTH = new ActionBattleHealthResolver(
            ActionBattleGhostRuntime.global().curses());

    private ActionBattleBugRuntime() {}

    public static Optional<ActionBattleBugCast> arm(PokemonEntity user, PokemonEntity target, Move move) {
        if (user == null || move == null || user.level().isClientSide
                || !ActionBattleTypeMechanicIdentity.hasMechanicBenefit(user, "bug")
                || FightOrFlightAdapter.isSelfOrAllyTargetCategory(FightOrFlightAdapter.moveTargetCategory(move))) {
            return Optional.empty();
        }
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(user.getUUID());
        if (session == null) return Optional.empty();
        UUID intendedTarget = target != null ? target.getPokemon().getUuid() : null;
        if (!isEnemy(session, user.getPokemon().getUuid(), intendedTarget)) return Optional.empty();
        return activate(user, target, move, true, session);
    }

    public static void onOwnedActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.targetingMode()
                != ActionBattleTypeMechanicActionContext.TargetingMode.SELF_OR_ALLY) return;
        PokemonEntity user = context.pokemon();
        ActionBattleSession session = user != null
                ? ActionBattleManager.findSessionForBattlePokemonEntity(user.getUUID()) : null;
        if (session != null) activate(user, null, context.move(), false, session);
    }

    private static Optional<ActionBattleBugCast> activate(PokemonEntity user, PokemonEntity target, Move move,
                                                           boolean targeted, ActionBattleSession session) {
        if (user == null || move == null || session == null
                || !ActionBattleTypeMechanicIdentity.hasMechanicBenefit(user, "bug")) return Optional.empty();
        Pokemon pokemon = user.getPokemon();
        UUID intendedTarget = target != null ? target.getPokemon().getUuid() : null;
        ActionBattleBugDiagnostics.TrainingTotals training = trainingTotals(pokemon);
        EnumSet<ActionBattleBugTrainingStat> highest = ActionBattleBugRules.highestForTargetingMode(
                targeted, training.hp(), training.attack(), training.defense(), training.specialAttack(),
                training.specialDefense(), training.speed());
        boolean bugTyped = true;
        long tick = user.level().getGameTime();
        var trigger = ActionBattleBugController.global().trigger(session.battleId(), pokemon.getUuid(),
                highest, bugTyped, pokemon.getMaxHealth(), tick);
        DebugLog.log(ActionBattleBugDiagnostics.selection(pokemon.getSpecies().getName(), pokemon.getUuid(),
                bugTyped, training, highest, trigger.activated(), trigger.skippedLocked()));
        if (trigger.activated().isEmpty()) return Optional.empty();
        applyPenalties(session, pokemon.getUuid(), trigger.activated(), bugTyped, tick);
        if (trigger.activated().contains(ActionBattleBugTrainingStat.DEFENSE)
                || trigger.activated().contains(ActionBattleBugTrainingStat.SPECIAL_DEFENSE)) {
            spawnCarapace((ServerLevel) user.level(), session, user, tick);
        }
        if (trigger.activated().contains(ActionBattleBugTrainingStat.SPEED)) dash(session, user, bugTyped);
        if (!targeted) return Optional.empty();
        ActionBattleBugCast cast = new ActionBattleBugCast(UUID.randomUUID(), session.battleId(),
                pokemon.getUuid(), intendedTarget, bugTyped, trigger.activated(), move.getName(),
                FightOrFlightAdapter.isRangedMove(move));
        return Optional.of(cast);
    }

    public static int resolveIncomingDamage(PokemonEntity target, int beforeHealth) {
        if (target == null || beforeHealth <= target.getPokemon().getCurrentHealth()) return 0;
        UUID battleId = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
        if (battleId == null) return 0;
        int incoming = beforeHealth - target.getPokemon().getCurrentHealth();
        ActionBattleBugState state = ActionBattleBugController.global().state(
                battleId, target.getPokemon().getUuid()).orElse(null);
        if (state == null) return incoming;
        var result = state.absorb(incoming, target.level().getGameTime());
        if (result.absorbed() > 0) {
            HEALTH.heal(ActionBattleGhostRuntime.healthAccess(target), battleId,
                    target.getPokemon().getUuid(), result.absorbed(), target.level().getGameTime());
        }
        return result.remainingDamage();
    }

    public static void resolveHit(ActionBattleBugCast cast, PokemonEntity target, int actualNormalDamage) {
        resolveHit(cast, target, actualNormalDamage, null);
    }

    public static void resolveHit(ActionBattleBugCast cast, PokemonEntity target,
                                  int actualNormalDamage, Move triggeringMove) {
        if (cast == null || target == null || actualNormalDamage <= 0
                || !cast.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(target.getUUID()))) return;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !isEnemy(session, cast.userPokemonId(), target.getPokemon().getUuid())) return;
        long tick = target.level().getGameTime();
        ActionBattleBugState armedState = ActionBattleBugController.global().state(
                cast.battleId(), cast.userPokemonId()).orElse(null);
        if (armedState == null || !(target.level() instanceof ServerLevel level)) return;
        PokemonEntity attacker = activeEntity(session, level, cast.userPokemonId());
        if (attacker == null) return;
        boolean attack = cast.activated().contains(ActionBattleBugTrainingStat.ATTACK);
        boolean specialAttack = cast.activated().contains(ActionBattleBugTrainingStat.SPECIAL_ATTACK);
        attack &= armedState.locked(ActionBattleBugTrainingStat.ATTACK, tick);
        specialAttack &= armedState.locked(ActionBattleBugTrainingStat.SPECIAL_ATTACK, tick);
        var plan = ActionBattleBugOffense.plan(attack, specialAttack, actualNormalDamage, cast.bugTyped());
        if (plan.damage() <= 0) return;
        Move move = triggeringMove != null && cast.moveName().equals(triggeringMove.getName())
                ? triggeringMove : resolveMove(attacker, cast.moveName());
        if (attack && move != null && cast.ranged()) {
            level.addFreshEntity(ActionBattleProjectileEntity.bugFollowup(
                    level, attacker, target, move, cast, plan.damage(), plan.area()));
            return;
        }
        if (attack && move != null) {
            PokemonUtils.sendAnimationPacket(attacker, "physical");
            PokemonAttackEffect.applyOnHitVisualEffect(attacker, target, move);
            PokemonAttackEffect.applySFX(attacker.level(), move, attacker.blockPosition());
        }
        resolveFollowupImpact(cast, target, plan.damage(), plan.area());
    }

    public static void resolveFollowupImpact(ActionBattleBugCast cast, PokemonEntity target,
                                             int damage, boolean area) {
        if (cast == null || target == null || damage <= 0
                || !(target.level() instanceof ServerLevel level)) return;
        ActionBattleSession session = ActionBattleManager.findSessionByBattleId(cast.battleId());
        if (session == null || !isEnemy(session, cast.userPokemonId(), target.getPokemon().getUuid())) return;
        if (!area) {
            damage(target, cast.battleId(), damage);
            return;
        }
        ActionBattleBugVisuals.explosion(target);
        for (PokemonEntity candidate : level.getEntitiesOfClass(PokemonEntity.class,
                new AABB(target.position(), target.position()).inflate(ActionBattleBugVisualPlan.explosionRadius()))) {
            if (candidate.isAlive() && isEnemy(session, cast.userPokemonId(), candidate.getPokemon().getUuid())) {
                damage(candidate, cast.battleId(), damage);
            }
        }
    }

    public static void tickPokemon(ActionBattleSession session, PokemonEntity pokemon, long tick) {
        if (session == null || pokemon == null) return;
        ActionBattleBugController.global().state(session.battleId(), pokemon.getPokemon().getUuid()).ifPresent(state -> {
            if (state.sheddingActive(tick)) ActionBattleBugVisuals.sheddingWrap(pokemon, tick);
            for (ActionBattleBugState.DotTick dot : state.tick(tick)) damage(
                    pokemon, session.battleId(), dot.damage(), ActionBattleDamageSource.bugSheddingDot(),
                    ActionBattleDamageFeedbackCategory.DOT);
        });
    }

    public static double locomotionMultiplier(ActionBattleSession session, UUID pokemonId, long tick) {
        return session == null ? 1.0D : ActionBattleBugController.global().state(session.battleId(), pokemonId)
                .map(state -> state.locomotionMultiplier(tick)).orElse(1.0D);
    }

    public static boolean protectsFromNewEffects(ActionBattleSession session, PokemonEntity target, long tick) {
        if (session == null || target == null || !(target.level() instanceof ServerLevel level)) return false;
        UUID targetId = target.getPokemon().getUuid();
        boolean playerSide = session.isPlayerPokemon(targetId);
        for (UUID owner : activePokemonIds(session)) {
            boolean allied = session.isPlayerPokemon(owner) == playerSide;
            ActionBattleBugState state = ActionBattleBugController.global().state(session.battleId(), owner).orElse(null);
            ActionBattleBugController.ConstructPosition position = ActionBattleBugController.global()
                    .construct(session.battleId(), owner).orElse(null);
            BlockPos blockPos = position == null ? null : new BlockPos(position.x(), position.y(), position.z());
            boolean validBlock = blockPos != null && level.getBlockEntity(blockPos)
                    instanceof ActionBattleBugCarapaceBlockEntity carapace && carapace.effectZone();
            double distance = validBlock ? target.distanceToSqr(
                    position.x() + 0.5D, position.y() + 0.5D, position.z() + 0.5D) : Double.POSITIVE_INFINITY;
            if (state != null && ActionBattleBugRules.effectZoneProtects(
                    allied, state.effectZoneActive(tick) && validBlock, distance)) return true;
        }
        return false;
    }

    public static void clearPokemon(ActionBattleSession session, UUID pokemonId) {
        clearPokemon(session, pokemonId, false);
    }
    public static void clearPokemon(ActionBattleSession session, UUID pokemonId, boolean fainted) {
        if (session != null && ActionBattleBugController.global().clearPokemon(session.battleId(), pokemonId)) {
            DebugLog.logf("[BugAdapt] cleanup owner=%s reason=%s", pokemonId,
                    fainted ? "faint" : "swap/recall");
        }
    }
    public static void clearBattle(UUID battleId) { ActionBattleBugController.global().clearBattle(battleId); }
    public static void clearAll() { ActionBattleBugController.global().clearAll(); }

    public static boolean areEnemies(UUID battleId, UUID first, UUID second) {
        ActionBattleSession session = battleId != null ? ActionBattleManager.findSessionByBattleId(battleId) : null;
        return session != null && isEnemy(session, first, second);
    }

    private static ActionBattleBugDiagnostics.TrainingTotals trainingTotals(Pokemon pokemon) {
        return new ActionBattleBugDiagnostics.TrainingTotals(score(pokemon, Stats.HP),
                score(pokemon, Stats.ATTACK), score(pokemon, Stats.DEFENCE),
                score(pokemon, Stats.SPECIAL_ATTACK), score(pokemon, Stats.SPECIAL_DEFENCE),
                score(pokemon, Stats.SPEED));
    }

    private static int score(Pokemon pokemon, Stats stat) {
        return pokemon.getIvs().getOrDefault(stat) + pokemon.getEvs().getOrDefault(stat);
    }

    private static void applyPenalties(ActionBattleSession session, UUID pokemonId,
                                       EnumSet<ActionBattleBugTrainingStat> activated,
                                       boolean bugTyped, long tick) {
        int stages = ActionBattleBugRules.penaltyStages(bugTyped);
        applyPenalty(session, pokemonId, activated, ActionBattleBugTrainingStat.ATTACK,
                ActionBattleStat.ATTACK, ActionBattleStatSource.BUG_ATTACK, stages, tick);
        applyPenalty(session, pokemonId, activated, ActionBattleBugTrainingStat.SPECIAL_ATTACK,
                ActionBattleStat.SPECIAL_ATTACK, ActionBattleStatSource.BUG_SPECIAL_ATTACK, stages, tick);
        applyPenalty(session, pokemonId, activated, ActionBattleBugTrainingStat.DEFENSE,
                ActionBattleStat.DEFENSE, ActionBattleStatSource.BUG_DEFENSE, stages, tick);
        applyPenalty(session, pokemonId, activated, ActionBattleBugTrainingStat.SPECIAL_DEFENSE,
                ActionBattleStat.SPECIAL_DEFENSE, ActionBattleStatSource.BUG_SPECIAL_DEFENSE, stages, tick);
    }

    private static void applyPenalty(ActionBattleSession session, UUID pokemonId,
                                     EnumSet<ActionBattleBugTrainingStat> activated,
                                     ActionBattleBugTrainingStat branch, ActionBattleStat stat,
                                     ActionBattleStatSource source, int stages, long tick) {
        if (activated.contains(branch)) ActionBattleEffectController.global().applyBoundedStatContribution(
                session.battleId(), pokemonId, stat, stages, tick, ActionBattleBugRules.LOCKOUT_TICKS, source);
    }

    private static void dash(ActionBattleSession session, PokemonEntity user, boolean bugTyped) {
        Optional<Vec3> directive = session.lastAcceptedMoveHereDirective(user.getPokemon().getUuid());
        Vec3 facing = user.getLookAngle();
        Vec3 wanted = directive.orElse(new Vec3(Double.NaN, 0.0D, Double.NaN));
        var plan = ActionBattleBugDashRules.plan(user.getX(), user.getZ(), wanted.x, wanted.z,
                facing.x, facing.z, ActionBattleBugRules.dashDistance(bugTyped));
        Vec3 full = new Vec3(plan.deltaX(), 0.0D, plan.deltaZ());
        Vec3 safe = Vec3.ZERO;
        for (int step = 1; step <= 20; step++) {
            Vec3 candidate = full.scale(step / 20.0D);
            if (!session.containsArena(user.getX() + candidate.x, user.getZ() + candidate.z)
                    || !user.level().noCollision(user, user.getBoundingBox().move(candidate))) break;
            safe = candidate;
        }
        if (safe.lengthSqr() > 0.0D) {
            Vec3 start = user.position();
            user.teleportTo(user.getX() + safe.x, user.getY(), user.getZ() + safe.z);
            if (user.level() instanceof ServerLevel level) ActionBattleBugVisuals.dashTrail(level, start, user.position());
        }
    }

    private static void spawnCarapace(ServerLevel level, ActionBattleSession session,
                                      PokemonEntity owner, long tick) {
        UUID ownerId = owner.getPokemon().getUuid();
        ActionBattleBugController.global().construct(session.battleId(), ownerId).ifPresent(existing ->
                level.removeBlock(new BlockPos(existing.x(), existing.y(), existing.z()), false));
        ActionBattleBugState state = ActionBattleBugController.global().state(
                session.battleId(), ownerId).orElse(null);
        if (state == null) return;
        ActionBattleBugCarapaceState.Snapshot snapshot = state.carapaceSnapshot(tick);
        boolean physical = snapshot.physicalActive();
        boolean zone = snapshot.effectZoneActive();
        int shellTicks = (int) Math.min(Integer.MAX_VALUE, snapshot.physicalRemainingTicks());
        int zoneTicks = (int) Math.min(Integer.MAX_VALUE, snapshot.effectZoneRemainingTicks());
        Vec3 facing = owner.getLookAngle();
        var placement = ActionBattleBugCarapacePlacement.plan(physical, zone,
                owner.getX(), owner.getZ(), facing.x, facing.z);
        BlockPos pos = nearbyLanding(level, owner.blockPosition().getY(), placement.blockX(), placement.blockZ());
        boolean valid = pos != null && session.containsArena(pos.getX() + 0.5D, pos.getZ() + 0.5D);
        boolean synced = valid && level.setBlock(pos, ModBlocks.ACTION_BATTLE_BUG_CARAPACE.get().defaultBlockState(), 3)
                && level.getBlockEntity(pos) instanceof ActionBattleBugCarapaceBlockEntity;
        if (synced && level.getBlockEntity(pos) instanceof ActionBattleBugCarapaceBlockEntity carapace) {
            carapace.initialize(session.battleId(), owner, snapshot);
            ActionBattleBugController.global().setConstruct(session.battleId(), ownerId,
                    pos.getX(), pos.getY(), pos.getZ());
        }
        String syncResult = synced ? "success" : "fail-invalid-or-obstructed";
        if (physical && zone) {
            DebugLog.logf("[BugAdapt] DEF+SPDEF combined carapace create owner=%s "
                    + "shellTicks=%d zoneTicks=%d blockProjectile=true effectZone=true radius=6 visualSync=%s",
                    ownerId, shellTicks, zoneTicks, syncResult);
        } else if (physical) {
            DebugLog.logf("[BugAdapt] DEF carapace create owner=%s durationTicks=%d visualSync=%s",
                    ownerId, shellTicks, syncResult);
        } else if (zone) {
            DebugLog.logf("[BugAdapt] SPDEF zone create owner=%s durationTicks=%d radius=6 visualSync=%s",
                    ownerId, zoneTicks, syncResult);
        }
    }

    private static Move resolveMove(PokemonEntity attacker, String moveName) {
        if (attacker == null || moveName == null) return null;
        for (Move move : attacker.getPokemon().getMoveSet()) {
            if (move != null && moveName.equals(move.getName())) return move;
        }
        return null;
    }

    private static BlockPos nearbyLanding(ServerLevel level, int ownerY, int x, int z) {
        for (int y = Math.min(level.getMaxBuildHeight() - 2, ownerY + 2);
             y >= Math.max(level.getMinBuildHeight() + 1, ownerY - 3); y--) {
            BlockPos candidate = new BlockPos(x, y, z);
            boolean empty = level.isLoaded(candidate) && level.getWorldBorder().isWithinBounds(candidate)
                    && level.isEmptyBlock(candidate) && level.isEmptyBlock(candidate.above());
            boolean supported = empty && level.getBlockState(candidate.below())
                    .isFaceSturdy(level, candidate.below(), Direction.UP);
            if (ActionBattleBugCarapacePlacement.validLanding(empty, supported)) return candidate;
        }
        return null;
    }

    private static void damage(PokemonEntity target, UUID battleId, int amount) {
        damage(target, battleId, amount, ActionBattleDamageSource.bugSecondary(),
                ActionBattleDamageFeedbackCategory.NORMAL);
    }

    private static void damage(PokemonEntity target, UUID battleId, int amount, ActionBattleDamageSource source,
                               ActionBattleDamageFeedbackCategory feedbackCategory) {
        int before = target.getPokemon().getCurrentHealth();
        HEALTH.damage(ActionBattleGhostRuntime.healthAccess(target), battleId, target.getPokemon().getUuid(),
                amount, source, target.level().getGameTime());
        ActionBattleDamageFeedbackController.global().recordDamage(battleId, target.getPokemon().getUuid(),
                before, target.getPokemon().getCurrentHealth(), feedbackCategory);
    }

    private static boolean isEnemy(ActionBattleSession session, UUID user, UUID target) {
        return user != null && target != null && session.isPlayerPokemon(user) != session.isPlayerPokemon(target)
                && (session.isPlayerPokemon(target) || target.equals(session.trainerActivePokemonUUID()));
    }
    private static Set<UUID> activePokemonIds(ActionBattleSession session) {
        java.util.HashSet<UUID> ids = new java.util.HashSet<>();
        for (UUID player : session.playerUUIDs()) {
            UUID id = session.playerActivePokemonUUID(player);
            if (id != null) ids.add(id);
        }
        if (session.trainerActivePokemonUUID() != null) ids.add(session.trainerActivePokemonUUID());
        return Set.copyOf(ids);
    }

    private static PokemonEntity activeEntity(ActionBattleSession session, ServerLevel level, UUID pokemonId) {
        UUID entityId = session.isPlayerPokemon(pokemonId) ? session.playerEntityForPokemon(pokemonId)
                : pokemonId.equals(session.trainerActivePokemonUUID()) ? session.trainerActiveEntityUUID() : null;
        Entity entity = entityId != null ? level.getEntity(entityId) : null;
        return entity instanceof PokemonEntity pokemon ? pokemon : null;
    }
}

package net.epiac9.cobblemonnml.battle.action.projectile;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import me.rufia.fightorflight.entity.PokemonAttackEffect;
import me.rufia.fightorflight.entity.projectile.AbstractPokemonProjectile;
import me.rufia.fightorflight.entity.projectile.PokemonArrow;
import me.rufia.fightorflight.utils.PokemonUtils;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSleepController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleEvasionController;
import net.epiac9.cobblemonnml.battle.action.compat.ActionBattleMoveEffectResolver;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackCategory;
import net.epiac9.cobblemonnml.battle.action.damage.ActionBattleDamageFeedbackController;
import net.epiac9.cobblemonnml.battle.action.ActionBattleCommittedMove;
import net.epiac9.cobblemonnml.battle.action.critical.ActionBattleCriticalResult;
import net.epiac9.cobblemonnml.battle.action.critical.ActionBattleCriticalRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.ActionBattleElectricController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ice.ActionBattleIceRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fairy.ActionBattleFairyController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fire.ActionBattleFireRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.poison.ActionBattlePoisonController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.poison.ActionBattlePoisonRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.psychic.ActionBattlePsycUpController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.ActionBattleWaterHealth;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.ActionBattleGrassController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ground.ActionBattleGroundController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.rock.ActionBattleRockRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostCast;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.fighting.ActionBattleFightingRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleFlyingRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugCast;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugRuntime;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugTrainingStat;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugCarapaceBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.bug.ActionBattleBugCarapaceState;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.registry.ModEntities;
import net.epiac9.cobblemonnml.util.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class ActionBattleProjectileEntity extends PokemonArrow {
    private static final EntityDataAccessor<String> DATA_MOVE_NAME = SynchedEntityData.defineId(ActionBattleProjectileEntity.class, EntityDataSerializers.STRING);
    private int maxLifetimeTicks = 80;
    private UUID intendedTargetUUID;
    private String committedMoveName = "";
    private transient Move committedMove;
    private boolean confusedShot;
    private double accuracySpeedMultiplier = 1.0D;
    private double committedGrassMultiplier = 1.0D;
    private double ghostDamageMultiplier = 1.0D;
    private ActionBattleCommittedMove committedContext = ActionBattleCommittedMove.none();
    private ActionBattleGhostCast ghostCast;
    private ActionBattleBugCast bugCast;
    private boolean bugFollowup;
    private int bugFollowupDamage;
    private boolean bugFollowupArea;
    private boolean flyingSpeedLogged;

    public ActionBattleProjectileEntity(EntityType<? extends AbstractPokemonProjectile> entityType, Level level) {
        super(entityType, level);
    }

    public static ActionBattleProjectileEntity bugFollowup(Level level, PokemonEntity shooter,
                                                            PokemonEntity target, Move move,
                                                            ActionBattleBugCast cast, int damage,
                                                            boolean area) {
        ActionBattleProjectileEntity projectile = new ActionBattleProjectileEntity(
                ModEntities.ACTION_BATTLE_PROJECTILE.get(), level);
        projectile.initPosition(shooter);
        projectile.setOwner(shooter);
        projectile.setNoGravity(!ActionProjectileProfile.isLobbed(move.getName()));
        projectile.intendedTargetUUID = target.getUUID();
        projectile.committedMoveName = move.getName();
        projectile.entityData.set(DATA_MOVE_NAME, move.getName());
        projectile.committedMove = move;
        projectile.bugCast = cast;
        projectile.bugFollowup = true;
        projectile.bugFollowupDamage = Math.max(0, damage);
        projectile.bugFollowupArea = area;
        projectile.setElementalType(move.getType().getName());
        projectile.setDamage(0.0F);
        projectile.maxLifetimeTicks = ActionProjectileProfile.maxLifetimeTicks(move.getName());
        Vec3 trackedTarget = ActionBattleEvasionController.trackedPosition(target, level.getGameTime())
                .add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 delta = trackedTarget.subtract(projectile.position());
        if (ActionProjectileProfile.isLobbed(move.getName())) {
            delta = delta.add(0.0D, Math.sqrt(delta.x * delta.x + delta.z * delta.z) * 0.30D, 0.0D);
        }
        projectile.shoot(delta.x, delta.y, delta.z,
                (float) ActionProjectileProfile.speedBlocksPerTick(move.getName()), 0.0F);
        return projectile;
    }

    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, LivingEntity target, Move move) {
        this(level, shooter, target, move, 1.0D);
    }

    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, LivingEntity target, Move move,
                                        double committedGrassMultiplier) {
        this(level, shooter, target, move, committedGrassMultiplier, null);
    }

    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, LivingEntity target, Move move,
                                        double committedGrassMultiplier, ActionBattleGhostCast ghostCast) {
        this(level, shooter, target, move, committedGrassMultiplier, 1.0D, ghostCast);
    }

    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, LivingEntity target, Move move,
                                        double committedGrassMultiplier, double ghostDamageMultiplier,
                                        ActionBattleGhostCast ghostCast) {
        this(level, shooter, target, move, committedGrassMultiplier, ghostDamageMultiplier, ghostCast,
                ActionBattleCommittedMove.none());
    }

    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, LivingEntity target, Move move,
                                        double committedGrassMultiplier, double ghostDamageMultiplier,
                                        ActionBattleGhostCast ghostCast, ActionBattleCommittedMove committedContext) {
        super(ModEntities.ACTION_BATTLE_PROJECTILE.get(), level);
        initPosition(shooter);
        setOwner(shooter);
        setNoGravity(!ActionProjectileProfile.isLobbed(move.getName()));
        intendedTargetUUID = target.getUUID();
        committedMoveName = move.getName();
        entityData.set(DATA_MOVE_NAME, committedMoveName);
        committedMove = move;
        this.committedGrassMultiplier = Math.max(1.0D, committedGrassMultiplier);
        this.ghostCast = ghostCast;
        this.ghostDamageMultiplier = Math.max(0.0D, ghostDamageMultiplier);
        this.committedContext = committedContext != null ? committedContext : ActionBattleCommittedMove.none();
        this.bugCast = ActionBattleBugRuntime.arm(shooter,
                target instanceof PokemonEntity pokemon ? pokemon : null, move).orElse(null);
        setElementalType(move.getType().getName());
        setDamage(FightOrFlightAdapter.isNativeDamageMove(move) ? ActionBattleCriticalRules.apply(FightOrFlightAdapter.scaleActionDamage(
                shooter, target, move, PokemonAttackEffect.calculatePokemonDamage(shooter, target, move),
                this.committedGrassMultiplier), this.committedContext.critical()) : 0.0F);
        accuracySpeedMultiplier = FightOrFlightAdapter.actionAccuracyProjectileMultiplier(shooter);
        maxLifetimeTicks = ActionProjectileProfile.maxLifetimeTicks(move.getName());
        Vec3 trackedTarget = target instanceof PokemonEntity pokemonTarget
                ? ActionBattleEvasionController.trackedPosition(pokemonTarget, level.getGameTime()).add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                : target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        double d = trackedTarget.x - getX();
        double e = trackedTarget.y - getY();
        double f = trackedTarget.z - getZ();
        if (ActionProjectileProfile.isLobbed(move.getName())) e += Math.sqrt(d * d + f * f) * 0.30D;
        shoot(d, e, f, (float) projectileSpeed(move.getName()), 0.0F);
    }


    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, Move move, Vec3 direction) {
        this(level, shooter, move, direction, 1.0D);
    }

    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, Move move, Vec3 direction,
                                        double committedGrassMultiplier) {
        this(level, shooter, move, direction, committedGrassMultiplier, null);
    }

    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, Move move, Vec3 direction,
                                        double committedGrassMultiplier, ActionBattleGhostCast ghostCast) {
        this(level, shooter, move, direction, committedGrassMultiplier, 1.0D, ghostCast);
    }

    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, Move move, Vec3 direction,
                                        double committedGrassMultiplier, double ghostDamageMultiplier,
                                        ActionBattleGhostCast ghostCast) {
        this(level, shooter, move, direction, committedGrassMultiplier, ghostDamageMultiplier, ghostCast,
                ActionBattleCommittedMove.none());
    }

    public ActionBattleProjectileEntity(Level level, PokemonEntity shooter, Move move, Vec3 direction,
                                        double committedGrassMultiplier, double ghostDamageMultiplier,
                                        ActionBattleGhostCast ghostCast, ActionBattleCommittedMove committedContext) {
        super(ModEntities.ACTION_BATTLE_PROJECTILE.get(), level);
        initPosition(shooter);
        setOwner(shooter);
        setNoGravity(!ActionProjectileProfile.isLobbed(move.getName()));
        intendedTargetUUID = null;
        confusedShot = true;
        committedMoveName = move.getName();
        entityData.set(DATA_MOVE_NAME, committedMoveName);
        committedMove = move;
        this.committedGrassMultiplier = Math.max(1.0D, committedGrassMultiplier);
        this.ghostCast = ghostCast;
        this.ghostDamageMultiplier = Math.max(0.0D, ghostDamageMultiplier);
        this.committedContext = committedContext != null ? committedContext : ActionBattleCommittedMove.none();
        this.bugCast = null;
        setElementalType(move.getType().getName());
        setDamage(0.0F);
        accuracySpeedMultiplier = FightOrFlightAdapter.actionAccuracyProjectileMultiplier(shooter);
        maxLifetimeTicks = ActionProjectileProfile.maxLifetimeTicks(move.getName());
        Vec3 shot = direction.lengthSqr() > 0.000001D ? direction.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
        shoot(shot.x, shot.y, shot.z, (float) projectileSpeed(move.getName()), 0.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOVE_NAME, "");
    }

    public String committedMoveName() {
        String synced = entityData.get(DATA_MOVE_NAME);
        return synced.isEmpty() ? committedMoveName : synced;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) updateDeliveryMotion();
        if (!level().isClientSide && interceptBugCarapace()) return;
        super.tick();
        if (!level().isClientSide && (isRemoved() || tickCount >= maxLifetimeTicks)) {
            ActionBattleGhostRuntime.global().discard(ghostCast);
            if (!isRemoved()) discard();
        }
    }

    private void updateDeliveryMotion() {
        if (!(level() instanceof ServerLevel serverLevel) || intendedTargetUUID == null) return;
        Entity rawTarget = serverLevel.getEntity(intendedTargetUUID);
        if (!(rawTarget instanceof LivingEntity target) || !target.isAlive()) return;
        double speed = projectileSpeed(committedMoveName());
        if (ActionProjectileProfile.isHoming(committedMoveName())) {
            Vec3 trackedEye = target instanceof PokemonEntity pokemonTarget
                    ? ActionBattleEvasionController.trackedEyePosition(pokemonTarget, serverLevel.getGameTime()) : target.getEyePosition();
            Vec3 delta = trackedEye.subtract(position());
            if (delta.lengthSqr() > 0.000001D) setDeltaMovement(delta.normalize().scale(speed));
            return;
        }
        if (ActionProjectileProfile.isGroundHuggingWave(committedMoveName())) {
            BlockPos column = blockPosition();
            double surfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) + 0.15D;
            Vec3 current = getDeltaMovement();
            double vertical = Math.max(-0.20D, Math.min(0.20D, surfaceY - getY()));
            Vec3 horizontal = new Vec3(current.x, 0.0D, current.z);
            if (horizontal.lengthSqr() < 0.000001D) {
                Vec3 tracked = target instanceof PokemonEntity pokemonTarget
                        ? ActionBattleEvasionController.trackedPosition(pokemonTarget, serverLevel.getGameTime()) : target.position();
                horizontal = new Vec3(tracked.x - getX(), 0.0D, tracked.z - getZ());
            }
            if (horizontal.lengthSqr() > 0.000001D) horizontal = horizontal.normalize().scale(speed);
            setDeltaMovement(horizontal.x, vertical, horizontal.z);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        boolean eligible = confusedShot
                ? target instanceof LivingEntity && target != getOwner() && super.canHitEntity(target)
                : intendedTargetUUID != null && intendedTargetUUID.equals(target.getUUID()) && super.canHitEntity(target);
        if (!eligible || !(target instanceof PokemonEntity pokemonTarget)) return eligible;
        Entity owner = getOwner();
        Move move = owner instanceof PokemonEntity attacker ? resolveCommittedMove(attacker) : null;
        boolean buriedAware = ActionBattleGroundController.isQualifyingMove(move);
        var effectiveBox = ActionBattleGroundController.effectiveCombatBox(
                pokemonTarget, level().getGameTime(), buriedAware).inflate(getBbWidth() * 0.5D);
        return ActionBattleGroundController.segmentIntersects(
                effectiveBox, position(), position().add(getDeltaMovement()));
    }

    private boolean interceptBugCarapace() {
        if (!(level() instanceof ServerLevel server) || !(getOwner() instanceof PokemonEntity attacker)) return false;
        var swept = getBoundingBox().expandTowards(getDeltaMovement()).inflate(0.15D);
        BlockPos min = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
        BlockPos max = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (!(server.getBlockEntity(new BlockPos(x, y, z))
                            instanceof ActionBattleBugCarapaceBlockEntity carapace)
                            || !carapace.collisionBox().intersects(swept)) continue;
                    ActionBattleBugCarapaceState.ProjectileOutcome outcome = carapace.collideProjectile(
                            attacker.getPokemon().getUuid(), server.getGameTime());
                    if (outcome == ActionBattleBugCarapaceState.ProjectileOutcome.DESTROY_BOTH) {
                        ActionBattleGhostRuntime.global().discard(ghostCast);
                        discard();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity owner = getOwner();
        Entity rawTarget = result.getEntity();
        if (!(owner instanceof PokemonEntity attacker) || !(rawTarget instanceof LivingEntity target)) {
            ActionBattleGhostRuntime.global().discard(ghostCast);
            discard();
            return;
        }
        Move move = resolveCommittedMove(attacker);
        if (move == null) {
            ActionBattleGhostRuntime.global().discard(ghostCast);
            discard();
            return;
        }
        UUID guardedBattle = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
        if (rawTarget instanceof PokemonEntity guardedTarget && guardedBattle != null
                && net.epiac9.cobblemonnml.battle.action.ActionBattleSwapTransitionGuard.rejectsHit(
                guardedBattle, guardedTarget.getPokemon().getUuid())) {
            ActionBattleGhostRuntime.global().discard(ghostCast);
            discard();
            return;
        }
        if (bugFollowup) {
            if (target instanceof PokemonEntity pokemonTarget) {
                PokemonAttackEffect.applyOnHitVisualEffect(attacker, pokemonTarget, move);
                PokemonAttackEffect.applySFX(attacker.level(), move, attacker.blockPosition());
                ActionBattleBugRuntime.resolveFollowupImpact(
                        bugCast, pokemonTarget, bugFollowupDamage, bugFollowupArea);
            }
            discard();
            return;
        }
        boolean nativeDamageMove = FightOrFlightAdapter.isNativeDamageMove(move);
        PokemonEntity pokemonTarget = target instanceof PokemonEntity value ? value : null;
        if (confusedShot && bugCast == null && pokemonTarget != null) {
            bugCast = ActionBattleBugRuntime.arm(attacker, pokemonTarget, move).orElse(null);
        }
        ActionBattleGroundController.HitPlan groundPlan = nativeDamageMove && pokemonTarget != null
                ? ActionBattleGroundController.planHit(attacker, pokemonTarget, move)
                : ActionBattleGroundController.HitPlan.NOT_QUALIFYING;
        if (nativeDamageMove) setDamage(ActionBattleCriticalRules.apply(
                FightOrFlightAdapter.scaleActionDamage(attacker, target, move,
                        PokemonAttackEffect.calculatePokemonDamage(attacker, target, move), committedGrassMultiplier),
                committedContext.critical()));
        int beforeHp = pokemonTarget != null ? pokemonTarget.getPokemon().getCurrentHealth() : 0;
        int attemptedPokemonDamage = pokemonTarget != null ? ActionBattleWaterHealth.toPokemonDamage(
                pokemonTarget.getPokemon().getMaxHealth(), pokemonTarget.getMaxHealth(), getDamage()) : 0;
        ActionBattleSession sleepSession = pokemonTarget != null ? ActionBattleManager.findSessionForBattlePokemonEntity(pokemonTarget.getUUID()) : null;
        long currentTick = attacker.level().getGameTime();
        ActionBattleSleepController.WakePlan wakePlan = nativeDamageMove && pokemonTarget != null
                ? ActionBattleSleepController.planDamagingWake(sleepSession, pokemonTarget, currentTick, true,
                ActionBattleFairyController.hasType(attacker.getPokemon(), "fairy"))
                : ActionBattleSleepController.WakePlan.NONE;
        if (nativeDamageMove) {
            FightOrFlightAdapter.applyOnUseEffectsWithoutActionStatuses(attacker, target, move);
        } else {
            ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, target, move,
                    ActionBattleMoveEffectResolver.StatTrigger.BEFORE_USE, true);
            ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, target, move,
                    ActionBattleMoveEffectResolver.StatTrigger.ON_USE, true);
        }
        boolean success = !nativeDamageMove || target.hurt(damageSources().indirectMagic(this, attacker), getDamage());
        if (nativeDamageMove && success) attacker.setLastHurtMob(target);
        if (nativeDamageMove) PokemonUtils.setHurtByPlayer(attacker, target);
        PokemonAttackEffect.applyOnHitVisualEffect(attacker, target, move);
        PokemonAttackEffect.applySFX(attacker.level(), move, attacker.blockPosition());
        if (nativeDamageMove) {
            FightOrFlightAdapter.applyPostEffectsWithoutActionStatuses(attacker, target, move, success);
        } else {
            ActionBattleMoveEffectResolver.applyDeclaredStatChanges(attacker, target, move,
                    ActionBattleMoveEffectResolver.StatTrigger.ON_HIT, success);
        }
        if (pokemonTarget != null) {
            boolean qualifyingWaterInteraction = success;
            FightOrFlightAdapter.ProtectionOutcome protection = FightOrFlightAdapter.applyProtectImpact(
                    attacker, pokemonTarget, move, beforeHp, attemptedPokemonDamage, success);
            int actualBugTriggerDamage = nativeDamageMove && success
                    ? ActionBattleBugRuntime.resolveIncomingDamage(pokemonTarget, beforeHp) : 0;
            ActionBattleBugRuntime.resolveHit(bugCast, pokemonTarget, actualBugTriggerDamage, move);
            ActionBattleFightingRuntime.onSuccessfulHit(attacker, move, success,
                    protection.protectParticipated() || protection.aquaParticipated());
            net.epiac9.cobblemonnml.battle.action.typeeffect.dark.ActionBattleDarkRuntime
                    .onConnectedHit(attacker, pokemonTarget, move, success);
            ActionBattleRockRuntime.HitResult rockHit = nativeDamageMove
                    ? ActionBattleRockRuntime.resolveDirectHit(attacker, pokemonTarget, beforeHp,
                    protection.incomingDamage(), success, protection.protectParticipated())
                    : ActionBattleRockRuntime.HitResult.NONE;
            if (nativeDamageMove) ActionBattleGroundController.resolveAfterDamage(
                    groundPlan, attacker, pokemonTarget, beforeHp);
            if (success) ActionBattleGrassController.onPokemonDamageResolved(attacker, pokemonTarget,
                    Math.max(0, beforeHp - pokemonTarget.getPokemon().getCurrentHealth()));
            if (nativeDamageMove && success) ActionBattleFireController.onSuccessfulMoveHit(attacker, pokemonTarget, move, ActionBattleFireRules.NORMAL_PRESSURE);
            if (qualifyingWaterInteraction) ActionBattleWaterController.onSuccessfulInteraction(attacker, pokemonTarget, move);
            if (success) ActionBattleGrassController.onSuccessfulMoveResolved(attacker, pokemonTarget, move);
            if (nativeDamageMove && success && beforeHp > pokemonTarget.getPokemon().getCurrentHealth()) {
                ActionBattleElectricController.onSuccessfulMoveHit(attacker, pokemonTarget, move);
            }
            if (!nativeDamageMove && success) {
                ActionBattleElectricController.onSuccessfulEnemyInteraction(attacker, pokemonTarget, move);
            }
            if (nativeDamageMove && ActionBattleIceRules.isQualifyingDamagingHit(success, beforeHp, pokemonTarget.getPokemon().getCurrentHealth())) {
                ActionBattleIceController.onSuccessfulMoveHit(attacker, pokemonTarget, move);
            }
            if (nativeDamageMove && ActionBattlePoisonRules.isQualifyingDamagingHit(
                    success, beforeHp, pokemonTarget.getPokemon().getCurrentHealth())) {
                ActionBattlePoisonController.onSuccessfulEnemyInteraction(attacker, pokemonTarget, move);
            }
            if (nativeDamageMove && success) ActionBattleSleepController.applyWakeDamageAndWake(sleepSession, pokemonTarget, currentTick, beforeHp, wakePlan);
            UUID battleId = ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID());
            if (battleId == null) battleId = ActionBattleManager.battleIdForPokemonEntity(pokemonTarget.getUUID());
            if (battleId != null) ActionBattleDamageFeedbackController.global().recordDamage(battleId, pokemonTarget.getPokemon().getUuid(), beforeHp, pokemonTarget.getPokemon().getCurrentHealth(), ActionBattleDamageFeedbackCategory.NORMAL);
            ActionBattleMoveEffectResolver.applyDeclaredFlinchOnHit(attacker, pokemonTarget, move, success);
            ActionBattleMoveEffectResolver.applyDeclaredConfusionOnHit(attacker, pokemonTarget, move, success);
            ActionBattleMoveEffectResolver.applyDeclaredParalysisOnHit(attacker, pokemonTarget, move, success);
            ActionBattlePsycUpController.onSuccessfulEnemyMoveResolved(attacker, pokemonTarget, move, success);
            if (!nativeDamageMove && success) ActionBattleFairyController.onSuccessfulEnemyTargetingMove(attacker, pokemonTarget, move);
            if (!nativeDamageMove && success) ActionBattlePoisonController.onSuccessfulEnemyInteraction(attacker, pokemonTarget, move);
            if (success) ActionBattleGhostRuntime.global().connect(ghostCast, pokemonTarget);
            else ActionBattleGhostRuntime.global().discard(ghostCast);
            ActionBattleGhostRuntime.global().onDamageResolved(pokemonTarget, beforeHp);
            ActionBattleRockRuntime.applyReflection(attacker, rockHit);
            if (net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime.isActive(
                    pokemonTarget,
                    net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRules.Branch.WEIGHTED,
                    currentTick)) {
                pokemonTarget.setDeltaMovement(Vec3.ZERO);
                pokemonTarget.hurtMarked = true;
            }
            if (success && !protection.protectParticipated() && !protection.aquaParticipated()
                    && net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime.isActive(
                    attacker,
                    net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRules.Branch.MAGNET_RISE,
                    currentTick)
                    && move.getType() != null && "steel".equalsIgnoreCase(move.getType().getName())
                    && FightOrFlightAdapter.isRangedMove(move)) {
                net.epiac9.cobblemonnml.battle.action.interrupt.ActionBattleInterruptController.apply(
                        ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID()), pokemonTarget,
                        net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRules.MAGNET_RISE_INTERRUPT_TICKS,
                        currentTick);
            }
        } else {
            ActionBattleGhostRuntime.global().discard(ghostCast);
        }
        discard();
    }

    private Move resolveCommittedMove(PokemonEntity attacker) {
        if (committedMove != null && committedMoveName.equals(committedMove.getName())) return committedMove;
        for (Move move : attacker.getPokemon().getMoveSet()) {
            if (move != null && committedMoveName.equals(move.getName())) {
                committedMove = move;
                return move;
            }
        }
        return null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (intendedTargetUUID != null) tag.putUUID("ActionTarget", intendedTargetUUID);
        tag.putString("ActionMove", committedMoveName);
        tag.putInt("ActionLifetime", maxLifetimeTicks);
        tag.putBoolean("ActionConfusedShot", confusedShot);
        tag.putDouble("ActionAccuracySpeedMultiplier", accuracySpeedMultiplier);
        tag.putDouble("ActionGrassMultiplier", committedGrassMultiplier);
        tag.putDouble("ActionGhostDamageMultiplier", ghostDamageMultiplier);
        tag.putInt("ActionFlyingMomentum", committedContext.flyingMomentum());
        tag.putInt("ActionCriticalBase", committedContext.critical().baseStage());
        tag.putInt("ActionCriticalFlying", committedContext.critical().flyingBonus());
        tag.putInt("ActionCriticalStage", committedContext.critical().effectiveStage());
        tag.putDouble("ActionCriticalRoll", committedContext.critical().roll());
        tag.putBoolean("ActionCriticalHit", committedContext.critical().critical());
        if (ghostCast != null) {
            tag.putUUID("ActionGhostCast", ghostCast.castId());
            tag.putUUID("ActionGhostBattle", ghostCast.battleId());
            tag.putUUID("ActionGhostCaster", ghostCast.casterPokemonUUID());
            tag.putBoolean("ActionGhostTyped", ghostCast.ghostCaster());
        }
        if (bugCast != null) {
            tag.putUUID("ActionBugCast", bugCast.castId());
            tag.putUUID("ActionBugBattle", bugCast.battleId());
            tag.putUUID("ActionBugUser", bugCast.userPokemonId());
            tag.putUUID("ActionBugTarget", bugCast.intendedTargetPokemonId());
            tag.putBoolean("ActionBugTyped", bugCast.bugTyped());
            tag.putString("ActionBugMove", bugCast.moveName());
            tag.putBoolean("ActionBugRanged", bugCast.ranged());
            int mask = 0;
            for (ActionBattleBugTrainingStat stat : bugCast.activated()) mask |= 1 << stat.ordinal();
            tag.putInt("ActionBugBranches", mask);
        }
        tag.putBoolean("ActionBugFollowup", bugFollowup);
        tag.putInt("ActionBugFollowupDamage", bugFollowupDamage);
        tag.putBoolean("ActionBugFollowupArea", bugFollowupArea);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        intendedTargetUUID = tag.hasUUID("ActionTarget") ? tag.getUUID("ActionTarget") : null;
        committedMoveName = tag.getString("ActionMove");
        entityData.set(DATA_MOVE_NAME, committedMoveName);
        maxLifetimeTicks = tag.contains("ActionLifetime") ? tag.getInt("ActionLifetime") : 80;
        confusedShot = tag.getBoolean("ActionConfusedShot");
        accuracySpeedMultiplier = tag.contains("ActionAccuracySpeedMultiplier") ? tag.getDouble("ActionAccuracySpeedMultiplier") : 1.0D;
        committedGrassMultiplier = tag.contains("ActionGrassMultiplier") ? Math.max(1.0D, tag.getDouble("ActionGrassMultiplier")) : 1.0D;
        ghostDamageMultiplier = tag.contains("ActionGhostDamageMultiplier")
                ? Math.max(0.0D, tag.getDouble("ActionGhostDamageMultiplier")) : 1.0D;
        committedContext = new ActionBattleCommittedMove(tag.getInt("ActionFlyingMomentum"),
                new ActionBattleCriticalResult(tag.getInt("ActionCriticalBase"),
                        tag.getInt("ActionCriticalFlying"), tag.getInt("ActionCriticalStage"),
                        tag.contains("ActionCriticalRoll") ? tag.getDouble("ActionCriticalRoll") : 1.0D,
                        tag.getBoolean("ActionCriticalHit")));
        bugFollowup = tag.getBoolean("ActionBugFollowup");
        bugFollowupDamage = Math.max(0, tag.getInt("ActionBugFollowupDamage"));
        bugFollowupArea = tag.getBoolean("ActionBugFollowupArea");
        if (tag.hasUUID("ActionGhostCast") && tag.hasUUID("ActionGhostBattle")
                && tag.hasUUID("ActionGhostCaster")) {
            ghostCast = new ActionBattleGhostCast(tag.getUUID("ActionGhostCast"),
                    tag.getUUID("ActionGhostBattle"), tag.getUUID("ActionGhostCaster"),
                    tag.getBoolean("ActionGhostTyped"), true);
            ActionBattleGhostRuntime.global().restore(ghostCast);
        }
        if (tag.hasUUID("ActionBugCast") && tag.hasUUID("ActionBugBattle")
                && tag.hasUUID("ActionBugUser") && tag.hasUUID("ActionBugTarget")) {
            java.util.EnumSet<ActionBattleBugTrainingStat> activated =
                    java.util.EnumSet.noneOf(ActionBattleBugTrainingStat.class);
            int mask = tag.getInt("ActionBugBranches");
            for (ActionBattleBugTrainingStat stat : ActionBattleBugTrainingStat.values()) {
                if ((mask & 1 << stat.ordinal()) != 0) activated.add(stat);
            }
            bugCast = new ActionBattleBugCast(tag.getUUID("ActionBugCast"),
                    tag.getUUID("ActionBugBattle"), tag.getUUID("ActionBugUser"),
                    tag.getUUID("ActionBugTarget"), tag.getBoolean("ActionBugTyped"), activated,
                    tag.getString("ActionBugMove"), tag.getBoolean("ActionBugRanged"));
        }
    }
    private double projectileSpeed(String moveName) {
        double flying = ActionBattleFlyingRules.projectileMultiplier(
                committedMove != null && ActionBattleFlyingRules.isFlyingMove(committedMove),
                committedContext.flyingMomentum());
        if (flying > 1.0D && !flyingSpeedLogged) {
            flyingSpeedLogged = true;
            DebugLog.log("[CobblemonNML] Flying projectile multiplier. move=" + moveName
                    + ", momentum=" + committedContext.flyingMomentum() + ", multiplier=" + flying);
        }
        double base = ActionProjectileProfile.speedBlocksPerTick(moveName) * accuracySpeedMultiplier * flying;
        return getOwner() instanceof PokemonEntity attacker
                ? net.epiac9.cobblemonnml.battle.action.typeeffect.steel.ActionBattleSteelRuntime
                .projectileSpeed(attacker, committedMove, base, level().getGameTime()) : base;
    }

}

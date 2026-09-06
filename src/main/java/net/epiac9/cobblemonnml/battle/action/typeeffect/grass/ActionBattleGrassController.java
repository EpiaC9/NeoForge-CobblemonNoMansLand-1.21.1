package net.epiac9.cobblemonnml.battle.action.typeeffect.grass;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.moves.Move;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.ActionBattleState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattlePokemonHealth;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObjectTracker;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldPlacement;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.field.GrassFlowerBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.field.GrassFlowerLifecycle;
import net.epiac9.cobblemonnml.battle.action.typeeffect.grass.field.GrassSeedBlockEntity;
import net.epiac9.cobblemonnml.mixin.ActionBattleLivingEntityAccessor;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.epiac9.cobblemonnml.battle.action.projectile.lob.ActionBattleLobPayload;
import net.epiac9.cobblemonnml.battle.action.projectile.lob.ActionBattleLobProjectileEntity;
import net.epiac9.cobblemonnml.battle.action.projectile.wave.ActionBattleWaveServerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.Locale;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public final class ActionBattleGrassController {
    private static final ActionBattleFieldObjectTracker FIELDS =
            new ActionBattleFieldObjectTracker(ActionBattleGrassRules.MAX_FIELD_OBJECTS);
    private static long nextSequence;

    private ActionBattleGrassController() {}

    public static boolean isQualifyingMove(Move move) {
        return move != null && ActionBattleGrassDeliveryRules.qualifies(
                move.getType() != null ? move.getType().getName() : null);
    }

    public static ActionBattleGrassState.GrassMoveCommit commitMove(PokemonEntity caster, Move move) {
        ActionBattleSession session = caster != null ? ActionBattleManager.findSessionForBattlePokemonEntity(caster.getUUID()) : null;
        if (session == null || session.state() != ActionBattleState.ACTIVE) {
            return new ActionBattleGrassState.GrassMoveCommit(1.0D, false);
        }
        return ActionBattleTypeEffectController.global().commitGrassMove(
                session.dungeonSessionId(), caster.getPokemon().getUuid(), isQualifyingMove(move));
    }

    public static void restoreEmpower(PokemonEntity caster, ActionBattleGrassState.GrassMoveCommit commit) {
        ActionBattleSession session = caster != null ? ActionBattleManager.findSessionForBattlePokemonEntity(caster.getUUID()) : null;
        if (session != null && commit != null && commit.consumed()) {
            ActionBattleTypeEffectController.global().applyGrassEmpower(
                    session.dungeonSessionId(), caster.getPokemon().getUuid(), commit.capturedDamageMultiplier());
        }
    }

    public static int onSuccessfulMoveResolved(PokemonEntity caster, PokemonEntity affectedPokemon, Move move) {
        if (caster == null || !isQualifyingMove(move) || !(caster.level() instanceof ServerLevel level)) return 0;
        PokemonEntity anchorEntity = ActionBattleGrassDeliveryRules.anchor(caster, affectedPokemon);
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(caster.getUUID());
        if (session == null || session.state() != ActionBattleState.ACTIVE
                || affectedPokemon != null && !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(affectedPokemon.getUUID()))) return 0;
        UUID owner = caster.getPokemon().getUuid();
        ActionBattleFieldObject.OwnerSide ownerSide = side(session, owner);
        if (ownerSide == null) return 0;
        BlockPos anchor = anchorEntity.blockPosition();
        Set<ActionBattleFieldPlacement.Position> reserved = new HashSet<>();
        int launched = 0;
        for (int index = 0; index < ActionBattleGrassDeliveryRules.SEED_LOB_COUNT; index++) {
            var candidates = ActionBattleFieldPlacement.validCandidates(shared(anchor), reserved, candidate -> {
                BlockPos pos = blockPos(candidate);
                var path = anchorEntity.getNavigation().createPath(pos, 0);
                return validPlacement(level, pos) && path != null && path.canReach();
            });
            if (candidates.isEmpty()) break;
            var selected = candidates.get(level.random.nextInt(candidates.size()));
            reserved.add(selected);
            BlockPos destination = blockPos(selected);
            var payload = new ActionBattleLobPayload(ActionBattleLobPayload.PayloadKind.GRASS_SEED,
                    session.dungeonSessionId(), session.battleId(), owner, ownerSide, anchor, destination);
            level.addFreshEntity(new ActionBattleLobProjectileEntity(level,
                    caster.position().add(0, caster.getBbHeight() * 0.5D, 0), payload, 20, 3.0D));
            launched++;
        }
        return launched;
    }

    public static void touchSeed(GrassSeedBlockEntity seed, PokemonEntity toucher) {
        if (seed == null || toucher == null || seed.getLevel() == null || seed.lifecycle() == null
                || !activeParticipant(toucher, seed.lifecycle().sessionId()) || !seed.lifecycle().consumeFirst()) return;
        removeSeed(seed);
    }

    public static void bloom(GrassSeedBlockEntity seed) {
        if (seed == null || seed.getLevel() == null || seed.lifecycle() == null
                || !seed.lifecycle().readyToBloom(seed.getLevel().getGameTime())) return;
        GrassFlowerLifecycle flowerLife = seed.lifecycle().bloom(seed.getLevel().getGameTime());
        BlockPos pos = seed.getBlockPos();
        if (!seed.getLevel().setBlock(pos, ModBlocks.GRASS_FLOWER.get().defaultBlockState(), 3)) return;
        BlockEntity raw = seed.getLevel().getBlockEntity(pos);
        if (!(raw instanceof GrassFlowerBlockEntity flower)) { seed.getLevel().removeBlock(pos, false); return; }
        flower.initialize(flowerLife.sessionId(), flowerLife.ownerPokemonUUID(), flowerLife.ownerSide(),
                flowerLife.bloomTick(), flowerLife.originalCreationTick(), flowerLife.creationSequence());
        ActionBattleFieldObject.Position trackedPos = position(pos);
        FIELDS.replace(flowerLife.sessionId(), trackedPos, new ActionBattleFieldObject(flowerLife.sessionId(),
                flowerLife.ownerPokemonUUID(), flowerLife.ownerSide(), seed.getLevel().dimension().location().toString(),
                trackedPos, flowerLife.originalCreationTick(), flowerLife.creationSequence(),
                flowerLife.bloomTick() + ActionBattleGrassRules.FLOWER_LIFETIME_TICKS));
    }

    public static void touchFlower(GrassFlowerBlockEntity flower, PokemonEntity toucher) {
        if (flower == null || toucher == null || flower.getLevel() == null || flower.lifecycle() == null
                || !activeParticipant(toucher, flower.lifecycle().sessionId())) return;
        GrassFlowerLifecycle life = flower.lifecycle();
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(toucher.getUUID());
        if (session == null || !life.consumeFirst()) return;
        UUID toucherId = toucher.getPokemon().getUuid();
        ActionBattleFieldObject.OwnerSide toucherSide = side(session, toucherId);
        if (toucherSide == null) return;
        boolean allied = toucherSide == life.ownerSide();
        boolean grassTyped = hasType(toucher.getPokemon(), "grass");
        long tick = flower.getLevel().getGameTime();
        ActionBattleTypeEffectController effects = ActionBattleTypeEffectController.global();
        effects.guardSession(life.sessionId());
        boolean seeded = effects.leechSeedView(life.sessionId(), toucherId, tick).isPresent();
        switch (ActionBattleGrassContactRules.resolve(allied, grassTyped, seeded)) {
            case ALLY_EMPOWER_110 -> effects.applyGrassEmpower(life.sessionId(), toucherId, ActionBattleGrassRules.ALLY_EMPOWER);
            case ALLY_EMPOWER_120 -> effects.applyGrassEmpower(life.sessionId(), toucherId, ActionBattleGrassRules.GRASS_ALLY_EMPOWER);
            case ENEMY_MOVEMENT -> effects.applyGrassMovement(life.sessionId(), toucherId, tick);
            case ENEMY_LEECH_SEED -> effects.applyLeechSeed(life.sessionId(), toucherId, tick);
            case ENEMY_LEECH_REACTIVATION -> {
                int actualDamage = ActionBattlePokemonHealth.damage(healthAccess(toucher.getPokemon()),
                        ActionBattleGrassRules.reactivationDamage(toucher.getPokemon().getMaxHealth()));
                int waveHeal = ActionBattleGrassRules.waveHealAmount(actualDamage);
                if (waveHeal > 0) ActionBattleWaveServerRuntime.launchHealing(
                        life.sessionId(), toucher.getPokemon().getUuid(), toucher.position(), waveHeal, tick);
            }
        }
        removeFlower(flower);
    }

    public static int onPokemonDamageResolved(PokemonEntity dealer, PokemonEntity seededTarget, int actualDamage) {
        if (dealer == null || seededTarget == null || actualDamage <= 0) return 0;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(seededTarget.getUUID());
        if (session == null || ActionBattleTypeEffectController.global().leechSeedView(session.dungeonSessionId(),
                seededTarget.getPokemon().getUuid(), seededTarget.level().getGameTime()).isEmpty()) return 0;
        return ActionBattlePokemonHealth.heal(healthAccess(dealer.getPokemon()),
                ActionBattleGrassRules.leechHealAmount(actualDamage, hasType(dealer.getPokemon(), "grass")));
    }

    public static int healPokemon(Pokemon pokemon, int requested) {
        return pokemon == null ? 0 : ActionBattlePokemonHealth.heal(healthAccess(pokemon), requested);
    }

    public static boolean createSeed(ServerLevel level, UUID sessionId, UUID owner,
                                     ActionBattleFieldObject.OwnerSide ownerSide, BlockPos pos) {
        if (level == null || sessionId == null || owner == null || ownerSide == null || pos == null
                || !level.getBlockState(pos).isAir()) return false;
        long tick = level.getGameTime();
        long sequence = nextSequence++;
        if (!level.setBlock(pos, ModBlocks.GRASS_SEED.get().defaultBlockState(), 3)) return false;
        if (!(level.getBlockEntity(pos) instanceof GrassSeedBlockEntity seed)) { level.removeBlock(pos, false); return false; }
        seed.initialize(sessionId, owner, ownerSide, tick, sequence);
        ActionBattleFieldObject tracked = new ActionBattleFieldObject(sessionId, owner, ownerSide,
                level.dimension().location().toString(), position(pos), tick, sequence,
                tick + ActionBattleGrassRules.SEED_ARM_TICKS);
        FIELDS.register(tracked).ifPresent(evicted -> removeTracked(level, evicted));
        return true;
    }

    public static boolean onSeedLobImpact(ServerLevel level, ActionBattleLobPayload payload, BlockPos pos) {
        return payload != null && payload.kind() == ActionBattleLobPayload.PayloadKind.GRASS_SEED
                && createSeed(level, payload.sessionId(), payload.ownerPokemonId(), payload.ownerSide(), pos);
    }

    public static void removeSeed(GrassSeedBlockEntity seed) { remove(seed, seed != null ? seed.lifecycle() : null); }
    public static void removeFlower(GrassFlowerBlockEntity flower) { remove(flower, flower != null ? flower.lifecycle() : null); }
    public static void unregisterSeed(GrassSeedBlockEntity seed) {
        if (seed != null && seed.lifecycle() != null) FIELDS.unregister(seed.lifecycle().sessionId(), position(seed.getBlockPos()));
    }
    public static void unregisterFlower(GrassFlowerBlockEntity flower) {
        if (flower != null && flower.lifecycle() != null) FIELDS.unregister(flower.lifecycle().sessionId(), position(flower.getBlockPos()));
    }
    public static void clearSession(ServerLevel level, UUID sessionId) {
        for (ActionBattleFieldObject object : FIELDS.clearSession(sessionId)) removeTracked(level, object);
    }
    public static void clearAll() { FIELDS.clearAll(); }

    private static void remove(BlockEntity entity, Object lifecycle) {
        if (entity == null || entity.getLevel() == null) return;
        if (lifecycle instanceof net.epiac9.cobblemonnml.battle.action.typeeffect.grass.field.GrassSeedLifecycle seed) {
            FIELDS.unregister(seed.sessionId(), position(entity.getBlockPos()));
        } else if (lifecycle instanceof GrassFlowerLifecycle flower) {
            FIELDS.unregister(flower.sessionId(), position(entity.getBlockPos()));
        }
        entity.getLevel().removeBlock(entity.getBlockPos(), false);
    }
    private static boolean activeParticipant(PokemonEntity pokemon, UUID sessionId) {
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(pokemon.getUUID());
        return session != null && session.state() == ActionBattleState.ACTIVE && sessionId.equals(session.dungeonSessionId());
    }
    private static ActionBattleFieldObject.OwnerSide side(ActionBattleSession session, UUID pokemonId) {
        return pokemonId.equals(session.playerActivePokemonUUID()) ? ActionBattleFieldObject.OwnerSide.PLAYER
                : pokemonId.equals(session.trainerActivePokemonUUID()) ? ActionBattleFieldObject.OwnerSide.TRAINER : null;
    }
    private static boolean hasType(Pokemon pokemon, String type) {
        return pokemon != null && (type.equals(normalize(pokemon.getPrimaryType() != null ? pokemon.getPrimaryType().getName() : null))
                || type.equals(normalize(pokemon.getSecondaryType() != null ? pokemon.getSecondaryType().getName() : null)));
    }
    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }
    private static ActionBattleFieldObject.Position position(BlockPos pos) { return new ActionBattleFieldObject.Position(pos.getX(), pos.getY(), pos.getZ()); }
    private static ActionBattleFieldPlacement.Position shared(BlockPos pos) { return new ActionBattleFieldPlacement.Position(pos.getX(), pos.getY(), pos.getZ()); }
    private static BlockPos blockPos(ActionBattleFieldPlacement.Position pos) { return new BlockPos(pos.x(), pos.y(), pos.z()); }
    private static boolean validPlacement(ServerLevel level, BlockPos pos) {
        return pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight() - 1
                && level.isLoaded(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }
    private static void removeTracked(ServerLevel level, ActionBattleFieldObject object) {
        if (level != null && level.dimension().location().toString().equals(object.dimensionId())) {
            level.removeBlock(new BlockPos(object.position().x(), object.position().y(), object.position().z()), false);
        }
    }
    private static ActionBattlePokemonHealth.Access healthAccess(Pokemon pokemon) {
        PokemonEntity entity = pokemon.getEntity();
        boolean deployed = entity != null && !entity.isRemoved();
        return new ActionBattlePokemonHealth.Access() {
            @Override public int currentHealth() { return pokemon.getCurrentHealth(); }
            @Override public int maxHealth() { return pokemon.getMaxHealth(); }
            @Override public boolean deployed() { return deployed; }
            @Override public float liveMaxHealth() { return deployed ? entity.getMaxHealth() : 0; }
            @Override public void setCurrentHealth(int value) { pokemon.setCurrentHealth(value); }
            @Override public void setLiveHealth(float value) {
                if (!deployed) return; entity.setHealth(value);
                if (value > 0) { entity.deathTime = 0; ((ActionBattleLivingEntityAccessor) entity).cobblemonNml$setDead(false); }
            }
        };
    }
}

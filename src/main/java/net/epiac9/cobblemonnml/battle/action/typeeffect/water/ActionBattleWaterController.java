package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleEffectiveMoveTypeResolver;
import net.epiac9.cobblemonnml.battle.action.effect.ActionBattleEffectApplicationGuard;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObjectTracker;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.field.AquaBubbleBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.field.WaterFieldPlacement;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.epiac9.cobblemonnml.battle.action.projectile.lob.ActionBattleLobPayload;
import net.epiac9.cobblemonnml.battle.action.projectile.lob.ActionBattleLobProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

public final class ActionBattleWaterController {
    private static final ActionBattleFieldObjectTracker BUBBLES =
            new ActionBattleFieldObjectTracker(ActionBattleWaterRules.MAX_BUBBLES_PER_OWNER);
    private static long nextSequence;

    private ActionBattleWaterController() {}

    public static boolean isQualifyingInteraction(Move move) {
        return move != null && ActionBattleWaterContactRules.isQualifyingInteraction(
                move.getType() != null ? move.getType().getName() : null,
                FightOrFlightAdapter.isNativeDamageMove(move), FightOrFlightAdapter.movePower(move),
                FightOrFlightAdapter.moveTargetCategory(move));
    }

    public static boolean isQualifyingInteraction(PokemonEntity attacker, Move move) {
        return attacker != null && move != null
                && ActionBattleTypeMechanicIdentity.hasMechanicBenefit(attacker, "water");
    }

    public static boolean onOwnedActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.pokemon() == null || context.move() == null) return false;
        PokemonEntity target = context.target() instanceof PokemonEntity pokemon ? pokemon : null;
        return launchBubble(context.pokemon(), target, context.move());
    }

    public static boolean onSuccessfulInteraction(PokemonEntity attacker, PokemonEntity target, Move move) {
        if (attacker == null || move == null || !isQualifyingInteraction(attacker, move)
                || !DungeonSession.isActive() || !(attacker.level() instanceof ServerLevel level)) return false;
        return launchBubble(attacker, target, move);
    }

    private static boolean launchBubble(PokemonEntity attacker, PokemonEntity target, Move move) {
        if (attacker == null || move == null || !DungeonSession.isActive()
                || !(attacker.level() instanceof ServerLevel level)) return false;
        PokemonEntity affected = target != null ? target : attacker;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(affected.getUUID()))
                || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return false;
        UUID owner = attacker.getPokemon().getUuid();
        ActionBattleFieldObject.OwnerSide side = session.isPlayerPokemon(owner)
                ? ActionBattleFieldObject.OwnerSide.PLAYER : owner.equals(session.trainerActivePokemonUUID())
                ? ActionBattleFieldObject.OwnerSide.TRAINER : null;
        if (side == null) return false;
        PokemonEntity anchor = FightOrFlightAdapter.isSelfOrAllyTargetCategory(
                FightOrFlightAdapter.moveTargetCategory(move)) ? attacker : affected;
        var candidates = WaterFieldPlacement.validCandidates(
                new WaterFieldPlacement.Position(anchor.blockPosition().getX(), anchor.blockPosition().getY(), anchor.blockPosition().getZ()),
                candidate -> {
                    BlockPos pos = blockPos(candidate);
                    var path = anchor.getNavigation().createPath(pos, 0);
                    return validPlacement(level, pos) && path != null && path.canReach();
                });
        if (candidates.isEmpty()) return false;
        BlockPos destination = blockPos(candidates.get(level.random.nextInt(candidates.size())));
        var payload = new ActionBattleLobPayload(ActionBattleLobPayload.PayloadKind.AQUA_BUBBLE,
                session.dungeonSessionId(), session.battleId(), owner, side, anchor.blockPosition(), destination);
        return level.addFreshEntity(new ActionBattleLobProjectileEntity(level,
                attacker.position().add(0, attacker.getBbHeight() * 0.5D, 0), payload, 20, 3.0D));
    }

    private static boolean placeBubble(ServerLevel level, UUID sessionId, UUID owner,
                                       ActionBattleFieldObject.OwnerSide side, PokemonEntity anchor) {
        return placeBubble(level, sessionId, owner, side, anchor.blockPosition(),
                position -> {
                    var path = anchor.getNavigation().createPath(position, 0);
                    return path != null && path.canReach();
                });
    }

    public static boolean placeBubble(ServerLevel level, UUID sessionId, UUID owner,
                                      ActionBattleFieldObject.OwnerSide side, BlockPos anchor) {
        return placeBubble(level, sessionId, owner, side, anchor, ignored -> true);
    }

    private static boolean placeBubble(ServerLevel level, UUID sessionId, UUID owner,
                                       ActionBattleFieldObject.OwnerSide side, BlockPos anchor,
                                       java.util.function.Predicate<BlockPos> reachable) {
        if (level == null || sessionId == null || owner == null || side == null || anchor == null) return false;
        var candidates = WaterFieldPlacement.validCandidates(
                new WaterFieldPlacement.Position(anchor.getX(), anchor.getY(), anchor.getZ()),
                candidate -> validPlacement(level, blockPos(candidate)) && reachable.test(blockPos(candidate)));
        if (candidates.isEmpty()) return false;
        BlockPos pos = blockPos(candidates.get(level.random.nextInt(candidates.size())));
        return createBubbleAt(level, sessionId, owner, side, pos);
    }

    public static boolean onLobImpact(ServerLevel level, ActionBattleLobPayload payload, BlockPos pos) {
        return payload != null && payload.kind() == ActionBattleLobPayload.PayloadKind.AQUA_BUBBLE
                && validPlacement(level, pos) && createBubbleAt(level, payload.sessionId(),
                payload.ownerPokemonId(), payload.ownerSide(), pos);
    }

    private static boolean createBubbleAt(ServerLevel level, UUID sessionId, UUID owner,
                                          ActionBattleFieldObject.OwnerSide side, BlockPos pos) {
        long tick = level.getGameTime();
        long sequence = nextSequence++;
        var owned = BUBBLES.objectsForOwner(sessionId, owner);
        if (owned.size() >= ActionBattleWaterRules.MAX_BUBBLES_PER_OWNER) {
            ActionBattleFieldObject oldest = owned.getFirst();
            BUBBLES.unregister(sessionId, oldest.position());
            removeTracked(level, oldest);
        }
        if (!level.setBlock(pos, ModBlocks.AQUA_BUBBLE.get().defaultBlockState(), 3)) return false;
        BlockEntity raw = level.getBlockEntity(pos);
        if (!(raw instanceof AquaBubbleBlockEntity bubble)) {
            level.removeBlock(pos, false);
            return false;
        }
        bubble.initialize(sessionId, owner, side, tick, sequence);
        ActionBattleFieldObject object = new ActionBattleFieldObject(sessionId, owner, side,
                level.dimension().location().toString(), position(pos), tick, sequence,
                tick + ActionBattleWaterRules.BUBBLE_LIFETIME_TICKS);
        BUBBLES.register(object).ifPresent(evicted -> removeTracked(level, evicted));
        return true;
    }

    public static void activateBubble(AquaBubbleBlockEntity bubble, PokemonEntity toucher) {
        if (bubble == null || toucher == null || bubble.getLevel() == null || bubble.lifecycle() == null) return;
        var lifecycle = bubble.lifecycle();
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(toucher.getUUID());
        if (session == null || !lifecycle.sessionId().equals(session.dungeonSessionId())) return;
        UUID pokemonUUID = toucher.getPokemon().getUuid();
        ActionBattleFieldObject.OwnerSide toucherSide = session.isPlayerPokemon(pokemonUUID)
                ? ActionBattleFieldObject.OwnerSide.PLAYER : pokemonUUID.equals(session.trainerActivePokemonUUID())
                ? ActionBattleFieldObject.OwnerSide.TRAINER : null;
        if (toucherSide == null) return;
        boolean allied = toucherSide == lifecycle.ownerSide();
        long tick = bubble.getLevel().getGameTime();
        ActionBattleTypeEffectController effects = ActionBattleTypeEffectController.global();
        effects.guardSession(lifecycle.sessionId());
        ActionBattleWaterContactRules.ActivationResult contact = ActionBattleWaterContactRules.resolveContact(allied, false);
        if (contact == ActionBattleWaterContactRules.ActivationResult.IGNORED || !lifecycle.consumeFirst()) return;
        bubble.setChanged();
        boolean appliesEffect = contact == ActionBattleWaterContactRules.ActivationResult.ENEMY_TRAPPED;
        if (appliesEffect && !ActionBattleEffectApplicationGuard.allowsNewApplication(session, toucher, tick)) {
            removeBubble(bubble);
            return;
        }
        switch (contact) {
            case ENEMY_TRAPPED -> {
                effects.applyImmobilized(lifecycle.sessionId(), pokemonUUID, tick);
                toucher.getNavigation().stop();
            }
            case IGNORED -> { }
        }
        if (contact == ActionBattleWaterContactRules.ActivationResult.ENEMY_TRAPPED) removeBubble(bubble);
    }

    public static boolean onPokemonDamaged(PokemonEntity damaged, int actualDamage) {
        if (damaged == null || actualDamage <= 0) return false;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(damaged.getUUID());
        return session != null && ActionBattleTypeEffectController.global().breakWaterTrapOnDamage(
                session.dungeonSessionId(), damaged.getPokemon().getUuid(), actualDamage);
    }

    public static void home(AquaBubbleBlockEntity bubble) {
        if (bubble == null || bubble.lifecycle() == null || !(bubble.getLevel() instanceof ServerLevel level)
                || Math.floorMod(level.getGameTime() + bubble.lifecycle().creationSequence(), 10L) != 0L) return;
        ActionBattleSession session = ActionBattleManager.findSessionForPokemon(bubble.lifecycle().ownerPokemonUUID());
        if (session == null) return;
        PokemonEntity nearest = level.getEntitiesOfClass(PokemonEntity.class,
                new net.minecraft.world.phys.AABB(bubble.getBlockPos()).inflate(32.0D), candidate -> {
                    UUID id = candidate.getPokemon().getUuid();
                    ActionBattleFieldObject.OwnerSide side = session.isPlayerPokemon(id)
                            ? ActionBattleFieldObject.OwnerSide.PLAYER
                            : id.equals(session.trainerActivePokemonUUID())
                            ? ActionBattleFieldObject.OwnerSide.TRAINER : null;
                    return side != null && side != bubble.lifecycle().ownerSide() && candidate.isAlive();
                }).stream().min(java.util.Comparator.comparingDouble(candidate ->
                candidate.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(bubble.getBlockPos())))).orElse(null);
        if (nearest == null) return;
        BlockPos current = bubble.getBlockPos();
        BlockPos preferred = current.offset(Integer.signum(nearest.blockPosition().getX() - current.getX()), 0,
                Integer.signum(nearest.blockPosition().getZ() - current.getZ()));
        java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
        candidates.add(preferred);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x != 0 || z != 0) candidates.add(current.offset(x, 0, z));
        }
        BlockPos destination = candidates.stream().filter(pos -> validFloatingPlacement(level, pos))
                .min(java.util.Comparator.comparingDouble(pos -> nearest.distanceToSqr(
                        net.minecraft.world.phys.Vec3.atCenterOf(pos)))).orElse(null);
        if (destination == null || destination.equals(current)) return;
        var lifecycle = bubble.lifecycle();
        BUBBLES.unregister(lifecycle.sessionId(), position(current));
        level.removeBlock(current, false);
        if (!level.setBlock(destination, ModBlocks.AQUA_BUBBLE.get().defaultBlockState(), 3)
                || !(level.getBlockEntity(destination) instanceof AquaBubbleBlockEntity moved)) {
            level.removeBlock(destination, false);
            if (level.setBlock(current, ModBlocks.AQUA_BUBBLE.get().defaultBlockState(), 3)
                    && level.getBlockEntity(current) instanceof AquaBubbleBlockEntity restored) {
                restored.initialize(lifecycle);
                BUBBLES.register(new ActionBattleFieldObject(lifecycle.sessionId(), lifecycle.ownerPokemonUUID(),
                        lifecycle.ownerSide(), level.dimension().location().toString(), position(current),
                        lifecycle.creationTick(), lifecycle.creationSequence(), lifecycle.expiryTick()));
            }
            return;
        }
        moved.initialize(lifecycle);
        BUBBLES.register(new ActionBattleFieldObject(lifecycle.sessionId(), lifecycle.ownerPokemonUUID(),
                lifecycle.ownerSide(), level.dimension().location().toString(), position(destination),
                lifecycle.creationTick(), lifecycle.creationSequence(), lifecycle.expiryTick()));
    }

    public static void removeBubble(AquaBubbleBlockEntity bubble) {
        if (bubble == null || bubble.getLevel() == null) return;
        if (bubble.lifecycle() != null) BUBBLES.unregister(bubble.lifecycle().sessionId(), position(bubble.getBlockPos()));
        bubble.getLevel().removeBlock(bubble.getBlockPos(), false);
    }

    public static void unregisterBubble(AquaBubbleBlockEntity bubble) {
        if (bubble != null && bubble.lifecycle() != null) {
            BUBBLES.unregister(bubble.lifecycle().sessionId(), position(bubble.getBlockPos()));
        }
    }

    public static void clearSession(ServerLevel level, UUID sessionId) {
        for (ActionBattleFieldObject object : BUBBLES.clearSession(sessionId)) {
            if (level != null && level.dimension().location().toString().equals(object.dimensionId())) {
                level.removeBlock(blockPos(object.position()), false);
            }
        }
    }

    private static boolean validPlacement(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 1
                || !level.getWorldBorder().isWithinBounds(pos) || !level.isLoaded(pos)) return false;
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static boolean validFloatingPlacement(ServerLevel level, BlockPos pos) {
        return validPlacement(level, pos);
    }

    private static void removeTracked(ServerLevel level, ActionBattleFieldObject object) {
        if (level.dimension().location().toString().equals(object.dimensionId())) {
            level.removeBlock(blockPos(object.position()), false);
        }
    }

    private static ActionBattleFieldObject.Position position(BlockPos pos) {
        return new ActionBattleFieldObject.Position(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos blockPos(ActionBattleFieldObject.Position pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    private static BlockPos blockPos(WaterFieldPlacement.Position pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    private static String normalize(String value) { return value != null ? value.toLowerCase(java.util.Locale.ROOT) : ""; }
}

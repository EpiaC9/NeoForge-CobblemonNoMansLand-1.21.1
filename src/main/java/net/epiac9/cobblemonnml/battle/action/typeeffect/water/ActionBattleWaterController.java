package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.compat.FightOrFlightAdapter;
import net.epiac9.cobblemonnml.battle.action.protect.ActionBattleProtectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeEffectController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObjectTracker;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.field.AquaBubbleBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.field.WaterFieldPlacement;
import net.epiac9.cobblemonnml.dimension.DungeonSession;
import net.epiac9.cobblemonnml.mixin.ActionBattleLivingEntityAccessor;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleWaterController {
    private static final ActionBattleFieldObjectTracker BUBBLES =
            new ActionBattleFieldObjectTracker(ActionBattleWaterRules.MAX_BUBBLES_PER_OWNER);
    private static final Map<UUID, Pokemon> POKEMON_REFS = new HashMap<>();
    private static final Map<UUID, UUID> BATTLE_REFS = new HashMap<>();
    private static long nextSequence;

    private ActionBattleWaterController() {}

    public static boolean isQualifyingInteraction(Move move) {
        return move != null && ActionBattleWaterContactRules.isQualifyingInteraction(
                move.getType() != null ? move.getType().getName() : null,
                FightOrFlightAdapter.isNativeDamageMove(move), FightOrFlightAdapter.movePower(move),
                FightOrFlightAdapter.moveTargetCategory(move));
    }

    public static boolean onSuccessfulInteraction(PokemonEntity attacker, PokemonEntity target, Move move) {
        if (attacker == null || target == null || move == null || !isQualifyingInteraction(move)
                || !DungeonSession.isActive() || !(attacker.level() instanceof ServerLevel level)) return false;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(target.getUUID());
        if (session == null || !session.battleId().equals(ActionBattleManager.battleIdForPokemonEntity(attacker.getUUID()))
                || !session.dungeonSessionId().equals(DungeonSession.getSessionId())) return false;
        UUID owner = attacker.getPokemon().getUuid();
        ActionBattleFieldObject.OwnerSide side = owner.equals(session.playerActivePokemonUUID())
                ? ActionBattleFieldObject.OwnerSide.PLAYER : owner.equals(session.trainerActivePokemonUUID())
                ? ActionBattleFieldObject.OwnerSide.TRAINER : null;
        if (side == null) return false;
        PokemonEntity anchor = level.random.nextBoolean() ? attacker : target;
        return placeBubble(level, session.dungeonSessionId(), owner, side, anchor);
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
        ActionBattleFieldObject.OwnerSide toucherSide = pokemonUUID.equals(session.playerActivePokemonUUID())
                ? ActionBattleFieldObject.OwnerSide.PLAYER : pokemonUUID.equals(session.trainerActivePokemonUUID())
                ? ActionBattleFieldObject.OwnerSide.TRAINER : null;
        if (toucherSide == null || !lifecycle.consumeFirst()) return;
        bubble.setChanged();
        boolean allied = toucherSide == lifecycle.ownerSide();
        boolean waterTyped = hasType(toucher.getPokemon(), "water");
        long tick = bubble.getLevel().getGameTime();
        ActionBattleTypeEffectController effects = ActionBattleTypeEffectController.global();
        effects.guardSession(lifecycle.sessionId());
        POKEMON_REFS.put(pokemonUUID, toucher.getPokemon());
        BATTLE_REFS.put(pokemonUUID, session.battleId());
        switch (ActionBattleWaterContactRules.resolveContact(allied, waterTyped)) {
            case ALLY_SHIELD -> {
                boolean protectActive = ActionBattleProtectController.global().activeStance(
                        session.battleId(), pokemonUUID, tick) != null;
                effects.applyAquaShield(lifecycle.sessionId(), pokemonUUID, tick, waterTyped, protectActive);
                resolveShieldEndEvents(lifecycle.sessionId(), pokemonUUID);
            }
            case ENEMY_WATER_HEALED -> heal(toucher.getPokemon());
            case ENEMY_IMMOBILIZED -> {
                effects.applyImmobilized(lifecycle.sessionId(), pokemonUUID, tick);
                session.addPokemonMovementCooldownPenalty(pokemonUUID, tick,
                        ActionBattleWaterRules.MOVEMENT_PENALTY_TICKS);
                toucher.getNavigation().stop();
            }
        }
        removeBubble(bubble);
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

    public static void tickSession(UUID sessionId) {
        if (sessionId == null) return;
        for (UUID pokemonUUID : ActionBattleTypeEffectController.global().trackedPokemonIds(sessionId)) {
            resolveShieldEndEvents(sessionId, pokemonUUID);
        }
    }

    public static void clearSession(ServerLevel level, UUID sessionId) {
        for (ActionBattleFieldObject object : BUBBLES.clearSession(sessionId)) {
            if (level != null && level.dimension().location().toString().equals(object.dimensionId())) {
                level.removeBlock(blockPos(object.position()), false);
            }
        }
        POKEMON_REFS.clear();
        BATTLE_REFS.clear();
    }

    public static void resolveShieldEndEvents(UUID sessionId, UUID pokemonUUID) {
        Pokemon pokemon = POKEMON_REFS.get(pokemonUUID);
        UUID battleId = BATTLE_REFS.get(pokemonUUID);
        for (ActionBattleWaterState.ShieldEndEvent event : ActionBattleTypeEffectController.global()
                .drainWaterShieldEndEvents(sessionId, pokemonUUID)) {
            if (pokemon != null) ActionBattleWaterHealth.applyNonHitShieldEnd(healthAccess(pokemon), event);
            if (event.reduceDeterioratingShield() && battleId != null) {
                ActionBattleProtectController.global().reduceDeterioratingShieldLevel(battleId, pokemonUUID);
            }
        }
    }

    public static void resolveProtectedHitShieldEnd(UUID sessionId, UUID pokemonUUID, PokemonEntity target,
                                                     int beforeHealth, int finalDamage) {
        Pokemon pokemon = target != null ? target.getPokemon() : POKEMON_REFS.get(pokemonUUID);
        UUID battleId = BATTLE_REFS.get(pokemonUUID);
        for (ActionBattleWaterState.ShieldEndEvent event : ActionBattleTypeEffectController.global()
                .drainWaterShieldEndEvents(sessionId, pokemonUUID)) {
            if (event.reason() == ActionBattleWaterState.ShieldEndReason.PROTECTED_HIT && pokemon != null) {
                ActionBattleWaterHealth.applyShieldHit(healthAccess(pokemon), beforeHealth,
                        finalDamage, event.healEligible());
            } else if (pokemon != null) {
                ActionBattleWaterHealth.applyNonHitShieldEnd(healthAccess(pokemon), event);
            }
            if (event.reduceDeterioratingShield() && battleId != null) {
                ActionBattleProtectController.global().reduceDeterioratingShieldLevel(battleId, pokemonUUID);
            }
        }
    }

    private static boolean validPlacement(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 1
                || !level.getWorldBorder().isWithinBounds(pos) || !level.isLoaded(pos)) return false;
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static void heal(Pokemon pokemon) {
        if (pokemon != null) ActionBattleWaterHealth.heal(healthAccess(pokemon));
    }

    private static ActionBattleWaterHealth.Access healthAccess(Pokemon pokemon) {
        PokemonEntity entity = pokemon.getEntity();
        boolean deployed = entity != null && !entity.isRemoved();
        return new ActionBattleWaterHealth.Access() {
            @Override public int currentHealth() { return pokemon.getCurrentHealth(); }
            @Override public int maxHealth() { return pokemon.getMaxHealth(); }
            @Override public boolean deployed() { return deployed; }
            @Override public float liveMaxHealth() { return deployed ? entity.getMaxHealth() : 0.0F; }
            @Override public void setCurrentHealth(int value) { pokemon.setCurrentHealth(value); }
            @Override public void setLiveHealth(float value) {
                if (!deployed) return;
                entity.setHealth(value);
                if (value > 0.0F) {
                    entity.deathTime = 0;
                    ((ActionBattleLivingEntityAccessor) entity).cobblemonNml$setDead(false);
                }
            }
        };
    }

    private static boolean hasType(Pokemon pokemon, String expected) {
        return pokemon != null && (expected.equals(normalize(pokemon.getPrimaryType() != null
                ? pokemon.getPrimaryType().getName() : null)) || expected.equals(normalize(
                pokemon.getSecondaryType() != null ? pokemon.getSecondaryType().getName() : null)));
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

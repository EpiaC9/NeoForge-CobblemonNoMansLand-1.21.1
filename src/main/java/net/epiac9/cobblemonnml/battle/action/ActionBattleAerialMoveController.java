package net.epiac9.cobblemonnml.battle.action;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleAerialMoveRules;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattleAerialMoveState;
import net.epiac9.cobblemonnml.battle.action.typeeffect.flying.ActionBattlePropulsionController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleAerialMoveController {
    private static final Map<Key, Entry> ACTIVE = new HashMap<>();

    private ActionBattleAerialMoveController() {}

    public static boolean destinationSupported(ServerLevel level, PokemonEntity pokemon, Vec3 destination) {
        if (!valid(level, pokemon, destination)) return false;
        Vec3 offset = destination.subtract(pokemon.position());
        AABB destinationBox = pokemon.getBoundingBox().move(offset);
        AABB supportProbe = destinationBox.deflate(0.05D, 0.0D, 0.05D).move(0.0D, -0.125D, 0.0D);
        return level.getBlockCollisions(pokemon, supportProbe).iterator().hasNext();
    }

    public static boolean tryStart(ActionBattleSession session, UUID ownerId,
                                   PokemonEntity pokemon, Vec3 destination) {
        if (session == null || ownerId == null || pokemon == null || pokemon.isRemoved()
                || !(pokemon.level() instanceof ServerLevel level) || !valid(level, pokemon, destination)
                || !session.containsArena(destination.x, destination.z)
                || !level.noCollision(pokemon, pokemon.getBoundingBox().move(
                destination.subtract(pokemon.position())))) return false;
        UUID pokemonId = pokemon.getPokemon().getUuid();
        Key key = new Key(session.battleId(), pokemonId);
        Entry existing = ACTIVE.get(key);
        if (existing != null && existing.entityId.equals(pokemon.getUUID())) {
            existing.state.replace(point(destination));
            pokemon.getNavigation().stop();
            pokemon.setNoGravity(true);
            pokemon.setDeltaMovement(Vec3.ZERO);
            return true;
        }
        clearEntry(ACTIVE.remove(key));
        double tolerance = Math.max(0.25D, pokemon.getBbWidth() * 0.5D);
        ActionBattleAerialMoveState state = new ActionBattleAerialMoveState(tolerance);
        state.replace(point(destination));
        ACTIVE.put(key, new Entry(ownerId, pokemon.getUUID(), pokemon, pokemon.isNoGravity(), state));
        pokemon.getNavigation().stop();
        pokemon.setNoGravity(true);
        pokemon.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    static void tickBattle(ActionBattleSession session, ServerLevel level, long currentTick) {
        if (session == null || level == null) return;
        for (Map.Entry<Key, Entry> active : new ArrayList<>(ACTIVE.entrySet())) {
            Key key = active.getKey();
            Entry entry = active.getValue();
            if (!key.battleId.equals(session.battleId())) continue;
            PokemonEntity pokemon = entry.entity;
            if (!ownsActivePokemon(session, key.pokemonId, entry)
                    || pokemon.isRemoved() || pokemon.level() != level
                    || ActionBattleMovementActionRules.isMovementBlocked(session, key.pokemonId, currentTick)
                    || !ActionBattleSleepController.canIssueCommand(session, key.pokemonId, currentTick,
                    ActionBattleSleepController.CommandKind.PENDING_CONTINUATION)) {
                ACTIVE.remove(key);
                clearEntry(entry);
                session.clearPlayerMoveTarget(entry.ownerId);
                continue;
            }
            if (!ActionBattleAerialMoveRules.shouldApplyHoverMotion(
                    ActionBattlePropulsionController.isActive(session, key.pokemonId))) continue;
            pokemon.getNavigation().stop();
            pokemon.setNoGravity(true);
            pokemon.setDeltaMovement(Vec3.ZERO);
            if (entry.state.hovering()) {
                session.clearPlayerMoveTarget(entry.ownerId);
                continue;
            }
            Vec3 current = pokemon.position();
            double modifier = ActionBattleMovementController.movementSpeed(session, key.pokemonId, currentTick);
            double maximumStep = Math.max(0.01D,
                    pokemon.getAttributeValue(Attributes.MOVEMENT_SPEED) * modifier);
            ActionBattleAerialMoveState.Point next = entry.state.advance(point(current), maximumStep,
                    segment -> safeStep(session, level, pokemon, vec(segment.from()), vec(segment.to())));
            if (next != null) {
                Vec3 delta = vec(next).subtract(current);
                if (delta.lengthSqr() > 0.0D) pokemon.move(MoverType.SELF, delta);
            }
            pokemon.setDeltaMovement(Vec3.ZERO);
            if (entry.state.hovering()) session.clearPlayerMoveTarget(entry.ownerId);
        }
    }

    public static boolean isActive(ActionBattleSession session, UUID pokemonId) {
        return session != null && pokemonId != null && ACTIVE.containsKey(new Key(session.battleId(), pokemonId));
    }

    public static void holdForAbility(ActionBattleSession session, PokemonEntity pokemon) {
        if (session == null || pokemon == null || pokemon.isRemoved()) return;
        Entry entry = ACTIVE.get(new Key(session.battleId(), pokemon.getPokemon().getUuid()));
        if (entry == null || !entry.entityId.equals(pokemon.getUUID())) return;
        entry.state.holdAt(point(pokemon.position()));
        pokemon.getNavigation().stop();
        pokemon.setNoGravity(true);
        pokemon.setDeltaMovement(Vec3.ZERO);
    }

    public static void clearPokemon(ActionBattleSession session, UUID pokemonId) {
        if (session == null || pokemonId == null) return;
        clearEntry(ACTIVE.remove(new Key(session.battleId(), pokemonId)));
    }

    public static void clearBattle(UUID battleId) {
        if (battleId == null) return;
        for (Map.Entry<Key, Entry> active : new ArrayList<>(ACTIVE.entrySet())) {
            if (!battleId.equals(active.getKey().battleId)) continue;
            ACTIVE.remove(active.getKey());
            clearEntry(active.getValue());
        }
    }

    public static void clearAll() {
        for (Entry entry : ACTIVE.values()) clearEntry(entry);
        ACTIVE.clear();
    }

    private static boolean safeStep(ActionBattleSession session, ServerLevel level,
                                    PokemonEntity pokemon, Vec3 from, Vec3 to) {
        if (!valid(level, pokemon, to) || !session.containsArena(to.x, to.z)) return false;
        if (level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, pokemon)).getType() != HitResult.Type.MISS) return false;
        AABB movedBox = pokemon.getBoundingBox().move(to.subtract(pokemon.position()));
        return level.noCollision(pokemon, movedBox);
    }

    private static boolean valid(ServerLevel level, PokemonEntity pokemon, Vec3 point) {
        if (level == null || pokemon == null || point == null
                || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) return false;
        BlockPos pos = BlockPos.containing(point);
        return point.y >= level.getMinBuildHeight()
                && point.y + pokemon.getBbHeight() < level.getMaxBuildHeight()
                && level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getWorldBorder().isWithinBounds(pos);
    }

    private static boolean ownsActivePokemon(ActionBattleSession session, UUID pokemonId, Entry entry) {
        return pokemonId.equals(session.playerActivePokemonUUID(entry.ownerId))
                && entry.entityId.equals(session.playerActiveEntityUUID(entry.ownerId));
    }

    private static void clearEntry(Entry entry) {
        if (entry == null) return;
        PokemonEntity pokemon = entry.entity;
        if (pokemon != null && !pokemon.isRemoved()) {
            pokemon.setNoGravity(entry.originalNoGravity);
            pokemon.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static ActionBattleAerialMoveState.Point point(Vec3 value) {
        return new ActionBattleAerialMoveState.Point(value.x, value.y, value.z);
    }

    private static Vec3 vec(ActionBattleAerialMoveState.Point value) {
        return new Vec3(value.x(), value.y(), value.z());
    }

    private record Key(UUID battleId, UUID pokemonId) {}

    private static final class Entry {
        private final UUID ownerId;
        private final UUID entityId;
        private final PokemonEntity entity;
        private final boolean originalNoGravity;
        private final ActionBattleAerialMoveState state;

        private Entry(UUID ownerId, UUID entityId, PokemonEntity entity,
                      boolean originalNoGravity, ActionBattleAerialMoveState state) {
            this.ownerId = ownerId;
            this.entityId = entityId;
            this.entity = entity;
            this.originalNoGravity = originalNoGravity;
            this.state = state;
        }
    }
}

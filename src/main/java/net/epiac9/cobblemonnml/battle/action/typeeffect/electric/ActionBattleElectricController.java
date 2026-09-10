package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.epiac9.cobblemonnml.battle.action.ActionBattleManager;
import net.epiac9.cobblemonnml.battle.action.ActionBattleSession;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.field.PlasmaBallBlockEntity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.electric.field.PlasmaBallLifecycle;
import net.epiac9.cobblemonnml.battle.action.typeeffect.field.ActionBattleFieldObject;
import net.epiac9.cobblemonnml.battle.action.typeeffect.normal.ActionBattleTypeMechanicIdentity;
import net.epiac9.cobblemonnml.battle.action.typeeffect.water.field.WaterFieldPlacement;
import net.epiac9.cobblemonnml.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActionBattleElectricController {
    private static final Map<UUID, Set<BlockPos>> POSITIONS = new HashMap<>();
    private static long nextSequence;
    private static int previewRoamIntervalTicks = 10;

    private ActionBattleElectricController() {}

    public static boolean onOwnedActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.pokemon() == null || !(context.pokemon().level() instanceof ServerLevel level)
                || context.arenaSessionId() == null || context.battleId() == null) return false;
        PokemonEntity anchor = context.targetingMode() == ActionBattleTypeMechanicActionContext.TargetingMode.TARGET
                && context.target() instanceof PokemonEntity target ? target : context.pokemon();
        var candidates = WaterFieldPlacement.validCandidates(position(anchor.blockPosition()), candidate -> {
            BlockPos groundAir = blockPos(candidate);
            return validHoverPlacement(level, groundAir.above(ActionBattleElectricRules.HOVER_BLOCKS));
        });
        if (candidates.isEmpty()) return false;
        BlockPos pos = blockPos(candidates.get(level.random.nextInt(candidates.size())))
                .above(ActionBattleElectricRules.HOVER_BLOCKS);
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(context.pokemon().getUUID());
        ActionBattleFieldObject.OwnerSide side = session != null && session.isPlayerPokemon(
                context.pokemon().getPokemon().getUuid()) ? ActionBattleFieldObject.OwnerSide.PLAYER
                : ActionBattleFieldObject.OwnerSide.TRAINER;
        return create(level, pos, new PlasmaBallLifecycle(context.arenaSessionId(), context.battleId(),
                context.pokemon().getPokemon().getUuid(), side, nextSequence++));
    }

    public static int chainProjectile(PokemonEntity attacker, Move move, Vec3 impact, float damage,
                                      boolean mechanicSecondary) {
        if (attacker == null || move == null || impact == null || damage <= 0.0F
                || !ActionBattleElectricRules.canChain(
                ActionBattleTypeMechanicIdentity.hasMechanicBenefit(attacker, "electric"), true,
                mechanicSecondary) || !(attacker.level() instanceof ServerLevel level)) return 0;
        ActionBattleSession session = ActionBattleManager.findSessionForBattlePokemonEntity(attacker.getUUID());
        if (session == null) return 0;
        BlockPos origin = BlockPos.containing(impact);
        List<BlockPos> balls = positions(session.dungeonSessionId()).stream()
                .filter(pos -> ActionBattleElectricRules.insideChainArea(grid(origin), grid(pos)))
                .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getX())
                        .thenComparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getZ())).toList();
        int instances = 0;
        for (BlockPos ball : balls) {
            AABB area = new AABB(ball).inflate(ActionBattleElectricRules.DAMAGE_HALF_WIDTH, 2.0D,
                    ActionBattleElectricRules.DAMAGE_HALF_WIDTH);
            for (PokemonEntity target : level.getEntitiesOfClass(PokemonEntity.class, area,
                    candidate -> candidate.isAlive() && candidate != attacker)) {
                UUID targetBattle = ActionBattleManager.battleIdForPokemonEntity(target.getUUID());
                if (!session.battleId().equals(targetBattle)) continue;
                boolean attackerPlayerSide = session.isPlayerPokemon(attacker.getPokemon().getUuid());
                if (attackerPlayerSide == session.isPlayerPokemon(target.getPokemon().getUuid())) continue;
                target.hurt(level.damageSources().indirectMagic(attacker, attacker), damage);
            }
            instances++;
        }
        return instances;
    }

    public static int activeCount(UUID sessionId) { return positions(sessionId).size(); }
    public static boolean trainerPrefersPlasma(UUID sessionId, boolean validInteraction,
                                               boolean playerIssuedTarget) {
        return ActionBattleElectricRules.prefersPlasmaTarget(activeCount(sessionId), validInteraction,
                playerIssuedTarget);
    }

    public static boolean hasChainNear(UUID sessionId, BlockPos impact) {
        if (impact == null) return false;
        return positions(sessionId).stream().anyMatch(pos ->
                ActionBattleElectricRules.insideChainArea(grid(impact), grid(pos)));
    }

    public static BlockPos nearestPlasmaBallTo(UUID sessionId, BlockPos target) {
        if (target == null) return null;
        return positions(sessionId).stream()
                .min(Comparator.comparingDouble(pos -> pos.distSqr(target)))
                .orElse(null);
    }

    public static void roam(PlasmaBallBlockEntity ball) {
        if (ball == null || ball.getLevel() == null || ball.lifecycle() == null
                || !(ball.getLevel() instanceof ServerLevel level)
                || Math.floorMod(level.getGameTime() + ball.lifecycle().sequence(), previewRoamIntervalTicks) != 0L) return;
        BlockPos current = ball.getBlockPos();
        List<ActionBattleElectricRules.GridPosition> candidates = ActionBattleElectricRules.roamingCandidates(
                grid(current), candidate -> validHoverPlacement(level, blockPos(candidate)));
        if (candidates.isEmpty()) return;
        BlockPos destination = blockPos(candidates.get(level.random.nextInt(candidates.size())));
        PlasmaBallLifecycle lifecycle = ball.lifecycle();
        unregister(lifecycle.sessionId(), current);
        level.removeBlock(current, false);
        if (!create(level, destination, lifecycle)) create(level, current, lifecycle);
    }

    public static void remove(PlasmaBallBlockEntity ball) {
        if (ball == null || ball.getLevel() == null) return;
        if (ball.lifecycle() != null) unregister(ball.lifecycle().sessionId(), ball.getBlockPos());
        ball.getLevel().removeBlock(ball.getBlockPos(), false);
    }
    public static void unregister(PlasmaBallBlockEntity ball) {
        if (ball != null && ball.lifecycle() != null) unregister(ball.lifecycle().sessionId(), ball.getBlockPos());
    }

    public static void clearSession(ServerLevel level, UUID sessionId) {
        for (BlockPos pos : positions(sessionId)) if (level != null) level.removeBlock(pos, false);
        POSITIONS.remove(sessionId);
    }
    public static void clearAll() { POSITIONS.clear(); }
    public static void configurePreviewRoamInterval(int ticks) { previewRoamIntervalTicks = Math.max(1, ticks); }

    private static boolean create(ServerLevel level, BlockPos pos, PlasmaBallLifecycle lifecycle) {
        if (!validHoverPlacement(level, pos)
                || !level.setBlock(pos, ModBlocks.PLASMA_BALL.get().defaultBlockState(), 3)) return false;
        if (!(level.getBlockEntity(pos) instanceof PlasmaBallBlockEntity ball)) {
            level.removeBlock(pos, false);
            return false;
        }
        ball.initialize(lifecycle);
        POSITIONS.computeIfAbsent(lifecycle.sessionId(), ignored -> new LinkedHashSet<>()).add(pos.immutable());
        return true;
    }

    private static boolean validHoverPlacement(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(pos) || !level.isLoaded(pos)) return false;
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isAir()
                && level.getBlockState(pos.below(2)).isFaceSturdy(level, pos.below(2), Direction.UP);
    }

    private static void unregister(UUID sessionId, BlockPos pos) {
        Set<BlockPos> positions = POSITIONS.get(sessionId);
        if (positions == null) return;
        positions.remove(pos);
        if (positions.isEmpty()) POSITIONS.remove(sessionId);
    }
    private static List<BlockPos> positions(UUID sessionId) {
        Set<BlockPos> positions = sessionId != null ? POSITIONS.get(sessionId) : null;
        return positions == null ? List.of() : List.copyOf(positions);
    }
    private static WaterFieldPlacement.Position position(BlockPos pos) {
        return new WaterFieldPlacement.Position(pos.getX(), pos.getY(), pos.getZ());
    }
    private static BlockPos blockPos(WaterFieldPlacement.Position pos) { return new BlockPos(pos.x(), pos.y(), pos.z()); }
    private static BlockPos blockPos(ActionBattleElectricRules.GridPosition pos) { return new BlockPos(pos.x(), pos.y(), pos.z()); }
    private static ActionBattleElectricRules.GridPosition grid(BlockPos pos) {
        return new ActionBattleElectricRules.GridPosition(pos.getX(), pos.getY(), pos.getZ());
    }
}

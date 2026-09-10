package net.epiac9.cobblemonnml.battle.action.typeeffect.ice;

import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattleTypeMechanicActionContext;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ActionBattleChillingAuraRuntime {
    private static final Map<UUID, List<Aura>> AURAS = new HashMap<>();
    private static Tuning tuning = new Tuning(2.0D, 180L, 0.10D);
    private ActionBattleChillingAuraRuntime() {}

    public static void onOwnedActionStarted(ActionBattleTypeMechanicActionContext context) {
        if (context == null || context.battleId() == null || context.pokemon() == null) return;
        Vec3 center = context.targetingMode() == ActionBattleTypeMechanicActionContext.TargetingMode.SELF_OR_ALLY
                || context.target() == null ? context.pokemon().position() : context.target().position();
        AURAS.computeIfAbsent(context.battleId(), ignored -> new ArrayList<>())
                .add(new Aura(center, context.pokemon().level().getGameTime()));
    }

    public static double multiplier(UUID battleId, Vec3 position, long tick) {
        if (battleId == null || position == null) return 1.0D;
        double slow = 0.0D;
        for (Aura aura : AURAS.getOrDefault(battleId, List.of())) {
            if (tick >= aura.createdTick() + tuning.lifetimeTicks()
                    || aura.center().distanceToSqr(position) > tuning.radius() * tuning.radius()) continue;
            long seconds = Math.max(1L, (tick - aura.createdTick()) / 20L + 1L);
            slow = Math.max(slow, Math.min(0.99D, seconds * tuning.slowPerSecond()));
        }
        return 1.0D - slow;
    }

    public static void tick(long tick) {
        AURAS.values().forEach(auras -> auras.removeIf(aura -> tick >= aura.createdTick() + tuning.lifetimeTicks()));
        AURAS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
    public static void configure(Tuning value) { if (value != null) tuning = value; }
    public static void clearBattle(UUID battleId) { AURAS.remove(battleId); }
    public static void clearAll() { AURAS.clear(); }

    public record Tuning(double radius, long lifetimeTicks, double slowPerSecond) {
        public Tuning {
            radius = Math.max(0.0D, radius);
            lifetimeTicks = Math.max(1L, lifetimeTicks);
            slowPerSecond = Math.clamp(slowPerSecond, 0.0D, 0.99D);
        }
    }
    private record Aura(Vec3 center, long createdTick) {}
}

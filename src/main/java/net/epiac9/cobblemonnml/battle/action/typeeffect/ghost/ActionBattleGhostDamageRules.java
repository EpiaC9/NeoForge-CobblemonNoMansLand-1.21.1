package net.epiac9.cobblemonnml.battle.action.typeeffect.ghost;

import net.epiac9.cobblemonnml.battle.action.ActionBattleTiming;

import java.util.UUID;

public final class ActionBattleGhostDamageRules {
    private final ActionBattleGhostController curses;

    public ActionBattleGhostDamageRules(ActionBattleGhostController curses) {
        if (curses == null) throw new IllegalArgumentException("Ghost controller cannot be null.");
        this.curses = curses;
    }

    public double modifyIncomingDirectDamage(UUID battleId, UUID targetPokemonUUID, long currentTick,
                                             double damage, boolean qualifyingDirectHit) {
        if (!(damage > 0.0D) || !qualifyingDirectHit) return damage;
        return damage;
    }

    public double prepareDamagingAbility(UUID battleId, UUID casterPokemonUUID, long currentTick,
                                         boolean damagingAbility) {
        if (!damagingAbility) return 1.0D;
        return 1.0D;
    }

    public CooldownPlan abilityCooldownPlan(UUID battleId, UUID casterPokemonUUID,
                                            int moveSlot, long currentTick) {
        return abilityCooldownPlan(battleId, casterPokemonUUID, moveSlot, currentTick,
                ActionBattleTiming.ABILITY_SHARED_COOLDOWN_TICKS);
    }

    public CooldownPlan abilityCooldownPlan(UUID battleId, UUID casterPokemonUUID,
                                            int moveSlot, long currentTick, long baseSharedTicks) {
        boolean burden = curses.consume(
                battleId, casterPokemonUUID, ActionBattleGhostCurseType.BURDEN, currentTick);
        boolean torment = curses.consume(
                battleId, casterPokemonUUID, ActionBattleGhostCurseType.TORMENT, currentTick);
        long sharedTicks = Math.max(0L, baseSharedTicks);
        return new CooldownPlan(torment ? 120L : sharedTicks,
                burden ? 60L : ActionBattleTiming.PERSONAL_MOVE_BASE_COOLDOWN_TICKS, moveSlot);
    }

    public SilencePlan silencePlan(UUID battleId, UUID casterPokemonUUID,
                                   long currentTick, boolean channeling) {
        if (!channeling || !curses.consume(
                battleId, casterPokemonUUID, ActionBattleGhostCurseType.SILENCE, currentTick)) {
            return SilencePlan.NONE;
        }
        return new SilencePlan(true, 1, ActionBattleTiming.ABILITY_SHARED_COOLDOWN_TICKS);
    }

    public record CooldownPlan(long sharedTicks, long personalTicks, int moveSlot) {}

    public record SilencePlan(boolean interrupted, int ppConsumed, long sharedCooldownTicks) {
        public static final SilencePlan NONE = new SilencePlan(false, 0, 0L);
    }
}

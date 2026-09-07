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
        return curses.consume(battleId, targetPokemonUUID, ActionBattleGhostCurseType.FRAILTY, currentTick)
                ? damage * 1.20D : damage;
    }

    public double prepareDamagingAbility(UUID battleId, UUID casterPokemonUUID, long currentTick,
                                         boolean damagingAbility) {
        if (!damagingAbility) return 1.0D;
        return curses.consume(battleId, casterPokemonUUID, ActionBattleGhostCurseType.WEAKNESS, currentTick)
                ? 0.80D : 1.0D;
    }

    public CooldownPlan abilityCooldownPlan(UUID battleId, UUID casterPokemonUUID,
                                            int moveSlot, long currentTick) {
        boolean burden = curses.consume(
                battleId, casterPokemonUUID, ActionBattleGhostCurseType.BURDEN, currentTick);
        boolean torment = curses.consume(
                battleId, casterPokemonUUID, ActionBattleGhostCurseType.TORMENT, currentTick);
        return new CooldownPlan(torment ? 120L : ActionBattleTiming.ABILITY_SHARED_COOLDOWN_TICKS,
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

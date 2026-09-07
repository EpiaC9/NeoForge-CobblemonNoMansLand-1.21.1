package net.epiac9.cobblemonnml.battle.action.health;

import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattlePokemonHealth;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostCurseType;

import java.util.UUID;

public final class ActionBattleDotExecutor {
    private final ActionBattleGhostController curses;
    private final ActionBattleHealthResolver health;

    public ActionBattleDotExecutor(ActionBattleGhostController curses, ActionBattleHealthResolver health) {
        if (curses == null || health == null) throw new IllegalArgumentException("DoT services cannot be null.");
        this.curses = curses;
        this.health = health;
    }

    public Result execute(ActionBattlePokemonHealth.Access access, UUID battleId, UUID pokemonUUID,
                          int requestedDamage, ActionBattleDamageSource source,
                          long currentTick, double misfortuneRoll) {
        if (source == null || requestedDamage <= 0) return new Result(0, 0, false);
        int base = health.damage(access, battleId, pokemonUUID, requestedDamage, source, currentTick).actualChange();
        boolean extra = source.dot() && !source.extraDotTick() && !source.nonReactive()
                && misfortuneRoll >= 0.0D && misfortuneRoll < 0.25D
                && curses.view(battleId, pokemonUUID,
                ActionBattleGhostCurseType.MISFORTUNE, currentTick).isPresent();
        int extraDamage = extra && access.currentHealth() > 0
                ? health.damage(access, battleId, pokemonUUID, requestedDamage,
                ActionBattleDamageSource.extraDot(source.id()), currentTick).actualChange() : 0;
        return new Result(base, extraDamage, extra);
    }

    public record Result(int baseActualDamage, int extraActualDamage, boolean extraTick) {
        public int totalActualDamage() { return baseActualDamage + extraActualDamage; }
    }
}

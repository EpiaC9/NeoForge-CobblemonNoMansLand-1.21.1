package net.epiac9.cobblemonnml.battle.action.health;

import net.epiac9.cobblemonnml.battle.action.typeeffect.ActionBattlePokemonHealth;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostController;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostCurseType;
import net.epiac9.cobblemonnml.battle.action.typeeffect.ghost.ActionBattleGhostRules;

import java.util.UUID;

public final class ActionBattleHealthResolver {
    private final ActionBattleGhostController curses;

    public ActionBattleHealthResolver(ActionBattleGhostController curses) {
        if (curses == null) throw new IllegalArgumentException("Ghost controller cannot be null.");
        this.curses = curses;
    }

    public Result heal(ActionBattlePokemonHealth.Access access, UUID battleId, UUID pokemonUUID,
                       int requested, long currentTick) {
        int adjusted = requested;
        if (requested > 0 && curses.view(
                battleId, pokemonUUID, ActionBattleGhostCurseType.WITHERING, currentTick).isPresent()) {
            adjusted = (int) Math.ceil(requested * 0.50D);
        }
        int actual = ActionBattlePokemonHealth.heal(access, adjusted);
        return new Result(requested, actual, access.currentHealth());
    }

    public Result damage(ActionBattlePokemonHealth.Access access, UUID battleId, UUID pokemonUUID,
                         int requested, ActionBattleDamageSource source, long currentTick) {
        int actual = ActionBattlePokemonHealth.damage(access, requested);
        return new Result(requested, actual, access.currentHealth());
    }

    public Result onPpConsumed(ActionBattlePokemonHealth.Access access, UUID battleId,
                               UUID pokemonUUID, int ppConsumed, long currentTick) {
        if (ppConsumed <= 0 || curses.view(
                battleId, pokemonUUID, ActionBattleGhostCurseType.HUNGER, currentTick).isEmpty()) {
            return new Result(0, 0, access.currentHealth());
        }
        int requested = ActionBattleGhostRules.hungerDamage(access.maxHealth(), ppConsumed);
        return damage(access, battleId, pokemonUUID, requested,
                ActionBattleDamageSource.curse("hunger"), currentTick);
    }

    public record Result(int requestedChange, int actualChange, int finalHealth) {}
}

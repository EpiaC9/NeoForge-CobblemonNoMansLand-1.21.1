package net.epiac9.cobblemonnml.battle.action.damage;
import java.util.UUID;
public record ActionBattleDamageFeedbackEvent(long eventId, UUID pokemonUUID, int damage, ActionBattleDamageFeedbackCategory category) {
    public ActionBattleDamageFeedbackEvent {
        if (pokemonUUID == null) throw new IllegalArgumentException("pokemonUUID cannot be null");
        damage = Math.max(0, damage);
        category = category != null ? category : ActionBattleDamageFeedbackCategory.NORMAL;
    }
}

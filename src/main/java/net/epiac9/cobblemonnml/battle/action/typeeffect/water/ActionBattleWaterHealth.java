package net.epiac9.cobblemonnml.battle.action.typeeffect.water;

public final class ActionBattleWaterHealth {
    public interface Access {
        int currentHealth();
        int maxHealth();
        boolean deployed();
        float liveMaxHealth();
        void setCurrentHealth(int value);
        void setLiveHealth(float value);
    }

    public record Result(int finalPokemonHealth, float finalLiveHealth, boolean fainted) {}

    private ActionBattleWaterHealth() {}

    public static Result heal(Access access) {
        requireAccess(access);
        int finalHealth = Math.min(access.maxHealth(), access.currentHealth()
                + ActionBattleWaterRules.healAmount(access.maxHealth()));
        return synchronize(access, finalHealth);
    }

    public static Result applyShieldHit(Access access, int beforeHealth, int finalDamage, boolean healEligible) {
        requireAccess(access);
        int heal = healEligible ? ActionBattleWaterRules.healAmount(access.maxHealth()) : 0;
        int finalHealth = Math.clamp(beforeHealth - Math.max(0, finalDamage) + heal, 0, access.maxHealth());
        return synchronize(access, finalHealth);
    }

    public static Result applyNonHitShieldEnd(Access access, ActionBattleWaterState.ShieldEndEvent event) {
        requireAccess(access);
        if (event == null || !event.healEligible()) {
            return new Result(access.currentHealth(), 0.0F, access.currentHealth() <= 0);
        }
        if (event.reason() == ActionBattleWaterState.ShieldEndReason.PROTECTED_HIT) {
            throw new IllegalArgumentException("Protected-hit healing requires final damage context.");
        }
        return heal(access);
    }

    public static int toPokemonDamage(int pokemonMaxHealth, float liveMaxHealth, float liveDamage) {
        if (pokemonMaxHealth <= 0 || !(liveMaxHealth > 0.0F) || !(liveDamage > 0.0F)) return 0;
        return Math.max(1, (int) Math.ceil(pokemonMaxHealth * liveDamage / liveMaxHealth));
    }

    private static Result synchronize(Access access, int finalHealth) {
        float liveHealth = 0.0F;
        if (access.deployed()) {
            liveHealth = finalHealth <= 0 ? 0.0F
                    : access.liveMaxHealth() * finalHealth / (float) access.maxHealth();
            access.setLiveHealth(liveHealth);
        }
        access.setCurrentHealth(finalHealth);
        return new Result(finalHealth, liveHealth, finalHealth <= 0);
    }

    private static void requireAccess(Access access) {
        if (access == null || access.maxHealth() <= 0) {
            throw new IllegalArgumentException("Water healing requires a positive maximum health.");
        }
    }
}

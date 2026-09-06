package net.epiac9.cobblemonnml.battle.action.typeeffect;

public final class ActionBattlePokemonHealth {
    public interface Access {
        int currentHealth();
        int maxHealth();
        boolean deployed();
        float liveMaxHealth();
        void setCurrentHealth(int value);
        void setLiveHealth(float value);
    }

    private ActionBattlePokemonHealth() {}

    public static int ceilPercent(int basis, double fraction) {
        if (basis <= 0 || !(fraction > 0.0D)) return 0;
        return Math.max(1, (int) Math.ceil(basis * fraction));
    }

    public static int heal(Access access, int requested) {
        requireAccess(access);
        if (requested <= 0) return 0;
        int before = Math.clamp(access.currentHealth(), 0, access.maxHealth());
        int after = Math.min(access.maxHealth(), before + requested);
        synchronize(access, after);
        return after - before;
    }

    public static int damage(Access access, int requested) {
        requireAccess(access);
        if (requested <= 0) return 0;
        int before = Math.clamp(access.currentHealth(), 0, access.maxHealth());
        int after = Math.max(0, before - requested);
        synchronize(access, after);
        return before - after;
    }

    private static void synchronize(Access access, int health) {
        if (access.deployed()) {
            float live = health <= 0 ? 0.0F : access.liveMaxHealth() * health / (float) access.maxHealth();
            access.setLiveHealth(live);
        }
        access.setCurrentHealth(health);
    }

    private static void requireAccess(Access access) {
        if (access == null || access.maxHealth() <= 0) {
            throw new IllegalArgumentException("Pokemon health synchronization requires positive maximum health.");
        }
    }
}

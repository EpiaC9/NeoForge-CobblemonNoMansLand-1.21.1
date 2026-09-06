package net.epiac9.cobblemonnml.battle.action.move;

import net.epiac9.cobblemonnml.battle.action.projectile.ActionProjectileProfile;
import net.epiac9.cobblemonnml.battle.action.projectile.wave.ActionBattleWaveParameters;

import java.util.Locale;

public final class ActionBattleEarthquakeRules {
    private static final double MAX_RADIUS = 10.0D;

    private ActionBattleEarthquakeRules() {}

    public static boolean isEarthquakeName(String moveName) {
        return "earthquake".equals(normalize(moveName));
    }

    public static ActionBattleWaveParameters waveParameters() {
        return new ActionBattleWaveParameters(
                ActionProjectileProfile.GROUND_HUGGING_WAVE_SPEED, MAX_RADIUS);
    }

    private static String normalize(String moveName) {
        return moveName == null ? "" : moveName.toLowerCase(Locale.ROOT)
                .replace("-", "").replace("_", "").replace(" ", "");
    }
}

package net.epiac9.cobblemonnml.battle.action.projectile.wave;

public record ActionBattleWaveParameters(double speed, double maxRadius) {
    public ActionBattleWaveParameters {
        if (!(speed > 0.0D) || !(maxRadius > 0.0D)) {
            throw new IllegalArgumentException("Wave speed and radius must be positive.");
        }
    }
}

package net.epiac9.cobblemonnml.battle.action.typeeffect.electric;

public final class ActionBattleElectricVisuals {
    public enum ContributionVisual { NONE, SUBTLE_STATIC, PARALYSIS_FLINCH }
    private static final String CHARGE_STATUS_ID = "TYPE_ELECTRIC_CHARGE";
    private static final String PARALYSIS_STATUS_ID = "TYPE_ELECTRIC_PARALYSIS";

    private ActionBattleElectricVisuals() {}

    public static ContributionVisual visualFor(ActionBattleParalysisState.FlinchContributionResult result) {
        if (result == ActionBattleParalysisState.FlinchContributionResult.ACCUMULATED) {
            return ContributionVisual.SUBTLE_STATIC;
        }
        if (result == ActionBattleParalysisState.FlinchContributionResult.FLINCH_TRIGGERED) {
            return ContributionVisual.PARALYSIS_FLINCH;
        }
        return ContributionVisual.NONE;
    }

    public static String chargeStatusId() { return CHARGE_STATUS_ID; }
    public static long chargeRemaining(int charge) { return Math.clamp(charge, 0, 100); }
    public static long chargeDuration() { return ActionBattleElectricRules.MAX_CHARGE; }
    public static String paralysisStatusId() { return PARALYSIS_STATUS_ID; }
    public static long paralysisRemaining(long remainingTicks) { return Math.max(0L, remainingTicks); }
    public static long paralysisDuration() { return ActionBattleElectricRules.PARALYSIS_DURATION_TICKS; }

    public static void emitSubtleStatic(Object entity) {
        ActionBattleElectricParticleVisuals.emitSubtleStatic(entity);
    }

    public static void emitParalysisFlinch(Object entity) {
        ActionBattleElectricParticleVisuals.emitParalysisFlinch(entity);
    }
}

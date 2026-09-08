package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleTeamSidebarRules {
    private ActionBattleTeamSidebarRules() {}

    public static int hpWidth(int availableWidth, int currentHp, int maxHp) {
        if (availableWidth <= 0 || currentHp <= 0 || maxHp <= 0) return 0;
        return Math.clamp((int) Math.round(availableWidth * Math.clamp((double) currentHp / maxHp, 0.0D, 1.0D)),
                0, availableWidth);
    }
}

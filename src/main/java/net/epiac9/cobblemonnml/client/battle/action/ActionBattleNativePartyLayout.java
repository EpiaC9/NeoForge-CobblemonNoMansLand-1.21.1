package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleNativePartyLayout {
    public static final int SLOT_WIDTH = 62;
    public static final int SLOT_HEIGHT = 30;
    public static final int SLOT_SPACING = 4;
    public static final int EFFECT_SIZE = ActionBattleEffectIconRules.standardSize();
    private static final int GROUP_OFFSET_Y = 10;
    private static final int SLOT_INSET_Y = 2;

    private ActionBattleNativePartyLayout() {}

    public static ActionBattleHudLayout.Rect slotBounds(int guiHeight, int partySize, int partySlot) {
        int safeSize = Math.max(0, Math.min(6, partySize));
        int safeSlot = Math.max(0, Math.min(5, partySlot));
        int startY = guiHeight / 2 - (safeSize * SLOT_HEIGHT) / 2 - GROUP_OFFSET_Y;
        int slotY = startY + safeSlot * (SLOT_HEIGHT + SLOT_SPACING);
        return new ActionBattleHudLayout.Rect(0, slotY, SLOT_WIDTH, SLOT_HEIGHT);
    }

    public static ActionBattleHudLayout.Rect effectAnchor(int guiHeight, int partySize, int partySlot) {
        ActionBattleHudLayout.Rect slot = slotBounds(guiHeight, partySize, partySlot);
        return new ActionBattleHudLayout.Rect(SLOT_WIDTH,
                slot.y() + SLOT_INSET_Y, EFFECT_SIZE, EFFECT_SIZE);
    }
}

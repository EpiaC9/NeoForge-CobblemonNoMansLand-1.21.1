package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleHudLayout {
    private static final int EDGE_MARGIN_X = 12;
    private static final int EDGE_MARGIN_Y = 10;
    private static final int POKEMON_PANEL_WIDTH = 140;
    private static final int POKEMON_PANEL_HEIGHT = 40;
    private static final int COMMAND_ROOT_X = 20;
    private static final int COMMAND_WIDTH = 92;
    private static final int COMMAND_HEIGHT = 24;
    private static final int COMMAND_TO_MOVE_GAP = 13;
    private static final int MOVE_ROOT_X = COMMAND_ROOT_X + COMMAND_WIDTH + COMMAND_TO_MOVE_GAP;
    private static final int MOVE_WIDTH = 92;
    private static final int MOVE_HEIGHT = 24;
    private static final int MOVE_HORIZONTAL_GAP = 13;
    private static final int MOVE_VERTICAL_GAP = 5;
    private static final int MOVE_BOTTOM_MARGIN = 104;

    private final Rect enemyPanel;
    private final Rect allyPanel;
    private final Rect[] commandButtons;
    private final Rect[] moveButtons;

    private ActionBattleHudLayout(Rect enemyPanel, Rect allyPanel, Rect[] commandButtons, Rect[] moveButtons) {
        this.enemyPanel = enemyPanel;
        this.allyPanel = allyPanel;
        this.commandButtons = commandButtons;
        this.moveButtons = moveButtons;
    }

    public static ActionBattleHudLayout forScreen(int screenWidth, int screenHeight) {
        Rect enemy = new Rect(EDGE_MARGIN_X, EDGE_MARGIN_Y, POKEMON_PANEL_WIDTH, POKEMON_PANEL_HEIGHT);
        Rect ally = new Rect(Math.max(EDGE_MARGIN_X, screenWidth - EDGE_MARGIN_X - POKEMON_PANEL_WIDTH), EDGE_MARGIN_Y, POKEMON_PANEL_WIDTH, POKEMON_PANEL_HEIGHT);
        int controlTop = Math.max(EDGE_MARGIN_Y + POKEMON_PANEL_HEIGHT + MOVE_VERTICAL_GAP,
                screenHeight - MOVE_BOTTOM_MARGIN - (MOVE_HEIGHT * 2 + MOVE_VERTICAL_GAP));
        Rect[] commands = {
                new Rect(COMMAND_ROOT_X, controlTop, COMMAND_WIDTH, COMMAND_HEIGHT),
                new Rect(COMMAND_ROOT_X, controlTop + MOVE_HEIGHT + MOVE_VERTICAL_GAP, COMMAND_WIDTH, COMMAND_HEIGHT)
        };
        Rect[] moves = new Rect[4];
        for (int slot = 0; slot < moves.length; slot++) {
            int col = slot % 2;
            int row = slot / 2;
            moves[slot] = new Rect(MOVE_ROOT_X + col * (MOVE_WIDTH + MOVE_HORIZONTAL_GAP), controlTop + row * (MOVE_HEIGHT + MOVE_VERTICAL_GAP), MOVE_WIDTH, MOVE_HEIGHT);
        }
        return new ActionBattleHudLayout(enemy, ally, commands, moves);
    }

    public Rect enemyPanel() { return enemyPanel; }
    public Rect allyPanel() { return allyPanel; }
    public Rect commandButton(int slot) {
        if (slot < 0 || slot >= commandButtons.length) throw new IllegalArgumentException("Command slot must be 0-1");
        return commandButtons[slot];
    }
    public Rect moveButton(int slot) {
        if (slot < 0 || slot >= moveButtons.length) throw new IllegalArgumentException("Move slot must be 0-3");
        return moveButtons[slot];
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}

package net.epiac9.cobblemonnml.client.battle.action;

public final class ActionBattleHudLayout {
    private static final int EDGE_MARGIN_X = 12;
    private static final int EDGE_MARGIN_Y = 10;
    private static final int POKEMON_PANEL_WIDTH = 140;
    private static final int POKEMON_PANEL_HEIGHT = 40;
    private static final int COMMAND_SIZE = 22;
    private static final int COMMAND_TO_MOVE_GAP = 7;
    private static final int MOVE_ROOT_X = 49;
    private static final int COMMAND_ROOT_X = MOVE_ROOT_X - COMMAND_SIZE - COMMAND_TO_MOVE_GAP;
    private static final int MOVE_WIDTH = 92;
    private static final int MOVE_HEIGHT = 24;
    private static final int MOVE_HORIZONTAL_GAP = 7;
    private static final int MOVE_VERTICAL_GAP = 3;
    private static final int MOVE_BOTTOM_MARGIN = 82;
    private static final int COMMAND_ROW_INSET = (MOVE_HEIGHT - COMMAND_SIZE) / 2;

    private final Rect playerPanel;
    private final Rect trainerPanel;
    private final Rect[] commandButtons;
    private final Rect[] moveButtons;

    private ActionBattleHudLayout(Rect playerPanel, Rect trainerPanel, Rect[] commandButtons, Rect[] moveButtons) {
        this.playerPanel = playerPanel;
        this.trainerPanel = trainerPanel;
        this.commandButtons = commandButtons;
        this.moveButtons = moveButtons;
    }

    public static ActionBattleHudLayout forScreen(int screenWidth, int screenHeight) {
        Rect player = new Rect(EDGE_MARGIN_X, EDGE_MARGIN_Y, POKEMON_PANEL_WIDTH, POKEMON_PANEL_HEIGHT);
        Rect trainer = new Rect(Math.max(EDGE_MARGIN_X, screenWidth - EDGE_MARGIN_X - POKEMON_PANEL_WIDTH), EDGE_MARGIN_Y, POKEMON_PANEL_WIDTH, POKEMON_PANEL_HEIGHT);
        int controlTop = Math.max(EDGE_MARGIN_Y + POKEMON_PANEL_HEIGHT + MOVE_VERTICAL_GAP,
                screenHeight - MOVE_BOTTOM_MARGIN - (MOVE_HEIGHT * 2 + MOVE_VERTICAL_GAP));
        Rect[] commands = {
                new Rect(COMMAND_ROOT_X, controlTop + COMMAND_ROW_INSET, COMMAND_SIZE, COMMAND_SIZE),
                new Rect(COMMAND_ROOT_X, controlTop + MOVE_HEIGHT + MOVE_VERTICAL_GAP + COMMAND_ROW_INSET, COMMAND_SIZE, COMMAND_SIZE)
        };
        Rect[] moves = new Rect[4];
        for (int slot = 0; slot < moves.length; slot++) {
            int col = slot % 2;
            int row = slot / 2;
            moves[slot] = new Rect(MOVE_ROOT_X + col * (MOVE_WIDTH + MOVE_HORIZONTAL_GAP), controlTop + row * (MOVE_HEIGHT + MOVE_VERTICAL_GAP), MOVE_WIDTH, MOVE_HEIGHT);
        }
        return new ActionBattleHudLayout(player, trainer, commands, moves);
    }

    public Rect playerPanel() { return playerPanel; }
    public Rect trainerPanel() { return trainerPanel; }
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

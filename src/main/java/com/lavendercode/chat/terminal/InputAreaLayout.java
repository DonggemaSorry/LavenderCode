package com.lavendercode.chat.terminal;

/**
 * Thread-safe snapshot of the bottom input panel screen rows (0-based).
 * Input height grows with draft content up to {@link #maxEditRows(int)}.
 */
public final class InputAreaLayout {

    public static final int MIN_EDIT_ROWS = 1;
    public static final int MAX_EDIT_ROWS_CAP = 12;
    private static final int MIN_VIEWPORT_ROWS = 5;
    private static final int BORDER_ROWS = 2;

    private volatile int editRows = MIN_EDIT_ROWS;
    private volatile int separatorTopRow;
    private volatile int inputFirstRow;
    private volatile int inputLastRow;
    private volatile int separatorBotRow;

    public int editRows() { return editRows; }

    public int separatorTopRow() { return separatorTopRow; }

    public int inputFirstRow() { return inputFirstRow; }

    public int inputLastRow() { return inputLastRow; }

    public int separatorBotRow() { return separatorBotRow; }

    public int promptRow() { return inputLastRow; }

    public int totalHeight() { return BORDER_ROWS + editRows; }

    public static int maxEditRows(int terminalRows) {
        int available = terminalRows - 1 - BORDER_ROWS - MIN_VIEWPORT_ROWS;
        return Math.min(MAX_EDIT_ROWS_CAP, Math.max(MIN_EDIT_ROWS, available));
    }

    void update(int terminalRows, int requestedEditRows) {
        editRows = Math.max(MIN_EDIT_ROWS, Math.min(requestedEditRows, maxEditRows(terminalRows)));
        int bot = terminalRows - 1;
        separatorBotRow = bot;
        inputLastRow = bot - 1;
        inputFirstRow = inputLastRow - (editRows - 1);
        separatorTopRow = inputFirstRow - 1;
    }

    void update(int terminalRows) {
        update(terminalRows, editRows);
    }
}

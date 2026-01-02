package org.example.game;

public final class TurnSystem {
    // This “first playable” is strict alternating:
    // Player acts -> Enemies act -> back to Player.
    private boolean playerTurn = true;

    public boolean isPlayerTurn() {
        return playerTurn;
    }

    public void endPlayerTurn() {
        playerTurn = false;
    }

    public void endEnemyTurn() {
        playerTurn = true;
    }

    public void reset() {
        playerTurn = true;
    }
}
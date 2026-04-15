package com.example.cardgame.model;

import java.util.List;

/**
 * Core game state data model, shared across players in a room.
 */
public class GameState {
    private List<Player> players;
    private String currentPlayerId;
    private Play lastPlay;           // Previous play record
    private boolean isOpeningTurn;   // Indicates if it's the opening turn
    private boolean isGameOver;      // Indicates if the game has ended
    private String winnerId;

    public GameState() {
        this.isOpeningTurn = true;
        this.isGameOver = false;
    }

    // Getters and Setters
    public List<Player> getPlayers() { return players; }
    public void setPlayers(List<Player> players) { this.players = players; }

    public String getCurrentPlayerId() { return currentPlayerId; }
    public void setCurrentPlayerId(String currentPlayerId) { this.currentPlayerId = currentPlayerId; }

    public Play getLastPlay() { return lastPlay; }
    public void setLastPlay(Play lastPlay) { this.lastPlay = lastPlay; }

    public boolean isOpeningTurn() { return isOpeningTurn; }
    public void setOpeningTurn(boolean openingTurn) { this.isOpeningTurn = openingTurn; }

    public boolean isGameOver() { return isGameOver; }
    public void setGameOver(boolean gameOver) { this.isGameOver = gameOver; }

    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }
}

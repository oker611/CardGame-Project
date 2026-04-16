package com.example.cardgame.model;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private List<Player> players;
    private String currentPlayerId;
    private Play lastPlay;
    private boolean openingTurn;
    private boolean gameOver;
    private String winnerId;

    public GameState() {
        this.players = new ArrayList<>();
        this.openingTurn = true;
        this.gameOver = false;
    }

    public GameState(List<Player> players,
                     String currentPlayerId,
                     Play lastPlay,
                     boolean openingTurn,
                     boolean gameOver,
                     String winnerId) {
        this.players = players != null ? new ArrayList<>(players) : new ArrayList<>();
        this.currentPlayerId = currentPlayerId;
        this.lastPlay = lastPlay;
        this.openingTurn = openingTurn;
        this.gameOver = gameOver;
        this.winnerId = winnerId;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players != null ? new ArrayList<>(players) : new ArrayList<>();
    }

    public void addPlayer(Player player) {
        if (player != null) {
            players.add(player);
        }
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(String currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public Play getLastPlay() {
        return lastPlay;
    }

    public void setLastPlay(Play lastPlay) {
        this.lastPlay = lastPlay;
    }

    public boolean isOpeningTurn() {
        return openingTurn;
    }

    public void setOpeningTurn(boolean openingTurn) {
        this.openingTurn = openingTurn;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }

    public Player getPlayerById(String playerId) {
        if (playerId == null) {
            return null;
        }

        for (Player player : players) {
            if (playerId.equals(player.getPlayerId())) {
                return player;
            }
        }
        return null;
    }

    public Player getCurrentPlayer() {
        return getPlayerById(currentPlayerId);
    }

    public void clearAllPassStatus() {
        for (Player player : players) {
            if (player != null) {
                player.setPassed(false);
            }
        }
    }

    public boolean areAllOtherPlayersPassed(String currentPlayerId) {
        for (Player player : players) {
            if (player == null) {
                continue;
            }
            if (currentPlayerId != null && currentPlayerId.equals(player.getPlayerId())) {
                continue;
            }
            if (!player.isPassed()) {
                return false;
            }
        }
        return true;
    }

    public Player findOpeningPlayer() {
        for (Player player : players) {
            if (player == null || player.getHandCards() == null) {
                continue;
            }
            for (Card card : player.getHandCards()) {
                if (card != null && card.isThreeOfDiamonds()) {
                    return player;
                }
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "GameState{" +
                "players=" + players +
                ", currentPlayerId='" + currentPlayerId + '\'' +
                ", lastPlay=" + lastPlay +
                ", openingTurn=" + openingTurn +
                ", gameOver=" + gameOver +
                ", winnerId='" + winnerId + '\'' +
                '}';
    }
}

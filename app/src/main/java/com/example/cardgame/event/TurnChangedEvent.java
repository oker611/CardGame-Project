package com.example.cardgame.event;

public class TurnChangedEvent extends GameEvent {
    private final String newCurrentPlayerId;
    private final String reason;

    public TurnChangedEvent(String newCurrentPlayerId, String reason) {
        this.newCurrentPlayerId = newCurrentPlayerId;
        this.reason = reason;
    }

    public String getNewCurrentPlayerId() {
        return newCurrentPlayerId;
    }

    public String getReason() {
        return reason;
    }
}
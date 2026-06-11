package com.example.cardgame.event;

public class TurnChangedEvent extends GameEvent {

    public enum Reason { PLAY, PASS, NEW_ROUND, GAME_START }

    private final String newCurrentPlayerId;
    private final Reason reason;

    public TurnChangedEvent(String newCurrentPlayerId, Reason reason) {
        this.newCurrentPlayerId = newCurrentPlayerId;
        this.reason = reason;
    }

    public TurnChangedEvent(String newCurrentPlayerId, String reason) {
        this.newCurrentPlayerId = newCurrentPlayerId;
        this.reason = parseReason(reason);
    }

    public String getNewCurrentPlayerId() { return newCurrentPlayerId; }

    public Reason getReason() { return reason; }

    @Deprecated
    public String getReasonString() { return reason.name(); }

    private static Reason parseReason(String s) {
        if (s == null) return Reason.PLAY;
        switch (s) {
            case "PASS": return Reason.PASS;
            case "NEW_ROUND": return Reason.NEW_ROUND;
            case "GAME_START": return Reason.GAME_START;
            default: return Reason.PLAY;
        }
    }
}

package com.example.cardgame.model;

import java.util.List;

/**
 * Data model recording a single play action by a player.
 */
public class Play {
    private final String playerId;
    // Card IDs used for UI and logic handovers
    private final List<String> cardIds;
    private final CardPattern pattern;

    public Play(String playerId, List<String> cardIds, CardPattern pattern) {
        this.playerId = playerId;
        this.cardIds = cardIds;
        this.pattern = pattern;
    }

    public String getPlayerId() { return playerId; }
    public List<String> getCardIds() { return cardIds; }
    public CardPattern getPattern() { return pattern; }
}

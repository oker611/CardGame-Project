package com.example.cardgame.network.payload;

import com.example.cardgame.model.Play;

import java.util.List;

public class PlayActionPayload {

    private String playerId;
    private List<String> selectedCardIds;
    private Play play;

    public PlayActionPayload(String playerId, List<String> selectedCardIds, Play play) {
        this.playerId = playerId;
        this.selectedCardIds = selectedCardIds;
        this.play = play;
    }

    public String getPlayerId() {
        return playerId;
    }

    public List<String> getSelectedCardIds() {
        return selectedCardIds;
    }

    public Play getPlay() {
        return play;
    }
}
// 【已修改，引入观察者模式基础框架】
package com.example.cardgame.event;

import java.util.ArrayList;
import java.util.List;

public class CardPlayedEvent extends GameEvent {
    private final String playerId;
    private final List<String> playedCardIds;

    public CardPlayedEvent(String playerId, List<String> playedCardIds) {
        this.playerId = playerId;
        this.playedCardIds = playedCardIds == null ? new ArrayList<>() : new ArrayList<>(playedCardIds);
    }

    public String getPlayerId() {
        return playerId;
    }

    public List<String> getPlayedCardIds() {
        return new ArrayList<>(playedCardIds);
    }
}

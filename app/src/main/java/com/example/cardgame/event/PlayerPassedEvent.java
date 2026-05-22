// 【已修改，引入观察者模式基础框架】
package com.example.cardgame.event;

public class PlayerPassedEvent extends GameEvent {
    private final String playerId;

    public PlayerPassedEvent(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerId() {
        return playerId;
    }
}

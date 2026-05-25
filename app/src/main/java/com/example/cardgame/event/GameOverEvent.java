// 【已修改，引入观察者模式基础框架】
package com.example.cardgame.event;

public class GameOverEvent extends GameEvent {
    private final String winnerId;

    public GameOverEvent(String winnerId) {
        this.winnerId = winnerId;
    }

    public String getWinnerId() {
        return winnerId;
    }
}

// 【已修改，引入观察者模式基础框架】
package com.example.cardgame.event;

public abstract class GameEvent {
    private final long timestamp;

    public GameEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}

package com.example.cardgame.event;

public interface IEventBus {
    void register(GameEventListener listener);
    void unregister(GameEventListener listener);
    void post(GameEvent event);
}

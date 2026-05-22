// 【已修改，引入观察者模式基础框架】
package com.example.cardgame.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {

    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();

    private EventBus() {
    }

    private static class Holder {
        private static final EventBus INSTANCE = new EventBus();
    }

    public static EventBus getInstance() {
        return Holder.INSTANCE;
    }

    public void register(GameEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregister(GameEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void post(GameEvent event) {
        if (event == null) {
            return;
        }
        for (GameEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

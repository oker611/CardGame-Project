package com.example.cardgame.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {

    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();

    private EventBus() {
        System.out.println("[EventBus] Created, listeners size=0");
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
            System.out.println("[EventBus] Registered " + listener.getClass().getSimpleName() + ", total listeners=" + listeners.size());
        } else if (listener == null) {
            System.err.println("[EventBus] register called with null listener");
        } else {
            System.out.println("[EventBus] Listener already registered: " + listener.getClass().getSimpleName());
        }
    }

    public void unregister(GameEventListener listener) {
        if (listener != null) {
            boolean removed = listeners.remove(listener);
            System.out.println("[EventBus] Unregistered " + listener.getClass().getSimpleName() + ", removed=" + removed + ", remaining=" + listeners.size());
        } else {
            System.err.println("[EventBus] unregister called with null listener");
        }
    }

    public void post(GameEvent event) {
        if (event == null) {
            System.err.println("[EventBus] post called with null event");
            return;
        }
        System.out.println("[EventBus] Posting " + event.getClass().getSimpleName() + " to " + listeners.size() + " listeners");
        for (GameEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                System.err.println("[EventBus] Listener " + listener.getClass().getSimpleName() + " threw exception: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
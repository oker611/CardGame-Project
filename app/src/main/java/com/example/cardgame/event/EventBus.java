package com.example.cardgame.event;

import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus implements IEventBus {

    private static final String TAG = "CardGame";

    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();

    private EventBus() {
        Log.d(TAG, "[EventBus] Created, listeners size=0");
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
            Log.d(TAG, "[EventBus] Registered " + listener.getClass().getSimpleName() + ", total listeners=" + listeners.size());
        } else if (listener == null) {
            Log.e(TAG, "[EventBus] register called with null listener");
        } else {
            Log.d(TAG, "[EventBus] Listener already registered: " + listener.getClass().getSimpleName());
        }
    }

    public void unregister(GameEventListener listener) {
        if (listener != null) {
            boolean removed = listeners.remove(listener);
            Log.d(TAG, "[EventBus] Unregistered " + listener.getClass().getSimpleName() + ", removed=" + removed + ", remaining=" + listeners.size());
        } else {
            Log.e(TAG, "[EventBus] unregister called with null listener");
        }
    }

    public void post(GameEvent event) {
        if (event == null) {
            Log.e(TAG, "[EventBus] post called with null event");
            return;
        }
        Log.d(TAG, "[EventBus] Posting " + event.getClass().getSimpleName()
                + " to " + listeners.size() + " listeners");
        for (GameEventListener listener : listeners) {
            try {
                dispatchTyped(listener, event);
            } catch (RuntimeException e) {
                Log.e(TAG, "[EventBus] Listener " + listener.getClass().getSimpleName()
                        + " threw exception", e);
            }
        }
    }

    private void dispatchTyped(GameEventListener listener, GameEvent event) {
        if (event instanceof CardPlayedEvent) {
            listener.onCardPlayed((CardPlayedEvent) event);
        } else if (event instanceof PlayerPassedEvent) {
            listener.onPlayerPassed((PlayerPassedEvent) event);
        } else if (event instanceof TurnChangedEvent) {
            listener.onTurnChanged((TurnChangedEvent) event);
        } else if (event instanceof GameOverEvent) {
            listener.onGameOver((GameOverEvent) event);
        } else {
            listener.onEvent(event);
        }
    }
}
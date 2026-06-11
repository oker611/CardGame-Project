package com.example.cardgame.engine;

import android.util.Log;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.event.EventBus;
import com.example.cardgame.event.IEventBus;
import com.example.cardgame.event.TurnChangedEvent;

import java.util.List;

public class TurnManager implements ITurnManager {

    private static final String TAG = "CardGame";

    private final IEventBus eventBus;

    public TurnManager(IEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public TurnManager() {
        this(EventBus.getInstance());
    }

    public void switchPlayer(GameState gameState) {
        List<Player> players = gameState.getPlayers();
        String currentId = gameState.getCurrentPlayerId();

        int currentIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getPlayerId().equals(currentId)) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = (currentIndex + 1) % players.size();
        Player nextPlayer = players.get(nextIndex);
        gameState.setCurrentPlayerId(nextPlayer.getPlayerId());

        eventBus.post(new TurnChangedEvent(nextPlayer.getPlayerId(), TurnChangedEvent.Reason.PLAY));
        Log.d(TAG, "posted TurnChangedEvent for " + nextPlayer.getPlayerId());

        gameState.setOpeningTurn(false);

        Log.d(TAG, "[CardGame][TURN] Next player: "
                + nextPlayer.getPlayerId()
                + " (" + nextPlayer.getPlayerName() + ")");
    }
}
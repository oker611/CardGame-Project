package com.example.cardgame.engine;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;

import org.junit.Test;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class SettlementManagerTest {

    private GameState state;
    private SettlementManager settlementManager;

    @Before
    public void setUp() {
        state = new GameState();
        Player p1 = new Player("P1", "Alice");
        Player p2 = new Player("P2", "Bob");
        state.setPlayers(Arrays.asList(p1, p2));
        state.setCurrentPlayerId("P1");
        settlementManager = new SettlementManager();
    }

    @Test
    public void settle_playerWithNoCardsWins() {
        Player p1 = state.getPlayerById("P1");
        p1.setHandCards(new ArrayList<>()); // empty hand = winner

        settlementManager.checkAndSettle(state);

        assertTrue(state.isGameOver());
        assertEquals("P1", state.getWinnerId());
    }

    @Test
    public void settle_withCardsNotWinner() {
        state.setGameOver(false);
        state.setWinnerId(null);
        settlementManager.checkAndSettle(state);
        // settle only sets gameOver when a player's hand is empty
    }
}

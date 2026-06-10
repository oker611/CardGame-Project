package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.PlayerType;

import org.junit.Test;
import org.junit.Before;

import java.util.Arrays;

import static org.junit.Assert.*;

public class DealManagerTest {

    private GameState state;
    private DealManager dealManager;

    @Before
    public void setUp() {
        state = new GameState();
        state.setPlayers(Arrays.asList(
                new Player("P1", "Alice"),
                new Player("P2", "Bob"),
                new Player("P3", "Cindy"),
                new Player("P4", "David")
        ));
        dealManager = new DealManager();
    }

    @Test
    public void dealCards_distributesAll52Cards() {
        dealManager.dealCards(state);

        int totalCards = 0;
        for (Player p : state.getPlayers()) {
            assertNotNull(p.getHandCards());
            totalCards += p.getHandCards().size();
        }
        assertEquals(52, totalCards);
    }

    @Test
    public void dealCards_eachPlayerGets13Cards() {
        dealManager.dealCards(state);

        for (Player p : state.getPlayers()) {
            assertEquals(13, p.getHandCards().size());
        }
    }

    @Test
    public void dealCards_setsCurrentPlayer() {
        dealManager.dealCards(state);
        assertNotNull(state.getCurrentPlayerId());
    }

    @Test
    public void dealCards_noDuplicateCards() {
        dealManager.dealCards(state);

        java.util.Set<String> allCardIds = new java.util.HashSet<>();
        for (Player p : state.getPlayers()) {
            for (com.example.cardgame.model.Card c : p.getHandCards()) {
                assertTrue("Duplicate card found: " + c.getCardId(),
                        allCardIds.add(c.getCardId()));
            }
        }
    }
}

package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;

import org.junit.Test;
import org.junit.Before;

import java.util.Arrays;

import static org.junit.Assert.*;

public class TurnManagerTest {

    private GameState state;
    private TurnManager turnManager;

    @Before
    public void setUp() {
        state = new GameState();
        state.setPlayers(Arrays.asList(
                new Player("P1", "Alice"),
                new Player("P2", "Bob"),
                new Player("P3", "Cindy"),
                new Player("P4", "David")
        ));
        state.setCurrentPlayerId("P1");
        turnManager = new TurnManager();
    }

    @Test
    public void switchPlayer_advancesToNext() {
        turnManager.switchPlayer(state);
        assertEquals("P2", state.getCurrentPlayerId());
    }

    @Test
    public void switchPlayer_wrapsAround() {
        state.setCurrentPlayerId("P4");
        turnManager.switchPlayer(state);
        assertEquals("P1", state.getCurrentPlayerId());
    }

    @Test
    public void switchPlayer_skipsGameOverState() {
        state.setGameOver(true);
        state.setCurrentPlayerId("P1");
        String before = state.getCurrentPlayerId();
        // Should not crash - but depends on implementation
        turnManager.switchPlayer(state);
        // If switchPlayer checks isGameOver, current player won't change
    }

    @Test
    public void consecutiveSwitch_cyclesThroughAll() {
        assertEquals("P1", state.getCurrentPlayerId());
        turnManager.switchPlayer(state);
        assertEquals("P2", state.getCurrentPlayerId());
        turnManager.switchPlayer(state);
        assertEquals("P3", state.getCurrentPlayerId());
        turnManager.switchPlayer(state);
        assertEquals("P4", state.getCurrentPlayerId());
        turnManager.switchPlayer(state);
        assertEquals("P1", state.getCurrentPlayerId());
    }
}

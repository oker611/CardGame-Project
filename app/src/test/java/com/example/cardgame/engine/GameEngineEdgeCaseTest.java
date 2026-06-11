package com.example.cardgame.engine;

import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PassResult;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class GameEngineEdgeCaseTest {

    @Test(expected = NullPointerException.class)
    public void nullState_playCards_throwsNPE() {
        GameEngine engine = new GameEngine();
        engine.playCards("P1", Collections.singletonList("SA"));
    }

    @Test
    public void nullState_getGameState_returnsNull() {
        GameEngine engine = new GameEngine();
        assertNull(engine.getGameState());
    }

    @Test
    public void emptyCards_rejected() {
        GameEngine engine = new GameEngine();
        engine.initializeGame(
                Arrays.asList(new Player("P1", "A"), new Player("P2", "B"),
                        new Player("P3", "C"), new Player("P4", "D")),
                RuleConfig.SOUTHERN);
        engine.dealCards();
        String current = engine.getGameState().getCurrentPlayerId();
        PlayResult r = engine.playCards(current, Collections.emptyList());
        assertFalse(r.isSuccess());
    }

    @Test
    public void nullCards_rejected() {
        GameEngine engine = new GameEngine();
        engine.initializeGame(
                Arrays.asList(new Player("P1", "A"), new Player("P2", "B"),
                        new Player("P3", "C"), new Player("P4", "D")),
                RuleConfig.SOUTHERN);
        engine.dealCards();
        String current = engine.getGameState().getCurrentPlayerId();
        PlayResult r = engine.playCards(current, null);
        assertFalse(r.isSuccess());
    }

    @Test
    public void gameOver_playCards_rejected() {
        GameEngine engine = new GameEngine();
        engine.initializeGame(
                Arrays.asList(new Player("P1", "A"), new Player("P2", "B"),
                        new Player("P3", "C"), new Player("P4", "D")),
                RuleConfig.SOUTHERN);
        engine.dealCards();
        engine.getGameState().setGameOver(true);
        PlayResult r = engine.playCards(engine.getGameState().getCurrentPlayerId(),
                Collections.singletonList("S3"));
        assertFalse(r.isSuccess());
    }
}

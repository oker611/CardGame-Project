package com.example.cardgame.ai;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;
import com.example.cardgame.model.CardPattern;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.rule.ConfigurableRuleEngine;

import org.junit.Test;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class PhaseManagerTest {

    private PhaseManager phaseManager;
    private GameState state;

    @Before
    public void setUp() {
        phaseManager = new PhaseManager(new ConfigurableRuleEngine(RuleConfig.SOUTHERN));
        state = new GameState();
        state.setPlayers(Arrays.asList(
                new Player("P1", "Alice"),
                new Player("P2", "Bob"),
                new Player("P3", "Cindy"),
                new Player("P4", "David")
        ));
    }

    @Test
    public void hasBigPattern_emptyHand_returnsFalse() {
        assertFalse(phaseManager.hasBigPattern(Collections.emptyList()));
    }

    @Test
    public void hasBigPattern_singleCard_returnsFalse() {
        assertFalse(phaseManager.hasBigPattern(
                Collections.singletonList(new Card("S3", Suit.SPADES, Rank.THREE))));
    }

    @Test
    public void shouldForceBeat_noLastPlay_returnsFalse() {
        Player p1 = state.getPlayerById("P1");
        p1.setHandCards(Arrays.asList(
                new Card("SA", Suit.SPADES, Rank.ACE)));
        assertFalse(phaseManager.shouldForceBeat(p1, state, null));
    }
}

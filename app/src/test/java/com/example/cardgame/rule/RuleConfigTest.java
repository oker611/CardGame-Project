package com.example.cardgame.rule;

import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;

import org.junit.Test;

import static org.junit.Assert.*;

public class RuleConfigTest {

    // ========== SOUTHERN ==========

    @Test
    public void southern_aceHigherThanKing() {
        int ace = RuleConfig.SOUTHERN.rankWeights.get(Rank.ACE);
        int king = RuleConfig.SOUTHERN.rankWeights.get(Rank.KING);
        assertTrue(ace > king);
    }

    @Test
    public void southern_twoHasHighestWeight() {
        int two = RuleConfig.SOUTHERN.rankWeights.get(Rank.TWO);
        int ace = RuleConfig.SOUTHERN.rankWeights.get(Rank.ACE);
        assertTrue(two > ace);
    }

    @Test
    public void southern_threeHasLowestWeight() {
        int three = RuleConfig.SOUTHERN.rankWeights.get(Rank.THREE);
        int four = RuleConfig.SOUTHERN.rankWeights.get(Rank.FOUR);
        assertTrue(three < four);
    }

    @Test
    public void southern_spadeHigherThanHeart() {
        int spade = RuleConfig.SOUTHERN.suitWeights.get(Suit.SPADES);
        int heart = RuleConfig.SOUTHERN.suitWeights.get(Suit.HEARTS);
        assertTrue(spade > heart);
    }

    @Test
    public void southern_allRanksHaveWeights() {
        for (Rank r : Rank.values()) {
            assertNotNull(RuleConfig.SOUTHERN.rankWeights.get(r));
        }
    }

    @Test
    public void southern_allSuitsHaveWeights() {
        for (Suit s : Suit.values()) {
            assertNotNull(RuleConfig.SOUTHERN.suitWeights.get(s));
        }
    }

    @Test
    public void southern_requiresDiamondThree() {
        assertEquals(Rank.THREE, RuleConfig.SOUTHERN.requiredOpeningRank);
        assertEquals(Suit.DIAMONDS, RuleConfig.SOUTHERN.requiredOpeningSuit);
    }

    // ========== NORTHERN ==========

    @Test
    public void northern_aceHigherThanKing() {
        int ace = RuleConfig.NORTHERN.rankWeights.get(Rank.ACE);
        int king = RuleConfig.NORTHERN.rankWeights.get(Rank.KING);
        assertTrue(ace > king);
    }

    @Test
    public void northern_diamondHigherThanClub() {
        int diamond = RuleConfig.NORTHERN.suitWeights.get(Suit.DIAMONDS);
        int club = RuleConfig.NORTHERN.suitWeights.get(Suit.CLUBS);
        assertTrue(diamond > club);
    }

    @Test
    public void northern_noOpeningRequirement() {
        assertNull(RuleConfig.NORTHERN.requiredOpeningRank);
        assertNull(RuleConfig.NORTHERN.requiredOpeningSuit);
    }

    // ========== immutability ==========

    @Test(expected = UnsupportedOperationException.class)
    public void southern_rankWeightsImmutable() {
        RuleConfig.SOUTHERN.rankWeights.put(Rank.ACE, 999);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void southern_suitWeightsImmutable() {
        RuleConfig.SOUTHERN.suitWeights.put(Suit.SPADES, 999);
    }
}

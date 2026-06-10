package com.example.cardgame.util;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;

import org.junit.Test;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class CardTrackerTest {

    private CardTracker tracker;

    @Before
    public void setUp() {
        tracker = new CardTracker();
    }

    // ========== reset ==========

    @Test
    public void reset_clearsAllState() {
        Card ace = new Card("SA", Suit.SPADES, Rank.ACE);
        tracker.onCardPlayed(ace, "P1");
        tracker.reset();

        assertEquals(0, tracker.getPlayedCount());
        assertEquals(4, tracker.getRemainingCountByRank(Rank.ACE));
    }

    // ========== onCardPlayed ==========

    @Test
    public void onCardPlayed_reducesRemainingCount() {
        tracker.onCardPlayed(new Card("S2", Suit.SPADES, Rank.TWO), "P1");
        assertEquals(3, tracker.getRemainingCountByRank(Rank.TWO));
    }

    @Test
    public void onCardPlayed_tracksPlayedBy() {
        Card ace = new Card("SA", Suit.SPADES, Rank.ACE);
        tracker.onCardPlayed(ace, "P1");
        assertEquals("P1", tracker.getPlayedBy(ace));
    }

    @Test
    public void onCardPlayed_dedupsSameCard() {
        Card ace = new Card("SA", Suit.SPADES, Rank.ACE);
        tracker.onCardPlayed(ace, "P1");
        tracker.onCardPlayed(ace, "P2");
        assertEquals(3, tracker.getRemainingCountByRank(Rank.ACE));
        assertEquals(1, tracker.getPlayedCount());
    }

    @Test
    public void onCardPlayed_nullCardIgnored() {
        tracker.onCardPlayed(null, "P1");
        assertEquals(0, tracker.getPlayedCount());
    }

    @Test
    public void onCardPlayed_neverGoesNegative() {
        for (int i = 0; i < 10; i++) {
            tracker.onCardPlayed(new Card("S" + i, Suit.SPADES, Rank.ACE), "P1");
        }
        assertEquals(0, tracker.getRemainingCountByRank(Rank.ACE));
    }

    // ========== onCardsPlayed ==========

    @Test
    public void onCardsPlayed_batchUpdate() {
        List<Card> cards = Arrays.asList(
                new Card("S2", Suit.SPADES, Rank.TWO),
                new Card("H3", Suit.HEARTS, Rank.THREE)
        );
        tracker.onCardsPlayed(cards, "P1");

        assertEquals(3, tracker.getRemainingCountByRank(Rank.TWO));
        assertEquals(3, tracker.getRemainingCountByRank(Rank.THREE));
        assertEquals(2, tracker.getPlayedCount());
    }

    @Test
    public void onCardsPlayed_emptyList() {
        tracker.onCardsPlayed(Collections.emptyList(), "P1");
        assertEquals(0, tracker.getPlayedCount());
    }

    // ========== getRemainingCountBySuit ==========

    @Test
    public void remainingCountBySuit_defaultsTo13() {
        assertEquals(13, tracker.getRemainingCountBySuit(Suit.SPADES));
    }

    @Test
    public void remainingCountBySuit_afterPlay() {
        tracker.onCardPlayed(new Card("SA", Suit.SPADES, Rank.ACE), "P1");
        assertEquals(12, tracker.getRemainingCountBySuit(Suit.SPADES));
    }

    // ========== isPlayed ==========

    @Test
    public void isPlayed_tracksCorrectly() {
        Card ace = new Card("SA", Suit.SPADES, Rank.ACE);
        assertFalse(tracker.isPlayed(ace));
        tracker.onCardPlayed(ace, "P1");
        assertTrue(tracker.isPlayed(ace));
    }

    // ========== getPlayedCards ==========

    @Test
    public void getPlayedCards_returnsCopy() {
        tracker.onCardPlayed(new Card("SA", Suit.SPADES, Rank.ACE), "P1");
        Set<Card> played = tracker.getPlayedCards();
        assertEquals(1, played.size());
        played.clear();
        assertEquals(1, tracker.getPlayedCount());
    }

    // ========== getRemainingProbability ==========

    @Test
    public void getRemainingProbability_fullDeck() {
        Card ace = new Card("SA", Suit.SPADES, Rank.ACE);
        assertEquals(1.0, tracker.getRemainingProbability(ace), 0.001);
    }

    @Test
    public void getRemainingProbability_afterPlay() {
        Card ace = new Card("SA", Suit.SPADES, Rank.ACE);
        tracker.onCardPlayed(ace, "P1");
        assertEquals(0.75, tracker.getRemainingProbability(ace), 0.001);
    }

    @Test
    public void getRemainingProbability_allPlayed() {
        for (int i = 0; i < 4; i++) {
            tracker.onCardPlayed(new Card("A" + i, Suit.values()[i % 4], Rank.ACE), "P1");
        }
        Card ace = new Card("AX", Suit.SPADES, Rank.ACE);
        assertEquals(0.0, tracker.getRemainingProbability(ace), 0.001);
    }

    // ========== recordPlay / getHistorySummary ==========

    @Test
    public void recordPlay_storesHistory() {
        tracker.recordPlay("P2", "S2,H3");
        tracker.recordPlay("P2", "C4,D5");
        assertTrue(tracker.getHistorySummary("P2").contains("S2,H3"));
    }

    @Test
    public void getHistorySummary_emptyForUnknownPlayer() {
        assertEquals("", tracker.getHistorySummary("P99"));
    }

    // ========== getRemainingCards ==========

    @Test
    public void getRemainingCards_filtersPlayed() {
        Card ace = new Card("SA", Suit.SPADES, Rank.ACE);
        Card king = new Card("SK", Suit.SPADES, Rank.KING);
        tracker.onCardPlayed(ace, "P1");

        List<Card> remaining = tracker.getRemainingCards(Arrays.asList(ace, king));
        assertEquals(1, remaining.size());
        assertTrue(remaining.contains(king));
    }

    // ========== getHighProbabilityCards ==========

    @Test
    public void getHighProbabilityCards_sortsByProbability() {
        Card ace = new Card("SA", Suit.SPADES, Rank.ACE);
        tracker.onCardPlayed(ace, "P1");

        Card ace2 = new Card("HA", Suit.HEARTS, Rank.ACE);
        Card king = new Card("SK", Suit.SPADES, Rank.KING);
        List<Card> top = tracker.getHighProbabilityCards(Arrays.asList(ace2, king), 1);
        assertEquals(1, top.size());
        assertTrue(top.get(0).getRank() == Rank.KING);
    }

    // ========== getDistributionSummary ==========

    @Test
    public void getDistributionSummary_includesPlayedInfo() {
        tracker.onCardPlayed(new Card("SA", Suit.SPADES, Rank.ACE), "P1");
        String summary = tracker.getDistributionSummary();
        assertTrue(summary.contains("ACE"));
    }
}

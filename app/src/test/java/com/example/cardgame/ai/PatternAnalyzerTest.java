package com.example.cardgame.ai;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.CardPattern;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class PatternAnalyzerTest {

    // ========== getPatternScore ==========

    @Test
    public void patternScore_single() { assertEquals(1, PatternAnalyzer.getPatternScore(CardPattern.SINGLE)); }
    @Test
    public void patternScore_pair() { assertEquals(2, PatternAnalyzer.getPatternScore(CardPattern.PAIR)); }
    @Test
    public void patternScore_straightFlush() { assertEquals(9, PatternAnalyzer.getPatternScore(CardPattern.STRAIGHT_FLUSH)); }
    @Test
    public void patternScore_null() { assertEquals(0, PatternAnalyzer.getPatternScore(null)); }

    // ========== isFlushSimple ==========

    @Test
    public void isFlush_allSameSuit() {
        List<Card> cards = Arrays.asList(
                new Card("SA", Suit.SPADES, Rank.ACE),
                new Card("SK", Suit.SPADES, Rank.KING),
                new Card("S3", Suit.SPADES, Rank.THREE),
                new Card("S5", Suit.SPADES, Rank.FIVE),
                new Card("S7", Suit.SPADES, Rank.SEVEN));
        assertTrue(PatternAnalyzer.isFlushSimple(cards));
    }

    @Test
    public void isFlush_mixedSuits() {
        List<Card> cards = Arrays.asList(
                new Card("SA", Suit.SPADES, Rank.ACE),
                new Card("HK", Suit.HEARTS, Rank.KING),
                new Card("S3", Suit.SPADES, Rank.THREE),
                new Card("S5", Suit.SPADES, Rank.FIVE),
                new Card("S7", Suit.SPADES, Rank.SEVEN));
        assertFalse(PatternAnalyzer.isFlushSimple(cards));
    }

    @Test public void isFlush_wrongSize() { assertFalse(PatternAnalyzer.isFlushSimple(Arrays.asList(new Card("SA", Suit.SPADES, Rank.ACE)))); }

    // ========== isStraightSimple ==========

    @Test
    public void isStraight_consecutive() {
        List<Card> cards = Arrays.asList(
                new Card("S3", Suit.SPADES, Rank.THREE),
                new Card("H4", Suit.HEARTS, Rank.FOUR),
                new Card("D5", Suit.DIAMONDS, Rank.FIVE),
                new Card("C6", Suit.CLUBS, Rank.SIX),
                new Card("S7", Suit.SPADES, Rank.SEVEN));
        assertTrue(PatternAnalyzer.isStraightSimple(cards));
    }

    @Test
    public void isStraight_notConsecutive() {
        List<Card> cards = Arrays.asList(
                new Card("S3", Suit.SPADES, Rank.THREE),
                new Card("H4", Suit.HEARTS, Rank.FOUR),
                new Card("D5", Suit.DIAMONDS, Rank.FIVE),
                new Card("C6", Suit.CLUBS, Rank.SIX),
                new Card("S8", Suit.SPADES, Rank.EIGHT));
        assertFalse(PatternAnalyzer.isStraightSimple(cards));
    }

    // ========== isFourOfAKindSimple ==========

    @Test
    public void isFourOfAKind_valid() {
        List<Card> cards = Arrays.asList(
                new Card("SA", Suit.SPADES, Rank.ACE),
                new Card("HA", Suit.HEARTS, Rank.ACE),
                new Card("DA", Suit.DIAMONDS, Rank.ACE),
                new Card("CA", Suit.CLUBS, Rank.ACE),
                new Card("S2", Suit.SPADES, Rank.TWO));
        assertTrue(PatternAnalyzer.isFourOfAKindSimple(cards));
    }

    @Test
    public void isFourOfAKind_notFour() {
        List<Card> cards = Arrays.asList(
                new Card("SA", Suit.SPADES, Rank.ACE),
                new Card("HA", Suit.HEARTS, Rank.ACE),
                new Card("DA", Suit.DIAMONDS, Rank.ACE),
                new Card("SK", Suit.SPADES, Rank.KING),
                new Card("S2", Suit.SPADES, Rank.TWO));
        assertFalse(PatternAnalyzer.isFourOfAKindSimple(cards));
    }

    // ========== isFullHouseSimple ==========

    @Test
    public void isFullHouse_valid() {
        List<Card> cards = Arrays.asList(
                new Card("SA", Suit.SPADES, Rank.ACE),
                new Card("HA", Suit.HEARTS, Rank.ACE),
                new Card("DA", Suit.DIAMONDS, Rank.ACE),
                new Card("SK", Suit.SPADES, Rank.KING),
                new Card("HK", Suit.HEARTS, Rank.KING));
        assertTrue(PatternAnalyzer.isFullHouseSimple(cards));
    }

    @Test
    public void isFullHouse_notFull() { assertFalse(PatternAnalyzer.isFullHouseSimple(Arrays.asList(new Card("SA", Suit.SPADES, Rank.ACE)))); }

    // ========== getPatternType ==========

    @Test
    public void getPatternType_flush() {
        List<Card> cards = Arrays.asList(
                new Card("SA", Suit.SPADES, Rank.ACE),
                new Card("SK", Suit.SPADES, Rank.KING),
                new Card("S3", Suit.SPADES, Rank.THREE),
                new Card("S5", Suit.SPADES, Rank.FIVE),
                new Card("S7", Suit.SPADES, Rank.SEVEN));
        assertEquals(2, PatternAnalyzer.getPatternType(cards));
    }

    @Test
    public void getPatternType_straightFlush() {
        List<Card> cards = Arrays.asList(
                new Card("S3", Suit.SPADES, Rank.THREE),
                new Card("S4", Suit.SPADES, Rank.FOUR),
                new Card("S5", Suit.SPADES, Rank.FIVE),
                new Card("S6", Suit.SPADES, Rank.SIX),
                new Card("S7", Suit.SPADES, Rank.SEVEN));
        assertEquals(5, PatternAnalyzer.getPatternType(cards));
    }
}

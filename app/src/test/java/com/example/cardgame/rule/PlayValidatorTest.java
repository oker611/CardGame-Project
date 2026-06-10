package com.example.cardgame.rule;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;

import org.junit.Test;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class PlayValidatorTest {

    private PlayValidator validator;

    @Before
    public void setUp() {
        validator = new PlayValidator(RuleConfig.SOUTHERN);
    }

    // ========== Pass validation ==========

    @Test
    public void firstRoundFirstTurn_cannotPass() {
        PlayValidator.ValidationResult result = validator.validatePlay(
                Collections.emptyList(), null, true, true);
        assertFalse(result.valid);
    }

    @Test
    public void pass_allowedWhenNotFirstTurn() {
        PlayValidator.ValidationResult result = validator.validatePlay(
                Collections.emptyList(),
                Collections.singletonList(new Card("S3", Suit.SPADES, Rank.THREE)),
                false, false);
        assertTrue(result.valid);
    }

    // ========== First round diamond-3 rule ==========

    @Test
    public void firstRound_mustContainThreeOfDiamonds() {
        List<Card> cards = Collections.singletonList(
                new Card("S3", Suit.SPADES, Rank.THREE));
        PlayValidator.ValidationResult result = validator.validatePlay(
                cards, null, true, true);
        assertFalse(result.valid);
    }

    @Test
    public void firstRound_validWithDiamondThree() {
        List<Card> cards = Collections.singletonList(
                new Card("D3", Suit.DIAMONDS, Rank.THREE));
        PlayValidator.ValidationResult result = validator.validatePlay(
                cards, null, true, true);
        assertTrue(result.valid);
    }

    // ========== Beat last play ==========

    @Test
    public void single_largerBeatsSmaller() {
        List<Card> lastPlay = Collections.singletonList(
                new Card("S3", Suit.SPADES, Rank.THREE));

        List<Card> current = Collections.singletonList(
                new Card("S5", Suit.SPADES, Rank.FIVE));

        PlayValidator.ValidationResult result = validator.validatePlay(
                current, lastPlay, false, false);
        assertTrue(result.valid);
    }

    @Test
    public void single_smallerCannotBeatLarger() {
        List<Card> lastPlay = Collections.singletonList(
                new Card("S5", Suit.SPADES, Rank.FIVE));

        List<Card> current = Collections.singletonList(
                new Card("S3", Suit.SPADES, Rank.THREE));

        PlayValidator.ValidationResult result = validator.validatePlay(
                current, lastPlay, false, false);
        assertFalse(result.valid);
    }

    // ========== Pair validation ==========

    @Test
    public void pair_validTwoSameRank() {
        List<Card> cards = Arrays.asList(
                new Card("S3", Suit.SPADES, Rank.THREE),
                new Card("H3", Suit.HEARTS, Rank.THREE));
        PlayValidator.ValidationResult result = validator.validatePlay(
                cards, null, false, false);
        assertTrue(result.valid);
    }

    // ========== Null / empty safety ==========

    @Test
    public void nullCurrentCards_treatedAsPass() {
        PlayValidator.ValidationResult result = validator.validatePlay(
                null, null, false, false);
        assertTrue(result.valid);
    }

    @Test
    public void firstTurn_newRound_noPriorPlay() {
        List<Card> cards = Collections.singletonList(
                new Card("S7", Suit.SPADES, Rank.SEVEN));
        PlayValidator.ValidationResult result = validator.validatePlay(
                cards, null, false, false);
        assertTrue(result.valid);
    }
}

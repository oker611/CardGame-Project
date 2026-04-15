package com.example.cardgame.model;

/**
 * Card pattern enumeration for valid plays.
 */
public enum CardPattern {
    SINGLE,          // Single card
    PAIR,            // Pair
    STRAIGHT,        // Straight
    FLUSH,           // Flush
    FULL_HOUSE,      // Full House
    FOUR_OF_A_KIND,  // Four of a Kind (Bomb)
    STRAIGHT_FLUSH   // Straight Flush
}

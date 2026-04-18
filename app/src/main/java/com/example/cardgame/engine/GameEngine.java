package com.example.cardgame.engine;

import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.CardPattern;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;

import java.util.List;

/**
 * Core game engine class that coordinates game flow and managers.
 */
public class GameEngine {

    private GameState gameState;
    private RuleConfig ruleConfig;

    private final DealManager dealManager;
    private final TurnManager turnManager;
    private final SettlementManager settlementManager;

    public GameEngine() {
        this.dealManager = new DealManager();
        this.turnManager = new TurnManager();
        this.settlementManager = new SettlementManager();
    }

    /**
     * Initialize the game with players and rules.
     */
    public void initializeGame(List<Player> players, RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
        this.gameState = new GameState();
        this.gameState.setPlayers(players);
        this.gameState.setGameOver(false);
        this.gameState.setWinnerId(null);
        this.gameState.setLastPlay(null);
        this.gameState.setOpeningTurn(true);

        System.out.println("[CardGame][START] Game initialized.");
    }

    /**
     * Shuffles and deals cards to players, determining the first player.
     */
    public void dealCards() {
        if (gameState != null) {
            dealManager.dealCards(gameState);
        }
    }

    /**
     * Execute play cards action and check for settlement.
     *
     * @param playerId ID of the player
     * @param selectedCardIds Selected card IDs
     */
    public PlayResult playCards(String playerId, List<String> selectedCardIds) {
        if (gameState == null) {
            System.out.println("[CardGame][ERROR] playCards failed: gameState is null.");
            return new PlayResult(false, "Game state is null.", null);
        }

        if (gameState.isGameOver()) {
            System.out.println("[CardGame][ERROR] playCards failed: game already over.");
            return new PlayResult(false, "Game is already over.", gameState);
        }

        Player currentPlayer = gameState.getCurrentPlayer();
        System.out.println("[CardGame][PLAY] Current player: " + gameState.getCurrentPlayerId());

        if (currentPlayer == null) {
            System.out.println("[CardGame][VALIDATION] valid=false, message=Current player not found.");
            return new PlayResult(false, "Current player not found.", gameState);
        }

        if (!currentPlayer.getPlayerId().equals(playerId)) {
            System.out.println("[CardGame][VALIDATION] valid=false, message=Not current player's turn.");
            return new PlayResult(false, "Not current player's turn.", gameState);
        }

        if (selectedCardIds == null || selectedCardIds.isEmpty()) {
            System.out.println("[CardGame][VALIDATION] valid=false, message=No cards selected.");
            return new PlayResult(false, "No cards selected.", gameState);
        }

        List<Card> selectedCards = currentPlayer.findCardsByIds(selectedCardIds);

        System.out.println("[CardGame][PLAY] Selected card IDs: " + selectedCardIds);
        System.out.println("[CardGame][PLAY] Selected cards: " + formatCards(selectedCards));

        if (selectedCards.size() != selectedCardIds.size()) {
            System.out.println("[CardGame][VALIDATION] valid=false, message=Some cards are not in player's hand.");
            return new PlayResult(false, "Some cards are not in player's hand.", gameState);
        }

        if (gameState.isOpeningTurn() && !containsThreeOfDiamonds(selectedCards)) {
            System.out.println("[CardGame][VALIDATION] valid=false, message=Opening play must contain 3 of Diamonds.");
            return new PlayResult(false, "Opening play must contain 3 of Diamonds.", gameState);
        }

        Play currentPlay = new Play(playerId, selectedCards, guessPattern(selectedCards));

        if (currentPlay.getPattern() == CardPattern.INVALID) {
            System.out.println("[CardGame][VALIDATION] valid=false, message=Unsupported card pattern.");
            return new PlayResult(false, "Unsupported card pattern.", gameState);
        }

        gameState.setLastPlay(currentPlay);
        gameState.setOpeningTurn(false);
        gameState.clearAllPassStatus();
        currentPlayer.setPassed(false);

        for (Card card : selectedCards) {
            currentPlayer.removeCard(card);
        }

        System.out.println("[CardGame][VALIDATION] valid=true, message=Play accepted, pattern="
                + currentPlay.getPattern());

        settlementManager.checkAndSettle(gameState);
        if (!gameState.isGameOver()) {
            turnManager.switchPlayer(gameState);
            return new PlayResult(true, "Play success.", gameState);
        }

        return new PlayResult(true, "Play success. Game over.", gameState);
    }

    /**
     * Player passes their turn.
     */
    public PassResult passTurn(String playerId) {
        if (gameState == null) {
            System.out.println("[CardGame][ERROR] passTurn failed: gameState is null.");
            return new PassResult(false, "Game state is null.", null);
        }

        if (gameState.isGameOver()) {
            System.out.println("[CardGame][ERROR] passTurn failed: game already over.");
            return new PassResult(false, "Game is already over.", gameState);
        }

        Player currentPlayer = gameState.getCurrentPlayer();
        if (currentPlayer == null) {
            System.out.println("[CardGame][VALIDATION] valid=false, message=Current player not found.");
            return new PassResult(false, "Current player not found.", gameState);
        }

        if (!currentPlayer.getPlayerId().equals(playerId)) {
            System.out.println("[CardGame][VALIDATION] valid=false, message=Not current player's turn, cannot pass.");
            return new PassResult(false, "Not current player's turn, cannot pass.", gameState);
        }

        if (gameState.isOpeningTurn()) {
            System.out.println("[CardGame][VALIDATION] valid=false, message=Opening player cannot pass.");
            return new PassResult(false, "Opening player cannot pass.", gameState);
        }

        currentPlayer.setPassed(true);
        System.out.println("[CardGame][PASS] Player "
                + currentPlayer.getPlayerId()
                + " (" + currentPlayer.getPlayerName() + ") passed.");

        if (gameState.areAllOtherPlayersPassed(currentPlayer.getPlayerId())) {
            gameState.clearAllPassStatus();
            gameState.setLastPlay(null);
            gameState.setOpeningTurn(true);
            System.out.println("[CardGame][PASS] All other players passed. Round reset.");
        }

        turnManager.switchPlayer(gameState);
        return new PassResult(true, "Pass success.", gameState);
    }

    public boolean isGameOver() {
        return gameState != null && gameState.isGameOver();
    }

    /**
     * Gets the ID of the winner.
     */
    public String getWinnerId() {
        return (gameState != null) ? gameState.getWinnerId() : null;
    }

    /**
     * Access the raw game state (for Controller use).
     */
    public GameState getGameState() {
        return gameState;
    }

    private boolean containsThreeOfDiamonds(List<Card> cards) {
        for (Card card : cards) {
            if (card != null && card.isThreeOfDiamonds()) {
                return true;
            }
        }
        return false;
    }

    private CardPattern guessPattern(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return CardPattern.INVALID;
        }
        if (cards.size() == 1) {
            return CardPattern.SINGLE;
        }
        if (cards.size() == 2 && sameRank(cards)) {
            return CardPattern.PAIR;
        }
        if (cards.size() == 3 && sameRank(cards)) {
            return CardPattern.TRIPLE;
        }
        return CardPattern.INVALID;
    }

    private boolean sameRank(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }
        Card first = cards.get(0);
        for (Card card : cards) {
            if (card == null || first.getRank() != card.getRank()) {
                return false;
            }
        }
        return true;
    }

    private String formatCards(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cards.size(); i++) {
            sb.append(cards.get(i).getDisplayText());
            if (i < cards.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
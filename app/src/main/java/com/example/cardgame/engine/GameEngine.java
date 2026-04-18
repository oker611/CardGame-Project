package com.example.cardgame.engine;

import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.ValidationResult;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.CardPattern;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.rule.RuleEngine;

import java.util.List;

/**
 * Core game engine class that coordinates game flow and managers.
 */
public class GameEngine {

    private GameState gameState;
    private RuleConfig ruleConfig;
    private RuleEngine ruleEngine;

    private final DealManager dealManager;
    private final TurnManager turnManager;
    private final SettlementManager settlementManager;

    public GameEngine() {
        this.dealManager = new DealManager();
        this.turnManager = new TurnManager();
        this.settlementManager = new SettlementManager();
        this.ruleEngine = ruleEngine;
    }

    /**
     * Initialize the game with players and rules.
     */
    public void initializeGame(List<Player> players, RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
        this.gameState = new GameState();
        this.gameState.setPlayers(players);
        this.gameState.setGameOver(false);
        this.gameState.setOpeningTurn(true);
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
     * @param playerId ID of the player
     * @param selectedCardIds Selected card IDs
     */
    public PlayResult playCards(String playerId, List<String> selectedCardIds) {
        Player player = gameState.getPlayerById(playerId);
        if (player == null || !playerId.equals(gameState.getCurrentPlayerId())) {
            return createPlayResult(false, "Not your turn (非当前玩家回合)", gameState);
        }
        List<Card> selectedCards = player.findCardsByIds(selectedCardIds);
        Play currentPlay = new Play(playerId, selectedCards, CardPattern.INVALID);

        // =================================================================================
        // TODO: Wait for M5 (Rule Layer) to refactor RuleEngine interfaces to match standard.
        // =================================================================================
        /*
        currentPlay.setPattern(ruleEngine.recognizePattern(selectedCards));
        ValidationResult validation = ruleEngine.validatePlay(
            currentPlay,
            gameState.getLastPlay(),
            ruleConfig,
            gameState.isOpeningTurn()
        );
        if (!validation.isValid()) {
            return createPlayResult(false, validation.getMessage(), gameState);
        }
        */
        // 临时逻辑：假设出牌永远合法，强制放行以测试 Engine 主流程
        currentPlay.setPattern(CardPattern.SINGLE);

        //remove cards from hand and update last play
        for (String cardId : selectedCardIds) {
            player.removeCardById(cardId);
        }
        gameState.setLastPlay(currentPlay);
        player.setPassed(false);
        settlementManager.checkAndSettle(gameState);
        if (!gameState.isGameOver()) {
            turnManager.switchPlayer(gameState);
        }
        return createPlayResult(true, "Play successful", gameState);
    }

    /**
     * Player passes their turn.
     */
    public PassResult passTurn(String playerId) {
        if (gameState == null || gameState.isGameOver()) {
            return createPassResult(false, "Game is over ", gameState);
        }

        Player player = gameState.getPlayerById(playerId);
        if (player == null || !playerId.equals(gameState.getCurrentPlayerId())) {
            return createPassResult(false, "Not your turn", gameState);
        }
        if (gameState.isOpeningTurn()) {
            return createPassResult(false, "Cannot pass on opening turn", gameState);
        }
        player.setPassed(true);
        turnManager.switchPlayer(gameState);

        // Check if all OTHER players have passed to start a new round
        if (gameState.areAllOtherPlayersPassed(gameState.getCurrentPlayerId())) {
            gameState.setLastPlay(null);
            gameState.clearAllPassStatus();
        }

        return createPassResult(true, "Pass successful ", gameState);
    }

    // Helper methods for creating DTO results
    private PlayResult createPlayResult(boolean success, String msg, GameState state) {
        return new PlayResult(success, msg, state);
    }

    private PassResult createPassResult(boolean success, String msg, GameState state) {
        return new PassResult(success, msg, state);
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
}
package com.example.cardgame.engine;

<<<<<<< HEAD
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;
=======
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PassResult;
>>>>>>> origin/dev-czh-ui-zhy

import java.util.List;

/**
<<<<<<< HEAD
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
        //  TODO: Validate play with RuleEngine
        //  TODO: Remove cards from player's hand

        //  Trigger settlement check immediately after cards are played
        if (gameState != null) {
            settlementManager.checkAndSettle(gameState);
            if (!gameState.isGameOver()) {
                turnManager.switchPlayer(gameState);
            }
        }

        return null;
    }

    /**
     * Player passes their turn.
     */
    public PassResult passTurn(String playerId) {
        if (gameState != null && !gameState.isGameOver()) {
            // TODO: Update pass records in GameState 
            turnManager.switchPlayer(gameState);
        }
        return null;
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
=======
 * Core game engine interface, defining the main flow of the game.
 */
public interface GameEngine {

    /**
     * Initialize players and rule configuration, create initial GameState.
     */
    void initializeGame(List<Player> players, RuleConfig ruleConfig);

    /**
     * Execute shuffling, dealing cards, and marking the opening player.
     * 执行洗牌、发牌，并标记首出玩家。
     */
    void dealCards();

    /**
     * Current player plays selected cards.
     * 当前玩家出牌。
     * @param playerId The ID of the player playing cards / 出牌玩家的ID
     * @param selectedCardIds The IDs of the selected cards / 选中的卡牌ID列表
     * @return PlayResult indicating success or failure and updated state / 出牌结果
     */
    PlayResult playCards(String playerId, List<String> selectedCardIds);

    /**
     * Current player passes their turn.
     * 当前玩家过牌。
     * @param playerId The ID of the player passing / 过牌玩家的ID
     * @return PassResult indicating the result of the pass / 过牌结果
     */
    PassResult passTurn(String playerId); // 注：根据命名规范表统一使用 passTurn

    /**
     * Check if the game is over.
     * 判断当前对局是否结束。
     */
    boolean isGameOver();

    /**
     * Get the ID of the winning player if the game is over.
     * 如果游戏结束，获取获胜玩家的ID。
     */
    String getWinnerId();

    /**
     * Get the current complete game state.
     * 获取当前完整的游戏状态（仅供 Controller 使用）。
     */
    GameState getGameState();
>>>>>>> origin/dev-czh-ui-zhy
}

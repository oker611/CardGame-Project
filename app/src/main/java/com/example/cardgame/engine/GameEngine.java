package com.example.cardgame.engine;

import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PassResult;

import java.util.List;

/**
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
}

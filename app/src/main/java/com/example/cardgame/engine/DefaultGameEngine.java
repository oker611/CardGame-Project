package com.example.cardgame.engine;

import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;

import java.util.List;

/**
 * Default implementation of the GameEngine.
 */
public class DefaultGameEngine implements GameEngine {

    private GameState gameState;
    private RuleConfig ruleConfig;

    private final DealManager dealManager;
    private final TurnManager turnManager;
    private final SettlementManager settlementManager;

    public DefaultGameEngine() {
        this.dealManager = new DealManager();
        this.turnManager = new TurnManager();
        this.settlementManager = new SettlementManager();
    }

    @Override
    public void initializeGame(List<Player> players, RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
        this.gameState = new GameState();
        this.gameState.setPlayers(players);
        this.gameState.setGameOver(false);
    }

    @Override
    public void dealCards() {
        dealManager.dealCards(gameState);
    }

    @Override
    public PlayResult playCards(String playerId, List<String> selectedCardIds) {
        // TODO: M3的下一步：结合 RuleEngine 校验牌型 -> 执行扣除手牌 -> 更新 gameState -> checkGameOver -> 切换玩家
        return null;
    }

    @Override
    public PassResult passTurn(String playerId) {
        // TODO: M3的下一步：记录过牌逻辑 -> 检查是否全员过牌(resetTurnCycle) -> 切换玩家(switchPlayer)
        return null; 
    }

    @Override
    public boolean isGameOver() {
        if (gameState == null) return false;
        return settlementManager.checkGameOver(gameState);
    }

    @Override
    public String getWinnerId() {
        if (gameState == null) return null;
        return gameState.getWinnerId();
    }

    @Override
    public GameState getGameState() {
        return gameState;
    }
}

package com.example.cardgame.ai;

import android.os.Handler;
import android.os.Looper;
import com.example.cardgame.controller.GameController;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.event.*;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.PlayerType;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AIEventListener implements GameEventListener {

    private final GameController gameController;
    private final GameEngine gameEngine;
    private final AIDecisionStrategy strategy;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean isHost;

    public AIEventListener(GameController gameController, GameEngine gameEngine,
                           AIDecisionStrategy strategy, boolean isHost) {
        this.gameController = gameController;
        this.gameEngine = gameEngine;
        this.strategy = strategy;
        this.isHost = isHost;
        EventBus.getInstance().register(this);
        System.out.println("[AIEventListener] Registered isHost=" + isHost);
    }

    @Override
    public void onEvent(GameEvent event) {
        System.out.println("[AIEventListener] onEvent: " + event.getClass().getSimpleName());

        if (event instanceof TurnChangedEvent) {
            TurnChangedEvent turnEvent = (TurnChangedEvent) event;
            String newPlayerId = turnEvent.getNewCurrentPlayerId();
            System.out.println("[AIEventListener] Turn to player: " + newPlayerId);

            Player currentPlayer = gameEngine.getGameState().getPlayerById(newPlayerId);
            if (currentPlayer == null) {
                System.out.println("[AIEventListener] currentPlayer is NULL !!!");
                return;
            }

            System.out.println("[AIEventListener] Player type: " + currentPlayer.getType());

            if (currentPlayer.getType() == PlayerType.AI) {
                // 仅 HOST 端运行 AI 逻辑；CLIENT 端 AI 回合由网络消息驱动
                if (!isHost) {
                    System.out.println("[AIEventListener] AI turn skipped on non-host device for " + newPlayerId);
                    return;
                }
                handler.postDelayed(() -> {
                    try {
                        GameState gameState = gameEngine.getGameState();
                        // 再次确认当前玩家是否还是这个AI（防止回合已变）
                        Player nowPlayer = gameState.getCurrentPlayer();
                        if (nowPlayer == null || !nowPlayer.getPlayerId().equals(newPlayerId)) {
                            System.out.println("[AIEventListener] Not my turn anymore, skip");
                            return;
                        }
                        List<Card> cards = strategy.decidePlay(nowPlayer, gameState);
                        // 生成AI提示
                        String hint = generateHint(cards, nowPlayer, gameState);
                        gameController.updateAiHint(hint);
                        
                        if (cards == null || cards.isEmpty()) {
                            PassResult passResult = gameEngine.passTurn(newPlayerId);
                            System.out.println("[AIEventListener] AI passed, result=" + passResult.isSuccess());
                            if (!passResult.isSuccess()) {
                                // Pass 被拒绝（新回合起始玩家不能Pass）
                                // 强制出最小单张作为兜底
                                System.err.println("[AIEventListener] Pass rejected, force play smallest single");
                                List<Card> hand = nowPlayer.getHandCards();
                                if (hand != null && !hand.isEmpty()) {
                                    Card smallest = hand.stream()
                                            .min(Comparator.comparingInt(c ->
                                                    c.getRank().getWeight() * 10 + c.getSuit().getWeight()))
                                            .orElse(null);
                                    if (smallest != null) {
                                        gameController.aiPlayCards(Collections.singletonList(smallest));
                                    }
                                }
                            }
                        } else {
                            System.out.println("[AIEventListener] AI playing: " + cards);
                            PlayResult result = gameController.aiPlayCards(cards);
                            System.out.println("[AIEventListener] Result: " + result.isSuccess() + " - " + result.getMessage());

                            if (result.isSuccess()) {
                                // 成功出牌，重置失败计数
                                strategy.resetFailCount();
                            } else {
                                // 出牌失败，增加失败计数
                                strategy.recordPlayFailure();
                                // 尝试过牌作为兜底，避免卡死
                                System.err.println("[AIEventListener] AI play failed, falling back to pass");
                                gameEngine.passTurn(newPlayerId);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[AIEventListener] Error: " + e.getMessage());
                        e.printStackTrace();
                    }
                }, 2200);
            } else {
                System.out.println("[AIEventListener] Not AI, skipping");
            }
        } else if (event instanceof GameOverEvent) {
            handler.removeCallbacksAndMessages(null);
            System.out.println("[AIEventListener] Game over");
        }
    }

    public void unregister() {
        EventBus.getInstance().unregister(this);
        handler.removeCallbacksAndMessages(null);
        System.out.println("[AIEventListener] Unregistered");
    }
    
    /**
     * 生成AI提示文本
     */
    private String generateHint(List<Card> cards, Player player, GameState gameState) {
        if (cards == null || cards.isEmpty()) {
            return "🤖 AI选择过牌";
        }
        
        StringBuilder hint = new StringBuilder("🤖 建议出");
        
        // 格式化牌面
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) {
                hint.append(" ");
            }
            Card card = cards.get(i);
            hint.append(card.getRank().getDisplayName());
        }
        
        // 添加牌型分析
        if (cards.size() == 1) {
            hint.append("（单张）");
        } else if (cards.size() == 2) {
            hint.append("（对子）");
        } else if (cards.size() == 3) {
            hint.append("（三张）");
        } else if (cards.size() == 4) {
            hint.append("（炸弹）");
        } else if (cards.size() == 5) {
            hint.append("（五张牌）");
        }
        
        // 添加对手情况提示
        int minOpponentHandSize = getMinOpponentHandSize(gameState, player);
        if (minOpponentHandSize <= 2) {
            hint.append("，对手仅剩").append(minOpponentHandSize).append("张");
        }
        
        return hint.toString();
    }
    
    private int getMinOpponentHandSize(GameState gameState, Player currentPlayer) {
        int minSize = Integer.MAX_VALUE;
        for (Player p : gameState.getPlayers()) {
            if (!p.getPlayerId().equals(currentPlayer.getPlayerId())) {
                minSize = Math.min(minSize, p.getHandCards().size());
            }
        }
        return minSize == Integer.MAX_VALUE ? 0 : minSize;
    }
}
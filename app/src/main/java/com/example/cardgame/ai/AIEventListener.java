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
}
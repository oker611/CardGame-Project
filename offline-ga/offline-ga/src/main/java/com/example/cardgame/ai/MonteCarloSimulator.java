package com.example.cardgame.ai;

import com.example.cardgame.model.*;
import com.example.cardgame.rule.RuleEngine;
import java.util.*;

public class MonteCarloSimulator {

    private final FastGameSimulator fastSimulator;
    private final int numSamples;

    public MonteCarloSimulator(RuleEngine ruleEngine, int numSamples) {
        this.fastSimulator = new FastGameSimulator(ruleEngine);
        this.numSamples = numSamples;
    }

    public double evaluate(Play candidate, Player aiPlayer, GameState currentState,
                           List<OpponentHandSampler.World> worlds) {
        double totalScore = 0.0;
        for (OpponentHandSampler.World world : worlds) {
            GameState simState = deepCopy(currentState);
            // 替换对手手牌
            int oppIdx = 0;
            for (Player p : simState.getPlayers()) {
                if (!p.getPlayerId().equals(aiPlayer.getPlayerId())) {
                    p.setHandCards(new ArrayList<>(world.opponentHands.get(oppIdx++)));
                }
            }
            // 应用候选动作
            applyPlay(simState, candidate, aiPlayer.getPlayerId());
            // 快速模拟
            int rank = fastSimulator.simulateToEnd(simState, aiPlayer.getPlayerId());
            double score = (4 - rank); // 第一3分，第四0分
            totalScore += score;
        }
        return totalScore / worlds.size();
    }

    private void applyPlay(GameState state, Play play, String playerId) {
        Player p = state.getPlayerById(playerId);
        if (play.isEmpty()) {
            // Pass
            p.setPassed(true);
            // 简化：切换到下一家
            List<Player> players = state.getPlayers();
            int idx = players.indexOf(p);
            state.setCurrentPlayerId(players.get((idx + 1) % players.size()).getPlayerId());
        } else {
            p.getHandCards().removeAll(play.getCards());
            state.setLastPlay(play);
            state.setOpeningTurn(false);
            // 重置所有玩家 Pass 状态
            for (Player pl : state.getPlayers()) pl.setPassed(false);
            // 切换到下一家
            List<Player> players = state.getPlayers();
            int idx = players.indexOf(p);
            state.setCurrentPlayerId(players.get((idx + 1) % players.size()).getPlayerId());
        }
    }

    private GameState deepCopy(GameState original) {
        GameState copy = new GameState();
        // 复制玩家
        List<Player> copyPlayers = new ArrayList<>();
        for (Player p : original.getPlayers()) {
            Player copyP = new Player(p.getPlayerId(), p.getPlayerName());
            copyP.setHandCards(new ArrayList<>(p.getHandCards()));
            copyP.setType(p.getType());
            copyP.setPassed(p.isPassed());
            copyP.setConsecutiveNoPlayCount(p.getConsecutiveNoPlayCount());
            copyPlayers.add(copyP);
        }
        copy.setPlayers(copyPlayers);
        copy.setCurrentPlayerId(original.getCurrentPlayerId());
        copy.setOpeningTurn(original.isOpeningTurn());
        copy.setGameOver(original.isGameOver());
        if (original.getLastPlay() != null) {
            Play last = original.getLastPlay();
            Play copyLast = new Play(last.getPlayerId(), new ArrayList<>(last.getCards()), last.getPattern());
            copy.setLastPlay(copyLast);
        }
        copy.setWinnerId(original.getWinnerId());
        copy.setLastWinnerId(original.getLastWinnerId());
        copy.setConsecutivePassCount(original.getConsecutivePassCount());
        for (Map.Entry<String, List<Card>> entry : original.getLastPlayByPlayer().entrySet()) {
            copy.updateLastPlayByPlayer(entry.getKey(), entry.getValue() == null ? null : new ArrayList<>(entry.getValue()));
        }
        return copy;
    }
}
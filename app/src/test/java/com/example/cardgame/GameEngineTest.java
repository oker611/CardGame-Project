package com.example.cardgame;

import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GameEngineTest {

    @Test
    public void runFullLogFlowTest() {
        GameEngine engine = new GameEngine();

        List<Player> players = createPlayers();

        // 当前阶段如果 RuleConfig 还没实际用到，可以先传 null
        RuleConfig ruleConfig = null;

        engine.initializeGame(players, ruleConfig);
        engine.dealCards();

        printDivider("AFTER DEAL");

        GameState gameState = engine.getGameState();
        Player openingPlayer = gameState.getCurrentPlayer();

        if (openingPlayer == null) {
            throw new AssertionError("Opening player not found.");
        }

        System.out.println("[TEST] Opening player is: "
                + openingPlayer.getPlayerId()
                + " (" + openingPlayer.getPlayerName() + ")");

        // 1) 首轮必须出包含方块3的牌
        Card threeOfDiamonds = findThreeOfDiamonds(openingPlayer);
        if (threeOfDiamonds == null) {
            throw new AssertionError("3 of Diamonds not found.");
        }

        printDivider("OPENING PLAY");
        PlayResult openingPlayResult = engine.playCards(
                openingPlayer.getPlayerId(),
                Collections.singletonList(threeOfDiamonds.getCardId())
        );
        printPlayResult(openingPlayResult);

        // 2) 下一位玩家 Pass
        printDivider("PASS TURN");
        Player passPlayer = engine.getGameState().getCurrentPlayer();
        if (passPlayer == null) {
            throw new AssertionError("Pass player not found.");
        }

        PassResult passResult = engine.passTurn(passPlayer.getPlayerId());
        printPassResult(passResult);

        // 3) 再下一位玩家出一张单牌
        printDivider("NORMAL PLAY");
        Player nextPlayer = engine.getGameState().getCurrentPlayer();
        if (nextPlayer == null) {
            throw new AssertionError("Next player not found.");
        }

        Card singleCard = findAnyCard(nextPlayer);
        if (singleCard == null) {
            throw new AssertionError("No playable card found.");
        }

        PlayResult normalPlayResult = engine.playCards(
                nextPlayer.getPlayerId(),
                Collections.singletonList(singleCard.getCardId())
        );
        printPlayResult(normalPlayResult);

        // 4) 人工制造一手获胜场景
        printDivider("FORCED WIN");
        Player winnerCandidate = engine.getGameState().getCurrentPlayer();
        if (winnerCandidate == null) {
            throw new AssertionError("Winner candidate not found.");
        }

        Card winningCard = findAnyCard(winnerCandidate);
        if (winningCard == null) {
            throw new AssertionError("Winner candidate has no card.");
        }

        winnerCandidate.getHandCards().clear();
        winnerCandidate.getHandCards().add(winningCard);

        // 这一手不是新一轮首出，所以确保 openingTurn 为 false
        engine.getGameState().setOpeningTurn(false);

        PlayResult winResult = engine.playCards(
                winnerCandidate.getPlayerId(),
                Collections.singletonList(winningCard.getCardId())
        );
        printPlayResult(winResult);

        printDivider("FINAL STATE");
        GameState finalState = engine.getGameState();
        System.out.println("[TEST] Game over: " + finalState.isGameOver());
        System.out.println("[TEST] Winner ID: " + finalState.getWinnerId());

        if (!finalState.isGameOver()) {
            throw new AssertionError("Game should be over.");
        }

        if (finalState.getWinnerId() == null) {
            throw new AssertionError("Winner ID should not be null.");
        }
    }

    private List<Player> createPlayers() {
        List<Player> players = new ArrayList<>();
        players.add(new Player("P1", "Alice"));
        players.add(new Player("P2", "Bob"));
        players.add(new Player("P3", "Cindy"));
        players.add(new Player("P4", "David"));
        return players;
    }

    private Card findThreeOfDiamonds(Player player) {
        if (player == null || player.getHandCards() == null) {
            return null;
        }

        for (Card card : player.getHandCards()) {
            if (card != null && card.isThreeOfDiamonds()) {
                return card;
            }
        }
        return null;
    }

    private Card findAnyCard(Player player) {
        if (player == null || player.getHandCards() == null || player.getHandCards().isEmpty()) {
            return null;
        }
        return player.getHandCards().get(0);
    }

    private void printPlayResult(PlayResult result) {
        if (result == null) {
            System.out.println("[TEST] PlayResult = null");
            return;
        }

        System.out.println("[TEST] PlayResult.success = " + result.isSuccess());
        System.out.println("[TEST] PlayResult.message = " + result.getMessage());
    }

    private void printPassResult(PassResult result) {
        if (result == null) {
            System.out.println("[TEST] PassResult = null");
            return;
        }

        System.out.println("[TEST] PassResult.success = " + result.isSuccess());
        System.out.println("[TEST] PassResult.message = " + result.getMessage());
    }

    private void printDivider(String title) {
        System.out.println();
        System.out.println("========== " + title + " ==========");
    }
}
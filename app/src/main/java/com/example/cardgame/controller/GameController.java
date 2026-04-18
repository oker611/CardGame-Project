package com.example.cardgame.controller;

import com.example.cardgame.dto.*;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameController implements GameActionHandler {

    private GameEngine gameEngine;
    private List<String> selectedCardIds = new ArrayList<>();

    public GameController(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    @Override
    public void startNewGame() {
        // TODO: 初始化玩家和规则
        // gameEngine.initializeGame(...)
        gameEngine.dealCards();
    }

    @Override
    public PlayResult submitPlay(List<String> selectedCardIds) {
        GameState state = gameEngine.getGameState();
        String currentPlayerId = state.getCurrentPlayer().getPlayerId();

        return gameEngine.playCards(currentPlayerId, selectedCardIds);
    }

    @Override
    public PassResult passTurn() {
        GameState state = gameEngine.getGameState();
        String currentPlayerId = state.getCurrentPlayer().getPlayerId();

        return gameEngine.passTurn(currentPlayerId);
    }

    @Override
    public void toggleCardSelection(String cardId) {
        if (selectedCardIds.contains(cardId)) {
            selectedCardIds.remove(cardId);
        } else {
            selectedCardIds.add(cardId);
        }
    }

    @Override
    public GameViewData getGameViewData() {
        GameState state = gameEngine.getGameState();

        List<PlayerViewData> players = new ArrayList<>();

        for (Player p : state.getPlayers()) {
            players.add(new PlayerViewData(
                    p.getPlayerId(),
                    p.getPlayerName(),
                    p.getHandCards().size(),
                    p.equals(state.getCurrentPlayer()),
                    state.areAllOtherPlayersPassed(p.getPlayerId())
            ));
        }

        Player winner = state.getWinnerId() != null ? state.getPlayerById(state.getWinnerId()) : null;
        Player currentPlayer = state.getCurrentPlayer();

        // ✅ 获取当前玩家的手牌（将 Card 对象转换为字符串）
        List<String> myHandCards = currentPlayer.getHandCards().stream()
                .map(card -> card.getSuit().getDisplayName() + card.getRank().getDisplayName())
                .collect(Collectors.toList());

        return new GameViewData(
                currentPlayer.getPlayerId(),
                currentPlayer.getPlayerName(),
                players,
                selectedCardIds,
                myHandCards,  // ✅ 新增的第5个参数
                state.getLastPlay() == null ? "" : state.getLastPlay().toString(),
                gameEngine.isGameOver(),
                gameEngine.isGameOver() && winner != null ? winner.getPlayerName() : ""
        );
    }
}
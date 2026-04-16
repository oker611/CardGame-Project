package com.example.cardgame.controller;

import com.example.cardgame.dto.*;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;

import java.util.ArrayList;
import java.util.List;

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
        String currentPlayerId = state.getCurrentPlayer().getId();

        return gameEngine.playCards(currentPlayerId, selectedCardIds);
    }

    @Override
    public PassResult passTurn() {
        GameState state = gameEngine.getGameState();
        String currentPlayerId = state.getCurrentPlayer().getId();

        return gameEngine.pass(currentPlayerId);
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
                    p.getId(),
                    p.getName(),
                    p.getHandCards().size(),
                    p.equals(state.getCurrentPlayer()),
                    state.getPassedPlayers().contains(p.getId())
            ));
        }

        return new GameViewData(
                state.getCurrentPlayer().getId(),
                state.getCurrentPlayer().getName(),
                players,
                selectedCardIds,
                state.getLastPlay() == null ? "" : state.getLastPlay().toString(),
                gameEngine.isGameOver(),
                gameEngine.isGameOver() ? state.getWinner().getName() : ""
        );
    }
}
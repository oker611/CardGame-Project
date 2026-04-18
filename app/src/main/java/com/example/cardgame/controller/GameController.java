package com.example.cardgame.controller;

import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PlayerViewData;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Player;
import com.example.cardgame.rule.RuleConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameController implements GameActionHandler {

    private final GameEngine gameEngine;
    private final List<String> selectedCardIds = new ArrayList<>();

    public GameController(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    @Override
    public void startNewGame() {
        System.out.println("[CardGame][CONTROLLER] startNewGame called");

        List<Player> players = new ArrayList<>();
        players.add(new Player("P1", "Alice"));
        players.add(new Player("P2", "Bob"));
        players.add(new Player("P3", "Cindy"));
        players.add(new Player("P4", "David"));

        RuleConfig ruleConfig = null;

        gameEngine.initializeGame(players, ruleConfig);
        gameEngine.dealCards();

        GameState state = gameEngine.getGameState();
        if (state != null && state.getCurrentPlayer() != null) {
            System.out.println("[CardGame][CONTROLLER] startNewGame finished, currentPlayer="
                    + state.getCurrentPlayer().getPlayerId());
        }
    }

    @Override
    public PlayResult submitPlay(List<String> selectedCardIds) {
        System.out.println("[CardGame][CONTROLLER] submitPlay called, selectedCardIds=" + selectedCardIds);

        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            System.out.println("[CardGame][CONTROLLER] submitPlay failed: game state not ready");
            return new PlayResult(false, "Game state not ready.", state);
        }

        String currentPlayerId = state.getCurrentPlayer().getPlayerId();
        PlayResult result = gameEngine.playCards(currentPlayerId, selectedCardIds);

        System.out.println("[CardGame][CONTROLLER] submitPlay result="
                + (result != null ? result.getMessage() : "null"));

        return result;
    }

    @Override
    public PassResult passTurn() {
        System.out.println("[CardGame][CONTROLLER] passTurn called");

        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            System.out.println("[CardGame][CONTROLLER] passTurn failed: game state not ready");
            return new PassResult(false, "Game state not ready.", state);
        }

        String currentPlayerId = state.getCurrentPlayer().getPlayerId();
        PassResult result = gameEngine.passTurn(currentPlayerId);

        System.out.println("[CardGame][CONTROLLER] passTurn result="
                + (result != null ? result.getMessage() : "null"));

        return result;
    }

    @Override
    public void toggleCardSelection(String cardId) {
        System.out.println("[CardGame][CONTROLLER] toggleCardSelection called, cardId=" + cardId);

        if (selectedCardIds.contains(cardId)) {
            selectedCardIds.remove(cardId);
        } else {
            selectedCardIds.add(cardId);
        }

        System.out.println("[CardGame][CONTROLLER] current selectedCardIds=" + selectedCardIds);
    }

    @Override
    public GameViewData getGameViewData() {
        System.out.println("[CardGame][CONTROLLER] getGameViewData called");

        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            System.out.println("[CardGame][CONTROLLER] getGameViewData failed: game state not ready");
            return new GameViewData(
                    "",
                    "",
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    "",
                    false,
                    ""
            );
        }

        List<PlayerViewData> players = new ArrayList<>();

        for (Player p : state.getPlayers()) {
            players.add(new PlayerViewData(
                    p.getPlayerId(),
                    p.getPlayerName(),
                    p.getHandCards().size(),
                    p.equals(state.getCurrentPlayer()),
                    p.isPassed()
            ));
        }

        Player winner = state.getWinnerId() != null ? state.getPlayerById(state.getWinnerId()) : null;
        Player currentPlayer = state.getCurrentPlayer();

        List<String> myHandCards = currentPlayer.getHandCards().stream()
                .map(card -> card.getCardId())
                .collect(Collectors.toList());

        return new GameViewData(
                currentPlayer.getPlayerId(),
                currentPlayer.getPlayerName(),
                players,
                new ArrayList<>(selectedCardIds),
                myHandCards,
                state.getLastPlay() == null ? "" : state.getLastPlay().toString(),
                gameEngine.isGameOver(),
                gameEngine.isGameOver() && winner != null ? winner.getPlayerName() : ""
        );
    }
}
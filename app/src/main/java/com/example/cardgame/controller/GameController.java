package com.example.cardgame.controller;

import android.os.Handler;
import android.os.Looper;

import com.example.cardgame.ai.AIPlayer;
import com.example.cardgame.dto.GameViewData;
import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.dto.PlayerViewData;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.PlayerType;
import com.example.cardgame.rule.RuleConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameController implements GameActionHandler {

    private final GameEngine gameEngine;
    private final List<String> selectedCardIds = new ArrayList<>();

    private String myPlayerId = "P1";
    private boolean bluetoothMode = false;
    private boolean hostMode = false;
    private BluetoothActionHandler bluetoothActionHandler;

    private final Map<String, AIPlayer> aiPlayerCache = new ConcurrentHashMap<>();

    private Runnable uiRefreshCallback;

    public GameController(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    @Override
    public void setUiRefreshCallback(Runnable callback) {
        this.uiRefreshCallback = callback;
    }

    @Override
    public void setBluetoothActionHandler(BluetoothActionHandler bluetoothActionHandler) {
        this.bluetoothActionHandler = bluetoothActionHandler;
    }

    @Override
    public void setBluetoothMode(boolean bluetoothMode, boolean hostMode, String localPlayerId) {
        this.bluetoothMode = bluetoothMode;
        this.hostMode = hostMode;
        this.myPlayerId = localPlayerId != null ? localPlayerId : "P1";

        if (gameEngine.getGameState() != null) {
            gameEngine.configureBluetoothPlayerTypes(
                    this.myPlayerId,
                    "P1".equals(this.myPlayerId) ? "P2" : "P1"
            );
        }

        System.out.println("[CardGame][CONTROLLER][BLUETOOTH] mode="
                + bluetoothMode
                + ", host="
                + hostMode
                + ", localPlayerId="
                + this.myPlayerId);
    }

    private void notifyUiRefresh() {
        if (uiRefreshCallback != null) {
            uiRefreshCallback.run();
        }
    }

    @Override
    public void startNewGame() {
        System.out.println("[CardGame][CONTROLLER] startNewGame called");

        List<Player> players = new ArrayList<>();

        Player p1 = new Player("P1", "Alice");
        Player p2 = new Player("P2", "Bob");
        Player p3 = new Player("P3", "Cindy");
        Player p4 = new Player("P4", "David");

        if (bluetoothMode) {
            p1.setType("P1".equals(myPlayerId) ? PlayerType.HUMAN : PlayerType.REMOTE);
            p2.setType("P2".equals(myPlayerId) ? PlayerType.HUMAN : PlayerType.REMOTE);
            p3.setType(PlayerType.AI);
            p4.setType(PlayerType.AI);
        } else {
            p1.setType(PlayerType.HUMAN);
            p2.setType(PlayerType.AI);
            p3.setType(PlayerType.AI);
            p4.setType(PlayerType.AI);
        }

        players.add(p1);
        players.add(p2);
        players.add(p3);
        players.add(p4);

        RuleConfig ruleConfig = new RuleConfig();

        gameEngine.initializeGame(players, ruleConfig);
        gameEngine.dealCards();

        if (bluetoothMode) {
            gameEngine.configureBluetoothPlayerTypes(
                    myPlayerId,
                    "P1".equals(myPlayerId) ? "P2" : "P1"
            );
        }

        aiPlayerCache.clear();

        GameState state = gameEngine.getGameState();
        if (state != null && state.getCurrentPlayer() != null) {
            System.out.println("[CardGame][CONTROLLER] startNewGame finished, currentPlayer="
                    + state.getCurrentPlayer().getPlayerId());
        }

        if (bluetoothMode && hostMode && bluetoothActionHandler != null) {
            bluetoothActionHandler.syncGameState(gameEngine.getGameState());
            System.out.println("[CardGame][BLUETOOTH] INIT_GAME sent by host");
        }

        notifyUiRefresh();
        triggerNextAction();
    }

    @Override
    public PlayResult submitPlay(List<String> selectedCardIds) {
        System.out.println("[CardGame][CONTROLLER] submitPlay called, selectedCardIds=" + selectedCardIds);

        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            return new PlayResult(false, "Game state not ready.", state);
        }

        Player currentPlayer = state.getCurrentPlayer();

        if (!myPlayerId.equals(currentPlayer.getPlayerId()) || currentPlayer.getType() != PlayerType.HUMAN) {
            System.out.println("[CardGame][CONTROLLER] submitPlay rejected: not human or not my turn");
            return new PlayResult(false, "不是您的回合", state);
        }

        List<String> cardsToPlay = new ArrayList<>();
        Player me = state.getPlayerById(myPlayerId);
        if (me != null && this.selectedCardIds != null) {
            for (String uiCardStr : this.selectedCardIds) {
                for (Card c : me.getHandCards()) {
                    if ((c.getSuit().getSymbol() + c.getRank().getDisplayName()).equals(uiCardStr)) {
                        cardsToPlay.add(c.getCardId());
                        break;
                    }
                }
            }
        }

        if (cardsToPlay.isEmpty()) {
            System.out.println("[CardGame][CONTROLLER] No cards selected");
            return new PlayResult(false, "请先选择要出的牌", state);
        }

        PlayResult result = gameEngine.playCards(currentPlayer.getPlayerId(), cardsToPlay);
        if (result.isSuccess()) {
            this.selectedCardIds.clear();

            if (bluetoothMode && bluetoothActionHandler != null && gameEngine.getGameState() != null) {
                Play lastPlay = gameEngine.getGameState().getLastPlay();
                if (lastPlay != null) {
                    bluetoothActionHandler.sendLocalPlay(lastPlay);
                    System.out.println("[CardGame][BLUETOOTH] PLAY_ACTION sent, playerId=" + lastPlay.getPlayerId());
                }

                sendGameOverIfNeeded();
            }

            notifyUiRefresh();

            if (!gameEngine.isGameOver()) {
                new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
            }
        }

        return result;
    }

    @Override
    public PassResult passTurn() {
        System.out.println("[CardGame][CONTROLLER] passTurn called");

        GameState state = gameEngine.getGameState();
        if (state == null || state.getCurrentPlayer() == null) {
            return new PassResult(false, "Game state not ready.", state);
        }

        Player currentPlayer = state.getCurrentPlayer();
        if (!myPlayerId.equals(currentPlayer.getPlayerId()) || currentPlayer.getType() != PlayerType.HUMAN) {
            System.out.println("[CardGame][CONTROLLER] passTurn rejected: not human or not my turn");
            return new PassResult(false, "不是您的回合", state);
        }

        PassResult result = gameEngine.passTurn(currentPlayer.getPlayerId());
        if (result.isSuccess()) {
            if (bluetoothMode && bluetoothActionHandler != null) {
                bluetoothActionHandler.sendLocalPass(currentPlayer.getPlayerId());
                System.out.println("[CardGame][BLUETOOTH] PASS_ACTION sent, playerId=" + currentPlayer.getPlayerId());
            }

            notifyUiRefresh();

            if (!gameEngine.isGameOver()) {
                new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
            }
        }

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
        notifyUiRefresh();
    }

    @Override
    public GameViewData getGameViewData() {
        System.out.println("[CardGame][CONTROLLER] getGameViewData called");

        GameState state = gameEngine.getGameState();
        if (state == null) {
            System.out.println("[CardGame][CONTROLLER] getGameViewData failed: state is null");
            return emptyViewData();
        }

        Player currentPlayer = state.getCurrentPlayer();
        if (currentPlayer == null) {
            System.out.println("[CardGame][CONTROLLER] getGameViewData failed: currentPlayer is null");
            return emptyViewData();
        }

        Player me = state.getPlayerById(myPlayerId);
        if (me == null) {
            System.out.println("[CardGame][CONTROLLER] getGameViewData failed: me is null");
            return emptyViewData();
        }

        List<PlayerViewData> players = new ArrayList<>();
        for (Player p : state.getPlayers()) {
            players.add(new PlayerViewData(
                    p.getPlayerId(),
                    p.getPlayerName(),
                    p.getHandCards().size(),
                    p.equals(currentPlayer),
                    p.isPassed(),
                    p.getType() == PlayerType.HUMAN
            ));
        }

        Player winner = state.getWinnerId() != null
                ? state.getPlayerById(state.getWinnerId())
                : null;

        List<Card> handCardsList = new ArrayList<>(me.getHandCards());
        handCardsList.sort((c1, c2) -> {
            int rankCompare = Integer.compare(c2.getRank().getWeight(), c1.getRank().getWeight());
            if (rankCompare != 0) return rankCompare;
            return Integer.compare(c2.getSuit().getWeight(), c1.getSuit().getWeight());
        });

        List<String> myHandCards = handCardsList.stream()
                .map(card -> card.getSuit().getSymbol() + card.getRank().getDisplayName())
                .collect(Collectors.toList());

        Map<String, List<String>> playerLastPlayCards = new HashMap<>();
        Map<String, List<Card>> rawMap = state.getLastPlayByPlayer();

        if (rawMap != null) {
            for (Map.Entry<String, List<Card>> entry : rawMap.entrySet()) {
                String pid = entry.getKey();
                List<Card> cards = entry.getValue();

                if (cards != null && !cards.isEmpty()) {
                    List<String> cardStrs = cards.stream()
                            .map(c -> c.getSuit().getSymbol() + c.getRank().getDisplayName())
                            .collect(Collectors.toList());
                    playerLastPlayCards.put(pid, cardStrs);
                } else {
                    playerLastPlayCards.put(pid, new ArrayList<>());
                }
            }
        }

        return new GameViewData(
                me.getPlayerId(),
                me.getPlayerName(),
                players,
                new ArrayList<>(selectedCardIds),
                myHandCards,
                state.getLastPlay() == null ? "" : state.getLastPlay().toString(),
                gameEngine.isGameOver(),
                gameEngine.isGameOver() && winner != null ? winner.getPlayerName() : "",
                playerLastPlayCards
        );
    }

    private GameViewData emptyViewData() {
        return new GameViewData(
                "",
                "",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                "",
                false,
                "",
                new HashMap<>()
        );
    }

    private AIPlayer getOrCreateAIPlayer(Player player) {
        if (player.getType() != PlayerType.AI) {
            return null;
        }

        return aiPlayerCache.computeIfAbsent(player.getPlayerId(), id -> {
            AIPlayer ai = new AIPlayer(id);
            ai.setHand(player.getHandCards());
            return ai;
        });
    }

    private void autoPlayForCurrentPlayer() {
        GameState state = gameEngine.getGameState();
        if (state == null || gameEngine.isGameOver()) {
            return;
        }

        Player currentPlayer = state.getCurrentPlayer();
        if (currentPlayer == null) {
            return;
        }

        if (myPlayerId.equals(currentPlayer.getPlayerId())) {
            return;
        }

        AIPlayer aiPlayer = getOrCreateAIPlayer(currentPlayer);

        if (aiPlayer == null) {
            List<Card> randomCards = currentPlayer.getRandomCards(2);

            if (randomCards == null || randomCards.isEmpty()) {
                PassResult passResult = gameEngine.passTurn(currentPlayer.getPlayerId());
                syncAiPassIfNeeded(currentPlayer, passResult);
            } else {
                List<String> cardIds = randomCards.stream()
                        .map(Card::getCardId)
                        .collect(Collectors.toList());

                PlayResult playResult = gameEngine.playCards(currentPlayer.getPlayerId(), cardIds);
                syncAiPlayIfNeeded(playResult);
            }
        } else {
            aiPlayer.setHand(currentPlayer.getHandCards());

            List<Card> lastPlayCards = gameEngine.getLastPlayCards();
            boolean isFirstRound = gameEngine.isFirstRound();
            boolean isFirstTurn = gameEngine.isFirstTurnOfCurrentRound();

            List<Card> chosenCards = aiPlayer.choosePlay(lastPlayCards, isFirstRound, isFirstTurn);

            if (chosenCards == null || chosenCards.isEmpty()) {
                PassResult passResult = gameEngine.passTurn(currentPlayer.getPlayerId());
                syncAiPassIfNeeded(currentPlayer, passResult);
            } else {
                List<String> cardIds = chosenCards.stream()
                        .map(Card::getCardId)
                        .collect(Collectors.toList());

                PlayResult playResult = gameEngine.playCards(currentPlayer.getPlayerId(), cardIds);
                syncAiPlayIfNeeded(playResult);
            }
        }

        notifyUiRefresh();

        if (!gameEngine.isGameOver()) {
            new Handler(Looper.getMainLooper()).postDelayed(this::triggerNextAction, 100);
        }
    }

    private void syncAiPassIfNeeded(Player currentPlayer, PassResult passResult) {
        if (bluetoothMode
                && hostMode
                && bluetoothActionHandler != null
                && passResult != null
                && passResult.isSuccess()) {
            bluetoothActionHandler.sendLocalPass(currentPlayer.getPlayerId());
            System.out.println("[CardGame][BLUETOOTH] AI PASS_ACTION sent, playerId="
                    + currentPlayer.getPlayerId());
        }
    }

    private void syncAiPlayIfNeeded(PlayResult playResult) {
        if (bluetoothMode
                && hostMode
                && bluetoothActionHandler != null
                && playResult != null
                && playResult.isSuccess()) {
            Play lastPlay = gameEngine.getGameState() != null
                    ? gameEngine.getGameState().getLastPlay()
                    : null;

            if (lastPlay != null) {
                bluetoothActionHandler.sendLocalPlay(lastPlay);
                System.out.println("[CardGame][BLUETOOTH] AI PLAY_ACTION sent, playerId="
                        + lastPlay.getPlayerId());
            }

            sendGameOverIfNeeded();
        }
    }

    private void sendGameOverIfNeeded() {
        if (!bluetoothMode || bluetoothActionHandler == null) {
            return;
        }

        if (!gameEngine.isGameOver()) {
            return;
        }

        GameState state = gameEngine.getGameState();
        if (state == null || state.getWinnerId() == null) {
            return;
        }

        Player winner = state.getPlayerById(state.getWinnerId());
        String winnerName = winner != null ? winner.getPlayerName() : state.getWinnerId();

        bluetoothActionHandler.sendGameOver(state.getWinnerId(), winnerName);

        System.out.println("[CardGame][BLUETOOTH] GAME_OVER sent, winnerId="
                + state.getWinnerId());
    }

    @Override
    public void triggerNextAction() {
        if (gameEngine.isGameOver()) {
            return;
        }

        if (gameEngine.getGameState() == null) {
            return;
        }

        Player current = gameEngine.getGameState().getCurrentPlayer();
        if (current == null) {
            return;
        }

        if (bluetoothMode && !hostMode && current.getType() != PlayerType.HUMAN) {
            return;
        }

        switch (current.getType()) {
            case HUMAN:
                break;

            case AI:
                long aiThinkTime = 3000;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (gameEngine.getGameState() != null
                            && current.equals(gameEngine.getGameState().getCurrentPlayer())) {
                        autoPlayForCurrentPlayer();
                    }
                }, aiThinkTime);
                break;

            case REMOTE:
                System.out.println("[CardGame][BLUETOOTH] 等待远程玩家出牌...");
                break;
        }
    }
}
package com.example.cardgame.engine;

import com.example.cardgame.dto.PassResult;
import com.example.cardgame.dto.PlayResult;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.CardPattern;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.Player;
import com.example.cardgame.model.PlayerType;
import com.example.cardgame.rule.RuleConfig;
import com.example.cardgame.rule.RuleEngine;
import com.example.cardgame.rule.PlayValidator;
import com.example.cardgame.rule.PatternRecognizer;
import com.example.cardgame.util.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;



public class GameEngine {

    private static final boolean DEBUG_AUTO_PLAY = false;

    private GameState gameState;
    private RuleConfig ruleConfig;
    private RuleEngine ruleEngine;
    private final DealManager dealManager;
    private final TurnManager turnManager;
    private final SettlementManager settlementManager;

    public GameEngine() {
        this.dealManager = new DealManager();
        this.turnManager = new TurnManager();
        this.settlementManager = new SettlementManager();
        this.ruleEngine = new RuleEngine();
    }

    public void initializeGame(List<Player> players, RuleConfig ruleConfig) {
        this.ruleConfig = ruleConfig;
        this.gameState = new GameState();
        this.gameState.setPlayers(players);
        this.gameState.setGameOver(false);
        this.gameState.setOpeningTurn(true);
    }

    public void dealCards() {
        if (gameState != null) dealManager.dealCards(gameState);

    }

    public PlayResult playCards(String playerId, List<String> selectedCardIds) {
        System.out.println("[CardGame][PLAY] request playerId=" + playerId + ", selectedCardIds=" + selectedCardIds);

        Player player = gameState.getPlayerById(playerId);
        if (player == null || !playerId.equals(gameState.getCurrentPlayerId())) {
            System.out.println("[CardGame][PLAY] rejected: not current player, currentPlayerId="
                    + (gameState != null ? gameState.getCurrentPlayerId() : "null"));
            return createPlayResult(false, "Not your turn", gameState);
        }
        if (selectedCardIds == null || selectedCardIds.isEmpty()) {
            System.out.println("[CardGame][PLAY] rejected: selectedCardIds is empty");
            return createPlayResult(false, "Please select cards first", gameState);
        }
        List<Card> selectedCards = player.findCardsByIds(selectedCardIds);
        List<Card> lastPlayCards = getLastPlayCards();
        boolean isFirstRound = gameState.isOpeningTurn();
        boolean isFirstTurn = isFirstTurnOfCurrentRound();

        PlayValidator.ValidationResult validationResult =
                ruleEngine.validatePlay(selectedCards, lastPlayCards, isFirstRound, isFirstTurn);

        if (!validationResult.valid) {
            System.out.println("[CardGame][PLAY] rejected: " + validationResult.reason);
            return createPlayResult(false, validationResult.reason, gameState);
        }

        PatternRecognizer.PatternInfo patternInfo = ruleEngine.recognizePattern(selectedCards);
        CardPattern finalPattern = mapPatternType(patternInfo.getType());

        Play currentPlay = new Play(playerId, selectedCards, finalPattern);

        player.getHandCards().removeIf(card -> selectedCardIds.contains(card.getCardId()));

        gameState.setLastPlay(currentPlay);
        player.setPassed(false);

        // 记录上一赢家
        gameState.setLastWinnerId(playerId);

        // 重置连续 Pass 计数器（因为有人出牌了）
        gameState.resetConsecutivePassCount();


        if (gameState.isOpeningTurn()) gameState.setOpeningTurn(false);
        gameState.updateLastPlayByPlayer(playerId, selectedCards);

        System.out.println("[CardGame][PLAY] success playerId=" + playerId
                + ", cards=" + selectedCardIds
                + ", pattern=" + currentPlay.getPattern());

        settlementManager.checkAndSettle(gameState);
        if (!gameState.isGameOver()) {
            turnManager.switchPlayer(gameState);
        } else {
            Logger.win("游戏结束，获胜者: " + gameState.getWinnerId());
        }
        return createPlayResult(true, "PLAY_OK", gameState);
    }

    public PassResult passTurn(String playerId) {
        System.out.println("[CardGame][PASS] request playerId=" + playerId);

        if (gameState == null || gameState.isGameOver()) {
            System.out.println("[CardGame][PASS] rejected: game is over");
            return createPassResult(false, "Game is over", gameState);
        }

        Player player = gameState.getPlayerById(playerId);
        if (player == null || !playerId.equals(gameState.getCurrentPlayerId())) {
            System.out.println("[CardGame][PASS] rejected: not current player, currentPlayerId="
                    + gameState.getCurrentPlayerId());
            return createPassResult(false, "Not your turn", gameState);
        }
        if (gameState.isOpeningTurn() || gameState.getLastPlay() == null || gameState.getLastPlay().isEmpty()) {
            System.out.println("[CardGame][PASS] rejected: new round starter must play cards");
            return createPassResult(false, "New round starter cannot pass", gameState);
        }


        // 标记当前玩家为 pass，并清除其个人出牌记录
        player.setPassed(true);
        gameState.updateLastPlayByPlayer(playerId, null);
        gameState.incrementConsecutivePassCount();   // 连续 Pass 计数加1

        // 检查是否连续三人 Pass（计数器 >= 3）
        if (gameState.getConsecutivePassCount() >= 3) {
            // 获取上一轮赢家
            String winnerId = gameState.getLastWinnerId();

            // 清空桌面、所有玩家 Pass 状态、所有出牌记录
            gameState.setLastPlay(null);
            gameState.clearAllPassStatus();
            gameState.clearAllLastPlayRecords();
            gameState.resetConsecutivePassCount();   // 重置计数器

            // 设置下一轮出牌的玩家为上一赢家
            if (winnerId != null && !gameState.isOpeningTurn()) {
                gameState.setCurrentPlayerId(winnerId);
                System.out.println("[CardGame][PASS] 连续三人Pass，清空桌面，新回合玩家（上赢家）: " + winnerId);
            } else {
                // 降级：按顺序切换（基本不会触发）
                turnManager.switchPlayer(gameState);
                System.out.println("[CardGame][PASS] 降级：按顺序切换玩家");
            }

            System.out.println("[CardGame][PASS] 桌面已完全清空，新回合开始");
        } else {
            // 正常 Pass，切换到下一个玩家
            turnManager.switchPlayer(gameState);
        }

        System.out.println("[CardGame][PASS] success playerId=" + playerId);
        return createPassResult(true, "PASS_OK", gameState);
    }

    private PlayResult createPlayResult(boolean success, String msg, GameState state) {
        return new PlayResult(success, msg, state);
    }

    private PassResult createPassResult(boolean success, String msg, GameState state) {
        return new PassResult(success, msg, state);
    }

    public boolean isGameOver() {
        return gameState != null && gameState.isGameOver();
    }

    public String getWinnerId() {
        return gameState != null ? gameState.getWinnerId() : null;
    }

    public GameState getGameState() {
        return gameState;
    }

    public List<Card> getLastPlayCards() {
        if (gameState == null || gameState.getLastPlay() == null) return null;
        return gameState.getLastPlay().getCards();
    }

    public boolean isFirstRound() {
        return gameState != null && gameState.isOpeningTurn();
    }

    public boolean isFirstTurnOfCurrentRound() {
        if (gameState == null) return true;
        List<Card> lastPlayCards = getLastPlayCards();
        return lastPlayCards == null || lastPlayCards.isEmpty();
    }

    public String getCurrentPlayerId() {
        return gameState != null ? gameState.getCurrentPlayerId() : null;
    }

    public List<Card> getPlayerHand(String playerId) {
        Player player = gameState != null ? gameState.getPlayerById(playerId) : null;
        return player != null ? player.getHandCards() : null;
    }

    private boolean containsThreeOfDiamonds(List<Card> cards) {
        for (Card c : cards) if (c.isThreeOfDiamonds()) return true;
        return false;
    }

    private boolean sameRank(List<Card> cards) {
        Card first = cards.get(0);
        for (Card c : cards) if (c.getRank() != first.getRank()) return false;
        return true;
    }

    private String formatCards(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cards.size(); i++) {
            sb.append(cards.get(i).getDisplayText());
            if (i < cards.size() - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Rebuild the full game state from bluetooth sync data.
     * This version is used when the sender directly sends a complete GameState.
     */
    public void rebuildGameState(GameState syncedState) {
        if (syncedState == null) {
            System.out.println("[CardGame][BLUETOOTH] rebuildGameState failed: syncedState is null");
            return;
        }

        this.gameState = syncedState;

        System.out.println("[CardGame][BLUETOOTH] GameState rebuilt from remote sync, currentPlayerId="
                + gameState.getCurrentPlayerId());
    }

    /**
     * Rebuild a minimal bluetooth game state from two hands.
     * This is kept for compatibility with InitGamePayload localHandCards / remoteHandCards.
     */
    public void rebuildGameState(List<Card> myHand, List<Card> opponentHand, String currentPlayerId) {
        GameState rebuiltState = new GameState();

        List<Player> players = new ArrayList<>();

        Player localPlayer = new Player("P1", "Me");
        localPlayer.setType(PlayerType.HUMAN);
        localPlayer.setHandCards(myHand);

        Player remotePlayer = new Player("P2", "Remote");
        remotePlayer.setType(PlayerType.REMOTE);
        remotePlayer.setHandCards(opponentHand);

        players.add(localPlayer);
        players.add(remotePlayer);

        rebuiltState.setPlayers(players);
        rebuiltState.setCurrentPlayerId(currentPlayerId != null ? currentPlayerId : "P1");
        rebuiltState.setOpeningTurn(false);
        rebuiltState.setGameOver(false);

        this.gameState = rebuiltState;

        System.out.println("[CardGame][BLUETOOTH] GameState rebuilt from hand cards, currentPlayerId="
                + rebuiltState.getCurrentPlayerId());
    }

    /**
     * Execute a play action received from bluetooth.
     */
    public PlayResult executeRemotePlay(Play play) {
        if (play == null) {
            System.out.println("[CardGame][BLUETOOTH] executeRemotePlay failed: play is null");
            return createPlayResult(false, "Remote play is null", gameState);
        }

        if (play.getCards() == null || play.getCards().isEmpty()) {
            System.out.println("[CardGame][BLUETOOTH] executeRemotePlay failed: cards are empty");
            return createPlayResult(false, "Remote play cards are empty", gameState);
        }

        List<String> cardIds = new ArrayList<>();
        for (Card card : play.getCards()) {
            if (card != null && card.getCardId() != null) {
                cardIds.add(card.getCardId());
            }
        }

        System.out.println("[CardGame][BLUETOOTH] executeRemotePlay playerId="
                + play.getPlayerId()
                + ", cardIds="
                + cardIds);

        return playCards(play.getPlayerId(), cardIds);
    }

    /**
     * Execute a pass action received from bluetooth.
     */
    public PassResult executeRemotePass(String playerId) {
        System.out.println("[CardGame][BLUETOOTH] executeRemotePass playerId=" + playerId);
        return passTurn(playerId);
    }

    public void configureBluetoothPlayerTypes(String localPlayerId, String remotePlayerId) {
        if (gameState == null || gameState.getPlayers() == null) {
            return;
        }

        for (Player player : gameState.getPlayers()) {
            if (player == null) {
                continue;
            }

            if (player.getPlayerId().equals(localPlayerId)) {
                player.setType(PlayerType.HUMAN);
            } else if (player.getPlayerId().equals(remotePlayerId)) {
                player.setType(PlayerType.REMOTE);
            } else {
                player.setType(PlayerType.AI);
            }
        }

        System.out.println("[CardGame][BLUETOOTH] Player types configured | local="
                + localPlayerId + ", remote=" + remotePlayerId);
    }

    /**
     * Multi-player version: rebuild game state from a map of playerId→handCards.
     */
    public void rebuildGameStateMulti(Map<String, List<Card>> handsByPlayerId, String currentPlayerId) {
        GameState rebuiltState = new GameState();

        List<Player> players = new ArrayList<>();

        for (Map.Entry<String, List<Card>> entry : handsByPlayerId.entrySet()) {
            String playerId = entry.getKey();
            List<Card> hand = entry.getValue();

            Player player = new Player(playerId, "Player " + playerId);
            player.setHandCards(hand != null ? new ArrayList<>(hand) : new ArrayList<>());
            player.setType(PlayerType.AI); // 默认AI，后续由 configureBluetoothPlayerTypes 修正
            players.add(player);
        }

        rebuiltState.setPlayers(players);
        rebuiltState.setCurrentPlayerId(currentPlayerId != null ? currentPlayerId : "P1");
        rebuiltState.setOpeningTurn(false);
        rebuiltState.setGameOver(false);

        this.gameState = rebuiltState;

        System.out.println("[CardGame][BLUETOOTH] GameState rebuilt (multi), playerCount="
                + players.size() + ", currentPlayerId=" + rebuiltState.getCurrentPlayerId());
    }

    /**
     * Multi-player version: configure player types from a type map.
     * Map key=playerId, value=typeName ("HUMAN", "REMOTE", "AI").
     */
    public void configureBluetoothPlayerTypesMulti(Map<String, String> typeMap) {
        if (gameState == null || gameState.getPlayers() == null || typeMap == null) {
            return;
        }

        for (Player player : gameState.getPlayers()) {
            if (player == null) {
                continue;
            }

            String typeName = typeMap.get(player.getPlayerId());
            if (typeName != null) {
                try {
                    player.setType(PlayerType.valueOf(typeName));
                } catch (IllegalArgumentException e) {
                    player.setType(PlayerType.AI);
                }
            } else {
                player.setType(PlayerType.AI);
            }
        }

        System.out.println("[CardGame][BLUETOOTH] Player types configured (multi) | typeMap=" + typeMap);
    }

    private CardPattern mapPatternType(PatternRecognizer.PatternType type) {
        if (type == null) return CardPattern.INVALID;
        switch (type) {
            case SINGLE:
                return CardPattern.SINGLE;
            case PAIR:
                return CardPattern.PAIR;
            case TRIPLE:
                return CardPattern.TRIPLE;
            case QUADRUPLE:
                return CardPattern.QUADRUPLE;
            case STRAIGHT:
                return CardPattern.STRAIGHT;
            case FLUSH:
                return CardPattern.FLUSH;
            case FULL_HOUSE:
                return CardPattern.FULL_HOUSE;
            case IRON_BRANCH:
                return CardPattern.IRON_BRANCH;
            case STRAIGHT_FLUSH:
                return CardPattern.STRAIGHT_FLUSH;
            default:
                return CardPattern.INVALID;
        }
    }

    private CardPattern guessPattern(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return CardPattern.INVALID;
        int size = cards.size();
        if (size == 1) return CardPattern.SINGLE;
        if (size == 2 && sameRank(cards)) return CardPattern.PAIR;
        if (size == 3 && sameRank(cards)) return CardPattern.TRIPLE;
        if (size == 4 && sameRank(cards)) return CardPattern.QUADRUPLE;
        return CardPattern.INVALID;
    }
}

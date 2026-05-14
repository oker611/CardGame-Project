package com.example.cardgame.network;

import android.util.Log;

import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.Card;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.PlayerType;
import com.example.cardgame.network.payload.ErrorPayload;
import com.example.cardgame.network.payload.GameOverPayload;
import com.example.cardgame.network.payload.InitGamePayload;
import com.example.cardgame.network.payload.JoinPayload;
import com.example.cardgame.network.payload.PassActionPayload;
import com.example.cardgame.network.payload.PlayActionPayload;
import com.example.cardgame.network.payload.PlayerLeftPayload;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkGameBridge {

    private final GameEngine gameEngine;
    private final BluetoothMessageCodec messageCodec;
    private BluetoothEventListener eventListener;
    private String localPlayerId;
    private List<String> remotePlayerIds = new ArrayList<>();

    public NetworkGameBridge(GameEngine gameEngine, BluetoothMessageCodec messageCodec) {
        this.gameEngine = gameEngine;
        this.messageCodec = messageCodec;
    }

    public void setBluetoothEventListener(BluetoothEventListener eventListener) {
        this.eventListener = eventListener;
    }

    /**
     * 设置玩家上下文（支持多远程玩家）。
     */
    public void setPlayerContext(String localPlayerId, List<String> remotePlayerIds) {
        this.localPlayerId = localPlayerId;
        this.remotePlayerIds = remotePlayerIds != null ? new ArrayList<>(remotePlayerIds) : new ArrayList<>();
    }

    public void handleMessage(BluetoothMessage message) {
        if (message == null || message.getMessageType() == null) {
            notifyError("Invalid bluetooth message", null);
            return;
        }

        switch (message.getMessageType()) {
            case INIT_GAME:
                handleInitGame(message);
                break;

            case PLAY_ACTION:
                handlePlayAction(message);
                break;

            case PASS_ACTION:
                handlePassAction(message);
                break;

            case GAME_OVER:
                handleGameOver(message);
                break;

            case ERROR:
                handleErrorMessage(message);
                break;

            case HEARTBEAT:
                Log.d("CardGame", "[DEBUG] [蓝牙] [接收] 心跳消息 | 发送者:" + message.getSenderPlayerId());
                break;

            default:
                Log.d("CardGame", "[DEBUG] [蓝牙] [接收] 消息未由Bridge处理 | 类型:" + message.getMessageType());
                break;
        }
    }

    private void handleInitGame(BluetoothMessage message) {
        try {
            InitGamePayload payload =
                    messageCodec.decodeInitGamePayload(message.getPayloadJson());

            if (payload.getGameState() != null) {
                // 完整 GameState 同步（HOST 发牌后广播）
                GameState syncedState = payload.getGameState();

                invokeEngineMethod(
                        "rebuildGameState",
                        new Class[]{GameState.class},
                        syncedState
                );

                configurePlayerTypes();

                notifyReceived(MessageType.INIT_GAME, "完整GameState已同步");
                return;
            }

            // N-player 手牌同步（新格式）
            Map<String, List<Card>> playerHandCards = payload.getPlayerHandCards();
            if (playerHandCards != null && !playerHandCards.isEmpty()) {
                invokeEngineMethod(
                        "rebuildGameStateMulti",
                        new Class[]{Map.class, String.class},
                        playerHandCards,
                        payload.getCurrentPlayerId()
                );

                configurePlayerTypes();

                notifyReceived(MessageType.INIT_GAME, "多人手牌已同步");
                return;
            }

            // 兼容旧2-player格式
            List<Card> myHand = payload.getRemoteHandCards();
            List<Card> opponentHand = payload.getLocalHandCards();
            String currentPlayerId = payload.getCurrentPlayerId();

            invokeEngineMethod(
                    "rebuildGameState",
                    new Class[]{List.class, List.class, String.class},
                    myHand,
                    opponentHand,
                    currentPlayerId
            );

            configurePlayerTypes();

            notifyReceived(MessageType.INIT_GAME, "开局手牌已同步");
        } catch (Exception exception) {
            notifyError("Failed to handle INIT_GAME", exception);
        }
    }

    private void handlePlayAction(BluetoothMessage message) {
        try {
            PlayActionPayload payload =
                    messageCodec.decodePlayActionPayload(message.getPayloadJson());

            Play play = payload.getPlay();

            if (play != null) {
                invokeEngineMethod(
                        "executeRemotePlay",
                        new Class[]{Play.class},
                        play
                );
            } else {
                invokeEngineMethod(
                        "playCards",
                        new Class[]{String.class, List.class},
                        payload.getPlayerId(),
                        payload.getSelectedCardIds()
                );
            }

            notifyReceived(MessageType.PLAY_ACTION, "收到远程出牌:" + payload.getPlayerId());
        } catch (Exception exception) {
            notifyError("Failed to handle PLAY_ACTION", exception);
        }
    }

    private void handlePassAction(BluetoothMessage message) {
        try {
            PassActionPayload payload =
                    messageCodec.decodePassActionPayload(message.getPayloadJson());

            boolean executed = invokeEngineMethod(
                    "executeRemotePass",
                    new Class[]{String.class},
                    payload.getPlayerId()
            );

            if (!executed) {
                invokeEngineMethod(
                        "passTurn",
                        new Class[]{String.class},
                        payload.getPlayerId()
                );
            }

            notifyReceived(MessageType.PASS_ACTION, "收到远程Pass:" + payload.getPlayerId());
        } catch (Exception exception) {
            notifyError("Failed to handle PASS_ACTION", exception);
        }
    }

    private void handleGameOver(BluetoothMessage message) {
        try {
            GameOverPayload payload =
                    messageCodec.decodeGameOverPayload(message.getPayloadJson());

            notifyReceived(MessageType.GAME_OVER, "游戏结束，胜者:" + payload.getWinnerId());

            if (eventListener != null) {
                eventListener.onGameOver(payload.getWinnerId(), payload.getWinnerName());
            }
        } catch (Exception exception) {
            notifyError("Failed to handle GAME_OVER", exception);
        }
    }

    private void handleErrorMessage(BluetoothMessage message) {
        try {
            ErrorPayload payload =
                    messageCodec.decodeErrorPayload(message.getPayloadJson());

            notifyError(payload.getErrorMessage(), null);
        } catch (Exception exception) {
            notifyError("Failed to handle ERROR message", exception);
        }
    }

    /**
     * 配置 GameEngine 中的玩家类型：本机=HUMAN，远程=REMOTE，其余=AI。
     */
    private void configurePlayerTypes() {
        try {
            Map<String, String> typeMap = new HashMap<>();
            typeMap.put(localPlayerId, PlayerType.HUMAN.name());

            for (String remoteId : remotePlayerIds) {
                typeMap.put(remoteId, PlayerType.REMOTE.name());
            }

            invokeEngineMethod(
                    "configureBluetoothPlayerTypesMulti",
                    new Class[]{Map.class},
                    typeMap
            );

            Log.d("CardGame", "[DEBUG] [蓝牙] PlayerTypes配置 | local=" + localPlayerId
                    + ", remote=" + remotePlayerIds);
        } catch (Exception e) {
            Log.w("CardGame", "[WARN] [蓝牙] configureBluetoothPlayerTypesMulti 不可用", e);
        }
    }

    private boolean invokeEngineMethod(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = gameEngine.getClass().getMethod(methodName, parameterTypes);
            method.invoke(gameEngine, args);
            return true;
        } catch (NoSuchMethodException exception) {
            Log.w("CardGame", "[WARN] [蓝牙] 引擎接口未暴露 | 方法:" + methodName);
            return false;
        } catch (Exception exception) {
            notifyError("Failed to invoke GameEngine method: " + methodName, exception);
            return false;
        }
    }

    private void notifyReceived(MessageType messageType, String summary) {
        Log.d("CardGame", "[DEBUG] [蓝牙] [接收] 消息处理完成 | 类型:" + messageType + " 内容:" + summary);

        if (eventListener != null) {
            eventListener.onMessageReceived(messageType, summary);
        }
    }

    private void notifyError(String message, Exception exception) {
        Log.e("CardGame", "[ERROR] [蓝牙] 消息处理异常 | 原因:" + message, exception);

        if (eventListener != null) {
            eventListener.onError(message, exception);
        }
    }
}

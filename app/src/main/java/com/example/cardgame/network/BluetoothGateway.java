package com.example.cardgame.network;

import android.content.Context;
import android.util.Log;

import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.network.payload.GameOverPayload;
import com.example.cardgame.network.payload.InitGamePayload;
import com.example.cardgame.network.payload.PassActionPayload;
import com.example.cardgame.network.payload.PlayActionPayload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BluetoothGateway implements MultiplayerGateway, BluetoothMessageListener {

    private final BluetoothConnectionManager connectionManager;
    private final BluetoothMessageCodec messageCodec;
    private final NetworkGameBridge networkGameBridge;

    private BluetoothSender sender;
    private BluetoothReceiver receiver;
    private BluetoothEventListener eventListener;

    private String localPlayerId;
    private String remotePlayerId;
    private String role;

    private volatile boolean communicationReady = false;

    public BluetoothGateway(Context context, GameEngine gameEngine) {
        this.connectionManager = new BluetoothConnectionManager(context);
        this.messageCodec = new BluetoothMessageCodec();
        this.networkGameBridge = new NetworkGameBridge(gameEngine, messageCodec);
        this.role = "NONE";
    }

    public void setBluetoothEventListener(BluetoothEventListener eventListener) {
        this.eventListener = eventListener;
        this.networkGameBridge.setBluetoothEventListener(eventListener);
    }

    public void startAsHost(String localPlayerId) {
        this.localPlayerId = localPlayerId;
        this.remotePlayerId = "P2";
        this.role = "HOST";
        this.communicationReady = false;

        networkGameBridge.setPlayerContext(this.localPlayerId, this.remotePlayerId);

        try {
            Log.i("CardGame", "[INFO] [蓝牙] 创建房间 | 本机玩家:" + localPlayerId);

            connectionManager.acceptConnectionAsServer();
            setupCommunicationChannel();

            notifyConnected();
            sendHeartbeat();

            Log.i("CardGame", "[INFO] [蓝牙] 房主通道已就绪 | 本机:" + this.localPlayerId
                    + " 对方:" + this.remotePlayerId);
        } catch (Exception exception) {
            handleConnectionError("创建蓝牙房间失败", exception);
        }
    }

    public void connectAsClient(String localPlayerId, String deviceAddress) {
        this.localPlayerId = localPlayerId;
        this.remotePlayerId = "P1";
        this.role = "CLIENT";
        this.communicationReady = false;

        networkGameBridge.setPlayerContext(this.localPlayerId, this.remotePlayerId);

        try {
            Log.i("CardGame", "[INFO] [蓝牙] 发起连接 | 目标设备:" + deviceAddress);

            connectionManager.connectToDevice(deviceAddress);
            setupCommunicationChannel();

            notifyConnected();
            sendHeartbeat();

            Log.i("CardGame", "[INFO] [蓝牙] 客户端通道已就绪 | 本机:" + this.localPlayerId
                    + " 对方:" + this.remotePlayerId);
        } catch (Exception exception) {
            handleConnectionError("连接蓝牙设备失败", exception);
        }
    }

    public List<BluetoothDeviceInfo> searchDevices() {
        if (!connectionManager.isBluetoothAvailable()) {
            notifyError("当前设备不支持蓝牙", null);
            return new ArrayList<>();
        }

        if (!connectionManager.isBluetoothEnabled()) {
            notifyError("蓝牙未开启", null);
            return new ArrayList<>();
        }

        connectionManager.startDiscovery();

        List<BluetoothDeviceInfo> devices = connectionManager.getBondedDevices();

        Log.i("CardGame", "[INFO] [蓝牙] 搜索设备完成 | 数量:" + devices.size());

        return devices;
    }

    private void setupCommunicationChannel() throws IOException {
        if (connectionManager.getOutputStream() == null || connectionManager.getInputStream() == null) {
            throw new IOException("Bluetooth stream is null");
        }

        sender = new BluetoothSender(
                connectionManager.getOutputStream(),
                messageCodec
        );

        receiver = new BluetoothReceiver(
                connectionManager.getInputStream(),
                messageCodec,
                this
        );

        receiver.startListening();
        communicationReady = true;

        Log.i("CardGame", "[INFO] [蓝牙] 通信通道建立 | 角色:" + role
                + " 本机:" + localPlayerId
                + " 对方:" + remotePlayerId);
    }

    @Override
    public void sendPlayAction(Play play) {
        if (play == null) {
            Log.w("CardGame", "[WARN] [蓝牙] [发送] 出牌消息为空 | play:null");
            return;
        }

        PlayActionPayload payload = new PlayActionPayload(
                play.getPlayerId(),
                new ArrayList<>(),
                play
        );

        BluetoothMessage message = messageCodec.buildPlayActionMessage(
                localPlayerId,
                remotePlayerId,
                payload
        );

        sendBluetoothMessage(message, "本地玩家出牌:" + play.getPlayerId());
    }

    @Override
    public void sendPassAction(String playerId) {
        if (playerId == null || playerId.trim().isEmpty()) {
            Log.w("CardGame", "[WARN] [蓝牙] [发送] Pass玩家为空 | playerId:null");
            return;
        }

        PassActionPayload payload = new PassActionPayload(playerId);

        BluetoothMessage message = messageCodec.buildPassActionMessage(
                localPlayerId,
                remotePlayerId,
                payload
        );

        sendBluetoothMessage(message, "本地玩家Pass:" + playerId);
    }

    @Override
    public void syncGameState(GameState gameState) {
        if (gameState == null) {
            Log.w("CardGame", "[WARN] [蓝牙] [发送] 同步状态为空 | gameState:null");
            return;
        }

        InitGamePayload payload = new InitGamePayload(
                gameState.getCurrentPlayerId(),
                localPlayerId,
                remotePlayerId,
                null,
                null,
                gameState
        );

        BluetoothMessage message = messageCodec.buildInitGameMessage(
                localPlayerId,
                remotePlayerId,
                payload
        );

        sendBluetoothMessage(message, "同步游戏状态 currentPlayer:" + gameState.getCurrentPlayerId());
    }

    public void sendInitGamePayload(InitGamePayload payload) {
        if (payload == null) {
            Log.w("CardGame", "[WARN] [蓝牙] [发送] 开局Payload为空 | payload:null");
            return;
        }

        BluetoothMessage message = messageCodec.buildInitGameMessage(
                localPlayerId,
                remotePlayerId,
                payload
        );

        sendBluetoothMessage(message, "开局同步");
    }

    public void sendGameOver(String winnerId, String winnerName) {
        if (winnerId == null || winnerId.trim().isEmpty()) {
            Log.w("CardGame", "[WARN] [蓝牙] [发送] 游戏结束胜者为空 | winnerId:null");
            return;
        }

        GameOverPayload payload = new GameOverPayload(winnerId, winnerName);

        BluetoothMessage message = messageCodec.buildGameOverMessage(
                localPlayerId,
                remotePlayerId,
                payload
        );

        sendBluetoothMessage(message, "游戏结束:" + winnerId);
    }

    private void sendHeartbeat() {
        BluetoothMessage message = messageCodec.buildHeartbeatMessage(
                localPlayerId,
                remotePlayerId
        );

        sendBluetoothMessage(message, "心跳检测");
    }

    private void sendBluetoothMessage(BluetoothMessage message, String summary) {
        try {
            if (!isReadyToSend()) {
                throw new IOException("Bluetooth channel is not ready");
            }

            sender.sendMessage(message);

            Log.d(
                    "CardGame",
                    "[DEBUG] [蓝牙] [发送] 消息发送 | 类型:"
                            + message.getMessageType()
                            + " 内容:"
                            + summary
            );

            if (eventListener != null) {
                eventListener.onMessageSent(message.getMessageType(), summary);
            }
        } catch (Exception exception) {
            handleConnectionError("发送蓝牙消息失败: " + summary, exception);
        }
    }

    private boolean isReadyToSend() {
        return communicationReady
                && sender != null
                && sender.isActive()
                && connectionManager.isConnected();
    }

    @Override
    public void onMessageReceived(BluetoothMessage message) {
        if (message == null) {
            Log.w("CardGame", "[WARN] [蓝牙] [接收] 空消息 | message:null");
            return;
        }

        Log.d(
                "CardGame",
                "[DEBUG] [蓝牙] [接收] 消息接收 | 类型:"
                        + message.getMessageType()
                        + " 发送者:"
                        + message.getSenderPlayerId()
        );

        if (eventListener != null) {
            eventListener.onMessageReceived(
                    message.getMessageType(),
                    "来自:" + message.getSenderPlayerId()
            );
        }

        networkGameBridge.handleMessage(message);
    }

    @Override
    public void onReceiveError(Exception exception) {
        handleConnectionError("蓝牙接收数据失败", exception);
    }

    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    public String getRole() {
        return role;
    }

    public void disconnect() {
        communicationReady = false;

        if (receiver != null) {
            receiver.stopListening();
            receiver = null;
        }

        if (sender != null) {
            sender.stop();
            sender = null;
        }

        connectionManager.close();

        Log.e("CardGame", "[ERROR] [蓝牙] 连接断开 | 原因:用户主动断开");

        if (eventListener != null) {
            eventListener.onDisconnected("用户主动断开");
        }
    }

    private void notifyConnected() {
        Log.i(
                "CardGame",
                "[INFO] [蓝牙] 连接成功 | 对方设备:"
                        + connectionManager.getConnectedDeviceName()
        );

        if (eventListener != null) {
            eventListener.onConnected(
                    connectionManager.getConnectedDeviceName(),
                    connectionManager.getConnectedDeviceAddress()
            );
        }
    }

    private void notifyError(String message, Exception exception) {
        Log.e("CardGame", "[ERROR] [蓝牙] 异常 | 原因:" + message, exception);

        if (eventListener != null) {
            eventListener.onError(message, exception);
        }
    }

    private void handleConnectionError(String message, Exception exception) {
        communicationReady = false;

        Log.e("CardGame", "[ERROR] [蓝牙] 连接断开 | 原因:" + message, exception);

        if (eventListener != null) {
            eventListener.onDisconnected(message);
            eventListener.onError(message, exception);
        }
    }
}
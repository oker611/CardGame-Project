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
        this.remotePlayerId = "player_2";
        this.role = "HOST";

        try {
            Log.i("CardGame", "[INFO] [蓝牙] 创建房间 | 本机玩家:" + localPlayerId);

            connectionManager.acceptConnectionAsServer();
            setupCommunicationChannel();

            notifyConnected();

            sendHeartbeat();
        } catch (Exception exception) {
            handleConnectionError("创建蓝牙房间失败", exception);
        }
    }

    public void connectAsClient(String localPlayerId, String deviceAddress) {
        this.localPlayerId = localPlayerId;
        this.remotePlayerId = "player_1";
        this.role = "CLIENT";

        try {
            Log.i("CardGame", "[INFO] [蓝牙] 发起连接 | 目标设备:" + deviceAddress);

            connectionManager.connectToDevice(deviceAddress);
            setupCommunicationChannel();

            notifyConnected();

            sendHeartbeat();
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
    }

    @Override
    public void sendPlayAction(Play play) {
        PlayActionPayload payload = new PlayActionPayload(
                localPlayerId,
                new ArrayList<>(),
                play
        );

        BluetoothMessage message = messageCodec.buildPlayActionMessage(
                localPlayerId,
                remotePlayerId,
                payload
        );

        sendBluetoothMessage(message, "本地玩家出牌:" + localPlayerId);
    }

    @Override
    public void sendPassAction(String playerId) {
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
        InitGamePayload payload = new InitGamePayload(
                null,
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

        sendBluetoothMessage(message, "同步游戏状态");
    }

    public void sendInitGamePayload(InitGamePayload payload) {
        BluetoothMessage message = messageCodec.buildInitGameMessage(
                localPlayerId,
                remotePlayerId,
                payload
        );

        sendBluetoothMessage(message, "开局同步");
    }

    public void sendGameOver(String winnerId, String winnerName) {
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
            if (sender == null || !sender.isActive()) {
                throw new IOException("Bluetooth sender is not ready");
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
            handleConnectionError("发送蓝牙消息失败", exception);
        }
    }

    @Override
    public void onMessageReceived(BluetoothMessage message) {
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
        if (receiver != null) {
            receiver.stopListening();
        }

        if (sender != null) {
            sender.stop();
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
        Log.e("CardGame", "[ERROR] [蓝牙] 连接断开 | 原因:" + message, exception);

        if (eventListener != null) {
            eventListener.onDisconnected(message);
            eventListener.onError(message, exception);
        }
    }
}
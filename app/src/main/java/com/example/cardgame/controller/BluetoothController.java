package com.example.cardgame.controller;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import com.example.cardgame.dto.BluetoothDeviceViewData;
import com.example.cardgame.dto.BluetoothViewData;
import com.example.cardgame.engine.GameEngine;
import com.example.cardgame.model.GameState;
import com.example.cardgame.model.Play;
import com.example.cardgame.network.BluetoothDeviceInfo;
import com.example.cardgame.network.BluetoothEventListener;
import com.example.cardgame.network.BluetoothGateway;
import com.example.cardgame.network.MessageType;

import java.util.ArrayList;
import java.util.List;

public class BluetoothController implements BluetoothActionHandler, BluetoothEventListener {

    private final BluetoothGateway bluetoothGateway;
    private final BluetoothViewData bluetoothViewData;

    public BluetoothController(Context context, GameEngine gameEngine) {
        this.bluetoothViewData = new BluetoothViewData();
        updateBluetoothStatus();
        this.bluetoothGateway = new BluetoothGateway(context, gameEngine);
        this.bluetoothGateway.setBluetoothEventListener(this);
    }

    private void updateBluetoothStatus() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothViewData.setBluetoothAvailable(adapter != null);
        bluetoothViewData.setBluetoothEnabled(adapter != null && adapter.isEnabled());
    }

    @Override
    public void createBluetoothRoom(String localPlayerId) {
        updateBluetoothStatus();

        bluetoothViewData.clearErrorMessage();
        bluetoothViewData.setLocalPlayerId(localPlayerId);
        bluetoothViewData.setRole("HOST");
        bluetoothViewData.setHosting(true);
        bluetoothViewData.setConnecting(false);
        bluetoothViewData.clearConnectedDevices();
        bluetoothViewData.setStatusText("正在创建蓝牙房间（4人模式）");

        // HOST 自己占用 slot 0
        bluetoothViewData.addConnectedDevice("房主 / 本机", "", "P1", 0);

        new Thread(() -> bluetoothGateway.startAsHost(localPlayerId)).start();
    }

    @Override
    public void searchBluetoothDevices() {
        updateBluetoothStatus();

        bluetoothViewData.clearErrorMessage();
        bluetoothViewData.setDevices(new ArrayList<>());
        bluetoothViewData.setStatusText("正在搜索可加入的手机或平板");

        new Thread(() -> {
            List<BluetoothDeviceInfo> devices = bluetoothGateway.searchDevices();
            List<BluetoothDeviceViewData> viewDevices = new ArrayList<>();

            for (BluetoothDeviceInfo device : devices) {
                if (!device.isJoinableCandidate()) {
                    continue;
                }

                viewDevices.add(new BluetoothDeviceViewData(
                        device.getDeviceName(),
                        device.getDeviceAddress(),
                        device.isBonded(),
                        false,
                        device.isBonded() ? "已配对，可尝试加入" : "新发现，可尝试加入"
                ));
            }

            bluetoothViewData.setDevices(viewDevices);
            bluetoothViewData.setStatusText("搜索完成，可加入候选设备：" + viewDevices.size());
        }).start();
    }

    @Override
    public void connectToDevice(String localPlayerId, String deviceAddress) {
        updateBluetoothStatus();

        bluetoothViewData.clearErrorMessage();
        bluetoothViewData.setLocalPlayerId(localPlayerId);
        bluetoothViewData.setRole("CLIENT");
        bluetoothViewData.setConnecting(true);
        bluetoothViewData.setConnected(false);
        bluetoothViewData.setStatusText("正在连接对方设备");

        new Thread(() -> bluetoothGateway.connectAsClient(localPlayerId, deviceAddress)).start();
    }

    @Override
    public void disconnectBluetooth() {
        bluetoothGateway.disconnect();

        bluetoothViewData.setConnected(false);
        bluetoothViewData.setConnecting(false);
        bluetoothViewData.setHosting(false);
        bluetoothViewData.clearConnectedDevices();
        bluetoothViewData.setConnectedDeviceName(null);
        bluetoothViewData.setConnectedDeviceAddress(null);
        bluetoothViewData.setStatusText("蓝牙连接已断开");
    }

    @Override
    public void sendLocalPlay(Play play) {
        bluetoothGateway.sendPlayAction(play);
    }

    @Override
    public void sendLocalPass(String playerId) {
        bluetoothGateway.sendPassAction(playerId);
    }

    @Override
    public void syncGameState(GameState gameState) {
        bluetoothGateway.syncGameState(gameState);
    }

    @Override
    public void sendGameOver(String winnerId, String winnerName) {
        bluetoothGateway.sendGameOver(winnerId, winnerName);
    }

    @Override
    public BluetoothViewData getBluetoothViewData() {
        updateBluetoothStatus();
        return bluetoothViewData;
    }

    @Override
    public List<String> getRemotePlayerIds() {
        return bluetoothGateway.getRemotePlayerIds();
    }

    // ========================================================================
    //  BluetoothEventListener 回调
    // ========================================================================

    @Override
    public void onConnected(String deviceName, String deviceAddress) {
        bluetoothViewData.clearErrorMessage();
        bluetoothViewData.setConnected(true);
        bluetoothViewData.setConnecting(false);
        bluetoothViewData.setHosting(false);
        bluetoothViewData.setConnectedDeviceName(deviceName);
        bluetoothViewData.setConnectedDeviceAddress(deviceAddress);
        bluetoothViewData.setStatusText("蓝牙连接成功");
    }

    @Override
    public void onServerReady() {
        bluetoothViewData.clearErrorMessage();
        bluetoothViewData.setConnected(false);
        bluetoothViewData.setConnecting(false);
        bluetoothViewData.setHosting(true);
        bluetoothViewData.setStatusText("房间已创建，等待加入");
    }

    @Override
    public void onPlayerAssigned(String playerId, int slotIndex) {
        // CLIENT 端：HOST 分配了角色
        bluetoothViewData.setAssignedPlayerId(playerId);
        bluetoothViewData.setAssignedSlotIndex(slotIndex);
        bluetoothViewData.setLocalPlayerId(playerId);
        bluetoothViewData.setRemotePlayerId("P1"); // HOST 始终是 P1
        bluetoothViewData.setStatusText("已加入房间，身份：" + playerId);
    }

    @Override
    public void onPlayerJoined(String playerId, String playerName, int slotIndex) {
        // 使用 playerId 去重，避免重复添加同一玩家
        bluetoothViewData.removeConnectedDeviceByPlayerId(playerId);
        bluetoothViewData.addConnectedDevice(
                playerName != null ? playerName : "Player " + playerId,
                playerId,  // 使用 playerId 作为地址标识
                playerId,
                slotIndex
        );

        String role = bluetoothViewData.getRole();
        String base = "HOST".equals(role) ? "玩家已加入" : "新玩家加入";
        bluetoothViewData.setStatusText(base + "：" + playerId + " (" + playerName + ")");
    }

    @Override
    public void onPlayerLeft(String playerId, String playerName) {
        bluetoothViewData.removeConnectedDeviceByPlayerId(playerId);
        bluetoothViewData.setStatusText("玩家已离开：" + playerId + " (" + playerName + ")");
    }

    @Override
    public void onAllPlayersReady() {
        bluetoothViewData.setStatusText("4人到齐，可以开始游戏");
    }

    @Override
    public void onDisconnected(String reason) {
        bluetoothViewData.setConnected(false);
        bluetoothViewData.setConnecting(false);
        bluetoothViewData.setHosting(false);
        bluetoothViewData.setStatusText("蓝牙连接断开");
        bluetoothViewData.setErrorMessage(reason);
    }

    @Override
    public void onMessageSent(MessageType messageType, String summary) {
        if (messageType != null) {
            bluetoothViewData.setLastSentMessageType(messageType.name());
        }
        bluetoothViewData.setLastSentSummary(summary);
    }

    @Override
    public void onMessageReceived(MessageType messageType, String summary) {
        if (messageType != null) {
            bluetoothViewData.setLastReceivedMessageType(messageType.name());
        }
        bluetoothViewData.setLastReceivedSummary(summary);
    }

    @Override
    public void onError(String message, Exception exception) {
        bluetoothViewData.setErrorMessage(message);
        bluetoothViewData.setStatusText("蓝牙异常：" + message);
    }

    @Override
    public void onGameOver(String winnerId, String winnerName) {
        bluetoothViewData.setStatusText("游戏结束，胜者：" + winnerName);
    }
}

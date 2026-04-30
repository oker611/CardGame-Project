package com.example.cardgame.controller;

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
    private BluetoothViewData bluetoothViewData;

    public BluetoothController(Context context, GameEngine gameEngine) {
        this.bluetoothViewData = new BluetoothViewData();
        this.bluetoothGateway = new BluetoothGateway(context, gameEngine);
        this.bluetoothGateway.setBluetoothEventListener(this);
    }

    @Override
    public void createBluetoothRoom(String localPlayerId) {
        bluetoothViewData.setLocalPlayerId(localPlayerId);
        bluetoothViewData.setRole("HOST");
        bluetoothViewData.setHosting(true);
        bluetoothViewData.setStatusText("正在创建蓝牙房间");

        new Thread(() -> bluetoothGateway.startAsHost(localPlayerId)).start();
    }

    @Override
    public void searchBluetoothDevices() {
        bluetoothViewData.setStatusText("正在搜索附近蓝牙设备");

        new Thread(() -> {
            List<BluetoothDeviceInfo> devices = bluetoothGateway.searchDevices();
            List<BluetoothDeviceViewData> viewDevices = new ArrayList<>();

            for (BluetoothDeviceInfo device : devices) {
                viewDevices.add(new BluetoothDeviceViewData(
                        device.getDeviceName(),
                        device.getDeviceAddress(),
                        device.isBonded(),
                        false,
                        "可连接"
                ));
            }

            bluetoothViewData.setDevices(viewDevices);
            bluetoothViewData.setStatusText("搜索完成");
        }).start();
    }

    @Override
    public void connectToDevice(String localPlayerId, String deviceAddress) {
        bluetoothViewData.setLocalPlayerId(localPlayerId);
        bluetoothViewData.setRole("CLIENT");
        bluetoothViewData.setConnecting(true);
        bluetoothViewData.setStatusText("正在连接对方设备");

        new Thread(() -> bluetoothGateway.connectAsClient(localPlayerId, deviceAddress)).start();
    }

    @Override
    public void disconnectBluetooth() {
        bluetoothGateway.disconnect();
        bluetoothViewData.setConnected(false);
        bluetoothViewData.setConnecting(false);
        bluetoothViewData.setHosting(false);
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
    public BluetoothViewData getBluetoothViewData() {
        return bluetoothViewData;
    }

    @Override
    public void onConnected(String deviceName, String deviceAddress) {
        bluetoothViewData.setConnected(true);
        bluetoothViewData.setConnecting(false);
        bluetoothViewData.setConnectedDeviceName(deviceName);
        bluetoothViewData.setConnectedDeviceAddress(deviceAddress);
        bluetoothViewData.setStatusText("蓝牙连接成功");
    }

    @Override
    public void onDisconnected(String reason) {
        bluetoothViewData.setConnected(false);
        bluetoothViewData.setConnecting(false);
        bluetoothViewData.setStatusText("蓝牙连接断开");
        bluetoothViewData.setErrorMessage(reason);
    }

    @Override
    public void onMessageSent(MessageType messageType, String summary) {
        bluetoothViewData.setLastSentMessageType(messageType.name());
        bluetoothViewData.setLastSentSummary(summary);
    }

    @Override
    public void onMessageReceived(MessageType messageType, String summary) {
        bluetoothViewData.setLastReceivedMessageType(messageType.name());
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
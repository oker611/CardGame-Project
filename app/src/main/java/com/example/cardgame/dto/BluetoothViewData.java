package com.example.cardgame.dto;

import java.util.ArrayList;
import java.util.List;

public class BluetoothViewData {

    private boolean bluetoothEnabled;
    private boolean connected;
    private boolean connecting;
    private boolean hosting;

    private String role;
    private String localPlayerId;
    private String remotePlayerId;

    private String connectedDeviceName;
    private String connectedDeviceAddress;

    private String statusText;
    private String errorMessage;

    private String lastSentMessageType;
    private String lastSentSummary;
    private String lastReceivedMessageType;
    private String lastReceivedSummary;

    private List<BluetoothDeviceViewData> devices;

    public BluetoothViewData() {
        this.bluetoothEnabled = false;
        this.connected = false;
        this.connecting = false;
        this.hosting = false;
        this.role = "NONE";
        this.statusText = "未连接";
        this.devices = new ArrayList<>();
    }

    public boolean isBluetoothEnabled() {
        return bluetoothEnabled;
    }

    public void setBluetoothEnabled(boolean bluetoothEnabled) {
        this.bluetoothEnabled = bluetoothEnabled;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isConnecting() {
        return connecting;
    }

    public void setConnecting(boolean connecting) {
        this.connecting = connecting;
    }

    public boolean isHosting() {
        return hosting;
    }

    public void setHosting(boolean hosting) {
        this.hosting = hosting;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getLocalPlayerId() {
        return localPlayerId;
    }

    public void setLocalPlayerId(String localPlayerId) {
        this.localPlayerId = localPlayerId;
    }

    public String getRemotePlayerId() {
        return remotePlayerId;
    }

    public void setRemotePlayerId(String remotePlayerId) {
        this.remotePlayerId = remotePlayerId;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    public void setConnectedDeviceName(String connectedDeviceName) {
        this.connectedDeviceName = connectedDeviceName;
    }

    public String getConnectedDeviceAddress() {
        return connectedDeviceAddress;
    }

    public void setConnectedDeviceAddress(String connectedDeviceAddress) {
        this.connectedDeviceAddress = connectedDeviceAddress;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getLastSentMessageType() {
        return lastSentMessageType;
    }

    public void setLastSentMessageType(String lastSentMessageType) {
        this.lastSentMessageType = lastSentMessageType;
    }

    public String getLastSentSummary() {
        return lastSentSummary;
    }

    public void setLastSentSummary(String lastSentSummary) {
        this.lastSentSummary = lastSentSummary;
    }

    public String getLastReceivedMessageType() {
        return lastReceivedMessageType;
    }

    public void setLastReceivedMessageType(String lastReceivedMessageType) {
        this.lastReceivedMessageType = lastReceivedMessageType;
    }

    public String getLastReceivedSummary() {
        return lastReceivedSummary;
    }

    public void setLastReceivedSummary(String lastReceivedSummary) {
        this.lastReceivedSummary = lastReceivedSummary;
    }

    public List<BluetoothDeviceViewData> getDevices() {
        return devices;
    }

    public void setDevices(List<BluetoothDeviceViewData> devices) {
        this.devices = devices;
    }
}
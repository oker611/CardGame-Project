package com.example.cardgame.dto;

import java.util.ArrayList;
import java.util.List;

public class BluetoothViewData {

    private boolean bluetoothAvailable;
    private boolean bluetoothEnabled;
    private boolean connected;
    private boolean connecting;
    private boolean hosting;

    private String role;
    private String localPlayerId;

    // 旧单连接字段（保留兼容）
    private String remotePlayerId;
    private String connectedDeviceName;
    private String connectedDeviceAddress;

    // ——— 多连接支持 ———
    private final List<ConnectedDevice> connectedDevices = new ArrayList<>();
    /** 客户端模式：HOST 分配的 playerId */
    private String assignedPlayerId;
    /** 客户端模式：HOST 分配的 slot 索引 (0-3) */
    private int assignedSlotIndex = -1;

    private String statusText;
    private String errorMessage;
    private String permissionStatus;

    private String lastSentMessageType;
    private String lastSentSummary;
    private String lastReceivedMessageType;
    private String lastReceivedSummary;

    private List<BluetoothDeviceViewData> devices;

    public BluetoothViewData() {
        this.bluetoothAvailable = false;
        this.bluetoothEnabled = false;
        this.connected = false;
        this.connecting = false;
        this.hosting = false;
        this.role = "NONE";
        this.statusText = "未连接";
        this.errorMessage = "";
        this.permissionStatus = "";
        this.devices = new ArrayList<>();
    }

    // ===== 旧 getter / setter =====

    public boolean isBluetoothAvailable() {
        return bluetoothAvailable;
    }

    public void setBluetoothAvailable(boolean bluetoothAvailable) {
        this.bluetoothAvailable = bluetoothAvailable;
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

    public String getPermissionStatus() {
        return permissionStatus;
    }

    public void setPermissionStatus(String permissionStatus) {
        this.permissionStatus = permissionStatus;
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
        this.devices = devices != null ? devices : new ArrayList<>();
    }

    public void clearErrorMessage() {
        this.errorMessage = "";
    }

    // ===== 多连接 getter / setter =====

    public List<ConnectedDevice> getConnectedDevices() {
        return connectedDevices;
    }

    public int getConnectedDeviceCount() {
        return connectedDevices.size();
    }

    public void addConnectedDevice(String deviceName, String deviceAddress, String playerId, int slotIndex) {
        // 先移除同地址的旧条目
        removeConnectedDeviceByAddress(deviceAddress);
        connectedDevices.add(new ConnectedDevice(deviceName, deviceAddress, playerId, slotIndex));

        // 同步旧字段
        if (connectedDevices.size() == 1) {
            this.connectedDeviceName = deviceName;
            this.connectedDeviceAddress = deviceAddress;
            this.remotePlayerId = playerId;
            this.connected = true;
        }
    }

    public void removeConnectedDeviceByAddress(String deviceAddress) {
        connectedDevices.removeIf(d -> java.util.Objects.equals(d.deviceAddress, deviceAddress));
        if (connectedDevices.isEmpty()) {
            this.connected = false;
            this.connectedDeviceName = null;
            this.connectedDeviceAddress = null;
            this.remotePlayerId = null;
        }
    }

    public void removeConnectedDeviceByPlayerId(String playerId) {
        connectedDevices.removeIf(d -> java.util.Objects.equals(d.playerId, playerId));
        if (connectedDevices.isEmpty()) {
            this.connected = false;
            this.connectedDeviceName = null;
            this.connectedDeviceAddress = null;
            this.remotePlayerId = null;
        }
    }

    public void clearConnectedDevices() {
        connectedDevices.clear();
        this.connected = false;
        this.connectedDeviceName = null;
        this.connectedDeviceAddress = null;
        this.remotePlayerId = null;
    }

    public String getAssignedPlayerId() {
        return assignedPlayerId;
    }

    public void setAssignedPlayerId(String assignedPlayerId) {
        this.assignedPlayerId = assignedPlayerId;
    }

    public int getAssignedSlotIndex() {
        return assignedSlotIndex;
    }

    public void setAssignedSlotIndex(int assignedSlotIndex) {
        this.assignedSlotIndex = assignedSlotIndex;
    }

    // ========================================================================
    //  内部类：已连接设备
    // ========================================================================

    public static class ConnectedDevice {
        public final String deviceName;
        public final String deviceAddress;
        public final String playerId;
        public final int slotIndex;

        public ConnectedDevice(String deviceName, String deviceAddress, String playerId, int slotIndex) {
            this.deviceName = deviceName;
            this.deviceAddress = deviceAddress;
            this.playerId = playerId;
            this.slotIndex = slotIndex;
        }
    }
}

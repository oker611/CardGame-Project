package com.example.cardgame.dto;

public class BluetoothDeviceViewData {

    private String deviceName;
    private String deviceAddress;
    private boolean bonded;
    private boolean selected;
    private String connectionStatus;

    public BluetoothDeviceViewData(
            String deviceName,
            String deviceAddress,
            boolean bonded,
            boolean selected,
            String connectionStatus
    ) {
        this.deviceName = deviceName;
        this.deviceAddress = deviceAddress;
        this.bonded = bonded;
        this.selected = selected;
        this.connectionStatus = connectionStatus;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public boolean isBonded() {
        return bonded;
    }

    public boolean isSelected() {
        return selected;
    }

    public String getConnectionStatus() {
        return connectionStatus;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setConnectionStatus(String connectionStatus) {
        this.connectionStatus = connectionStatus;
    }
}
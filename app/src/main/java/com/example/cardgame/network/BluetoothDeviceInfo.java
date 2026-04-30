package com.example.cardgame.network;

public class BluetoothDeviceInfo {

    private String deviceName;
    private String deviceAddress;
    private boolean bonded;

    public BluetoothDeviceInfo(String deviceName, String deviceAddress, boolean bonded) {
        this.deviceName = deviceName;
        this.deviceAddress = deviceAddress;
        this.bonded = bonded;
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
}
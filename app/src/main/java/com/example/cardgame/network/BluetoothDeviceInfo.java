package com.example.cardgame.network;

public class BluetoothDeviceInfo {

    private String deviceName;
    private String deviceAddress;
    private boolean bonded;
    private int deviceType;      // 0: 手机/平板, 1: 主机/可加入设备, 2: 其他
    private int signalStrength;  // 0: 弱, 1: 中, 2: 强
    private boolean joinableCandidate;

    public BluetoothDeviceInfo(String deviceName, String deviceAddress, boolean bonded) {
        this(deviceName, deviceAddress, bonded, 0, 1, true);
    }

    public BluetoothDeviceInfo(String deviceName,
                               String deviceAddress,
                               boolean bonded,
                               int deviceType,
                               int signalStrength,
                               boolean joinableCandidate) {
        this.deviceName = deviceName;
        this.deviceAddress = deviceAddress;
        this.bonded = bonded;
        this.deviceType = deviceType;
        this.signalStrength = signalStrength;
        this.joinableCandidate = joinableCandidate;
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

    public int getDeviceType() {
        return deviceType;
    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public boolean isJoinableCandidate() {
        return joinableCandidate;
    }
}
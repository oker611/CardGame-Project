package com.example.cardgame.model;

public class DeviceInfo {
    private String deviceName;
    private String address;
    private int deviceType; // 0: 手机, 1: 主机, 2: 其他
    private boolean paired;
    private int signalStrength; // 0: 弱, 1: 中, 2: 强

    public DeviceInfo(String deviceName, String address, int deviceType, boolean paired, int signalStrength) {
        this.deviceName = deviceName;
        this.address = address;
        this.deviceType = deviceType;
        this.paired = paired;
        this.signalStrength = signalStrength;
    }

    // getters and setters
    public String getDeviceName() { return deviceName; }
    public String getAddress() { return address; }
    public int getDeviceType() { return deviceType; }
    public boolean isPaired() { return paired; }
    public int getSignalStrength() { return signalStrength; }
}
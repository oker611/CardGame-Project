package com.example.cardgame.model;

public class RemotePlayerInfo {

    private String playerId;
    private String playerName;
    private String deviceName;
    private String deviceAddress;
    private PlayerType playerType;
    private boolean connected;

    public RemotePlayerInfo(
            String playerId,
            String playerName,
            String deviceName,
            String deviceAddress
    ) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.deviceName = deviceName;
        this.deviceAddress = deviceAddress;
        this.playerType = PlayerType.REMOTE;
        this.connected = false;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public PlayerType getPlayerType() {
        return playerType;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}
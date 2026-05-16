package com.example.cardgame.network.payload;

public class JoinPayload {

    private String playerName;
    private String assignedPlayerId;
    private int slotIndex;

    public JoinPayload() {
    }

    /**
     * Client → Host: join request.
     */
    public JoinPayload(String playerName) {
        this.playerName = playerName;
        this.assignedPlayerId = null;
        this.slotIndex = -1;
    }

    /**
     * Host → Client: join acknowledgement with assigned slot.
     */
    public JoinPayload(String playerName, String assignedPlayerId, int slotIndex) {
        this.playerName = playerName;
        this.assignedPlayerId = assignedPlayerId;
        this.slotIndex = slotIndex;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getAssignedPlayerId() {
        return assignedPlayerId;
    }

    public void setAssignedPlayerId(String assignedPlayerId) {
        this.assignedPlayerId = assignedPlayerId;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }
}

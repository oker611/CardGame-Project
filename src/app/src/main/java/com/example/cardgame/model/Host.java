package com.example.cardgame.model;

public class Host {
    private int host_id;
    private int room_id;

    public void bindRoom(int roomId) {
        this.room_id = roomId;
    }

    public int getHostRoom() {
        return this.room_id;
    }
}

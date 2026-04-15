package com.example.cardgame.model;

import java.sql.Timestamp;

public class Room {
    private int room_id;
    private String status;
    private long created_at;  // timestamp 使用 long 存储毫秒值

    public void createRoom() {
        this.status = "WAITING";
        this.created_at = System.currentTimeMillis();
    }

    public void updateStatus(String status) {
        this.status = status;
    }

    public String getRoomInfo() {
        return "RoomID: " + room_id + ", Status: " + status;
    }

    public int getRoom_id() { return room_id; }
    public void setRoom_id(int room_id) { this.room_id = room_id; }
}

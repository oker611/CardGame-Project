package com.example.cardgame.network;

import org.junit.Test;
import org.junit.Before;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

public class RoomControllerTest {

    private RoomController controller;
    private ConcurrentHashMap<String, String> deviceToPlayerId;
    private ConcurrentHashMap<String, String> playerIdToDevice;
    private ConcurrentHashMap<String, String> playerNamesById;
    private ConcurrentHashMap<String, String> pendingJoinNames;

    @Before
    public void setUp() {
        deviceToPlayerId = new ConcurrentHashMap<>();
        playerIdToDevice = new ConcurrentHashMap<>();
        playerNamesById = new ConcurrentHashMap<>();
        pendingJoinNames = new ConcurrentHashMap<>();
        controller = new RoomController(
                deviceToPlayerId, playerIdToDevice, playerNamesById, pendingJoinNames);
    }

    @Test
    public void assignClientSlot_setsMappings() {
        controller.assignClientSlot("AA:BB:CC", 0);
        assertEquals("P2", deviceToPlayerId.get("AA:BB:CC"));
        assertEquals("AA:BB:CC", playerIdToDevice.get("P2"));
    }

    @Test
    public void clientPlayerIdForSlot() {
        assertEquals("P2", controller.clientPlayerIdForSlot(0));
        assertEquals("P3", controller.clientPlayerIdForSlot(1));
        assertEquals("P4", controller.clientPlayerIdForSlot(2));
    }

    @Test
    public void registerPlayerName() {
        controller.registerPlayerName("P1", "Alice");
        assertEquals("Alice", controller.getPlayerName("P1"));
    }

    @Test
    public void safePlayerName_trims() {
        assertEquals("Alice", RoomController.safePlayerName("  Alice  ", "fallback"));
    }

    @Test
    public void safePlayerName_nullUsesFallback() {
        assertEquals("fallback", RoomController.safePlayerName(null, "fallback"));
    }

    @Test
    public void clear_removesAll() {
        controller.assignClientSlot("AA:BB", 0);
        controller.registerPlayerName("P1", "Alice");
        controller.clear();
        assertNull(deviceToPlayerId.get("AA:BB"));
        assertEquals("P1", controller.getPlayerName("P1")); // names not in device maps
    }
}

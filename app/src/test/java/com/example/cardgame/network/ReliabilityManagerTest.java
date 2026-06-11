package com.example.cardgame.network;

import org.junit.Test;
import org.junit.Before;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

public class ReliabilityManagerTest {

    private ReliabilityManager rm;
    private ConcurrentHashMap<String, Long> lastHb;
    private ConcurrentHashMap<String, BluetoothGateway.PendingMessage> pending;

    @Before
    public void setUp() {
        lastHb = new ConcurrentHashMap<>();
        pending = new ConcurrentHashMap<>();
        rm = new ReliabilityManager(lastHb, pending);
    }

    @Test
    public void recordHeartbeat_storesTimestamp() {
        rm.recordHeartbeat("AA:BB");
        assertNotNull(lastHb.get("AA:BB"));
        assertTrue(lastHb.get("AA:BB") > 0);
    }

    @Test
    public void removeChannel_clearsHeartbeat() {
        rm.recordHeartbeat("AA:BB");
        rm.removeChannel("AA:BB");
        assertNull(lastHb.get("AA:BB"));
    }

    @Test
    public void needsAck_trueForGameMessages() {
        assertTrue(rm.needsAck(MessageType.INIT_GAME));
        assertTrue(rm.needsAck(MessageType.PLAY_ACTION));
        assertTrue(rm.needsAck(MessageType.PASS_ACTION));
        assertTrue(rm.needsAck(MessageType.GAME_OVER));
    }

    @Test
    public void needsAck_falseForHeartbeat() {
        assertFalse(rm.needsAck(MessageType.HEARTBEAT));
    }

    @Test
    public void trackPending_storesMessage() {
        BluetoothMessage msg = new BluetoothMessage("id1", 1, MessageType.PLAY_ACTION,
                "P1", "ALL", System.currentTimeMillis(), "{}", null, 0);
        rm.trackPending("AA:BB", msg);
        assertNotNull(pending.get("AA:BB"));
    }

    @Test
    public void trackPending_skipsNonAckable() {
        BluetoothMessage msg = new BluetoothMessage("id1", 1, MessageType.HEARTBEAT,
                "P1", "ALL", System.currentTimeMillis(), "", null, 0);
        rm.trackPending("AA:BB", msg);
        assertNull(pending.get("AA:BB"));
    }

    @Test
    public void ackReceived_removesPending() {
        BluetoothMessage msg = new BluetoothMessage("id1", 1, MessageType.PLAY_ACTION,
                "P1", "ALL", System.currentTimeMillis(), "{}", null, 0);
        rm.trackPending("AA:BB", msg);
        rm.ackReceived("AA:BB");
        assertNull(pending.get("AA:BB"));
    }

    @Test
    public void getPending_returnsCorrectMessage() {
        BluetoothMessage msg = new BluetoothMessage("id1", 1, MessageType.GAME_OVER,
                "P1", "ALL", System.currentTimeMillis(), "{}", null, 0);
        rm.trackPending("AA:BB", msg);
        assertNotNull(rm.getPending("AA:BB"));
    }

    @Test
    public void stop_clearsState() {
        rm.recordHeartbeat("AA:BB");
        rm.stop();
        assertTrue(lastHb.isEmpty());
    }
}

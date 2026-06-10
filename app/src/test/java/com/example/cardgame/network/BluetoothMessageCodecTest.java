package com.example.cardgame.network;

import com.example.cardgame.model.Card;
import com.example.cardgame.model.Rank;
import com.example.cardgame.model.Suit;
import com.example.cardgame.model.Play;
import com.example.cardgame.model.CardPattern;
import com.example.cardgame.network.payload.PlayActionPayload;
import com.example.cardgame.network.payload.PassActionPayload;
import com.example.cardgame.network.payload.GameOverPayload;
import com.example.cardgame.network.payload.JoinPayload;
import com.example.cardgame.network.payload.AckPayload;

import org.junit.Test;
import org.junit.Before;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class BluetoothMessageCodecTest {

    private BluetoothMessageCodec codec;

    @Before
    public void setUp() {
        codec = new BluetoothMessageCodec();
    }

    // ========== encode / decode round-trip ==========

    @Test
    public void encodeDecode_roundTrip() {
        BluetoothMessage original = codec.buildHeartbeatMessage("P1", "P2");
        String json = codec.encode(original);
        BluetoothMessage decoded = codec.decode(json);

        assertEquals(original.getMessageId(), decoded.getMessageId());
        assertEquals(original.getMessageType(), decoded.getMessageType());
        assertEquals(original.getSenderPlayerId(), decoded.getSenderPlayerId());
    }

    // ========== PLAY_ACTION payload ==========

    @Test
    public void playActionPayload_roundTrip() {
        Card card = new Card("SA", Suit.SPADES, Rank.ACE);
        Play play = new Play("P1", Collections.singletonList(card), CardPattern.SINGLE);
        PlayActionPayload payload = new PlayActionPayload("P1", Collections.emptyList(), play);

        BluetoothMessage msg = codec.buildPlayActionMessage("P1", "ALL", payload);
        BluetoothMessage decoded = codec.decode(codec.encode(msg));
        PlayActionPayload decodedPayload = codec.decodePlayActionPayload(decoded.getPayloadJson());

        assertNotNull(decodedPayload.getPlay());
        assertEquals("P1", decodedPayload.getPlay().getPlayerId());
        assertEquals(CardPattern.SINGLE, decodedPayload.getPlay().getPattern());
        assertEquals(1, decodedPayload.getPlay().getCards().size());
    }

    // ========== PASS_ACTION payload ==========

    @Test
    public void passActionPayload_roundTrip() {
        PassActionPayload payload = new PassActionPayload("P2");
        BluetoothMessage msg = codec.buildPassActionMessage("P2", "ALL", payload);

        BluetoothMessage decoded = codec.decode(codec.encode(msg));
        PassActionPayload decodedPayload = codec.decodePassActionPayload(decoded.getPayloadJson());

        assertEquals("P2", decodedPayload.getPlayerId());
    }

    // ========== GAME_OVER payload ==========

    @Test
    public void gameOverPayload_roundTrip() {
        GameOverPayload payload = new GameOverPayload("P1", "Alice");
        BluetoothMessage msg = codec.buildGameOverMessage("P1", "ALL", payload);

        BluetoothMessage decoded = codec.decode(codec.encode(msg));
        GameOverPayload decodedPayload = codec.decodeGameOverPayload(decoded.getPayloadJson());

        assertEquals("P1", decodedPayload.getWinnerId());
        assertEquals("Alice", decodedPayload.getWinnerName());
    }

    // ========== ACK payload ==========

    @Test
    public void ackPayload_roundTrip() {
        AckPayload payload = new AckPayload("msg-uuid-123");
        BluetoothMessage msg = codec.buildAckMessage("P2", "P1", payload);

        BluetoothMessage decoded = codec.decode(codec.encode(msg));
        AckPayload decodedPayload = codec.decodeAckPayload(decoded.getPayloadJson());

        assertEquals("msg-uuid-123", decodedPayload.getAcknowledgedMessageId());
    }

    // ========== message metadata ==========

    @Test
    public void messageHasProtocolVersion() {
        BluetoothMessage msg = codec.buildHeartbeatMessage("P1", "P2");
        BluetoothMessage decoded = codec.decode(codec.encode(msg));
        assertEquals(1, decoded.getProtocolVersion());
    }

    @Test
    public void messageHasTimestamp() {
        BluetoothMessage msg = codec.buildHeartbeatMessage("P1", "P2");
        BluetoothMessage decoded = codec.decode(codec.encode(msg));
        assertTrue(decoded.getTimestamp() > 0);
    }

    // ========== null handling ==========

    @Test
    public void decode_nullJson() {
        BluetoothMessage result = codec.decode(null);
        assertNull(result);
    }

    @Test
    public void decodePlayAction_nullJson() {
        PlayActionPayload result = codec.decodePlayActionPayload(null);
        assertNull(result);
    }
}

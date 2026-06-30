package com.meshpay.relay1.controller;

import com.meshpay.common.dto.Packet;
import com.meshpay.common.dto.PacketStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class RelayControllerTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private RelayController relayController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(relayController, "nextNodeUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(relayController, "restTemplate", restTemplate);
    }

    @Test
    public void testForwardPacketSuccess() {
        // Arrange
        Packet packet = Packet.builder()
                .packetId("pkt-123")
                .transactionId(UUID.randomUUID())
                .ttl(LocalDateTime.now().plusMinutes(10))
                .hopCount(0)
                .maxHopCount(5)
                .routeHistory(new ArrayList<>())
                .packetStatus(PacketStatus.RECEIVED)
                .build();

        Packet ackPacket = Packet.builder()
                .packetStatus(PacketStatus.FORWARDED)
                .build();

        when(restTemplate.postForEntity(anyString(), any(Packet.class), eq(Packet.class)))
                .thenReturn(new ResponseEntity<>(ackPacket, HttpStatus.OK));

        // Act
        ResponseEntity<Packet> response = relayController.receivePacket(packet);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PacketStatus.FORWARDED, response.getBody().getPacketStatus());
        assertEquals(1, packet.getHopCount());
        assertTrue(packet.getRouteHistory().contains("Relay-1"));
        assertEquals("Relay-1", packet.getCurrentNode());
        assertEquals("Relay-2", packet.getNextNode());

        verify(restTemplate, times(1)).postForEntity(anyString(), any(Packet.class), eq(Packet.class));
    }

    @Test
    public void testForwardPacketTtlExpired() {
        // Arrange
        Packet packet = Packet.builder()
                .packetId("pkt-123")
                .transactionId(UUID.randomUUID())
                .ttl(LocalDateTime.now().minusMinutes(1)) // Expired
                .hopCount(0)
                .maxHopCount(5)
                .routeHistory(new ArrayList<>())
                .packetStatus(PacketStatus.RECEIVED)
                .build();

        // Act
        ResponseEntity<Packet> response = relayController.receivePacket(packet);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PacketStatus.DROPPED, response.getBody().getPacketStatus());

        verify(restTemplate, never()).postForEntity(anyString(), any(Packet.class), eq(Packet.class));
    }

    @Test
    public void testForwardPacketRetryFailure() {
        // Arrange
        Packet packet = Packet.builder()
                .packetId("pkt-123")
                .transactionId(UUID.randomUUID())
                .ttl(LocalDateTime.now().plusMinutes(10))
                .hopCount(0)
                .maxHopCount(5)
                .routeHistory(new ArrayList<>())
                .packetStatus(PacketStatus.RECEIVED)
                .build();

        // Mock failure on RestTemplate calls
        when(restTemplate.postForEntity(anyString(), any(Packet.class), eq(Packet.class)))
                .thenThrow(new RuntimeException("Connection Timeout"));

        // Act
        ResponseEntity<Packet> response = relayController.receivePacket(packet);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PacketStatus.FAILED, response.getBody().getPacketStatus());

        // Verify it tried 3 times
        verify(restTemplate, times(3)).postForEntity(anyString(), any(Packet.class), eq(Packet.class));
    }
}

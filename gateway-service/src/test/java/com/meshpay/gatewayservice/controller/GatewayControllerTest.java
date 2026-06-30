package com.meshpay.gatewayservice.controller;

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

public class GatewayControllerTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private GatewayController gatewayController;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(gatewayController, "bankServiceUrl", "http://localhost:8085");
        ReflectionTestUtils.setField(gatewayController, "transactionServiceUrl", "http://localhost:8086");
        ReflectionTestUtils.setField(gatewayController, "restTemplate", restTemplate);
    }

    @Test
    public void testGatewayForwardPacketSuccess() {
        // Arrange
        Packet packet = Packet.builder()
                .packetId("pkt-999")
                .transactionId(UUID.randomUUID())
                .checksum("checksum-hash")
                .ttl(LocalDateTime.now().plusMinutes(10))
                .hopCount(2)
                .maxHopCount(5)
                .routeHistory(new ArrayList<>())
                .packetStatus(PacketStatus.RECEIVED)
                .build();

        Packet bankAck = Packet.builder()
                .packetStatus(PacketStatus.DELIVERED_TO_GATEWAY)
                .build();

        // Mock notify call returning 200 Void
        when(restTemplate.postForEntity(eq("http://localhost:8086/api/transactions/packet-status"), any(Packet.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        // Mock bank forward call returning 200 OK
        when(restTemplate.postForEntity(eq("http://localhost:8085/api/payments/process"), any(Packet.class), eq(Packet.class)))
                .thenReturn(new ResponseEntity<>(bankAck, HttpStatus.OK));

        // Act
        ResponseEntity<Packet> response = gatewayController.receivePacket(packet);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(PacketStatus.FORWARDED, response.getBody().getPacketStatus());
        assertEquals(3, packet.getHopCount());
        assertTrue(packet.getRouteHistory().contains("Gateway"));
        assertEquals("Gateway", packet.getCurrentNode());
        assertEquals("Bank", packet.getNextNode());

        // Verify transaction service notification called at least twice (reached gateway, and forwarded to bank)
        verify(restTemplate, atLeastOnce()).postForEntity(eq("http://localhost:8086/api/transactions/packet-status"), any(Packet.class), eq(Void.class));
        // Verify bank service forward called once
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8085/api/payments/process"), any(Packet.class), eq(Packet.class));
    }

    @Test
    public void testGatewayForwardPacketTtlExpired() {
        // Arrange
        Packet packet = Packet.builder()
                .packetId("pkt-999")
                .transactionId(UUID.randomUUID())
                .checksum("checksum-hash")
                .ttl(LocalDateTime.now().minusMinutes(5)) // Expired
                .hopCount(2)
                .maxHopCount(5)
                .routeHistory(new ArrayList<>())
                .packetStatus(PacketStatus.RECEIVED)
                .build();

        // Act
        ResponseEntity<Packet> response = gatewayController.receivePacket(packet);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(PacketStatus.DROPPED, response.getBody().getPacketStatus());

        // Verify failure notified to transaction service
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8086/api/transactions/packet-status"), any(Packet.class), eq(Void.class));
        verify(restTemplate, never()).postForEntity(eq("http://localhost:8085/api/payments/process"), any(Packet.class), eq(Packet.class));
    }

    @Test
    public void testGatewayForwardRetryFailure() {
        // Arrange
        Packet packet = Packet.builder()
                .packetId("pkt-999")
                .transactionId(UUID.randomUUID())
                .checksum("checksum-hash")
                .ttl(LocalDateTime.now().plusMinutes(10))
                .hopCount(2)
                .maxHopCount(5)
                .routeHistory(new ArrayList<>())
                .packetStatus(PacketStatus.RECEIVED)
                .build();

        // Mock bank call failing
        when(restTemplate.postForEntity(eq("http://localhost:8085/api/payments/process"), any(Packet.class), eq(Packet.class)))
                .thenThrow(new RuntimeException("Bank Connection Timeout"));

        // Act
        ResponseEntity<Packet> response = gatewayController.receivePacket(packet);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(PacketStatus.FAILED, response.getBody().getPacketStatus());

        // Verify retried 3 times
        verify(restTemplate, times(3)).postForEntity(eq("http://localhost:8085/api/payments/process"), any(Packet.class), eq(Packet.class));
        // Verify failure notified to transaction service
        verify(restTemplate, atLeastOnce()).postForEntity(eq("http://localhost:8086/api/transactions/packet-status"), any(Packet.class), eq(Void.class));
    }
}

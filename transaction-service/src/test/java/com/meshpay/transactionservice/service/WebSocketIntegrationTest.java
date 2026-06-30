package com.meshpay.transactionservice.service;

import com.meshpay.common.dto.TransactionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    }
)
public class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TransactionService transactionService;

    private WebSocketStompClient stompClient;
    private BlockingQueue<TransactionEvent> blockingQueue;

    @BeforeEach
    public void setUp() {
        blockingQueue = new LinkedBlockingDeque<>();
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(new StandardWebSocketClient()));
        transports.add(new RestTemplateXhrTransport(new RestTemplate()));
        
        stompClient = new WebSocketStompClient(new SockJsClient(transports));
        
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        converter.setObjectMapper(mapper);
        stompClient.setMessageConverter(converter);
    }

    @Test
    public void testWebSocketEventBroadcasting() throws Exception {
        UUID transactionId = UUID.randomUUID();
        String wsUrl = "ws://localhost:" + port + "/ws";

        StompSession session = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        assertNotNull(session);

        session.subscribe("/topic/transactions/" + transactionId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TransactionEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                blockingQueue.offer((TransactionEvent) payload);
            }
        });

        // Delay to allow subscription registration to complete on Stomp broker
        Thread.sleep(1000);

        // Publish event locally from transactionService
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(transactionId)
                .timestamp(LocalDateTime.now())
                .serviceName("Test-Service")
                .currentStage("RELAY_1")
                .transactionStatus("ROUTING")
                .packetStatus("RECEIVED")
                .hopCount(1)
                .routeHistory(new ArrayList<>(List.of("Transaction-Service", "Relay-1")))
                .message("Test message broadcast")
                .build();

        transactionService.processTransactionEvent(event);

        TransactionEvent receivedEvent = blockingQueue.poll(8, TimeUnit.SECONDS);

        assertNotNull(receivedEvent);
        assertEquals(transactionId, receivedEvent.getTransactionId());
        assertEquals("Test-Service", receivedEvent.getServiceName());
        assertEquals("RELAY_1", receivedEvent.getCurrentStage());
        assertEquals("Test message broadcast", receivedEvent.getMessage());
    }
}

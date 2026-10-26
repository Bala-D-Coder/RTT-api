package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.dto.request.NoteCreateRequest;
import com.bunny.rtt_api.dto.request.ReminderCreateRequest;
import com.bunny.rtt_api.dto.response.NoteResponse;
import com.bunny.rtt_api.dto.response.RealtimeTicketResponse;
import com.bunny.rtt_api.dto.response.ReminderResponse;
import com.bunny.rtt_api.entity.AsrCache;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.exception.AppException;
import com.bunny.rtt_api.repository.UserRepository;
import com.bunny.rtt_api.security.TokenService;
import com.bunny.rtt_api.service.note.NoteService;
import com.bunny.rtt_api.service.realtime.AsrCacheService;
import com.bunny.rtt_api.service.realtime.Scheduler;
import com.bunny.rtt_api.service.realtime.TicketService;
import com.bunny.rtt_api.service.realtime.VadService;
import com.bunny.rtt_api.service.reminder.ReminderService;
import com.bunny.rtt_api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RealtimeAndNotificationIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private NoteService noteService;

    @Autowired
    private ReminderService reminderService;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private VadService vadService;

    @Autowired
    private AsrCacheService asrCacheService;

    private User testUser;
    private String userToken;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByEmail("test_realtime@apex.in").orElseGet(() ->
                userRepository.save(User.builder()
                        .email("test_realtime@apex.in")
                        .name("Realtime Tester")
                        .passwordHash(passwordEncoder.encode("Password123!"))
                        .timezone("Asia/Kolkata")
                        .role("user")
                        .build())
        );

        userToken = tokenService.generateAccessToken(testUser.getId().toString(), testUser.getEmail(), testUser.getRole());
    }

    @Test
    @DisplayName("Ticket issuance and single-use Caffeine cache consumption")
    void testTicketIssuanceAndSingleUseConsumption() throws Exception {
        // 1. Issue ticket via POST /api/v1/realtime/ticket
        String responseJson = mockMvc.perform(post("/api/v1/realtime/ticket")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isString())
                .andExpect(jsonPath("$.expiresIn").value(30))
                .andReturn().getResponse().getContentAsString();

        RealtimeTicketResponse ticketResponse = objectMapper.readValue(responseJson, RealtimeTicketResponse.class);
        String ticket = ticketResponse.ticket();

        // 2. Consume ticket once -> success
        String consumedUserId = ticketService.consumeTicket(ticket);
        assertThat(consumedUserId).isEqualTo(testUser.getId().toString());

        // 3. Second consume must fail (single-use cache eviction)
        assertThatThrownBy(() -> ticketService.consumeTicket(ticket))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Ticket expired or already used");
    }

    @Test
    @DisplayName("/ws/transcribe: handshake, start, audio frames, flush, and event stream")
    void testTranscribeWebSocketStream() throws Exception {
        RealtimeTicketResponse ticketResponse = ticketService.createTicket(testUser);

        String wsUrl = "ws://localhost:" + port + "/ws/transcribe?ticket=" + ticketResponse.ticket();
        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();

        WebSocketSession session = client.execute(new AbstractWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                receivedMessages.add(message.getPayload());
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        assertThat(session.isOpen()).isTrue();

        // 1. First message must be ready event
        String readyMsg = receivedMessages.poll(5, TimeUnit.SECONDS);
        assertThat(readyMsg).isNotNull();
        JsonNode readyNode = objectMapper.readTree(readyMsg);
        assertThat(readyNode.get("type").asText()).isEqualTo("ready");
        assertThat(readyNode.has("sessionId")).isTrue();

        // 2. Send start message
        String startJson = "{\"sampleRate\":16000,\"encoding\":\"pcm_s16le\",\"channels\":1,\"mode\":\"create\"}";
        session.sendMessage(new TextMessage(startJson));

        // 3. Send PCM16 audio frames (100ms = 3200 bytes)
        byte[] pcmBytes = new byte[3200];
        for (int i = 0; i < pcmBytes.length; i += 2) {
            short val = (short) (1000 + (i % 500));
            pcmBytes[i] = (byte) (val & 0xFF);
            pcmBytes[i + 1] = (byte) ((val >> 8) & 0xFF);
        }

        // Send 4 frames (~400 ms)
        for (int i = 0; i < 4; i++) {
            session.sendMessage(new BinaryMessage(pcmBytes));
            Thread.sleep(50);
        }

        // 4. Send flush message
        session.sendMessage(new TextMessage("flush"));

        // 5. Verify partial and/or final events received
        boolean receivedFinal = false;
        for (int i = 0; i < 5; i++) {
            String msg = receivedMessages.poll(3, TimeUnit.SECONDS);
            if (msg == null) break;
            JsonNode node = objectMapper.readTree(msg);
            String type = node.get("type").asText();
            if ("final".equals(type)) {
                receivedFinal = true;
                assertThat(node.has("text")).isTrue();
                assertThat(node.has("seq")).isTrue();
                break;
            }
        }

        assertThat(receivedFinal).isTrue();
        session.close();
    }

    @Test
    @DisplayName("/ws/transcribe: dictation mode appends note block with source='dictation' (B18)")
    void testTranscribeAppendsDictationBlock() throws Exception {
        // Create an empty note
        NoteResponse note = noteService.createNote(new NoteCreateRequest("Dictation Test Note", "sand", null));
        UUID noteId = note.id();

        RealtimeTicketResponse ticketResp = ticketService.createTicket(testUser);
        String wsUrl = "ws://localhost:" + port + "/ws/transcribe?ticket=" + ticketResp.ticket();
        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        WebSocketSession session = client.execute(new AbstractWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messages.add(message.getPayload());
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        // Wait for ready
        messages.poll(5, TimeUnit.SECONDS);

        // Start with noteId
        String startJson = String.format("{\"sampleRate\":16000,\"encoding\":\"pcm_s16le\",\"channels\":1,\"noteId\":\"%s\",\"mode\":\"append\"}", noteId);
        session.sendMessage(new TextMessage(startJson));
        Thread.sleep(100);

        // Send non-empty audio frames
        byte[] pcmBytes = new byte[3200];
        for (int i = 0; i < pcmBytes.length; i += 2) {
            short val = (short) (1000 + (i % 500));
            pcmBytes[i] = (byte) (val & 0xFF);
            pcmBytes[i + 1] = (byte) ((val >> 8) & 0xFF);
        }

        for (int i = 0; i < 3; i++) {
            session.sendMessage(new BinaryMessage(pcmBytes));
            Thread.sleep(50);
        }

        // Flush
        session.sendMessage(new TextMessage("flush"));

        // Wait for final event
        boolean receivedFinal = false;
        for (int i = 0; i < 5; i++) {
            String msg = messages.poll(3, TimeUnit.SECONDS);
            if (msg != null && msg.contains("\"final\"")) {
                receivedFinal = true;
                break;
            }
        }
        assertThat(receivedFinal).isTrue();

        session.close();

        // Verify note now contains a block with source = 'dictation'
        NoteResponse updatedNote = noteService.getNote(noteId);
        assertThat(updatedNote.body()).isNotEmpty();
        assertThat(updatedNote.body().get(0).source()).isEqualTo("dictation");
    }

    @Test
    @DisplayName("/ws/notifications: workspace room broadcasts reminder_due on scheduler tick")
    void testNotificationWebSocketBroadcast() throws Exception {
        // Create a due reminder
        Instant pastDue = Instant.now().minus(5, ChronoUnit.MINUTES);
        ReminderResponse reminder = reminderService.createReminder(new ReminderCreateRequest(
                "Standup Reminder Test",
                pastDue,
                null,
                "none",
                null,
                "Asia/Kolkata"
        ));

        // Connect to /ws/notifications
        RealtimeTicketResponse ticket = ticketService.createTicket(testUser);
        String wsUrl = "ws://localhost:" + port + "/ws/notifications?ticket=" + ticket.ticket();
        StandardWebSocketClient client = new StandardWebSocketClient();
        BlockingQueue<String> notifications = new LinkedBlockingQueue<>();

        WebSocketSession session = client.execute(new AbstractWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                notifications.add(message.getPayload());
            }
        }, wsUrl).get(5, TimeUnit.SECONDS);

        // 1. Initial reconnected event
        String initMsg = notifications.poll(5, TimeUnit.SECONDS);
        assertThat(initMsg).isNotNull();
        JsonNode initNode = objectMapper.readTree(initMsg);
        assertThat(initNode.get("type").asText()).isEqualTo("reconnected");

        // 2. Trigger scheduler runOnce to claim and deliver the reminder
        int claimed = scheduler.runOnce();
        assertThat(claimed).isGreaterThanOrEqualTo(1);

        // 3. Verify notification received over WebSocket room
        String notifMsg = notifications.poll(5, TimeUnit.SECONDS);
        assertThat(notifMsg).isNotNull();
        JsonNode notifNode = objectMapper.readTree(notifMsg);
        assertThat(notifNode.get("type").asText()).isEqualTo("reminder_due");
        assertThat(notifNode.get("reminderId").asText()).isEqualTo(reminder.id().toString());
        assertThat(notifNode.get("title").asText()).isEqualTo("Standup Reminder Test");

        session.close();
    }

    @Test
    @DisplayName("VAD Service energy calculation and ASR cache hit tracking")
    void testVadAndAsrCache() {
        // 1. VAD check
        byte[] silentPcm = new byte[3200]; // all zeros
        boolean speechOnSilent = vadService.isSpeech(ByteBuffer.wrap(silentPcm));
        assertThat(speechOnSilent).isFalse();

        byte[] loudPcm = new byte[3200];
        for (int i = 0; i < loudPcm.length; i += 2) {
            short val = 5000;
            loudPcm[i] = (byte) (val & 0xFF);
            loudPcm[i + 1] = (byte) ((val >> 8) & 0xFF);
        }
        boolean speechOnLoud = vadService.isSpeech(ByteBuffer.wrap(loudPcm));
        assertThat(speechOnLoud).isTrue();

        // 2. ASR Cache
        String sha = asrCacheService.computeSha256(loudPcm);
        assertThat(sha).isNotBlank();

        // Put in cache
        AsrCache saved = asrCacheService.put(sha, "mock", "test-model", "Test transcript", null);
        assertThat(saved.getHitCount()).isEqualTo(0);

        // Lookup -> hit count increments
        Optional<AsrCache> hit = asrCacheService.lookup(sha);
        assertThat(hit).isPresent();
        assertThat(hit.get().getHitCount()).isEqualTo(1);
        assertThat(hit.get().getText()).isEqualTo("Test transcript");
    }
}

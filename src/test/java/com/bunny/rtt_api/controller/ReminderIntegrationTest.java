package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.bootstrap.SeedAccountsRunner;
import com.bunny.rtt_api.config.AppProps;
import com.bunny.rtt_api.dto.request.NoteCreateRequest;
import com.bunny.rtt_api.dto.request.ReminderCreateRequest;
import com.bunny.rtt_api.dto.request.ReminderPatchRequest;
import com.bunny.rtt_api.dto.request.ReminderSnoozeRequest;
import com.bunny.rtt_api.dto.response.NoteResponse;
import com.bunny.rtt_api.dto.response.ReminderResponse;
import com.bunny.rtt_api.entity.Reminder;
import com.bunny.rtt_api.entity.ReminderDelivery;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.repository.NoteRepository;
import com.bunny.rtt_api.repository.ReminderDeliveryRepository;
import com.bunny.rtt_api.repository.ReminderRepository;
import com.bunny.rtt_api.repository.UserRepository;
import com.bunny.rtt_api.security.TokenService;
import com.bunny.rtt_api.service.realtime.Scheduler;
import com.bunny.rtt_api.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReminderIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private ReminderDeliveryRepository reminderDeliveryRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SeedAccountsRunner seedAccountsRunner;

    @Autowired
    private AppProps appProps;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Scheduler scheduler;

    private String adminToken;

    @BeforeEach
    void setUp() {
        seedAccountsRunner.seedAccounts();

        reminderDeliveryRepository.deleteAll();
        reminderRepository.deleteAll();
        noteRepository.deleteAll();

        User admin = userRepository.findByEmail(appProps.admin().email()).orElseThrow();
        adminToken = tokenService.generateAccessToken(admin.getId().toString(), admin.getEmail(), admin.getRole());
    }

    @Test
    void testReminderCrudAndTransitions() throws Exception {
        // 1. Create reminder
        Instant dueAt = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        ReminderCreateRequest createReq = new ReminderCreateRequest(
                "Submit sprint review",
                dueAt,
                null,
                "none",
                null,
                "Asia/Kolkata"
        );

        MvcResult createRes = mockMvc.perform(post("/api/v1/reminders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Submit sprint review"))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.repeat").value("none"))
                .andReturn();

        ReminderResponse reminder = objectMapper.readValue(createRes.getResponse().getContentAsString(), ReminderResponse.class);
        UUID reminderId = reminder.id();

        // 2. Get reminder
        mockMvc.perform(get("/api/v1/reminders/" + reminderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reminderId.toString()))
                .andExpect(jsonPath("$.title").value("Submit sprint review"));

        // 3. Patch reminder
        ReminderPatchRequest patchReq = new ReminderPatchRequest(
                "Submit final sprint review",
                null,
                null,
                "daily",
                null,
                null,
                null
        );
        mockMvc.perform(patch("/api/v1/reminders/" + reminderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Submit final sprint review"))
                .andExpect(jsonPath("$.repeat").value("daily"));

        // 4. Snooze reminder
        Instant snoozeTo = Instant.now().plus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        ReminderSnoozeRequest snoozeReq = new ReminderSnoozeRequest(snoozeTo);
        mockMvc.perform(post("/api/v1/reminders/" + reminderId + "/snooze")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(snoozeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("snoozed"));

        // 5. Complete reminder
        mockMvc.perform(post("/api/v1/reminders/" + reminderId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));

        // 6. Delete reminder
        mockMvc.perform(delete("/api/v1/reminders/" + reminderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // 7. Verify 404 after deletion
        mockMvc.perform(get("/api/v1/reminders/" + reminderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void testSchedulerClaimAndExactlyOnceDelivery() {
        // Create due reminder with repeat=none
        Instant due = Instant.now().minus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        Reminder r = Reminder.builder()
                .title("Standup Reminder")
                .createdBy("admin@apex.in")
                .dueAt(due)
                .repeat("none")
                .status("pending")
                .build();
        reminderRepository.save(r);

        // Run scheduler tick
        int processed = scheduler.runOnce();
        assertThat(processed).isEqualTo(1);

        // Verify reminder status and attempt count
        Reminder updated = reminderRepository.findById(r.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("sent");
        assertThat(updated.getAttemptCount()).isEqualTo(1);

        // Verify exactly-once delivery row in reminder_deliveries
        List<ReminderDelivery> deliveries = reminderDeliveryRepository.findByReminderId(r.getId());
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).getChannel()).isEqualTo("ws");
        assertThat(deliveries.get(0).getAttempt()).isEqualTo(1);

        // Run second tick -> nothing due, exactly 0 processed
        int secondTick = scheduler.runOnce();
        assertThat(secondTick).isEqualTo(0);
    }

    @Test
    void testRepeatingRemindersAdvanceDueAt() {
        // Create repeating daily reminder that is due
        Instant due = Instant.now().minus(2, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        Reminder r = Reminder.builder()
                .title("Daily Standup")
                .createdBy("admin@apex.in")
                .dueAt(due)
                .timezone("Asia/Kolkata")
                .repeat("daily")
                .status("pending")
                .build();
        reminderRepository.save(r);

        // Run tick
        int processed = scheduler.runOnce();
        assertThat(processed).isEqualTo(1);

        // For repeating reminder, status must flip back to 'pending' and due_at must advance by 1 day
        Reminder updated = reminderRepository.findById(r.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("pending");
        assertThat(updated.getAttemptCount()).isEqualTo(1);
        assertThat(updated.getDueAt()).isAfter(due);
        assertThat(ChronoUnit.HOURS.between(due, updated.getDueAt())).isCloseTo(24, org.assertj.core.data.Offset.offset(1L));
    }

    @Test
    void testTrashedNoteSuppressesReminder() throws Exception {
        // 1. Create note
        NoteCreateRequest noteReq = new NoteCreateRequest("Remindable Note", "sand", null);
        MvcResult noteRes = mockMvc.perform(post("/api/v1/notes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noteReq))).andReturn();
        UUID noteId = objectMapper.readValue(noteRes.getResponse().getContentAsString(), NoteResponse.class).id();

        // 2. Attach due reminder to note
        Instant due = Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        ReminderCreateRequest remReq = new ReminderCreateRequest("Note Reminder", due, noteId, "none", null, "Asia/Kolkata");
        MvcResult remRes = mockMvc.perform(post("/api/v1/reminders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(remReq))).andReturn();
        UUID reminderId = objectMapper.readValue(remRes.getResponse().getContentAsString(), ReminderResponse.class).id();

        // 3. Move note to bin (soft delete)
        mockMvc.perform(delete("/api/v1/notes/" + noteId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)).andExpect(status().isOk());

        // 4. Scheduler runOnce() -> reminder on trashed note MUST NOT be claimed
        int processedWhileTrashed = scheduler.runOnce();
        assertThat(processedWhileTrashed).isEqualTo(0);

        Reminder stillPending = reminderRepository.findById(reminderId).orElseThrow();
        assertThat(stillPending.getStatus()).isEqualTo("pending");
        assertThat(stillPending.getAttemptCount()).isEqualTo(0);

        // 5. Restore note from bin -> reminder re-armed
        mockMvc.perform(post("/api/v1/notes/" + noteId + "/restore")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)).andExpect(status().isOk());

        // 6. Scheduler runOnce() now claims and delivers the reminder
        int processedAfterRestore = scheduler.runOnce();
        assertThat(processedAfterRestore).isEqualTo(1);

        Reminder delivered = reminderRepository.findById(reminderId).orElseThrow();
        assertThat(delivered.getStatus()).isEqualTo("sent");
        assertThat(delivered.getAttemptCount()).isEqualTo(1);
    }
}

package com.bunny.rtt_api.repository;

import com.bunny.rtt_api.entity.AsrCache;
import com.bunny.rtt_api.entity.Note;
import com.bunny.rtt_api.entity.NoteBlock;
import com.bunny.rtt_api.entity.Reminder;
import com.bunny.rtt_api.entity.ReminderDelivery;
import com.bunny.rtt_api.entity.Tag;
import com.bunny.rtt_api.entity.User;
import com.bunny.rtt_api.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaAndMigrationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private NoteBlockRepository noteBlockRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private ReminderDeliveryRepository reminderDeliveryRepository;

    @Autowired
    private AsrCacheRepository asrCacheRepository;

    @Test
    @DisplayName("Flyway migrations run and Hibernate ddl-auto=validate confirms JPA entities match DB schema")
    void flywayMigrationsAndEntityMappingsAreValid() {
        assertThat(postgres.isRunning()).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("User CRUD: creates, finds by email, and validates fields")
    void userCrudOperations() {
        String email = "testuser-" + UUID.randomUUID() + "@apex.in";
        User user = User.builder()
                .email(email)
                .name("Test User")
                .passwordHash("argon2-hash-sample")
                .role("demo")
                .timezone("Asia/Kolkata")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        User saved = userRepository.save(user);
        assertThat(saved.getId()).isNotNull();

        Optional<User> found = userRepository.findByEmail(email);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test User");
        assertThat(found.get().getRole()).isEqualTo("demo");
        assertThat(found.get().getTimezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    @Transactional
    @DisplayName("Note & NoteBlock: cascade saves blocks with provenance ('dictation' and 'typed')")
    void noteAndBlocksCascading() {
        Note note = Note.builder()
                .createdBy("admin@apex.in")
                .title("Architecture Brainstorm")
                .color("moss")
                .pinned(true)
                .seedKey("test-note-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        NoteBlock block1 = NoteBlock.builder()
                .text("Spoke about live streaming audio transcription.")
                .position(0)
                .source("dictation")
                .createdAt(Instant.now())
                .build();

        NoteBlock block2 = NoteBlock.builder()
                .text("Agreed on 100ms frames PCM16 at 16kHz.")
                .position(1)
                .source("typed")
                .createdAt(Instant.now())
                .build();

        note.addBlock(block1);
        note.addBlock(block2);

        Note saved = noteRepository.save(note);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBlocks()).hasSize(2);

        List<NoteBlock> blocks = noteBlockRepository.findByNoteIdOrderByPositionAsc(saved.getId());
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).getSource()).isEqualTo("dictation");
        assertThat(blocks.get(1).getSource()).isEqualTo("typed");
    }

    @Test
    @Transactional
    @DisplayName("Note & Tag: Many-to-Many association via note_tags")
    void noteAndTagManyToMany() {
        Tag tag1 = tagRepository.save(Tag.builder().name("Urgent-" + UUID.randomUUID()).build());
        Tag tag2 = tagRepository.save(Tag.builder().name("Roadmap-" + UUID.randomUUID()).build());

        Note note = Note.builder()
                .createdBy("admin@apex.in")
                .title("Tagged Note")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        note.addTag(tag1);
        note.addTag(tag2);

        Note saved = noteRepository.save(note);
        assertThat(saved.getTags()).hasSize(2);

        Optional<Note> reloaded = noteRepository.findById(saved.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getTags()).extracting(Tag::getName)
                .contains(tag1.getName(), tag2.getName());
    }

    @Test
    @Transactional
    @DisplayName("Reminder & ReminderDelivery: status, weekdays array, and delivery logging")
    void reminderAndDeliveryCrud() {
        Note note = noteRepository.save(Note.builder()
                .createdBy("admin@apex.in")
                .title("Reminder Note")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        Reminder reminder = Reminder.builder()
                .createdBy("admin@apex.in")
                .note(note)
                .title("Standup Call")
                .dueAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                .timezone("Asia/Kolkata")
                .repeat("weekdays")
                .weekdays(new Integer[]{1, 2, 3, 4, 5})
                .status("pending")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Reminder savedReminder = reminderRepository.save(reminder);
        assertThat(savedReminder.getId()).isNotNull();
        assertThat(savedReminder.getWeekdays()).containsExactly(1, 2, 3, 4, 5);

        ReminderDelivery delivery = ReminderDelivery.builder()
                .reminderId(savedReminder.getId())
                .channel("ws")
                .attempt(1)
                .deliveredAt(Instant.now())
                .build();

        ReminderDelivery savedDelivery = reminderDeliveryRepository.save(delivery);
        assertThat(savedDelivery.getId()).isNotNull();
        assertThat(reminderDeliveryRepository.existsByReminderIdAndChannelAndAttempt(
                savedReminder.getId(), "ws", 1)).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("AsrCache: stores audio SHA-256 and JSON segments")
    void asrCacheOperations() {
        String sha256 = "sha256-" + UUID.randomUUID();
        AsrCache cache = AsrCache.builder()
                .audioSha256(sha256)
                .engine("gemini_live")
                .model("gemini-3.5-transcribe-live")
                .text("Testing audio transcription cache.")
                .segmentsJson("{\"segments\": [{\"start\": 0.0, \"end\": 1.5, \"text\": \"Testing\"}]}")
                .hitCount(1)
                .createdAt(Instant.now())
                .build();

        AsrCache saved = asrCacheRepository.save(cache);
        assertThat(saved.getId()).isNotNull();

        Optional<AsrCache> found = asrCacheRepository.findByAudioSha256(sha256);
        assertThat(found).isPresent();
        assertThat(found.get().getText()).isEqualTo("Testing audio transcription cache.");
        assertThat(found.get().getHitCount()).isEqualTo(1);
    }
}

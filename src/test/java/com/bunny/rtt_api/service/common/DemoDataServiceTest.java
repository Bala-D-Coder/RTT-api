package com.bunny.rtt_api.service.common;

import com.bunny.rtt_api.entity.NoteBlock;
import com.bunny.rtt_api.entity.Reminder;
import com.bunny.rtt_api.entity.Tag;
import com.bunny.rtt_api.repository.NoteBlockRepository;
import com.bunny.rtt_api.repository.NoteRepository;
import com.bunny.rtt_api.repository.ReminderRepository;
import com.bunny.rtt_api.repository.TagRepository;
import com.bunny.rtt_api.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataServiceTest extends IntegrationTestBase {

        @Autowired
        private DemoDataService demoDataService;

        @Autowired
        private NoteRepository noteRepository;

        @Autowired
        private NoteBlockRepository noteBlockRepository;

        @Autowired
        private TagRepository tagRepository;

        @Autowired
        private ReminderRepository reminderRepository;

        @org.junit.jupiter.api.BeforeEach
        void cleanUpSeedData() {
                reminderRepository.deleteAll();
                noteBlockRepository.deleteAll();
                noteRepository.deleteAll();
                tagRepository.deleteAll();
        }

        @Test
        @DisplayName("DemoDataService seeds 8 notes with Hinglish blocks, tags, and reminders; running twice inserts 0 rows")
        void demoDataSeedingIsIdempotentAndInsertsZeroRowsOnSecondRun() {
                // First run
                DemoDataService.ReseedResult firstRun = demoDataService.seedDemoData();

                assertThat(firstRun.notes()).isGreaterThanOrEqualTo(8);
                assertThat(firstRun.blocks()).isGreaterThanOrEqualTo(14);
                assertThat(firstRun.tags()).isGreaterThanOrEqualTo(5);
                assertThat(firstRun.reminders()).isGreaterThanOrEqualTo(4);

                // Verify realistic Hinglish content and dictation provenance
                List<NoteBlock> dictationBlocks = noteBlockRepository.findBySourceOrderByCreatedAtDesc("dictation");
                assertThat(dictationBlocks).isNotEmpty();
                boolean hasHinglish = dictationBlocks.stream()
                                .anyMatch(b -> b.getText().contains("standup") || b.getText().contains("karna hai")
                                                || b.getText().contains("pasand aayi"));
                assertThat(hasHinglish).isTrue();

                List<NoteBlock> typedBlocks = noteBlockRepository.findBySourceOrderByCreatedAtDesc("typed");
                assertThat(typedBlocks).isNotEmpty();

                // Verify tags are attached to notes
                List<Tag> allTags = tagRepository.findAll();
                assertThat(allTags).extracting(Tag::getName)
                                .contains("Work", "Sprint", "Client", "Personal", "Ideas");

                // Verify reminders with repeat and status
                List<Reminder> reminders = reminderRepository.findAll();
                assertThat(reminders).extracting(Reminder::getStatus)
                                .contains("pending", "done");
                assertThat(reminders).extracting(Reminder::getRepeat)
                                .contains("daily", "none", "weekdays");

                // Second run: must insert ZERO rows
                DemoDataService.ReseedResult secondRun = demoDataService.seedDemoData();

                assertThat(secondRun.notes()).isEqualTo(0);
                assertThat(secondRun.blocks()).isEqualTo(0);
                assertThat(secondRun.reminders()).isEqualTo(0);
                assertThat(secondRun.tags()).isEqualTo(0);
        }
}

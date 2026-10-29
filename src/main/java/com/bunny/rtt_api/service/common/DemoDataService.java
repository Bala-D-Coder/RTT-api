package com.bunny.rtt_api.service.common;

import com.bunny.rtt_api.config.AppProps;
import com.bunny.rtt_api.entity.Note;
import com.bunny.rtt_api.entity.NoteBlock;
import com.bunny.rtt_api.entity.Reminder;
import com.bunny.rtt_api.entity.Tag;
import com.bunny.rtt_api.repository.NoteBlockRepository;
import com.bunny.rtt_api.repository.NoteRepository;
import com.bunny.rtt_api.repository.ReminderRepository;
import com.bunny.rtt_api.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DemoDataService {

        private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

        private final AppProps appProps;
        private final NoteRepository noteRepository;
        private final TagRepository tagRepository;
        private final ReminderRepository reminderRepository;

        public DemoDataService(
                        AppProps appProps,
                        NoteRepository noteRepository,
                        NoteBlockRepository noteBlockRepository,
                        TagRepository tagRepository,
                        ReminderRepository reminderRepository) {
                this.appProps = appProps;
                this.noteRepository = noteRepository;
                this.tagRepository = tagRepository;
                this.reminderRepository = reminderRepository;
        }

        public record ReseedResult(int notes, int blocks, int reminders, int tags) {
        }

        @Transactional
        public ReseedResult seedDemoData() {
                String createdBy = appProps.admin().email();
                Instant now = Instant.now();

                int tagsCreated = 0;
                int notesCreated = 0;
                int blocksCreated = 0;
                int remindersCreated = 0;

                // 1. Seed Tags (reusable, shared tag space)
                Map<String, Tag> tagMap = new HashMap<>();
                String[][] tagDefinitions = {
                                { "seed-tag-work", "Work" },
                                { "seed-tag-sprint", "Sprint" },
                                { "seed-tag-client", "Client" },
                                { "seed-tag-personal", "Personal" },
                                { "seed-tag-ideas", "Ideas" }
                };

                for (String[] tagDef : tagDefinitions) {
                        String seedKey = tagDef[0];
                        String name = tagDef[1];
                        Tag tag = tagRepository.findBySeedKey(seedKey).orElse(null);
                        if (tag == null) {
                                tag = tagRepository.findByName(name).orElse(null);
                        }
                        if (tag == null) {
                                tag = Tag.builder()
                                                .seedKey(seedKey)
                                                .name(name)
                                                .build();
                                tag = tagRepository.save(tag);
                                tagsCreated++;
                        }
                        tagMap.put(name, tag);
                }

                // 2. Define 8 realistic Hinglish notes with dictation & typed blocks
                record SeedBlockDef(String text, int position, String source) {
                }
                record SeedReminderDef(String seedKey, String title, Instant dueAt, String repeat, Integer[] weekdays,
                                String status) {
                }
                record SeedNoteDef(
                                String seedKey,
                                String title,
                                String color,
                                boolean pinned,
                                List<String> tagNames,
                                List<SeedBlockDef> blocks,
                                List<SeedReminderDef> reminders) {
                }

                List<SeedNoteDef> noteDefs = List.of(
                                new SeedNoteDef(
                                                "seed-note-standup",
                                                "Daily Standup & Sprint Sync",
                                                "sand",
                                                true,
                                                List.of("Work", "Sprint"),
                                                List.of(
                                                                new SeedBlockDef(
                                                                                "Aaj ke standup me API migration discuss karna hai. Whisper sidecar benchmark pass ho gaya.",
                                                                                0, "dictation"),
                                                                new SeedBlockDef(
                                                                                "Need to verify WebSocket streaming latency and verify token refresh logic before demo.",
                                                                                1, "typed")),
                                                List.of(
                                                                new SeedReminderDef("seed-reminder-standup",
                                                                                "Sync with team on Hinglish transcription latency",
                                                                                now.plus(2, ChronoUnit.MINUTES),
                                                                                "daily", null, "pending"))),
                                new SeedNoteDef(
                                                "seed-note-client",
                                                "Client Meeting Notes",
                                                "coral",
                                                false,
                                                List.of("Work", "Client"),
                                                List.of(
                                                                new SeedBlockDef(
                                                                                "Client ko audio transcription ki accuracy aur Hinglish code-switching demo bohot pasand aayi.",
                                                                                0, "dictation"),
                                                                new SeedBlockDef(
                                                                                "Follow-up on export formats (Markdown, TXT, JSON) by end of this week.",
                                                                                1, "typed")),
                                                List.of(
                                                                new SeedReminderDef("seed-reminder-client",
                                                                                "Send client follow-up email with export samples",
                                                                                now.plus(1, ChronoUnit.HOURS), "none",
                                                                                null, "pending"))),
                                new SeedNoteDef(
                                                "seed-note-groceries",
                                                "Ghar ka Samaan & Groceries",
                                                "moss",
                                                false,
                                                List.of("Personal"),
                                                List.of(
                                                                new SeedBlockDef(
                                                                                "Shaam ko aate waqt dahi, doodh, brown bread aur green tea lena mat bhoolna.",
                                                                                0, "dictation"),
                                                                new SeedBlockDef("Check if dry fruits stock is over.",
                                                                                1, "typed")),
                                                List.of()),
                                new SeedNoteDef(
                                                "seed-note-ideas",
                                                "Product Ideas & Brainstorming",
                                                "clay",
                                                false,
                                                List.of("Ideas", "Personal"),
                                                List.of(
                                                                new SeedBlockDef(
                                                                                "Real-time Voice to Reminder automatic parser with offline Compromise NLP.",
                                                                                0, "dictation"),
                                                                new SeedBlockDef(
                                                                                "Zero-friction quick capture on mobile lockscreen with PWA offline fallback.",
                                                                                1, "typed")),
                                                List.of()),
                                new SeedNoteDef(
                                                "seed-note-tech-debt",
                                                "Architecture & Tech Debt",
                                                "sand",
                                                false,
                                                List.of("Work"),
                                                List.of(
                                                                new SeedBlockDef(
                                                                                "Database indexes review: ensure partial index on notes(updated_at DESC) where deleted_at is null is utilized.",
                                                                                0, "typed"),
                                                                new SeedBlockDef(
                                                                                "Hibernate batch size configured to 50 to prevent N+1 queries on notes list views.",
                                                                                1, "typed")),
                                                List.of(
                                                                new SeedReminderDef("seed-reminder-tech-debt",
                                                                                "Review Postgres query plans for batch queries",
                                                                                now.minus(2, ChronoUnit.HOURS), "none",
                                                                                null, "done"))),
                                new SeedNoteDef(
                                                "seed-note-fitness",
                                                "Weekly Workout & Fitness Plan",
                                                "moss",
                                                false,
                                                List.of("Personal"),
                                                List.of(
                                                                new SeedBlockDef(
                                                                                "Subah 6 baje 5km running aur core workout complete karna hai.",
                                                                                0, "dictation"),
                                                                new SeedBlockDef(
                                                                                "Hydration reminder: 3 litres of water daily minimum.",
                                                                                1, "typed")),
                                                List.of(
                                                                new SeedReminderDef("seed-reminder-workout",
                                                                                "Morning running & fitness routine",
                                                                                now.plus(1, ChronoUnit.DAYS),
                                                                                "weekdays",
                                                                                new Integer[] { 1, 2, 3, 4, 5 },
                                                                                "pending"))),
                                new SeedNoteDef(
                                                "seed-note-travel",
                                                "Weekend Trip to Manali",
                                                "slate",
                                                false,
                                                List.of("Personal"),
                                                List.of(
                                                                new SeedBlockDef(
                                                                                "Bus booking confirm karni hai aur hotel advance transfer pending hai.",
                                                                                0, "dictation"),
                                                                new SeedBlockDef(
                                                                                "Packing list: warm jackets, thermals, power bank, running shoes.",
                                                                                1, "typed")),
                                                List.of()),
                                new SeedNoteDef(
                                                "seed-note-books",
                                                "Reading List & Book Summary",
                                                "sand",
                                                false,
                                                List.of("Ideas"),
                                                List.of(
                                                                new SeedBlockDef(
                                                                                "Designing Data-Intensive Applications: Chapter 7 on Transactions and Serializability.",
                                                                                0, "typed")),
                                                List.of()));

                for (SeedNoteDef def : noteDefs) {
                        Note note = noteRepository.findBySeedKey(def.seedKey()).orElse(null);
                        if (note == null) {
                                Set<Tag> noteTags = new HashSet<>();
                                for (String tagName : def.tagNames()) {
                                        Tag tag = tagMap.get(tagName);
                                        if (tag != null) {
                                                noteTags.add(tag);
                                        }
                                }

                                note = Note.builder()
                                                .seedKey(def.seedKey())
                                                .createdBy(createdBy)
                                                .title(def.title())
                                                .color(def.color())
                                                .pinned(def.pinned())
                                                .tags(noteTags)
                                                .createdAt(now)
                                                .updatedAt(now)
                                                .build();

                                List<NoteBlock> blocks = new ArrayList<>();
                                for (SeedBlockDef bDef : def.blocks()) {
                                        NoteBlock block = NoteBlock.builder()
                                                        .note(note)
                                                        .text(bDef.text())
                                                        .position(bDef.position())
                                                        .source(bDef.source())
                                                        .createdAt(now)
                                                        .build();
                                        blocks.add(block);
                                        blocksCreated++;
                                }
                                note.setBlocks(blocks);

                                note = noteRepository.save(note);
                                notesCreated++;

                                // Reminders attached to this note
                                for (SeedReminderDef rDef : def.reminders()) {
                                        if (!reminderRepository.existsBySeedKey(rDef.seedKey())) {
                                                Reminder reminder = Reminder.builder()
                                                                .seedKey(rDef.seedKey())
                                                                .createdBy(createdBy)
                                                                .note(note)
                                                                .title(rDef.title())
                                                                .dueAt(rDef.dueAt())
                                                                .repeat(rDef.repeat())
                                                                .weekdays(rDef.weekdays())
                                                                .status(rDef.status())
                                                                .timezone("Asia/Kolkata")
                                                                .createdAt(now)
                                                                .updatedAt(now)
                                                                .build();
                                                reminderRepository.save(reminder);
                                                remindersCreated++;
                                        }
                                }
                        } else {
                                // Note already exists; check if any missing reminders should be added
                                // idempotently
                                for (SeedReminderDef rDef : def.reminders()) {
                                        if (!reminderRepository.existsBySeedKey(rDef.seedKey())) {
                                                Reminder reminder = Reminder.builder()
                                                                .seedKey(rDef.seedKey())
                                                                .createdBy(createdBy)
                                                                .note(note)
                                                                .title(rDef.title())
                                                                .dueAt(rDef.dueAt())
                                                                .repeat(rDef.repeat())
                                                                .weekdays(rDef.weekdays())
                                                                .status(rDef.status())
                                                                .timezone("Asia/Kolkata")
                                                                .createdAt(now)
                                                                .updatedAt(now)
                                                                .build();
                                                reminderRepository.save(reminder);
                                                remindersCreated++;
                                        }
                                }
                        }
                }

                ReseedResult result = new ReseedResult(notesCreated, blocksCreated, remindersCreated, tagsCreated);
                log.info("DemoDataService completed. Inserted: {} notes, {} blocks, {} reminders, {} tags",
                                result.notes(), result.blocks(), result.reminders(), result.tags());
                return result;
        }
}

package com.bunny.rtt_api.service.realtime;

import com.bunny.rtt_api.entity.Reminder;
import com.bunny.rtt_api.entity.ReminderDelivery;
import com.bunny.rtt_api.repository.ReminderDeliveryRepository;
import com.bunny.rtt_api.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bunny.rtt_api.service.notification.NotificationService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class Scheduler {

    private final JdbcClient jdbcClient;
    private final ReminderRepository reminderRepository;
    private final ReminderDeliveryRepository reminderDeliveryRepository;
    private final NotificationService notificationService;

    private static final String CLAIM_SQL = """
            WITH claim AS (
              SELECT r.id FROM reminders r
              LEFT JOIN notes n ON n.id = r.note_id
              WHERE r.status IN ('pending', 'snoozed')
                AND r.due_at <= now()
                AND (r.note_id IS NULL OR n.deleted_at IS NULL)
              ORDER BY r.due_at
              FOR UPDATE OF r SKIP LOCKED
              LIMIT :batchSize
            )
            UPDATE reminders SET status = 'sent', attempt_count = reminders.attempt_count + 1, updated_at = now()
            FROM claim WHERE reminders.id = claim.id
            RETURNING reminders.id;
            """;

    /**
     * Synchronous single-tick worker execution.
     * Claims due reminders with FOR UPDATE SKIP LOCKED, delivers them,
     * logs delivery attempts, and advances repeating reminders.
     *
     * @return the number of claimed reminders processed in this tick
     */
    @Transactional
    public int runOnce() {
        List<UUID> claimedIds = jdbcClient.sql(CLAIM_SQL)
                .param("batchSize", 50)
                .query(UUID.class)
                .list();

        if (claimedIds.isEmpty()) {
            return 0;
        }

        List<Reminder> claimedReminders = reminderRepository.findAllById(claimedIds);

        for (Reminder reminder : claimedReminders) {
            processClaimedReminder(reminder);
        }

        log.debug("Scheduler processed {} reminders", claimedReminders.size());
        return claimedReminders.size();
    }

    @Scheduled(fixedDelayString = "${app.scheduler.delay-ms:1000}")
    public void scheduleTick() {
        runOnce();
    }

    private void processClaimedReminder(Reminder reminder) {
        int attempt = reminder.getAttemptCount();

        // 1. Exactly-once delivery log: unique (reminder_id, channel, attempt)
        try {
            ReminderDelivery delivery = ReminderDelivery.builder()
                    .reminderId(reminder.getId())
                    .channel("ws")
                    .attempt(attempt)
                    .deliveredAt(Instant.now())
                    .build();
            reminderDeliveryRepository.saveAndFlush(delivery);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Delivery already recorded for reminder {} attempt {}", reminder.getId(), attempt);
            return;
        }

        // 2. Broadcast reminder due notification (WebSocket room + FCM push)
        notificationService.notifyReminderDue(reminder);

        // 3. Advance repeating reminders in reminder's own timezone
        String repeat = reminder.getRepeat() != null ? reminder.getRepeat().toLowerCase() : "none";
        if (!"none".equals(repeat)) {
            Instant nextDueAt = computeNextDueAt(reminder.getDueAt(), reminder.getTimezone(), repeat,
                    reminder.getWeekdays());
            reminder.setDueAt(nextDueAt);
            reminder.setStatus("pending");
            reminder.setUpdatedAt(Instant.now());
            reminderRepository.save(reminder);
        }
    }

    public static Instant computeNextDueAt(Instant currentDueAt, String timezoneStr, String repeat,
            Integer[] weekdays) {
        ZoneId zone;
        try {
            zone = (timezoneStr != null && !timezoneStr.isBlank()) ? ZoneId.of(timezoneStr) : ZoneId.of("Asia/Kolkata");
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Kolkata");
        }

        ZonedDateTime zdt = currentDueAt.atZone(zone);

        if ("daily".equalsIgnoreCase(repeat)) {
            return zdt.plusDays(1).toInstant();
        } else if ("weekly".equalsIgnoreCase(repeat)) {
            return zdt.plusWeeks(1).toInstant();
        } else if ("weekdays".equalsIgnoreCase(repeat)) {
            List<Integer> targetDays = (weekdays != null && weekdays.length > 0)
                    ? Arrays.asList(weekdays)
                    : List.of(1, 2, 3, 4, 5); // Monday to Friday

            ZonedDateTime next = zdt.plusDays(1);
            int loopGuard = 0;
            while (!targetDays.contains(next.getDayOfWeek().getValue()) && loopGuard < 14) {
                next = next.plusDays(1);
                loopGuard++;
            }
            return next.toInstant();
        }

        return currentDueAt;
    }
}

package com.bunny.rtt_api.repository;

import com.bunny.rtt_api.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, UUID>, JpaSpecificationExecutor<Reminder> {
    Optional<Reminder> findBySeedKey(String seedKey);
    boolean existsBySeedKey(String seedKey);
    List<Reminder> findByStatus(String status);
    List<Reminder> findByStatusInAndDueAtLessThanEqual(List<String> statuses, Instant dueAt);
}

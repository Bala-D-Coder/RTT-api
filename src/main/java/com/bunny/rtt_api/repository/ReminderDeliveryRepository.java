package com.bunny.rtt_api.repository;

import com.bunny.rtt_api.entity.ReminderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReminderDeliveryRepository extends JpaRepository<ReminderDelivery, UUID> {
    boolean existsByReminderIdAndChannelAndAttempt(UUID reminderId, String channel, int attempt);
    List<ReminderDelivery> findByReminderId(UUID reminderId);
}

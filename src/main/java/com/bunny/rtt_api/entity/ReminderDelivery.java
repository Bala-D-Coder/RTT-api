package com.bunny.rtt_api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "reminder_deliveries",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_reminder_channel_attempt", columnNames = {"reminder_id", "channel", "attempt"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reminder_id", nullable = false)
    private UUID reminderId;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;

    @PrePersist
    protected void onCreate() {
        if (deliveredAt == null) {
            deliveredAt = Instant.now();
        }
    }
}

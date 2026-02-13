package com.jumunhasyeo.hub.infrastructure.outbox;

import com.jumunhasyeo.common.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(
        name = "p_outbox_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbox_event_key", columnNames = "event_key")
        },
        indexes = {
                @Index(name = "idx_outbox_status_created_at", columnList = "status, created_at"),
                @Index(name = "idx_outbox_status_claimed_at", columnList = "status, claimed_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OutboxEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "event_key", nullable = false)
    private String eventKey;

    @Column(nullable = false)
    private String topic;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage = "";

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    private OutboxEvent(String eventName, String payload, OutboxStatus status, String eventKey, String topic) {
        this.eventName = eventName;
        this.payload = payload;
        this.status = status;
        this.eventKey = eventKey;
        this.retryCount = 0;
        this.maxRetries = 3;
        this.topic = topic;
    }

    public static OutboxEvent of(String eventName, String payload, String eventKey, String topic) {
        return new OutboxEvent(eventName, payload, OutboxStatus.PENDING, eventKey, topic);
    }

    public void claimProcessing() {
        this.status = OutboxStatus.PROCESSING;
        this.claimedAt = LocalDateTime.now();
        this.processedAt = null;
    }

    public void markProcessed() {
        this.status = OutboxStatus.COMPLETE;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void markDead(String errorMessage) {
        this.status = OutboxStatus.DEAD;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }

    public void publishSuccess() {
        markProcessed();
    }

    public void publishFail(String errMessage) {
        incrementRetryCount();
        if (canRetry()) {
            markFailed(errMessage);
            return;
        }
        markDead(errMessage);
    }
}

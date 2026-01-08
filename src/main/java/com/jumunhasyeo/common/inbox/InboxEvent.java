package com.jumunhasyeo.common.inbox;

import com.jumunhasyeo.common.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "p_inbox_events",
        indexes = {
                @Index(name = "idx_inbox_event_key", columnList = "eventKey")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class InboxEvent extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String eventKey;  // 멱등성 키

    @Column(nullable = false)
    private String eventName;

    @Type(JsonBinaryType.class)
    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload;  // JSON 형태로 저장

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboxStatus status;

    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public static InboxEvent from(String eventKey, String eventName, String payload) {
        return InboxEvent.builder()
                .eventKey(eventKey)
                .eventName(eventName)
                .payload(payload)
                .status(InboxStatus.RECEIVED)
                .receivedAt(LocalDateTime.now())
                .build();
    }

    // 상태 변경 메서드
    public void updateStatus(InboxStatus newStatus) {
        this.status = newStatus;
        if (newStatus == InboxStatus.COMPLETED) {
            this.processedAt = LocalDateTime.now();
        }
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

    public void markFailed(String errorMessage) {
        this.status = InboxStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void markCompleted() {
        this.status = InboxStatus.COMPLETED;
        this.processedAt = LocalDateTime.now();
    }

    public void markProcessing() {
        this.status = InboxStatus.PROCESSING;
    }

    public void dispatchSuccess() {
        incrementRetryCount();
        markCompleted();
    }

    public void dispatchFail(String errMessage) {
        incrementRetryCount();
        setErrorMessage(errMessage);
        if (canRetry()) {
            this.status = InboxStatus.RECEIVED;
            return;
        }
        this.status = InboxStatus.FAILED;
    }
}

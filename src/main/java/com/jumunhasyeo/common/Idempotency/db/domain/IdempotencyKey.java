package com.jumunhasyeo.common.Idempotency.db.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

@Entity
@Table(name = "p_idempotency_keys")
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Getter
public class IdempotencyKey {
    @Id
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "idempotent_status")
    private IdempotentStatus status;

    @Type(JsonBinaryType.class)
    @Builder.Default
    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload = "{}";

    @Column(name = "error_message")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    private IdempotentType type;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static IdempotencyKey create(
            String idempotencyKey,
            IdempotentStatus status,
            long ttlSeconds,
            IdempotentType type,
            String payload
    ) {
        String errorMsg = "";
        return new IdempotencyKey(
                idempotencyKey,
                status,
                payload,
                errorMsg,
                type,
                LocalDateTime.now(),
                LocalDateTime.now().plusSeconds(ttlSeconds));
    }

    public void updateStatus(IdempotentStatus status) {
        this.status = status;
    }

    public void applyError(String errorMessage) {
        this.status = IdempotentStatus.FAIL;
        this.errorMessage = errorMessage;
    }

    public String genCancelKey() {
        return "CANCEL_" + this.idempotencyKey;
    }
}

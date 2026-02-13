package com.jumunhasyeo.common.Idempotency.db.domain.repository;

import com.jumunhasyeo.common.Idempotency.db.domain.IdempotencyKey;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;
import com.jumunhasyeo.common.Idempotency.db.infrastructure.repository.IdempotencyKeyRepositoryAdapter;
import com.jumunhasyeo.testsupport.RepositorySliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeyRepositorySliceTest extends RepositorySliceTest {

    @Autowired
    private IdempotencyKeyRepositoryAdapter repository;

    @Test
    @DisplayName("멱등키를 저장할 수 있다.")
    void save_idempotencyKey_success() {
        // given
        IdempotencyKey key = createKey("ORDER-123", IdempotentStatus.PROCESSING);

        // when
        repository.save(key);
        Optional<IdempotencyKey> findKey = repository.findByIdempotencyKeyAndNotExpired(
                "ORDER-123",
                LocalDateTime.now()
        );

        // then
        assertThat(findKey).isPresent();
        assertThat(findKey.get().getIdempotencyKey()).isEqualTo("ORDER-123");
        assertThat(findKey.get().getStatus()).isEqualTo(IdempotentStatus.PROCESSING);
    }

    @Test
    @DisplayName("멱등키를 조회할 수 있다.")
    public void findByIdempotencyKeyAndNotExpired_key_success() {
        // given
        IdempotencyKey savedKey = createKey("ORDER-456", IdempotentStatus.SUCCESS);
        testEntityManager.persistAndFlush(savedKey);

        // when
        Optional<IdempotencyKey> findKey = repository.findByIdempotencyKeyAndNotExpired(
                "ORDER-456",
                LocalDateTime.now()
        );

        // then
        assertThat(findKey).isPresent();
        assertThat(findKey.get().getIdempotencyKey()).isEqualTo("ORDER-456");
        assertThat(findKey.get().getStatus()).isEqualTo(IdempotentStatus.SUCCESS);
    }

    @Test
    @DisplayName("만료된 멱등키는 조회되지 않는다.")
    public void findByIdempotencyKeyAndNotExpired_expiredKey_shouldNotFound() {
        // given
        IdempotencyKey expiredKey = IdempotencyKey.builder()
                .idempotencyKey("ORDER-789")
                .status(IdempotentStatus.PROCESSING)
                .createdAt(LocalDateTime.now().minusDays(2))
                .expiresAt(LocalDateTime.now().minusDays(1)) // 어제 만료
                .build();
        testEntityManager.persistAndFlush(expiredKey);

        // when
        Optional<IdempotencyKey> findKey = repository.findByIdempotencyKeyAndNotExpired(
                "ORDER-789",
                LocalDateTime.now()
        );

        // then
        assertThat(findKey).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 키는 조회되지 않는다.")
    public void findByIdempotencyKeyAndNotExpired_nonExistingKey_shouldNotFound() {
        // when
        Optional<IdempotencyKey> findKey = repository.findByIdempotencyKeyAndNotExpired(
                "NON-EXISTING",
                LocalDateTime.now()
        );

        // then
        assertThat(findKey).isEmpty();
    }

    private static IdempotencyKey createKey(String key, IdempotentStatus status) {
        return IdempotencyKey.builder()
                .idempotencyKey(key)
                .status(status)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }
}

package com.jumunhasyeo.common.Idempotency;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeo.common.Idempotency.db.application.PersistentIdempotencyService;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotencyKey;
import com.jumunhasyeo.common.Idempotency.db.domain.IdempotentStatus;
import com.jumunhasyeo.common.Idempotency.db.domain.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistentIdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    @InjectMocks
    private PersistentIdempotencyService service;
    private final String payload = "";

    private ObjectMapper objectMapper;
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new PersistentIdempotencyService(repository, objectMapper);
    }

    private static IdempotencyKey createKey(String key, IdempotentStatus status) {
        return IdempotencyKey.builder()
                .idempotencyKey(key)
                .status(status)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }

    @Test
    @DisplayName("새로운 키의 상태를 조회하면 NONE을 반환한다.")
    public void getCurrentStatus_newKey_success() {
        // given
        String key = "ORDER-123";
        when(repository.findByIdempotencyKeyAndNotExpired(eq(key), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        // when
        IdempotentStatus status = service.getCurrentStatus(key);

        // then
        assertThat(status).isEqualTo(IdempotentStatus.NONE);
    }

    @Test
    @DisplayName("저장된 키의 상태를 조회하면 저장된 상태를 반환한다.")
    public void getCurrentStatus_existingKey_success() {
        // given
        String key = "ORDER-456";
        IdempotencyKey savedKey = createKey(key, IdempotentStatus.PROCESSING);
        when(repository.findByIdempotencyKeyAndNotExpired(eq(key), any(LocalDateTime.class)))
                .thenReturn(Optional.of(savedKey));

        // when
        IdempotentStatus status = service.getCurrentStatus(key);

        // then
        assertThat(status).isEqualTo(IdempotentStatus.PROCESSING);
    }

    @Test
    @DisplayName("새로운 키로 setIfAbsent를 호출하면 true를 반환한다.")
    public void setIfAbsent_newKey_success() throws JsonProcessingException {
        // given
        String key = "ORDER-111";
        when(repository.save(any(IdempotencyKey.class))).thenReturn(createKey(key, IdempotentStatus.PROCESSING));
        // when
        Boolean result = service.setIfAbsent(key, IdempotentStatus.PROCESSING, 86400, payload);

        // then
        assertThat(result).isTrue();
        verify(repository).save(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("이미 존재하는 키로 setIfAbsent를 호출하면 false를 반환한다.")
    public void setIfAbsent_duplicateKey_shouldReturnFalse() throws JsonProcessingException {
        // given
        String key = "ORDER-222";
        when(repository.save(any(IdempotencyKey.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));
        // when
        Boolean result = service.setIfAbsent(key, IdempotentStatus.PROCESSING, 86400, payload);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("존재하는 키의 상태를 업데이트할 수 있다.")
    public void saveStatus_existingKey_success() {
        // given
        String key = "ORDER-333";
        IdempotencyKey savedKey = createKey(key, IdempotentStatus.PROCESSING);
        when(repository.findByIdempotencyKeyAndNotExpired(eq(key), any(LocalDateTime.class)))
                .thenReturn(Optional.of(savedKey));

        // when
        service.saveStatus(key, IdempotentStatus.SUCCESS, 86400);

        // then
        verify(repository).save(any(IdempotencyKey.class));
        assertThat(savedKey.getStatus()).isEqualTo(IdempotentStatus.SUCCESS);
    }

    @Test
    @DisplayName("존재하지 않는 키의 상태를 업데이트하면 저장하지 않는다.")
    public void saveStatus_nonExistingKey_shouldNotSave() {
        // given
        String key = "ORDER-444";
        when(repository.findByIdempotencyKeyAndNotExpired(eq(key), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        // when
        service.saveStatus(key, IdempotentStatus.SUCCESS, 86400);

        // then
        verify(repository, never()).save(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("존재하는 키에 에러 메시지를 저장할 수 있다.")
    public void saveError_existingKey_success() {
        // given
        String key = "ORDER-555";
        String errorMsg = "Payment failed";
        IdempotencyKey savedKey = createKey(key, IdempotentStatus.PROCESSING);
        when(repository.findByIdempotencyKeyAndNotExpired(eq(key), any(LocalDateTime.class)))
                .thenReturn(Optional.of(savedKey));

        // when
        service.saveError(key, errorMsg, 86400);

        // then
        verify(repository).save(any(IdempotencyKey.class));
        assertThat(savedKey.getErrorMessage()).isEqualTo(errorMsg);
        assertThat(savedKey.getStatus()).isEqualTo(IdempotentStatus.FAIL);
    }

    @Test
    @DisplayName("존재하지 않는 키에 에러 메시지를 저장하면 저장하지 않는다.")
    public void saveError_nonExistingKey_shouldNotSave() {
        // given
        String key = "ORDER-666";
        when(repository.findByIdempotencyKeyAndNotExpired(eq(key), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());

        // when
        service.saveError(key, "Error", 86400);

        // then
        verify(repository, never()).save(any(IdempotencyKey.class));
    }
}
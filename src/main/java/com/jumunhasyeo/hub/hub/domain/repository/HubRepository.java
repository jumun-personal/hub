package com.jumunhasyeo.hub.hub.domain.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HubRepository {
    Hub save(Hub hub);
    void flush();
    Optional<Hub> findById(UUID id);
    Optional<Hub> findByIdIncludingCreating(UUID id);
    Optional<Hub> findByIdIncludingDeleted(UUID id);
    Boolean existById(UUID uuid);
    long count();
    List<Hub> findAllByHubType(HubType type);
    List<Hub> findAll();
    int completeIfPending(UUID hubId);
    int failRouteBuildIfPending(UUID hubId);
    int retryRouteBuildIfFailed(UUID hubId);
    int failIfPending(UUID hubId, LocalDateTime deletedAt, Long deletedBy);
}

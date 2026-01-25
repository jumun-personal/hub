package com.jumunhasyeo.hub.hub.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import com.jumunhasyeo.hub.hub.domain.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubRepositoryAdapter implements HubRepository {
    private final JpaHubRepository jpaHubRepository;

    @Override
    public Hub save(Hub hub) {
        return jpaHubRepository.save(hub);
    }

    @Override
    public Optional<Hub> findById(UUID id) {
        return jpaHubRepository.findById(id, HubStatus.COMPLETE);
    }

    @Override
    public Optional<Hub> findByIdIncludingCreating(UUID id) {
        return jpaHubRepository.findByIdIncludingCreating(id);
    }

    @Override
    public Optional<Hub> findByIdIncludingDeleted(UUID id) {
        return jpaHubRepository.findByIdIncludingDeleted(id);
    }

    @Override
    public Boolean existById(UUID hubId) {
        return jpaHubRepository.existsById(hubId, HubStatus.COMPLETE);
    }

    @Override
    public long count() {
        return jpaHubRepository.count(HubStatus.COMPLETE);
    }

    @Override
    public List<Hub> findAllByHubType(HubType type) {
        return jpaHubRepository.findAllByHubType(type, HubStatus.COMPLETE);
    }

    @Override
    public List<Hub> findAll() {
        return jpaHubRepository.findAll(HubStatus.COMPLETE);
    }
}

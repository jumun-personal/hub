package com.jumunhasyeo.hub.hub.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubRelation;
import com.jumunhasyeo.hub.hub.domain.repository.HubRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubRelationRepositoryAdapter implements HubRelationRepository {
    private final JpaHubRelationRepository jpaHubRelationRepository;

    @Override
    public boolean existsByParentHubAndChildHub(Hub parent, Hub child) {
        return parent.getBranchHubs().contains(child);
    }

    @Override
    public HubRelation save(HubRelation relation) {
        return jpaHubRelationRepository.save(relation);
    }

    @Override
    public List<HubRelation> findByParentHub(Hub parent) {
        return parent.getCenterHubRelations().stream().toList();
    }

    @Override
    public void deleteByHubId(UUID hubId) {
        jpaHubRelationRepository.deleteByHubId(hubId);
    }
}

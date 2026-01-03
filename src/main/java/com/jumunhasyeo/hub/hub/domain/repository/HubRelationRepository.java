package com.jumunhasyeo.hub.hub.domain.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubRelation;

import java.util.List;

public interface HubRelationRepository {
    boolean existsByParentHubAndChildHub(Hub parent, Hub child);
    HubRelation save(HubRelation relation);
    List<HubRelation> findByParentHub(Hub parent);
    void deleteByHubId(java.util.UUID hubId);
}

package com.jumunhasyeo.hub.hub.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.HubRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JpaHubRelationRepository extends JpaRepository<HubRelation, UUID> {
    @Modifying
    @Query("DELETE FROM HubRelation r WHERE r.centerHub.hubId = :hubId OR r.branchHub.hubId = :hubId")
    void deleteByHubId(@Param("hubId") UUID hubId);
}

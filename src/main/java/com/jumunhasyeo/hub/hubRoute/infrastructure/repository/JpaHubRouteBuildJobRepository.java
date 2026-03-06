package com.jumunhasyeo.hub.hubRoute.infrastructure.repository;

import com.jumunhasyeo.hub.hubRoute.domain.entity.HubRouteBuildJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaHubRouteBuildJobRepository extends JpaRepository<HubRouteBuildJob, UUID> {
}

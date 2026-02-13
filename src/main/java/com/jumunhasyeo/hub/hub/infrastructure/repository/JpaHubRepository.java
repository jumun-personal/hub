package com.jumunhasyeo.hub.hub.infrastructure.repository;

import com.jumunhasyeo.hub.hub.domain.entity.Hub;
import com.jumunhasyeo.hub.hub.domain.entity.HubStatus;
import com.jumunhasyeo.hub.hub.domain.entity.HubType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaHubRepository extends JpaRepository<Hub, UUID> {

    @Query("SELECT h FROM Hub h WHERE h.hubId = :id AND h.isDeleted = false AND (h.status = :status OR h.status IS NULL)")
    Optional<Hub> findById(@Param("id") UUID id, @Param("status") HubStatus status);

    @Query("SELECT h FROM Hub h WHERE h.hubType = :hubType AND h.isDeleted = false AND (h.status = :status OR h.status IS NULL)")
    List<Hub> findAllByHubType(@Param("hubType") HubType hubType, @Param("status") HubStatus status);
    
    @Query("SELECT h FROM Hub h WHERE h.isDeleted = false AND (h.status = :status OR h.status IS NULL)")
    List<Hub> findAll(@Param("status") HubStatus status);
    
    @Query("SELECT COUNT(h) FROM Hub h WHERE h.isDeleted = false AND (h.status = :status OR h.status IS NULL)")
    long count(@Param("status") HubStatus status);
    
    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END FROM Hub h WHERE h.hubId = :id AND h.isDeleted = false AND (h.status = :status OR h.status IS NULL)")
    boolean existsById(@Param("id") UUID id, @Param("status") HubStatus status);

    @Query("SELECT h FROM Hub h WHERE h.hubId = :id AND h.isDeleted = false")
    Optional<Hub> findByIdIncludingCreating(@Param("id") UUID id);

    @Query("SELECT h FROM Hub h WHERE h.hubId = :id")
    Optional<Hub> findByIdIncludingDeleted(@Param("id") UUID id);

    @Modifying
    @Query("""
            UPDATE Hub h
               SET h.status = :completeStatus
             WHERE h.hubId = :id
               AND h.status = :pendingStatus
               AND h.isDeleted = false
            """)
    int completeIfPending(
            @Param("id") UUID id,
            @Param("pendingStatus") HubStatus pendingStatus,
            @Param("completeStatus") HubStatus completeStatus
    );

    @Modifying
    @Query("""
            UPDATE Hub h
               SET h.status = :failedStatus,
                   h.deletedAt = :deletedAt,
                   h.deletedBy = :deletedBy,
                   h.isDeleted = true
             WHERE h.hubId = :id
               AND h.status = :pendingStatus
               AND h.isDeleted = false
            """)
    int failIfPending(
            @Param("id") UUID id,
            @Param("pendingStatus") HubStatus pendingStatus,
            @Param("failedStatus") HubStatus failedStatus,
            @Param("deletedAt") LocalDateTime deletedAt,
            @Param("deletedBy") Long deletedBy
    );
}

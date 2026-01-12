package com.jumunhasyeo.stock.infrastructure.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaInboxRepository extends JpaRepository<InboxEvent, UUID> {
    
    Optional<InboxEvent> findByEventKey(String eventKey);
    
    boolean existsByEventKey(String eventKey);
    
    // 스케줄러용: 특정 상태이면서 일정 시간 지난 이벤트 조회
    @Query("SELECT i FROM InboxEvent i WHERE i.status = :status AND i.modifiedAt < :threshold")
    List<InboxEvent> findByStatusAndModifiedAtBefore(
            @Param("status") InboxStatus status, 
            @Param("threshold") LocalDateTime threshold
    );
    
    // 상태별 개수 조회
    long countByStatus(InboxStatus status);
    
    // 오래된 이벤트 삭제
    @Modifying
    @Transactional
    @Query("DELETE FROM InboxEvent i WHERE i.status = :status AND i.modifiedAt < :threshold")
    int deleteByStatusAndModifiedAtBefore(
            @Param("status") InboxStatus status, 
            @Param("threshold") LocalDateTime threshold
    );
}

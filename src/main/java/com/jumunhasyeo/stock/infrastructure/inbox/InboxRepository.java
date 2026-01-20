package com.jumunhasyeo.stock.infrastructure.inbox;

import java.time.LocalDateTime;
import java.util.List;

public interface InboxRepository {
    void save(InboxEvent inboxEvent);
    List<InboxEvent> findByStatusAndModifiedAtBefore(InboxStatus inboxStatus, LocalDateTime threshold);
    boolean existsByEventKey(String eventKey);
}

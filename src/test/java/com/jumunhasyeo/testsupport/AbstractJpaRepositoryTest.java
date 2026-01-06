package com.jumunhasyeo.testsupport;

import com.jumunhasyeo.common.Idempotency.db.infrastructure.repository.IdempotentKeyRepositoryAdapter;
import com.jumunhasyeo.common.inbox.InboxRepositoryAdapter;
import com.jumunhasyeo.common.outbox.OutboxRepositoryAdapter;
import com.jumunhasyeo.company.infrastructure.repository.CompanyRepositoryAdapter;
import com.jumunhasyeo.hub.hub.infrastructure.repository.HubRepositoryAdapter;
import com.jumunhasyeo.hub.hub.infrastructure.repository.JpaHubRepositoryCustomImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@Import({
        CompanyRepositoryAdapter.class,
        OutboxRepositoryAdapter.class,
        InboxRepositoryAdapter.class,
        IdempotentKeyRepositoryAdapter.class,
        HubRepositoryAdapter.class,
        JpaHubRepositoryCustomImpl.class
})
public abstract class AbstractJpaRepositoryTest extends AbstractDataJpaSliceTest {

    @Autowired
    protected TestEntityManager testEntityManager;
}

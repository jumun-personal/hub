package com.jumunhasyeo.testsupport;

import com.jumunhasyeo.CleanUp;
import com.jumunhasyeo.CommonTestContainer;
import com.jumunhasyeo.RepositoryTestConfig;
import com.jumunhasyeo.common.Idempotency.db.infrastructure.repository.IdempotencyKeyRepositoryAdapter;
import com.jumunhasyeo.common.config.JpaConfig;
import com.jumunhasyeo.stock.infrastructure.inbox.InboxRepositoryAdapter;
import com.jumunhasyeo.hub.infrastructure.outbox.OutboxRepositoryAdapter;
import com.jumunhasyeo.company.infrastructure.repository.CompanyRepositoryAdapter;
import com.jumunhasyeo.hub.hub.infrastructure.repository.HubRepositoryAdapter;
import com.jumunhasyeo.hub.hub.infrastructure.repository.JpaHubRepositoryCustomImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@Import({
        CompanyRepositoryAdapter.class,
        OutboxRepositoryAdapter.class,
        InboxRepositoryAdapter.class,
        IdempotencyKeyRepositoryAdapter.class,
        HubRepositoryAdapter.class,
        JpaHubRepositoryCustomImpl.class,
        CleanUp.class, RepositoryTestConfig.class, JpaConfig.class
})
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class RepositorySliceTest extends CommonTestContainer {

    @Autowired
    protected CleanUp cleanUp;

    @BeforeEach
    protected void truncateTables() {
        cleanUp.truncateAll();
        beforeEachAfterTruncate();
    }

    @AfterEach
    protected void baseAfterEach() {
        afterEachCleanup();
    }

    protected void beforeEachAfterTruncate() {
    }

    protected void afterEachCleanup() {
    }

    @Autowired
    protected TestEntityManager testEntityManager;
}

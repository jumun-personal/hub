package com.jumunhasyeo.testsupport;

import com.jumunhasyeo.CleanUp;
import com.jumunhasyeo.CommonTestContainer;
import com.jumunhasyeo.RepositoryTestConfig;
import com.jumunhasyeo.common.config.JpaConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CleanUp.class, RepositoryTestConfig.class, JpaConfig.class})
public abstract class AbstractDataJpaSliceTest extends CommonTestContainer {

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
}

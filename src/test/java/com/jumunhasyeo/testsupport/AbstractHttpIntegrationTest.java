package com.jumunhasyeo.testsupport;

import com.jumunhasyeo.CleanUp;
import com.jumunhasyeo.CommonTestContainer;
import com.jumunhasyeo.InternalIntegrationTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({CleanUp.class, InternalIntegrationTestConfig.class})
public abstract class AbstractHttpIntegrationTest extends CommonTestContainer {

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

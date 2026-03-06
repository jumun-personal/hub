package com.jumunhasyeo.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    @DisplayName("허브 경로 Executor는 동시 실행 2개와 대기 큐 0개로 구성된다")
    void hubRouteBuildExecutor_hasTenWorkersAndNoQueue() {
        // given
        AsyncConfig asyncConfig = new AsyncConfig();

        // when
        ThreadPoolTaskExecutor executor = asyncConfig.hubRouteBuildExecutor();

        // then
        assertThat(executor.getCorePoolSize()).isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).isEqualTo(2);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
        executor.shutdown();
    }
}

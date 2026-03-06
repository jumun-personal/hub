package com.jumunhasyeo.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    @Test
    @DisplayName("공용 스케줄러 Executor의 용량을 설정값으로 구성한다")
    void schedulerExecutor_usesConfiguredCapacity() {
        // given
        AsyncConfig asyncConfig = new AsyncConfig();

        // when
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) asyncConfig.schedulerExecutor();

        // then
        assertThat(executor.getCorePoolSize()).isEqualTo(6);
        assertThat(executor.getMaxPoolSize()).isEqualTo(10);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isZero();
        executor.shutdown();
    }
}

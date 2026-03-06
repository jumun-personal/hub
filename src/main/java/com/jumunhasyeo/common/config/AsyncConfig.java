package com.jumunhasyeo.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "schedulerExecutor")
    public Executor schedulerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);      // 스케줄러 6개
        executor.setMaxPoolSize(10);       // 여유분
        executor.setQueueCapacity(0);    // 대기큐 불필요
        executor.setThreadNamePrefix("scheduler-");
        executor.setRejectedExecutionHandler((task, executor1) -> {
            log.error("[{}] Scheduler task rejected: {}, Active: {}, Queue: {}",
                    Thread.currentThread().getName(),
                    task.toString(),
                    executor1.getActiveCount(),
                    executor1.getQueue().size()
            );
        });
        executor.initialize();
        return executor;
    }

    @Bean(name = "eventExecutor")
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(40);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("event-");
        executor.setRejectedExecutionHandler((task, executor1) -> {
            log.error("[{}] event task rejected: {}, Active: {}, Queue: {}",
                    Thread.currentThread().getName(),
                    task.toString(),
                    executor1.getActiveCount(),
                    executor1.getQueue().size()
            );
        });
        executor.initialize();
        return executor;
    }

    @Bean(name = "hubRouteBuildExecutor")
    public ThreadPoolTaskExecutor hubRouteBuildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("hub-route-build-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}

package com.jumunhasyeo.common.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.jumunhasyeo", defaultConfiguration = AutoTracingConfig.class)
public class FeignClientConfig {
}

package com.jumunhasyeo.common.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SlackPublisher {
    public void publish(String title, String message) {
        log.warn("Slack publisher is not configured. title={}, message={}", title, message);
    }
}

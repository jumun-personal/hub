//package com.jumunhasyeo.common.config;
//
//import com.jumunhasyeo.common.slack.SlackPublisher;
//import io.github.resilience4j.circuitbreaker.CircuitBreaker;
//import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
//import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
//import jakarta.annotation.PostConstruct;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.annotation.Configuration;
//
///**
// * Circuit Breaker 상태 전이 이벤트 리스너
// *
// * Redis Cache Circuit Breaker 상태 변경 시 Slack 알림 발송:
// * - CLOSED → OPEN: Redis 장애 감지
// * - OPEN → HALF_OPEN: 복구 테스트 시작
// * - HALF_OPEN → CLOSED: Redis 복구 완료
// */
//@Slf4j
//@Configuration
//@RequiredArgsConstructorit
//public class CircuitBreakerEventListener {
//
//    private final CircuitBreakerRegistry circuitBreakerRegistry;
//    private final SlackPublisher slackPublisher;
//
//    @PostConstruct
//    public void registerEventListener() {
//        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redisCache");
//
//        circuitBreaker.getEventPublisher()
//                .onStateTransition(this::handleStateTransition);
//
//        log.info(" Circuit Breaker Event Listener registered - name: redisCache");
//    }
//
//    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent event) {
//        String fromState = event.getStateTransition().getFromState().name();
//        String toState = event.getStateTransition().getToState().name();
//
//        log.warn(" Circuit Breaker State Transition - {} → {}", fromState, toState);
//
//        // Slack 알림
//        switch (toState) {
//            case "OPEN":
//                sendSlackAlert(
//                        " Redis Cache Circuit OPEN",
//                        String.format(
//                                "Redis 장애 감지!\n" +
//                                "From: %s → To: %s\n" +
//                                "10초간 Redis 차단\n" +
//                                "L1 Cache만 사용",
//                                fromState, toState
//                        )
//                );
//                break;
//
//            case "HALF_OPEN":
//                log.info(" Circuit Breaker HALF_OPEN - Testing Redis recovery (3 attempts)");
//                break;
//
//            case "CLOSED":
//                if ("HALF_OPEN".equals(fromState)) {
//                    sendSlackAlert(
//                            " Redis Cache Circuit CLOSED",
//                            String.format(
//                                    "Redis 복구 완료!\n" +
//                                    "• From: %s → To: %s\n" +
//                                    "• L1 + L2 정상 동작",
//                                    fromState, toState
//                            )
//                    );
//                }
//                break;
//        }
//    }
//
//    private void sendSlackAlert(String title, String message) {
//        try {
//            slackPublisher.publish(title, message);
//        } catch (Exception e) {
//            log.error("Failed to send Slack alert - title: {}, error: {}", title, e.getMessage());
//        }
//    }
//}

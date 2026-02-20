package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class RouteProviderMetrics {

    private static final String CALL_DURATION = "route.provider.call.duration";
    private static final String CALLS = "route.provider.calls";
    private static final Duration[] LATENCY_BUCKETS = {
            Duration.ofMillis(100),
            Duration.ofMillis(300),
            Duration.ofMillis(500),
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(3)
    };

    private final MeterRegistry meterRegistry;
    private final Map<MapProvider, Timer> successLatency;

    public RouteProviderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.successLatency = Map.of(
                MapProvider.KAKAO, successTimer(meterRegistry, MapProvider.KAKAO),
                MapProvider.NAVER, successTimer(meterRegistry, MapProvider.NAVER)
        );
    }

    public <T> T observe(
            MapProvider provider,
            RouteCallPhase phase,
            Supplier<T> apiCall
    ) {
        long startedNanos = System.nanoTime();
        try {
            T result = apiCall.get();
            recordSuccessLatency(provider, System.nanoTime() - startedNanos);
            increment(provider, phase, "success");
            return result;
        } catch (RuntimeException exception) {
            increment(provider, phase, outcome(exception));
            throw exception;
        }
    }

    private void recordSuccessLatency(MapProvider provider, long elapsedNanos) {
        Timer timer = successLatency.get(provider);
        if (timer != null) {
            timer.record(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private void increment(MapProvider provider, RouteCallPhase phase, String outcome) {
        meterRegistry.counter(
                CALLS,
                "provider", tag(provider),
                "phase", phase.tagValue(),
                "outcome", outcome
        ).increment();
    }

    private String outcome(RuntimeException exception) {
        if (exception instanceof RouteProviderTransientException transientException) {
            return switch (transientException.failureType()) {
                case TIMEOUT -> "timeout";
                case CONNECTION -> "connect_error";
                case SERVER_ERROR -> "5xx";
                case UNKNOWN -> "error";
            };
        }
        if (exception instanceof RouteRateLimitExceededException) {
            return "rate_limited";
        }
        if (exception instanceof RouteRequestRejectedException
                || exception instanceof RouteProviderConfigurationException) {
            return "rejected";
        }
        return "error";
    }

    private String tag(MapProvider provider) {
        return provider.name().toLowerCase();
    }

    private static Timer successTimer(MeterRegistry meterRegistry, MapProvider provider) {
        return Timer.builder(CALL_DURATION)
                .description("Successful route provider API response time")
                .tag("provider", provider.name().toLowerCase())
                .serviceLevelObjectives(LATENCY_BUCKETS)
                .register(meterRegistry);
    }

    public enum RouteCallPhase {
        INITIAL("initial"),
        RETRY("retry"),
        FALLBACK("fallback");

        private final String tagValue;

        RouteCallPhase(String tagValue) {
            this.tagValue = tagValue;
        }

        String tagValue() {
            return tagValue;
        }
    }
}

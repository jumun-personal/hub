package com.jumunhasyeo.hub.hubRoute.application.service;

import com.jumunhasyeo.hub.hubRoute.application.dto.MapProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class DistributedRouteRateLimiter {

    private static final String KEY_PREFIX = "hub-route:rate-limit:";
    private static final DefaultRedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>("""
            local capacity = tonumber(ARGV[1])
            local refillPerSecond = tonumber(ARGV[2])
            local ttlMillis = tonumber(ARGV[3])
            local redisTime = redis.call('TIME')
            local nowMillis = (tonumber(redisTime[1]) * 1000) + math.floor(tonumber(redisTime[2]) / 1000)
            local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillMillis')
            local tokens = tonumber(bucket[1])
            local lastRefillMillis = tonumber(bucket[2])
            if tokens == nil then
                tokens = capacity
            end
            if lastRefillMillis == nil then
                lastRefillMillis = nowMillis
            end
            local elapsedMillis = math.max(0, nowMillis - lastRefillMillis)
            tokens = math.min(capacity, tokens + (elapsedMillis / 1000.0) * refillPerSecond)
            if tokens >= 1 then
                tokens = tokens - 1
                redis.call('HSET', KEYS[1], 'tokens', tokens, 'lastRefillMillis', nowMillis)
                redis.call('PEXPIRE', KEYS[1], ttlMillis)
                return 0
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'lastRefillMillis', nowMillis)
            redis.call('PEXPIRE', KEYS[1], ttlMillis)
            local retryAfterMillis = math.ceil(((1 - tokens) / refillPerSecond) * 1000)
            return math.max(1, retryAfterMillis)
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${hub.route.rate-limit.kakao.permits-per-second:3}")
    private int kakaoPermitsPerSecond;

    @Value("${hub.route.rate-limit.naver.permits-per-second:3}")
    private int naverPermitsPerSecond;

    public DistributedRouteRateLimiter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public RateLimitDecision acquire(MapProvider provider) {
        int permitsPerSecond = permitsPerSecond(provider);
        try {
            Long retryAfterMillis = stringRedisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    List.of(KEY_PREFIX + provider.name().toLowerCase()),
                    Integer.toString(permitsPerSecond),
                    Integer.toString(permitsPerSecond),
                    "10000"
            );
            if (retryAfterMillis == null || retryAfterMillis == 0L) {
                return RateLimitDecision.distributedAllowed();
            }
            return RateLimitDecision.denied(Duration.ofMillis(retryAfterMillis));
        } catch (RuntimeException e) {
            log.warn("Distributed route rate limiter unavailable. fall back to local limiter. provider={}", provider, e);
            return RateLimitDecision.localFallback();
        }
    }

    private int permitsPerSecond(MapProvider provider) {
        return switch (provider) {
            case KAKAO -> kakaoPermitsPerSecond;
            case NAVER -> naverPermitsPerSecond;
            case UNKNOWN -> throw new IllegalArgumentException("Unknown route provider");
        };
    }

    public record RateLimitDecision(boolean allowed, Duration retryAfter, boolean useLocalFallback) {

        public static RateLimitDecision distributedAllowed() {
            return new RateLimitDecision(true, Duration.ZERO, false);
        }

        public static RateLimitDecision denied(Duration retryAfter) {
            return new RateLimitDecision(false, retryAfter, false);
        }

        public static RateLimitDecision localFallback() {
            return new RateLimitDecision(true, Duration.ZERO, true);
        }
    }
}

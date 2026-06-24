package com.stocksense.agent;

import com.stocksense.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Per-tenant fixed-window rate limiter backed by Redis, used to cap AI agent calls (cost control).
 * Fails open: if Redis is unavailable the request is allowed rather than blocked.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param action     logical action name (used in the key)
     * @param tenantId   tenant being limited
     * @param maxPerMin  allowed calls per minute
     */
    public void check(String action, Long tenantId, int maxPerMin) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "ratelimit:%s:%d:%d".formatted(action, tenantId, minute);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(70));
            }
            if (count != null && count > maxPerMin) {
                throw new RateLimitExceededException(
                        "Rate limit exceeded for %s — max %d/min. Try again shortly.".formatted(action, maxPerMin));
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Rate limiter unavailable ({}), allowing request", e.getMessage());
        }
    }
}

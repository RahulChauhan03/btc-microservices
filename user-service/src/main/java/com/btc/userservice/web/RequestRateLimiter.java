package com.btc.userservice.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Small in-memory sliding-window limiter for the public password-recovery endpoints. Per instance: with
 * several user-service instances each enforces its own limits (documented limitation).
 */
@Component
public class RequestRateLimiter {

    private static final int MAX_KEYS = 50_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public RequestRateLimiter(Clock passwordResetClock) {
        this.clock = passwordResetClock;
    }

    /** Records a hit for {@code key}; returns false (and records nothing) if the limit is already reached. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = clock.instant();
        if (hits.size() > MAX_KEYS) {
            // Bound memory: drop keys whose hits have all left the window (pruned atomically per key).
            for (String existing : hits.keySet()) {
                hits.computeIfPresent(existing, (k, times) -> prune(times, now, window).isEmpty() ? null : times);
            }
        }
        boolean[] allowed = {false};
        hits.compute(key, (k, times) -> {
            Deque<Instant> deque = prune(times == null ? new ArrayDeque<>() : times, now, window);
            if (deque.size() < limit) {
                deque.addLast(now);
                allowed[0] = true;
            }
            return deque;
        });
        return allowed[0];
    }

    /**
     * The caller's IP. Behind the API gateway this is the last X-Forwarded-For entry, which the gateway itself
     * appends (values a client puts earlier in the header are ignored); otherwise the TCP peer.
     */
    public static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    private static Deque<Instant> prune(Deque<Instant> times, Instant now, Duration window) {
        while (!times.isEmpty() && !times.peekFirst().isAfter(now.minus(window))) {
            times.pollFirst();
        }
        return times;
    }
}

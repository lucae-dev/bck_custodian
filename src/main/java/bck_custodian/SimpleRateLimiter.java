package bck_custodian;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SimpleRateLimiter {
    private final int maxRequests = 100;
    private final Duration window = Duration.ofMinutes(60);
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> starts = new ConcurrentHashMap<>();

    public synchronized boolean isAllowed(String key) {
        Instant now = Instant.now();
        starts.compute(key, (k, start) -> {
            if (start == null || Duration.between(start, now).compareTo(window) > 0) {
                counts.put(k, new AtomicInteger(0));
                return now;
            }
            return start;
        });
        AtomicInteger counter = counts.computeIfAbsent(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= maxRequests;
    }

    /**
     * If the key has exceeded its window, returns how much time remains until reset.
     * Otherwise returns zero.
     */
    public synchronized Duration getTimeUntilReset(String key) {
        Instant start = starts.get(key);
        if (start == null) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(start, Instant.now());
        Duration remaining = window.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
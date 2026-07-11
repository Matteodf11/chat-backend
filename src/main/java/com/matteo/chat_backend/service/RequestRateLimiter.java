package com.matteo.chat_backend.service;

import com.matteo.chat_backend.exception.TooManyRequestsException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RequestRateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    public void requireCapacity(String key, int maximumAttempts, Duration window, String errorMessage) {
        long now = System.currentTimeMillis();
        long oldestAllowed = now - window.toMillis();
        Deque<Long> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < oldestAllowed) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maximumAttempts) {
                throw new TooManyRequestsException(errorMessage);
            }
            timestamps.addLast(now);
        }

        if (attempts.size() > 10_000) {
            attempts.entrySet().removeIf(entry -> {
                Deque<Long> values = entry.getValue();
                synchronized (values) {
                    return values.isEmpty() || values.peekLast() < oldestAllowed;
                }
            });
        }
    }
}

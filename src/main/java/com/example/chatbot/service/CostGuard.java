package com.example.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple in-memory guard for local practice.
 * It limits prompt size and requests per client per rolling minute.
 * It is not a billing system and is not a production distributed rate limiter.
 */
@Component
public class CostGuard {

    private final int maxInputCharacters;
    private final int requestsPerMinute;
    private final ConcurrentHashMap<String, Deque<Long>> requestTimes = new ConcurrentHashMap<>();

    public CostGuard(
            @Value("${chat.max-input-characters:2000}") int maxInputCharacters,
            @Value("${chat.requests-per-minute:10}") int requestsPerMinute) {
        this.maxInputCharacters = maxInputCharacters;
        this.requestsPerMinute = requestsPerMinute;
    }

    public void check(String question, String clientKey) {
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question must not be empty.");
        }

        if (question.length() > maxInputCharacters) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Question is too long. Maximum is " + maxInputCharacters + " characters."
            );
        }

        String key = (clientKey == null || clientKey.isBlank()) ? "unknown-client" : clientKey;
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000L;

        Deque<Long> times = requestTimes.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < cutoff) {
                times.removeFirst();
            }

            if (times.size() >= requestsPerMinute) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Request limit reached. Please wait a minute and try again."
                );
            }

            times.addLast(now);
        }
    }
}

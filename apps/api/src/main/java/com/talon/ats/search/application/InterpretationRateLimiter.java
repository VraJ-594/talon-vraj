package com.talon.ats.search.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InterpretationRateLimiter {

  private static final int MAX_REQUESTS = 10;
  private final ConcurrentHashMap<UUID, Window> windows = new ConcurrentHashMap<>();
  private final Clock clock;

  public InterpretationRateLimiter(Clock clock) {
    this.clock = clock;
  }

  public void acquire(UUID userId) {
    Instant now = clock.instant();
    windows.compute(
        userId,
        (ignored, current) -> {
          if (current == null || !now.isBefore(current.startedAt().plus(1, ChronoUnit.MINUTES))) {
            return new Window(now, 1);
          }
          if (current.count() >= MAX_REQUESTS) {
            throw new InterpretationRateLimitException();
          }
          return new Window(current.startedAt(), current.count() + 1);
        });
  }

  private record Window(Instant startedAt, int count) {}
}

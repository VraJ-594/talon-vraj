package com.talon.ats.platform.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class LeakyBucketTests {

  @Test
  void permitsFiveImmediateStartsThenLeaksAtFivePerSecond() throws Exception {
    AtomicLong now = new AtomicLong();
    List<Long> sleeps = new ArrayList<>();
    LeakyBucket limiter =
        new LeakyBucket(
            5,
            5,
            5,
            now::get,
            nanos -> {
              sleeps.add(nanos);
              now.addAndGet(nanos);
            });

    for (int start = 0; start < 5; start++) {
      limiter.acquire().close();
    }
    limiter.acquire().close();

    assertThat(sleeps).containsExactly(Duration.ofMillis(200).toNanos());
  }

  @Test
  void neverAllowsMoreThanFiveInFlightOperations() throws Exception {
    LeakyBucket limiter = new LeakyBucket(1_000, 5, 5);
    List<RateLimitPermit> held = new ArrayList<>();
    for (int operation = 0; operation < 5; operation++) {
      held.add(limiter.acquire());
    }

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<RateLimitPermit> sixth = executor.submit(limiter::acquire);
      Thread.sleep(50);
      assertThat(sixth.isDone()).isFalse();

      held.removeFirst().close();
      RateLimitPermit acquired = sixth.get(1, TimeUnit.SECONDS);
      acquired.close();
    } finally {
      held.forEach(RateLimitPermit::close);
    }
  }
}

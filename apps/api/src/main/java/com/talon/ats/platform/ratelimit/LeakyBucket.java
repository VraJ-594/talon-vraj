package com.talon.ats.platform.ratelimit;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public final class LeakyBucket implements RateLimiter {

  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  private final double startsPerNanosecond;
  private final int capacity;
  private final Semaphore inFlight;
  private final LongSupplier nanoTime;
  private final NanoSleeper sleeper;
  private double available;
  private long lastRefill;

  public LeakyBucket(int startsPerSecond, int capacity, int maximumInFlight) {
    this(startsPerSecond, capacity, maximumInFlight, System::nanoTime, LeakyBucket::sleep);
  }

  LeakyBucket(
      int startsPerSecond,
      int capacity,
      int maximumInFlight,
      LongSupplier nanoTime,
      NanoSleeper sleeper) {
    if (startsPerSecond <= 0 || capacity <= 0 || maximumInFlight <= 0) {
      throw new IllegalArgumentException("rate, capacity, and maximumInFlight must be positive");
    }
    this.startsPerNanosecond = (double) startsPerSecond / NANOS_PER_SECOND;
    this.capacity = capacity;
    this.inFlight = new Semaphore(maximumInFlight, true);
    this.nanoTime = nanoTime;
    this.sleeper = sleeper;
    this.available = capacity;
    this.lastRefill = nanoTime.getAsLong();
  }

  @Override
  public RateLimitPermit acquire() throws InterruptedException {
    inFlight.acquire();
    try {
      acquireStart();
    } catch (InterruptedException exception) {
      inFlight.release();
      throw exception;
    }
    AtomicBoolean released = new AtomicBoolean();
    return () -> {
      if (released.compareAndSet(false, true)) {
        inFlight.release();
      }
    };
  }

  private void acquireStart() throws InterruptedException {
    while (true) {
      long waitNanos;
      synchronized (this) {
        refill();
        if (available >= 1.0d) {
          available -= 1.0d;
          return;
        }
        waitNanos = Math.max(1L, (long) Math.ceil((1.0d - available) / startsPerNanosecond));
      }
      sleeper.sleep(waitNanos);
    }
  }

  private void refill() {
    long now = nanoTime.getAsLong();
    long elapsed = Math.max(0L, now - lastRefill);
    available = Math.min(capacity, available + elapsed * startsPerNanosecond);
    lastRefill = now;
  }

  private static void sleep(long nanos) throws InterruptedException {
    long millis = nanos / 1_000_000L;
    int remainder = (int) (nanos % 1_000_000L);
    Thread.sleep(millis, remainder);
  }

  @FunctionalInterface
  interface NanoSleeper {
    void sleep(long nanos) throws InterruptedException;
  }
}

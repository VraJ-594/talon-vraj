package com.talon.ats.platform.ratelimit;

@FunctionalInterface
public interface RateLimitPermit extends AutoCloseable {

  @Override
  void close();

  static RateLimitPermit noop() {
    return () -> {};
  }
}

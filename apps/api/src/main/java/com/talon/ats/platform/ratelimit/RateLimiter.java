package com.talon.ats.platform.ratelimit;

@FunctionalInterface
public interface RateLimiter {

  RateLimitPermit acquire() throws InterruptedException;
}

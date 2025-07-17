package com.genericembedding;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimiter {
    private final int maxRequestsPerSecond;
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private final AtomicLong requestCount = new AtomicLong(0);

    public RateLimiter(int maxRequestsPerSecond) {
        this.maxRequestsPerSecond = maxRequestsPerSecond;
    }

    public void acquire() throws InterruptedException {
        if (maxRequestsPerSecond <= 0) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long lastTime = lastRequestTime.get();
        long currentCount = requestCount.get();

        if (currentTime - lastTime >= 1000) {
            lastRequestTime.set(currentTime);
            requestCount.set(1);
            return;
        }

        if (currentCount >= maxRequestsPerSecond) {
            long waitTime = 1000 - (currentTime - lastTime);
            Thread.sleep(waitTime);
            lastRequestTime.set(System.currentTimeMillis());
            requestCount.set(1);
        } else {
            requestCount.incrementAndGet();
        }
    }
}

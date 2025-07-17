package com.genericembedding;

public class BackoffStrategy {
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;
    private int attempt = 0;

    public BackoffStrategy(long initialDelayMs, long maxDelayMs, double multiplier) {
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
    }

    public void reset() {
        attempt = 0;
    }

    public long nextDelay() {
        long delay = (long) (initialDelayMs * Math.pow(multiplier, attempt));
        attempt++;
        return Math.min(delay, maxDelayMs);
    }
}
